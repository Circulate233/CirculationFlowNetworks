package com.circulation.circulation_networks.handlers;

import com.circulation.circulation_networks.api.API;
import com.circulation.circulation_networks.api.node.IEnergySupplyNode;
import com.circulation.circulation_networks.manager.EnergyMachineManager;
import static com.circulation.circulation_networks.utils.SideCompat.isClientWorld;
//? if <1.20 {
import com.circulation.circulation_networks.tiles.TileEntityMultiblockShell;
//?} else {
/*import com.circulation.circulation_networks.tiles.MultiblockShellBlockEntity;
*///?}
//~ mc_imports
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
//? if <1.20 {
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
//?} else if <1.21 {
/*import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
*///?} else {
/*import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
*///?}

public final class NodeRescanHandler {

    public static final NodeRescanHandler INSTANCE = new NodeRescanHandler();

    private NodeRescanHandler() {
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!isMainHand(event)) {
            return;
        }

        var player = getPlayer(event);
        if (!isSneaking(player) || !isMainHandEmpty(player)) {
            return;
        }

        var world = getWorld(event);
        if (isClientWorld(world)) {
            return;
        }

        var node = API.getNodeAt(world, resolveTargetPos(world, getPos(event)));
        if (!(node instanceof IEnergySupplyNode energySupplyNode)) {
            return;
        }

        EnergyMachineManager.INSTANCE.rescanMachinesAroundNode(energySupplyNode);
        markHandled(event);
    }

    //? if <1.20 {
    private static EntityPlayer getPlayer(PlayerInteractEvent.RightClickBlock event) {
        return event.getEntityPlayer();
    }

    private static World getWorld(PlayerInteractEvent.RightClickBlock event) {
        return event.getWorld();
    }

    private static BlockPos getPos(PlayerInteractEvent.RightClickBlock event) {
        return event.getPos();
    }

    private static BlockPos resolveTargetPos(World world, BlockPos pos) {
        var blockEntity = world.getTileEntity(pos);
        if (blockEntity instanceof TileEntityMultiblockShell shell && shell.canRedirect()) {
            return shell.getOriginPos();
        }
        return pos;
    }

    private static boolean isMainHand(PlayerInteractEvent.RightClickBlock event) {
        return event.getHand() == EnumHand.MAIN_HAND;
    }

    private static boolean isSneaking(EntityPlayer player) {
        return player.isSneaking();
    }

    private static boolean isMainHandEmpty(EntityPlayer player) {
        return player.getHeldItemMainhand().isEmpty();
    }

    private static void markHandled(PlayerInteractEvent.RightClickBlock event) {
        event.setCancellationResult(EnumActionResult.SUCCESS);
        event.setCanceled(true);
    }
    //?} else {
    /*private static Player getPlayer(PlayerInteractEvent.RightClickBlock event) {
        return event.getEntity();
    }

    private static Level getWorld(PlayerInteractEvent.RightClickBlock event) {
        return event.getLevel();
    }

    private static BlockPos getPos(PlayerInteractEvent.RightClickBlock event) {
        return event.getPos();
    }

    private static BlockPos resolveTargetPos(Level world, BlockPos pos) {
        var blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof MultiblockShellBlockEntity shell && shell.canRedirect()) {
            return shell.getOriginPos();
        }
        return pos;
    }

    private static boolean isMainHand(PlayerInteractEvent.RightClickBlock event) {
        return event.getHand() == InteractionHand.MAIN_HAND;
    }

    private static boolean isSneaking(Player player) {
        return player.isShiftKeyDown();
    }

    private static boolean isMainHandEmpty(Player player) {
        return player.getMainHandItem().isEmpty();
    }

    private static void markHandled(PlayerInteractEvent.RightClickBlock event) {
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
    *///?}
}
