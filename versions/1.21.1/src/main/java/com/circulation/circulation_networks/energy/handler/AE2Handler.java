package com.circulation.circulation_networks.energy.handler;

import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;
import appeng.me.energy.IEnergyOverlayGridConnection;
import appeng.me.service.EnergyService;
import appeng.blockentity.grid.AENetworkedPoweredBlockEntity;
import appeng.blockentity.networking.ControllerBlockEntity;
import appeng.blockentity.networking.EnergyAcceptorBlockEntity;
import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.EnergyAmounts;
import com.circulation.circulation_networks.api.HandlerTickResult;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.manager.HandlerBindingPolicy;
import com.circulation.circulation_networks.manager.HandlerInvalidationSink;
import com.circulation.circulation_networks.network.nodes.HubNode;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;

import java.util.Objects;

/** AE2 endpoint binding. Physical transfers are owned by a shared backend. */
public final class AE2Handler implements IEnergyHandler {

    private static final HandlerBindingPolicy BINDING_POLICY = HandlerBindingPolicy.of(
        HandlerBindingPolicy.TickLifecycle.BEGIN_TICK,
        HandlerBindingPolicy.RoleScope.FIXED,
        HandlerBindingPolicy.MappingScope.SHARED_BACKEND,
        HandlerBindingPolicy.PairMatching.NONE
    );

    @Nullable
    private AENetworkedPoweredBlockEntity blockEntity;
    @Nullable
    private IEnergyService energyService;
    @Nullable
    private HandlerInvalidationSink invalidationSink;
    private final ReferenceOpenHashSet<IEnergyService> componentServices = new ReferenceOpenHashSet<>();
    private final ReferenceOpenHashSet<IEnergyService> componentScratch = new ReferenceOpenHashSet<>();
    private final ReferenceOpenHashSet<IEnergyOverlayGridConnection> connectionScratch = new ReferenceOpenHashSet<>();
    private final ObjectArrayList<EnergyService> serviceQueue = new ObjectArrayList<>();

    @Override
    public HandlerBindingPolicy bindingPolicy() {
        return BINDING_POLICY;
    }

    @Override
    public void bindBlockEntity(BlockEntity blockEntity, HandlerInvalidationSink invalidationSink) {
        if (this.blockEntity != null) {
            throw new IllegalStateException("AE2 endpoint handler is already bound");
        }
        this.invalidationSink = Objects.requireNonNull(invalidationSink, "invalidationSink");
        if (!(blockEntity instanceof ControllerBlockEntity)
            && !(blockEntity instanceof EnergyAcceptorBlockEntity)) {
            throw new IllegalArgumentException("AE2 endpoint requires a controller or energy acceptor");
        }
        AENetworkedPoweredBlockEntity endpoint = (AENetworkedPoweredBlockEntity) blockEntity;
        IEnergyService service = resolveComponent(endpoint, componentServices);
        if (service == null) {
            throw new IllegalStateException("AE2 endpoint is not attached to a grid");
        }
        this.blockEntity = endpoint;
        this.energyService = service;
    }

    @Override
    public HandlerTickResult beginServerTick(long epoch) {
        AENetworkedPoweredBlockEntity endpoint = requireBound();
        componentScratch.clear();
        IEnergyService resolved = resolveComponent(endpoint, componentScratch);
        if (resolved == null) {
            energyService = null;
            return HandlerTickResult.SUSPEND_UNTIL_REBIND;
        }
        if (resolved == energyService && componentServices.equals(componentScratch)) {
            return HandlerTickResult.UNCHANGED;
        }
        energyService = resolved;
        componentServices.clear();
        componentServices.addAll(componentScratch);
        Objects.requireNonNull(invalidationSink, "AE2 endpoint invalidation sink").backendChanged();
        return HandlerTickResult.STATE_CHANGED;
    }

    @Override
    public void endServerTick(long epoch) {
        requireBound();
    }

