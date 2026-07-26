package com.circulation.circulation_networks.container;

import com.circulation.circulation_networks.api.CFNBlockEntityEx;
import com.circulation.circulation_networks.items.CirculationConfiguratorModeModel.ToolFunction;
import com.circulation.circulation_networks.items.CirculationConfiguratorState;
import com.circulation.circulation_networks.manager.EnergyMachineManager;
import com.circulation.circulation_networks.registry.CFNItems;
import com.circulation.circulation_networks.registry.CFNMenuTypes;
import com.circulation.circulation_networks.utils.GuiSync;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Server-authoritative machine-priority session backed by CFN's annotated container synchronization. */
public final class ContainerMachinePriority extends CFNBaseContainer {

    private static final String TITLE_KEY = "gui.circulation_configurator.priority.title";
    private static final long SNAPSHOT_READY_MASK = 1L << 32;
    private static final long INVALID_GENERATION = Long.MIN_VALUE;
    private static final String ERROR_CONTEXT = "item.circulation_networks.circulation_configurator.priority.error.context_changed";
    private static final String ERROR_TARGET = "item.circulation_networks.circulation_configurator.priority.error.invalid_target";
    private static final String ERROR_DISCONNECTED = "item.circulation_networks.circulation_configurator.priority.error.disconnected";
    private static final String ERROR_REACH = "item.circulation_networks.circulation_configurator.priority.error.out_of_reach";
    private static final String SUCCESS = "item.circulation_networks.circulation_configurator.priority.set";

    private final Level level;
    private final BlockPos position;
    private final InteractionHand hand;
    @Nullable
    private final CFNBlockEntityEx target;
    private final long bindingGeneration;

    @GuiSync(0)
    public long prioritySnapshot;

    public ContainerMachinePriority(MenuType<?> menuType, int containerId, Player player,
                                    BlockPos position, InteractionHand hand) {
        super(menuType, containerId, player);
        this.level = player.level();
        this.position = position;
        this.hand = hand;
        this.target = level.isClientSide() ? null : resolveTarget(level, position);
        EnergyMachineManager.ConnectedMachinePriority priority = target != null
            ? EnergyMachineManager.INSTANCE.getConnectedMachinePriority(target) : null;
        this.bindingGeneration = priority != null ? priority.bindingGeneration() : INVALID_GENERATION;
        this.prioritySnapshot = priority != null ? encodePriority(priority.priority()) : 0L;
    }

    public static void open(ServerPlayer player, Level level, BlockPos position, InteractionHand hand) {
        if (validateOpen(player, level, position, hand) == null) {
            return;
        }
        SimpleMenuProvider provider = new SimpleMenuProvider(
            (containerId, inventory, menuPlayer) -> new ContainerMachinePriority(
                CFNMenuTypes.MACHINE_PRIORITY_MENU, containerId, menuPlayer, position, hand
            ),
            Component.translatable(TITLE_KEY)
        );
        player.openMenu(provider, buffer -> {
            buffer.writeBlockPos(position);
            buffer.writeByte(hand.ordinal());
        });
    }

    public static InteractionHand handFromOrdinal(int ordinal) {
        if (ordinal == InteractionHand.MAIN_HAND.ordinal()) {
            return InteractionHand.MAIN_HAND;
        }
        if (ordinal == InteractionHand.OFF_HAND.ordinal()) {
            return InteractionHand.OFF_HAND;
        }
        throw new IllegalArgumentException("Invalid machine-priority hand ordinal: " + ordinal);
    }

    public int getSessionId() {
        return containerId;
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
        if (!(player instanceof ServerPlayer serverPlayer) || !validateCurrent(serverPlayer, true)
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
    public boolean stillValid(@NotNull Player playerIn) {
        return level.isClientSide() || playerIn instanceof ServerPlayer serverPlayer
            && validateCurrent(serverPlayer, false);
    }

    @Override
    public void broadcastChanges() {
        if (!level.isClientSide()) {
            refreshPrioritySnapshot();
        }
        super.broadcastChanges();
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

    private boolean validateCurrent(ServerPlayer serverPlayer, boolean report) {
        if (serverPlayer.level() != level) {
            return fail(serverPlayer, report, ERROR_CONTEXT);
        }
        if (level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4) == null
            || target == null || resolveTarget(level, position) != target) {
            return fail(serverPlayer, report, ERROR_TARGET);
        }
        double reach = serverPlayer.blockInteractionRange();
        if (serverPlayer.distanceToSqr(position.getX() + 0.5D, position.getY() + 0.5D,
            position.getZ() + 0.5D) > reach * reach) {
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
    private static CFNBlockEntityEx validateOpen(ServerPlayer player, Level level,
                                                  BlockPos position, InteractionHand hand) {
        if (player.level() != level) {
            send(player, ERROR_CONTEXT);
            return null;
        }
        if (level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4) == null) {
            send(player, ERROR_TARGET);
            return null;
        }
        double reach = player.blockInteractionRange();
        if (player.distanceToSqr(position.getX() + 0.5D, position.getY() + 0.5D,
            position.getZ() + 0.5D) > reach * reach) {
            send(player, ERROR_REACH);
            return null;
        }
        if (!hasPriorityConfigurator(player, hand)) {
            send(player, ERROR_CONTEXT);
            return null;
        }
        CFNBlockEntityEx target = resolveTarget(level, position);
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

    private static boolean hasPriorityConfigurator(Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        return stack.getItem() == CFNItems.circulationConfigurator
            && CirculationConfiguratorState.getFunction(stack) == ToolFunction.PRIORITY;
    }

    @Nullable
    private static CFNBlockEntityEx resolveTarget(Level level, BlockPos position) {
        var blockEntity = level.getBlockEntity(position);
        return blockEntity instanceof CFNBlockEntityEx target ? target : null;
    }

    private static long encodePriority(int priority) {
        return SNAPSHOT_READY_MASK | Integer.toUnsignedLong(priority);
    }

    private static boolean fail(ServerPlayer player, boolean report, String key) {
        if (report) {
            send(player, key);
        }
        return false;
    }

    private static void send(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args), false);
    }
}
