package com.circulation.circulation_networks.energy.manager;

import appeng.blockentity.networking.ControllerBlockEntity;
import appeng.blockentity.networking.EnergyAcceptorBlockEntity;
import appeng.api.networking.IGrid;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.api.IEnergyHandlerManager;
import com.circulation.circulation_networks.energy.handler.AE2Handler;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class AE2HandlerManager implements IEnergyHandlerManager {

    public static final AE2HandlerManager INSTANCE = new AE2HandlerManager();

    private final Reference2ObjectMap<com.circulation.circulation_networks.api.IGrid, ReferenceSet<IGrid>> seenGrids =
        new Reference2ObjectOpenHashMap<>();

    private AE2HandlerManager() {
    }

    public void clearTickCache() {
        seenGrids.clear();
    }

    public boolean claim(com.circulation.circulation_networks.api.IGrid currentGrid, IGrid aeGrid) {
        ReferenceSet<IGrid> claimed = seenGrids.get(currentGrid);
        if (claimed == null) {
            claimed = new ReferenceOpenHashSet<>();
            seenGrids.put(currentGrid, claimed);
        }
        return claimed.add(aeGrid);
    }

    @Override
    public boolean isAvailable(BlockEntity blockEntity) {
        return blockEntity instanceof ControllerBlockEntity || blockEntity instanceof EnergyAcceptorBlockEntity;
    }

    @Override
    public boolean isAvailable(ItemStack itemStack) {
        return false;
    }

    @Override
    public Class<AE2Handler> getEnergyHandlerClass() {
        return AE2Handler.class;
    }

    @Override
    public int getPriority() {
        return Integer.MAX_VALUE;
    }

    @Override
    public IEnergyHandler newBlockEntityInstance() {
        return new AE2Handler();
    }

    @Override
    public IEnergyHandler newItemInstance() {
        return new AE2Handler();
    }

    @Override
    public String getUnit() {
        return "AE";
    }

    @Override
    public double getMultiplying() {
        return 2.0D;
    }
}
