package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.api.ICirculationShielderBlockEntity;
import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.utils.Functions;
import com.circulation.circulation_networks.utils.WorldResolveCompat;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import it.unimi.dsi.fastutil.objects.ReferenceSets;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
public final class CirculationShielderManager {

    public static final CirculationShielderManager INSTANCE = new CirculationShielderManager();
    private static final ICirculationShielderBlockEntity[] EMPTY = new ICirculationShielderBlockEntity[0];
    private final Object2ObjectMap<String, ReferenceSet<ICirculationShielderBlockEntity>> dimShielders = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<String, Long2ObjectMap<ReferenceSet<ICirculationShielderBlockEntity>>> dimChunkShielders = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<String, Long2ObjectMap<ObjectArrayList<ActiveShielderSnapshot>>> activeChunkShielders = new Object2ObjectOpenHashMap<>();
    private final Reference2ObjectMap<ICirculationShielderBlockEntity, LongSet> shielderCoveredChunks = new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectMap<ICirculationShielderBlockEntity, ActiveShielderSnapshot> activeShielderSnapshots = new Reference2ObjectOpenHashMap<>();

    public CirculationShielderManager() {
        dimShielders.defaultReturnValue(ReferenceSets.emptySet());
    }

    private static String getDimensionId(Level world) {
        return WorldResolveCompat.getDimensionId(world);
    }

    public Object2ObjectMap<String, ReferenceSet<ICirculationShielderBlockEntity>> getDimShielders() {
        return dimShielders;
    }

    @NotNull
    public ICirculationShielderBlockEntity[] getShieldersForDim(String dimId) {
        var shielders = dimShielders.get(dimId);
        if (shielders == null || shielders.isEmpty()) {
            return EMPTY;
        }
        return shielders.toArray(EMPTY);
    }

