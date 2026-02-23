package com.circulation.circulation_networks.blocks.machines;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.api.IMachineNodeTileEntity;
import com.circulation.circulation_networks.blocks.BaseBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;

public abstract class BaseMachineNodeBlock extends BaseBlock {

    protected Class<? extends IMachineNodeTileEntity> nodeTileClass;

    protected BaseMachineNodeBlock(String name) {
        super(name);
    }

    protected final <T extends TileEntity & IMachineNodeTileEntity> void setNodeTileClass(Class<T> nodeTileClass) {
        this.nodeTileClass = nodeTileClass;
        TileEntity.register(Objects.requireNonNull(this.getRegistryName()).getPath(), nodeTileClass);
    }

    public abstract boolean hasGui();

    @Override
    public boolean onBlockActivated(@NotNull World worldIn, @NotNull BlockPos pos, @NotNull IBlockState state, @NotNull EntityPlayer playerIn, @NotNull EnumHand hand, @NotNull EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!worldIn.isRemote && hasGui()) {
            CirculationFlowNetworks.openGui(playerIn, worldIn, pos.getX(), pos.getY(), pos.getZ());
            return true;
        }
        return super.onBlockActivated(worldIn, pos, state, playerIn, hand, facing, hitX, hitY, hitZ);
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
