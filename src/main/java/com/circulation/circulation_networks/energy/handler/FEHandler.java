package com.circulation.circulation_networks.energy.handler;

import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.EnergyAmounts;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.network.nodes.HubNode;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import org.jetbrains.annotations.Nullable;

public class FEHandler implements IEnergyHandler {

    private static final Direction[] DIRECTIONS = Direction.values();
    private static final int ROLE_UNKNOWN = 0;
    private static final int ROLE_SUPPORTED = 1;
    private static final int ROLE_UNSUPPORTED = 2;

    @Nullable
    private EnergyHandler send;
    @Nullable
    private EnergyHandler receive;
    @Nullable
    private Direction sendDirection;
    @Nullable
    private Direction receiveDirection;
    private EnergyType energyType;
    private boolean initialized;
    private int sendState = ROLE_UNKNOWN;
    private int receiveState = ROLE_UNKNOWN;

    private static boolean simulateExtract(EnergyHandler handler) {
        try (Transaction transaction = Transaction.openRoot()) {
            return handler.extract(1, transaction) > 0;
        }
    }

    private static int simulateExtractAmount(EnergyHandler handler) {
        if (!canExtractNow(handler)) return 0;
        try (Transaction transaction = Transaction.openRoot()) {
            return handler.extract(Integer.MAX_VALUE, transaction);
        }
    }

    private static boolean simulateInsert(EnergyHandler handler) {
        try (Transaction transaction = Transaction.openRoot()) {
            return handler.insert(1, transaction) > 0;
        }
    }

    private static int simulateInsertAmount(EnergyHandler handler) {
        if (!canReceiveNow(handler)) return 0;
        try (Transaction transaction = Transaction.openRoot()) {
            return handler.insert(Integer.MAX_VALUE, transaction);
        }
    }

    private static boolean canExtractNow(EnergyHandler handler) {
        return handler.getAmountAsLong() > 0L;
    }

    private static boolean canReceiveNow(EnergyHandler handler) {
        long capacity = handler.getCapacityAsLong();
        return capacity > 0L && handler.getAmountAsLong() < capacity;
    }

    private void bindSend(EnergyHandler storage, Direction direction, boolean proveRole) {
        if (sendState == ROLE_SUPPORTED) {
            if (canExtractNow(storage)) {
                send = storage;
            }
            return;
        }
        if (sendState == ROLE_UNSUPPORTED) {
            return;
        }
        if (!canExtractNow(storage)) {
            if (sendDirection == null) {
                sendDirection = direction;
            }
            return;
        }
        if (proveRole && simulateExtract(storage)) {
            sendDirection = direction;
            sendState = ROLE_SUPPORTED;
            send = storage;
        }
    }

    private void bindReceive(EnergyHandler storage, Direction direction, boolean proveRole) {
        if (receiveState == ROLE_SUPPORTED) {
            if (canReceiveNow(storage)) {
                receive = storage;
            }
            return;
        }
        if (receiveState == ROLE_UNSUPPORTED) {
            return;
        }
        if (!canReceiveNow(storage)) {
            if (receiveDirection == null) {
                receiveDirection = direction;
            }
            return;
        }
        if (proveRole && simulateInsert(storage)) {
            receiveDirection = direction;
            receiveState = ROLE_SUPPORTED;
            receive = storage;
        }
    }

    private void bindHintedHandlers(BlockEntity blockEntity) {
        var level = blockEntity.getLevel();
        if (level == null) return;
        var pos = blockEntity.getBlockPos();
        if (sendDirection != null) {
            var storage = level.getCapability(Capabilities.Energy.BLOCK, pos, sendDirection);
            if (storage == null) {
                sendState = ROLE_UNKNOWN;
            } else {
                bindSend(storage, sendDirection, sendState == ROLE_UNKNOWN);
            }
        }
        if (receiveDirection != null) {
            var storage = level.getCapability(Capabilities.Energy.BLOCK, pos, receiveDirection);
            if (storage == null) {
                receiveState = ROLE_UNKNOWN;
            } else {
                bindReceive(storage, receiveDirection, receiveState == ROLE_UNKNOWN);
            }
        }
    }

