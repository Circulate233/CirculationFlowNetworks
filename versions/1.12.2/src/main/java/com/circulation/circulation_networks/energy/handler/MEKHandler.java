package com.circulation.circulation_networks.energy.handler;

import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.EnergyAmounts;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.network.nodes.HubNode;
import com.circulation.circulation_networks.utils.EnergyAmountConversionUtils;
import mekanism.api.energy.IEnergizedItem;
import mekanism.api.energy.IStrictEnergyAcceptor;
import mekanism.api.energy.IStrictEnergyOutputter;
import mekanism.api.energy.IStrictEnergyStorage;
import mekanism.common.base.IEnergyWrapper;
import mekanism.common.content.matrix.SynchronizedMatrixData;
import mekanism.common.tier.EnergyCubeTier;
import mekanism.common.tile.TileEntityEnergyCube;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.math.BigInteger;

public class MEKHandler implements IEnergyHandler {

    private static final Class<?> inductionPort;
    private static final MethodHandle structureGet;
    private static final double FE_TO_MEK_RATIO = 2.5D;
    private static final BigInteger MAX_DIRECT_DOUBLE_TRANSFER = BigDecimal.valueOf(Double.MAX_VALUE).toBigInteger();
    private static final BigInteger MAX_SCALED_DOUBLE_TRANSFER = BigDecimal.valueOf(Double.MAX_VALUE / FE_TO_MEK_RATIO).toBigInteger();

    static {
        Class<?> temp;
        MethodHandle temp2;
        try {
            temp = Class.forName("mekanism.common.tile.multiblock.TileEntityInductionPort");
        } catch (ClassNotFoundException e) {
            try {
                temp = Class.forName("mekanism.common.tile.TileEntityInductionPort");
            } catch (ClassNotFoundException ex) {
                throw new RuntimeException(ex);
            }
        }
        try {
            temp2 = MethodHandles.lookup().unreflectGetter(temp.getField("structure"));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            temp2 = null;
        }
        inductionPort = temp;
        structureGet = temp2;
    }

    private final EnergyAmount maxReceive = EnergyAmount.obtain(MAX_SCALED_DOUBLE_TRANSFER);
    private final EnergyAmount maxExtract = EnergyAmount.obtain(MAX_SCALED_DOUBLE_TRANSFER);
    private final EnergyAmount needEnergy = EnergyAmount.obtain(0L);
    @Nullable
    private IStrictEnergyStorage send;
    @Nullable
    private IStrictEnergyStorage receive;
    private boolean isItem;
    private IEnergizedItem receiveItem;
    private ItemStack stack = ItemStack.EMPTY;
    private EnergyType energyType = EnergyType.INVALID;
    private boolean creative;
    private boolean initialized;

    public MEKHandler() {
    }

    private static void clampToMaximum(EnergyAmount amount, BigInteger maximum) {
        if (amount == null || !amount.isInitialized() || amount.isNegative()) {
            return;
        }
        if (amount.asBigInteger().compareTo(maximum) > 0) {
            amount.init(maximum);
        }
    }

