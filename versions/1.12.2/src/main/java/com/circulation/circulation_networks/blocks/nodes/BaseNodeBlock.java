package com.circulation.circulation_networks.blocks.nodes;

import com.circulation.circulation_networks.blocks.BaseBlock;
import com.circulation.circulation_networks.tiles.nodes.BaseNodeTileEntity;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;

@SuppressWarnings("deprecation")
public abstract class BaseNodeBlock extends BaseBlock {
    protected Class<? extends BaseNodeTileEntity<?>> nodeTileClass;

    protected BaseNodeBlock(String name) {
        super(name);
    }

    protected BaseNodeBlock(String modid, String name) {
        super(modid, name);
    }

    @Override
    public boolean isOpaqueCube(@NotNull IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(@NotNull IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullBlock(@NotNull IBlockState state) {
        return false;
    }

    protected final <T extends BaseNodeTileEntity<?>> void setNodeTileClass(Class<T> nodeTileClass) {
        this.nodeTileClass = nodeTileClass;
        TileEntity.register(Objects.requireNonNull(getRegistryName()).toString(), nodeTileClass);
    }

    @Override
    public boolean hasGui() {
        return false;
    }

    public final boolean hasTileEntity(@NotNull IBlockState state) {
        return nodeTileClass != null;
    }

    @Override
    public final @Nullable BaseNodeTileEntity<?> createNewTileEntity(@Nullable World world, int meta) {
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

    @Override
    @SideOnly(Side.CLIENT)
    public boolean hasCustomBreakingProgress(@NotNull IBlockState state) {
        return true;
    }

}
