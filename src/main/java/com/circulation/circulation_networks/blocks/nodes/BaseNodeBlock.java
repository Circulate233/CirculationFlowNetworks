package com.circulation.circulation_networks.blocks.nodes;

import com.circulation.circulation_networks.blocks.BaseBlock;
import com.circulation.circulation_networks.tiles.nodes.BaseNodeTileEntity;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;

public abstract class BaseNodeBlock extends BaseBlock {
    protected Class<? extends BaseNodeTileEntity> nodeTileClass;

    protected BaseNodeBlock(String name) {
        super(name);
    }

    protected final <T extends BaseNodeTileEntity> void setNodeTileClass(Class<T> nodeTileClass) {
        this.nodeTileClass = nodeTileClass;
    }

    @Override
    public boolean hasGui() {
        return false;
    }

    public final boolean hasTileEntity(@NotNull IBlockState state) {
        return nodeTileClass != null;
    }

    @Override
    public final @Nullable BaseNodeTileEntity createNewTileEntity(@Nullable World world, int meta) {
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