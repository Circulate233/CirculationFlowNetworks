package com.circulation.circulation_networks.blocks.nodes;

import com.circulation.circulation_networks.handlers.LongDistanceRelayInteraction;
import com.circulation.circulation_networks.registry.CFNItems;
import com.circulation.circulation_networks.tiles.nodes.TileEntityLongDistanceRelayNode;
import com.circulation.circulation_networks.utils.MultiblockStructure;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("deprecation")
public final class BlockLongDistanceRelayNode extends PedestalRequiredNodeBlock {
    public BlockLongDistanceRelayNode() {
        super("long_distance_relay_node");
        setNodeTileClass(TileEntityLongDistanceRelayNode.class);
    }

    private static BlockPos[] shells(BlockPos origin) {
        return new BlockPos[] {origin.up(), origin.up(2)};
    }

    @Override
    public boolean canPlaceBlockAt(@NotNull World world, @NotNull BlockPos pos) {
        if (!super.canPlaceBlockAt(world, pos) || !MultiblockStructure.canReplace(world, pos)) return false;
        for (BlockPos shell : shells(pos)) if (!MultiblockStructure.canReplace(world, shell)) return false;
        return true;
    }

    @Override
    public void onBlockPlacedBy(@NotNull World world, @NotNull BlockPos pos, @NotNull IBlockState state,
                                @NotNull EntityLivingBase placer, @NotNull ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        if (world.getBlockState(pos).getBlock() == this) MultiblockStructure.place(world, pos, shells(pos));
    }

    @Override
    public void breakBlock(@NotNull World world, @NotNull BlockPos pos, @NotNull IBlockState state) {
        MultiblockStructure.remove(world, pos, shells(pos));
        super.breakBlock(world, pos, state);
    }

    @Override
    public @NotNull EnumPushReaction getPushReaction(@NotNull IBlockState state) {
        return EnumPushReaction.BLOCK;
    }

    @Override
    public @NotNull EnumBlockRenderType getRenderType(@NotNull IBlockState state) {
        return EnumBlockRenderType.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public boolean onBlockActivated(@NotNull World world, @NotNull BlockPos pos, @NotNull IBlockState state,
                                     @NotNull EntityPlayer player, @NotNull EnumHand hand, @NotNull EnumFacing face,
                                     float hitX, float hitY, float hitZ) {
        if (hand != EnumHand.MAIN_HAND || player.isSneaking()) return false;
        if (player.getHeldItem(hand).getItem() == CFNItems.circulationConfigurator) {
            return super.onBlockActivated(world, pos, state, player, hand, face, hitX, hitY, hitZ);
        }
        if (!world.isRemote && world.getTileEntity(pos) instanceof TileEntityLongDistanceRelayNode relay) {
            relay.syncNodeAfterNetworkInit();
            player.sendMessage(new TextComponentTranslation(LongDistanceRelayInteraction.select(player, relay.getNode())));
        }
        return true;
    }
}
