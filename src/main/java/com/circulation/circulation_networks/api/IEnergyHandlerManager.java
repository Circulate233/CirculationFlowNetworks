package com.circulation.circulation_networks.api;

import org.jetbrains.annotations.NotNull;
//~ mc_imports
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

public interface IEnergyHandlerManager extends Comparable<IEnergyHandlerManager> {

    //~ if >=1.20 'TileEntity ' -> 'BlockEntity ' {
    boolean isAvailable(TileEntity tileEntity);

    boolean isAvailable(ItemStack itemStack);

    Class<? extends IEnergyHandler> getEnergyHandlerClass();

    int getPriority();

    IEnergyHandler newBlockEntityInstance();
    //~}

    IEnergyHandler newItemInstance();

    default String getUnit() {
        return "FE";
    }

    default double getMultiplying() {
        return 1;
    }

    @Override
    default int compareTo(@NotNull IEnergyHandlerManager o) {
        return Integer.compare(this.getPriority(), o.getPriority());
    }
}
