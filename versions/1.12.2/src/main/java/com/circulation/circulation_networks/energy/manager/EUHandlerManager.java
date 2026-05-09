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
import ic2.api.item.ElectricItem;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
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
    public boolean isAvailable(ItemStack itemStack) {
        return ElectricItem.manager.getMaxCharge(itemStack) > 0;
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
    public IEnergyHandler newBlockEntityInstance() {
        return new EUHandler();
    }

    @Override
    public IEnergyHandler newItemInstance() {
        return new EUHandler();
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
        if (!(event.getWorld() instanceof WorldServer server)) return;
        var tile = event.tile;
        if (tile instanceof TileEntity te && this.isAvailable(te) && !te.isInvalid()) {
            if (EnergyMachineManager.INSTANCE.getMachineGridMap().containsKey(te)) return;
            if (this.isAvailable(te))
                EnergyMachineManager.INSTANCE.addMachine(te);
        } else if (tile instanceof ILocatable il) {
            server.addScheduledTask(() -> {
                var te = il.getWorldObj().getTileEntity(il.getPosition());
                if (te == null) return;
                if (EnergyMachineManager.INSTANCE.getMachineGridMap().containsKey(te)) return;
                if (this.isAvailable(te))
                    EnergyMachineManager.INSTANCE.addMachine(te);
            });
        }
    }
}
