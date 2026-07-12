package com.circulation.circulation_networks.energy.handler;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.tile.grid.AENetworkPowerTile;
import appeng.tile.networking.TileController;
import appeng.tile.networking.TileEnergyAcceptor;
import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.HandlerTickResult;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.manager.EnergyHandlerNotReadyException;
import com.circulation.circulation_networks.manager.HandlerBindingPolicy;
import com.circulation.circulation_networks.manager.HandlerInvalidationSink;
import com.circulation.circulation_networks.network.nodes.HubNode;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class AE2Handler implements IEnergyHandler {

    private static final HandlerBindingPolicy BINDING_POLICY = HandlerBindingPolicy.of(
        HandlerBindingPolicy.TickLifecycle.BEGIN_TICK,
        HandlerBindingPolicy.RoleScope.FIXED,
        HandlerBindingPolicy.MappingScope.SHARED_BACKEND,
        HandlerBindingPolicy.PairMatching.NONE
    );

    @Nullable
    private AENetworkPowerTile blockEntity;
    @Nullable
    private IEnergyGrid backendIdentity;
    @Nullable
    private IGridNode node;
    @Nullable
    private IGrid gridIdentity;
    @Nullable
    private HandlerInvalidationSink invalidationSink;
    private long activeEpoch = Long.MIN_VALUE;

    private static IllegalStateException endpointTransferCall() {
        return new IllegalStateException("AE2 endpoint handler cannot be used as a transfer backend");
    }

    @Override
    public HandlerBindingPolicy bindingPolicy() {
        return BINDING_POLICY;
    }

    @Override
    public void bindBlockEntity(TileEntity tileEntity, HandlerInvalidationSink invalidationSink) {
        if (blockEntity != null) {
            throw new IllegalStateException("AE2 endpoint handler is already bound");
        }
        TileEntity boundTileEntity = Objects.requireNonNull(tileEntity, "tileEntity");
        if (!(boundTileEntity instanceof TileController) && !(boundTileEntity instanceof TileEnergyAcceptor)) {
            throw new IllegalArgumentException("AE2 endpoint is not an AE power tile");
        }
        IGridNode node = ((AENetworkPowerTile) boundTileEntity).getProxy().getNode();
        if (node == null) {
            throw new EnergyHandlerNotReadyException("AE2 endpoint network node is not ready");
        }
        IGrid gridIdentity = node.getGrid();
        if (gridIdentity == null) {
            throw new EnergyHandlerNotReadyException("AE2 endpoint energy grid is not ready");
        }
        IEnergyGrid grid = gridIdentity.getCache(IEnergyGrid.class);
        if (grid == null) {
            throw new EnergyHandlerNotReadyException("AE2 endpoint energy-grid cache is not ready");
        }
        blockEntity = (AENetworkPowerTile) boundTileEntity;
        this.node = node;
        this.gridIdentity = gridIdentity;
        backendIdentity = grid;
        this.invalidationSink = Objects.requireNonNull(invalidationSink, "invalidationSink");
    }

    @Override
    public HandlerTickResult beginServerTick(long epoch) {
        if (blockEntity == null || invalidationSink == null) {
            throw new IllegalStateException("AE2 endpoint handler has no block-entity binding");
        }
        if (epoch <= activeEpoch) {
            throw new IllegalArgumentException("AE2 endpoint epoch must increase: previous " + activeEpoch + ", got " + epoch);
        }
        activeEpoch = epoch;
        IGridNode boundNode = node;
        IGrid currentGrid = boundNode == null ? null : boundNode.getGrid();
        if (currentGrid == null) {
            gridIdentity = null;
            backendIdentity = null;
            return HandlerTickResult.SUSPEND_UNTIL_REBIND;
        }
        if (currentGrid != gridIdentity) {
            IEnergyGrid current = currentGrid.getCache(IEnergyGrid.class);
            if (current == null) {
                gridIdentity = null;
                backendIdentity = null;
                return HandlerTickResult.SUSPEND_UNTIL_REBIND;
            }
            gridIdentity = currentGrid;
            backendIdentity = current;
            invalidationSink.backendChanged();
            return HandlerTickResult.STATE_CHANGED;
        }
        return HandlerTickResult.UNCHANGED;
    }

    @Override
    public void endServerTick(long epoch) {
        throw new IllegalStateException("AE2 endpoint handler uses begin-only tick lifecycle");
    }

    @Override
    public void unbindBlockEntity() {
        blockEntity = null;
        node = null;
        gridIdentity = null;
        backendIdentity = null;
        invalidationSink = null;
        activeEpoch = Long.MIN_VALUE;
    }

    public IEnergyGrid backendIdentity() {
        return Objects.requireNonNull(backendIdentity, "AE2 endpoint has no backend identity");
    }

    @Override
    public void bindItem(ItemStack itemStack, @Nullable HubNode.HubMetadata hubMetadata) {
        throw new UnsupportedOperationException("AE2 does not support item energy bindings");
    }

    @Override
    public void unbindItem() {
        throw new UnsupportedOperationException("AE2 does not support item energy bindings");
    }

    @Override
    public EnergyAmount receiveEnergy(EnergyAmount maxReceive, @Nullable HubNode.HubMetadata hubMetadata) {
        throw endpointTransferCall();
    }

    @Override
    public EnergyAmount extractEnergy(EnergyAmount maxExtract, @Nullable HubNode.HubMetadata hubMetadata) {
        throw endpointTransferCall();
    }

    @Override
    public EnergyAmount canExtractValue(@Nullable HubNode.HubMetadata hubMetadata) {
        throw endpointTransferCall();
    }

    @Override
    public EnergyAmount canReceiveValue(@Nullable HubNode.HubMetadata hubMetadata) {
        throw endpointTransferCall();
    }

    @Override
    public boolean canExtract(IEnergyHandler receiveHandler, @Nullable HubNode.HubMetadata hubMetadata) {
        return false;
    }

    @Override
    public boolean canReceive(IEnergyHandler sendHandler, @Nullable HubNode.HubMetadata hubMetadata) {
        return true;
    }

    @Override
    public EnergyType getType(@Nullable HubNode.HubMetadata hubMetadata) {
        return EnergyType.RECEIVE;
    }
}
