package com.circulation.circulation_networks.utils;

import com.circulation.circulation_networks.api.NodeCreator;
import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.api.node.NodeContext;
import com.circulation.circulation_networks.api.node.NodeType;
import com.circulation.circulation_networks.registry.NodeTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public final class Functions {

    @SuppressWarnings("unchecked")
    @NotNull
    public static <N extends INode> N createNode(@NotNull NodeType<? extends N> nodeType, @NotNull NodeContext context) {
        NodeCreator creator = NodeTypes.getCreator(nodeType.id());
        if (creator == null) {
            throw new IllegalArgumentException("No creator registered for node type: " + nodeType.id());
        }
        return (N) creator.apply(context);
    }

    @NotNull
    public static CompoundTag getOrCreateTagCompound(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    public static void saveTagCompound(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
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
        return WorldResolveCompat.isChunkLoaded(world, chunkX, chunkZ);
    }

    public static boolean isChunkLoaded(Level world, BlockPos pos) {
        return isChunkLoaded(world, getChunkX(pos), getChunkZ(pos));
    }

    public static long mergeChunkCoords(BlockPos pos) {
        return mergeChunkCoords(getChunkX(pos), getChunkZ(pos));
    }
}
