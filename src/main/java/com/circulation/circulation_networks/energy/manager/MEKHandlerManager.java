package com.circulation.circulation_networks.energy.manager;

import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.api.IEnergyHandlerManager;
import com.circulation.circulation_networks.energy.handler.MEKHandler;
import mekanism.api.energy.IStrictEnergyAcceptor;
import mekanism.api.energy.IStrictEnergyOutputter;
import mekanism.api.energy.IStrictEnergyStorage;
import net.minecraft.tileentity.TileEntity;

public final class MEKHandlerManager implements IEnergyHandlerManager {

    @Override
    public boolean isAvailable(TileEntity tile) {
        if (tile instanceof IStrictEnergyStorage)
            return tile instanceof IStrictEnergyAcceptor || tile instanceof IStrictEnergyOutputter;
        else return false;
    }

    @Override
    public Class<? extends IEnergyHandler> getEnergyHandlerClass() {
        return MEKHandler.class;
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public IEnergyHandler newInstance(TileEntity tileEntity) {
        return new MEKHandler(tileEntity);
    }

    @Override
    public String getUnit() {
        return "J";
    }

    @Override
    public double getMultiplying() {
        return 0.4;
    }
}
