package com.circulation.circulation_networks.energy.handler;

import com.circulation.circulation_networks.api.IEnergyHandler;
import lombok.Getter;
import mekanism.api.energy.IStrictEnergyAcceptor;
import mekanism.api.energy.IStrictEnergyOutputter;
import mekanism.api.energy.IStrictEnergyStorage;
import mekanism.common.base.IEnergyWrapper;
import mekanism.common.tier.EnergyCubeTier;
import mekanism.common.tile.TileEntityEnergyCube;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nullable;

public final class MEKHandler implements IEnergyHandler {

    private static final Class<?> inductionPort;

    static {
        Class<?> temp;
        try {
            temp = Class.forName("mekanism.common.tile.multiblock.TileEntityInductionPort");
        } catch (ClassNotFoundException e) {
            try {
                temp = Class.forName("mekanism.common.tile.TileEntityInductionPort");
            } catch (ClassNotFoundException ex) {
                temp = null;
            }
        }
        inductionPort = temp;
    }

    @Getter
    private TileEntity tileEntity;
    private long maxOutput = Long.MAX_VALUE;
    @Nullable
    private IStrictEnergyStorage send;
    @Nullable
    private IStrictEnergyStorage receive;
    private EnergyType energyType = EnergyType.INVALID;
    private boolean creative;

    public MEKHandler(TileEntity tileEntity) {
        this.tileEntity = tileEntity;
        init();
    }

    @Override
    public IEnergyHandler init(TileEntity tileEntity) {
        this.tileEntity = tileEntity;
        init();
        return this;
    }

    private void init() {
        if (tileEntity instanceof TileEntityEnergyCube te) {
            creative = te.tier == EnergyCubeTier.CREATIVE;
            send = (IStrictEnergyStorage) tileEntity;
            receive = (IStrictEnergyStorage) tileEntity;
            energyType = EnergyType.STORAGE;
            maxOutput = (long) te.getMaxOutput();
        } else if (inductionPort != null && inductionPort.isInstance(tileEntity)) {
            send = (IStrictEnergyStorage) tileEntity;
            receive = (IStrictEnergyStorage) tileEntity;
            energyType = EnergyType.STORAGE;
            maxOutput = (long) ((IEnergyWrapper) tileEntity).getMaxOutput();
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
                maxOutput = (long) te.getMaxOutput();
            }
        }
    }

    @Override
    public void clear() {
        tileEntity = null;
        maxOutput = Long.MAX_VALUE;
        send = null;
        receive = null;
        energyType = EnergyType.INVALID;
        creative = false;
    }

    @Override
    public long receiveEnergy(long maxReceive) {
        if (receive == null) return 0;
        var i = Math.min(canReceiveValue(), maxReceive);
        receive.setEnergy(receive.getEnergy() + i * 2.5);
        return i;
    }

    @Override
    public long extractEnergy(long maxExtract) {
        if (send == null) return 0;
        var o = Math.min(canExtractValue(), maxExtract);
        if (!creative) send.setEnergy(send.getEnergy() - o * 2.5);
        return o;
    }

    @Override
    public long canExtractValue() {
        if (send == null) return 0;
        if (creative) return Long.MAX_VALUE;
        double o = send.getEnergy() / 2.5;
        return Math.min((long) o, maxOutput);
    }

    @Override
    public long canReceiveValue() {
        if (receive == null) return 0;
        double i = (receive.getMaxEnergy() - receive.getEnergy()) / 2.5;
        return Math.min((long) i, maxOutput);
    }

    @Override
    public boolean canExtract() {
        if (creative) return true;
        return send != null && send.getEnergy() >= 2.5;
    }

    @Override
    public boolean canReceive() {
        return receive != null && (receive.getMaxEnergy() - receive.getEnergy()) / 2.5 >= 0;
    }

    @Override
    public EnergyType getType() {
        return energyType;
    }

}
