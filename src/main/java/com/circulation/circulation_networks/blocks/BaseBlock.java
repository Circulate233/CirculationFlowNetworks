package com.circulation.circulation_networks.blocks;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.tiles.BaseTileEntity;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.circulation.circulation_networks.CirculationFlowNetworks.CREATIVE_TAB;

public abstract class BaseBlock extends Block implements ITileEntityProvider {

    protected BaseBlock(String name) {
        super(Material.IRON);
        this.setRegistryName(new ResourceLocation(CirculationFlowNetworks.MOD_ID, name));
        this.setTranslationKey(CirculationFlowNetworks.MOD_ID + "." + name);
        this.setCreativeTab(CREATIVE_TAB);
        var te = createNewTileEntity(null, 0);
        if (te == null) return;
        TileEntity.register(name, te.getClass());
    }

    public boolean hasTileEntity(@NotNull IBlockState state) {
        return false;
    }

    /**
     * @return 如果返回是，对应的TileEntity应该实现{@link BaseTileEntity#getContainer}与{@link BaseTileEntity#getGui}
     */
    public abstract boolean hasGui();

    @Override
    public boolean onBlockActivated(@NotNull World worldIn, @NotNull BlockPos pos, @NotNull IBlockState state, @NotNull EntityPlayer playerIn, @NotNull EnumHand hand, @NotNull EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!worldIn.isRemote && hasGui()) {
            CirculationFlowNetworks.openGui(playerIn, worldIn, pos.getX(), pos.getY(), pos.getZ());
            return true;
        }
        return super.onBlockActivated(worldIn, pos, state, playerIn, hand, facing, hitX, hitY, hitZ);
    }

    @Override
    public final @Nullable BaseTileEntity createTileEntity(@NotNull World world, @NotNull IBlockState state) {
        return createNewTileEntity(world, 0);
    }

    @Override
    public @Nullable BaseTileEntity createNewTileEntity(@Nullable World world, int meta) {
        return null;
    }

}