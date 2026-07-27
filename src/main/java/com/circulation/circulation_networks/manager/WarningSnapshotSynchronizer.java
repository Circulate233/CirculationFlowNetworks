package com.circulation.circulation_networks.manager;

import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongPredicate;

/**
 * Maintains the authoritative per-player warning snapshot schedule. Global
 * changes and dimension changes are sent immediately, while unchanged visible
 * ranges are sent again on the configured periodic synchronization boundary.
 */
final class WarningSnapshotSynchronizer {

    private final long rangeSyncIntervalTicks;
    private final Object2ObjectMap<UUID, PlayerState> players = new Object2ObjectOpenHashMap<>();
    private final LongOpenHashSet desiredVisible = new LongOpenHashSet();

    WarningSnapshotSynchronizer(long rangeSyncIntervalTicks) {
        if (rangeSyncIntervalTicks <= 0L) {
            throw new IllegalArgumentException("Warning range synchronization interval must be positive");
        }
        this.rangeSyncIntervalTicks = rangeSyncIntervalTicks;
    }

    /**
     * Updates one player's current dimension and emits a complete visible
     * warning snapshot when required by global, range, retry, or dimension
     * state.
     *
     * @param playerId stable player identity
     * @param dimensionKey canonical, collision-free identity of the player's current dimension
     * @param tick monotonically increasing server tick
     * @param globalPositions complete warning set for the dimension
     * @param isVisible position visibility predicate for this player
     * @param sink complete-snapshot receiver
     */
    void synchronize(UUID playerId,
                        String dimensionKey,
                     long tick,
                     LongSet globalPositions,
                     LongPredicate isVisible,
                     SnapshotSink sink) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(dimensionKey, "dimensionKey");
        Objects.requireNonNull(globalPositions, "globalPositions");
        Objects.requireNonNull(isVisible, "isVisible");
        Objects.requireNonNull(sink, "sink");

        PlayerState player = players.computeIfAbsent(playerId, ignored -> new PlayerState());
        boolean dimensionChanged = !dimensionKey.equals(player.currentDimension);
        player.currentDimension = dimensionKey;
        DimensionState dimension = player.dimensions.computeIfAbsent(dimensionKey, ignored -> new DimensionState());

        boolean globalChanged = !dimension.globalPositions.equals(globalPositions);
        if (globalChanged) {
            dimension.globalPositions.clear();
            dimension.globalPositions.addAll(globalPositions);
        }
        if (globalChanged || dimensionChanged || tick % rangeSyncIntervalTicks == 0L) {
            dimension.syncPending = true;
        }
        if (!dimension.syncPending) {
            return;
        }

        desiredVisible.clear();
        for (long position : dimension.globalPositions) {
            if (isVisible.test(position)) {
                desiredVisible.add(position);
            }
        }
        if (dimension.revision == Long.MAX_VALUE) {
            throw new IllegalStateException("Energy warning revision exhausted");
        }
        long revision = ++dimension.revision;
        sink.send(dimensionKey, revision, desiredVisible);
        if (!dimension.visiblePositions.equals(desiredVisible)) {
            dimension.visiblePositions.clear();
            dimension.visiblePositions.addAll(desiredVisible);
        }
        dimension.syncPending = false;
    }

    /**
     * Drops synchronization state for players no longer online.
     *
     * @param onlinePlayerIds complete online-player identity set
     */
    void retainPlayers(Set<UUID> onlinePlayerIds) {
        Objects.requireNonNull(onlinePlayerIds, "onlinePlayerIds");
        players.keySet().retainAll(onlinePlayerIds);
    }

    /** Clears all per-session player and revision state. */
    void clear() {
        players.clear();
        desiredVisible.clear();
    }

    /**
     * Receives one complete visible warning snapshot. Implementations must
     * consume or copy {@code positions} before returning.
     */
    interface SnapshotSink {

        /**
         * Sends one complete snapshot for a player dimension.
         *
         * @param dimensionKey canonical target dimension identity
         * @param revision strictly increasing per-player/dimension revision
         * @param positions complete visible packed-position collection
         */
        void send(String dimensionKey, long revision, LongCollection positions);
    }

    private static final class PlayerState {
        private final Object2ObjectMap<String, DimensionState> dimensions = new Object2ObjectOpenHashMap<>();
        private String currentDimension;
    }

    private static final class DimensionState {
        private final LongSet globalPositions = new LongOpenHashSet();
        private final LongSet visiblePositions = new LongOpenHashSet();
        private long revision;
        private boolean syncPending = true;
    }
}
