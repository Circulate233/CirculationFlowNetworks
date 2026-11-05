package com.circulation.circulation_networks.utils;

import com.circulation.circulation_networks.blocks.MultiblockShellBlock;
import com.circulation.circulation_networks.registry.CFNBlocks;
//~ mc_imports
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
//? if <1.20 {
import com.circulation.circulation_networks.tiles.TileEntityMultiblockShell;
//?} else {
/*import com.circulation.circulation_networks.tiles.MultiblockShellBlockEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
*///?}

/** Shared shell placement/removal for the hub and tall nodes. */
public final class MultiblockStructure {
    private MultiblockStructure() {
    }

    //? if <1.20 {
    public static boolean canReplace(World world, BlockPos pos) {
        if (pos.getY() < 0 || pos.getY() >= world.getHeight()) return false;
        var state = world.getBlockState(pos);
        return state.getBlock() instanceof MultiblockShellBlock
            ? MultiblockShellBlock.canBeReplacedAt(world, pos) : state.getBlock().isReplaceable(world, pos);
    }

    public static void place(World world, BlockPos origin, BlockPos[] positions) {
        if (world.isRemote) return;
        for (BlockPos pos : positions) {
            world.setBlockState(pos, CFNBlocks.blockMultiblockShell.getDefaultState(), 3);
            if (world.getTileEntity(pos) instanceof TileEntityMultiblockShell shell) shell.setOriginPos(origin);
        }
    }

    public static void remove(World world, BlockPos origin, BlockPos[] positions) {
        for (BlockPos pos : positions) {
            if (world.getTileEntity(pos) instanceof TileEntityMultiblockShell shell
                && shell.getOriginPos().equals(origin)) {
                // Detach before removal so the shell does not recursively destroy its origin.
                shell.setOriginPos(pos);
                world.setBlockToAir(pos);
            }
        }
    }
    //?} else {
    /*public static boolean canReplace(Level world, BlockPos pos, BlockPlaceContext context) {
        if (world.isOutsideBuildHeight(pos)) return false;
        var state = world.getBlockState(pos);
        return state.getBlock() instanceof MultiblockShellBlock
            ? MultiblockShellBlock.canBeReplacedAt(world, pos) : state.canBeReplaced(context);
    }

    public static void place(Level world, BlockPos origin, BlockPos[] positions) {
        if (world.isClientSide()) return;
        for (BlockPos pos : positions) {
            world.setBlock(pos, CFNBlocks.blockMultiblockShell.defaultBlockState(), 3);
            if (world.getBlockEntity(pos) instanceof MultiblockShellBlockEntity shell) shell.setOriginPos(origin);
        }
    }

    public static void remove(Level world, BlockPos origin, BlockPos[] positions) {
        for (BlockPos pos : positions) {
            if (world.getBlockEntity(pos) instanceof MultiblockShellBlockEntity shell
                && shell.getOriginPos().equals(origin)) {
                shell.setOriginPos(pos);
                world.removeBlock(pos, false);
            }
        }
    }
    *///?}
}
