package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.pocket.PocketNodeRecord;
import com.circulation.circulation_networks.utils.Functions;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Owns pending pocket-node records and their host-chunk index. Startup retries
 * are gated by a chunk-availability callback before activation is invoked, and
 * chunk-load retries visit only records indexed to the loaded chunk.
 */
final class PocketNodePendingIndex {

    private final Object2ObjectMap<String, Long2ObjectMap<PocketNodeRecord>> records =
        new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<String, Long2ObjectMap<LongSet>> chunks =
        new Object2ObjectOpenHashMap<>();
    private final ObjectArrayList<PocketNodeRecord> retryScratch = new ObjectArrayList<>();

    /** Adds or replaces one pending record and updates its chunk membership. */
    void put(PocketNodeRecord record) {
        Objects.requireNonNull(record, "record");
        String dimensionId = record.dimensionId();
        long position = record.pos().asLong();
        Long2ObjectMap<PocketNodeRecord> dimensionRecords = records.computeIfAbsent(
            dimensionId, ignored -> new Long2ObjectOpenHashMap<>());
        PocketNodeRecord previous = dimensionRecords.put(position, record);
        if (previous != null) {
            unindex(previous, position);
        }
        long chunk = Functions.mergeChunkCoords(record.pos());
        Long2ObjectMap<LongSet> dimensionChunks = chunks.computeIfAbsent(
            dimensionId, ignored -> new Long2ObjectOpenHashMap<>());
        dimensionChunks.computeIfAbsent(chunk, ignored -> new LongOpenHashSet()).add(position);
    }

    /** Returns the pending record at one dimension and packed position. */
    @Nullable
    PocketNodeRecord get(String dimensionId, long position) {
        Long2ObjectMap<PocketNodeRecord> dimensionRecords = records.get(dimensionId);
        return dimensionRecords == null ? null : dimensionRecords.get(position);
    }

    /** Returns whether a pending record exists at one dimension and position. */
    boolean contains(String dimensionId, long position) {
        return get(dimensionId, position) != null;
    }

    /** Removes and returns one pending record, including its chunk membership. */
    @Nullable
    PocketNodeRecord remove(String dimensionId, long position) {
        Long2ObjectMap<PocketNodeRecord> dimensionRecords = records.get(dimensionId);
        if (dimensionRecords == null) {
            return null;
        }
        PocketNodeRecord removed = dimensionRecords.remove(position);
        if (removed == null) {
            return null;
        }
        unindex(removed, position);
        if (dimensionRecords.isEmpty()) {
            records.remove(dimensionId);
            chunks.remove(dimensionId);
        }
        return removed;
    }

    /**
     * Retries every startup record whose host chunk is already loaded. The
     * activation callback is never invoked for an unloaded host chunk.
     */
    void retryLoaded(ChunkAvailability availability, ActivationAttempt activation) {
        Objects.requireNonNull(availability, "availability");
        Objects.requireNonNull(activation, "activation");
        retryScratch.clear();
        for (Long2ObjectMap<PocketNodeRecord> dimensionRecords : records.values()) {
            retryScratch.addAll(dimensionRecords.values());
        }
        try {
            for (int index = 0; index < retryScratch.size(); index++) {
                PocketNodeRecord record = retryScratch.get(index);
                if (availability.isLoaded(record)) {
                    activation.tryActivate(record);
                }
            }
        } finally {
            retryScratch.clear();
        }
    }

    /** Retries only records indexed to one loaded host chunk. */
    void retryChunk(String dimensionId, long chunk, ActivationAttempt activation) {
        Objects.requireNonNull(activation, "activation");
        Long2ObjectMap<LongSet> dimensionChunks = chunks.get(dimensionId);
        LongSet positions = dimensionChunks == null ? null : dimensionChunks.get(chunk);
        if (positions == null || positions.isEmpty()) {
            return;
        }
        Long2ObjectMap<PocketNodeRecord> dimensionRecords = records.get(dimensionId);
        retryScratch.clear();
        for (long position : positions) {
            PocketNodeRecord record = dimensionRecords == null ? null : dimensionRecords.get(position);
            if (record != null) {
                retryScratch.add(record);
            }
        }
        try {
            for (int index = 0; index < retryScratch.size(); index++) {
                activation.tryActivate(retryScratch.get(index));
            }
        } finally {
            retryScratch.clear();
        }
    }

    /** Visits every pending record without exposing mutable index maps. */
    void forEach(RecordVisitor visitor) {
        Objects.requireNonNull(visitor, "visitor");
        for (Long2ObjectMap<PocketNodeRecord> dimensionRecords : records.values()) {
            for (PocketNodeRecord record : dimensionRecords.values()) {
                visitor.visit(record);
            }
        }
    }

    /** Returns whether the pending index contains no records. */
    boolean isEmpty() {
        return records.isEmpty();
    }

    /** Clears all pending records and chunk memberships. */
    void clear() {
        records.clear();
        chunks.clear();
        retryScratch.clear();
    }

    private void unindex(PocketNodeRecord record, long position) {
        String dimensionId = record.dimensionId();
        Long2ObjectMap<LongSet> dimensionChunks = chunks.get(dimensionId);
        if (dimensionChunks == null) {
            return;
        }
        long chunk = Functions.mergeChunkCoords(record.pos());
        LongSet positions = dimensionChunks.get(chunk);
        if (positions == null) {
            return;
        }
        positions.remove(position);
        if (positions.isEmpty()) {
            dimensionChunks.remove(chunk);
        }
    }

    /** Tests whether a pending record's host chunk can be safely inspected. */
    interface ChunkAvailability {

        /** Returns whether activation may inspect the record's host block. */
        boolean isLoaded(PocketNodeRecord record);
    }

    /** Performs one real activation attempt for an eligible pending record. */
    interface ActivationAttempt {

        /** Attempts to activate the supplied pending record. */
        void tryActivate(PocketNodeRecord record);
    }

    /** Consumes one pending record during persistence traversal. */
    interface RecordVisitor {

        /** Visits one pending record. */
        void visit(PocketNodeRecord record);
    }
}
