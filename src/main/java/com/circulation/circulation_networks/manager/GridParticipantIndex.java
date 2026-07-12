package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.api.IGrid;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Grid-owned index for {@link EnergyMachineManager.MachineTransferSlot}.
 * Participant-owned memberships provide direct bucket lookup for removal and
 * role migration.
 */
public final class GridParticipantIndex {

    /** Default transfer priority used when no explicit priority is supplied. */
    public static final int DEFAULT_PRIORITY = PriorityRoleIndex.DEFAULT_PRIORITY;

    @Nullable
    private final IGrid ownerGrid;
    private final PriorityRoleIndex send = new PriorityRoleIndex();
    private final PriorityRoleIndex storage = new PriorityRoleIndex();
    private final PriorityRoleIndex receive = new PriorityRoleIndex();
    private int size;
    private long additions;
    private long removals;
    private long moves;
    private boolean routingActive;
    private long routingEpoch = Long.MIN_VALUE;
    @Nullable
    private UUID channelId;

    public GridParticipantIndex(IGrid ownerGrid) {
        this.ownerGrid = Objects.requireNonNull(ownerGrid, "ownerGrid");
    }

    public PriorityRoleIndex send() {
        return send;
    }

    public PriorityRoleIndex storage() {
        return storage;
    }

    public PriorityRoleIndex receive() {
        return receive;
    }

    public PriorityRoleIndex role(IEnergyHandler.EnergyType role) {
        return roleIndex(role);
    }

    public void add(IEnergyHandler.EnergyType role, EnergyMachineManager.MachineTransferSlot participant) {
        add(role, participant, DEFAULT_PRIORITY);
    }

    public void add(IEnergyHandler.EnergyType role, EnergyMachineManager.MachineTransferSlot participant, int priority) {
        Objects.requireNonNull(participant, "participant");
        requireStructuralMutationAllowed();
        GridParticipantMembership membership = participant.membership();
        if (membership.isBound()) {
            throw new IllegalStateException("Participant is already registered in a grid index");
        }
        PriorityRoleIndex roleIndex = roleIndex(role);
        if (ownerGrid != null) {
            ChannelParticipantIndex.INSTANCE.validateGridParticipantAddition(ownerGrid, participant, role, priority);
        }
        roleIndex.add(participant, priority);
        membership.bind(this, roleIndex, roleIndex.insertedBucket(priority), role, priority);
        size++;
        if (ownerGrid != null) {
            ChannelParticipantIndex.INSTANCE.onGridParticipantAdded(ownerGrid, participant, role, priority);
            LocalParticipantRoutingIndex.INSTANCE.refresh(ownerGrid);
        }
        additions++;
    }

    public boolean remove(EnergyMachineManager.MachineTransferSlot participant) {
        if (participant == null) {
            return false;
        }
        requireStructuralMutationAllowed();
        GridParticipantMembership membership = participant.membership();
        if (membership.owner() != this) {
            return false;
        }
        PriorityRoleIndex roleIndex = requireRoleIndex(membership);
        if (ownerGrid != null) {
            ChannelParticipantIndex.INSTANCE.validateGridParticipantRemoval(ownerGrid, participant);
            ChannelParticipantIndex.INSTANCE.onGridParticipantRemoved(ownerGrid, participant);
        }
        if (!roleIndex.remove(participant)) {
            throw new IllegalStateException("Participant membership is inconsistent with its role index");
        }
        membership.clear(this);
        size--;
        if (ownerGrid != null) {
            LocalParticipantRoutingIndex.INSTANCE.refresh(ownerGrid);
        }
        removals++;
        return true;
    }

    public void move(EnergyMachineManager.MachineTransferSlot participant, int priority) {
        Objects.requireNonNull(participant, "participant");
        requireStructuralMutationAllowed();
        GridParticipantMembership membership = requireMembership(participant);
        IEnergyHandler.EnergyType role = Objects.requireNonNull(membership.role(), "participant membership role");
        PriorityRoleIndex roleIndex = roleIndex(role);
        if (ownerGrid != null) {
            ChannelParticipantIndex.INSTANCE.validateGridParticipantMove(ownerGrid, participant, role, priority);
        }
        PriorityRoleIndex.Bucket destinationBucket = roleIndex.transferTo(participant, roleIndex, priority);
        membership.move(roleIndex, destinationBucket, role, priority);
        if (ownerGrid != null) {
            ChannelParticipantIndex.INSTANCE.onGridParticipantMoved(ownerGrid, participant, role, priority);
        }
        moves++;
    }

