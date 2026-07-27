package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.api.IGrid;
import it.unimi.dsi.fastutil.longs.AbstractLong2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.AbstractObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Grid-owned index for {@link EnergyMachineManager.MachineTransferSlot}.
 * Participant-owned memberships provide direct bucket lookup for removal and
 * role migration.
 */
public final class GridParticipantIndex {

    static final class MoveRollbackException extends IllegalStateException {

        MoveRollbackException(RuntimeException cause) {
            super("Grid participant move rollback could not restore both membership scopes", cause);
        }
    }

    static final class MoveRollback {

        private final GridParticipantIndex owner;
        private final EnergyMachineManager.MachineTransferSlot participant;
        private final PriorityRoleIndex source;
        private final PriorityRoleIndex.Bucket sourceBucket;
        private final IEnergyHandler.EnergyType sourceRole;
        private final int sourcePriority;
        private final int sourceIndex;
        private final PriorityRoleIndex destination;
        private final IEnergyHandler.EnergyType destinationRole;
        private final int destinationPriority;
        @Nullable
        private PriorityRoleIndex.Bucket destinationBucket;
        @Nullable
        private ChannelParticipantIndex.MoveRollback channelRollback;

        private MoveRollback(GridParticipantIndex owner,
                             EnergyMachineManager.MachineTransferSlot participant,
                             PriorityRoleIndex source,
                             PriorityRoleIndex.Bucket sourceBucket,
                             IEnergyHandler.EnergyType sourceRole,
                             int sourcePriority,
                             int sourceIndex,
                             PriorityRoleIndex destination,
                             IEnergyHandler.EnergyType destinationRole,
                             int destinationPriority) {
            this.owner = owner;
            this.participant = participant;
            this.source = source;
            this.sourceBucket = sourceBucket;
            this.sourceRole = sourceRole;
            this.sourcePriority = sourcePriority;
            this.sourceIndex = sourceIndex;
            this.destination = destination;
            this.destinationRole = destinationRole;
            this.destinationPriority = destinationPriority;
        }

        GridParticipantIndex owner() {
            return owner;
        }
    }

    @Nullable
    private final IGrid ownerGrid;
    private final PriorityRoleIndex send = new PriorityRoleIndex();
    private final PriorityRoleIndex storage = new PriorityRoleIndex();
    private final PriorityRoleIndex receive = new PriorityRoleIndex();
    private final Long2ObjectMap<EnergyMachineManager.Interaction> machineInteractions =
        new Long2ObjectOpenHashMap<>();
    private final Long2ObjectMap<EnergyMachineManager.Interaction> machineInteractionsView =
        new MachineInteractionReadView(machineInteractions);
    private int size;
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

    /**
     * Returns the pre-created read-only position index exposed by the owning grid.
     * Per-machine accounting is demand-driven, so handing out this view renews the accounting window.
     */
    public Long2ObjectMap<EnergyMachineManager.Interaction> machineInteractions() {
        EnergyMachineManager.INSTANCE.renewMachineInteractionDemand();
        return machineInteractionsView;
    }

    /** Reads one registered tracker for internal consistency checks without renewing the accounting window. */
    @Nullable
    EnergyMachineManager.Interaction machineInteraction(long packedPosition) {
        return machineInteractions.get(packedPosition);
    }

    void registerMachineInteraction(long packedPosition, EnergyMachineManager.Interaction interaction) {
        Objects.requireNonNull(interaction, "interaction");
        EnergyMachineManager.Interaction current = machineInteractions.get(packedPosition);
        if (current == interaction) {
            return;
        }
        if (current != null) {
            throw new IllegalStateException("Grid machine interaction position is already owned by another tracker");
        }
        machineInteractions.put(packedPosition, interaction);
    }

    void unregisterMachineInteraction(long packedPosition, EnergyMachineManager.Interaction interaction) {
        Objects.requireNonNull(interaction, "interaction");
        EnergyMachineManager.Interaction current = machineInteractions.get(packedPosition);
        if (current != interaction) {
            throw new IllegalStateException("Grid machine interaction removal does not match the registered tracker");
        }
        machineInteractions.remove(packedPosition);
    }

    void add(IEnergyHandler.EnergyType role, EnergyMachineManager.MachineTransferSlot participant, int priority) {
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
    }

