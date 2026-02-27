package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.api.ClientTickMachine;
import com.circulation.circulation_networks.api.ServerTickMachine;
import com.circulation.circulation_networks.events.TileEntityLifeCycleEvent;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class MachineNodeTEManager {

    public static final MachineNodeTEManager INSTANCE = new MachineNodeTEManager();

    private final ReferenceSet<ServerTickMachine> serverTe = new ReferenceLinkedOpenHashSet<>();
    private final ReferenceSet<ClientTickMachine> clientTe = new ReferenceLinkedOpenHashSet<>();

    public void onTileEntityValidate(TileEntityLifeCycleEvent.Validate event) {
        if (event.getWorld().isRemote) {
            if (event.getTileEntity() instanceof ClientTickMachine te) clientTe.add(te);
        } else if (event.getTileEntity() instanceof ServerTickMachine te) serverTe.add(te);
    }

    public void onTileEntityInvalidate(TileEntityLifeCycleEvent.Invalidate event) {
        if (event.getWorld().isRemote) {
            if (event.getTileEntity() instanceof ClientTickMachine te) clientTe.remove(te);
        } else if (event.getTileEntity() instanceof ServerTickMachine te) serverTe.remove(te);
    }

    @SideOnly(Side.CLIENT)
    public void onClientTick() {
        for (var tileEntity : clientTe) {
            tileEntity.clientUpdate();
        }
    }

    public void onServerTick() {
        for (var tileEntity : serverTe) {
            tileEntity.serverUpdate();
        }
    }

    public void clear() {
        serverTe.clear();
        clientTe.clear();
    }
}