    public void move(EnergyMachineManager.MachineTransferSlot participant, IEnergyHandler.EnergyType role, int priority) {
        Objects.requireNonNull(participant, "participant");
        Objects.requireNonNull(role, "role");
        requireStructuralMutationAllowed();
        GridParticipantMembership membership = requireMembership(participant);
        if (membership.role() == role) {
            move(participant, priority);
            return;
        }
        PriorityRoleIndex source = requireRoleIndex(membership);
        PriorityRoleIndex destination = roleIndex(role);
        if (ownerGrid != null) {
            ChannelParticipantIndex.INSTANCE.validateGridParticipantMove(ownerGrid, participant, role, priority);
        }
        PriorityRoleIndex.Bucket destinationBucket = source.transferTo(participant, destination, priority);
        membership.move(destination, destinationBucket, role, priority);
        if (ownerGrid != null) {
            ChannelParticipantIndex.INSTANCE.onGridParticipantMoved(ownerGrid, participant, role, priority);
        }
        moves++;
    }

    public IEnergyHandler.EnergyType roleOf(EnergyMachineManager.MachineTransferSlot participant) {
        if (participant == null || participant.membership().owner() != this) {
            return null;
        }
        return participant.membership().role();
    }

    public int priorityOf(EnergyMachineManager.MachineTransferSlot participant) {
        return requireMembership(Objects.requireNonNull(participant, "participant")).priority();
    }

    public int size() {
        return size;
    }

    /**
     * Captures participant mutation counters for stable-routing regression
     * tests. It is sampled only by tests and is not part of the tick path.
     */
    @SuppressWarnings("unused")
    StructuralMetrics structuralMetrics() {
        return new StructuralMetrics(additions, removals, moves);
    }

    public void beginRouting(long epoch) {
        if (routingActive && routingEpoch != epoch) {
            throw new IllegalStateException("Grid routing is already active for another epoch");
        }
        routingActive = true;
        routingEpoch = epoch;
    }

    public void endRouting(long epoch) {
        if (!routingActive || routingEpoch != epoch) {
            throw new IllegalStateException("Grid routing epoch does not match the active epoch");
        }
        routingActive = false;
        routingEpoch = Long.MIN_VALUE;
    }

    public boolean isRoutingActive() {
        return routingActive;
    }

    public @Nullable UUID channelId() {
        return channelId;
    }

    public void setChannelBinding(UUID channelId) {
        Objects.requireNonNull(channelId, "channelId");
        requireStructuralMutationAllowed();
        if (this.channelId != null && !this.channelId.equals(channelId)) {
            throw new IllegalStateException("Grid is already bound to another channel");
        }
        this.channelId = channelId;
        if (ownerGrid != null) {
            LocalParticipantRoutingIndex.INSTANCE.refresh(ownerGrid);
        }
    }

    public void clearChannelBinding(UUID channelId) {
        Objects.requireNonNull(channelId, "channelId");
        requireStructuralMutationAllowed();
        if (!channelId.equals(this.channelId)) {
            throw new IllegalStateException("Grid channel binding does not match the channel being removed");
        }
        this.channelId = null;
        if (ownerGrid != null) {
            LocalParticipantRoutingIndex.INSTANCE.refresh(ownerGrid);
        }
    }

    public void clear() {
        requireStructuralMutationAllowed();
        if (ownerGrid != null) {
            ChannelParticipantIndex.INSTANCE.detachGrid(ownerGrid);
        }
        send.clear();
        storage.clear();
        receive.clear();
        size = 0;
        routingActive = false;
        routingEpoch = Long.MIN_VALUE;
        if (ownerGrid != null) {
            LocalParticipantRoutingIndex.INSTANCE.remove(ownerGrid);
        }
    }

    private GridParticipantMembership requireMembership(EnergyMachineManager.MachineTransferSlot participant) {
        GridParticipantMembership membership = participant.membership();
        if (membership.owner() != this) {
            throw new IllegalStateException("Participant is not registered in this grid index");
        }
        return membership;
    }

    private void requireStructuralMutationAllowed() {
        if (routingActive) {
            throw new IllegalStateException("Cannot mutate grid participant membership while routing is active for epoch " + routingEpoch);
        }
    }

    private PriorityRoleIndex roleIndex(IEnergyHandler.EnergyType role) {
        Objects.requireNonNull(role, "role");
        return switch (role) {
            case SEND -> send;
            case STORAGE -> storage;
            case RECEIVE -> receive;
            case INVALID -> throw new IllegalArgumentException("INVALID is not a participant role");
        };
    }

    private PriorityRoleIndex requireRoleIndex(GridParticipantMembership membership) {
        PriorityRoleIndex roleIndex = membership.ownerRoleIndex();
        return roleIndex;
    }

    static final class StructuralMetrics {
        final long additions;
        final long removals;
        final long moves;

        private StructuralMetrics(long additions, long removals, long moves) {
            this.additions = additions;
            this.removals = removals;
            this.moves = moves;
        }
    }
}
