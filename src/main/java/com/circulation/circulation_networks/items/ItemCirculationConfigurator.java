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
import com.circulation.circulation_networks.registry.RegistryEnergyHandler;
import com.circulation.circulation_networks.tooltip.LocalizedComponent;
import com.circulation.circulation_networks.utils.FormatNumberUtils;
//? if <1.20 {
import com.circulation.circulation_networks.tiles.TileEntityMultiblockShell;
import net.minecraft.util.math.Vec3d;
//?} else {
/*import com.circulation.circulation_networks.tiles.MultiblockShellBlockEntity;
*///?}
//~ mc_imports
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

//? if <1.20 {
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
//?} else {
/*import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.HitResult;
*///?}

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ItemCirculationConfigurator extends BaseItem {

    //? if <1.20 {
    public ItemCirculationConfigurator() {
        super("circulation_configurator");
    }
    //?} else {
    /*public ItemCirculationConfigurator(Properties properties) {
        super(properties);
    }
    *///?}

    //~ if >=1.20 'EntityPlayerMP' -> 'ServerPlayer' {
    public static void sendModeMessage(EntityPlayerMP player, CirculationConfiguratorSelection selection) {
        //? if <1.20 {
        TextComponentTranslation modeComponent = new TextComponentTranslation(selection.modeLangKey());
        modeComponent.getStyle().setColor(TextFormatting.GOLD);
        if (!selection.function().hasSubModes()) {
            player.sendStatusMessage(modeComponent, true);
            return;
        }
        TextComponentTranslation submodeComponent = new TextComponentTranslation(selection.subModeLangKey());
        submodeComponent.getStyle().setColor(TextFormatting.BLUE);

        TextComponentTranslation message = new TextComponentTranslation(
            selection.modeDisplayKey(),
            modeComponent,
            submodeComponent
        );
        player.sendStatusMessage(message, true);
        //?} else {
        /*Component modeComponent = Component.translatable(selection.modeLangKey()).withStyle(ChatFormatting.GOLD);
        if (!selection.function().hasSubModes()) {
            player.displayClientMessage(modeComponent, true);
            return;
        }
        player.displayClientMessage(
            Component.translatable(
                selection.modeDisplayKey(),
                modeComponent,
                Component.translatable(selection.subModeLangKey()).withStyle(ChatFormatting.BLUE)
            ),
            true
        );
        *///?}
    }
    //~}

    //~ if >=1.20 'EntityPlayerMP' -> 'ServerPlayer' {
    private static void sendInteractionMessage(EntityPlayerMP player, String messageKey, Object... args) {
        //? if <1.20 {
        player.sendMessage(new TextComponentTranslation(messageKey, args));
        //?} else {
        /*player.displayClientMessage(Component.translatable(messageKey, args), false);
        *///?}
    }
    //~}

    //~ if >=1.20 'EntityPlayerMP' -> 'ServerPlayer' {
    private static void sendFeedbackMessage(EntityPlayerMP player, String messageKey, String detailKey) {
        //? if <1.20 {
        if (detailKey != null) {
            player.sendMessage(new TextComponentTranslation(messageKey, new TextComponentTranslation(detailKey)));
        } else {
            player.sendMessage(new TextComponentTranslation(messageKey));
        }
        //?} else {
        /*if (detailKey != null) {
            player.displayClientMessage(Component.translatable(messageKey, Component.translatable(detailKey)), false);
        } else {
            player.displayClientMessage(Component.translatable(messageKey), false);
        }
        *///?}
    }
    //~}

    //~ if >=1.20 'EntityPlayerMP' -> 'ServerPlayer' {
    public static CirculationConfiguratorSelection toggleFunction(ItemStack stack, EntityPlayerMP player) {
        var toggleResult = CirculationConfiguratorState.toggleFunction(stack);
        var selection = CirculationConfiguratorSelection.fromStack(stack);
        if (toggleResult.currentFunction() == ToolFunction.CONFIGURATION) {
            ConfigOverrideRendering.sendFullSync(player);
        } else if (toggleResult.previousFunction() == ToolFunction.CONFIGURATION) {
            ConfigOverrideRendering.sendClear(player);
        }
        return selection;
    }
    //~}

    //~ if >=1.20 'World world' -> 'Level world' {
    //~ if >=1.20 'world.provider.getDimension()' -> 'world.dimension().location().hashCode()' {
    private static int getDimensionId(World world) {
        return world.provider.getDimension();
    }
    //~}
    //~}

    //~ if >=1.20 '.toLong()' -> '.asLong()' {
    private static long packPos(BlockPos pos) {
        return pos.toLong();
    }
    //~}

    private static String formatEnergy(EnergyAmount amount) {
        RegistryEnergyHandler.Pair unit = RegistryEnergyHandler.getPair(0);
        if (!amount.isZero() && unit.multiplying() != 0.0D) {
            amount.divide(unit.multiplying());
        }
        return FormatNumberUtils.formatNumber(amount) + " " + unit.unit();
    }

    //~ if >=1.20 'World ' -> 'Level ' {
    private static boolean isChunkResident(World world, BlockPos pos) {
        //? if <1.20 {
        return world.getChunkProvider().getLoadedChunk(pos.getX() >> 4, pos.getZ() >> 4) != null;
        //?} else {
        /*return world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
        *///?}
    }
    //~}

    //~ if >=1.20 'World ' -> 'Level ' {
    //~ if >=1.20 'world.getTileEntity(pos)' -> 'world.getBlockEntity(pos)' {
    //? if <1.20 {
    //~ if >=1.20 'TileEntityMultiblockShell' -> 'MultiblockShellBlockEntity' {
    private static @Nullable BlockPos resolveResidentOriginPos(World world, BlockPos pos) {
        if (!isChunkResident(world, pos)) {
            return null;
        }
        var te = world.getTileEntity(pos);
        if (te instanceof TileEntityMultiblockShell shell && shell.canRedirect()) {
            BlockPos origin = shell.getOriginPos();
            return isChunkResident(world, origin) ? origin : null;
        }
        return pos;
    }
    //~}
    //?} else {
    /*private static @Nullable BlockPos resolveResidentOriginPos(World world, BlockPos pos) {
        if (!isChunkResident(world, pos)) {
            return null;
        }
        var te = world.getTileEntity(pos);
        if (te instanceof MultiblockShellBlockEntity shell && shell.canRedirect()) {
            BlockPos origin = shell.getOriginPos();
            return isChunkResident(world, origin) ? origin : null;
        }
        return pos;
    }
    *///?}
    //~}
    //~}

    //? if <1.20 {
    @Override
    public @NotNull EnumActionResult onItemUseFirst(@NotNull EntityPlayer player, @NotNull World world, @NotNull BlockPos pos,
                                                    @NotNull EnumFacing side, float hitX, float hitY, float hitZ, @NotNull EnumHand hand) {
        if (player.isSneaking()) {
            return EnumActionResult.PASS;
        }
        if (world.isRemote) {
            return isChunkResident(world, pos) && API.getNodeAt(world, pos) != null
                ? EnumActionResult.SUCCESS
                : EnumActionResult.PASS;
        }
        BlockPos resolved = resolveResidentOriginPos(world, pos);
        if (resolved == null) {
            return EnumActionResult.PASS;
        }
        if (!resolved.equals(pos)) {
            return EnumActionResult.PASS;
        }
        return PocketNodeManager.INSTANCE.removePocketNode(world, pos, true)
            ? EnumActionResult.SUCCESS
            : EnumActionResult.PASS;
    }
    //?} else {
    /*@Override
    public @NotNull InteractionResult onItemUseFirst(@NotNull ItemStack stack, @NotNull UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (context.getLevel().isClientSide) {
            return isChunkResident(context.getLevel(), context.getClickedPos())
                && API.getNodeAt(context.getLevel(), context.getClickedPos()) != null
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
    *///?}

    //? if <1.20 {
    @Override
    public @NotNull EnumActionResult onItemUse(@NotNull EntityPlayer player, @NotNull World worldIn, @NotNull BlockPos pos, @NotNull EnumHand hand, @NotNull EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (worldIn.isRemote) {
            CirculationConfiguratorSelection selection = CirculationConfiguratorSelection.fromStack(player.getHeldItem(hand));
            if (player.isSneaking() && !selection.function().hasSubModes()) {
                BlockPos target = resolveResidentOriginPos(worldIn, pos);
                if (target == null) {
                    return EnumActionResult.PASS;
                }
                return API.getNodeAt(worldIn, target) != null || worldIn.getTileEntity(target) != null
                    ? EnumActionResult.SUCCESS
                    : EnumActionResult.PASS;
            }
            return !player.isSneaking() && isChunkResident(worldIn, pos) && API.getNodeAt(worldIn, pos) != null
                ? EnumActionResult.SUCCESS : EnumActionResult.PASS;
        }
        if (!(player instanceof EntityPlayerMP p)) {
            return EnumActionResult.PASS;
        }
        if (!isChunkResident(worldIn, pos)) {
            return EnumActionResult.PASS;
        }
        if (!p.isSneaking() && PocketNodeManager.INSTANCE.removePocketNode(worldIn, pos, true)) {
            return EnumActionResult.SUCCESS;
        }

        BlockPos target = resolveResidentOriginPos(worldIn, pos);
        if (target == null) {
            return EnumActionResult.PASS;
        }
        ItemStack stack = p.getHeldItem(hand);
        CirculationConfiguratorSelection selection = CirculationConfiguratorSelection.fromStack(stack);
        return switch (selection.function()) {
            case INSPECTION -> executeInspection(p, worldIn, target);
            case CONFIGURATION -> executeConfiguration(p, worldIn, target, selection.subMode());
            case PRIORITY -> executePriority(p, worldIn, target, hand);
            case INTERACTION -> executeInteraction(p, worldIn, target);
        };
    }
    //?} else {
    /*@Override
    public @NotNull InteractionResult useOn(@NotNull UseOnContext context) {
        Player player = context.getPlayer();
        if (context.getLevel().isClientSide) {
            if (player == null) {
                return InteractionResult.PASS;
            }
            CirculationConfiguratorSelection selection = CirculationConfiguratorSelection.fromStack(context.getItemInHand());
            if (player.isShiftKeyDown() && !selection.function().hasSubModes()) {
                BlockPos target = resolveResidentOriginPos(context.getLevel(), context.getClickedPos());
                if (target == null) {
                    return InteractionResult.PASS;
                }
                return API.getNodeAt(context.getLevel(), target) != null || context.getLevel().getBlockEntity(target) != null
                    ? InteractionResult.SUCCESS : InteractionResult.PASS;
            }
            return !player.isShiftKeyDown() && isChunkResident(context.getLevel(), context.getClickedPos())
                && API.getNodeAt(context.getLevel(), context.getClickedPos()) != null
                ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer p)) {
            return InteractionResult.PASS;
        }
        if (!isChunkResident(context.getLevel(), context.getClickedPos())) {
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
            case INSPECTION -> executeInspection(p, context.getLevel(), target);
            case CONFIGURATION -> executeConfiguration(p, context.getLevel(), target, selection.subMode());
            case PRIORITY -> executePriority(p, context.getLevel(), target, context.getHand());
            case INTERACTION -> executeInteraction(p, context.getLevel(), target);
        };
    }
    *///?}

    //? if <1.20 {
    private EnumActionResult executePriority(EntityPlayerMP player, World world, BlockPos pos, EnumHand hand) {
        if (!player.isSneaking()) {
            return EnumActionResult.PASS;
        }
        ContainerMachinePriority.open(player, world, pos, hand);
        return EnumActionResult.SUCCESS;
    }
    //?} else {
    /*private InteractionResult executePriority(ServerPlayer player, Level world, BlockPos pos, InteractionHand hand) {
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        ContainerMachinePriority.open(player, world, pos, hand);
        return InteractionResult.SUCCESS;
    }
    *///?}

    //? if <1.20 {
    private EnumActionResult executeInteraction(EntityPlayerMP player, World world, BlockPos pos) {
        if (!player.isSneaking()) {
            return EnumActionResult.PASS;
        }
        return queueInteractionReport(player, world, pos)
            ? EnumActionResult.SUCCESS : EnumActionResult.FAIL;
    }
    //?} else {
    /*private InteractionResult executeInteraction(ServerPlayer player, Level world, BlockPos pos) {
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        return queueInteractionReport(player, world, pos)
            ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }
    *///?}

    //~ if >=1.20 'EntityPlayerMP' -> 'ServerPlayer' {
    //~ if >=1.20 'World ' -> 'Level ' {
    //~ if >=1.20 'world.getTileEntity(pos)' -> 'world.getBlockEntity(pos)' {
    private boolean queueInteractionReport(EntityPlayerMP player, World world, BlockPos pos) {
        if (!isChunkResident(world, pos)) {
            return false;
        }
        INode node = API.getNodeAt(world, pos);
        var nativeBlockEntity = world.getTileEntity(pos);
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
    //~}
    //~}
    //~}

    //~ if >=1.20 'EntityPlayerMP' -> 'ServerPlayer' {
    //~ if >=1.20 'World ' -> 'Level ' {
    //~ if >=1.20 'world.getTileEntity(pos)' -> 'world.getBlockEntity(pos)' {
    private boolean sendInteractionReport(EntityPlayerMP player, World world, BlockPos pos) {
        if (!isChunkResident(world, pos)) {
            return false;
        }
        INode node = API.getNodeAt(world, pos);
        var nativeBlockEntity = world.getTileEntity(pos);
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
    //~}
    //~}

    @Override
    protected List<LocalizedComponent> buildTooltips(ItemStack stack) {
        List<LocalizedComponent> tips = CirculationConfiguratorSelection.fromStack(stack).tooltipLines();
        tips.addAll(super.buildTooltips(stack));
        return tips;
    }

    //? if <1.20 {
    @Override
    public @NotNull ActionResult<ItemStack> onItemRightClick(@NotNull World worldIn, @NotNull EntityPlayer player, @NotNull EnumHand hand) {
        if (player.isSneaking()) {
            double blockReachDistance = player.getEntityAttribute(EntityPlayer.REACH_DISTANCE).getAttributeValue();
            Vec3d vec3d = player.getPositionEyes(1.0F);
            Vec3d vec3d1 = player.getLook(1.0F);
            Vec3d vec3d2 = vec3d.add(vec3d1.x * blockReachDistance, vec3d1.y * blockReachDistance, vec3d1.z * blockReachDistance);
            RayTraceResult ray = player.world.rayTraceBlocks(vec3d, vec3d2, false, false, true);
            if (ray == null || ray.typeOfHit == RayTraceResult.Type.MISS) {
                ItemStack stack = player.getHeldItem(hand);
                if (worldIn.isRemote) {
                    CirculationFlowNetworks.sendToServer(new ToggleItemFunctionMessage());
                }
                return new ActionResult<>(EnumActionResult.SUCCESS, stack);
            }
        }
        return super.onItemRightClick(worldIn, player, hand);
    }

    //?} else {
    /*@Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level worldIn, @NotNull Player player, @NotNull InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            HitResult ray = player.pick(5.0D, 1.0F, false);
            if (ray == null || ray.getType() == HitResult.Type.MISS) {
                ItemStack stack = player.getItemInHand(hand);
                if (worldIn.isClientSide) {
                    CirculationFlowNetworks.sendToServer(new ToggleItemFunctionMessage());
                }
                return InteractionResultHolder.success(stack);
            }
        }
        return super.use(worldIn, player, hand);
    }
    *///?}

    //? if <1.20 {
    private EnumActionResult executeInspection(EntityPlayerMP player, World world, BlockPos pos) {
        INode node = API.getNodeAt(world, pos);
        if (node == null) {
            return EnumActionResult.PASS;
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
        return EnumActionResult.SUCCESS;
    }
    //?} else {
    /*private InteractionResult executeInspection(ServerPlayer player, Level world, BlockPos pos) {
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
    *///?}

    //? if <1.20 {
    private EnumActionResult executeConfiguration(EntityPlayerMP player, World world, BlockPos pos, int subMode) {
        var manager = EnergyTypeOverrideManager.get();
        if (manager == null) {
            return EnumActionResult.FAIL;
        }
        if (!isChunkResident(world, pos)) {
            return EnumActionResult.PASS;
        }

        INode node = API.getNodeAt(world, pos);
        //~ if >=1.20 'world.getTileEntity(pos)' -> 'world.getBlockEntity(pos)' {
        var blockEntity = world.getTileEntity(pos);
        //~}
        if (node != null) {
            sendFeedbackMessage(player, "item.circulation_networks.circulation_configurator.config.node_blocked", null);
            return EnumActionResult.FAIL;
        }
        if (blockEntity == null) {
            return EnumActionResult.PASS;
        }
        if (RegistryEnergyHandler.isBlack(blockEntity) || !RegistryEnergyHandler.isEnergyTileEntity(blockEntity)) {
            sendFeedbackMessage(player, "item.circulation_networks.circulation_configurator.config.invalid_target", null);
            return EnumActionResult.FAIL;
        }

        ConfigurationMode mode = ConfigurationMode.fromID(subMode);
        int dim = getDimensionId(world);
        if (mode == ConfigurationMode.CLEAR) {
            manager.clearOverride(dim, pos);
            ConfigOverrideRendering.sendRemove(player, packPos(pos));
            sendFeedbackMessage(player, "item.circulation_networks.circulation_configurator.config.cleared", null);
            return EnumActionResult.SUCCESS;
        }

        var energyType = mode.getEnergyType();
        manager.setOverride(dim, pos, energyType);
        ConfigOverrideRendering.sendAdd(player, packPos(pos), energyType);
        sendFeedbackMessage(player, "item.circulation_networks.circulation_configurator.config.set", mode.getLangKey());
        return EnumActionResult.SUCCESS;
    }
    //?} else {
    /*private InteractionResult executeConfiguration(ServerPlayer player, Level world, BlockPos pos, int subMode) {
        var manager = EnergyTypeOverrideManager.get();
        if (manager == null) {
            return InteractionResult.FAIL;
        }
        if (!isChunkResident(world, pos)) {
            return InteractionResult.PASS;
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
        int dim = getDimensionId(world);
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
    *///?}
}
