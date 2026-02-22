package com.circulation.circulation_networks.blocks.nodes;

import com.circulation.circulation_networks.api.INodeTileEntity;
import com.circulation.circulation_networks.blocks.BaseBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;

public abstract class BaseNodeBlock extends BaseBlock {
    protected Class<? extends INodeTileEntity> nodeTileClass;

    protected BaseNodeBlock(String name) {
        super(name);
    }

    protected final <T extends TileEntity & INodeTileEntity> void setNodeTileClass(Class<T> nodeTileClass) {
        this.nodeTileClass = nodeTileClass;
        TileEntity.register(Objects.requireNonNull(this.getRegistryName()).getPath(), nodeTileClass);
    }

    public final boolean hasTileEntity(@NotNull IBlockState state) {
        return nodeTileClass != null;
    }

    @Override
    public final @Nullable TileEntity createTileEntity(@NotNull World world, @NotNull IBlockState state) {
        return createNewTileEntity(world, 0);
    }

    @Override
    public final @Nullable TileEntity createNewTileEntity(@NotNull World world, int meta) {
        if (nodeTileClass == null) {
            return null;
        } else {
            try {
                return (TileEntity) nodeTileClass.getDeclaredConstructor().newInstance();
            } catch (InstantiationException | NoSuchMethodException | InvocationTargetException |
                     IllegalAccessException e) {
                return null;
            }
        }
    }

}