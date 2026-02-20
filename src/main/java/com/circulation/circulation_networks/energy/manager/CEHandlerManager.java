package com.circulation.circulation_networks.energy.manager;

import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.api.IEnergyHandlerManager;
import com.circulation.circulation_networks.api.IMachineNodeTileEntity;
import com.circulation.circulation_networks.energy.handler.CEHandler;
import net.minecraft.tileentity.TileEntity;

public final class CEHandlerManager implements IEnergyHandlerManager {

    @Override
    public boolean isAvailable(TileEntity tileEntity) {
        return tileEntity instanceof IMachineNodeTileEntity;
    }

    @Override
    public Class<CEHandler> getEnergyHandlerClass() {
        return CEHandler.class;
    }

    @Override
    public int getPriority() {
        return Integer.MIN_VALUE;
    }

    @Override
    public IEnergyHandler newInstance(TileEntity tileEntity) {
        return ((IMachineNodeTileEntity) tileEntity).getCEHandler();
    }

    @Override
    public String getUnit() {
        return "CE";
    }
}
