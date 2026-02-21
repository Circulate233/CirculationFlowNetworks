package com.circulation.circulation_networks.blocks;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
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
    }

    public boolean hasTileEntity(@NotNull IBlockState state) {
        return false;
    }

    @Override
    public @Nullable TileEntity createTileEntity(@NotNull World world, @NotNull IBlockState state) {
        return null;
    }

    @Override
    public @Nullable TileEntity createNewTileEntity(@NotNull World world, int meta) {
        return null;
    }

}