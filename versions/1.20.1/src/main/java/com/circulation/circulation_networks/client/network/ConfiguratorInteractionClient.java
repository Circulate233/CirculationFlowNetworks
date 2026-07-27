package com.circulation.circulation_networks.client.network;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.packets.ConfiguratorInteractionReport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class ConfiguratorInteractionClient {

    private ConfiguratorInteractionClient() {
    }

    public static void display(ConfiguratorInteractionReport message) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        Level level = minecraft.level;
        if (player == null || level == null) {
            return;
        }
        if (message.hasMachine()) {
            BlockPos pos = BlockPos.of(message.getMachinePosition());
            player.displayClientMessage(Component.translatable(
                "item.circulation_networks.circulation_configurator.interaction.machine.title",
                resolveName(player, level, pos), pos.getX(), pos.getY(), pos.getZ()
            ), false);
            player.displayClientMessage(Component.translatable(
                "item.circulation_networks.circulation_configurator.interaction.machine.input",
                message.getMachineInput()
            ), false);
            player.displayClientMessage(Component.translatable(
                "item.circulation_networks.circulation_configurator.interaction.machine.output",
                message.getMachineOutput()
            ), false);
        }
        if (message.hasNetwork()) {
            displayRanking(player, level,
                "item.circulation_networks.circulation_configurator.interaction.network.input_top",
                message, true);
            displayRanking(player, level,
                "item.circulation_networks.circulation_configurator.interaction.network.output_top",
                message, false);
        }
    }

    private static void displayRanking(LocalPlayer player, Level level, String titleKey,
                                       ConfiguratorInteractionReport message, boolean input) {
        player.displayClientMessage(Component.translatable(titleKey), false);
        int count = input ? message.getInputCount() : message.getOutputCount();
        if (count == 0) {
            player.displayClientMessage(Component.translatable(
                "item.circulation_networks.circulation_configurator.interaction.network.empty"
            ), false);
            return;
        }
        for (int index = 0; index < count; index++) {
            long position = input ? message.getInputPosition(index) : message.getOutputPosition(index);
            String value = input ? message.getInputValue(index) : message.getOutputValue(index);
            BlockPos pos = BlockPos.of(position);
            player.displayClientMessage(Component.translatable(
                "item.circulation_networks.circulation_configurator.interaction.network.entry",
                index + 1, resolveName(player, level, pos), pos.getX(), pos.getY(), pos.getZ(), value
            ), false);
        }
    }

    private static Component resolveName(LocalPlayer player, Level level, BlockPos pos) {
        if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) {
            return Component.literal(pos.toShortString());
        }
        BlockState state = level.getBlockState(pos);
        try {
            BlockHitResult target = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
            ItemStack picked = state.getBlock().getCloneItemStack(state, target, level, pos, player);
            if (!picked.isEmpty()) {
                return picked.getHoverName();
            }
        } catch (RuntimeException exception) {
            CirculationFlowNetworks.LOGGER.warn("Failed to resolve picked block name at {}", pos, exception);
        }
        return state.getBlock().getName();
    }
}
