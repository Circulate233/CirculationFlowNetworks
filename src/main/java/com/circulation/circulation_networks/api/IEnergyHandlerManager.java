package com.circulation.circulation_networks.api;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import org.jetbrains.annotations.NotNull;

public interface IEnergyHandlerManager extends Comparable<IEnergyHandlerManager> {

    boolean isAvailable(TileEntity tileEntity);

    boolean isAvailable(ItemStack itemStack);

    Class<? extends IEnergyHandler> getEnergyHandlerClass();

    int getPriority();

    IEnergyHandler newInstance(TileEntity tileEntity);

    IEnergyHandler newInstance(ItemStack itemStack);

    String getUnit();

    default double getMultiplying() {
        return 1;
    }

    default int compareTo(@NotNull IEnergyHandlerManager o) {
        return Integer.compare(this.getPriority(), o.getPriority());
    }
}