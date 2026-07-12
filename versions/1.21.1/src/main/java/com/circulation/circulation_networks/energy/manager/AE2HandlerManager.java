package com.circulation.circulation_networks.energy.manager;

import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;
import appeng.blockentity.grid.AENetworkedPoweredBlockEntity;
import appeng.blockentity.networking.ControllerBlockEntity;
import appeng.blockentity.networking.EnergyAcceptorBlockEntity;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.api.IEnergyHandlerManager;
import com.circulation.circulation_networks.energy.handler.AE2BackendHandler;
import com.circulation.circulation_networks.energy.handler.AE2Handler;
import com.circulation.circulation_networks.manager.MappedEnergyHandlerProvider;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** Owns AE2 shared backends keyed strictly by energy-service identity. */
public final class AE2HandlerManager implements IEnergyHandlerManager, MappedEnergyHandlerProvider {

    public static final AE2HandlerManager INSTANCE = new AE2HandlerManager();

    private final Reference2ObjectMap<IEnergyService, BackendLease> identities = new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectMap<IEnergyHandler, BackendLease> backends = new Reference2ObjectOpenHashMap<>();

    private AE2HandlerManager() {
    }

    @Override
    public @NotNull IEnergyHandler resolveRuntime(IEnergyHandler boundHandler, long epoch) {
        throw new UnsupportedOperationException("AE2 uses shared-backend mapping, not runtime mapping");
    }

    @Override
    public @NotNull IEnergyHandler acquireSharedBackend(IEnergyHandler boundHandler) {
        AE2Handler endpoint = requireEndpoint(boundHandler);
        ReferenceSet<IEnergyService> component = endpoint.componentServices();
        BackendLease lease = findExactComponent(component);
        if (lease == null) {
            lease = new BackendLease(new AE2BackendHandler(endpoint.energyService()), component);
            backends.put(lease.backend, lease);
            for (IEnergyService service : component) {
                identities.put(service, lease);
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
        if (!(sharedBackend instanceof AE2BackendHandler backend)) {
            throw new IllegalArgumentException("AE2 manager received backend " + sharedBackend.getClass().getName());
        }
        BackendLease lease = backends.get(backend);
        if (lease == null || lease.backend != backend || lease.references <= 0) {
            throw new IllegalStateException("AE2 shared-backend lease is inconsistent");
        }
        lease.references--;
        if (lease.references == 0) {
            backends.remove(backend);
            removeIdentityMappings(lease);
            backend.close();
        }
    }

    private void removeIdentityMappings(BackendLease retired) {
        identities.reference2ObjectEntrySet().removeIf(entry -> entry.getValue() == retired);
    }

    private BackendLease findExactComponent(ReferenceSet<IEnergyService> component) {
        if (component.isEmpty()) {
            throw new IllegalStateException("AE2 endpoint has an empty overlay component");
        }
        BackendLease candidate = identities.get(component.iterator().next());
        return candidate != null && candidate.matches(component) ? candidate : null;
    }

    @Override
    public boolean isAvailable(BlockEntity blockEntity) {
        if (!(blockEntity instanceof ControllerBlockEntity)
            && !(blockEntity instanceof EnergyAcceptorBlockEntity)) {
            return false;
        }
        AENetworkedPoweredBlockEntity endpoint = (AENetworkedPoweredBlockEntity) blockEntity;
        IGrid grid = endpoint.getMainNode().getGrid();
        return grid != null && grid.getEnergyService() != null;
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
        throw new UnsupportedOperationException("AE2 handler manager does not support item bindings");
    }

    @Override
    public String getUnit() {
        return "AE";
    }

    @Override
    public double getMultiplying() {
        return 2.0D;
    }

    private static AE2Handler requireEndpoint(IEnergyHandler handler) {
        Objects.requireNonNull(handler, "handler");
        if (handler instanceof AE2Handler endpoint) {
            return endpoint;
        }
        throw new IllegalArgumentException("AE2 manager received endpoint " + handler.getClass().getName());
    }

    private static final class BackendLease {
        private final AE2BackendHandler backend;
        private final ReferenceOpenHashSet<IEnergyService> services;
        private int references;

        private BackendLease(AE2BackendHandler backend, ReferenceSet<IEnergyService> services) {
            this.backend = backend;
            this.services = new ReferenceOpenHashSet<>(services);
        }

        private boolean matches(ReferenceSet<IEnergyService> candidate) {
            return services.size() == candidate.size() && services.containsAll(candidate);
        }
    }
}
