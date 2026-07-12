package com.circulation.circulation_networks.energy.handler;

import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.EnergyAmounts;
import com.circulation.circulation_networks.api.HandlerTickResult;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.manager.HandlerBindingPolicy;
import com.circulation.circulation_networks.manager.HandlerInvalidationSink;
import com.circulation.circulation_networks.network.nodes.HubNode;
import com.circulation.circulation_networks.utils.EnergyAmountConversionUtils;
import mekanism.api.Action;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.api.math.FloatingLong;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.component.config.slot.ISlotInfo;
import mekanism.common.tile.multiblock.TileEntityInductionPort;
import mekanism.common.tile.prefab.TileEntityConfigurableMachine;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

/** Mekanism energy binding with structure-derived roles. */
public final class MEKHandler implements IEnergyHandler {

    private static final Direction[] DIRECTIONS = Direction.values();
    private static final HandlerBindingPolicy STATIC_POLICY = HandlerBindingPolicy.of(
        HandlerBindingPolicy.TickLifecycle.STATIC,
        HandlerBindingPolicy.RoleScope.FIXED,
        HandlerBindingPolicy.MappingScope.NONE,
        HandlerBindingPolicy.PairMatching.NONE
    );
    private static final HandlerBindingPolicy INDUCTION_PORT_POLICY = HandlerBindingPolicy.of(
        HandlerBindingPolicy.TickLifecycle.BEGIN_TICK,
        HandlerBindingPolicy.RoleScope.RUNTIME_DYNAMIC,
        HandlerBindingPolicy.MappingScope.NONE,
        HandlerBindingPolicy.PairMatching.NONE
    );
    private static final double FE_TO_MEK_RATIO = 2.5D;
    private static final BigInteger MAX_DIRECT_DOUBLE_TRANSFER =
        BigDecimal.valueOf(Double.MAX_VALUE).toBigInteger();

    private final EnergyAmount needEnergy = EnergyAmount.obtain(0L);
    @Nullable
    private IStrictEnergyHandler send;
    @Nullable
    private IStrictEnergyHandler receive;
    @Nullable
    private IStrictEnergyHandler inductionCapability;
    @Nullable
    private BlockEntity blockEntity;
    private HandlerBindingPolicy bindingPolicy = STATIC_POLICY;
    private boolean itemBound;
    private EnergyType energyType = EnergyType.INVALID;

    @Override
    public HandlerBindingPolicy bindingPolicy() {
        return bindingPolicy;
    }

    @Override
    public void bindBlockEntity(BlockEntity blockEntity, HandlerInvalidationSink invalidationSink) {
        if (this.blockEntity != null || itemBound) {
            throw new IllegalStateException("Mekanism handler is already bound");
        }
        this.blockEntity = Objects.requireNonNull(blockEntity, "blockEntity");
        Objects.requireNonNull(invalidationSink, "invalidationSink");
        bindingPolicy = blockEntity instanceof TileEntityInductionPort
            ? INDUCTION_PORT_POLICY
            : STATIC_POLICY;

        boolean foundCapability = false;
        for (Direction direction : DIRECTIONS) {
            foundCapability |= bindCapability(blockEntity, direction, invalidationSink);
        }
        if (!foundCapability) {
            bindCapability(blockEntity, null, invalidationSink);
        }

        if (blockEntity instanceof TileEntityInductionPort port) {
            if (inductionCapability == null) {
                throw new IllegalArgumentException("Mekanism induction port has no usable energy capability");
            }
            applyInductionPortRole(port);
        } else {
            energyType = structuralType();
            if (energyType == EnergyType.INVALID) {
                throw new IllegalArgumentException("Mekanism block entity has no usable energy capability");
            }
        }
    }

