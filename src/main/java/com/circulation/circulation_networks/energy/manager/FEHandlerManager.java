package com.circulation.circulation_networks.energy.manager;

import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.api.IEnergyHandlerManager;
import com.circulation.circulation_networks.energy.handler.FEHandler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

public final class FEHandlerManager implements IEnergyHandlerManager {

    @Override
    public boolean isAvailable(TileEntity tileEntity) {
        for (var i = 0; i < EnumFacing.VALUES.length; ++i) {
            if (tileEntity.hasCapability(CapabilityEnergy.ENERGY, EnumFacing.VALUES[i])) return true;
        }
        return tileEntity instanceof IEnergyStorage;
    }

    @Override
    public Class<FEHandler> getEnergyHandlerClass() {
        return FEHandler.class;
    }

    @Override
    public int getPriority() {
        return 10;
    }

    @Override
    public IEnergyHandler newInstance(TileEntity tileEntity) {
        return new FEHandler(tileEntity);
    }

    @Override
    public String getUnit() {
        return "FE";
    }
}
