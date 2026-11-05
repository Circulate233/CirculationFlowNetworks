package com.circulation.circulation_networks.handlers;

import com.circulation.circulation_networks.api.API;
import com.circulation.circulation_networks.blocks.MultiblockShellBlock;
import com.circulation.circulation_networks.blocks.nodes.BlockLongDistanceRelayNode;
import com.circulation.circulation_networks.network.nodes.LongDistanceRelayNode;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;


public final class LongDistanceRelayInteractionHandler {
    public static final LongDistanceRelayInteractionHandler INSTANCE = new LongDistanceRelayInteractionHandler();

    private LongDistanceRelayInteractionHandler() {
    }
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        var world = event.getLevel();
        var player = event.getEntity();
        var stack = player.getMainHandItem();
        if (!player.isShiftKeyDown() || !stack.isEmpty()) return;
        var origin = MultiblockShellBlock.resolveRedirectedPos(world, event.getPos());
        if (!(world.getBlockState(origin).getBlock() instanceof BlockLongDistanceRelayNode)) return;

        if (player instanceof ServerPlayer serverPlayer) {
            var node = API.getNodeAt(world, origin);
            String message = LongDistanceRelayInteraction.clear(player,
                node instanceof LongDistanceRelayNode relay ? relay : null);
            serverPlayer.sendSystemMessage(Component.translatable(message));
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
}
