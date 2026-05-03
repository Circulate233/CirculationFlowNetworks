package com.circulation.circulation_networks.handlers;

import com.circulation.circulation_networks.api.INodeBlockEntity;
import com.circulation.circulation_networks.api.node.NodeContext;
import com.circulation.circulation_networks.manager.NetworkManager;
import com.circulation.circulation_networks.network.nodes.NodeFactory;
import com.github.bsideup.jabel.Desugar;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.jetbrains.annotations.Nullable;

public final class NodePlacementValidationHandler {

    public static final NodePlacementValidationHandler INSTANCE = new NodePlacementValidationHandler();

    private NodePlacementValidationHandler() {
    }

    @SubscribeEvent
    public void onBlockPlace(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != EnumHand.MAIN_HAND) {
            return;
        }
        World world = event.getWorld();
        if (world.isRemote) {
            return;
        }
        EntityPlayer player = event.getEntityPlayer();
        ItemStack stack = player.getHeldItem(event.getHand());
        if (!(stack.getItem() instanceof ItemBlock itemBlock)) {
            return;
        }
        PlacementData placement = resolvePlacement(world, event.getPos(), event.getFace(), event.getHitVec(), stack, itemBlock, player);
        if (placement == null) {
            return;
        }
        TileEntity blockEntity = null;
        if (placement.state.getBlock().hasTileEntity(placement.state)) {
            blockEntity = placement.state.getBlock().createTileEntity(world, placement.state);
        }
        if (!(blockEntity instanceof INodeBlockEntity<?> nodeBlockEntity)) {
            return;
        }
        var node = NodeFactory.createNode(nodeBlockEntity.getNodeType(), NodeContext.fromWorld(world, placement.pos));
        node.setActive(true);
        try {
            NetworkManager.AddNodeResult result = NetworkManager.INSTANCE.canAddNode(node, blockEntity);
            if (!result.isSuccess()) {
                if (result.getStatus() == NetworkManager.AddNodeResult.Status.HUB_CONFLICT) {
                    player.sendMessage(new TextComponentTranslation("message.circulation_networks.hub_conflict"));
                }
                syncPlayerInventory(player);
                event.setCancellationResult(EnumActionResult.FAIL);
                event.setCanceled(true);
            }
        } finally {
            node.setActive(false);
        }
    }

    private static @Nullable PlacementData resolvePlacement(World world, BlockPos clickedPos, EnumFacing face, Vec3d hitVec,
                                                            ItemStack stack, ItemBlock itemBlock, EntityPlayer player) {
        IBlockState clickedState = world.getBlockState(clickedPos);
        BlockPos placePos = clickedState.getBlock().isReplaceable(world, clickedPos) ? clickedPos : clickedPos.offset(face);
        if (!player.canPlayerEdit(placePos, face, stack) || !world.mayPlace(itemBlock.getBlock(), placePos, false, face, player)) {
            return null;
        }
        int meta = itemBlock.getMetadata(stack.getMetadata());
        float hitX = (float) (hitVec.x - clickedPos.getX());
        float hitY = (float) (hitVec.y - clickedPos.getY());
        float hitZ = (float) (hitVec.z - clickedPos.getZ());
        IBlockState state = itemBlock.getBlock().getStateForPlacement(world, placePos, face, hitX, hitY, hitZ, meta, player, EnumHand.MAIN_HAND);
        return state == null ? null : new PlacementData(placePos, state);
    }

    private static void syncPlayerInventory(EntityPlayer player) {
        if (player instanceof net.minecraft.entity.player.EntityPlayerMP serverPlayer) {
            serverPlayer.sendContainerToPlayer(serverPlayer.openContainer);
        }
    }

    @Desugar
    private record PlacementData(BlockPos pos, IBlockState state) {
    }
}
