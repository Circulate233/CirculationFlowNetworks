package com.circulation.circulation_networks.events;

import lombok.Getter;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.Event;

@Getter
public class TileEntityLifeCycleEvent extends Event {

    private final World world;
    private final BlockPos pos;
    private final TileEntity tileEntity;

    public TileEntityLifeCycleEvent(World world, BlockPos pos, TileEntity tileEntity) {
        this.world = world;
        this.pos = pos;
        this.tileEntity = tileEntity;
    }

    public static class Validate extends TileEntityLifeCycleEvent {

        public Validate(World world, BlockPos pos, TileEntity tileEntity) {
            super(world, pos, tileEntity);
        }
    }

    public static class Invalidate extends TileEntityLifeCycleEvent {

        public Invalidate(World world, BlockPos pos, TileEntity tileEntity) {
            super(world, pos, tileEntity);
        }
    }
}