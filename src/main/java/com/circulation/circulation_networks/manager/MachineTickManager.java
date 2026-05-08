package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.api.ClientTickMachine;
import com.circulation.circulation_networks.api.ServerTickMachine;
import com.circulation.circulation_networks.events.BlockEntityLifeCycleEvent;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;

import static com.circulation.circulation_networks.utils.WorldResolveCompat.isClientWorld;

public class MachineTickManager {

    public static final MachineTickManager INSTANCE = new MachineTickManager();

    private final ReferenceSet<ServerTickMachine> serverTe = new ReferenceLinkedOpenHashSet<>();
    private final ReferenceSet<ClientTickMachine> clientTe = new ReferenceLinkedOpenHashSet<>();

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
        for (var machine : clientTe) {
            machine.clientUpdate();
        }
    }

    public void onServerTick() {
        for (var machine : serverTe) {
            machine.serverUpdate();
        }
    }

    public void clear() {
        serverTe.clear();
        clientTe.clear();
    }
}
