package com.circulation.circulation_networks.energy.manager;

import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.api.IEnergyHandlerManager;
import com.circulation.circulation_networks.energy.handler.FEHandler;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;

public final class FEHandlerManager implements IEnergyHandlerManager {

    private static final Direction[] DIRECTIONS = Direction.values();

    @Override
    public boolean isAvailable(BlockEntity blockEntity) {
        var level = blockEntity.getLevel();
        if (level == null) return false;
        var pos = blockEntity.getBlockPos();
        for (Direction direction : DIRECTIONS) {
            var storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, null, blockEntity, direction);
            if (storage != null && (storage.canExtract() || storage.canReceive())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isAvailable(ItemStack itemStack) {
        var storage = itemStack.getCapability(Capabilities.EnergyStorage.ITEM);
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

    @Override
    public String getUnit() {
        return IEnergyHandlerManager.super.getUnit();
    }
}
