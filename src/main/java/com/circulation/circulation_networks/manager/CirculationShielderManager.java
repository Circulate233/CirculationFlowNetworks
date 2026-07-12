package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.api.ICirculationShielderBlockEntity;
import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.utils.ChunkCoordUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import it.unimi.dsi.fastutil.objects.ReferenceSets;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
//~ mc_imports
import net.minecraft.util.math.BlockPos;

public final class CirculationShielderManager {

    public static final CirculationShielderManager INSTANCE = new CirculationShielderManager();

    private final Int2ObjectMap<ReferenceSet<ICirculationShielderBlockEntity>> dimShielders = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<ICirculationShielderBlockEntity[]> idimShielders = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<Long2ObjectMap<ReferenceSet<ICirculationShielderBlockEntity>>> chunkShielders = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<Long2ObjectMap<ObjectArrayList<ActiveShielderSnapshot>>> activeChunkShielders = new Int2ObjectOpenHashMap<>();
    private final Reference2ObjectMap<ICirculationShielderBlockEntity, LongSet> shielderCoveredChunks = new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectMap<ICirculationShielderBlockEntity, ActiveShielderSnapshot> activeShielderSnapshots = new Reference2ObjectOpenHashMap<>();
    private static final ICirculationShielderBlockEntity[] EMPTY = new ICirculationShielderBlockEntity[0];

    public CirculationShielderManager() {
        dimShielders.defaultReturnValue(ReferenceSets.emptySet());
    }

    public Int2ObjectMap<ICirculationShielderBlockEntity[]> getDimShielders() {
        return idimShielders;
    }

    public ICirculationShielderBlockEntity[] getShieldersForDim(int dimId) {
        var i = idimShielders.get(dimId);
        if (i == null) {
            return EMPTY;
        }
        return i;
    }

    public void register(ICirculationShielderBlockEntity shielder, int dimId) {
        if (shielder == null) return;

        ReferenceSet<ICirculationShielderBlockEntity> set = dimShielders.get(dimId);
        if (set == dimShielders.defaultReturnValue()) {
            dimShielders.put(dimId, set = new ReferenceOpenHashSet<>());
        }
        if (set.add(shielder)) {
            indexShielder(shielder, dimId);
            refreshDimCache(dimId, set);
            notifyActiveCoverageChanged(dimId, null, activeShielderSnapshots.get(shielder));
        }
    }

    public void unregister(ICirculationShielderBlockEntity shielder, int dimId) {
        if (shielder == null) return;

        var shielders = dimShielders.get(dimId);
        if (shielders == null) return;
        if (!shielders.remove(shielder)) return;
        ActiveShielderSnapshot previousSnapshot = activeShielderSnapshots.get(shielder);
        removeShielderIndex(shielder, dimId);
        removeActiveShielderIndex(shielder, dimId);
        notifyActiveCoverageChanged(dimId, previousSnapshot, null);
        if (shielders.isEmpty()) {
            dimShielders.remove(dimId);
            chunkShielders.remove(dimId);
            activeChunkShielders.remove(dimId);
        }
        refreshDimCache(dimId, shielders);
    }

    public void refreshActiveState(ICirculationShielderBlockEntity shielder, int dimId) {
        if (shielder == null) return;
        var shielders = dimShielders.get(dimId);
        if (shielders == null || shielders == dimShielders.defaultReturnValue()) {
            register(shielder, dimId);
            return;
        }
        if (!shielders.contains(shielder)) {
            register(shielder, dimId);
            return;
        }
        if (!shielderCoveredChunks.containsKey(shielder)) {
            indexShielder(shielder, dimId);
        }
        ActiveShielderSnapshot previousSnapshot = activeShielderSnapshots.get(shielder);
        removeActiveShielderIndex(shielder, dimId);
        indexActiveShielder(shielder, dimId);
        notifyActiveCoverageChanged(dimId, previousSnapshot, activeShielderSnapshots.get(shielder));
    }

