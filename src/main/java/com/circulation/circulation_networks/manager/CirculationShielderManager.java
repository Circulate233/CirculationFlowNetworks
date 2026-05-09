package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.api.ICirculationShielderBlockEntity;
import com.circulation.circulation_networks.utils.Functions;
import com.circulation.circulation_networks.utils.WorldResolveCompat;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
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
    private final Reference2ObjectMap<ICirculationShielderBlockEntity, LongSet> shielderCoveredChunks = new Reference2ObjectOpenHashMap<>();

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
        set.add(shielder);

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
    }

    public void unregister(ICirculationShielderBlockEntity shielder, String dimId) {
        if (shielder == null) return;

        var shielders = dimShielders.get(dimId);
        if (shielders != null) {
            shielders.remove(shielder);
            if (shielders.isEmpty()) {
                dimShielders.remove(dimId);
            }
        }

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

    public boolean isBlockedByShielder(String dimId, BlockPos tePos) {
        var chunkMap = dimChunkShielders.get(dimId);
        if (chunkMap == null || chunkMap.isEmpty()) {
            return false;
        }
        long chunkCoord = Functions.mergeChunkCoords(tePos.getX() >> 4, tePos.getZ() >> 4);
        ReferenceSet<ICirculationShielderBlockEntity> candidates = chunkMap.get(chunkCoord);
        if (candidates == null || candidates.isEmpty()) {
            return false;
        }
        for (ICirculationShielderBlockEntity shielder : candidates) {
            if (!shielder.isActive()) {
                continue;
            }
            if (!shielder.checkScope(tePos)) {
                continue;
            }
            return true;
        }
        return false;
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
