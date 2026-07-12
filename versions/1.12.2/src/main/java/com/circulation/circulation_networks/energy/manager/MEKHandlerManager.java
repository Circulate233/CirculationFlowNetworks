package com.circulation.circulation_networks.energy.manager;

import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.api.IEnergyHandlerManager;
import com.circulation.circulation_networks.energy.handler.MEKHandler;
import mekanism.api.energy.IEnergizedItem;
import mekanism.api.energy.IStrictEnergyAcceptor;
import mekanism.api.energy.IStrictEnergyOutputter;
import mekanism.api.energy.IStrictEnergyStorage;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

public final class MEKHandlerManager implements IEnergyHandlerManager {

    @Override
    public boolean isAvailable(TileEntity tile) {
        if (tile instanceof mekanism.common.tile.TileEntityEnergyCube) {
            return true;
        }
        if (!(tile instanceof IStrictEnergyStorage)) {
            return false;
        }
        if (tile instanceof IStrictEnergyAcceptor acceptor) {
            for (net.minecraft.util.EnumFacing facing : net.minecraft.util.EnumFacing.VALUES) {
                if (acceptor.canReceiveEnergy(facing)) {
                    return true;
                }
            }
        }
        if (tile instanceof IStrictEnergyOutputter outputter) {
            for (net.minecraft.util.EnumFacing facing : net.minecraft.util.EnumFacing.VALUES) {
                if (outputter.canOutputEnergy(facing)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean isAvailable(ItemStack itemStack) {
        return itemStack.getItem() instanceof IEnergizedItem i && i.canReceive(itemStack);
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
    public IEnergyHandler newBlockEntityInstance() {
        return new MEKHandler();
    }

    @Override
    public IEnergyHandler newItemInstance() {
        return new MEKHandler();
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
