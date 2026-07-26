package com.circulation.circulation_networks.container;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.api.CFNBlockEntityEx;
import com.circulation.circulation_networks.items.CirculationConfiguratorModeModel.ToolFunction;
import com.circulation.circulation_networks.items.CirculationConfiguratorState;
import com.circulation.circulation_networks.manager.EnergyMachineManager;
import com.circulation.circulation_networks.registry.CFNItems;
import com.circulation.circulation_networks.utils.GuiSync;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Server-authoritative machine-priority session backed by CFN's annotated container synchronization. */
public final class ContainerMachinePriority extends CFNBaseContainer {

    public static final int GUI_MAIN_HAND = 1;
    public static final int GUI_OFF_HAND = 2;
    private static final long SNAPSHOT_READY_MASK = 1L << 32;
    private static final long INVALID_GENERATION = Long.MIN_VALUE;
    private static final String ERROR_CONTEXT = "item.circulation_networks.circulation_configurator.priority.error.context_changed";
    private static final String ERROR_TARGET = "item.circulation_networks.circulation_configurator.priority.error.invalid_target";
    private static final String ERROR_DISCONNECTED = "item.circulation_networks.circulation_configurator.priority.error.disconnected";
    private static final String ERROR_REACH = "item.circulation_networks.circulation_configurator.priority.error.out_of_reach";
    private static final String SUCCESS = "item.circulation_networks.circulation_configurator.priority.set";

    private final World world;
    private final BlockPos position;
    private final EnumHand hand;
    @Nullable
    private final CFNBlockEntityEx target;
    private final long bindingGeneration;

    @GuiSync(0)
    public long prioritySnapshot;

    public ContainerMachinePriority(EntityPlayer player, World world, BlockPos position, EnumHand hand) {
        super(player);
        this.world = world;
        this.position = position;
        this.hand = hand;
        this.target = world.isRemote ? null : resolveTarget(world, position);
        EnergyMachineManager.ConnectedMachinePriority priority = target != null
            ? EnergyMachineManager.INSTANCE.getConnectedMachinePriority(target) : null;
        this.bindingGeneration = priority != null ? priority.bindingGeneration() : INVALID_GENERATION;
        this.prioritySnapshot = priority != null ? encodePriority(priority.priority()) : 0L;
    }

    public static void open(EntityPlayerMP player, World world, BlockPos position, EnumHand hand) {
        if (validateOpen(player, world, position, hand) == null) {
            return;
        }
        int guiId = hand == EnumHand.MAIN_HAND ? GUI_MAIN_HAND : GUI_OFF_HAND;
        CirculationFlowNetworks.openGui(guiId, player, world, position.getX(), position.getY(), position.getZ());
    }

    @Nullable
    public static EnumHand handFromGuiId(int guiId) {
        return guiId == GUI_MAIN_HAND ? EnumHand.MAIN_HAND
            : guiId == GUI_OFF_HAND ? EnumHand.OFF_HAND : null;
    }

    public int getSessionId() {
        return windowId;
    }

    public boolean hasPrioritySnapshot() {
        return (prioritySnapshot & SNAPSHOT_READY_MASK) != 0L;
    }

    public int getPriority() {
        if (!hasPrioritySnapshot()) {
            throw new IllegalStateException("Machine priority requested before the annotated snapshot arrived");
        }
        return (int) prioritySnapshot;
    }

    public void applyPriority(int priority) {
        CFNBlockEntityEx currentTarget = target;
        if (!(player instanceof EntityPlayerMP serverPlayer) || !validateCurrent(serverPlayer, true)
            || currentTarget == null) {
            return;
        }
        if (!EnergyMachineManager.INSTANCE.setConnectedMachinePriority(currentTarget, bindingGeneration, priority)) {
            EnergyMachineManager.ConnectedMachinePriority current =
                EnergyMachineManager.INSTANCE.getConnectedMachinePriority(currentTarget);
            send(serverPlayer, current == null ? ERROR_DISCONNECTED : ERROR_CONTEXT);
            return;
        }
        prioritySnapshot = encodePriority(priority);
        send(serverPlayer, SUCCESS, priority);
    }

