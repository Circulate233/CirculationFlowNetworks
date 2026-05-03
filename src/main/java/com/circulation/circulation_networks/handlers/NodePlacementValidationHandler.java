package com.circulation.circulation_networks.handlers;

import com.circulation.circulation_networks.api.INodeBlockEntity;
import com.circulation.circulation_networks.api.node.NodeContext;
import com.circulation.circulation_networks.manager.NetworkManager;
import com.circulation.circulation_networks.network.nodes.NodeFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

public final class NodePlacementValidationHandler {

    public static final NodePlacementValidationHandler INSTANCE = new NodePlacementValidationHandler();

    private NodePlacementValidationHandler() {
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Level level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }
        Player player = event.getEntity();
        ItemStack stack = player.getItemInHand(event.getHand());
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return;
        }
        PlacementData placement = resolvePlacement(player, event.getHand(), event.getPos(), event.getFace(), event.getHitVec(), blockItem);
        if (placement == null) {
            return;
        }
        @Nullable BlockEntity blockEntity = null;
        if (placement.state.getBlock() instanceof EntityBlock entityBlock) {
            blockEntity = entityBlock.newBlockEntity(placement.pos, placement.state);
        }
        if (!(blockEntity instanceof INodeBlockEntity nodeBlockEntity)) {
            return;
        }
        var node = NodeFactory.createNode(nodeBlockEntity.getNodeType(), NodeContext.fromWorld(level, placement.pos));
        node.setActive(true);
        try {
            NetworkManager.AddNodeResult result = NetworkManager.INSTANCE.canAddNode(node, blockEntity);
            if (!result.isSuccess()) {
                if (result.getStatus() == NetworkManager.AddNodeResult.Status.HUB_CONFLICT) {
                    player.sendSystemMessage(Component.translatable("message.circulation_networks.hub_conflict"));
                }
                syncPlayerInventory(player);
                event.setCancellationResult(InteractionResult.FAIL);
                event.setCanceled(true);
            }
        } finally {
            node.setActive(false);
        }
    }

    private static @Nullable PlacementData resolvePlacement(Player player, InteractionHand hand, BlockPos clickedPos,
                                                            net.minecraft.core.Direction face, BlockHitResult hitResult,
                                                            BlockItem blockItem) {
        BlockPlaceContext context = new BlockPlaceContext(
            new UseOnContext(player, hand, new BlockHitResult(hitResult.getLocation(), face, clickedPos, hitResult.isInside()))
        );
        BlockState state = blockItem.getBlock().getStateForPlacement(context);
        return state == null ? null : new PlacementData(context.getClickedPos(), state);
    }

    private static void syncPlayerInventory(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.containerMenu.broadcastFullState();
        }
    }

    private record PlacementData(BlockPos pos, BlockState state) {
    }
}
