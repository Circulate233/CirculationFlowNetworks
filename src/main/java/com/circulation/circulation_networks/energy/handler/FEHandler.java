package com.circulation.circulation_networks.energy.handler;

import com.circulation.circulation_networks.api.IEnergyHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

import javax.annotation.Nullable;

public final class FEHandler implements IEnergyHandler {

    @Nullable
    private IEnergyStorage send;
    @Nullable
    private IEnergyStorage receive;
    private EnergyType energyType;

    public FEHandler(TileEntity tileEntity) {
        init(tileEntity);
    }

    public FEHandler(ItemStack stack) {
        init(stack);
    }

    @Override
    public IEnergyHandler init(TileEntity tileEntity) {
        for (int i = 0; i < 6 && this.getType() != EnergyType.STORAGE; i++) {
            EnumFacing facing = EnumFacing.VALUES[i];
            var ies = tileEntity.getCapability(CapabilityEnergy.ENERGY, facing);
            if (ies == null) continue;
            if (ies.canExtract() && this.send == null) {
                this.send = ies;
            }
            if (ies.canReceive() && this.receive == null) {
                this.receive = ies;
            }
        }
        return this;
    }

    @Override
    public IEnergyHandler init(ItemStack itemStack) {
        var ies = itemStack.getCapability(CapabilityEnergy.ENERGY, null);
        if (ies == null) return this;
        if (ies.canReceive()) {
            this.receive = ies;
        }
        return this;
    }

    @Override
    public void clear() {
        send = null;
        receive = null;
        energyType = null;
    }

    @Override
    public long extractEnergy(long maxExtract) {
        if (send == null) return 0;
        int e = (int) Math.min(maxExtract, canExtractValue());
        return send.extractEnergy(e, false);
    }

    @Override
    public long receiveEnergy(long maxReceive) {
        if (receive == null) return 0;
        int e = (int) Math.min(maxReceive, canReceiveValue());
        return receive.receiveEnergy(e, false);
    }

    @Override
    public long canExtractValue() {
        return send == null ? 0 : send.extractEnergy(Integer.MAX_VALUE, true);
    }

    @Override
    public long canReceiveValue() {
        return receive == null ? 0 : receive.receiveEnergy(Integer.MAX_VALUE, true);
    }

    @Override
    public EnergyType getType() {
        if (energyType == null) {
            energyType = IEnergyHandler.super.getType();
        }
        return energyType;
    }

    @Override
    public boolean canExtract() {
        return send != null;
    }

    @Override
    public boolean canReceive() {
        return receive != null;
    }
}