    private void indexShielder(ICirculationShielderBlockEntity shielder, int dimId) {
        var pos = shielder.getBEPos();
        int range = Math.max(0, shielder.getMaxScope());
        int minChunkX = (pos.getX() - range) >> 4;
        int maxChunkX = (pos.getX() + range) >> 4;
        int minChunkZ = (pos.getZ() - range) >> 4;
        int maxChunkZ = (pos.getZ() + range) >> 4;

        Long2ObjectMap<ReferenceSet<ICirculationShielderBlockEntity>> dimChunks = chunkShielders.get(dimId);
        if (dimChunks == null) {
            dimChunks = new Long2ObjectOpenHashMap<>();
            dimChunks.defaultReturnValue(ReferenceSets.emptySet());
            chunkShielders.put(dimId, dimChunks);
        }

        LongSet coveredChunks = new LongOpenHashSet();
        for (int cx = minChunkX; cx <= maxChunkX; ++cx) {
            for (int cz = minChunkZ; cz <= maxChunkZ; ++cz) {
                long chunkCoord = ChunkCoordUtils.mergeChunkCoords(cx, cz);
                coveredChunks.add(chunkCoord);

                ReferenceSet<ICirculationShielderBlockEntity> chunkSet = dimChunks.get(chunkCoord);
                if (chunkSet == dimChunks.defaultReturnValue()) {
                    chunkSet = new ReferenceOpenHashSet<>();
                    dimChunks.put(chunkCoord, chunkSet);
                }
                chunkSet.add(shielder);
            }
        }
        shielderCoveredChunks.put(shielder, LongSets.unmodifiable(coveredChunks));
        indexActiveShielder(shielder, dimId);
    }

    private void removeShielderIndex(ICirculationShielderBlockEntity shielder, int dimId) {
        LongSet coveredChunks = shielderCoveredChunks.remove(shielder);
        if (coveredChunks == null || coveredChunks.isEmpty()) return;

        Long2ObjectMap<ReferenceSet<ICirculationShielderBlockEntity>> dimChunks = chunkShielders.get(dimId);
        if (dimChunks == null) return;

        for (long chunkCoord : coveredChunks) {
            ReferenceSet<ICirculationShielderBlockEntity> chunkSet = dimChunks.get(chunkCoord);
            if (chunkSet == dimChunks.defaultReturnValue()) continue;
            if (chunkSet.size() == 1) {
                dimChunks.remove(chunkCoord);
            } else {
                chunkSet.remove(shielder);
            }
        }
    }

    private void indexActiveShielder(ICirculationShielderBlockEntity shielder, int dimId) {
        if (!shielder.isActive()) {
            return;
        }
        var pos = shielder.getBEPos();
        int scope = Math.max(0, shielder.getScope());
        int minX = pos.getX() - scope;
        int minY = pos.getY() - scope;
        int minZ = pos.getZ() - scope;
        int maxX = pos.getX() + scope;
        int maxY = pos.getY() + scope;
        int maxZ = pos.getZ() + scope;
        int minChunkX = minX >> 4;
        int maxChunkX = maxX >> 4;
        int minChunkZ = minZ >> 4;
        int maxChunkZ = maxZ >> 4;

        Long2ObjectMap<ObjectArrayList<ActiveShielderSnapshot>> dimChunks = activeChunkShielders.get(dimId);
        if (dimChunks == null) {
            dimChunks = new Long2ObjectOpenHashMap<>();
            activeChunkShielders.put(dimId, dimChunks);
        }

        LongSet coveredChunks = new LongOpenHashSet();
        ActiveShielderSnapshot snapshot = new ActiveShielderSnapshot(minX, minY, minZ, maxX, maxY, maxZ, LongSets.unmodifiable(coveredChunks));
        for (int cx = minChunkX; cx <= maxChunkX; ++cx) {
            for (int cz = minChunkZ; cz <= maxChunkZ; ++cz) {
                long chunkCoord = ChunkCoordUtils.mergeChunkCoords(cx, cz);
                coveredChunks.add(chunkCoord);
                ObjectArrayList<ActiveShielderSnapshot> chunkSnapshots = dimChunks.get(chunkCoord);
                if (chunkSnapshots == null) {
                    chunkSnapshots = new ObjectArrayList<>();
                    dimChunks.put(chunkCoord, chunkSnapshots);
                }
                chunkSnapshots.add(snapshot);
            }
        }
        activeShielderSnapshots.put(shielder, snapshot);
    }

