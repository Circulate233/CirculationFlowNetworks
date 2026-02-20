package com.circulation.circulation_networks.api;

import net.minecraft.tileentity.TileEntity;
import org.jetbrains.annotations.NotNull;

public interface IEnergyHandlerManager extends Comparable<IEnergyHandlerManager> {

    boolean isAvailable(TileEntity tileEntity);

    Class<? extends IEnergyHandler> getEnergyHandlerClass();

    int getPriority();

    IEnergyHandler newInstance(TileEntity tileEntity);

    String getUnit();

    default double getMultiplying() {
        return 1;
    }

    default int compareTo(@NotNull IEnergyHandlerManager o) {
        return Integer.compare(this.getPriority(), o.getPriority());
    }
}