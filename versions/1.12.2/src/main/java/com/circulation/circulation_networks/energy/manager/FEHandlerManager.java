package com.circulation.circulation_networks.energy.manager;

import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.api.IEnergyHandlerManager;
import com.circulation.circulation_networks.energy.handler.FEHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.energy.CapabilityEnergy;

public final class FEHandlerManager implements IEnergyHandlerManager {

    @Override
    public boolean isAvailable(TileEntity tileEntity) {
        return FEHandler.supports(tileEntity);
    }

    @Override
    public boolean isAvailable(ItemStack itemStack) {
        var storage = itemStack.getCapability(CapabilityEnergy.ENERGY, null);
        return storage != null && storage.canReceive();
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
    public IEnergyHandler newBlockEntityInstance() {
        return new FEHandler();
    }

    @Override
    public IEnergyHandler newItemInstance() {
        return new FEHandler();
    }

}
