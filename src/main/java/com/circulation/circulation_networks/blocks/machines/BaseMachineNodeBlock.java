package com.circulation.circulation_networks.blocks.machines;

import com.circulation.circulation_networks.blocks.BaseBlock;
import com.circulation.circulation_networks.tiles.machines.BaseMachineNodeTileEntity;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;

public abstract class BaseMachineNodeBlock extends BaseBlock {

    protected Class<? extends BaseMachineNodeTileEntity> nodeTileClass;

    protected BaseMachineNodeBlock(String name) {
        super(name);
    }

    protected final <T extends BaseMachineNodeTileEntity> void setNodeTileClass(Class<T> nodeTileClass) {
        this.nodeTileClass = nodeTileClass;
    }

    public final boolean hasTileEntity(@NotNull IBlockState state) {
        return nodeTileClass != null;
    }

    @Override
    public final @Nullable BaseMachineNodeTileEntity createNewTileEntity(@Nullable World world, int meta) {
        if (nodeTileClass == null) {
            return null;
        } else {
            try {
                return nodeTileClass.getDeclaredConstructor().newInstance();
            } catch (InstantiationException | NoSuchMethodException | InvocationTargetException |
                     IllegalAccessException e) {
                return null;
            }
        }
    }

}
