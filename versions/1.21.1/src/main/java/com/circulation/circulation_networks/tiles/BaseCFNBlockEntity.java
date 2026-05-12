package com.circulation.circulation_networks.tiles;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class BaseCFNBlockEntity extends BlockEntity {

    public BaseCFNBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void serverUpdate(Level world, BlockPos pos, BlockState state, BaseCFNBlockEntity blockEntity) {

    }

    public void clientUpdate(Level world, BlockPos pos, BlockState state, BaseCFNBlockEntity blockEntity) {

    }
}