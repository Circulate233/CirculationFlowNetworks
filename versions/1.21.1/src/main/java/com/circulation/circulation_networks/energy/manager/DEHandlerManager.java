package com.circulation.circulation_networks.energy.manager;

import com.brandon3055.brandonscore.capability.CapabilityOP;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.api.IEnergyHandlerManager;
import com.circulation.circulation_networks.energy.handler.DEHandler;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class DEHandlerManager implements IEnergyHandlerManager {

    private static final Direction[] DIRECTIONS = Direction.values();

    @Override
    public boolean isAvailable(BlockEntity blockEntity) {
        if (blockEntity.getLevel() == null) {
            return false;
        }
        for (Direction direction : DIRECTIONS) {
            var storage = CapabilityOP.fromBlockEntity(blockEntity, direction);
            if (storage != null && (storage.canExtract() || storage.canReceive())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isAvailable(ItemStack itemStack) {
        var storage = itemStack.getCapability(CapabilityOP.ITEM);
        return storage != null && storage.canReceive();
    }

    @Override
    public Class<DEHandler> getEnergyHandlerClass() {
        return DEHandler.class;
    }

    @Override
    public int getPriority() {
        return 11;
    }

    @Override
    public IEnergyHandler newBlockEntityInstance() {
        return new DEHandler();
    }

    @Override
    public IEnergyHandler newItemInstance() {
        return new DEHandler();
    }

    @Override
    public String getUnit() {
        return "OP";
    }
}
