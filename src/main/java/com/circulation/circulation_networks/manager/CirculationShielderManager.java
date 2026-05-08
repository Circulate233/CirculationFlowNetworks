package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.api.ICirculationShielderBlockEntity;
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
//~ mc_imports
import net.minecraft.util.math.BlockPos;

public final class CirculationShielderManager {

    public static final CirculationShielderManager INSTANCE = new CirculationShielderManager();

    private final Int2ObjectMap<ReferenceSet<ICirculationShielderBlockEntity>> dimShielders = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<ICirculationShielderBlockEntity[]> idimShielders = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<Long2ObjectMap<ReferenceSet<ICirculationShielderBlockEntity>>> chunkShielders = new Int2ObjectOpenHashMap<>();
    private final Reference2ObjectMap<ICirculationShielderBlockEntity, LongSet> shielderCoveredChunks = new Reference2ObjectOpenHashMap<>();
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
        }
    }

    public void unregister(ICirculationShielderBlockEntity shielder, int dimId) {
        if (shielder == null) return;

        var shielders = dimShielders.get(dimId);
        if (shielders == null) return;
        if (!shielders.remove(shielder)) return;
        removeShielderIndex(shielder, dimId);
        if (shielders.isEmpty()) {
            dimShielders.remove(dimId);
            chunkShielders.remove(dimId);
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

    private void refreshDimCache(int dimId, ReferenceSet<ICirculationShielderBlockEntity> shielders) {
        if (shielders.isEmpty()) {
            idimShielders.remove(dimId);
            return;
        }
        idimShielders.put(dimId, shielders.toArray(EMPTY));
    }

    public boolean isBlockedByShielder(int dimId, BlockPos tePos) {
        Long2ObjectMap<ReferenceSet<ICirculationShielderBlockEntity>> dimChunks = chunkShielders.get(dimId);
        if (dimChunks == null) return false;

        ReferenceSet<ICirculationShielderBlockEntity> shielders = dimChunks.get(ChunkCoordUtils.mergeChunkCoords(tePos));
        if (shielders == null || shielders == dimChunks.defaultReturnValue() || shielders.isEmpty()) return false;

        for (ICirculationShielderBlockEntity shielder : shielders) {
            if (!shielder.isActive()) continue;
            if (shielder.checkScope(tePos)) return true;
        }
        return false;
    }
}