    private void scanHandlers(BlockEntity blockEntity) {
        var level = blockEntity.getLevel();
        if (level == null) return;
        var pos = blockEntity.getBlockPos();
        boolean attemptedSend = false;
        boolean attemptedReceive = false;
        for (Direction direction : DIRECTIONS) {
            boolean needSend = send == null && sendState == ROLE_UNKNOWN;
            boolean needReceive = receive == null && receiveState == ROLE_UNKNOWN;
            if (!needSend && !needReceive) {
                break;
            }
            var storage = level.getCapability(Capabilities.Energy.BLOCK, pos, direction);
            if (storage == null) {
                continue;
            }
            if (needSend && canExtractNow(storage)) {
                attemptedSend = true;
                bindSend(storage, direction, true);
            }
            if (needReceive && canReceiveNow(storage)) {
                attemptedReceive = true;
                bindReceive(storage, direction, true);
            }
        }
        if (send == null && sendState == ROLE_UNKNOWN && attemptedSend) {
            sendState = ROLE_UNSUPPORTED;
        }
        if (receive == null && receiveState == ROLE_UNKNOWN && attemptedReceive) {
            receiveState = ROLE_UNSUPPORTED;
        }
    }

    @Override
    public IEnergyHandler init(BlockEntity blockEntity, @Nullable HubNode.HubMetadata hubMetadata) {
        if (initialized) {
            return this;
        }
        initialized = true;
        bindHintedHandlers(blockEntity);
        if ((send == null && sendState == ROLE_UNKNOWN) || (receive == null && receiveState == ROLE_UNKNOWN)) {
            scanHandlers(blockEntity);
        }
        return this;
    }

    @Override
    public IEnergyHandler init(ItemStack itemStack, @Nullable HubNode.HubMetadata hubMetadata) {
        if (itemStack.isEmpty()) return this;
        var ies = ItemAccess.forStack(itemStack).getCapability(Capabilities.Energy.ITEM);
        if (ies == null) return this;
        initialized = true;
        if (canReceiveNow(ies) && simulateInsert(ies)) {
            this.receive = ies;
        }
        return this;
    }

    @Override
    public void clear() {
        send = null;
        receive = null;
        energyType = null;
        initialized = false;
    }

    @Override
    public EnergyAmount extractEnergy(EnergyAmount maxExtract, @Nullable HubNode.HubMetadata hubMetadata) {
        if (send == null) return EnergyAmounts.ZERO;
        int amount = maxExtract.intValue();
        try (Transaction transaction = Transaction.openRoot()) {
            int extracted = send.extract(amount, transaction);
            transaction.commit();
            if (amount > 0 && extracted == 0) {
                sendState = ROLE_UNKNOWN;
            }
            return EnergyAmount.obtain(extracted);
        }
    }

    @Override
    public EnergyAmount receiveEnergy(EnergyAmount maxReceive, @Nullable HubNode.HubMetadata hubMetadata) {
        if (receive == null) return EnergyAmounts.ZERO;
        int amount = maxReceive.intValue();
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = receive.insert(amount, transaction);
            transaction.commit();
            if (amount > 0 && inserted == 0) {
                receiveState = ROLE_UNKNOWN;
            }
            return EnergyAmount.obtain(inserted);
        }
    }

    @Override
    public EnergyAmount canExtractValue(@Nullable HubNode.HubMetadata hubMetadata) {
        if (send == null) return EnergyAmounts.ZERO;
        int extracted = simulateExtractAmount(send);
        return extracted <= 0 ? EnergyAmounts.ZERO : EnergyAmount.obtain(extracted);
    }

    @Override
    public EnergyAmount canReceiveValue(@Nullable HubNode.HubMetadata hubMetadata) {
        if (receive == null) return EnergyAmounts.ZERO;
        int inserted = simulateInsertAmount(receive);
        return inserted <= 0 ? EnergyAmounts.ZERO : EnergyAmount.obtain(inserted);
    }

    @Override
    public EnergyType getType(@Nullable HubNode.HubMetadata hubMetadata) {
        if (energyType == null) {
            boolean receive = this.receive != null;
            if (send != null) {
                return energyType = receive ? EnergyType.STORAGE : EnergyType.SEND;
            } else if (receive) {
                return energyType = EnergyType.RECEIVE;
            }
            return energyType = EnergyType.INVALID;
        }
        return energyType;
    }

    @Override
    public boolean canExtract(IEnergyHandler receiveHandler, @Nullable HubNode.HubMetadata hubMetadata) {
        return send != null;
    }

    @Override
    public boolean canReceive(IEnergyHandler sendHandler, @Nullable HubNode.HubMetadata hubMetadata) {
        return receive != null;
    }
}
