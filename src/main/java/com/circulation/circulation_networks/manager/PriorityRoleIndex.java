package com.circulation.circulation_networks.manager;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Dense implementation of one descending role index. Priority bucket
 * insertion and stable removal occur only on cold-path membership changes;
 * stable server ticks walk contiguous participant arrays without sorting,
 * copying, tombstone checks, or iterator allocation.
 */
public final class PriorityRoleIndex {

    /** Default transfer priority used when no explicit priority is supplied. */
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
        boolean pairMatching = participant.requiresPairMatch();
        requireCounterCapacity(pairMatching);
        Bucket bucket = getOrCreateBucket(priority);
        try {
            bucket.participants.add(participant);
        } catch (RuntimeException | Error exception) {
            try {
                removeBucketIfEmpty(bucket);
            } catch (RuntimeException | Error rollbackException) {
                exception.addSuppressed(rollbackException);
            }
            throw exception;
        }
        size++;
        if (pairMatching) {
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
        Bucket bucket = requireBucket(membership.bucket(membershipScope));
        int participantIndex = requireIdentityIndex(bucket, participant);
        boolean pairMatching = participant.requiresPairMatch();
        requireRemovalCounters(pairMatching);
        bucket.participants.remove(participantIndex);
        size--;
        if (pairMatching) {
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
        Bucket previous = requireBucket(membership.bucket(membershipScope));
        int previousIndex = requireIdentityIndex(previous, participant);
        if (destination == this && previous.priority == priority) {
            return previous;
        }
        boolean pairMatching = participant.requiresPairMatch();
        requireRemovalCounters(pairMatching);
        if (destination != this) {
            destination.requireCounterCapacity(pairMatching);
        }
        Bucket next = destination.getOrCreateBucket(priority);
        try {
            next.participants.add(participant);
        } catch (RuntimeException | Error exception) {
            try {
                destination.removeBucketIfEmpty(next);
            } catch (RuntimeException | Error rollbackException) {
                exception.addSuppressed(rollbackException);
            }
            throw exception;
        }
        try {
            if (previous.participants.get(previousIndex) != participant) {
                throw new IllegalStateException("Participant moved within its source bucket during transfer");
            }
            previous.participants.remove(previousIndex);
        } catch (RuntimeException | Error exception) {
            try {
                rollbackDestinationAppend(next, participant);
                destination.removeBucketIfEmpty(next);
            } catch (RuntimeException | Error rollbackException) {
                exception.addSuppressed(rollbackException);
            }
            throw exception;
        }
        size--;
        destination.size++;
        if (pairMatching) {
            pairMatchingParticipants--;
            destination.pairMatchingParticipants++;
        }
        removeBucketIfEmpty(previous);
        return next;
    }

    Bucket rollbackTransferTo(EnergyMachineManager.MachineTransferSlot participant,
                              PriorityRoleIndex destination,
                              Bucket originalBucket,
                              int originalIndex) {
        Objects.requireNonNull(participant, "participant");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(originalBucket, "originalBucket");
        if (membershipScope != destination.membershipScope) {
            throw new IllegalArgumentException("Cannot roll back a participant between different membership scopes");
        }
        GridParticipantMembership membership = participant.membership();
        if (!membership.isBound(membershipScope) || membership.ownerRoleIndex(membershipScope) != this) {
            throw new IllegalStateException("Participant belongs to a different rollback source role index");
        }
        Bucket currentBucket = requireBucket(membership.bucket(membershipScope));
        int currentIndex = requireIdentityIndex(currentBucket, participant);
        if (originalBucket.owner != destination) {
            throw new IllegalStateException("Rollback destination bucket belongs to another role index");
        }
        Bucket registeredOriginalBucket = destination.bucketsByPriority.get(originalBucket.priority);
        boolean restoreOriginalBucket = registeredOriginalBucket == null;
        if (!restoreOriginalBucket && registeredOriginalBucket != originalBucket) {
            throw new IllegalStateException("Rollback destination priority is owned by another bucket");
        }
        if (restoreOriginalBucket
            && (!originalBucket.participants.isEmpty()
            || originalBucket.previous != null
            || originalBucket.next != null)) {
            throw new IllegalStateException("Detached rollback destination bucket is not empty and isolated");
        }
        int destinationSize = originalBucket.participants.size();
        if (originalIndex < 0 || originalIndex > destinationSize) {
            throw new IllegalStateException("Rollback destination index is outside the original bucket");
        }
        if (identityIndex(originalBucket, participant) >= 0) {
            throw new IllegalStateException("Rollback destination bucket already contains the participant");
        }
        boolean pairMatching = participant.requiresPairMatch();
        requireRemovalCounters(pairMatching);
        if (destination != this) {
            destination.requireCounterCapacity(pairMatching);
        }
        if (restoreOriginalBucket) {
            destination.restoreDetachedBucket(originalBucket);
        }
        try {
            originalBucket.participants.add(originalIndex, participant);
        } catch (RuntimeException | Error exception) {
            if (restoreOriginalBucket) {
                try {
                    destination.removeBucketIfEmpty(originalBucket);
                } catch (RuntimeException | Error rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
            }
            throw exception;
        }
        try {
            if (currentBucket.participants.get(currentIndex) != participant) {
                throw new IllegalStateException("Participant moved within its rollback source bucket");
            }
            currentBucket.participants.remove(currentIndex);
        } catch (RuntimeException | Error exception) {
            try {
                rollbackDestinationInsert(originalBucket, originalIndex, participant);
                if (restoreOriginalBucket) {
                    destination.removeBucketIfEmpty(originalBucket);
                }
            } catch (RuntimeException | Error rollbackException) {
                exception.addSuppressed(rollbackException);
            }
            throw exception;
        }
        size--;
        destination.size++;
        if (pairMatching) {
            pairMatchingParticipants--;
            destination.pairMatchingParticipants++;
        }
        removeBucketIfEmpty(currentBucket);
        return originalBucket;
    }

    int participantIndex(Bucket bucket, EnergyMachineManager.MachineTransferSlot participant) {
        return requireIdentityIndex(requireBucket(bucket), Objects.requireNonNull(participant, "participant"));
    }
    /**
     * Returns the dense participant bucket for an exact priority.
     *
     * @param priority full-range transfer priority
     * @return matching bucket, or {@code null} when no participant uses it
     */
    public @Nullable Bucket bucket(int priority) {
        return bucketsByPriority.get(priority);
    }

    /**
     * Returns the highest-priority bucket in the descending bucket chain.
     *
     * @return first bucket, or {@code null} when this role index is empty
     */
    public @Nullable Bucket firstBucket() {
        return firstBucket;
    }

    void clear() {
        ReferenceOpenHashSet<EnergyMachineManager.MachineTransferSlot> participants = new ReferenceOpenHashSet<>(size);
        int participantCount = 0;
        int pairMatchingCount = 0;
        for (Bucket bucket = firstBucket; bucket != null; bucket = bucket.next) {
            requireBucket(bucket);
            for (int index = 0; index < bucket.participants.size(); index++) {
                EnergyMachineManager.MachineTransferSlot participant = bucket.participants.get(index);
                GridParticipantMembership membership = participant.membership();
                if (!membership.isBound(membershipScope)
                    || membership.ownerRoleIndex(membershipScope) != this
                    || membership.bucket(membershipScope) != bucket
                    || !participants.add(participant)) {
                    throw new IllegalStateException("Participant membership is inconsistent with its priority bucket");
                }
                participantCount++;
                if (participant.requiresPairMatch()) {
                    pairMatchingCount++;
                }
            }
        }
        if (participantCount != size || pairMatchingCount != pairMatchingParticipants) {
            throw new IllegalStateException("Priority role index counters are inconsistent with its buckets");
        }
        for (Bucket bucket = firstBucket; bucket != null; ) {
            Bucket next = bucket.next;
            for (int index = 0; index < bucket.participants.size(); index++) {
                bucket.participants.get(index).membership().clearFromRoleIndex(membershipScope, this);
            }
            bucket.participants.clear();
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
        return requireBucket(bucket(priority));
    }

    private Bucket getOrCreateBucket(int priority) {
        Bucket bucket = bucketsByPriority.get(priority);
        if (bucket != null) {
            return bucket;
        }
        bucket = new Bucket(this, priority);
        bucketsByPriority.put(priority, bucket);
        insertDescending(bucket);
        return bucket;
    }

    private void restoreDetachedBucket(Bucket bucket) {
        if (bucket.owner != this || bucketsByPriority.get(bucket.priority) != null
            || !bucket.participants.isEmpty() || bucket.previous != null || bucket.next != null) {
            throw new IllegalStateException("Cannot restore a rollback bucket in its current state");
        }
        bucketsByPriority.put(bucket.priority, bucket);
        insertDescending(bucket);
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
        requireBucket(bucket);
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
        bucket.participants.clear();
    }

    private Bucket requireBucket(Bucket bucket) {
        if (bucket == null || bucket.owner != this || bucketsByPriority.get(bucket.priority) != bucket) {
            throw new IllegalStateException("Participant bucket does not belong to this role index");
        }
        return bucket;
    }

    private static int requireIdentityIndex(Bucket bucket,
                                            EnergyMachineManager.MachineTransferSlot participant) {
        int index = identityIndex(bucket, participant);
        if (index < 0) {
            throw new IllegalStateException("Participant membership is inconsistent with its priority bucket");
        }
        return index;
    }

    private static int identityIndex(Bucket bucket,
                                     EnergyMachineManager.MachineTransferSlot participant) {
        for (int index = 0; index < bucket.participants.size(); index++) {
            if (bucket.participants.get(index) == participant) {
                return index;
            }
        }
        return -1;
    }

    private static void rollbackDestinationAppend(Bucket bucket,
                                                  EnergyMachineManager.MachineTransferSlot participant) {
        int lastIndex = bucket.participants.size() - 1;
        if (lastIndex < 0 || bucket.participants.get(lastIndex) != participant) {
            throw new IllegalStateException("Destination role index append rollback is inconsistent");
        }
        bucket.participants.remove(lastIndex);
    }

    private static void rollbackDestinationInsert(Bucket bucket,
                                                  int index,
                                                  EnergyMachineManager.MachineTransferSlot participant) {
        if (index < 0 || index >= bucket.participants.size() || bucket.participants.get(index) != participant) {
            throw new IllegalStateException("Destination role index insertion rollback is inconsistent");
        }
        bucket.participants.remove(index);
    }

    private void requireCounterCapacity(boolean pairMatching) {
        if (size == Integer.MAX_VALUE || pairMatching && pairMatchingParticipants == Integer.MAX_VALUE) {
            throw new IllegalStateException("Priority role index participant count overflow");
        }
    }

    private void requireRemovalCounters(boolean pairMatching) {
        if (size <= 0 || pairMatching && pairMatchingParticipants <= 0) {
            throw new IllegalStateException("Priority role index participant count underflow");
        }
    }

    /**
     * Dense participants sharing one exact priority. Structural mutation is
     * owned by {@link PriorityRoleIndex}; callers may only traverse the stable
     * index window exposed here.
     */
    public static final class Bucket {

        private final PriorityRoleIndex owner;
        private final int priority;
        private final ObjectArrayList<EnergyMachineManager.MachineTransferSlot> participants = new ObjectArrayList<>();
        private Bucket previous;
        private Bucket next;

        private Bucket(PriorityRoleIndex owner, int priority) {
            this.owner = owner;
            this.priority = priority;
        }
        /** @return the exact full-range priority represented by this bucket */
        public int priority() {
            return priority;
        }

        /**
         * Compatibility entry point for the former sparse traversal API.
         *
         * @return {@code 0} for a non-empty dense bucket, otherwise {@code -1}
         */
        public int firstAliveIndex() {
            return nextAliveIndex(-1);
        }

        /**
         * Compatibility entry point for the former sparse traversal API.
         *
         * @param currentIndex previously returned dense participant index
         * @return the following dense index, or {@code -1} at the end
         */
        public int nextAliveIndex(int currentIndex) {
            if (currentIndex < -1) {
                return firstAliveIndex();
            }
            return currentIndex < participants.size() - 1 ? currentIndex + 1 : -1;
        }
        int participantCount() {
            return participants.size();
        }
        EnergyMachineManager.MachineTransferSlot participantAt(int index) {
            return participants.get(index);
        }

        @Nullable Bucket next() {
            return next;
        }
    }
}
