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
import com.circulation.circulation_networks.registry.RegistryEnergyHandler;
import com.circulation.circulation_networks.tooltip.LocalizedComponent;
//? if <1.20 {
import com.circulation.circulation_networks.tiles.TileEntityMultiblockShell;
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
        TextComponentTranslation submodeComponent = new TextComponentTranslation(selection.subModeLangKey());

        modeComponent.getStyle().setColor(TextFormatting.GOLD);
        submodeComponent.getStyle().setColor(TextFormatting.BLUE);

        TextComponentTranslation message = new TextComponentTranslation(
            selection.modeDisplayKey(),
            modeComponent,
            submodeComponent
        );
        player.sendStatusMessage(message, true);
        //?} else {
        /*player.displayClientMessage(
            Component.translatable(
                selection.modeDisplayKey(),
                Component.translatable(selection.modeLangKey()).withStyle(ChatFormatting.GOLD),
                Component.translatable(selection.subModeLangKey()).withStyle(ChatFormatting.BLUE)
            ),
            true
        );
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

    //~ if >=1.20 'World ' -> 'Level ' {
    //~ if >=1.20 'world.getTileEntity(pos)' -> 'world.getBlockEntity(pos)' {
    //? if <1.20 {
    //~ if >=1.20 'TileEntityMultiblockShell' -> 'MultiblockShellBlockEntity' {
    private static BlockPos resolveOriginPos(World world, BlockPos pos) {
        var te = world.getTileEntity(pos);
        if (te instanceof TileEntityMultiblockShell shell && shell.canRedirect()) {
            return shell.getOriginPos();
        }
        return pos;
    }
    //~}
    //?} else {
    /*private static BlockPos resolveOriginPos(Level world, BlockPos pos) {
        var te = world.getBlockEntity(pos);
        if (te instanceof MultiblockShellBlockEntity shell && shell.canRedirect()) {
            return shell.getOriginPos();
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
            return API.getNodeAt(world, pos) != null
                ? EnumActionResult.SUCCESS
                : EnumActionResult.PASS;
        }
        BlockPos resolved = resolveOriginPos(world, pos);
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
    *///?}

    //? if <1.20 {
    @Override
    public @NotNull EnumActionResult onItemUse(@NotNull EntityPlayer player, @NotNull World worldIn, @NotNull BlockPos pos, @NotNull EnumHand hand, @NotNull EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (worldIn.isRemote) {
            return !player.isSneaking() && API.getNodeAt(worldIn, pos) != null
                ? EnumActionResult.SUCCESS
                : EnumActionResult.PASS;
        }
        if (!(player instanceof EntityPlayerMP p)) {
            return EnumActionResult.PASS;
        }
        if (!p.isSneaking() && PocketNodeManager.INSTANCE.removePocketNode(worldIn, pos, true)) {
            return EnumActionResult.SUCCESS;
        }

        BlockPos target = resolveOriginPos(worldIn, pos);
        ItemStack stack = p.getHeldItemMainhand();
        CirculationConfiguratorSelection selection = CirculationConfiguratorSelection.fromStack(stack);
        return switch (selection.function()) {
            case INSPECTION -> executeInspection(p, worldIn, target, selection.subMode());
            case CONFIGURATION -> executeConfiguration(p, worldIn, target, selection.subMode());
        };
    }
    //?} else {
    /*@Override
    public @NotNull InteractionResult useOn(@NotNull UseOnContext context) {
        Player player = context.getPlayer();
        if (context.getLevel().isClientSide) {
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
    *///?}

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
            RayTraceResult ray = player.rayTrace(player.getEntityAttribute(EntityPlayer.REACH_DISTANCE).getAttributeValue(), 1.0F);
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
    private EnumActionResult executeInspection(EntityPlayerMP player, World world, BlockPos pos, int subMode) {
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
    /*private InteractionResult executeInspection(ServerPlayer player, Level world, BlockPos pos, int subMode) {
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
