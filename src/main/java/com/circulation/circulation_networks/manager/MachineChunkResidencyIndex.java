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
 * Chunk events only update the residency sets; tick-pre owns all transition work.
 *
 * @param <R> lightweight machine position record type
 */
final class MachineChunkResidencyIndex<R extends MachineChunkResidencyIndex.PositionRecord> {

    private final Int2ObjectMap<LongSet> loadedChunks = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<Long2ObjectMap<Bucket<R>>> machineBuckets = new Int2ObjectOpenHashMap<>();
    private final ObjectArrayList<BucketEntry<R>> bucketOrder = new ObjectArrayList<>();

    /** Marks one chunk resident without touching machine records or runtime state. */
    public synchronized void markLoaded(int dimensionId, long chunkCoordinate) {
        loadedChunks.computeIfAbsent(dimensionId, ignored -> new LongOpenHashSet()).add(chunkCoordinate);
    }

    /** Marks one chunk non-resident without touching machine records or runtime state. */
    public synchronized void markUnloaded(int dimensionId, long chunkCoordinate) {
        LongSet dimensionChunks = loadedChunks.get(dimensionId);
        if (dimensionChunks == null) {
            return;
        }
        dimensionChunks.remove(chunkCoordinate);
        if (dimensionChunks.isEmpty()) {
            loadedChunks.remove(dimensionId);
        }
    }

    /** Clears primitive residency for a dimension while retaining its cold machine records. */
    public synchronized void clearDimensionResidency(int dimensionId) {
        loadedChunks.remove(dimensionId);
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
            bucket = new Bucket<>();
            bucket.loaded = isMarkedLoaded(dimensionId, chunkCoordinate);
            dimensionBuckets.put(chunkCoordinate, bucket);
            bucketOrder.add(new BucketEntry<>(dimensionId, chunkCoordinate, bucket));
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
            BucketEntry<R> entry = bucketOrder.get(index);
            entry.bucket.records.values().removeIf(predicate);
            if (!entry.bucket.records.isEmpty()) {
                continue;
            }
            bucketOrder.remove(index);
            Long2ObjectMap<Bucket<R>> dimensionBuckets = machineBuckets.get(entry.dimensionId);
            if (dimensionBuckets == null || dimensionBuckets.remove(entry.chunkCoordinate) != entry.bucket) {
                throw new IllegalStateException("Machine bucket maps diverged during record pruning");
            }
            if (dimensionBuckets.isEmpty()) {
                machineBuckets.remove(entry.dimensionId);
            }
        }
    }

    /**
     * Checks each machine bucket against the primitive residency marker exactly once and
     * invokes transition work only when the bucket's residency changed.
     */
    public void tick(TransitionSink<R> sink) {
        Objects.requireNonNull(sink, "sink");
        List<Transition<R>> transitions = null;
        synchronized (this) {
            for (int bucketIndex = 0, bucketCount = bucketOrder.size(); bucketIndex < bucketCount; bucketIndex++) {
                BucketEntry<R> bucketEntry = bucketOrder.get(bucketIndex);
                int dimensionId = bucketEntry.dimensionId;
                LongSet dimensionResidency = loadedChunks.get(dimensionId);
                long chunkCoordinate = bucketEntry.chunkCoordinate;
                Bucket<R> bucket = bucketEntry.bucket;
                boolean loaded = dimensionResidency != null && dimensionResidency.contains(chunkCoordinate);
                if (loaded == bucket.loaded) {
                    continue;
                }
                if (transitions == null) {
                    transitions = new ArrayList<>();
                }
                transitions.add(new Transition<>(
                    dimensionId, chunkCoordinate, loaded, new ArrayList<>(bucket.records.values())
                ));
                bucket.loaded = loaded;
            }
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
        bucketOrder.clear();
    }

    private void removeEmptyBucket(int dimensionId,
                                   long chunkCoordinate,
                                   Long2ObjectMap<Bucket<R>> dimensionBuckets,
                                   Bucket<R> bucket) {
        if (!bucket.records.isEmpty()) {
            return;
        }
        removeBucketOrder(bucket);
        dimensionBuckets.remove(chunkCoordinate);
        if (dimensionBuckets.isEmpty()) {
            machineBuckets.remove(dimensionId);
        }
    }

    private void removeBucketOrder(Bucket<R> bucket) {
        for (int index = bucketOrder.size() - 1; index >= 0; index--) {
            if (bucketOrder.get(index).bucket == bucket) {
                bucketOrder.remove(index);
                return;
            }
        }
        throw new IllegalStateException("Machine bucket is missing from stable iteration order");
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
        private final Long2ObjectMap<R> records = new Long2ObjectOpenHashMap<>();
        private boolean loaded;
    }

    //? if <1.20
    @Desugar
    private record BucketEntry<R extends PositionRecord>(
        int dimensionId,
        long chunkCoordinate,
        Bucket<R> bucket
    ) {
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
