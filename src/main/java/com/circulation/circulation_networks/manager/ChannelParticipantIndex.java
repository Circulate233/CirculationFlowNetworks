package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.api.IGrid;
import com.circulation.circulation_networks.network.nodes.HubNode;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import it.unimi.dsi.fastutil.objects.ReferenceSets;

import java.util.Objects;
import java.util.UUID;

/**
 * Channel registry. Grid-to-channel migration never alters grid
 * buckets, so traversal remains stable while the separate channel membership
 * is rebuilt through participant-owned bucket references.
 */
public final class ChannelParticipantIndex {

    /** Shared server channel registry. */
    public static final ChannelParticipantIndex INSTANCE = new ChannelParticipantIndex();

    private ChannelParticipantIndex() {
    }

    private final Object2ObjectOpenHashMap<UUID, ChannelEntry> channels = new Object2ObjectOpenHashMap<>();
    private final Reference2ObjectOpenHashMap<IGrid, UUID> gridChannels = new Reference2ObjectOpenHashMap<>();
    private final ObjectArrayList<ChannelEntry> routingChannels = new ObjectArrayList<>();
    private long participantAdditions;
    private long participantRemovals;
    private long participantMoves;
    public void migrateGrid(IGrid grid, UUID oldChannelId, UUID newChannelId) {
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(oldChannelId, "oldChannelId");
        Objects.requireNonNull(newChannelId, "newChannelId");
        UUID currentChannelId = gridChannels.get(grid);
        if (currentChannelId != null && !currentChannelId.equals(oldChannelId)) {
            throw new IllegalStateException("Grid channel migration does not match its current binding");
        }
        if (oldChannelId.equals(newChannelId) && currentChannelId != null) {
            return;
        }
        ChannelEntry source = currentChannelId == null ? null : requireChannel(currentChannelId);
        ChannelEntry destination = isUnbound(newChannelId) ? null : channels.get(newChannelId);
        validateGridMigration(grid, source, destination);
        if (currentChannelId != null) {
            removeGridFromChannel(grid, currentChannelId);
        }
        if (!isUnbound(newChannelId)) {
            try {
                addGridToChannel(grid, newChannelId);
            } catch (RuntimeException exception) {
                if (currentChannelId != null) {
                    try {
                        addGridToChannel(grid, currentChannelId);
                    } catch (RuntimeException rollbackException) {
                        exception.addSuppressed(rollbackException);
                    }
                }
                throw exception;
            }
        }
    }
    public void detachGrid(IGrid grid) {
        Objects.requireNonNull(grid, "grid");
        UUID channelId = gridChannels.get(grid);
        if (channelId != null) {
            removeGridFromChannel(grid, channelId);
        }
    }
    public void onGridParticipantAdded(IGrid grid,
                                       EnergyMachineManager.MachineTransferSlot participant,
                                       IEnergyHandler.EnergyType role,
                                       int priority) {
        UUID channelId = gridChannels.get(Objects.requireNonNull(grid, "grid"));
        if (channelId == null) {
            return;
        }
        ChannelEntry entry = requireChannel(channelId);
        entry.add(participant, role, priority);
    }
    public void validateGridParticipantAddition(IGrid grid,
                                                EnergyMachineManager.MachineTransferSlot participant,
                                                IEnergyHandler.EnergyType role,
                                                int priority) {
        ChannelEntry entry = entryForGrid(grid);
        if (entry != null) {
            entry.validateAddition(participant, role, priority);
        }
    }
    public void onGridParticipantRemoved(IGrid grid, EnergyMachineManager.MachineTransferSlot participant) {
        UUID channelId = gridChannels.get(Objects.requireNonNull(grid, "grid"));
        if (channelId == null) {
            return;
        }
        requireChannel(channelId).remove(participant);
    }
    public void validateGridParticipantRemoval(IGrid grid, EnergyMachineManager.MachineTransferSlot participant) {
        ChannelEntry entry = entryForGrid(grid);
        if (entry != null) {
            entry.validateRemoval(participant);
        }
    }
    public void onGridParticipantMoved(IGrid grid,
                                       EnergyMachineManager.MachineTransferSlot participant,
                                       IEnergyHandler.EnergyType role,
                                       int priority) {
        UUID channelId = gridChannels.get(Objects.requireNonNull(grid, "grid"));
        if (channelId == null) {
            return;
        }
        requireChannel(channelId).move(participant, role, priority);
    }
    public void validateGridParticipantMove(IGrid grid,
                                            EnergyMachineManager.MachineTransferSlot participant,
                                            IEnergyHandler.EnergyType role,
                                            int priority) {
        ChannelEntry entry = entryForGrid(grid);
        if (entry != null) {
            entry.validateMove(participant, role, priority);
        }
    }
    public ChannelEntry channel(UUID channelId) {
        return channelView(channels.get(Objects.requireNonNull(channelId, "channelId")));
    }
    public int routingChannelCount() {
        return routingChannels.size();
    }

