package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.utils.TombstoneReferenceBag;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.util.Objects;

/**
 * Tombstone-bag implementation of one descending role index. Priority bucket
 * insertion occurs only on cold-path membership changes; stable server ticks
 * walk the linked chain without sorting, copying, or iterator allocation.
 */
public final class PriorityRoleIndex {

    /** Default transfer priority used when no explicit priority is supplied. */
    public static final int DEFAULT_PRIORITY = 0;

    private final ParticipantMembershipScope membershipScope;
    private final Int2ObjectOpenHashMap<Bucket> bucketsByPriority = new Int2ObjectOpenHashMap<>();
    private Bucket firstBucket;
    private Bucket lastBucket;
    private int size;
    private int pairMatchingParticipants;

    PriorityRoleIndex() {
        this(ParticipantMembershipScope.GRID);
    }

    PriorityRoleIndex(ParticipantMembershipScope membershipScope) {
        this.membershipScope = Objects.requireNonNull(membershipScope, "membershipScope");
    }
    public int size() {
        return size;
    }
    public boolean isEmpty() {
        return size == 0;
    }
    public boolean hasPairMatchingParticipant() {
        return pairMatchingParticipants != 0;
    }

    void add(EnergyMachineManager.MachineTransferSlot participant, int priority) {
        Objects.requireNonNull(participant, "participant");
        if (participant.membership().isBound(membershipScope)) {
            throw new IllegalStateException("Participant is already registered in this role index scope");
        }
        Bucket bucket = getOrCreateBucket(priority);
        try {
            bucket.participants.addDirect(participant);
        } catch (RuntimeException exception) {
            removeBucketIfEmpty(bucket);
            throw exception;
        }
        size++;
        if (participant.requiresPairMatch()) {
            pairMatchingParticipants++;
        }
    }

    boolean remove(EnergyMachineManager.MachineTransferSlot participant) {
        if (participant == null) {
            return false;
        }
        GridParticipantMembership membership = participant.membership();
        if (!membership.isBound(membershipScope) || membership.ownerRoleIndex(membershipScope) != this) {
            return false;
        }
        Bucket bucket = castBucket(membership.bucket(membershipScope));
        if (!bucket.participants.remove(participant)) {
            throw new IllegalStateException("Participant membership is inconsistent with its priority bucket");
        }
        size--;
        if (participant.requiresPairMatch()) {
            if (pairMatchingParticipants == 0) {
                throw new IllegalStateException("Pair-matching participant count underflow");
            }
            pairMatchingParticipants--;
        }
        removeBucketIfEmpty(bucket);
        return true;
    }

    Bucket transferTo(EnergyMachineManager.MachineTransferSlot participant, PriorityRoleIndex destination, int priority) {
        Objects.requireNonNull(participant, "participant");
        Objects.requireNonNull(destination, "destination");
        if (membershipScope != destination.membershipScope) {
            throw new IllegalArgumentException("Cannot transfer a participant between different membership scopes");
        }
        GridParticipantMembership membership = participant.membership();
        if (!membership.isBound(membershipScope) || membership.ownerRoleIndex(membershipScope) != this) {
            throw new IllegalStateException("Participant belongs to a different role index");
        }
        Bucket previous = castBucket(membership.bucket(membershipScope));
        if (!previous.participants.contains(participant)) {
            throw new IllegalStateException("Participant membership is inconsistent with its priority bucket");
        }
        if (this == destination && previous.priority == priority) {
            return previous;
        }
        Bucket next = destination.getOrCreateBucket(priority);
        if (!previous.participants.remove(participant)) {
            destination.removeBucketIfEmpty(next);
            throw new IllegalStateException("Participant membership is inconsistent with its priority bucket");
        }
        try {
            next.participants.addDirect(participant);
        } catch (RuntimeException exception) {
            try {
                previous.participants.addDirect(participant);
            } catch (RuntimeException rollbackException) {
                exception.addSuppressed(rollbackException);
            }
            destination.removeBucketIfEmpty(next);
            throw exception;
        }
        size--;
        destination.size++;
        if (participant.requiresPairMatch()) {
            if (pairMatchingParticipants == 0) {
                throw new IllegalStateException("Pair-matching participant count underflow");
            }
            pairMatchingParticipants--;
            destination.pairMatchingParticipants++;
        }
        removeBucketIfEmpty(previous);
        return next;
    }
    public Bucket bucket(int priority) {
        return bucketsByPriority.get(priority);
    }
    public Bucket firstBucket() {
        return firstBucket;
    }

