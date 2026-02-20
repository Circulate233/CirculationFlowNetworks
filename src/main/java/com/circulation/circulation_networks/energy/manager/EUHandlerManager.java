package com.circulation.circulation_networks.energy.manager;

import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.api.IEnergyHandlerManager;
import com.circulation.circulation_networks.energy.handler.EUHandler;
import com.circulation.circulation_networks.manager.EnergyMachineManager;
import ic2.api.energy.EnergyNet;
import ic2.api.energy.event.EnergyTileLoadEvent;
import ic2.api.energy.tile.IEnergySink;
import ic2.api.energy.tile.IEnergySource;
import ic2.api.energy.tile.IEnergyTile;
import ic2.api.info.ILocatable;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public final class EUHandlerManager implements IEnergyHandlerManager {

    public static final EUHandlerManager INSTANCE = new EUHandlerManager();

    private EUHandlerManager() {
    }

    @Override
    public boolean isAvailable(TileEntity tileEntity) {
        IEnergyTile tile = EnergyNet.instance.getSubTile(tileEntity.getWorld(), tileEntity.getPos());
        return tile instanceof IEnergySource || tile instanceof IEnergySink;
    }

    @Override
    public Class<EUHandler> getEnergyHandlerClass() {
        return EUHandler.class;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public IEnergyHandler newInstance(TileEntity tileEntity) {
        return new EUHandler(tileEntity);
    }

    @Override
    public String getUnit() {
        return "EU";
    }

    @Override
    public double getMultiplying() {
        return 4;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEnergyTileLoad(EnergyTileLoadEvent event) {
        if (event.getWorld().isRemote) return;
        var tile = event.tile;
        BlockPos pos;
        if (tile instanceof TileEntity te && this.isAvailable(te)) {
            pos = te.getPos();
        } else if (tile instanceof ILocatable te) {
            pos = te.getPosition();
        } else return;
        var tileEntity = event.getWorld().getTileEntity(pos);
        if (tileEntity != null) {
            if (this.isAvailable(tileEntity))
                EnergyMachineManager.INSTANCE.addMachine(tileEntity);
        }
    }
}
