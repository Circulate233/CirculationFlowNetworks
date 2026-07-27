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
    private HandlerBindingPolicy policy;

    private EnergyTransferParticipant() {
    }

    static EnergyTransferParticipant obtain(IEnergyHandler handler,
                                            @Nullable IGrid grid,
                                            @Nullable HubNode.HubMetadata hubMetadata) {
        EnergyTransferParticipant p = POOL.obtain();
        p.handler = handler;
        p.grid = grid;
        p.hubMetadata = hubMetadata;
        p.policy = EnergyHandlerRuntime.policy(handler);
        return p;
    }

    boolean requiresPairMatch() {
        return policy.pairMatching() == HandlerBindingPolicy.PairMatching.REQUIRED;
    }

    IEnergyHandler handler() {
        return handler;
    }

    IEnergyHandler.EnergyType getType() {
        return EnergyHandlerRuntime.type(handler, hubMetadata);
    }

    EnergyAmount canExtractValue() {
        return EnergyHandlerRuntime.canExtract(handler, hubMetadata);
    }

    EnergyAmount canReceiveValue() {
        return EnergyHandlerRuntime.canReceive(handler, hubMetadata);
    }

    boolean canExtract(EnergyTransferParticipant receiveParticipant) {
        return EnergyHandlerRuntime.canExtract(handler, receiveParticipant.handler, hubMetadata);
    }

    boolean canReceive(EnergyTransferParticipant sendParticipant) {
        return EnergyHandlerRuntime.canReceive(handler, sendParticipant.handler, hubMetadata);
    }

    boolean canReceive(EnergyMachineManager.MachineTransferSlot sendSlot) {
        return EnergyHandlerRuntime.canReceive(handler, sendSlot.handler(), hubMetadata);
    }

    EnergyAmount extractEnergy(EnergyAmount maxExtract) {
        return EnergyHandlerRuntime.extract(handler, maxExtract, hubMetadata);
    }

    EnergyAmount receiveEnergy(EnergyAmount maxReceive) {
        EnergyAmount received = EnergyHandlerRuntime.receive(handler, maxReceive, hubMetadata);
        if (!received.isNegative() && received.compareTo(maxReceive) <= 0) {
            return received;
        }
        IllegalStateException violation = new IllegalStateException(
            "Item energy handler returned " + received + " for receive request " + maxReceive
        );
        EnergyHandlerRuntime.logContractViolation(
            handler, "receiveEnergy", EnergyHandlerRuntime.FailureContext.UNKNOWN, violation
        );
        received.recycle();
        throw violation;
    }

    @Nullable
    IGrid grid() {
        return grid;
    }

    void recycle() {
        if (handler == null) {
            return;
        }
        EnergyHandlerRuntime.unbindItem(handler);
        POOL.recycle(this);
    }

    private void reset() {
        handler = null;
        grid = null;
        hubMetadata = null;
        policy = null;
    }
}
