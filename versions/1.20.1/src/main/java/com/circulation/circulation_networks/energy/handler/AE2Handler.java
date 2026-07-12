package com.circulation.circulation_networks.energy.handler;

import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;
import appeng.me.energy.IEnergyOverlayGridConnection;
import appeng.me.service.EnergyService;
import appeng.blockentity.grid.AENetworkPowerBlockEntity;
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

/**
 * AE2 endpoint binding. Transfer state belongs to an independent backend keyed
 * by {@link IEnergyService} identity; this object only observes endpoint grid
 * changes and never owns a transfer budget.
 */
public final class AE2Handler implements IEnergyHandler {

    private static final HandlerBindingPolicy BINDING_POLICY = HandlerBindingPolicy.of(
        HandlerBindingPolicy.TickLifecycle.BEGIN_TICK,
        HandlerBindingPolicy.RoleScope.FIXED,
        HandlerBindingPolicy.MappingScope.SHARED_BACKEND,
        HandlerBindingPolicy.PairMatching.NONE
    );

    @Nullable
    private AENetworkPowerBlockEntity blockEntity;
    @Nullable
    private IEnergyService energyService;
    @Nullable
    private HandlerInvalidationSink invalidationSink;
    private final ReferenceOpenHashSet<IEnergyService> componentServices = new ReferenceOpenHashSet<>();
    private final ReferenceOpenHashSet<IEnergyService> componentScratch = new ReferenceOpenHashSet<>();
    private final ReferenceOpenHashSet<IEnergyOverlayGridConnection> connectionScratch = new ReferenceOpenHashSet<>();
    private final ObjectArrayList<EnergyService> serviceQueue = new ObjectArrayList<>();
    private boolean itemBound;

    @Override
    public HandlerBindingPolicy bindingPolicy() {
        return BINDING_POLICY;
    }

    @Override
    public void bindBlockEntity(BlockEntity blockEntity, HandlerInvalidationSink invalidationSink) {
        if (this.blockEntity != null || itemBound) {
            throw new IllegalStateException("AE2 endpoint handler is already bound");
        }
        this.invalidationSink = Objects.requireNonNull(invalidationSink, "invalidationSink");
        if (!(blockEntity instanceof ControllerBlockEntity)
            && !(blockEntity instanceof EnergyAcceptorBlockEntity)) {
            throw new IllegalArgumentException("AE2 endpoint requires a controller or energy acceptor");
        }
        this.blockEntity = (AENetworkPowerBlockEntity) blockEntity;
        energyService = resolveComponent(this.blockEntity, componentServices);
        if (energyService == null || componentServices.isEmpty()) {
            this.blockEntity = null;
            this.invalidationSink = null;
            throw new IllegalStateException("AE2 endpoint is not attached to an energy service");
        }
    }

    @Override
    public HandlerTickResult beginServerTick(long epoch) {
        AENetworkPowerBlockEntity endpoint = requireBlockBinding();
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
        requireBlockBinding();
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
        if (blockEntity != null || itemBound) {
            throw new IllegalStateException("AE2 endpoint handler is already bound");
        }
        itemBound = true;
    }

    @Override
    public void unbindItem() {
        itemBound = false;
    }

    public IEnergyService energyService() {
        return Objects.requireNonNull(energyService, "AE2 endpoint energy service");
    }

    public ReferenceSet<IEnergyService> componentServices() {
        if (componentServices.isEmpty()) {
            throw new IllegalStateException("AE2 endpoint has no overlay component identity");
        }
        return componentServices;
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
        return blockEntity == null || energyService == null ? EnergyType.INVALID : EnergyType.RECEIVE;
    }

    private AENetworkPowerBlockEntity requireBlockBinding() {
        if (blockEntity == null) {
            throw new IllegalStateException("AE2 endpoint handler has no block-entity binding");
        }
        return blockEntity;
    }

    private @Nullable IEnergyService resolveComponent(AENetworkPowerBlockEntity blockEntity,
                                                       ReferenceSet<IEnergyService> destination) {
        var mainNode = blockEntity.getMainNode();
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
