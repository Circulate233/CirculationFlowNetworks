package com.circulation.circulation_networks.energy.handler;

import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.EnergyAmounts;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.network.nodes.HubNode;
import com.circulation.circulation_networks.utils.EnergyAmountConversionUtils;
import mekanism.api.Action;
import mekanism.api.energy.IStrictEnergyHandler;
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
            total += (double) handler.getEnergy(i);
        }
        return total;
    }

    private static double getMaxStoredEnergy(IStrictEnergyHandler handler) {
        double total = 0.0D;
        for (int i = 0, count = handler.getEnergyContainerCount(); i < count; i++) {
            total += (double) handler.getMaxEnergy(i);
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
        if (needSendScan && send == null && handler.extractEnergy(1L, Action.SIMULATE) > 0L) {
            send = handler;
            sendDirection = direction;
            sendState = ROLE_SUPPORTED;
        }
        if (needReceiveScan && receive == null && handler.insertEnergy(1L, Action.SIMULATE) == 0L) {
            receive = handler;
            receiveDirection = direction;
            receiveState = ROLE_SUPPORTED;
        }
        return attempted;
    }

    private void bindHint(BlockEntity blockEntity) {
        var level = blockEntity.getLevel();
        if (level == null) {
            return;
        }
        var pos = blockEntity.getBlockPos();
        if (sendState == ROLE_SUPPORTED && sendDirection != null) {
            IStrictEnergyHandler handler = level.getCapability(Capabilities.STRICT_ENERGY.block(), pos, sendDirection);
            if (handler != null && hasEnergy(handler)) {
                send = handler;
            } else if (handler == null) {
                sendState = ROLE_UNKNOWN;
            }
        }
        if (receiveState == ROLE_SUPPORTED && receiveDirection != null) {
            IStrictEnergyHandler handler = level.getCapability(Capabilities.STRICT_ENERGY.block(), pos, receiveDirection);
            if (handler != null && hasRoom(handler)) {
                receive = handler;
            } else if (handler == null) {
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
        var level = blockEntity.getLevel();
        if (level == null) {
            return;
        }
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
            var pos = blockEntity.getBlockPos();
            bindHint(blockEntity);
            boolean needSendScan = send == null && sendState == ROLE_UNKNOWN;
            boolean needReceiveScan = receive == null && receiveState == ROLE_UNKNOWN;
            boolean attemptedSend = false;
            boolean attemptedReceive = false;
            for (Direction direction : DIRECTIONS) {
                if (!needSendScan && !needReceiveScan) {
                    break;
                }
                IStrictEnergyHandler handler = level.getCapability(Capabilities.STRICT_ENERGY.block(), pos, direction);
                if (handler == null) {
                    continue;
                }
                int attempted = bindHandler(handler, direction, needSendScan, needReceiveScan);
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
        IStrictEnergyHandler handler = itemStack.getCapability(Capabilities.STRICT_ENERGY.item());
        if (handler != null) {
            receive = handler;
            double stored = getStoredEnergy(handler);
            double max = getMaxStoredEnergy(handler);
            double remaining = max - stored;
            long insertRemainder = handler.insertEnergy(Long.MAX_VALUE, Action.SIMULATE);
            double maxInsert = (double) (Long.MAX_VALUE - insertRemainder);
            EnergyAmountConversionUtils.setFromDoubleFloor(needEnergy, joulesToFe(Math.clamp(remaining, 0.0D, maxInsert)));
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
            long requested = (long) (EnergyAmountConversionUtils.toDoubleClamped(accepted) * FE_TO_MEK_RATIO);
            long remainder = receive.insertEnergy(requested, Action.EXECUTE);
            long inserted = requested - remainder;
            if (inserted <= 0L) {
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
        long requestJoules = (long) (EnergyAmountConversionUtils.toDoubleClamped(maxReceive) * FE_TO_MEK_RATIO);
        long remainder = receive.insertEnergy(requestJoules, Action.EXECUTE);
        long insertedJoules = requestJoules - remainder;
        if (insertedJoules <= 0L && maxReceive.isPositive()) {
            receiveState = ROLE_UNKNOWN;
        }
        return EnergyAmountConversionUtils.obtainFromDoubleFloor(insertedJoules / FE_TO_MEK_RATIO);
    }

    @Override
    public EnergyAmount extractEnergy(EnergyAmount maxExtract, @Nullable HubNode.HubMetadata hubMetadata) {
        if (send == null) return EnergyAmounts.ZERO;
        long requestJoules = (long) (EnergyAmountConversionUtils.toDoubleClamped(maxExtract) * FE_TO_MEK_RATIO);
        long extractedJoules = send.extractEnergy(requestJoules, Action.EXECUTE);
        if (extractedJoules <= 0L && maxExtract.isPositive()) {
            sendState = ROLE_UNKNOWN;
        }
        return EnergyAmountConversionUtils.obtainFromDoubleFloor(extractedJoules / FE_TO_MEK_RATIO);
    }

    @Override
    public EnergyAmount canExtractValue(@Nullable HubNode.HubMetadata hubMetadata) {
        if (send == null) return EnergyAmounts.ZERO;
        long extracted = send.extractEnergy(Long.MAX_VALUE, Action.SIMULATE);
        return EnergyAmountConversionUtils.obtainFromDoubleFloor(joulesToFe((double) extracted));
    }

    @Override
    public EnergyAmount canReceiveValue(@Nullable HubNode.HubMetadata hubMetadata) {
        if (isItem) {
            return EnergyAmount.obtain(needEnergy);
        }
        if (receive == null) return EnergyAmounts.ZERO;
        long remainder = receive.insertEnergy(Long.MAX_VALUE, Action.SIMULATE);
        return EnergyAmountConversionUtils.obtainFromDoubleFloor(joulesToFe((double) (Long.MAX_VALUE - remainder)));
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
