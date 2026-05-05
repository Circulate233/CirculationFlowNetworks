package com.circulation.circulation_networks.handlers;

import com.circulation.circulation_networks.api.API;
import com.circulation.circulation_networks.api.node.IEnergySupplyNode;
import com.circulation.circulation_networks.manager.EnergyMachineManager;
import com.circulation.circulation_networks.tiles.BlockEntityMultiblockShell;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class NodeRescanHandler {

    public static final NodeRescanHandler INSTANCE = new NodeRescanHandler();

    private NodeRescanHandler() {
    }

    private static BlockPos resolveTargetPos(Level world, BlockPos pos) {
        var blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof BlockEntityMultiblockShell shell && shell.canRedirect()) {
            return shell.getOriginPos();
        }
        return pos;
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        Player player = event.getEntity();
        if (!player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()) {
            return;
        }

        Level world = event.getLevel();
        if (world.isClientSide()) {
            return;
        }

        var node = API.getNodeAt(world, resolveTargetPos(world, event.getPos()));
        if (!(node instanceof IEnergySupplyNode energySupplyNode)) {
            return;
        }

        if (EnergyMachineManager.INSTANCE.rescanMachinesAroundNode(energySupplyNode) && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.translatable("message.circulation_networks.node_rescan_success"));
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
}
