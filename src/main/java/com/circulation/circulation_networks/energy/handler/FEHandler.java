package com.circulation.circulation_networks.energy.handler;

import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.EnergyAmounts;
import com.circulation.circulation_networks.api.HandlerTickResult;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.manager.HandlerBindingPolicy;
import com.circulation.circulation_networks.manager.HandlerInvalidationSink;
import com.circulation.circulation_networks.network.nodes.HubNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class FEHandler implements IEnergyHandler {

    private static final Direction[] DIRECTIONS = Direction.values();
    private static final HandlerBindingPolicy BINDING_POLICY = HandlerBindingPolicy.of(
        HandlerBindingPolicy.TickLifecycle.BEGIN_TICK,
        HandlerBindingPolicy.RoleScope.RUNTIME_DYNAMIC,
        HandlerBindingPolicy.MappingScope.NONE,
        HandlerBindingPolicy.PairMatching.NONE
    );

    @Nullable
    private EnergyHandler send;
    @Nullable
    private EnergyHandler receive;
    @Nullable
    private Direction sendDirection;
    @Nullable
    private Direction receiveDirection;
    @Nullable
    private Direction capabilityDirection;
    @Nullable
    private BlockEntity blockEntity;
    private EnergyType energyType = EnergyType.INVALID;
    private boolean itemBound;
    private int extractBudget;
    private int receiveBudget;

    private static int probeExtract(EnergyHandler handler) {
        try (Transaction transaction = Transaction.openRoot()) {
            return requireValidAmount(handler.extract(Integer.MAX_VALUE, transaction), Integer.MAX_VALUE, "extract probe");
        }
    }

    private static int probeReceive(EnergyHandler handler) {
        try (Transaction transaction = Transaction.openRoot()) {
            return requireValidAmount(handler.insert(Integer.MAX_VALUE, transaction), Integer.MAX_VALUE, "receive probe");
        }
    }

    private boolean refreshHandlers(BlockEntity target) {
        var level = target.getLevel();
        if (level == null) {
            return false;
        }
        Direction previousSendDirection = sendDirection;
        Direction previousReceiveDirection = receiveDirection;
        Direction previousCapabilityDirection = capabilityDirection;
        clearBlockTickState();
        var position = target.getBlockPos();
        boolean foundCapability = probeDirection(level, position, previousSendDirection);
        if ((send == null || receive == null) && previousReceiveDirection != previousSendDirection) {
            foundCapability |= probeDirection(level, position, previousReceiveDirection);
        }
        if ((send == null || receive == null) && previousCapabilityDirection != previousSendDirection
            && previousCapabilityDirection != previousReceiveDirection) {
            foundCapability |= probeDirection(level, position, previousCapabilityDirection);
        }
        for (Direction direction : DIRECTIONS) {
            if (direction == previousSendDirection || direction == previousReceiveDirection
                || direction == previousCapabilityDirection) {
                continue;
            }
            if (send != null && receive != null) {
                break;
            }
            foundCapability |= probeDirection(level, position, direction);
        }
        return foundCapability;
    }

    private boolean probeDirection(Level level, BlockPos position, @Nullable Direction direction) {
        if (direction == null) {
            return false;
        }
        EnergyHandler handler = level.getCapability(Capabilities.Energy.BLOCK, position, direction);
        if (handler == null) {
            return false;
        }
        if (capabilityDirection == null) {
            capabilityDirection = direction;
        }
        if (send == null) {
            int candidateBudget = probeExtract(handler);
            if (candidateBudget > 0) {
                send = handler;
                sendDirection = direction;
                extractBudget = candidateBudget;
            }
        }
        if (receive == null) {
            int candidateBudget = probeReceive(handler);
            if (candidateBudget > 0) {
                receive = handler;
                receiveDirection = direction;
                receiveBudget = candidateBudget;
            }
        }
        return true;
    }

    @Override
    public HandlerBindingPolicy bindingPolicy() {
        return BINDING_POLICY;
    }

    @Override
    public void bindBlockEntity(BlockEntity target, HandlerInvalidationSink invalidationSink) {
        requireUnbound();
        Objects.requireNonNull(invalidationSink, "invalidationSink");
        if (target.getLevel() == null || target.getLevel().isClientSide()) {
            throw new IllegalArgumentException("FE handler requires a server-level block entity");
        }
        if (!refreshHandlers(target)) {
            throw new IllegalArgumentException("FE block entity has no energy capability");
        }
        blockEntity = target;
        energyType = roleOf(extractBudget, receiveBudget);
    }

    @Override
    public HandlerTickResult beginServerTick(long epoch) {
        BlockEntity target = Objects.requireNonNull(blockEntity, "FE handler has no block binding");
        EnergyType previousType = energyType;
        if (!refreshHandlers(target)) {
            energyType = EnergyType.INVALID;
            return HandlerTickResult.SUSPEND_UNTIL_REBIND;
        }
        energyType = roleOf(extractBudget, receiveBudget);
        return energyType == previousType ? HandlerTickResult.UNCHANGED : HandlerTickResult.STATE_CHANGED;
    }

    @Override
    public void endServerTick(long epoch) {
        throw new IllegalStateException("BEGIN_TICK FE handler does not receive end callbacks");
    }

    @Override
    public void unbindBlockEntity() {
        clearState();
        blockEntity = null;
    }

    @Override
    public void bindItem(ItemStack itemStack, @Nullable HubNode.HubMetadata hubMetadata) {
        requireUnbound();
        Objects.requireNonNull(itemStack, "itemStack");
        itemBound = true;
        if (itemStack.isEmpty()) {
            return;
        }
        EnergyHandler handler = ItemAccess.forStack(itemStack).getCapability(Capabilities.Energy.ITEM);
        if (handler == null) {
            return;
        }
        int candidateBudget = probeReceive(handler);
        if (candidateBudget > 0) {
            receive = handler;
            receiveBudget = candidateBudget;
            energyType = EnergyType.RECEIVE;
        }
    }

    @Override
    public void unbindItem() {
        clearState();
        itemBound = false;
    }

    @Override
    public EnergyAmount extractEnergy(EnergyAmount maxExtract, @Nullable HubNode.HubMetadata hubMetadata) {
        EnergyHandler handler = send;
        if (handler == null) return EnergyAmounts.ZERO;
        int amount = maxExtract.intValue();
        try (Transaction transaction = Transaction.openRoot()) {
            int extracted = requireValidAmount(handler.extract(amount, transaction), amount, "extract");
            transaction.commit();
            return EnergyAmount.obtain(extracted);
        }
    }

    @Override
    public EnergyAmount receiveEnergy(EnergyAmount maxReceive, @Nullable HubNode.HubMetadata hubMetadata) {
        EnergyHandler handler = receive;
        if (handler == null) return EnergyAmounts.ZERO;
        int amount = maxReceive.intValue();
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = requireValidAmount(handler.insert(amount, transaction), amount, "receive");
            transaction.commit();
            return EnergyAmount.obtain(inserted);
        }
    }

    @Override
    public EnergyAmount canExtractValue(@Nullable HubNode.HubMetadata hubMetadata) {
        return extractBudget <= 0 ? EnergyAmounts.ZERO : EnergyAmount.obtain(extractBudget);
    }

    @Override
    public EnergyAmount canReceiveValue(@Nullable HubNode.HubMetadata hubMetadata) {
        return receiveBudget <= 0 ? EnergyAmounts.ZERO : EnergyAmount.obtain(receiveBudget);
    }

    @Override
    public EnergyType getType(@Nullable HubNode.HubMetadata hubMetadata) {
        return energyType;
    }

    @Override
    public boolean canExtract(IEnergyHandler receiveHandler, @Nullable HubNode.HubMetadata hubMetadata) {
        return energyType == EnergyType.SEND || energyType == EnergyType.STORAGE;
    }

    @Override
    public boolean canReceive(IEnergyHandler sendHandler, @Nullable HubNode.HubMetadata hubMetadata) {
        return energyType == EnergyType.RECEIVE || energyType == EnergyType.STORAGE;
    }

    private static EnergyType roleOf(int extract, int receive) {
        if (extract > 0) {
            return receive > 0 ? EnergyType.STORAGE : EnergyType.SEND;
        }
        return receive > 0 ? EnergyType.RECEIVE : EnergyType.INVALID;
    }

    private static int requireValidAmount(int amount, int requested, String operation) {
        if (amount < 0 || amount > requested) {
            throw new IllegalStateException(
                "FE capability returned " + amount + " for " + operation + " request " + requested
            );
        }
        return amount;
    }

    private void clearBlockTickState() {
        send = null;
        receive = null;
        sendDirection = null;
        receiveDirection = null;
        capabilityDirection = null;
        extractBudget = 0;
        receiveBudget = 0;
    }

    private void clearState() {
        clearBlockTickState();
        energyType = EnergyType.INVALID;
    }

    private void requireUnbound() {
        if (blockEntity != null || itemBound) {
            throw new IllegalStateException("FE handler is already bound");
        }
    }
}
