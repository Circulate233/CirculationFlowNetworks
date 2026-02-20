package com.circulation.circulation_networks.energy.handler;

import com.circulation.circulation_networks.api.IEnergyHandler;
import lombok.Getter;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

import javax.annotation.Nullable;

public final class FEHandler implements IEnergyHandler {

    @Getter
    private TileEntity tileEntity;
    @Nullable
    private IEnergyStorage send;
    @Nullable
    private IEnergyStorage receive;
    private int extractValue;
    private int receiveValue;

    public FEHandler(TileEntity tileEntity) {
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
        for (int i = 0; i < 6 && this.getType() != EnergyType.STORAGE; i++) {
            EnumFacing facing = EnumFacing.VALUES[i];
            var ies = tileEntity.getCapability(CapabilityEnergy.ENERGY, facing);
            if (ies == null) continue;
            if (ies.canExtract() && this.send == null) {
                this.send = ies;
                extractValue = ies.extractEnergy(Integer.MAX_VALUE, true);
            }
            if (ies.canReceive() && this.receive == null) {
                this.receive = ies;
                receiveValue = ies.receiveEnergy(Integer.MAX_VALUE, true);
            }
        }
    }

    @Override
    public void clear() {
        tileEntity = null;
        send = null;
        receive = null;
        extractValue = 0;
        receiveValue = 0;
    }

    @Override
    public long extractEnergy(long maxExtract) {
        if (send == null) return 0;
        int e = (int) Math.min(maxExtract, extractValue);
        var o = send.extractEnergy(e, false);
        extractValue -= o;
        return o;
    }

    @Override
    public long receiveEnergy(long maxReceive) {
        if (receive == null) return 0;
        int e = (int) Math.min(maxReceive, receiveValue);
        var o = receive.receiveEnergy(e, false);
        receiveValue -= o;
        return o;
    }

    @Override
    public long canExtractValue() {
        return extractValue;
    }

    @Override
    public long canReceiveValue() {
        return receiveValue;
    }

    @Override
    public EnergyType getType() {
        var t = IEnergyHandler.super.getType();
        if (t == EnergyType.INVALID) return t;
        if (send == null || receive == null) return t;
        return EnergyType.STORAGE;
    }

    @Override
    public boolean canExtract() {
        return extractValue > 0;
    }

    @Override
    public boolean canReceive() {
        return receiveValue > 0;
    }
}
