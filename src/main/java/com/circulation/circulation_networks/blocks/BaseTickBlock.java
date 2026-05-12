package com.circulation.circulation_networks.blocks;

import com.circulation.circulation_networks.tiles.BaseCFNBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class BaseTickBlock extends BaseBlock {

    protected BaseTickBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level p_153212_, @NotNull BlockState p_153213_, @NotNull BlockEntityType<T> p_153214_) {
        return (world, pos, c, te) -> {
            if (te instanceof BaseCFNBlockEntity cfnBlockEntity) {
                if (world.isClientSide()) {
                    cfnBlockEntity.clientUpdate(world, pos, c, cfnBlockEntity);
                } else {
                    cfnBlockEntity.serverUpdate(world, pos, c, cfnBlockEntity);
                }
            }
        };
    }
}