    /**
     * Captures channel participant mutation counters for stable-routing
     * regression tests. It is sampled only by tests and never by routing ticks.
     */
    @SuppressWarnings("unused")
    StructuralMetrics structuralMetrics() {
        return new StructuralMetrics(participantAdditions, participantRemovals, participantMoves);
    }
    public ChannelEntry routingChannelAt(int index) {
        return channelView(routingChannels.get(index));
    }
    public void beginRouting(UUID channelId, long epoch) {
        requireChannel(channelId).beginRouting(epoch);
    }
    public void endRouting(UUID channelId, long epoch) {
        requireChannel(channelId).endRouting(epoch);
    }
    public void onServerStop() {
        ObjectArrayList<IGrid> grids = new ObjectArrayList<>(gridChannels.keySet());
        RuntimeException failure = null;
        try {
            for (int index = 0; index < grids.size(); index++) {
                IGrid grid = grids.get(index);
                try {
                    detachGrid(grid);
                } catch (RuntimeException exception) {
                    if (failure == null) {
                        failure = exception;
                    } else {
                        failure.addSuppressed(exception);
                    }
                }
            }
            if (!routingChannels.isEmpty()) {
                IllegalStateException exception = new IllegalStateException(
                    "Routing channels remained after all grid bindings were removed"
                );
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        } finally {
            gridChannels.clear();
            routingChannels.clear();
            channels.clear();
            grids.clear();
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void addGridToChannel(IGrid grid, UUID channelId) {
        ChannelEntry entry = channels.get(channelId);
        boolean newChannel = entry == null;
        if (entry == null) {
            entry = new ChannelEntry(channelId);
        }
        entry.validateGridAddition(grid);
        try {
            mirrorGrid(entry, grid);
            grid.getParticipantIndex().setChannelBinding(channelId);
        } catch (RuntimeException exception) {
            rollbackMirroredGrid(entry, grid, exception);
            throw exception;
        }
        entry.addGrid(grid);
        gridChannels.put(grid, channelId);
        if (newChannel) {
            entry.routingIndex = routingChannels.size();
            routingChannels.add(entry);
            channels.put(channelId, entry);
        }
    }

    private void removeGridFromChannel(IGrid grid, UUID channelId) {
        ChannelEntry entry = requireChannel(channelId);
        entry.validateGridRemoval(grid);
        try {
            removeMirroredGrid(entry, grid);
            grid.getParticipantIndex().clearChannelBinding(channelId);
        } catch (RuntimeException exception) {
            restoreMirroredGrid(entry, grid, exception);
            throw exception;
        }
        entry.removeGrid(grid);
        gridChannels.remove(grid);
        if (entry.grids.isEmpty()) {
            entry.clear();
            channels.remove(channelId);
            removeRoutingChannel(entry);
        }
    }

    private void removeRoutingChannel(ChannelEntry entry) {
        int removeIndex = entry.routingIndex;
        int lastIndex = routingChannels.size() - 1;
        if (removeIndex < 0 || removeIndex > lastIndex || routingChannels.get(removeIndex) != entry) {
            throw new IllegalStateException("Channel routing index is inconsistent");
        }
        ChannelEntry last = routingChannels.remove(lastIndex);
        if (removeIndex < lastIndex) {
            routingChannels.set(removeIndex, last);
            last.routingIndex = removeIndex;
        }
        entry.routingIndex = -1;
    }

    private void mirrorGrid(ChannelEntry entry, IGrid grid) {
        mirrorRole(entry, grid.getParticipantIndex().send());
        mirrorRole(entry, grid.getParticipantIndex().storage());
        mirrorRole(entry, grid.getParticipantIndex().receive());
    }

    private void mirrorRole(ChannelEntry entry, PriorityRoleIndex roleIndex) {
        for (PriorityRoleIndex.Bucket bucket = roleIndex.firstBucket(); bucket != null; bucket = bucket.next()) {
            for (int index = bucket.firstAliveIndex(); index >= 0; index = bucket.nextAliveIndex(index)) {
                EnergyMachineManager.MachineTransferSlot participant = bucket.participantAt(index);
                entry.add(participant, participant.membership().role(), participant.membership().priority());
            }
        }
    }

    private void removeMirroredGrid(ChannelEntry entry, IGrid grid) {
        removeMirroredRole(entry, grid.getParticipantIndex().send());
        removeMirroredRole(entry, grid.getParticipantIndex().storage());
        removeMirroredRole(entry, grid.getParticipantIndex().receive());
    }

    private void removeMirroredRole(ChannelEntry entry, PriorityRoleIndex roleIndex) {
        for (PriorityRoleIndex.Bucket bucket = roleIndex.firstBucket(); bucket != null; bucket = bucket.next()) {
            for (int index = bucket.firstAliveIndex(); index >= 0; index = bucket.nextAliveIndex(index)) {
                entry.remove(bucket.participantAt(index));
            }
        }
    }

    private ChannelEntry requireChannel(UUID channelId) {
        ChannelEntry entry = channels.get(Objects.requireNonNull(channelId, "channelId"));
        if (entry == null) {
            throw new IllegalStateException("Channel has no participant index");
        }
        return entry;
    }

    private ChannelEntry channelView(ChannelEntry entry) {
        return entry;
    }

    private ChannelEntry entryForGrid(IGrid grid) {
        UUID channelId = gridChannels.get(Objects.requireNonNull(grid, "grid"));
        return channelId == null ? null : requireChannel(channelId);
    }

    private void validateGridMigration(IGrid grid, ChannelEntry source, ChannelEntry destination) {
        if (grid.getParticipantIndex().isRoutingActive()) {
            throw new IllegalStateException("Cannot migrate grid channel membership while local routing is active");
        }
        if (source != null) {
            source.validateGridRemoval(grid);
        }
        if (destination != null) {
            destination.validateGridMigrationTarget(grid, source);
        }
    }

    private void rollbackMirroredGrid(ChannelEntry entry, IGrid grid, RuntimeException exception) {
        try {
            removeMirroredGrid(entry, grid);
        } catch (RuntimeException rollbackException) {
            exception.addSuppressed(rollbackException);
        }
    }

    private void restoreMirroredGrid(ChannelEntry entry, IGrid grid, RuntimeException exception) {
        try {
            mirrorGrid(entry, grid);
        } catch (RuntimeException rollbackException) {
            exception.addSuppressed(rollbackException);
        }
    }

    private static boolean isUnbound(UUID channelId) {
        return HubNode.EMPTY.equals(channelId);
    }

    final class ChannelEntry {

        private final UUID channelId;
        private final PriorityRoleIndex send = new PriorityRoleIndex(ParticipantMembershipScope.CHANNEL);
        private final PriorityRoleIndex storage = new PriorityRoleIndex(ParticipantMembershipScope.CHANNEL);
        private final PriorityRoleIndex receive = new PriorityRoleIndex(ParticipantMembershipScope.CHANNEL);
        private final ReferenceSet<IGrid> grids = new ReferenceOpenHashSet<>();
        private final ReferenceSet<IGrid> gridView = ReferenceSets.unmodifiable(grids);
        private final ObjectArrayList<IGrid> gridList = new ObjectArrayList<>();
        private final Reference2IntOpenHashMap<IGrid> gridPositions = new Reference2IntOpenHashMap<>();
        private int routingIndex = -1;
        private boolean routingActive;
        private long routingEpoch = Long.MIN_VALUE;
        private long chargingEpoch = Long.MIN_VALUE;

        private ChannelEntry(UUID channelId) {
            this.channelId = channelId;
            gridPositions.defaultReturnValue(-1);
        }
        public UUID channelId() {
            return channelId;
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
        public ReferenceSet<IGrid> grids() {
            return gridView;
        }
        public int gridCount() {
            return gridList.size();
        }
        public IGrid gridAt(int index) {
            return gridList.get(index);
        }

        private void addGrid(IGrid grid) {
            if (!grids.add(grid)) {
                throw new IllegalStateException("Grid is already registered in this channel");
            }
            gridPositions.put(grid, gridList.size());
            gridList.add(grid);
        }

        private void removeGrid(IGrid grid) {
            int position = gridPositions.removeInt(grid);
            if (position < 0 || !grids.remove(grid)) {
                throw new IllegalStateException("Grid channel dense index is inconsistent");
            }
            int lastIndex = gridList.size() - 1;
            IGrid last = gridList.remove(lastIndex);
            if (position < lastIndex) {
                gridList.set(position, last);
                gridPositions.put(last, position);
            }
        }
        public boolean isRoutingActive() {
            return routingActive;
        }
        public long routingEpoch() {
            if (!routingActive) {
                throw new IllegalStateException("Channel routing is inactive");
            }
            return routingEpoch;
        }

        private void beginRouting(long epoch) {
            if (routingActive && routingEpoch != epoch) {
                throw new IllegalStateException("Channel routing is already active for another epoch");
            }
            routingActive = true;
            routingEpoch = epoch;
        }

        private void endRouting(long epoch) {
            if (!routingActive || routingEpoch != epoch) {
                throw new IllegalStateException("Channel routing epoch does not match the active epoch");
            }
            routingActive = false;
            routingEpoch = Long.MIN_VALUE;
        }

        /**
         * Claims channel charging work for one server epoch. Charging targets
         * are keyed by grid, so several targets can reference the same channel
         * during one scan without rebuilding an external de-duplication set.
         *
         * @return {@code true} only for the first claim in {@code epoch}
         */
        boolean beginChargingEpoch(long epoch) {
            if (chargingEpoch == epoch) {
                return false;
            }
            chargingEpoch = epoch;
            return true;
        }

        private void add(EnergyMachineManager.MachineTransferSlot participant, IEnergyHandler.EnergyType role, int priority) {
            validateAddition(participant, role, priority);
            if (participant.membership().isChannelBound()) {
                if (channelId.equals(participant.membership().channelId())
                    && participant.membership().channelRole() == role
                    && participant.membership().channelPriority() == priority) {
                    return;
                }
                throw new IllegalStateException("Participant is already bound to another channel");
            }
            PriorityRoleIndex roleIndex = roleIndex(role);
            roleIndex.add(participant, priority);
            participant.membership().bindChannel(ChannelParticipantIndex.this, channelId, roleIndex,
                roleIndex.insertedBucket(priority), role, priority);
            participantAdditions++;
        }

        private void remove(EnergyMachineManager.MachineTransferSlot participant) {
            if (!participant.membership().isChannelBound()) {
                return;
            }
            validateRemoval(participant);
            PriorityRoleIndex roleIndex = requireChannelRoleIndex(participant);
            if (!roleIndex.remove(participant)) {
                throw new IllegalStateException("Participant channel membership is inconsistent with its role index");
            }
            participant.membership().clearChannel(ChannelParticipantIndex.this, channelId);
            participantRemovals++;
        }

        private void move(EnergyMachineManager.MachineTransferSlot participant, IEnergyHandler.EnergyType role, int priority) {
            validateMove(participant, role, priority);
            PriorityRoleIndex source = requireChannelRoleIndex(participant);
            PriorityRoleIndex destination = roleIndex(role);
            PriorityRoleIndex.Bucket destinationBucket = source.transferTo(participant, destination, priority);
            participant.membership().moveChannel(destination, destinationBucket, role, priority);
            participantMoves++;
        }

        private void clear() {
            requireStructuralMutationAllowed();
            send.clear();
            storage.clear();
            receive.clear();
            grids.clear();
            gridList.clear();
            gridPositions.clear();
            routingActive = false;
            routingEpoch = Long.MIN_VALUE;
            chargingEpoch = Long.MIN_VALUE;
        }

        private PriorityRoleIndex roleIndex(IEnergyHandler.EnergyType role) {
            return switch (Objects.requireNonNull(role, "role")) {
                case SEND -> send;
                case STORAGE -> storage;
                case RECEIVE -> receive;
                case INVALID -> throw new IllegalArgumentException("INVALID is not a participant role");
            };
        }

        private PriorityRoleIndex requireChannelRoleIndex(EnergyMachineManager.MachineTransferSlot participant) {
            return participant.membership().ownerRoleIndex(ParticipantMembershipScope.CHANNEL);
        }

        private void validateAddition(EnergyMachineManager.MachineTransferSlot participant, IEnergyHandler.EnergyType role, int priority) {
            Objects.requireNonNull(participant, "participant");
            requireStructuralMutationAllowed();
            roleIndex(role);
            if (!participant.membership().isChannelBound()) {
                return;
            }
            if (channelId.equals(participant.membership().channelId())
                && participant.membership().channelRole() == role
                && participant.membership().channelPriority() == priority) {
                return;
            }
            throw new IllegalStateException("Participant is already bound to a channel");
        }

        private void validateRemoval(EnergyMachineManager.MachineTransferSlot participant) {
            Objects.requireNonNull(participant, "participant");
            requireStructuralMutationAllowed();
            GridParticipantMembership membership = participant.membership();
            if (!channelId.equals(membership.channelId())) {
                throw new IllegalStateException("Participant belongs to another channel");
            }
            IEnergyHandler.EnergyType role = membership.channelRole();
            if (role == null || membership.bucket(ParticipantMembershipScope.CHANNEL) == null
                || roleIndex(role) != requireChannelRoleIndex(participant)) {
                throw new IllegalStateException("Participant channel membership is inconsistent with its role index");
            }
        }

        private void validateMove(EnergyMachineManager.MachineTransferSlot participant, IEnergyHandler.EnergyType role, int priority) {
            validateRemoval(participant);
            PriorityRoleIndex destination = roleIndex(role);
            PriorityRoleIndex.Bucket targetBucket = destination.bucket(priority);
            if (targetBucket != null && targetBucket.priority() != priority) {
                throw new IllegalStateException("Channel priority bucket does not match its lookup priority");
            }
        }

        private void validateGridAddition(IGrid grid) {
            Objects.requireNonNull(grid, "grid");
            requireStructuralMutationAllowed();
            if (grid.getParticipantIndex().isRoutingActive()) {
                throw new IllegalStateException("Cannot mirror a grid while its local routing window is active");
            }
            validateGridRoleAddition(grid.getParticipantIndex().send());
            validateGridRoleAddition(grid.getParticipantIndex().storage());
            validateGridRoleAddition(grid.getParticipantIndex().receive());
        }

        private void validateGridRemoval(IGrid grid) {
            Objects.requireNonNull(grid, "grid");
            requireStructuralMutationAllowed();
            if (!grids.contains(grid)) {
                throw new IllegalStateException("Grid is not registered in this channel");
            }
            validateGridRoleRemoval(grid.getParticipantIndex().send());
            validateGridRoleRemoval(grid.getParticipantIndex().storage());
            validateGridRoleRemoval(grid.getParticipantIndex().receive());
        }

        private void validateGridMigrationTarget(IGrid grid, ChannelEntry source) {
            Objects.requireNonNull(grid, "grid");
            requireStructuralMutationAllowed();
            validateGridRoleMigrationTarget(grid.getParticipantIndex().send(), source);
            validateGridRoleMigrationTarget(grid.getParticipantIndex().storage(), source);
            validateGridRoleMigrationTarget(grid.getParticipantIndex().receive(), source);
        }

        private void validateGridRoleAddition(PriorityRoleIndex roleIndex) {
            for (PriorityRoleIndex.Bucket bucket = roleIndex.firstBucket(); bucket != null; bucket = bucket.next()) {
                for (int index = bucket.firstAliveIndex(); index >= 0; index = bucket.nextAliveIndex(index)) {
                    EnergyMachineManager.MachineTransferSlot participant = bucket.participantAt(index);
                    GridParticipantMembership membership = participant.membership();
                    if (membership.isChannelBound()) {
                        throw new IllegalStateException("Grid participant is already bound to a channel");
                    }
                    IEnergyHandler.EnergyType role = membership.role();
                    if (role == null) {
                        throw new IllegalStateException("Grid participant has no grid role");
                    }
                    this.roleIndex(role);
                }
            }
        }

        private void validateGridRoleRemoval(PriorityRoleIndex roleIndex) {
            for (PriorityRoleIndex.Bucket bucket = roleIndex.firstBucket(); bucket != null; bucket = bucket.next()) {
                for (int index = bucket.firstAliveIndex(); index >= 0; index = bucket.nextAliveIndex(index)) {
                    validateRemoval(bucket.participantAt(index));
                }
            }
        }

        private void validateGridRoleMigrationTarget(PriorityRoleIndex roleIndex, ChannelEntry source) {
            for (PriorityRoleIndex.Bucket bucket = roleIndex.firstBucket(); bucket != null; bucket = bucket.next()) {
                for (int index = bucket.firstAliveIndex(); index >= 0; index = bucket.nextAliveIndex(index)) {
                    GridParticipantMembership membership = bucket.participantAt(index).membership();
                    if (membership.isChannelBound()
                        && (source == null || !source.channelId.equals(membership.channelId()))) {
                        throw new IllegalStateException("Grid participant is bound to an unrelated channel");
                    }
                    IEnergyHandler.EnergyType role = membership.role();
                    if (role == null) {
                        throw new IllegalStateException("Grid participant has no grid role");
                    }
                    this.roleIndex(role);
                }
            }
        }

        private void requireStructuralMutationAllowed() {
            if (routingActive) {
                throw new IllegalStateException(
                    "Cannot mutate channel participant membership while routing is active for epoch " + routingEpoch
                );
            }
        }
    }

    static final class StructuralMetrics {
        final long participantAdditions;
        final long participantRemovals;
        final long participantMoves;

        private StructuralMetrics(long participantAdditions, long participantRemovals, long participantMoves) {
            this.participantAdditions = participantAdditions;
            this.participantRemovals = participantRemovals;
            this.participantMoves = participantMoves;
        }
    }
}
