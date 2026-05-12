package com.circulation.circulation_networks.blocks;

import com.circulation.circulation_networks.tiles.BlockEntityCirculationShielder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public final class BlockCirculationShielder extends BaseTickBlock {

    public BlockCirculationShielder(ResourceKey<Block> id) {
        super(metalPropertiesNoOcclusion().setId(id));
    }

    @Override
    public boolean hasGui() {
        return true;
    }

    @NotNull
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new BlockEntityCirculationShielder(pos, state);
    }

    @Override
    public boolean isCollisionShapeFullBlock(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return false;
    }
}
