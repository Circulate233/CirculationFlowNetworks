package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.utils.ChunkCoordUtils;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Reference-counted observer windows, updated only where player coverage changes.
 */
final class WarningObserverIndex {

    private final int radius;
    private final Object2ObjectMap<UUID, Observer> players = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<String, Long2IntMap> counts = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<String, LongSet> chunks = new Object2ObjectOpenHashMap<>();

    WarningObserverIndex(int radius) {
        if (radius < 0) {
            throw new IllegalArgumentException("Observer radius must not be negative");
        }
        this.radius = radius;
    }

    Object2ObjectMap<String, LongSet> chunks() {
        return chunks;
    }

    void update(UUID playerId, String dimension, int chunkX, int chunkZ) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(dimension, "dimension");
        Observer previous = players.get(playerId);
        boolean sameDimension = previous != null && dimension.equals(previous.dimension);
        if (sameDimension && previous.x == chunkX && previous.z == chunkZ) {
            return;
        }
        if (previous != null) {
            updateWindow(previous.dimension, previous.x, previous.z, -1,
                sameDimension, chunkX, chunkZ);
        } else {
            previous = new Observer();
            players.put(playerId, previous);
        }
        updateWindow(dimension, chunkX, chunkZ, 1, sameDimension, previous.x, previous.z);
        previous.dimension = dimension;
        previous.x = chunkX;
        previous.z = chunkZ;
    }

    void retainPlayers(Set<UUID> onlinePlayers) {
        for (var iterator = players.object2ObjectEntrySet().iterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            if (!onlinePlayers.contains(entry.getKey())) {
                Observer observer = entry.getValue();
                updateWindow(observer.dimension, observer.x, observer.z, -1, false, 0, 0);
                iterator.remove();
            }
        }
    }

    void clear() {
        players.clear();
        counts.clear();
        chunks.clear();
    }

    private void updateWindow(String dimension, int centerX, int centerZ, int delta,
                              boolean skipOverlap, int otherX, int otherZ) {
        Long2IntMap dimensionCounts = counts.get(dimension);
        if (dimensionCounts == null) {
            if (delta < 0) {
                throw new IllegalStateException("Missing observer dimension " + dimension);
            }
            dimensionCounts = new Long2IntOpenHashMap();
            counts.put(dimension, dimensionCounts);
            chunks.put(dimension, dimensionCounts.keySet());
        }
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                if (skipOverlap && Math.abs((long) x - otherX) <= radius
                    && Math.abs((long) z - otherZ) <= radius) {
                    continue;
                }
                long chunk = ChunkCoordUtils.mergeChunkCoords(x, z);
                int count = dimensionCounts.get(chunk) + delta;
                if (count < 0) {
                    throw new IllegalStateException("Negative observer count");
                }
                if (count == 0) {
                    dimensionCounts.remove(chunk);
                } else {
                    dimensionCounts.put(chunk, count);
                }
            }
        }
        if (dimensionCounts.isEmpty()) {
            counts.remove(dimension);
            chunks.remove(dimension);
        }
    }

    private static final class Observer {
        String dimension;
        int x;
        int z;
    }
}
