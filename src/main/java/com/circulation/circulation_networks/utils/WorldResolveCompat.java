package com.circulation.circulation_networks.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class WorldResolveCompat {

    private WorldResolveCompat() {
    }

    public static @Nullable MinecraftServer getCurrentServer() {
        return ServerLifecycleHooks.getCurrentServer();
    }

    public static @Nullable Level resolveWorld(String dimensionKey) {
        var server = getCurrentServer();
        if (server == null) {
            return null;
        }

        if (dimensionKey != null && !dimensionKey.isEmpty()) {
            return server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimensionKey)));
        }
        return null;
    }

    public static boolean isRegisteredDimension(String dimKey) {
        var server = getCurrentServer();
        if (server == null) {
            return false;
        }
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimKey))) != null;
    }

    public static boolean isClientWorld(@NotNull Level world) {
        return world.isClientSide();
    }

    public static boolean isServerWorld(@NotNull Level world) {
        return !isClientWorld(world);
    }

    public static List<? extends Player> getPlayers(@NotNull Level world) {
        return world.players();
    }

    public static String getDimensionId(@NotNull Level world) {
        return world.dimension().identifier().toString();
    }

    public static String getPlayerDimensionId(ServerPlayer player) {
        return player.level().dimension().identifier().toString();
    }

    public static String getPlayerDimensionId(Player player) {
        return player.level().dimension().identifier().toString();
    }

    public static String getBlockVisualId(Level world, BlockPos pos) {
        return ResourceIdCompat.getBlockId(world.getBlockState(pos).getBlock());
    }

    @Nullable
    public static BlockEntity getBlockEntity(Level world, BlockPos pos) {
        return world.getBlockEntity(pos);
    }

    public static void destroyBlock(Level world, BlockPos pos) {
        world.destroyBlock(pos, true, null);
    }

    public static double getPlayerDistanceSq(ServerPlayer player, BlockPos pos) {
        double dx = player.getX() - (pos.getX() + 0.5D);
        double dy = player.getY() - (pos.getY() + 1.25D);
        double dz = player.getZ() - (pos.getZ() + 0.5D);
        return dx * dx + dy * dy + dz * dz;
    }

    public static long getPackedPos(BlockEntity blockEntity) {
        return BlockPosCompat.toLong(blockEntity.getBlockPos());
    }

    public static List<ServerPlayer> getServerPlayers(MinecraftServer server) {
        return server.getPlayerList().getPlayers();
    }

    public static Collection<BlockEntity> getLoadedChunkBlockEntities(Level world, int chunkX, int chunkZ) {
        var chunk = world.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (chunk == null) {
            return Collections.emptyList();
        }
        return chunk.getBlockEntities().values();
    }

    public static boolean isChunkLoaded(Level world, int chunkX, int chunkZ) {
        return world.getChunkSource().hasChunk(chunkX, chunkZ);
    }

    public static Path getRootSavePath() {
        return Objects.requireNonNull(getCurrentServer()).getWorldPath(LevelResource.ROOT);
    }
}
