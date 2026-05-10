package com.circulation.circulation_networks.energy.handler;

import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.EnergyAmounts;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.network.nodes.HubNode;
import com.circulation.circulation_networks.utils.EnergyAmountConversionUtils;
import mekanism.api.Action;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.api.math.FloatingLong;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.tile.TileEntityEnergyCube;
import mekanism.common.tile.multiblock.TileEntityInductionPort;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;

public class MEKHandler implements IEnergyHandler {

    private static final Direction[] DIRECTIONS = Direction.values();
    private static final double FE_TO_MEK_RATIO = 2.5D;
    private static final BigInteger MAX_DIRECT_DOUBLE_TRANSFER = BigDecimal.valueOf(Double.MAX_VALUE).toBigInteger();
    private static final BigInteger MAX_SCALED_DOUBLE_TRANSFER = BigDecimal.valueOf(Double.MAX_VALUE / FE_TO_MEK_RATIO).toBigInteger();

    private final EnergyAmount maxExtract = EnergyAmount.obtain(0L);
    private final EnergyAmount maxReceive = EnergyAmount.obtain(0L);
    private final EnergyAmount needEnergy = EnergyAmount.obtain(0L);
    @Nullable
    private IStrictEnergyHandler send;
    @Nullable
    private IStrictEnergyHandler receive;
    private boolean isItem;
    private EnergyType energyType = EnergyType.INVALID;
    private boolean initialized;

    public MEKHandler() {
    }

    private static void clampToMaximum(EnergyAmount amount) {
        if (amount == null || !amount.isInitialized() || amount.isNegative()) {
            return;
        }
        if (amount.asBigInteger().compareTo(MEKHandler.MAX_DIRECT_DOUBLE_TRANSFER) > 0) {
            amount.init(MEKHandler.MAX_DIRECT_DOUBLE_TRANSFER);
        }
    }

    private static double joulesToFe(double joules) {
        return joules / FE_TO_MEK_RATIO;
    }

    private static double getStoredEnergy(IStrictEnergyHandler handler) {
        double total = 0.0D;
        for (int i = 0, count = handler.getEnergyContainerCount(); i < count; i++) {
            total += handler.getEnergy(i).doubleValue();
        }
        return total;
    }

    private static double getMaxStoredEnergy(IStrictEnergyHandler handler) {
        double total = 0.0D;
        for (int i = 0, count = handler.getEnergyContainerCount(); i < count; i++) {
            total += handler.getMaxEnergy(i).doubleValue();
        }
        return total;
    }

    private void bindHandler(@Nullable IStrictEnergyHandler handler) {
        if (handler == null) {
            return;
        }
        if (send == null && !handler.extractEnergy(FloatingLong.ONE, Action.SIMULATE).isZero()) {
            send = handler;
        }
        if (receive == null && !handler.insertEnergy(FloatingLong.ONE, Action.SIMULATE).equals(FloatingLong.ONE)) {
            receive = handler;
        }
    }

    @Override
    public IEnergyHandler init(BlockEntity blockEntity, @Nullable HubNode.HubMetadata hubMetadata) {
        if (initialized) {
            return this;
        }
        initialized = true;
        maxExtract.setZero();
        maxReceive.setZero();
        if (blockEntity instanceof TileEntityEnergyCube energyCube) {
            send = energyCube;
            receive = energyCube;
            energyType = EnergyType.STORAGE;
        } else if (blockEntity instanceof TileEntityInductionPort port) {
            send = port;
            receive = port;
            energyType = EnergyType.STORAGE;
        } else {
            for (Direction direction : DIRECTIONS) {
                if (send != null && receive != null) {
                    break;
                }
                var optional = blockEntity.getCapability(Capabilities.STRICT_ENERGY, direction);
                if (!optional.isPresent()) {
                    continue;
                }
                bindHandler(optional.orElse(null));
            }
        }
        if (send != null) {
            double probe = send.extractEnergy(FloatingLong.MAX_VALUE, Action.SIMULATE).doubleValue();
            EnergyAmountConversionUtils.setFromDoubleFloor(maxExtract, joulesToFe(probe));
        }
        if (receive != null) {
            FloatingLong remainder = receive.insertEnergy(FloatingLong.MAX_VALUE, Action.SIMULATE);
            double accepted = FloatingLong.MAX_VALUE.subtract(remainder).doubleValue();
            EnergyAmountConversionUtils.setFromDoubleFloor(maxReceive, joulesToFe(accepted));
        }
        if (send != null) {
            energyType = receive != null ? EnergyType.STORAGE : EnergyType.SEND;
        } else if (receive != null) {
            energyType = EnergyType.RECEIVE;
        }
        return this;
    }

