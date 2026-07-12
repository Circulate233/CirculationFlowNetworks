package com.circulation.circulation_networks.energy.manager;

import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.energy.IEnergyGridProvider;
import appeng.tile.networking.TileController;
import appeng.tile.networking.TileEnergyAcceptor;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.api.IEnergyHandlerManager;
import com.circulation.circulation_networks.energy.handler.AE2BackendHandler;
import com.circulation.circulation_networks.energy.handler.AE2Handler;
import com.circulation.circulation_networks.manager.MappedEnergyHandlerProvider;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import org.jetbrains.annotations.NotNull;

public final class AE2HandlerManager implements IEnergyHandlerManager, MappedEnergyHandlerProvider {

    public static final AE2HandlerManager INSTANCE = new AE2HandlerManager();

    private final Reference2ObjectMap<IEnergyGrid, BackendLease> grids = new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectMap<IEnergyHandler, BackendLease> backends = new Reference2ObjectOpenHashMap<>();

    private AE2HandlerManager() {
    }

    private static AE2Handler requireEndpoint(IEnergyHandler handler) {
        if (!(handler instanceof AE2Handler endpoint)) {
            throw new IllegalArgumentException("AE2 manager received " + handler.getClass().getName());
        }
        return endpoint;
    }

    @Override
    public @NotNull IEnergyHandler resolveRuntime(IEnergyHandler boundHandler, long epoch) {
        throw new UnsupportedOperationException("AE2 uses shared backend leases");
    }

    @Override
    public @NotNull IEnergyHandler acquireSharedBackend(IEnergyHandler boundHandler) {
        AE2Handler endpoint = requireEndpoint(boundHandler);
        IEnergyGrid identity = endpoint.backendIdentity();
        ReferenceSet<IEnergyGridProvider> visited = new ReferenceOpenHashSet<>();
        ReferenceSet<IEnergyGrid> connectedGrids = new ReferenceOpenHashSet<>();
        ObjectArrayList<IEnergyGridProvider> queue = new ObjectArrayList<>();
        queue.add(identity);
        while (!queue.isEmpty()) {
            IEnergyGridProvider provider = queue.remove(queue.size() - 1);
            if (!visited.add(provider)) {
                continue;
            }
            if (provider instanceof IEnergyGrid grid) {
                connectedGrids.add(grid);
            }
            queue.addAll(provider.providers());
        }
        BackendLease lease = findExactComponent(connectedGrids);
        if (lease == null) {
            lease = new BackendLease(new AE2BackendHandler(identity), connectedGrids);
            backends.put(lease.backend, lease);
            for (IEnergyGrid grid : connectedGrids) {
                grids.put(grid, lease);
            }
        }
        if (lease.references == Integer.MAX_VALUE) {
            throw new IllegalStateException("AE2 shared-backend reference count exhausted");
        }
        lease.references++;
        return lease.backend;
    }

    @Override
    public void releaseSharedBackend(IEnergyHandler boundHandler, IEnergyHandler sharedBackend) {
        requireEndpoint(boundHandler);
        BackendLease lease = backends.get(sharedBackend);
        if (lease == null || lease.references <= 0) {
            throw new IllegalStateException("AE2 shared backend lease is inconsistent");
        }
        lease.references--;
        if (lease.references == 0) {
            backends.remove(sharedBackend);
            removeGridMappings(lease);
            lease.backend.close();
        }
    }

    private void removeGridMappings(BackendLease retired) {
        grids.reference2ObjectEntrySet().removeIf(entry -> entry.getValue() == retired);
    }

    private BackendLease findExactComponent(ReferenceSet<IEnergyGrid> component) {
        if (component.isEmpty()) {
            throw new IllegalStateException("AE2 endpoint has an empty energy-grid component");
        }
        BackendLease candidate = grids.get(component.iterator().next());
        return candidate != null && candidate.matches(component) ? candidate : null;
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

    private static final class BackendLease {
        private final AE2BackendHandler backend;
        private final ReferenceOpenHashSet<IEnergyGrid> grids;
        private int references;

        private BackendLease(AE2BackendHandler backend, ReferenceSet<IEnergyGrid> grids) {
            this.backend = backend;
            this.grids = new ReferenceOpenHashSet<>(grids);
        }

        private boolean matches(ReferenceSet<IEnergyGrid> candidate) {
            return grids.size() == candidate.size() && grids.containsAll(candidate);
        }
    }
}
