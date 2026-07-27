package com.circulation.circulation_networks.items;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.api.API;
import com.circulation.circulation_networks.api.CFNBlockEntityEx;
import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.IGrid;
import com.circulation.circulation_networks.api.node.IChargingNode;
import com.circulation.circulation_networks.api.node.IEnergySupplyNode;
import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.container.ContainerMachinePriority;
import com.circulation.circulation_networks.items.CirculationConfiguratorModeModel.ConfigurationMode;
import com.circulation.circulation_networks.items.CirculationConfiguratorModeModel.ToolFunction;
import com.circulation.circulation_networks.manager.EnergyTypeOverrideManager;
import com.circulation.circulation_networks.manager.PocketNodeManager;
import com.circulation.circulation_networks.packets.ConfigOverrideRendering;
import com.circulation.circulation_networks.packets.ConfiguratorInteractionReport;
import com.circulation.circulation_networks.packets.NodeNetworkRendering;
import com.circulation.circulation_networks.packets.SpoceRendering;
import com.circulation.circulation_networks.packets.ToggleItemFunctionMessage;
import com.circulation.circulation_networks.registry.CFNItems;
import com.circulation.circulation_networks.registry.RegistryEnergyHandler;
import com.circulation.circulation_networks.tiles.BlockEntityMultiblockShell;
import com.circulation.circulation_networks.tooltip.LocalizedComponent;
import com.circulation.circulation_networks.utils.FormatNumberUtils;
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
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ItemCirculationConfigurator extends BaseItem {

    public ItemCirculationConfigurator(Properties properties) {
        super(properties);
    }

    public static void sendModeMessage(ServerPlayer player, CirculationConfiguratorSelection selection) {
        Component mode = Component.translatable(selection.modeLangKey()).withStyle(ChatFormatting.GOLD);
        if (!selection.function().hasSubModes()) {
            player.sendOverlayMessage(mode);
            return;
        }
        player.sendOverlayMessage(
            Component.translatable(
                selection.modeDisplayKey(),
                mode,
                Component.translatable(selection.subModeLangKey()).withStyle(ChatFormatting.BLUE)
            )
        );
    }

    private static void sendInteractionMessage(ServerPlayer player, String messageKey, Object... args) {
        player.sendSystemMessage(Component.translatable(messageKey, args));
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

    private static String formatEnergy(EnergyAmount amount) {
        RegistryEnergyHandler.Pair unit = RegistryEnergyHandler.getPair(0);
        if (!amount.isZero() && unit.multiplying() != 0.0D) {
            amount.divide(unit.multiplying());
        }
        return FormatNumberUtils.formatNumber(amount) + " " + unit.unit();
    }

    private static boolean isChunkResident(Level world, BlockPos pos) {
        return world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
    }

    private static @Nullable BlockPos resolveResidentOriginPos(Level world, BlockPos pos) {
        if (!isChunkResident(world, pos)) {
            return null;
        }
        var te = world.getBlockEntity(pos);
        if (te instanceof BlockEntityMultiblockShell shell && shell.canRedirect()) {
            BlockPos origin = shell.getOriginPos();
            return isChunkResident(world, origin) ? origin : null;
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
        BlockPos resolved = resolveResidentOriginPos(context.getLevel(), context.getClickedPos());
        if (resolved == null) {
            return InteractionResult.PASS;
        }
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
            if (player == null) {
                return InteractionResult.PASS;
            }
            CirculationConfiguratorSelection selection = CirculationConfiguratorSelection.fromStack(context.getItemInHand());
            if (player.isShiftKeyDown() && !selection.function().hasSubModes()) {
                BlockPos target = resolveResidentOriginPos(context.getLevel(), context.getClickedPos());
                return target != null && (API.getNodeAt(context.getLevel(), target) != null
                    || context.getLevel().getBlockEntity(target) != null)
                    ? InteractionResult.SUCCESS : InteractionResult.PASS;
            }
            return !player.isShiftKeyDown() && isChunkResident(context.getLevel(), context.getClickedPos())
                && API.getNodeAt(context.getLevel(), context.getClickedPos()) != null
                ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer p)) {
            return InteractionResult.PASS;
        }
        if (!p.isShiftKeyDown() && PocketNodeManager.INSTANCE.removePocketNode(context.getLevel(), context.getClickedPos(), true)) {
            return InteractionResult.SUCCESS;
        }

        BlockPos target = resolveResidentOriginPos(context.getLevel(), context.getClickedPos());
        if (target == null) {
            return InteractionResult.PASS;
        }
        ItemStack stack = context.getItemInHand();
        CirculationConfiguratorSelection selection = CirculationConfiguratorSelection.fromStack(stack);
        return switch (selection.function()) {
            case INSPECTION -> executeInspection(p, context.getLevel(), target, selection.subMode());
            case CONFIGURATION -> executeConfiguration(p, context.getLevel(), target, selection.subMode());
            case PRIORITY -> executePriority(p, context.getLevel(), target, context.getHand());
            case INTERACTION -> executeInteraction(p, context.getLevel(), target);
        };
    }

    private InteractionResult executePriority(ServerPlayer player, Level world, BlockPos pos, InteractionHand hand) {
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        ContainerMachinePriority.open(player, world, pos, hand);
        return InteractionResult.SUCCESS;
    }

    private InteractionResult executeInteraction(ServerPlayer player, Level world, BlockPos pos) {
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        return queueInteractionReport(player, world, pos) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    private boolean queueInteractionReport(ServerPlayer player, Level world, BlockPos pos) {
        if (!isChunkResident(world, pos)) {
            return false;
        }
        INode node = API.getNodeAt(world, pos);
        var nativeBlockEntity = world.getBlockEntity(pos);
        boolean registeredMachine = nativeBlockEntity != null
            && API.getMachineInteraction(CFNBlockEntityEx.cfn_cast(nativeBlockEntity)) != null;
        if (!registeredMachine && node == null) {
            sendInteractionMessage(player,
                "item.circulation_networks.circulation_configurator.interaction.error.invalid_target");
            return false;
        }
        API.submitMachineInteractionQuery(() -> sendInteractionReport(player, world, pos));
        return true;
    }

    private boolean sendInteractionReport(ServerPlayer player, Level world, BlockPos pos) {
        if (!isChunkResident(world, pos)) {
            return false;
        }
        INode node = API.getNodeAt(world, pos);
        var nativeBlockEntity = world.getBlockEntity(pos);
        var interaction = nativeBlockEntity != null
            ? API.getMachineInteraction(CFNBlockEntityEx.cfn_cast(nativeBlockEntity)) : null;
        boolean hasMachine = interaction != null;
        String machineInput = "";
        String machineOutput = "";
        if (hasMachine) {
            try (ConfiguratorInteractionQuery.MachineSnapshot snapshot =
                     ConfiguratorInteractionQuery.snapshotMachine(interaction)) {
                machineInput = formatEnergy(snapshot.input());
                machineOutput = formatEnergy(snapshot.output());
            }
        }
        boolean hasNetwork = false;
        long[] inputPositions = new long[0];
        String[] inputValues = new String[0];
        long[] outputPositions = new long[0];
        String[] outputValues = new String[0];
        if (node != null) {
            IGrid grid = node.getGrid();
            if (grid == null) {
                sendInteractionMessage(player,
                    "item.circulation_networks.circulation_configurator.interaction.error.no_grid");
            } else {
                hasNetwork = true;
                try (ConfiguratorInteractionQuery.GridSnapshot snapshot = ConfiguratorInteractionQuery.snapshotGrid(
                    grid, packedPosition -> isChunkResident(world,
                        ConfiguratorInteractionQuery.unpack(packedPosition)))) {
                    inputPositions = rankingPositions(snapshot.inputs());
                    inputValues = rankingValues(snapshot.inputs());
                    outputPositions = rankingPositions(snapshot.outputs());
                    outputValues = rankingValues(snapshot.outputs());
                }
            }
        }
        if (!hasMachine && !hasNetwork) {
            sendInteractionMessage(player,
                "item.circulation_networks.circulation_configurator.interaction.error.invalid_target");
            return false;
        }
        CirculationFlowNetworks.sendToPlayer(new ConfiguratorInteractionReport(
            hasMachine, packPos(pos), machineInput, machineOutput, hasNetwork,
            inputPositions, inputValues, outputPositions, outputValues
        ), player);
        return true;
    }

    private static long[] rankingPositions(List<ConfiguratorInteractionQuery.RankedInteraction> ranking) {
        long[] positions = new long[ranking.size()];
        for (int index = 0; index < ranking.size(); index++) {
            positions[index] = ranking.get(index).packedPosition();
        }
        return positions;
    }

    private static String[] rankingValues(List<ConfiguratorInteractionQuery.RankedInteraction> ranking) {
        String[] values = new String[ranking.size()];
        for (int index = 0; index < ranking.size(); index++) {
            values[index] = formatEnergy(ranking.get(index).amount());
        }
        return values;
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
