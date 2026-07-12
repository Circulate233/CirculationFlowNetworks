package com.circulation.circulation_networks.energy.manager;

import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.api.IEnergyHandlerManager;
import com.circulation.circulation_networks.energy.handler.EIOBackendHandler;
import com.circulation.circulation_networks.energy.handler.EIOHandler;
import com.circulation.circulation_networks.manager.MappedEnergyHandlerProvider;
import crazypants.enderio.base.power.IPowerStorage;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

public final class EIOHandlerManager implements IEnergyHandlerManager, MappedEnergyHandlerProvider {

    public static final EIOHandlerManager INSTANCE = new EIOHandlerManager();
    private final Reference2ObjectMap<IPowerStorage, BackendLease> identities = new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectMap<IEnergyHandler, BackendLease> backends = new Reference2ObjectOpenHashMap<>();

    private EIOHandlerManager() {
    }

    private static EIOHandler requireEndpoint(IEnergyHandler handler) {
        if (!(handler instanceof EIOHandler endpoint)) {
            throw new IllegalArgumentException("Ender IO manager received " + handler.getClass().getName());
        }
        return endpoint;
    }

    @Override
    public IEnergyHandler resolveRuntime(IEnergyHandler value, long epoch) {
        throw new UnsupportedOperationException("Ender IO capacitor banks use shared backend leases");
    }

    @Override
    public IEnergyHandler acquireSharedBackend(IEnergyHandler boundHandler) {
        EIOHandler endpoint = requireEndpoint(boundHandler);
        IPowerStorage identity = endpoint.backendIdentity();
        BackendLease lease = identities.get(identity);
        if (lease == null) {
            lease = new BackendLease(new EIOBackendHandler(identity));
            identities.put(identity, lease);
            backends.put(lease.backend, lease);
        }
        if (lease.references == Integer.MAX_VALUE) {
            throw new IllegalStateException("Ender IO shared-backend reference count exhausted");
        }
        lease.references++;
        return lease.backend;
    }

    @Override
    public void releaseSharedBackend(IEnergyHandler boundHandler, IEnergyHandler sharedBackend) {
        requireEndpoint(boundHandler);
        BackendLease lease = backends.get(sharedBackend);
        if (lease == null || lease.references <= 0)
            throw new IllegalStateException("Ender IO backend lease is inconsistent");
        lease.references--;
        if (lease.references == 0) {
            backends.remove(sharedBackend);
            BackendLease retired = lease;
            identities.values().removeIf(value -> value == retired);
        }
    }

    @Override
    public boolean isAvailable(TileEntity tileEntity) {
        return EIOHandler.supports(tileEntity);
    }

    @Override
    public boolean isAvailable(ItemStack itemStack) {
        return false;
    }

    @Override
    public Class<EIOHandler> getEnergyHandlerClass() {
        return EIOHandler.class;
    }

    @Override
    public int getPriority() {
        return 12;
    }

    @Override
    public IEnergyHandler newBlockEntityInstance() {
        return new EIOHandler();
    }

    @Override
    public IEnergyHandler newItemInstance() {
        return new EIOHandler();
    }

    @Override
    public String getUnit() {
        return "RF";
    }

    private static final class BackendLease {
        private final EIOBackendHandler backend;
        private int references;

        private BackendLease(EIOBackendHandler backend) {
            this.backend = backend;
        }
    }
}
