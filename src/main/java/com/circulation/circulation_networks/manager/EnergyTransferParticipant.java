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
        new ObjectPool<>(EnergyTransferParticipant::new, EnergyTransferParticipant::reset, MAX_POOL_SIZE, EnergyTransferParticipant[]::new);

    private IEnergyHandler handler;
    @Nullable
    private IGrid grid;
    @Nullable
    private HubNode.HubMetadata hubMetadata;
    @Nullable
    private EnergyMachineManager.Interaction interaction;
    private boolean recycleHandlerOnRecycle;
    // Cached once in obtain() so the hot transfer loop reads a field instead of re-querying
    // the handler for every sender/receiver pairing.
    private boolean pairMatch;

    private EnergyTransferParticipant() {
    }

    static EnergyTransferParticipant obtain(IEnergyHandler handler,
                                            @Nullable IGrid grid,
                                            @Nullable HubNode.HubMetadata hubMetadata) {
        return obtain(handler, grid, hubMetadata, true);
    }

    static EnergyTransferParticipant obtain(IEnergyHandler handler,
                                            @Nullable IGrid grid,
                                            @Nullable HubNode.HubMetadata hubMetadata,
                                            boolean recycleHandlerOnRecycle) {
        EnergyTransferParticipant p = POOL.obtain();
        p.handler = handler;
        p.grid = grid;
        p.hubMetadata = hubMetadata;
        p.recycleHandlerOnRecycle = recycleHandlerOnRecycle;
        p.pairMatch = handler.requiresPairMatch(hubMetadata);
        return p;
    }

    void setInteraction(@Nullable EnergyMachineManager.Interaction interaction) {
        this.interaction = interaction;
    }

    /**
     * Whether this participant opts into precise per-pair canExtract/canReceive matching.
     * Default handlers return false, letting the transfer loop skip the per-pair predicate
     * (the value-zero checks already gate transfers).
     */
    boolean requiresPairMatch() {
        return pairMatch;
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
        // Neutral-state guard (see ObjectPool): a live participant always has a non-null
        // handler (set in obtain). Once recycled, reset() nulls it, so this cheaply absorbs
        // a redundant recycle without the pool needing a contains check — and also avoids a
        // NPE on the handler.clear() below.
        if (handler == null) {
            return;
        }
        if (recycleHandlerOnRecycle) {
            handler.clear();
        }
        POOL.recycle(this);
    }

    private void reset() {
        handler = null;
        grid = null;
        hubMetadata = null;
        interaction = null;
        recycleHandlerOnRecycle = false;
        pairMatch = false;
    }
}
