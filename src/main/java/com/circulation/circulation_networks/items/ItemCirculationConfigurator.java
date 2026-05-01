package com.circulation.circulation_networks.items;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.api.API;
import com.circulation.circulation_networks.api.node.IChargingNode;
import com.circulation.circulation_networks.api.node.IEnergySupplyNode;
import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.items.CirculationConfiguratorModeModel.ConfigurationMode;
import com.circulation.circulation_networks.items.CirculationConfiguratorModeModel.ToolFunction;
import com.circulation.circulation_networks.manager.EnergyTypeOverrideManager;
import com.circulation.circulation_networks.manager.PocketNodeManager;
import com.circulation.circulation_networks.packets.ConfigOverrideRendering;
import com.circulation.circulation_networks.packets.NodeNetworkRendering;
import com.circulation.circulation_networks.packets.SpoceRendering;
import com.circulation.circulation_networks.packets.ToggleItemFunctionMessage;
import com.circulation.circulation_networks.registry.CFNItems;
import com.circulation.circulation_networks.registry.RegistryEnergyHandler;
import com.circulation.circulation_networks.tiles.BlockEntityMultiblockShell;
import com.circulation.circulation_networks.tooltip.LocalizedComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ItemCirculationConfigurator extends BaseItem {

    public ItemCirculationConfigurator(Properties properties) {
        super(properties);
    }

    public static void sendModeMessage(ServerPlayer player, CirculationConfiguratorSelection selection) {
        player.sendOverlayMessage(
            Component.translatable(
                selection.modeDisplayKey(),
                Component.translatable(selection.modeLangKey()).withStyle(ChatFormatting.GOLD),
                Component.translatable(selection.subModeLangKey()).withStyle(ChatFormatting.BLUE)
            )
        );
    }

    private static void sendFeedbackMessage(ServerPlayer player, String messageKey, String detailKey) {
        if (detailKey != null) {
            player.sendSystemMessage(Component.translatable(messageKey, Component.translatable(detailKey)));
        } else {
            player.sendSystemMessage(Component.translatable(messageKey));
        }
    }

    public static CirculationConfiguratorSelection toggleFunction(ItemStack stack, ServerPlayer player) {
        var toggleResult = CirculationConfiguratorState.toggleFunction(stack);
        var selection = CirculationConfiguratorSelection.fromStack(stack);
        if (toggleResult.currentFunction() == ToolFunction.CONFIGURATION) {
            ConfigOverrideRendering.sendFullSync(player);
        } else if (toggleResult.previousFunction() == ToolFunction.CONFIGURATION) {
            ConfigOverrideRendering.sendClear(player);
        }
        return selection;
    }

    private static String getDimensionId(Level world) {
        return world.dimension().identifier().toString();
    }

    private static long packPos(BlockPos pos) {
        return pos.asLong();
    }

    private static BlockPos resolveOriginPos(Level world, BlockPos pos) {
        var te = world.getBlockEntity(pos);
        if (te instanceof BlockEntityMultiblockShell shell && shell.canRedirect()) {
            return shell.getOriginPos();
        }
        return pos;
    }

    @Override
    public @NotNull InteractionResult onItemUseFirst(@NotNull ItemStack stack, @NotNull UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (context.getLevel().isClientSide()) {
            return API.getNodeAt(context.getLevel(), context.getClickedPos()) != null
                ? InteractionResult.SUCCESS
                : InteractionResult.PASS;
        }
        BlockPos resolved = resolveOriginPos(context.getLevel(), context.getClickedPos());
        if (!resolved.equals(context.getClickedPos())) {
            return InteractionResult.PASS;
        }
        return PocketNodeManager.INSTANCE.removePocketNode(context.getLevel(), context.getClickedPos(), true)
            ? InteractionResult.SUCCESS
            : InteractionResult.PASS;
    }

    @Override
    public @NotNull InteractionResult useOn(@NotNull UseOnContext context) {
        Player player = context.getPlayer();
        if (context.getLevel().isClientSide()) {
            return player != null && !player.isShiftKeyDown()
                && API.getNodeAt(context.getLevel(), context.getClickedPos()) != null
                ? InteractionResult.SUCCESS
                : InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer p)) {
            return InteractionResult.PASS;
        }
        if (!p.isShiftKeyDown() && PocketNodeManager.INSTANCE.removePocketNode(context.getLevel(), context.getClickedPos(), true)) {
            return InteractionResult.SUCCESS;
        }

        BlockPos target = resolveOriginPos(context.getLevel(), context.getClickedPos());
        ItemStack stack = context.getItemInHand();
        CirculationConfiguratorSelection selection = CirculationConfiguratorSelection.fromStack(stack);
        return switch (selection.function()) {
            case INSPECTION -> executeInspection(p, context.getLevel(), target, selection.subMode());
            case CONFIGURATION -> executeConfiguration(p, context.getLevel(), target, selection.subMode());
        };
    }

    @Override
    protected List<LocalizedComponent> buildTooltips(ItemStack stack) {
        List<LocalizedComponent> tips = CirculationConfiguratorSelection.fromStack(stack).tooltipLines();
        tips.addAll(super.buildTooltips(stack));
        return tips;
    }

    @Override
    public @NotNull InteractionResult use(@NotNull Level worldIn, @NotNull Player player, @NotNull InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() != CFNItems.circulationConfigurator) {
                return super.use(worldIn, player, hand);
            }
            HitResult ray = player.pick(5.0D, 1.0F, false);
            if (ray == null || ray.getType() == HitResult.Type.MISS) {
                if (worldIn.isClientSide()) {
                    CirculationFlowNetworks.sendToServer(new ToggleItemFunctionMessage());
                }
                return InteractionResult.SUCCESS;
            }
        }
        return super.use(worldIn, player, hand);
    }

    private InteractionResult executeInspection(ServerPlayer player, Level world, BlockPos pos, int subMode) {
        INode node = API.getNodeAt(world, pos);
        if (node == null) {
            return InteractionResult.PASS;
        }

        double energyScope = 0;
        double chargingScope = 0;
        if (node instanceof IEnergySupplyNode energySupplyNode) {
            energyScope = energySupplyNode.getEnergyScope();
        }
        if (node instanceof IChargingNode chargingNode) {
            chargingScope = chargingNode.getChargingScope();
        }

        CirculationFlowNetworks.sendToPlayer(
            new SpoceRendering(node.getPos(), node.getLinkScope(), energyScope, chargingScope),
            player
        );
        CirculationFlowNetworks.sendToPlayer(new NodeNetworkRendering(player, node.getGrid()), player);
        NodeNetworkRendering.addPlayer(node.getGrid(), player);
        return InteractionResult.SUCCESS;
    }

    private InteractionResult executeConfiguration(ServerPlayer player, Level world, BlockPos pos, int subMode) {
        var manager = EnergyTypeOverrideManager.get();
        if (manager == null) {
            return InteractionResult.FAIL;
        }

        INode node = API.getNodeAt(world, pos);
        var blockEntity = world.getBlockEntity(pos);
        if (node != null) {
            sendFeedbackMessage(player, "item.circulation_networks.circulation_configurator.config.node_blocked", null);
            return InteractionResult.FAIL;
        }
        if (blockEntity == null) {
            return InteractionResult.PASS;
        }
        if (RegistryEnergyHandler.isBlack(blockEntity) || !RegistryEnergyHandler.isEnergyTileEntity(blockEntity)) {
            sendFeedbackMessage(player, "item.circulation_networks.circulation_configurator.config.invalid_target", null);
            return InteractionResult.FAIL;
        }

        ConfigurationMode mode = ConfigurationMode.fromID(subMode);
        String dim = getDimensionId(world);
        if (mode == ConfigurationMode.CLEAR) {
            manager.clearOverride(dim, pos);
            ConfigOverrideRendering.sendRemove(player, packPos(pos));
            sendFeedbackMessage(player, "item.circulation_networks.circulation_configurator.config.cleared", null);
            return InteractionResult.SUCCESS;
        }

        var energyType = mode.getEnergyType();
        manager.setOverride(dim, pos, energyType);
        ConfigOverrideRendering.sendAdd(player, packPos(pos), energyType);
        sendFeedbackMessage(player, "item.circulation_networks.circulation_configurator.config.set", mode.getLangKey());
        return InteractionResult.SUCCESS;
    }
}
