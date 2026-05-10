package com.circulation.circulation_networks.energy.manager;

import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.api.IEnergyHandlerManager;
import com.circulation.circulation_networks.energy.handler.FEHandler;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

public final class FEHandlerManager implements IEnergyHandlerManager {

    private static final Direction[] DIRECTIONS = Direction.values();

    @Override
    public boolean isAvailable(BlockEntity blockEntity) {
        for (Direction direction : DIRECTIONS) {
            if (blockEntity.getCapability(ForgeCapabilities.ENERGY, direction).isPresent()) return true;
        }
        return false;
    }

    @Override
    public boolean isAvailable(ItemStack itemStack) {
        return itemStack.getCapability(ForgeCapabilities.ENERGY).isPresent();
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

    @Override
    public String getUnit() {
        return IEnergyHandlerManager.super.getUnit();
    }
}