    void remove(EnergyMachineManager.MachineTransferSlot participant) {
        if (participant == null) {
            return;
        }
        requireStructuralMutationAllowed();
        GridParticipantMembership membership = participant.membership();
        if (membership.owner() != this) {
            return;
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
    }

    @Nullable
    MoveRollback move(EnergyMachineManager.MachineTransferSlot participant, int priority) {
        Objects.requireNonNull(participant, "participant");
        GridParticipantMembership membership = requireMembership(participant);
        IEnergyHandler.EnergyType role = Objects.requireNonNull(membership.role(), "participant membership role");
        return move(participant, membership, role, priority);
    }

    void move(EnergyMachineManager.MachineTransferSlot participant, IEnergyHandler.EnergyType role, int priority) {
        Objects.requireNonNull(participant, "participant");
        Objects.requireNonNull(role, "role");
        GridParticipantMembership membership = requireMembership(participant);
        move(participant, membership, role, priority);
    }

    void rollbackMove(MoveRollback rollback) {
        Objects.requireNonNull(rollback, "rollback");
        if (rollback.owner != this) {
            throw new IllegalStateException("Grid participant move rollback belongs to another grid index");
        }
        GridParticipantMembership membership = rollback.participant.membership();
        PriorityRoleIndex.Bucket destinationBucket = Objects.requireNonNull(
            rollback.destinationBucket, "Grid participant move rollback was not committed"
        );
        if (membership.owner() != this
            || membership.role() != rollback.destinationRole
            || membership.priority() != rollback.destinationPriority
            || requireRoleIndex(membership) != rollback.destination
            || membership.gridBucket() != destinationBucket) {
            throw new MoveRollbackException(
                new IllegalStateException("Grid participant move rollback no longer matches participant membership")
            );
        }
        rollback.destination.participantIndex(destinationBucket, rollback.participant);
        RuntimeException failure = null;
        if (rollback.channelRollback != null) {
            try {
                ChannelParticipantIndex.INSTANCE.rollbackGridParticipantMove(rollback.channelRollback);
            } catch (RuntimeException exception) {
                failure = exception;
            }
        }
        try {
            PriorityRoleIndex.Bucket restoredBucket = rollback.destination.rollbackTransferTo(
                rollback.participant, rollback.source, rollback.sourceBucket, rollback.sourceIndex
            );
            membership.move(
                rollback.source, restoredBucket, rollback.sourceRole, rollback.sourcePriority
            );
        } catch (RuntimeException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        }
        if (failure != null) {
            throw new MoveRollbackException(failure);
        }
    }

    void validateMove(EnergyMachineManager.MachineTransferSlot participant,
                      IEnergyHandler.EnergyType role,
                      int priority) {
        Objects.requireNonNull(participant, "participant");
        Objects.requireNonNull(role, "role");
        requireStructuralMutationAllowed();
        GridParticipantMembership membership = requireMembership(participant);
        IEnergyHandler.EnergyType currentRole = Objects.requireNonNull(
            membership.role(), "participant membership role"
        );
        PriorityRoleIndex source = requireRoleIndex(membership);
        if (source != roleIndex(currentRole)) {
            throw new IllegalStateException("Participant membership role does not match its role index");
        }
        PriorityRoleIndex.Bucket sourceBucket = membership.gridBucket();
        if (sourceBucket == null || sourceBucket.priority() != membership.priority()
            || !containsIdentity(sourceBucket, participant)) {
            throw new IllegalStateException("Participant membership is inconsistent with its priority bucket");
        }
        roleIndex(role);
        if (channelId == null) {
            if (membership.isChannelBound()) {
                throw new IllegalStateException("Unbound grid participant retains channel membership");
            }
        } else {
            if (!membership.isChannelBound() || !channelId.equals(membership.channelId())
                || membership.channelRole() != currentRole
                || membership.channelPriority() != membership.priority()) {
                throw new IllegalStateException("Grid and channel participant memberships are inconsistent");
            }
        }
        if (ownerGrid != null) {
            ChannelParticipantIndex.INSTANCE.validateGridParticipantMove(ownerGrid, participant, role, priority);
        }
    }

    @Nullable
    private MoveRollback move(EnergyMachineManager.MachineTransferSlot participant,
                              GridParticipantMembership membership,
                              IEnergyHandler.EnergyType role,
                              int priority) {
        validateMove(participant, role, priority);
        IEnergyHandler.EnergyType previousRole = Objects.requireNonNull(
            membership.role(), "participant membership role"
        );
        int previousPriority = membership.priority();
        PriorityRoleIndex source = requireRoleIndex(membership);
        PriorityRoleIndex destination = roleIndex(role);
        if (source == destination && previousPriority == priority) {
            return null;
        }
        PriorityRoleIndex.Bucket sourceBucket = Objects.requireNonNull(
            membership.gridBucket(), "participant membership bucket"
        );
        int sourceIndex = source.participantIndex(sourceBucket, participant);
        MoveRollback rollback = new MoveRollback(
            this, participant, source, sourceBucket, previousRole, previousPriority, sourceIndex,
            destination, role, priority
        );
        PriorityRoleIndex.Bucket destinationBucket = source.transferTo(participant, destination, priority);
        membership.move(destination, destinationBucket, role, priority);
        if (ownerGrid != null) {
            try {
                ChannelParticipantIndex.MoveRollback channelRollback =
                    ChannelParticipantIndex.INSTANCE.onGridParticipantMoved(ownerGrid, participant, role, priority);
                if (channelId != null && channelRollback == null) {
                    throw new IllegalStateException("Channel participant move did not produce a rollback token");
                }
                rollback.channelRollback = channelRollback;
            } catch (RuntimeException | Error exception) {
                boolean rollbackFailed = false;
                try {
                    PriorityRoleIndex.Bucket restoredBucket = destination.rollbackTransferTo(
                        participant, source, sourceBucket, sourceIndex
                    );
                    membership.move(source, restoredBucket, previousRole, previousPriority);
                } catch (RuntimeException | Error rollbackException) {
                    exception.addSuppressed(rollbackException);
                    rollbackFailed = true;
                }
                if (rollbackFailed && exception instanceof RuntimeException runtimeException) {
                    throw new MoveRollbackException(runtimeException);
                }
                throw exception;
            }
        }
        rollback.destinationBucket = destinationBucket;
        return rollback;
    }

    public int size() {
        return size;
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
        if (!machineInteractions.isEmpty()) {
            throw new IllegalStateException("Cannot clear a grid participant index with registered machine interactions");
        }
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
        return membership.ownerRoleIndex();
    }

    private static boolean containsIdentity(PriorityRoleIndex.Bucket bucket,
                                            EnergyMachineManager.MachineTransferSlot participant) {
        int matches = 0;
        for (int index = 0; index < bucket.participantCount(); index++) {
            if (bucket.participantAt(index) == participant) {
                matches++;
            }
        }
        return matches == 1;
    }

    /**
     * Live read-only map whose entry objects cannot replace values. Fastutil's standard unmodifiable map leaves the
     * wrapped mutable entries exposed, so this view owns an immutable entry-set facade as well as map-level guards.
     */
    private static final class MachineInteractionReadView
        extends AbstractLong2ObjectMap<EnergyMachineManager.Interaction> {

        private final Long2ObjectMap<EnergyMachineManager.Interaction> source;
        private final LongSet readOnlyKeys;
        private final ObjectCollection<EnergyMachineManager.Interaction> readOnlyValues;
        private final ObjectSet<Long2ObjectMap.Entry<EnergyMachineManager.Interaction>> readOnlyEntries;

        private MachineInteractionReadView(Long2ObjectMap<EnergyMachineManager.Interaction> source) {
            this.source = Objects.requireNonNull(source, "source");
            Long2ObjectMap<EnergyMachineManager.Interaction> readOnlyDelegate =
                Long2ObjectMaps.unmodifiable(source);
            readOnlyKeys = readOnlyDelegate.keySet();
            readOnlyValues = readOnlyDelegate.values();
            readOnlyEntries = new ReadOnlyEntrySet(source);
        }

        @Override
        public int size() {
            return source.size();
        }

        @Override
        public boolean isEmpty() {
            return source.isEmpty();
        }

        @Override
        public boolean containsKey(long key) {
            return source.containsKey(key);
        }

        @Override
        public boolean containsValue(Object value) {
            return source.containsValue(value);
        }

        @Override
        public EnergyMachineManager.Interaction get(long key) {
            return source.get(key);
        }

        @Override
        public EnergyMachineManager.Interaction defaultReturnValue() {
            return source.defaultReturnValue();
        }

        @Override
        public @NotNull LongSet keySet() {
            return readOnlyKeys;
        }

        @Override
        public @NotNull ObjectCollection<EnergyMachineManager.Interaction> values() {
            return readOnlyValues;
        }

        @Override
        public @NotNull ObjectSet<Long2ObjectMap.Entry<EnergyMachineManager.Interaction>> long2ObjectEntrySet() {
            return readOnlyEntries;
        }

        @Override
        public int hashCode() {
            return source.hashCode();
        }

        @Override
        public boolean equals(Object candidate) {
            if (!(candidate instanceof Map)) {
                return false;
            }
            return source.equals(candidate);
        }

        @Override
        public @NotNull String toString() {
            return source.toString();
        }

        @Override
        public EnergyMachineManager.Interaction put(long key, EnergyMachineManager.Interaction value) {
            throw readOnlyMutation();
        }

        @Override
        public EnergyMachineManager.Interaction remove(long key) {
            throw readOnlyMutation();
        }

        @Override
        public void defaultReturnValue(EnergyMachineManager.Interaction value) {
            throw readOnlyMutation();
        }

        @Override
        public void clear() {
            throw readOnlyMutation();
        }

        private static UnsupportedOperationException readOnlyMutation() {
            return new UnsupportedOperationException("Machine interaction view is read-only");
        }
    }

    private static final class ImmutableMachineInteractionEntry
        extends AbstractLong2ObjectMap.BasicEntry<EnergyMachineManager.Interaction> {

        private ImmutableMachineInteractionEntry(long key, EnergyMachineManager.Interaction value) {
            super(key, value);
        }

        @Override
        public EnergyMachineManager.Interaction setValue(@NotNull EnergyMachineManager.Interaction value) {
            throw new UnsupportedOperationException("Machine interaction entry is read-only");
        }
    }

    private static final class ReadOnlyEntryIterator
        extends AbstractObjectIterator<Long2ObjectMap.Entry<EnergyMachineManager.Interaction>> {

        private final ObjectIterator<Long2ObjectMap.Entry<EnergyMachineManager.Interaction>> delegate;

        private ReadOnlyEntryIterator(
            ObjectIterator<Long2ObjectMap.Entry<EnergyMachineManager.Interaction>> delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public @NotNull Long2ObjectMap.Entry<EnergyMachineManager.Interaction> next() {
            Long2ObjectMap.Entry<EnergyMachineManager.Interaction> entry = delegate.next();
            return new ImmutableMachineInteractionEntry(entry.getLongKey(), entry.getValue());
        }

        @Override
        public int skip(int count) {
            if (count < 0) {
                throw new IllegalArgumentException("Skip count must not be negative");
            }
            return delegate.skip(count);
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("Machine interaction view is read-only");
        }
    }

    private static class ReadOnlyEntrySet
        extends AbstractObjectSet<Long2ObjectMap.Entry<EnergyMachineManager.Interaction>> {

        private final Long2ObjectMap<EnergyMachineManager.Interaction> source;

        private ReadOnlyEntrySet(Long2ObjectMap<EnergyMachineManager.Interaction> source) {
            this.source = source;
        }

        @Override
        public @NotNull ObjectIterator<Long2ObjectMap.Entry<EnergyMachineManager.Interaction>> iterator() {
            return new ReadOnlyEntryIterator(source.long2ObjectEntrySet().iterator());
        }

        @Override
        public int size() {
            return source.size();
        }

        @Override
        public boolean contains(Object candidate) {
            if (!(candidate instanceof Map.Entry)) {
                return false;
            }
            return source.long2ObjectEntrySet().contains(candidate);
        }

        @Override
        public boolean add(Long2ObjectMap.Entry<EnergyMachineManager.Interaction> entry) {
            throw new UnsupportedOperationException("Machine interaction view is read-only");
        }

        @Override
        public boolean addAll(
            @NotNull Collection<? extends Long2ObjectMap.Entry<EnergyMachineManager.Interaction>> entries) {
            throw new UnsupportedOperationException("Machine interaction view is read-only");
        }

        @Override
        public boolean remove(Object entry) {
            throw new UnsupportedOperationException("Machine interaction view is read-only");
        }

        @Override
        public boolean removeAll(@NotNull Collection<?> entries) {
            throw new UnsupportedOperationException("Machine interaction view is read-only");
        }

        @Override
        public boolean retainAll(@NotNull Collection<?> entries) {
            throw new UnsupportedOperationException("Machine interaction view is read-only");
        }

        @Override
        public boolean removeIf(
            @NotNull Predicate<? super Long2ObjectMap.Entry<EnergyMachineManager.Interaction>> filter) {
            throw new UnsupportedOperationException("Machine interaction view is read-only");
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException("Machine interaction view is read-only");
        }

        @Override
        protected final Object clone() throws CloneNotSupportedException {
            throw new CloneNotSupportedException("Machine interaction view is read-only");
        }
    }

}
