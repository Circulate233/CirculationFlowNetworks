package com.circulation.circulation_networks.manager;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
//? if <1.20
import com.github.bsideup.jabel.Desugar;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Tracks primitive chunk residency separately from persistent machine-position buckets.
 * Chunk events only update the residency sets and enqueue the affected bucket; tick-pre
 * owns all transition work and only inspects buckets that a residency event touched, so a
 * tick with no chunk activity costs nothing regardless of how many cold buckets are retained.
 *
 * @param <R> lightweight machine position record type
 */
final class MachineChunkResidencyIndex<R extends MachineChunkResidencyIndex.PositionRecord> {

    private final Int2ObjectMap<LongSet> loadedChunks = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<Long2ObjectMap<Bucket<R>>> machineBuckets = new Int2ObjectOpenHashMap<>();
    private final ObjectArrayList<Bucket<R>> bucketOrder = new ObjectArrayList<>();
    private final ObjectArrayList<Bucket<R>> residencyCandidates = new ObjectArrayList<>();

    /** Marks one chunk resident without touching machine records or runtime state. */
    public synchronized void markLoaded(int dimensionId, long chunkCoordinate) {
        loadedChunks.computeIfAbsent(dimensionId, ignored -> new LongOpenHashSet()).add(chunkCoordinate);
        enqueueResidencyCandidate(dimensionId, chunkCoordinate);
    }

    /** Marks one chunk non-resident without touching machine records or runtime state. */
    public synchronized void markUnloaded(int dimensionId, long chunkCoordinate) {
        LongSet dimensionChunks = loadedChunks.get(dimensionId);
        if (dimensionChunks == null) {
            return;
        }
        if (!dimensionChunks.remove(chunkCoordinate)) {
            return;
        }
        if (dimensionChunks.isEmpty()) {
            loadedChunks.remove(dimensionId);
        }
        enqueueResidencyCandidate(dimensionId, chunkCoordinate);
    }

    /** Clears primitive residency for a dimension while retaining its cold machine records. */
    public synchronized void clearDimensionResidency(int dimensionId) {
        if (loadedChunks.remove(dimensionId) == null) {
            return;
        }
        Long2ObjectMap<Bucket<R>> dimensionBuckets = machineBuckets.get(dimensionId);
        if (dimensionBuckets == null) {
            return;
        }
        for (Bucket<R> bucket : dimensionBuckets.values()) {
            if (bucket.loaded) {
                enqueueResidencyCandidate(bucket);
            }
        }
    }

    public synchronized boolean isMarkedLoaded(int dimensionId, long chunkCoordinate) {
        LongSet dimensionChunks = loadedChunks.get(dimensionId);
        return dimensionChunks != null && dimensionChunks.contains(chunkCoordinate);
    }

    /** Adds or replaces a lightweight record after tick-pre has observed a loaded machine. */
    public synchronized void put(int dimensionId, long chunkCoordinate, R record) {
        Objects.requireNonNull(record, "record");
        Long2ObjectMap<Bucket<R>> dimensionBuckets = machineBuckets.computeIfAbsent(
            dimensionId, ignored -> new Long2ObjectOpenHashMap<>()
        );
        Bucket<R> bucket = dimensionBuckets.get(chunkCoordinate);
        if (bucket == null) {
            bucket = new Bucket<>(dimensionId, chunkCoordinate);
            bucket.loaded = isMarkedLoaded(dimensionId, chunkCoordinate);
            dimensionBuckets.put(chunkCoordinate, bucket);
            bucket.orderIndex = bucketOrder.size();
            bucketOrder.add(bucket);
        }
        bucket.records.put(record.packedPosition(), record);
    }

    public synchronized R get(int dimensionId, long chunkCoordinate, long packedPosition) {
        Long2ObjectMap<Bucket<R>> dimensionBuckets = machineBuckets.get(dimensionId);
        Bucket<R> bucket = dimensionBuckets == null ? null : dimensionBuckets.get(chunkCoordinate);
        return bucket == null ? null : bucket.records.get(packedPosition);
    }

    /** Removes a definitive machine position from its persistent bucket. */
    public synchronized boolean remove(int dimensionId, long chunkCoordinate, long packedPosition) {
        Long2ObjectMap<Bucket<R>> dimensionBuckets = machineBuckets.get(dimensionId);
        if (dimensionBuckets == null) {
            return false;
        }
        Bucket<R> bucket = dimensionBuckets.get(chunkCoordinate);
        if (bucket == null || bucket.records.remove(packedPosition) == null) {
            return false;
        }
        removeEmptyBucket(dimensionId, chunkCoordinate, dimensionBuckets, bucket);
        return true;
    }

    /** Removes records that no longer have a retention reason, such as removed node scope. */
    public synchronized void removeIf(Predicate<R> predicate) {
        Objects.requireNonNull(predicate, "predicate");
        for (int index = bucketOrder.size() - 1; index >= 0; index--) {
            Bucket<R> bucket = bucketOrder.get(index);
            bucket.records.values().removeIf(predicate);
            if (!bucket.records.isEmpty()) {
                continue;
            }
            Long2ObjectMap<Bucket<R>> dimensionBuckets = machineBuckets.get(bucket.dimensionId);
            if (dimensionBuckets == null || dimensionBuckets.remove(bucket.chunkCoordinate) != bucket) {
                throw new IllegalStateException("Machine bucket maps diverged during record pruning");
            }
            removeBucketOrder(bucket);
            dropResidencyCandidate(bucket);
            if (dimensionBuckets.isEmpty()) {
                machineBuckets.remove(bucket.dimensionId);
            }
        }
    }

