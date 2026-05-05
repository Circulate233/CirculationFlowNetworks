package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.api.IGrid;
import com.circulation.circulation_networks.network.nodes.HubNode;
import com.circulation.circulation_networks.utils.ObjectPool;

import org.jetbrains.annotations.Nullable;

final class EnergyTransferParticipant {

    private static final int MAX_POOL_SIZE = 4096;
    private static final ObjectPool<EnergyTransferParticipant> POOL =
        new ObjectPool<>(EnergyTransferParticipant::new, EnergyTransferParticipant::reset, MAX_POOL_SIZE);

    private IEnergyHandler handler;
    @Nullable
    private IGrid grid;
    @Nullable
    private HubNode.HubMetadata hubMetadata;
    @Nullable
    private EnergyMachineManager.Interaction interaction;
    private boolean recycleHandlerOnRecycle;

    private EnergyTransferParticipant() {
    }

    static EnergyTransferParticipant obtain(IEnergyHandler handler,
                                            @Nullable IGrid grid,
                                            @Nullable HubNode.HubMetadata hubMetadata,
                                            @Nullable EnergyMachineManager.Interaction interaction) {
        return obtain(handler, grid, hubMetadata, interaction, true);
    }

    static EnergyTransferParticipant obtain(IEnergyHandler handler,
                                            @Nullable IGrid grid,
                                            @Nullable HubNode.HubMetadata hubMetadata,
                                            @Nullable EnergyMachineManager.Interaction interaction,
                                            boolean recycleHandlerOnRecycle) {
        EnergyTransferParticipant p = POOL.obtain();
        p.handler = handler;
        p.grid = grid;
        p.hubMetadata = hubMetadata;
        p.interaction = interaction;
        p.recycleHandlerOnRecycle = recycleHandlerOnRecycle;
        return p;
    }

    IEnergyHandler.EnergyType getType() {
        return handler.getType(hubMetadata);
    }

    EnergyAmount canExtractValue() {
        return handler.canExtractValue(hubMetadata);
    }

    EnergyAmount canReceiveValue() {
        return handler.canReceiveValue(hubMetadata);
    }

    boolean canExtract(EnergyTransferParticipant receiveParticipant) {
        return handler.canExtract(receiveParticipant.handler, hubMetadata);
    }

    boolean canReceive(EnergyTransferParticipant sendParticipant) {
        return handler.canReceive(sendParticipant.handler, hubMetadata);
    }

    EnergyAmount extractEnergy(EnergyAmount maxExtract) {
        return handler.extractEnergy(maxExtract, hubMetadata);
    }

    EnergyAmount receiveEnergy(EnergyAmount maxReceive) {
        return handler.receiveEnergy(maxReceive, hubMetadata);
    }

    @Nullable
    EnergyMachineManager.Interaction interaction() {
        return interaction;
    }

    @Nullable
    IGrid grid() {
        return grid;
    }

    void recycle() {
        if (recycleHandlerOnRecycle) {
            handler.recycle();
        }
        POOL.recycle(this);
    }

    private void reset() {
        handler = null;
        grid = null;
        hubMetadata = null;
        interaction = null;
        recycleHandlerOnRecycle = false;
    }
}