    @Override
    public IEnergyHandler init(TileEntity tileEntity, @Nullable HubNode.HubMetadata hubMetadata) {
        if (initialized) {
            return this;
        }
        initialized = true;
        if (tileEntity instanceof TileEntityEnergyCube te) {
            creative = te.tier == EnergyCubeTier.CREATIVE;
            send = (IStrictEnergyStorage) tileEntity;
            receive = (IStrictEnergyStorage) tileEntity;
            energyType = EnergyType.STORAGE;
            EnergyAmountConversionUtils.setFromDoubleFloor(maxExtract, te.getMaxOutput());
            EnergyAmountConversionUtils.setFromDoubleFloor(maxReceive, te.getMaxOutput());
        } else if (inductionPort != null && inductionPort.isInstance(tileEntity)) {
            send = (IStrictEnergyStorage) tileEntity;
            receive = (IStrictEnergyStorage) tileEntity;
            energyType = EnergyType.STORAGE;
            try {
                SynchronizedMatrixData m = (SynchronizedMatrixData) structureGet.invoke(tileEntity);
                if (m != null) {
                    EnergyAmountConversionUtils.setFromDoubleFloor(maxExtract, m.getRemainingOutput());
                    EnergyAmountConversionUtils.setFromDoubleFloor(maxReceive, m.getRemainingInput());
                }
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        } else {
            boolean a = false;
            boolean b = false;
            for (var i = 0; i < EnumFacing.VALUES.length && !(a && b); i++) {
                var f = EnumFacing.VALUES[i];
                if (receive == null && tileEntity instanceof IStrictEnergyAcceptor a1) {
                    if (a1.canReceiveEnergy(f)) {
                        receive = (IStrictEnergyStorage) tileEntity;
                        a = true;
                    }
                }
                if (send == null && tileEntity instanceof IStrictEnergyOutputter o) {
                    if (o.canOutputEnergy(f)) {
                        send = (IStrictEnergyStorage) tileEntity;
                        b = true;
                    }
                }
            }
            if (a) energyType = b ? EnergyType.STORAGE : EnergyType.RECEIVE;
            else if (b) energyType = EnergyType.SEND;

            if (tileEntity instanceof IEnergyWrapper te && te.getMaxOutput() != 0) {
                EnergyAmountConversionUtils.setFromDoubleFloor(maxExtract, te.getMaxOutput());
                EnergyAmountConversionUtils.setFromDoubleFloor(maxReceive, te.getMaxOutput());
            } else {
                maxExtract.init(MAX_SCALED_DOUBLE_TRANSFER);
                maxReceive.init(MAX_SCALED_DOUBLE_TRANSFER);
            }
        }
        return this;
    }

    @Override
    public IEnergyHandler init(ItemStack itemStack, @Nullable HubNode.HubMetadata hubMetadata) {
        isItem = true;
        receiveItem = (IEnergizedItem) itemStack.getItem();
        double i = receiveItem.getMaxTransfer(itemStack);
        double r = receiveItem.getMaxEnergy(itemStack) - receiveItem.getEnergy(itemStack);
        EnergyAmountConversionUtils.setFromDoubleFloor(needEnergy, Math.max(0.0D, i == 0 ? r : Math.min(r, i)));
        stack = itemStack;
        energyType = EnergyType.RECEIVE;
        return this;
    }

    @Override
    public void clear() {
        maxReceive.setZero();
        maxExtract.setZero();
        send = null;
        receive = null;
        receiveItem = null;
        energyType = EnergyType.INVALID;
        creative = false;
        isItem = false;
        initialized = false;
        needEnergy.setZero();
        stack = ItemStack.EMPTY;
    }

    @Override
    public EnergyAmount receiveEnergy(EnergyAmount maxReceive, @Nullable HubNode.HubMetadata hubMetadata) {
        if (isItem) {
            EnergyAmount accepted = EnergyAmount.obtain(needEnergy).min(maxReceive);
            clampToMaximum(accepted, MAX_DIRECT_DOUBLE_TRANSFER);
            if (!accepted.isZero()) {
                receiveItem.setEnergy(stack, receiveItem.getEnergy(stack) + EnergyAmountConversionUtils.toDoubleClamped(accepted));
                needEnergy.subtract(accepted);
            }
            return accepted;
        } else {
            if (receive == null) return EnergyAmounts.ZERO;
            EnergyAmount receivable = EnergyAmount.obtain(maxReceive);
            clampToMaximum(receivable, MAX_SCALED_DOUBLE_TRANSFER);
            if (!receivable.isZero()) {
                receive.setEnergy(receive.getEnergy() + EnergyAmountConversionUtils.toDoubleClamped(receivable) * FE_TO_MEK_RATIO);
            }
            return receivable;
        }
    }

    @Override
    public EnergyAmount extractEnergy(EnergyAmount maxExtract, @Nullable HubNode.HubMetadata hubMetadata) {
        if (send == null) return EnergyAmounts.ZERO;
        EnergyAmount extractable = EnergyAmount.obtain(maxExtract);
        clampToMaximum(extractable, MAX_SCALED_DOUBLE_TRANSFER);
        if (!extractable.isZero() && !creative) {
            send.setEnergy(send.getEnergy() - EnergyAmountConversionUtils.toDoubleClamped(extractable) * FE_TO_MEK_RATIO);
        }
        return extractable;
    }

    @Override
    public EnergyAmount canExtractValue(@Nullable HubNode.HubMetadata hubMetadata) {
        if (send == null) return EnergyAmounts.ZERO;
        if (creative) return EnergyAmount.obtain(maxExtract);
        EnergyAmount extractable = EnergyAmountConversionUtils.obtainFromDoubleFloor(send.getEnergy() * 0.4D);
        return extractable.min(maxExtract);
    }

    @Override
    public EnergyAmount canReceiveValue(@Nullable HubNode.HubMetadata hubMetadata) {
        if (isItem) {
            return EnergyAmount.obtain(needEnergy);
        } else {
            if (receive == null) return EnergyAmounts.ZERO;
            EnergyAmount receivable = EnergyAmountConversionUtils.obtainFromDoubleFloor((receive.getMaxEnergy() - receive.getEnergy()) * 0.4D);
            return receivable.min(maxReceive);
        }
    }

    @Override
    public boolean canExtract(IEnergyHandler receiveHandler, @Nullable HubNode.HubMetadata hubMetadata) {
        if (creative) return true;
        return send != null && send.getEnergy() >= 2.5;
    }

    @Override
    public boolean canReceive(IEnergyHandler sendHandler, @Nullable HubNode.HubMetadata hubMetadata) {
        if (isItem) return needEnergy.isPositive();
        else return receive != null && (receive.getMaxEnergy() - receive.getEnergy()) * 0.4D > 0.0D;
    }

    @Override
    public EnergyType getType(@Nullable HubNode.HubMetadata hubMetadata) {
        return energyType;
    }

}