    private void removeActiveShielderIndex(ICirculationShielderBlockEntity shielder, int dimId) {
        ActiveShielderSnapshot snapshot = activeShielderSnapshots.remove(shielder);
        if (snapshot == null) {
            return;
        }
        Long2ObjectMap<ObjectArrayList<ActiveShielderSnapshot>> dimChunks = activeChunkShielders.get(dimId);
        if (dimChunks == null) {
            return;
        }
        for (long chunkCoord : snapshot.coveredChunks) {
            ObjectArrayList<ActiveShielderSnapshot> chunkSnapshots = dimChunks.get(chunkCoord);
            if (chunkSnapshots == null) {
                continue;
            }
            chunkSnapshots.remove(snapshot);
            if (chunkSnapshots.isEmpty()) {
                dimChunks.remove(chunkCoord);
            }
        }
        if (dimChunks.isEmpty()) {
            activeChunkShielders.remove(dimId);
        }
    }

    private void notifyActiveCoverageChanged(int dimId,
                                             ActiveShielderSnapshot previousSnapshot,
                                             ActiveShielderSnapshot currentSnapshot) {
        LongSet previousPositions = snapshotCoveredNodePositions(dimId, previousSnapshot);
        LongSet currentPositions = snapshotCoveredNodePositions(dimId, currentSnapshot);
        LongSet added = difference(currentPositions, previousPositions);
        LongSet removed = difference(previousPositions, currentPositions);
        // Machine routes are indexed separately from nodes, so every active
        // coverage transition must refresh the dimension's machine routes.
        MachineBindingIndex.INSTANCE.onShielderCoverageChanged(dimId, added, removed);
    }

    private LongSet snapshotCoveredNodePositions(int dimId, ActiveShielderSnapshot snapshot) {
        LongSet positions = new LongOpenHashSet();
        if (snapshot == null) {
            return LongSets.unmodifiable(positions);
        }
        for (INode node : NetworkManager.INSTANCE.getActiveNodes()) {
            if (node.getDimensionId() == dimId && snapshot.contains(node.getPos())) {
                //~ if >=1.20 '.toLong()' -> '.asLong()' {
                positions.add(node.getPos().toLong());
                //~}
            }
        }
        return LongSets.unmodifiable(positions);
    }

    private static LongSet difference(LongSet first, LongSet second) {
        LongSet difference = new LongOpenHashSet();
        for (long position : first) {
            if (!second.contains(position)) {
                difference.add(position);
            }
        }
        return LongSets.unmodifiable(difference);
    }

    private void refreshDimCache(int dimId, ReferenceSet<ICirculationShielderBlockEntity> shielders) {
        if (shielders.isEmpty()) {
            idimShielders.remove(dimId);
            return;
        }
        idimShielders.put(dimId, shielders.toArray(EMPTY));
    }

    public boolean isBlockedByShielder(int dimId, BlockPos tePos) {
        Long2ObjectMap<ObjectArrayList<ActiveShielderSnapshot>> dimChunks = activeChunkShielders.get(dimId);
        if (dimChunks == null) return false;

        ObjectArrayList<ActiveShielderSnapshot> snapshots = dimChunks.get(ChunkCoordUtils.mergeChunkCoords(tePos));
        if (snapshots == null || snapshots.isEmpty()) return false;

        for (int i = 0, size = snapshots.size(); i < size; i++) {
            if (snapshots.get(i).contains(tePos)) return true;
        }
        return false;
    }

    public boolean hasActiveShielderInChunk(int dimId, long chunkCoord) {
        Long2ObjectMap<ObjectArrayList<ActiveShielderSnapshot>> dimChunks = activeChunkShielders.get(dimId);
        if (dimChunks == null) return false;
        ObjectArrayList<ActiveShielderSnapshot> snapshots = dimChunks.get(chunkCoord);
        return snapshots != null && !snapshots.isEmpty();
    }

    private static final class ActiveShielderSnapshot {
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;
        private final LongSet coveredChunks;

        private ActiveShielderSnapshot(int minX,
                                       int minY,
                                       int minZ,
                                       int maxX,
                                       int maxY,
                                       int maxZ,
                                       LongSet coveredChunks) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.coveredChunks = coveredChunks;
        }

        private boolean contains(BlockPos pos) {
            return minX <= pos.getX() && minY <= pos.getY() && minZ <= pos.getZ()
                && maxX >= pos.getX() && maxY >= pos.getY() && maxZ >= pos.getZ();
        }
    }
}
