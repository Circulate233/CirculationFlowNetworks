package com.circulation.circulation_networks.energy.manager;

import appeng.api.networking.energy.IEnergyService;
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

public final class AE2HandlerManager implements IEnergyHandlerManager, MappedEnergyHandlerProvider {

    public static final AE2HandlerManager INSTANCE = new AE2HandlerManager();

    private final Reference2ObjectMap<IEnergyService, BackendReference> identities =
        new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectMap<IEnergyHandler, BackendReference> backends =
        new Reference2ObjectOpenHashMap<>();

    private AE2HandlerManager() {
    }

    @Override
    public @NotNull IEnergyHandler resolveRuntime(IEnergyHandler boundHandler, long epoch) {
        throw new UnsupportedOperationException("AE2 uses shared backend leases, not runtime mapping");
    }

    @Override
    public @NotNull IEnergyHandler acquireSharedBackend(IEnergyHandler boundHandler) {
        AE2Handler endpoint = requireEndpoint(boundHandler);
        ReferenceSet<IEnergyService> component = endpoint.componentServices();
        BackendReference reference = findExactComponent(component);
        if (reference == null) {
            reference = new BackendReference(new AE2BackendHandler(endpoint.energyService()), component);
            backends.put(reference.backend, reference);
            for (IEnergyService service : component) {
                identities.put(service, reference);
            }
        }
        if (reference.references == Integer.MAX_VALUE) {
            throw new IllegalStateException("AE2 shared-backend reference count exhausted");
        }
        reference.references++;
        return reference.backend;
    }

    @Override
    public void releaseSharedBackend(IEnergyHandler boundHandler, IEnergyHandler sharedBackend) {
        AE2Handler endpoint = requireEndpoint(boundHandler);
        if (!(sharedBackend instanceof AE2BackendHandler backend)) {
            throw new IllegalArgumentException("AE2 manager received non-AE2 shared backend "
                + sharedBackend.getClass().getName());
        }
        BackendReference reference = backends.get(backend);
        if (reference == null || reference.backend != backend || reference.references == 0) {
            throw new IllegalStateException("AE2 backend reference is inconsistent for endpoint "
                + endpoint.getClass().getName());
        }
        reference.references--;
        if (reference.references == 0) {
            backends.remove(backend);
            removeIdentityMappings(reference);
            backend.close();
        }
    }

    private void removeIdentityMappings(BackendReference retired) {
        identities.reference2ObjectEntrySet().removeIf(entry -> entry.getValue() == retired);
    }

    private BackendReference findExactComponent(ReferenceSet<IEnergyService> component) {
        if (component.isEmpty()) {
            throw new IllegalStateException("AE2 endpoint has an empty overlay component");
        }
        BackendReference candidate = identities.get(component.iterator().next());
        return candidate != null && candidate.matches(component) ? candidate : null;
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

    private static AE2Handler requireEndpoint(IEnergyHandler handler) {
        if (!(handler instanceof AE2Handler endpoint)) {
            throw new IllegalArgumentException("AE2 manager received " + handler.getClass().getName());
        }
        return endpoint;
    }

    private static final class BackendReference {
        private final AE2BackendHandler backend;
        private final ReferenceOpenHashSet<IEnergyService> services;
        private int references;

        private BackendReference(AE2BackendHandler backend, ReferenceSet<IEnergyService> services) {
            this.backend = backend;
            this.services = new ReferenceOpenHashSet<>(services);
        }

        private boolean matches(ReferenceSet<IEnergyService> candidate) {
            return services.size() == candidate.size() && services.containsAll(candidate);
        }
    }
}
