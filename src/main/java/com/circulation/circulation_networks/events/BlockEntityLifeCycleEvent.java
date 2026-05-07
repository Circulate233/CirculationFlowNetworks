package com.circulation.circulation_networks.events;

//~ mc_imports

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
//? if <1.20 {
import net.minecraftforge.fml.common.eventhandler.Event;
//?} else if <1.21 {
/*import net.minecraftforge.fml.common.eventhandler.Event;
 *///?} else {
/*import net.neoforged.bus.api.Event;
 *///?}

// Shared lifecycle event for block entities across supported loaders and versions.
public class BlockEntityLifeCycleEvent extends Event {

    //~ if >=1.20 'World ' -> 'Level ' {
    //~ if >=1.20 'TileEntity ' -> 'BlockEntity ' {
    private final World world;
    private final BlockPos pos;
    private final TileEntity blockEntity;

    public BlockEntityLifeCycleEvent(World world, BlockPos pos, TileEntity blockEntity) {
        this.world = world;
        this.pos = pos;
        this.blockEntity = blockEntity;
    }

    public World getWorld() {
        return world;
    }

    public BlockPos getPos() {
        return pos;
    }

    public TileEntity getBlockEntity() {
        return blockEntity;
    }

    public static class Validate extends BlockEntityLifeCycleEvent {

        public Validate(World world, BlockPos pos, TileEntity blockEntity) {
            super(world, pos, blockEntity);
        }
    }

    public static class Invalidate extends BlockEntityLifeCycleEvent {

        public Invalidate(World world, BlockPos pos, TileEntity blockEntity) {
            super(world, pos, blockEntity);
        }
    }
    //~}
    //~}
}
