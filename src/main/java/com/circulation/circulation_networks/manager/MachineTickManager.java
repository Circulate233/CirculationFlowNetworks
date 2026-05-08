package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.api.ClientTickMachine;
import com.circulation.circulation_networks.api.ServerTickMachine;
import com.circulation.circulation_networks.events.BlockEntityLifeCycleEvent;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import static com.circulation.circulation_networks.utils.SideCompat.isClientWorld;

public class MachineTickManager {

    public static final MachineTickManager INSTANCE = new MachineTickManager();

    private final ReferenceSet<ServerTickMachine> serverTe = new ReferenceLinkedOpenHashSet<>();
    private final ReferenceSet<ClientTickMachine> clientTe = new ReferenceLinkedOpenHashSet<>();

    //~ if >=1.20 'net.minecraft.world.World' -> 'net.minecraft.world.level.Level' {
    public void onBlockEntityValidate(BlockEntityLifeCycleEvent.Validate event) {
        if (isClientWorld(event.getWorld())) {
            if (event.getBlockEntity() instanceof ClientTickMachine te) registerClientMachine(te);
        } else if (event.getBlockEntity() instanceof ServerTickMachine te) {
            registerServerMachine(te);
        }
    }

    public void onBlockEntityInvalidate(BlockEntityLifeCycleEvent.Invalidate event) {
        if (isClientWorld(event.getWorld())) {
            if (event.getBlockEntity() instanceof ClientTickMachine te) unregisterClientMachine(te);
        } else if (event.getBlockEntity() instanceof ServerTickMachine te) {
            unregisterServerMachine(te);
        }
    }

    public void registerClientMachine(ClientTickMachine machine) {
        if (machine != null) {
            clientTe.add(machine);
        }
    }

    public void unregisterClientMachine(ClientTickMachine machine) {
        if (machine != null) {
            clientTe.remove(machine);
        }
    }

    public void registerServerMachine(ServerTickMachine machine) {
        if (machine != null) {
            serverTe.add(machine);
        }
    }

    public void unregisterServerMachine(ServerTickMachine machine) {
        if (machine != null) {
            serverTe.remove(machine);
        }
    }

    public void onClientTick() {
        boolean n = false;
        for (var machine : clientTe) {
            if (machine != null) machine.clientUpdate();
            else n = true;
        }
        if (n) clientTe.remove(null);
    }

    public void onServerTick() {
        boolean n = false;
        for (var machine : serverTe) {
            if (machine != null) machine.serverUpdate();
            else n = true;
        }
        if (n) serverTe.remove(null);
    }

    public void clear() {
        serverTe.clear();
        clientTe.clear();
    }
    //~}
}
