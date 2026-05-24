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
    private static final int ROLE_UNKNOWN = 0;
    private static final int ROLE_SUPPORTED = 1;
    private static final int ROLE_UNSUPPORTED = 2;
    private static final BigInteger MAX_DIRECT_DOUBLE_TRANSFER = BigDecimal.valueOf(Double.MAX_VALUE).toBigInteger();

    private final EnergyAmount needEnergy = EnergyAmount.obtain(0L);
    @Nullable
    private IStrictEnergyHandler send;
    @Nullable
    private IStrictEnergyHandler receive;
    private boolean isItem;
    private EnergyType energyType = EnergyType.INVALID;
    private boolean initialized;
    @Nullable
    private Direction sendDirection;
    @Nullable
    private Direction receiveDirection;
    private int sendState = ROLE_UNKNOWN;
    private int receiveState = ROLE_UNKNOWN;

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

    private static boolean hasEnergy(IStrictEnergyHandler handler) {
        return getStoredEnergy(handler) >= FE_TO_MEK_RATIO;
    }

    private static boolean hasRoom(IStrictEnergyHandler handler) {
        return (getMaxStoredEnergy(handler) - getStoredEnergy(handler)) * 0.4D > 0.0D;
    }

    private int bindHandler(@Nullable IStrictEnergyHandler handler, Direction direction, boolean needSendScan, boolean needReceiveScan) {
        if (handler == null) {
            return 0;
        }
        int attempted = 0;
        if (needSendScan && hasEnergy(handler)) {
            attempted |= 1;
        }
        if (needReceiveScan && hasRoom(handler)) {
            attempted |= 2;
        }
        if (needSendScan && send == null && !handler.extractEnergy(FloatingLong.ONE, Action.SIMULATE).isZero()) {
            send = handler;
            sendDirection = direction;
            sendState = ROLE_SUPPORTED;
        }
        if (needReceiveScan && receive == null && !handler.insertEnergy(FloatingLong.ONE, Action.SIMULATE).equals(FloatingLong.ONE)) {
            receive = handler;
            receiveDirection = direction;
            receiveState = ROLE_SUPPORTED;
        }
        return attempted;
    }

    private void bindHint(BlockEntity blockEntity) {
        if (sendState == ROLE_SUPPORTED && sendDirection != null) {
            var optional = blockEntity.getCapability(Capabilities.STRICT_ENERGY, sendDirection);
            if (optional.isPresent()) {
                IStrictEnergyHandler handler = optional.orElseThrow(IllegalStateException::new);
                if (hasEnergy(handler)) {
                    send = handler;
                }
            } else {
                sendState = ROLE_UNKNOWN;
            }
        }
        if (receiveState == ROLE_SUPPORTED && receiveDirection != null) {
            var optional = blockEntity.getCapability(Capabilities.STRICT_ENERGY, receiveDirection);
            if (optional.isPresent()) {
                IStrictEnergyHandler handler = optional.orElseThrow(IllegalStateException::new);
                if (hasRoom(handler)) {
                    receive = handler;
                }
            } else {
                receiveState = ROLE_UNKNOWN;
            }
        }
    }

    @Override
    public void init(BlockEntity blockEntity, @Nullable HubNode.HubMetadata hubMetadata) {
        if (initialized) {
            return;
        }
        initialized = true;
        if (blockEntity instanceof TileEntityEnergyCube energyCube) {
            send = energyCube;
            receive = energyCube;
            energyType = EnergyType.STORAGE;
            return;
        } else if (blockEntity instanceof TileEntityInductionPort port) {
            send = port;
            receive = port;
            energyType = EnergyType.STORAGE;
            return;
        } else {
            bindHint(blockEntity);
            boolean needSendScan = send == null && sendState == ROLE_UNKNOWN;
            boolean needReceiveScan = receive == null && receiveState == ROLE_UNKNOWN;
            boolean attemptedSend = false;
            boolean attemptedReceive = false;
            for (Direction direction : DIRECTIONS) {
                if (!needSendScan && !needReceiveScan) {
                    break;
                }
                var optional = blockEntity.getCapability(Capabilities.STRICT_ENERGY, direction);
                if (!optional.isPresent()) {
                    continue;
                }
                int attempted = bindHandler(optional.orElseThrow(IllegalStateException::new), direction, needSendScan, needReceiveScan);
                attemptedSend |= (attempted & 1) != 0;
                attemptedReceive |= (attempted & 2) != 0;
                needSendScan = send == null && sendState == ROLE_UNKNOWN;
                needReceiveScan = receive == null && receiveState == ROLE_UNKNOWN;
            }
            if (send == null && sendState == ROLE_UNKNOWN && attemptedSend) {
                sendState = ROLE_UNSUPPORTED;
            }
            if (receive == null && receiveState == ROLE_UNKNOWN && attemptedReceive) {
                receiveState = ROLE_UNSUPPORTED;
            }
        }
        if (send != null) {
            energyType = receive != null ? EnergyType.STORAGE : EnergyType.SEND;
        } else if (receive != null) {
            energyType = EnergyType.RECEIVE;
        }
    }

    @Override
    public void init(ItemStack itemStack, @Nullable HubNode.HubMetadata hubMetadata) {
        isItem = true;
        var optional = itemStack.getCapability(Capabilities.STRICT_ENERGY);
        if (optional.isPresent()) {
            IStrictEnergyHandler handler = optional.orElseThrow(IllegalStateException::new);
            receive = handler;
            double stored = getStoredEnergy(handler);
            double max = getMaxStoredEnergy(handler);
            double remaining = max - stored;
            FloatingLong insertRemainder = handler.insertEnergy(FloatingLong.MAX_VALUE, Action.SIMULATE);
            double maxInsert = FloatingLong.MAX_VALUE.subtract(insertRemainder).doubleValue();
            EnergyAmountConversionUtils.setFromDoubleFloor(needEnergy, joulesToFe(Math.max(0.0D, Math.min(remaining, maxInsert))));
        }
        energyType = EnergyType.RECEIVE;
    }

    @Override
    public void clear() {
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
                receiveState = ROLE_UNKNOWN;
                return EnergyAmounts.ZERO;
            }
            EnergyAmount actual = EnergyAmountConversionUtils.obtainFromDoubleFloor(inserted / FE_TO_MEK_RATIO);
            actual.min(accepted);
            needEnergy.subtract(actual);
            accepted.recycle();
            return actual;
        }
        if (receive == null) return EnergyAmounts.ZERO;
        double requestJoules = EnergyAmountConversionUtils.toDoubleClamped(maxReceive) * FE_TO_MEK_RATIO;
        FloatingLong remainder = receive.insertEnergy(FloatingLong.create(requestJoules), Action.EXECUTE);
        double insertedJoules = requestJoules - remainder.doubleValue();
        if (insertedJoules <= 0.0D && maxReceive.isPositive()) {
            receiveState = ROLE_UNKNOWN;
        }
        return EnergyAmountConversionUtils.obtainFromDoubleFloor(insertedJoules / FE_TO_MEK_RATIO);
    }

    @Override
    public EnergyAmount extractEnergy(EnergyAmount maxExtract, @Nullable HubNode.HubMetadata hubMetadata) {
        if (send == null) return EnergyAmounts.ZERO;
        double requestJoules = EnergyAmountConversionUtils.toDoubleClamped(maxExtract) * FE_TO_MEK_RATIO;
        FloatingLong extracted = send.extractEnergy(FloatingLong.create(requestJoules), Action.EXECUTE);
        if (extracted.isZero() && maxExtract.isPositive()) {
            sendState = ROLE_UNKNOWN;
        }
        return EnergyAmountConversionUtils.obtainFromDoubleFloor(extracted.doubleValue() / FE_TO_MEK_RATIO);
    }

    @Override
    public EnergyAmount canExtractValue(@Nullable HubNode.HubMetadata hubMetadata) {
        if (send == null) return EnergyAmounts.ZERO;
        double extracted = send.extractEnergy(FloatingLong.MAX_VALUE, Action.SIMULATE).doubleValue();
        return EnergyAmountConversionUtils.obtainFromDoubleFloor(joulesToFe(extracted));
    }

    @Override
    public EnergyAmount canReceiveValue(@Nullable HubNode.HubMetadata hubMetadata) {
        if (isItem) {
            return EnergyAmount.obtain(needEnergy);
        }
        if (receive == null) return EnergyAmounts.ZERO;
        FloatingLong remainder = receive.insertEnergy(FloatingLong.MAX_VALUE, Action.SIMULATE);
        double accepted = FloatingLong.MAX_VALUE.subtract(remainder).doubleValue();
        return EnergyAmountConversionUtils.obtainFromDoubleFloor(joulesToFe(accepted));
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