    /**
     * Checks every bucket whose chunk residency was touched since the previous tick and
     * invokes transition work only when the bucket's residency actually changed. Buckets
     * that no chunk event touched are never inspected.
     */
    public void tick(TransitionSink<R> sink) {
        Objects.requireNonNull(sink, "sink");
        List<Transition<R>> transitions = null;
        synchronized (this) {
            if (residencyCandidates.isEmpty()) {
                return;
            }
            for (int index = 0, count = residencyCandidates.size(); index < count; index++) {
                Bucket<R> bucket = residencyCandidates.get(index);
                bucket.candidateIndex = -1;
                LongSet dimensionResidency = loadedChunks.get(bucket.dimensionId);
                boolean loaded = dimensionResidency != null && dimensionResidency.contains(bucket.chunkCoordinate);
                if (loaded == bucket.loaded) {
                    continue;
                }
                if (transitions == null) {
                    transitions = new ArrayList<>();
                }
                transitions.add(new Transition<>(
                    bucket.dimensionId, bucket.chunkCoordinate, loaded, new ArrayList<>(bucket.records.values())
                ));
                bucket.loaded = loaded;
            }
            residencyCandidates.clear();
        }
        if (transitions == null) {
            return;
        }
        for (Transition<R> transition : transitions) {
            if (transition.loaded) {
                for (R record : transition.records) {
                    if (!sink.reconcileLoaded(transition.dimensionId, transition.chunkCoordinate, record)) {
                        remove(transition.dimensionId, transition.chunkCoordinate, record.packedPosition());
                    }
                }
            } else {
                sink.dehydrateUnloaded(
                    transition.dimensionId, transition.chunkCoordinate, transition.records
                );
            }
        }
    }

    public synchronized void clear() {
        loadedChunks.clear();
        machineBuckets.clear();
        for (int index = 0; index < bucketOrder.size(); index++) {
            Bucket<R> bucket = bucketOrder.get(index);
            bucket.orderIndex = -1;
            bucket.candidateIndex = -1;
        }
        bucketOrder.clear();
        residencyCandidates.clear();
    }

    private void enqueueResidencyCandidate(int dimensionId, long chunkCoordinate) {
        Long2ObjectMap<Bucket<R>> dimensionBuckets = machineBuckets.get(dimensionId);
        Bucket<R> bucket = dimensionBuckets == null ? null : dimensionBuckets.get(chunkCoordinate);
        if (bucket != null) {
            enqueueResidencyCandidate(bucket);
        }
    }

    private void enqueueResidencyCandidate(Bucket<R> bucket) {
        if (bucket.candidateIndex >= 0) {
            return;
        }
        bucket.candidateIndex = residencyCandidates.size();
        residencyCandidates.add(bucket);
    }

    private void dropResidencyCandidate(Bucket<R> bucket) {
        int candidateIndex = bucket.candidateIndex;
        if (candidateIndex < 0) {
            return;
        }
        bucket.candidateIndex = -1;
        int lastIndex = residencyCandidates.size() - 1;
        if (candidateIndex != lastIndex) {
            Bucket<R> moved = residencyCandidates.get(lastIndex);
            residencyCandidates.set(candidateIndex, moved);
            moved.candidateIndex = candidateIndex;
        }
        residencyCandidates.remove(lastIndex);
    }

    private void removeEmptyBucket(int dimensionId,
                                   long chunkCoordinate,
                                   Long2ObjectMap<Bucket<R>> dimensionBuckets,
                                   Bucket<R> bucket) {
        if (!bucket.records.isEmpty()) {
            return;
        }
        removeBucketOrder(bucket);
        dropResidencyCandidate(bucket);
        dimensionBuckets.remove(chunkCoordinate);
        if (dimensionBuckets.isEmpty()) {
            machineBuckets.remove(dimensionId);
        }
    }

    private void removeBucketOrder(Bucket<R> bucket) {
        int orderIndex = bucket.orderIndex;
        if (orderIndex < 0 || orderIndex >= bucketOrder.size() || bucketOrder.get(orderIndex) != bucket) {
            throw new IllegalStateException("Machine bucket is missing from stable iteration order");
        }
        bucket.orderIndex = -1;
        int lastIndex = bucketOrder.size() - 1;
        if (orderIndex != lastIndex) {
            Bucket<R> moved = bucketOrder.get(lastIndex);
            bucketOrder.set(orderIndex, moved);
            moved.orderIndex = orderIndex;
        }
        bucketOrder.remove(lastIndex);
    }

    /** Lightweight record contract required for position-keyed cold storage. */
    interface PositionRecord {

        /** Returns the packed block position used as the stable record identity. */
        long packedPosition();
    }

    /** Tick-pre transition callbacks that own runtime dehydration and loaded reconciliation. */
    interface TransitionSink<R> {

        /** Deactivates every runtime in a bucket that has just become non-resident. */
        void dehydrateUnloaded(int dimensionId, long chunkCoordinate, Collection<R> records);

        /**
         * Resolves one saved position in a bucket that has just become resident.
         *
         * @return true to retain the position record, false when removal is definitive
         */
        boolean reconcileLoaded(int dimensionId, long chunkCoordinate, R record);
    }

    private static final class Bucket<R extends PositionRecord> {
        private final int dimensionId;
        private final long chunkCoordinate;
        private final Long2ObjectMap<R> records = new Long2ObjectOpenHashMap<>();
        private boolean loaded;
        private int orderIndex = -1;
        private int candidateIndex = -1;

        private Bucket(int dimensionId, long chunkCoordinate) {
            this.dimensionId = dimensionId;
            this.chunkCoordinate = chunkCoordinate;
        }
    }

    //? if <1.20
    @Desugar
    private record Transition<R extends PositionRecord>(
        int dimensionId,
        long chunkCoordinate,
        boolean loaded,
        Collection<R> records
    ) {
    }
}
