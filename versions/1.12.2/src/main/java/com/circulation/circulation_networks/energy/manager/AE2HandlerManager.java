package com.circulation.circulation_networks.energy.manager;

import appeng.api.networking.IGrid;
import appeng.tile.networking.TileController;
import appeng.tile.networking.TileEnergyAcceptor;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.api.IEnergyHandlerManager;
import com.circulation.circulation_networks.energy.handler.AE2Handler;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

public final class AE2HandlerManager implements IEnergyHandlerManager {

    public static final AE2HandlerManager INSTANCE = new AE2HandlerManager();

    private final Reference2ObjectMap<IGrid, AE2Handler> gridCache = new Reference2ObjectOpenHashMap<>();

    private AE2HandlerManager() {
    }

    public void clearTickCache() {
        gridCache.clear();
    }

    public AE2Handler claim(IGrid grid, AE2Handler aeGrid) {
        var a = gridCache.get(grid);
        if (a == null) {
            gridCache.put(grid, aeGrid);
            return aeGrid;
        }
        return a;
    }

    @Override
    public boolean isAvailable(TileEntity tileEntity) {
        return tileEntity instanceof TileController || tileEntity instanceof TileEnergyAcceptor;
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
