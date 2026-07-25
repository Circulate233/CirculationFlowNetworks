package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.api.IGrid;
import it.unimi.dsi.fastutil.longs.AbstractLong2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectSet;
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

    /** Default transfer priority used when no explicit priority is supplied. */
    public static final int DEFAULT_PRIORITY = PriorityRoleIndex.DEFAULT_PRIORITY;

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

    /** Returns the pre-created read-only position index exposed by the owning grid. */
    public Long2ObjectMap<EnergyMachineManager.Interaction> machineInteractions() {
        return machineInteractionsView;
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

    void move(EnergyMachineManager.MachineTransferSlot participant, int priority) {
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
    }

    void move(EnergyMachineManager.MachineTransferSlot participant, IEnergyHandler.EnergyType role, int priority) {
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

    /**
     * Live read-only map whose entry objects cannot replace values. Fastutil's standard unmodifiable map leaves the
     * wrapped mutable entries exposed, so this view owns an immutable entry-set facade as well as map-level guards.
     */
    private static final class MachineInteractionReadView
        extends Long2ObjectMaps.UnmodifiableMap<EnergyMachineManager.Interaction> {

        private static final long serialVersionUID = 1L;
        private final ObjectSet<Long2ObjectMap.Entry<EnergyMachineManager.Interaction>> readOnlyEntries;
        private final ObjectSet<Map.Entry<Long, EnergyMachineManager.Interaction>> readOnlyBoxedEntries;

        private MachineInteractionReadView(Long2ObjectMap<EnergyMachineManager.Interaction> source) {
            super(source);
            readOnlyEntries = new ReadOnlyEntrySet(source);
            readOnlyBoxedEntries = new ReadOnlyBoxedEntrySet(source);
        }

        @Override
        public ObjectSet<Long2ObjectMap.Entry<EnergyMachineManager.Interaction>> long2ObjectEntrySet() {
            return readOnlyEntries;
        }

        @Override
        public ObjectSet<Map.Entry<Long, EnergyMachineManager.Interaction>> entrySet() {
            return readOnlyBoxedEntries;
        }
    }

    private static final class ImmutableMachineInteractionEntry
        extends AbstractLong2ObjectMap.BasicEntry<EnergyMachineManager.Interaction> {

        private ImmutableMachineInteractionEntry(long key, EnergyMachineManager.Interaction value) {
            super(key, value);
        }

        @Override
        public EnergyMachineManager.Interaction setValue(EnergyMachineManager.Interaction value) {
            throw new UnsupportedOperationException("Machine interaction entry is read-only");
        }
    }

    private static final class ReadOnlyEntrySet
        extends AbstractObjectSet<Long2ObjectMap.Entry<EnergyMachineManager.Interaction>> {

        private final Long2ObjectMap<EnergyMachineManager.Interaction> source;

        private ReadOnlyEntrySet(Long2ObjectMap<EnergyMachineManager.Interaction> source) {
            this.source = source;
        }

        @Override
        public ObjectIterator<Long2ObjectMap.Entry<EnergyMachineManager.Interaction>> iterator() {
            ObjectIterator<Long2ObjectMap.Entry<EnergyMachineManager.Interaction>> delegate =
                source.long2ObjectEntrySet().iterator();
            return new ObjectIterator<Long2ObjectMap.Entry<EnergyMachineManager.Interaction>>() {
                @Override
                public boolean hasNext() {
                    return delegate.hasNext();
                }

                @Override
                public Long2ObjectMap.Entry<EnergyMachineManager.Interaction> next() {
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
            };
        }

        @Override
        public int size() {
            return source.size();
        }

        @Override
        public boolean contains(Object candidate) {
            return source.long2ObjectEntrySet().contains(candidate);
        }

        @Override
        public boolean add(Long2ObjectMap.Entry<EnergyMachineManager.Interaction> entry) {
            throw new UnsupportedOperationException("Machine interaction view is read-only");
        }

        @Override
        public boolean addAll(Collection<? extends Long2ObjectMap.Entry<EnergyMachineManager.Interaction>> entries) {
            throw new UnsupportedOperationException("Machine interaction view is read-only");
        }

        @Override
        public boolean remove(Object entry) {
            throw new UnsupportedOperationException("Machine interaction view is read-only");
        }

        @Override
        public boolean removeAll(Collection<?> entries) {
            throw new UnsupportedOperationException("Machine interaction view is read-only");
        }

        @Override
        public boolean retainAll(Collection<?> entries) {
            throw new UnsupportedOperationException("Machine interaction view is read-only");
        }

        @Override
        public boolean removeIf(Predicate<? super Long2ObjectMap.Entry<EnergyMachineManager.Interaction>> filter) {
            throw new UnsupportedOperationException("Machine interaction view is read-only");
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException("Machine interaction view is read-only");
        }
    }

    private static final class ReadOnlyBoxedEntrySet
        extends AbstractObjectSet<Map.Entry<Long, EnergyMachineManager.Interaction>> {

        private final Long2ObjectMap<EnergyMachineManager.Interaction> source;

        private ReadOnlyBoxedEntrySet(Long2ObjectMap<EnergyMachineManager.Interaction> source) {
            this.source = source;
        }

        @Override
        public ObjectIterator<Map.Entry<Long, EnergyMachineManager.Interaction>> iterator() {
            ObjectIterator<Long2ObjectMap.Entry<EnergyMachineManager.Interaction>> delegate =
                source.long2ObjectEntrySet().iterator();
            return new ObjectIterator<Map.Entry<Long, EnergyMachineManager.Interaction>>() {
                @Override
                public boolean hasNext() {
                    return delegate.hasNext();
                }

                @Override
                public Map.Entry<Long, EnergyMachineManager.Interaction> next() {
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
            };
        }

        @Override
        public int size() {
            return source.size();
        }

        @Override
        public boolean contains(Object candidate) {
            return source.entrySet().contains(candidate);
        }

        @Override
        public boolean add(Map.Entry<Long, EnergyMachineManager.Interaction> entry) {
            throw new UnsupportedOperationException("Machine interaction view is read-only");
        }

        @Override
        public boolean addAll(Collection<? extends Map.Entry<Long, EnergyMachineManager.Interaction>> entries) {
            throw new UnsupportedOperationException("Machine interaction view is read-only");
        }

        @Override
        public boolean remove(Object entry) {
            throw new UnsupportedOperationException("Machine interaction view is read-only");
        }

        @Override
        public boolean removeAll(Collection<?> entries) {
            throw new UnsupportedOperationException("Machine interaction view is read-only");
        }

        @Override
        public boolean retainAll(Collection<?> entries) {
            throw new UnsupportedOperationException("Machine interaction view is read-only");
        }

        @Override
        public boolean removeIf(Predicate<? super Map.Entry<Long, EnergyMachineManager.Interaction>> filter) {
            throw new UnsupportedOperationException("Machine interaction view is read-only");
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException("Machine interaction view is read-only");
        }
    }
}
