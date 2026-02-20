package com.circulation.circulation_networks.energy.manager;

import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.api.IEnergyHandlerManager;
import com.circulation.circulation_networks.api.node.IMachineNode;
import com.circulation.circulation_networks.energy.handler.CEHandler;
import com.circulation.circulation_networks.proxy.CommonProxy;
import net.minecraft.tileentity.TileEntity;

public final class CEHandlerManager implements IEnergyHandlerManager {

    @Override
    public boolean isAvailable(TileEntity tileEntity) {
        return tileEntity.getCapability(CommonProxy.nodeCapability, null) instanceof IMachineNode;
    }

    @Override
    public Class<CEHandler> getEnergyHandlerClass() {
        return CEHandler.class;
    }

    @Override
    public int getPriority() {
        return Integer.MAX_VALUE;
    }

    @Override
    public IEnergyHandler newInstance(TileEntity tileEntity) {
        return tileEntity.getCapability(CommonProxy.ceHandlerCapability, null);
    }

    @Override
    public String getUnit() {
        return "CE";
    }
}
