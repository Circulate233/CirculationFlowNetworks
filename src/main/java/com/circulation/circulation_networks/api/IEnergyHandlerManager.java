package com.circulation.circulation_networks.api;

import org.jetbrains.annotations.NotNull;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public interface IEnergyHandlerManager extends Comparable<IEnergyHandlerManager> {

    boolean isAvailable(BlockEntity tileEntity);

    boolean isAvailable(ItemStack itemStack);

    Class<? extends IEnergyHandler> getEnergyHandlerClass();

    int getPriority();

    IEnergyHandler newBlockEntityInstance();

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