    public void register(ICirculationShielderBlockEntity shielder, String dimId) {
        if (shielder == null) return;

        ReferenceSet<ICirculationShielderBlockEntity> set = dimShielders.get(dimId);
        if (set == dimShielders.defaultReturnValue()) {
            set = new ReferenceOpenHashSet<>();
            dimShielders.put(dimId, set);
        }
        if (!set.add(shielder)) {
            return;
        }

        Long2ObjectMap<ReferenceSet<ICirculationShielderBlockEntity>> chunkMap = dimChunkShielders.get(dimId);
        if (chunkMap == null) {
            chunkMap = new Long2ObjectOpenHashMap<>();
            chunkMap.defaultReturnValue(ReferenceSets.emptySet());
            dimChunkShielders.put(dimId, chunkMap);
        }

        int scope = Math.max(0, shielder.getMaxScope());
        int nodeX = shielder.getBEPos().getX();
        int nodeZ = shielder.getBEPos().getZ();
        int minChunkX = (nodeX - scope) >> 4;
        int maxChunkX = (nodeX + scope) >> 4;
        int minChunkZ = (nodeZ - scope) >> 4;
        int maxChunkZ = (nodeZ + scope) >> 4;

        LongSet coveredChunks = new LongOpenHashSet();
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                long chunkCoord = Functions.mergeChunkCoords(chunkX, chunkZ);
                coveredChunks.add(chunkCoord);

                ReferenceSet<ICirculationShielderBlockEntity> chunkShielders = chunkMap.get(chunkCoord);
                if (chunkShielders == chunkMap.defaultReturnValue()) {
                    chunkShielders = new ReferenceOpenHashSet<>();
                    chunkMap.put(chunkCoord, chunkShielders);
                }
                chunkShielders.add(shielder);
            }
        }
        shielderCoveredChunks.put(shielder, coveredChunks);
        indexActiveShielder(shielder, dimId);
        notifyActiveCoverageChanged(dimId, null, activeShielderSnapshots.get(shielder));
    }

    public void unregister(ICirculationShielderBlockEntity shielder, String dimId) {
        if (shielder == null) return;

        ActiveShielderSnapshot previousSnapshot = activeShielderSnapshots.get(shielder);

        var shielders = dimShielders.get(dimId);
        if (shielders != null) {
            shielders.remove(shielder);
            if (shielders.isEmpty()) {
                dimShielders.remove(dimId);
            }
        }

        removeActiveShielderIndex(shielder, dimId);
        notifyActiveCoverageChanged(dimId, previousSnapshot, null);
        LongSet coveredChunks = shielderCoveredChunks.remove(shielder);
        if (coveredChunks == null || coveredChunks.isEmpty()) {
            return;
        }

        var chunkMap = dimChunkShielders.get(dimId);
        if (chunkMap == null) {
            return;
        }

        for (long coveredChunk : coveredChunks) {
            ReferenceSet<ICirculationShielderBlockEntity> chunkShielders = chunkMap.get(coveredChunk);
            if (chunkShielders == chunkMap.defaultReturnValue()) {
                continue;
            }
            if (chunkShielders.size() == 1) {
                chunkMap.remove(coveredChunk);
            } else {
                chunkShielders.remove(shielder);
            }
        }
        if (chunkMap.isEmpty()) {
            dimChunkShielders.remove(dimId);
        }
    }

    public void refreshActiveState(ICirculationShielderBlockEntity shielder, String dimId) {
        if (shielder == null || !shielderCoveredChunks.containsKey(shielder)) {
            return;
        }
        ActiveShielderSnapshot previousSnapshot = activeShielderSnapshots.get(shielder);
        removeActiveShielderIndex(shielder, dimId);
        indexActiveShielder(shielder, dimId);
        notifyActiveCoverageChanged(dimId, previousSnapshot, activeShielderSnapshots.get(shielder));
    }

    private void indexActiveShielder(ICirculationShielderBlockEntity shielder, String dimId) {
        if (!shielder.isActive()) {
            return;
        }
        BlockPos pos = shielder.getBEPos();
        int scope = Math.max(0, shielder.getScope());
        int minX = pos.getX() - scope;
        int minY = pos.getY() - scope;
        int minZ = pos.getZ() - scope;
        int maxX = pos.getX() + scope;
        int maxY = pos.getY() + scope;
        int maxZ = pos.getZ() + scope;
        Long2ObjectMap<ObjectArrayList<ActiveShielderSnapshot>> dimChunks =
            activeChunkShielders.computeIfAbsent(dimId, ignored -> new Long2ObjectOpenHashMap<>());
        LongSet coveredChunks = new LongOpenHashSet();
        ActiveShielderSnapshot snapshot = new ActiveShielderSnapshot(
            minX, minY, minZ, maxX, maxY, maxZ, coveredChunks
        );
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                long chunkCoord = Functions.mergeChunkCoords(chunkX, chunkZ);
                coveredChunks.add(chunkCoord);
                dimChunks.computeIfAbsent(chunkCoord, ignored -> new ObjectArrayList<>()).add(snapshot);
            }
        }
        activeShielderSnapshots.put(shielder, snapshot);
    }

    private void removeActiveShielderIndex(ICirculationShielderBlockEntity shielder, String dimId) {
        ActiveShielderSnapshot snapshot = activeShielderSnapshots.remove(shielder);
        if (snapshot == null) {
            return;
        }
        Long2ObjectMap<ObjectArrayList<ActiveShielderSnapshot>> dimChunks = activeChunkShielders.get(dimId);
        if (dimChunks == null) {
            throw new IllegalStateException("Active shielder dimension index is missing");
        }
        for (long chunkCoord : snapshot.coveredChunks) {
            ObjectArrayList<ActiveShielderSnapshot> snapshots = dimChunks.get(chunkCoord);
            if (snapshots == null || !snapshots.remove(snapshot)) {
                throw new IllegalStateException("Active shielder chunk index is missing its snapshot");
            }
            if (snapshots.isEmpty()) {
                dimChunks.remove(chunkCoord);
            }
        }
        if (dimChunks.isEmpty()) {
            activeChunkShielders.remove(dimId);
        }
    }

    private void notifyActiveCoverageChanged(String dimId,
                                             ActiveShielderSnapshot previousSnapshot,
                                             ActiveShielderSnapshot currentSnapshot) {
        LongSet previousPositions = snapshotCoveredNodePositions(dimId, previousSnapshot);
        LongSet currentPositions = snapshotCoveredNodePositions(dimId, currentSnapshot);
        LongSet added = difference(currentPositions, previousPositions);
        LongSet removed = difference(previousPositions, currentPositions);
        MachineBindingIndex.INSTANCE.onShielderCoverageChanged(dimId.hashCode(), added, removed);
    }

    private static LongSet snapshotCoveredNodePositions(String dimId, ActiveShielderSnapshot snapshot) {
        LongSet positions = new LongOpenHashSet();
        if (snapshot == null) {
            return positions;
        }
        for (INode node : NetworkManager.INSTANCE.getActiveNodes()) {
            if (dimId.equals(node.getDimensionId()) && snapshot.contains(node.getPos())) {
                positions.add(node.getPos().asLong());
            }
        }
        return positions;
    }

    private static LongSet difference(LongSet first, LongSet second) {
        LongSet result = new LongOpenHashSet();
        for (long position : first) {
            if (!second.contains(position)) {
                result.add(position);
            }
        }
        return result;
    }

    public boolean isBlockedByShielder(String dimId, BlockPos tePos) {
        var chunkMap = activeChunkShielders.get(dimId);
        if (chunkMap == null || chunkMap.isEmpty()) {
            return false;
        }
        long chunkCoord = Functions.mergeChunkCoords(tePos.getX() >> 4, tePos.getZ() >> 4);
        ObjectArrayList<ActiveShielderSnapshot> candidates = chunkMap.get(chunkCoord);
        if (candidates == null || candidates.isEmpty()) {
            return false;
        }
        for (int index = 0, count = candidates.size(); index < count; index++) {
            if (candidates.get(index).contains(tePos)) return true;
        }
        return false;
    }

    public boolean hasActiveShielderInChunk(String dimId, long chunkCoord) {
        Long2ObjectMap<ObjectArrayList<ActiveShielderSnapshot>> dimChunks = activeChunkShielders.get(dimId);
        if (dimChunks == null) {
            return false;
        }
        ObjectArrayList<ActiveShielderSnapshot> snapshots = dimChunks.get(chunkCoord);
        return snapshots != null && !snapshots.isEmpty();
    }

    private record ActiveShielderSnapshot(int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                                          LongSet coveredChunks) {

        private boolean contains(BlockPos pos) {
                return minX <= pos.getX() && minY <= pos.getY() && minZ <= pos.getZ()
                    && maxX >= pos.getX() && maxY >= pos.getY() && maxZ >= pos.getZ();
            }
        }

    public boolean isBlockedByShielder(BlockPos tePos, ICirculationShielderBlockEntity[] shielders) {
        for (ICirculationShielderBlockEntity shielder : shielders) {
            if (!shielder.isActive()) continue;
            if (!shielder.checkScope(tePos)) continue;
            return true;
        }
        return false;
    }
}