    @Override
    public IEnergyHandler init(ItemStack itemStack, @Nullable HubNode.HubMetadata hubMetadata) {
        isItem = true;
        var optional = itemStack.getCapability(Capabilities.STRICT_ENERGY);
        if (optional.isPresent()) {
            IStrictEnergyHandler handler = optional.orElse(null);
            if (handler != null) {
                receive = handler;
                double stored = getStoredEnergy(handler);
                double max = getMaxStoredEnergy(handler);
                double remaining = max - stored;
                FloatingLong insertRemainder = handler.insertEnergy(FloatingLong.MAX_VALUE, Action.SIMULATE);
                double maxInsert = FloatingLong.MAX_VALUE.subtract(insertRemainder).doubleValue();
                EnergyAmountConversionUtils.setFromDoubleFloor(needEnergy, joulesToFe(Math.max(0.0D, Math.min(remaining, maxInsert))));
            }
        }
        energyType = EnergyType.RECEIVE;
        return this;
    }

    @Override
    public void clear() {
        maxExtract.setZero();
        maxReceive.setZero();
        send = null;
        receive = null;
        energyType = EnergyType.INVALID;
        isItem = false;
        initialized = false;
        needEnergy.setZero();
    }

    @Override
    public EnergyAmount receiveEnergy(EnergyAmount maxReceive, @Nullable HubNode.HubMetadata hubMetadata) {
        if (isItem) {
            if (receive == null) return EnergyAmounts.ZERO;
            EnergyAmount accepted = EnergyAmount.obtain(needEnergy).min(maxReceive);
            clampToMaximum(accepted);
            if (accepted.isZero()) {
                return accepted;
            }
            double requestedJoules = EnergyAmountConversionUtils.toDoubleClamped(accepted) * FE_TO_MEK_RATIO;
            FloatingLong remainder = receive.insertEnergy(
                FloatingLong.create(requestedJoules),
                Action.EXECUTE
            );
            double inserted = requestedJoules - remainder.doubleValue();
            if (inserted <= 0.0D) {
                accepted.recycle();
                return EnergyAmounts.ZERO;
            }
            needEnergy.subtract(accepted);
            return accepted;
        }
        if (receive == null) return EnergyAmounts.ZERO;
        double requestJoules = EnergyAmountConversionUtils.toDoubleClamped(maxReceive) * FE_TO_MEK_RATIO;
        FloatingLong remainder = receive.insertEnergy(FloatingLong.create(requestJoules), Action.EXECUTE);
        double insertedJoules = requestJoules - remainder.doubleValue();
        return EnergyAmountConversionUtils.obtainFromDoubleFloor(insertedJoules / FE_TO_MEK_RATIO);
    }

    @Override
    public EnergyAmount extractEnergy(EnergyAmount maxExtract, @Nullable HubNode.HubMetadata hubMetadata) {
        if (send == null) return EnergyAmounts.ZERO;
        double requestJoules = EnergyAmountConversionUtils.toDoubleClamped(maxExtract) * FE_TO_MEK_RATIO;
        FloatingLong extracted = send.extractEnergy(FloatingLong.create(requestJoules), Action.EXECUTE);
        return EnergyAmountConversionUtils.obtainFromDoubleFloor(extracted.doubleValue() / FE_TO_MEK_RATIO);
    }

    @Override
    public EnergyAmount canExtractValue(@Nullable HubNode.HubMetadata hubMetadata) {
        if (send == null) return EnergyAmounts.ZERO;
        EnergyAmount extractable = EnergyAmountConversionUtils.obtainFromDoubleFloor(getStoredEnergy(send) * 0.4D);
        return extractable.min(maxExtract);
    }

    @Override
    public EnergyAmount canReceiveValue(@Nullable HubNode.HubMetadata hubMetadata) {
        if (isItem) {
            return EnergyAmount.obtain(needEnergy);
        }
        if (receive == null) return EnergyAmounts.ZERO;
        EnergyAmount receivable = EnergyAmountConversionUtils.obtainFromDoubleFloor(
            (getMaxStoredEnergy(receive) - getStoredEnergy(receive)) * 0.4D
        );
        return receivable.min(maxReceive);
    }

    @Override
    public boolean canExtract(IEnergyHandler receiveHandler, @Nullable HubNode.HubMetadata hubMetadata) {
        return send != null && getStoredEnergy(send) >= 2.5D;
    }

    @Override
    public boolean canReceive(IEnergyHandler sendHandler, @Nullable HubNode.HubMetadata hubMetadata) {
        if (isItem) return needEnergy.isPositive();
        return receive != null && (getMaxStoredEnergy(receive) - getStoredEnergy(receive)) * 0.4D > 0.0D;
    }

    @Override
    public EnergyType getType(@Nullable HubNode.HubMetadata hubMetadata) {
        return energyType;
    }
}
