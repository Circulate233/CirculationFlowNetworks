package com.circulation.circulation_networks.utils;

import com.circulation.circulation_networks.blocks.MultiblockShellBlock;
import com.circulation.circulation_networks.registry.CFNBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import com.circulation.circulation_networks.tiles.BlockEntityMultiblockShell;
import net.minecraft.world.item.context.BlockPlaceContext;


public final class MultiblockStructure {
    private MultiblockStructure() {
    }
    public static boolean canReplace(Level world, BlockPos pos, BlockPlaceContext context) {
        if (world.isOutsideBuildHeight(pos)) return false;
        var state = world.getBlockState(pos);
        return state.getBlock() instanceof MultiblockShellBlock
            ? MultiblockShellBlock.canBeReplacedAt(world, pos) : state.canBeReplaced(context);
    }

    public static void place(Level world, BlockPos origin, BlockPos[] positions) {
        if (world.isClientSide()) return;
        for (BlockPos pos : positions) {
            world.setBlock(pos, CFNBlocks.blockMultiblockShell.defaultBlockState(), 3);
            if (world.getBlockEntity(pos) instanceof BlockEntityMultiblockShell shell) shell.setOriginPos(origin);
        }
    }

    public static void remove(Level world, BlockPos origin, BlockPos[] positions) {
        for (BlockPos pos : positions) {
            if (world.getBlockEntity(pos) instanceof BlockEntityMultiblockShell shell
                && shell.getOriginPos().equals(origin)) {
                shell.setOriginPos(pos);
                world.removeBlock(pos, false);
            }
        }
    }
}