    void clear() {
        for (Bucket bucket = firstBucket; bucket != null; ) {
            Bucket next = bucket.next;
            for (int index = bucket.participants.firstAliveIndex(); index >= 0; index = bucket.participants.nextAliveIndex(index)) {
                bucket.participants.elementAt(index).membership().clearFromRoleIndex(membershipScope, this);
            }
            bucket.participants.clearAndNullUsed();
            bucket.previous = null;
            bucket.next = null;
            bucket = next;
        }
        bucketsByPriority.clear();
        firstBucket = null;
        lastBucket = null;
        size = 0;
        pairMatchingParticipants = 0;
    }

    Bucket insertedBucket(int priority) {
        return castBucket(bucket(priority));
    }

    private Bucket getOrCreateBucket(int priority) {
        Bucket bucket = bucketsByPriority.get(priority);
        if (bucket != null) {
            return bucket;
        }
        bucket = new Bucket(priority);
        bucketsByPriority.put(priority, bucket);
        insertDescending(bucket);
        return bucket;
    }

    private void insertDescending(Bucket inserted) {
        if (firstBucket == null) {
            firstBucket = inserted;
            lastBucket = inserted;
            return;
        }
        if (inserted.priority > firstBucket.priority) {
            inserted.next = firstBucket;
            firstBucket.previous = inserted;
            firstBucket = inserted;
            return;
        }
        if (inserted.priority < lastBucket.priority) {
            inserted.previous = lastBucket;
            lastBucket.next = inserted;
            lastBucket = inserted;
            return;
        }
        Bucket cursor = firstBucket;
        while (cursor.next != null && cursor.next.priority > inserted.priority) {
            cursor = cursor.next;
        }
        inserted.previous = cursor;
        inserted.next = cursor.next;
        cursor.next = inserted;
        if (inserted.next != null) {
            inserted.next.previous = inserted;
        } else {
            lastBucket = inserted;
        }
    }

    private void removeBucketIfEmpty(Bucket bucket) {
        if (!bucket.participants.isEmpty()) {
            return;
        }
        bucketsByPriority.remove(bucket.priority);
        if (bucket.previous != null) {
            bucket.previous.next = bucket.next;
        } else {
            firstBucket = bucket.next;
        }
        if (bucket.next != null) {
            bucket.next.previous = bucket.previous;
        } else {
            lastBucket = bucket.previous;
        }
        bucket.previous = null;
        bucket.next = null;
        bucket.participants.clearAndNullUsed();
    }

    private Bucket castBucket(Bucket bucket) {
        if (!(bucket instanceof Bucket)) {
            throw new IllegalStateException("Participant bucket does not belong to this role index");
        }
        return (Bucket) bucket;
    }

    static final class Bucket {

        private final int priority;
        private final TombstoneReferenceBag<EnergyMachineManager.MachineTransferSlot> participants = new TombstoneReferenceBag<>();
        private Bucket previous;
        private Bucket next;

        private Bucket(int priority) {
            this.priority = priority;
        }
        public int priority() {
            return priority;
        }
        public int firstAliveIndex() {
            return participants.firstAliveIndex();
        }
        public int nextAliveIndex(int currentIndex) {
            return participants.nextAliveIndex(currentIndex);
        }
        public EnergyMachineManager.MachineTransferSlot participantAt(int index) {
            return participants.elementAt(index);
        }
        public Bucket next() {
            return next;
        }
    }
}