    @Override
    public boolean canInteractWith(@NotNull EntityPlayer playerIn) {
        return world.isRemote || playerIn instanceof EntityPlayerMP serverPlayer
            && validateCurrent(serverPlayer, false);
    }

    @Override
    public void detectAndSendChanges() {
        if (!world.isRemote) {
            refreshPrioritySnapshot();
        }
        super.detectAndSendChanges();
    }

    private void refreshPrioritySnapshot() {
        if (target == null || bindingGeneration == INVALID_GENERATION) {
            prioritySnapshot = 0L;
            return;
        }
        EnergyMachineManager.ConnectedMachinePriority current =
            EnergyMachineManager.INSTANCE.getConnectedMachinePriority(target);
        prioritySnapshot = current != null && current.bindingGeneration() == bindingGeneration
            ? encodePriority(current.priority()) : 0L;
    }

    private boolean validateCurrent(EntityPlayerMP serverPlayer, boolean report) {
        if (serverPlayer.world != world || !(world instanceof WorldServer serverWorld)) {
            return fail(serverPlayer, report, ERROR_CONTEXT);
        }
        if (serverWorld.getChunkProvider().getLoadedChunk(position.getX() >> 4, position.getZ() >> 4) == null
            || target == null || resolveTarget(world, position) != target) {
            return fail(serverPlayer, report, ERROR_TARGET);
        }
        double reach = serverPlayer.getEntityAttribute(EntityPlayer.REACH_DISTANCE).getAttributeValue();
        if (serverPlayer.getDistanceSqToCenter(position) > reach * reach) {
            return fail(serverPlayer, report, ERROR_REACH);
        }
        if (!hasPriorityConfigurator(serverPlayer, hand)) {
            return fail(serverPlayer, report, ERROR_CONTEXT);
        }
        EnergyMachineManager.ConnectedMachinePriority current =
            EnergyMachineManager.INSTANCE.getConnectedMachinePriority(target);
        if (current == null) {
            return fail(serverPlayer, report, ERROR_DISCONNECTED);
        }
        return current.bindingGeneration() == bindingGeneration
            || fail(serverPlayer, report, ERROR_CONTEXT);
    }

    @Nullable
    private static CFNBlockEntityEx validateOpen(EntityPlayerMP player, World world, BlockPos position, EnumHand hand) {
        if (player.world != world || !(world instanceof WorldServer serverWorld)) {
            send(player, ERROR_CONTEXT);
            return null;
        }
        if (serverWorld.getChunkProvider().getLoadedChunk(position.getX() >> 4, position.getZ() >> 4) == null) {
            send(player, ERROR_TARGET);
            return null;
        }
        double reach = player.getEntityAttribute(EntityPlayer.REACH_DISTANCE).getAttributeValue();
        if (player.getDistanceSqToCenter(position) > reach * reach) {
            send(player, ERROR_REACH);
            return null;
        }
        if (!hasPriorityConfigurator(player, hand)) {
            send(player, ERROR_CONTEXT);
            return null;
        }
        CFNBlockEntityEx target = resolveTarget(world, position);
        if (target == null) {
            send(player, ERROR_TARGET);
            return null;
        }
        if (EnergyMachineManager.INSTANCE.getConnectedMachinePriority(target) == null) {
            send(player, ERROR_DISCONNECTED);
            return null;
        }
        return target;
    }

    private static boolean hasPriorityConfigurator(EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        return stack.getItem() == CFNItems.circulationConfigurator
            && CirculationConfiguratorState.getFunction(stack) == ToolFunction.PRIORITY;
    }

    @Nullable
    private static CFNBlockEntityEx resolveTarget(World world, BlockPos position) {
        TileEntity blockEntity = world.getTileEntity(position);
        return blockEntity instanceof CFNBlockEntityEx target ? target : null;
    }

    private static long encodePriority(int priority) {
        return SNAPSHOT_READY_MASK | Integer.toUnsignedLong(priority);
    }

    private static boolean fail(EntityPlayerMP player, boolean report, String key) {
        if (report) {
            send(player, key);
        }
        return false;
    }

    private static void send(EntityPlayerMP player, String key, Object... args) {
        player.sendMessage(new TextComponentTranslation(key, args));
    }
}
