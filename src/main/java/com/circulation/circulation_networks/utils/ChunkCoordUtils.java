package com.circulation.circulation_networks.utils;


import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class ChunkCoordUtils {

    private ChunkCoordUtils() {
    }

    public static long mergeChunkCoords(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public static int getChunkX(BlockPos pos) {
        return pos.getX() >> 4;
    }

    public static int getChunkZ(BlockPos pos) {
        return pos.getZ() >> 4;
    }

    public static boolean isChunkLoaded(Level world, int chunkX, int chunkZ) {
        return world.getChunkSource().hasChunk(chunkX, chunkZ);
    }

    public static boolean isChunkLoaded(Level world, BlockPos pos) {
        return isChunkLoaded(world, getChunkX(pos), getChunkZ(pos));
    }

    public static long mergeChunkCoords(BlockPos pos) {
        return mergeChunkCoords(getChunkX(pos), getChunkZ(pos));
    }
}
