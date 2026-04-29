package com.circulation.circulation_networks.energy.manager;

import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.api.IEnergyHandlerManager;
import com.circulation.circulation_networks.energy.handler.EIOHandler;
import crazypants.enderio.powertools.machine.capbank.TileCapBank;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

public final class EIOHandlerManager implements IEnergyHandlerManager {

    @Override
    public boolean isAvailable(TileEntity tileEntity) {
        return tileEntity instanceof TileCapBank;
    }

    @Override
    public boolean isAvailable(ItemStack itemStack) {
        return false;
    }

    @Override
    public Class<EIOHandler> getEnergyHandlerClass() {
        return EIOHandler.class;
    }

    @Override
    public int getPriority() {
        return 12;
    }

    @Override
    public IEnergyHandler newBlockEntityInstance() {
        return new EIOHandler();
    }

    @Override
    public IEnergyHandler newItemInstance() {
        return new EIOHandler();
    }

    @Override
    public String getUnit() {
        return "RF";
    }
}