    private boolean bindCapability(BlockEntity blockEntity,
                                   @Nullable Direction direction,
                                   HandlerInvalidationSink invalidationSink) {
        var capability = blockEntity.getCapability(Capabilities.STRICT_ENERGY, direction);
        if (!capability.isPresent()) {
            return false;
        }
        capability.addListener(ignored -> invalidationSink.suspendUntilRebind());
        IStrictEnergyHandler handler = capability.orElseThrow(IllegalStateException::new);

        if (blockEntity instanceof TileEntityInductionPort) {
            if (inductionCapability == null) {
                inductionCapability = handler;
            }
            return true;
        }

        boolean canInput = true;
        boolean canOutput = true;
        if (direction != null && blockEntity instanceof TileEntityConfigurableMachine configurable) {
            ISlotInfo slotInfo = configurable.getConfig().getSlotInfo(TransmissionType.ENERGY, direction);
            canInput = slotInfo != null && slotInfo.isEnabled() && slotInfo.canInput();
            canOutput = slotInfo != null && slotInfo.isEnabled() && slotInfo.canOutput();
        }
        if (send == null && canOutput) {
            send = handler;
        }
        if (receive == null && canInput) {
            receive = handler;
        }
        return true;
    }

    @Override
    public HandlerTickResult beginServerTick(long epoch) {
        BlockEntity binding = requireBlockBinding();
        if (binding instanceof TileEntityInductionPort port) {
            if (inductionCapability == null) {
                energyType = EnergyType.INVALID;
                return HandlerTickResult.SUSPEND_UNTIL_REBIND;
            }
            EnergyType previous = energyType;
            applyInductionPortRole(port);
            return energyType == previous ? HandlerTickResult.UNCHANGED : HandlerTickResult.STATE_CHANGED;
        }
        throw new IllegalStateException("STATIC Mekanism handler does not receive tick callbacks");
    }

    @Override
    public void endServerTick(long epoch) {
        throw new IllegalStateException("Mekanism handler does not use end tick callbacks");
    }

    @Override
    public void unbindBlockEntity() {
        send = null;
        receive = null;
        inductionCapability = null;
        energyType = EnergyType.INVALID;
        bindingPolicy = STATIC_POLICY;
        blockEntity = null;
    }

    @Override
    public void bindItem(ItemStack itemStack, @Nullable HubNode.HubMetadata hubMetadata) {
        if (blockEntity != null || itemBound) {
            throw new IllegalStateException("Mekanism handler is already bound");
        }
        itemBound = true;
        var capability = itemStack.getCapability(Capabilities.STRICT_ENERGY);
        if (capability.isPresent()) {
            receive = capability.orElseThrow(IllegalStateException::new);
            refreshItemBudget();
            energyType = EnergyType.RECEIVE;
        }
    }

    @Override
    public void unbindItem() {
        send = null;
        receive = null;
        energyType = EnergyType.INVALID;
        itemBound = false;
        needEnergy.setZero();
    }

    @Override
    public EnergyAmount receiveEnergy(EnergyAmount maxReceive, @Nullable HubNode.HubMetadata hubMetadata) {
        if (receive == null) {
            return EnergyAmounts.ZERO;
        }
        if (itemBound) {
            EnergyAmount accepted = EnergyAmount.obtain(needEnergy).min(maxReceive);
            clampToMaximum(accepted);
            if (accepted.isZero()) {
                return accepted;
            }
            EnergyAmount actual = insertEnergy(receive, accepted);
            needEnergy.subtract(actual);
            accepted.recycle();
            return actual;
        }
        return insertEnergy(receive, maxReceive);
    }

    @Override
    public EnergyAmount extractEnergy(EnergyAmount maxExtract, @Nullable HubNode.HubMetadata hubMetadata) {
        if (send == null) {
            return EnergyAmounts.ZERO;
        }
        double requestedJoules = EnergyAmountConversionUtils.toDoubleClamped(maxExtract) * FE_TO_MEK_RATIO;
        FloatingLong extracted = send.extractEnergy(FloatingLong.create(requestedJoules), Action.EXECUTE);
        return EnergyAmountConversionUtils.obtainFromDoubleFloor(extracted.doubleValue() / FE_TO_MEK_RATIO);
    }