    @Override
    public void unbindBlockEntity() {
        energyService = null;
        invalidationSink = null;
        blockEntity = null;
        componentServices.clear();
        componentScratch.clear();
        connectionScratch.clear();
        serviceQueue.clear();
    }

    @Override
    public void bindItem(ItemStack itemStack, @Nullable HubNode.HubMetadata hubMetadata) {
        throw new UnsupportedOperationException("AE2 endpoint handlers do not support item bindings");
    }

    @Override
    public void unbindItem() {
        throw new UnsupportedOperationException("AE2 endpoint handlers do not support item bindings");
    }

    @Override
    public EnergyAmount receiveEnergy(EnergyAmount maxReceive, @Nullable HubNode.HubMetadata hubMetadata) {
        return EnergyAmounts.ZERO;
    }

    @Override
    public EnergyAmount extractEnergy(EnergyAmount maxExtract, @Nullable HubNode.HubMetadata hubMetadata) {
        return EnergyAmounts.ZERO;
    }

    @Override
    public EnergyAmount canExtractValue(@Nullable HubNode.HubMetadata hubMetadata) {
        return EnergyAmounts.ZERO;
    }

    @Override
    public EnergyAmount canReceiveValue(@Nullable HubNode.HubMetadata hubMetadata) {
        return EnergyAmounts.ZERO;
    }

    @Override
    public boolean canExtract(IEnergyHandler receiveHandler, @Nullable HubNode.HubMetadata hubMetadata) {
        return false;
    }

    @Override
    public boolean canReceive(IEnergyHandler sendHandler, @Nullable HubNode.HubMetadata hubMetadata) {
        return false;
    }

    @Override
    public EnergyType getType(@Nullable HubNode.HubMetadata hubMetadata) {
        return blockEntity == null ? EnergyType.INVALID : EnergyType.RECEIVE;
    }

    public IEnergyService energyService() {
        requireBound();
        return Objects.requireNonNull(energyService, "AE2 endpoint energy service");
    }

    public ReferenceSet<IEnergyService> componentServices() {
        if (componentServices.isEmpty()) {
            throw new IllegalStateException("AE2 endpoint has no overlay component identity");
        }
        return componentServices;
    }

    private AENetworkedPoweredBlockEntity requireBound() {
        if (blockEntity == null) {
            throw new IllegalStateException("AE2 endpoint handler has no block-entity binding");
        }
        return blockEntity;
    }

    private @Nullable IEnergyService resolveComponent(AENetworkedPoweredBlockEntity endpoint,
                                                       ReferenceSet<IEnergyService> destination) {
        var mainNode = endpoint.getMainNode();
        if (mainNode == null || mainNode.getNode() == null) {
            return null;
        }
        IGrid grid = mainNode.getGrid();
        IEnergyService ownService = grid == null ? null : grid.getEnergyService();
        if (ownService == null) {
            return null;
        }
        destination.add(ownService);
        serviceQueue.clear();
        connectionScratch.clear();
        if (ownService instanceof EnergyService service) {
            serviceQueue.add(service);
        }
        IEnergyOverlayGridConnection endpointConnection = mainNode.getNode()
            .getService(IEnergyOverlayGridConnection.class);
        if (endpointConnection != null) {
            connectionScratch.add(endpointConnection);
        }
        for (int index = 0; index < serviceQueue.size(); index++) {
            connectionScratch.addAll(serviceQueue.get(index).getOverlayGridConnections());
        }
        for (IEnergyOverlayGridConnection connection : connectionScratch) {
            for (EnergyService connected : connection.connectedEnergyServices()) {
                if (destination.add(connected)) {
                    serviceQueue.add(connected);
                }
            }
        }
        for (int index = 0; index < serviceQueue.size(); index++) {
            for (IEnergyOverlayGridConnection connection : serviceQueue.get(index).getOverlayGridConnections()) {
                if (!connectionScratch.add(connection)) {
                    continue;
                }
                for (EnergyService connected : connection.connectedEnergyServices()) {
                    if (destination.add(connected)) {
                        serviceQueue.add(connected);
                    }
                }
            }
        }
        return ownService;
    }
}
