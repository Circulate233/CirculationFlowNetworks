package com.circulation.circulation_networks.handlers;

import com.circulation.circulation_networks.api.API;
import com.circulation.circulation_networks.blocks.MultiblockShellBlock;
import com.circulation.circulation_networks.blocks.nodes.BlockLongDistanceRelayNode;
import com.circulation.circulation_networks.network.nodes.LongDistanceRelayNode;
//? if <1.20 {
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
//?} else if <1.21 {
/*import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
*///?} else {
/*import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
*///?}

/** Clears links on an empty-hand sneak click; tools retain their normal item interaction. */
public final class LongDistanceRelayInteractionHandler {
    public static final LongDistanceRelayInteractionHandler INSTANCE = new LongDistanceRelayInteractionHandler();

    private LongDistanceRelayInteractionHandler() {
    }

    //? if <1.20 {
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != EnumHand.MAIN_HAND) return;
        var world = event.getWorld();
        var player = event.getEntityPlayer();
        var stack = player.getHeldItemMainhand();
        if (!player.isSneaking() || !stack.isEmpty()) return;
        var origin = MultiblockShellBlock.resolveRedirectedPos(world, event.getPos());
        if (!(world.getBlockState(origin).getBlock() instanceof BlockLongDistanceRelayNode)) return;

        if (!world.isRemote) {
            var node = API.getNodeAt(world, origin);
            String message = LongDistanceRelayInteraction.clear(player,
                node instanceof LongDistanceRelayNode relay ? relay : null);
            player.sendMessage(new TextComponentTranslation(message));
        }
        event.setCancellationResult(EnumActionResult.SUCCESS);
        event.setCanceled(true);
    }
    //?} else {
    /*@SubscribeEvent(priority = EventPriority.HIGH)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        var world = event.getLevel();
        var player = event.getEntity();
        var stack = player.getMainHandItem();
        if (!player.isShiftKeyDown() || !stack.isEmpty()) return;
        var origin = MultiblockShellBlock.resolveRedirectedPos(world, event.getPos());
        if (!(world.getBlockState(origin).getBlock() instanceof BlockLongDistanceRelayNode)) return;

        if (!world.isClientSide()) {
            var node = API.getNodeAt(world, origin);
            String message = LongDistanceRelayInteraction.clear(player,
                node instanceof LongDistanceRelayNode relay ? relay : null);
            player.displayClientMessage(Component.translatable(message), false);
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
    *///?}
}