    @Override
    public EnergyAmount canExtractValue(@Nullable HubNode.HubMetadata hubMetadata) {
        if (send == null) {
            return EnergyAmounts.ZERO;
        }
        double extracted = send.extractEnergy(FloatingLong.MAX_VALUE, Action.SIMULATE).doubleValue();
        return EnergyAmountConversionUtils.obtainFromDoubleFloor(extracted / FE_TO_MEK_RATIO);
    }

    @Override
    public EnergyAmount canReceiveValue(@Nullable HubNode.HubMetadata hubMetadata) {
        if (itemBound) {
            return EnergyAmount.obtain(needEnergy);
        }
        if (receive == null) {
            return EnergyAmounts.ZERO;
        }
        FloatingLong remainder = receive.insertEnergy(FloatingLong.MAX_VALUE, Action.SIMULATE);
        double accepted = FloatingLong.MAX_VALUE.subtract(remainder).doubleValue();
        return EnergyAmountConversionUtils.obtainFromDoubleFloor(accepted / FE_TO_MEK_RATIO);
    }

    @Override
    public boolean canExtract(IEnergyHandler receiveHandler, @Nullable HubNode.HubMetadata hubMetadata) {
        return send != null && !send.extractEnergy(FloatingLong.ONE, Action.SIMULATE).isZero();
    }

    @Override
    public boolean canReceive(IEnergyHandler sendHandler, @Nullable HubNode.HubMetadata hubMetadata) {
        if (itemBound) {
            return needEnergy.isPositive();
        }
        return receive != null
            && !receive.insertEnergy(FloatingLong.ONE, Action.SIMULATE).equals(FloatingLong.ONE);
    }

    @Override
    public EnergyType getType(@Nullable HubNode.HubMetadata hubMetadata) {
        return energyType;
    }

    private void applyInductionPortRole(TileEntityInductionPort port) {
        if (inductionCapability == null) {
            send = null;
            receive = null;
            energyType = EnergyType.INVALID;
        } else if (port.getActive()) {
            send = inductionCapability;
            receive = null;
            energyType = EnergyType.SEND;
        } else {
            send = null;
            receive = inductionCapability;
            energyType = EnergyType.RECEIVE;
        }
    }

    private void refreshItemBudget() {
        if (receive == null) {
            needEnergy.setZero();
            return;
        }
        FloatingLong remainder = receive.insertEnergy(FloatingLong.MAX_VALUE, Action.SIMULATE);
        double accepted = FloatingLong.MAX_VALUE.subtract(remainder).doubleValue();
        EnergyAmountConversionUtils.setFromDoubleFloor(needEnergy, accepted / FE_TO_MEK_RATIO);
    }

    private static EnergyAmount insertEnergy(IStrictEnergyHandler handler, EnergyAmount maximum) {
        double requestedJoules = EnergyAmountConversionUtils.toDoubleClamped(maximum) * FE_TO_MEK_RATIO;
        FloatingLong remainder = handler.insertEnergy(FloatingLong.create(requestedJoules), Action.EXECUTE);
        double inserted = requestedJoules - remainder.doubleValue();
        return EnergyAmountConversionUtils.obtainFromDoubleFloor(Math.max(0.0D, inserted) / FE_TO_MEK_RATIO);
    }

    private EnergyType structuralType() {
        if (send != null) {
            return receive != null ? EnergyType.STORAGE : EnergyType.SEND;
        }
        return receive != null ? EnergyType.RECEIVE : EnergyType.INVALID;
    }

    private BlockEntity requireBlockBinding() {
        if (blockEntity == null) {
            throw new IllegalStateException("Mekanism handler has no block-entity binding");
        }
        return blockEntity;
    }

    private static void clampToMaximum(EnergyAmount amount) {
        if (!amount.isInitialized() || amount.isNegative()) {
            throw new IllegalArgumentException("Mekanism transfer amount must be initialized and non-negative");
        }
        if (amount.asBigInteger().compareTo(MAX_DIRECT_DOUBLE_TRANSFER) > 0) {
            amount.init(MAX_DIRECT_DOUBLE_TRANSFER);
        }
    }
}
