package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.EnergyAmounts;
import com.circulation.circulation_networks.api.CFNBlockEntityEx;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.api.IEnergyHandlerManager;
import com.circulation.circulation_networks.api.IGrid;
import com.circulation.circulation_networks.api.IMachineNodeBlockEntity;
import com.circulation.circulation_networks.api.node.IEnergySupplyNode;
import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.api.node.IHubNode;
import com.circulation.circulation_networks.events.BlockEntityLifeCycleEvent;
import com.circulation.circulation_networks.packets.NodeNetworkRendering;
import com.circulation.circulation_networks.packets.EnergyWarningRendering;
import com.circulation.circulation_networks.network.nodes.HubNode;
import com.circulation.circulation_networks.utils.ChunkCoordUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import it.unimi.dsi.fastutil.objects.ReferenceSets;
//~ mc_imports
import net.minecraft.util.math.BlockPos;
//~ if >=1.20 'net.minecraft.world.World' -> 'net.minecraft.world.level.Level' {
import net.minecraft.world.World;
//~}
//? if <1.20 {
import net.minecraft.entity.player.EntityPlayerMP;
//?} else {
/*import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
 *///?}
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import static com.circulation.circulation_networks.utils.SideCompat.isClientWorld;

public final class EnergyMachineManager {

    public static final EnergyMachineManager INSTANCE = new EnergyMachineManager();

    public enum RegistrationResult {
        REGISTERED,
        ALREADY_REGISTERED,
        WAITING_FOR_SCOPE,
        WAITING_FOR_READY,
        UNSUPPORTED,
        STALE,
        FAILED
    }

    private static final long NODE_RESCAN_COOLDOWN_TICKS = 40L;
    private static final long WARNING_RANGE_SYNC_INTERVAL_TICKS = 20L;
    private static final long WARNING_EVALUATION_INTERVAL_TICKS = 20L;
    private static final double WARNING_RENDER_DISTANCE_SQ = 48.0D * 48.0D;
    /**
     * Chunk radius that fully contains {@link #WARNING_RENDER_DISTANCE_SQ} around a player. It is a deliberate
     * over-approximation of the exact distance test so warning sampling can be skipped for machines no player
     * could ever be shown, without ever excluding a machine the exact test would accept.
     */
    private static final int WARNING_OBSERVER_CHUNK_RADIUS = 3;
    /**
     * Ticks that one read of a machine-scoped interaction tracker keeps per-machine accounting alive. It only has
     * to outlast the polling interval of an open GUI or HUD so a continuously observed machine never falls out of
     * the window between two reads.
     */
    private static final long MACHINE_INTERACTION_DEMAND_TICKS = 100L;
    /** Backstop against unbounded growth if machine ticks stop draining queued statistics reads. */
    private static final int MAX_DEFERRED_INTERACTION_QUERIES = 256;
    private static final int POSITION_READY_MAX_RESOLUTION_ATTEMPTS = 2;
    private static final int MIN_DISCOVERY_THROTTLE_TIMER =
        CFNBlockEntityEx.MIN_ENERGY_DISCOVERY_THROTTLE_TIMER;
    private static final int DISCOVERY_THROTTLE_TIMER_COUNT =
        CFNBlockEntityEx.ENERGY_DISCOVERY_THROTTLE_SPREAD;
    private static final int DISCOVERY_THROTTLE_WHEEL_SIZE =
        CFNBlockEntityEx.MAX_STORED_ENERGY_THROTTLE_TIMER + 1;
    private static final long NO_DISCOVERY_THROTTLE_MEMBERSHIP = Long.MIN_VALUE;
    private final Int2ObjectMap<Long2ObjectMap<ReferenceSet<IEnergySupplyNode>>> scopeNode = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<Object2ObjectMap<IEnergySupplyNode, LongSet>> nodeScope = new Int2ObjectOpenHashMap<>();
    private final Reference2ObjectMap<INode, ReferenceSet<CFNBlockEntityEx>> gridMachineMap = new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectMap<CFNBlockEntityEx, ReferenceSet<INode>> machineGridMap = new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectMap<CFNBlockEntityEx, ReferenceSet<IEnergySupplyNode>> machineSupplyNodes = new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectMap<IEnergySupplyNode, ReferenceSet<CFNBlockEntityEx>> supplyNodeMachines = new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectMap<CFNBlockEntityEx, MachineRoute> machineRoutes = new Reference2ObjectOpenHashMap<>();
    private final Int2ObjectMap<Long2ObjectMap<MachineRoute>> machineRoutesByPosition = new Int2ObjectOpenHashMap<>();
    private final Reference2LongMap<IEnergySupplyNode> nodeRescanTicks = new Reference2LongOpenHashMap<>();
    private final Object2ObjectMap<String, LongSet> warningPositionsScratch = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<String, LongSet> warningObserverChunksScratch = new Object2ObjectOpenHashMap<>();
    private final WarningSnapshotSynchronizer warningSnapshots =
        new WarningSnapshotSynchronizer(WARNING_RANGE_SYNC_INTERVAL_TICKS);
    private final ObjectOpenHashSet<UUID> onlineWarningPlayerIdsScratch = new ObjectOpenHashSet<>();
    private final Object lifecycleLock = new Object();
    private final MachineLifecyclePositionIndex<CFNBlockEntityEx> lifecyclePositions =
        new MachineLifecyclePositionIndex<>();
    private final MachineChunkResidencyIndex<MachinePositionRecord> machineChunkResidency =
        new MachineChunkResidencyIndex<>();
    private final MachineChunkResidencyIndex.TransitionSink<MachinePositionRecord> machineChunkTransitions =
        new MachineChunkResidencyIndex.TransitionSink<>() {
            @Override
            public void dehydrateUnloaded(int dimensionId,
                                          long chunkCoordinate,
                                          Collection<MachinePositionRecord> records) {
                dehydrateMachineChunk(dimensionId, records);
            }

            @Override
            public boolean reconcileLoaded(int dimensionId,
                                           long chunkCoordinate,
                                           MachinePositionRecord record) {
                return reconcileLoadedMachinePosition(dimensionId, record);
            }
        };
    private final ObjectArrayList<LifecycleCommand> lifecycleInbox = new ObjectArrayList<>();
    private final ObjectArrayList<LifecycleCommand> lifecycleDrain = new ObjectArrayList<>();
    private final ObjectArrayList<ObjectArrayList<CFNBlockEntityEx>> discoveryThrottleWheel =
        new ObjectArrayList<>(DISCOVERY_THROTTLE_WHEEL_SIZE);
    private final ObjectArrayList<CFNBlockEntityEx> discoveryThrottleDrain = new ObjectArrayList<>();
    private final Reference2LongMap<CFNBlockEntityEx> discoveryThrottleMembership =
        new Reference2LongOpenHashMap<>();
    private final ObjectArrayList<Runnable> deferredInteractionQueries = new ObjectArrayList<>();
    private final ObjectArrayList<Runnable> deferredInteractionQueryDrain = new ObjectArrayList<>();
    private final Object2ObjectMap<PositionLifecycleKey, PositionLifecycleCommand> positionLifecycleInbox =
        new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<PositionLifecycleKey, PositionLifecycleCommand> positionLifecycleDrain =
        new Object2ObjectOpenHashMap<>();
    private final PriorityRoleCursor sendCursor = new PriorityRoleCursor();
    private final PriorityRoleCursor receiveCursor = new PriorityRoleCursor();
    private final PriorityRoleCursor itemSendCursor = new PriorityRoleCursor();
    private final PriorityRoleCursor warningCursor = new PriorityRoleCursor();
    private long warningTickCounter;
    private long warningSessionGeneration = 1L;
    private long interactionEpoch;
    private long transferPassId;
    private long machineInteractionDemandEpoch = Long.MIN_VALUE;
    private int discoveryThrottleCursor;
    private int discoveryThrottleWheelCursor;
    private boolean lifecycleDraining;

    {
        scopeNode.defaultReturnValue(Long2ObjectMaps.emptyMap());
        nodeScope.defaultReturnValue(Object2ObjectMaps.emptyMap());
        gridMachineMap.defaultReturnValue(ReferenceSets.emptySet());
        supplyNodeMachines.defaultReturnValue(ReferenceSets.emptySet());
        discoveryThrottleMembership.defaultReturnValue(NO_DISCOVERY_THROTTLE_MEMBERSHIP);
        for (int index = 0; index < DISCOVERY_THROTTLE_WHEEL_SIZE; index++) {
            discoveryThrottleWheel.add(new ObjectArrayList<>());
        }
    }

    //? if <1.20 {
    private static MinecraftServer getServer() {
        return CirculationFlowNetworks.server;
    }

    private static int getDimensionId(World world) {
        return world.provider.getDimension();
    }

    private static World resolveDimension(MinecraftServer server, int dimensionId) {
        return server.getWorld(dimensionId);
    }

    private static String getDimensionKey(World world) {
        return "legacy:" + world.provider.getDimension();
    }

    private static String getPlayerDimensionKey(EntityPlayerMP player) {
        return "legacy:" + player.dimension;
    }

    private static UUID getPlayerId(EntityPlayerMP player) {
        return player.getUniqueID();
    }

    private static double getPlayerDistanceSq(EntityPlayerMP player, BlockPos pos) {
        return player.getDistanceSq(pos.getX() + 0.5D, pos.getY() + 1.25D, pos.getZ() + 0.5D);
    }

    private static BlockPos getPlayerBlockPos(EntityPlayerMP player) {
        return player.getPosition();
    }
    //?} else if <1.21 {
    /*private static MinecraftServer getServer() {
        return net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
    }

    private static int getDimensionId(Level world) {
        return world.dimension().location().hashCode();
    }

    private static Level resolveDimension(MinecraftServer server, int dimensionId) {
        for (ServerLevel level : server.getAllLevels()) {
            if (getDimensionId(level) == dimensionId) {
                return level;
            }
        }
        return null;
    }

    private static String getDimensionKey(Level world) {
        return world.dimension().location().toString();
    }

    private static String getPlayerDimensionKey(ServerPlayer player) {
        return player.level().dimension().location().toString();
    }

    private static UUID getPlayerId(ServerPlayer player) {
        return player.getUUID();
    }

    private static double getPlayerDistanceSq(ServerPlayer player, BlockPos pos) {
        double dx = player.getX() - (pos.getX() + 0.5D);
        double dy = player.getY() - (pos.getY() + 1.25D);
        double dz = player.getZ() - (pos.getZ() + 0.5D);
        return dx * dx + dy * dy + dz * dz;
    }

    private static BlockPos getPlayerBlockPos(ServerPlayer player) {
        return player.blockPosition();
    }
    *///?} else {
    /*private static MinecraftServer getServer() {
        return net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
    }

    private static int getDimensionId(Level world) {
        return world.dimension().location().hashCode();
    }

    private static Level resolveDimension(MinecraftServer server, int dimensionId) {
        for (ServerLevel level : server.getAllLevels()) {
            if (getDimensionId(level) == dimensionId) {
                return level;
            }
        }
        return null;
    }

    private static String getDimensionKey(Level world) {
        return world.dimension().location().toString();
    }

    private static String getPlayerDimensionKey(ServerPlayer player) {
        return player.level().dimension().location().toString();
    }

    private static UUID getPlayerId(ServerPlayer player) {
        return player.getUUID();
    }

    private static double getPlayerDistanceSq(ServerPlayer player, BlockPos pos) {
        double dx = player.getX() - (pos.getX() + 0.5D);
        double dy = player.getY() - (pos.getY() + 1.25D);
        double dz = player.getZ() - (pos.getZ() + 0.5D);
        return dx * dx + dy * dy + dz * dz;
    }

    private static BlockPos getPlayerBlockPos(ServerPlayer player) {
        return player.blockPosition();
    }
    *///?}

    //~ if >=1.20 '.fromLong(' -> '.of(' {
    private static BlockPos blockPosFromLong(long posLong) {
        return BlockPos.fromLong(posLong);
    }
    //~}

    //~ if >=1.20 '.toLong()' -> '.asLong()' {
    private static long packBlockPosition(BlockPos position) {
        return position.toLong();
    }
    //~}

    @SuppressWarnings("unused")
    public Map<CFNBlockEntityEx, ReferenceSet<INode>> getMachineGridMap() {
        return machineGridMap;
    }

    public void onBlockEntityValidate(BlockEntityLifeCycleEvent.Validate event) {
        if (isClientWorld(event.getWorld())) return;
        submitLifecycle(event, MachineLifecyclePositionIndex.Action.DISCOVERED);
    }

    public void onBlockEntityInvalidate(BlockEntityLifeCycleEvent.Invalidate event) {
        if (isClientWorld(event.getWorld())) return;
        submitLifecycle(event, MachineLifecyclePositionIndex.Action.INVALIDATE);
    }

    public void onBlockEntityReady(CFNBlockEntityEx blockEntity) {
        submitLifecycle(blockEntity, MachineLifecyclePositionIndex.Action.READY);
    }

    //~ if >=1.20 'World ' -> 'Level ' {
    public void onBlockPositionReady(World world, BlockPos position) {
        submitPositionLifecycle(world, position, MachineLifecyclePositionIndex.Action.READY, null);
    }
    //~}

    //~ if >=1.20 'World ' -> 'Level ' {
    public void onManagerUnavailableAtPosition(World world,
                                               BlockPos position,
                                               IEnergyHandlerManager unavailableManager) {
        submitPositionLifecycle(
            world, position, MachineLifecyclePositionIndex.Action.MANAGER_UNAVAILABLE,
            Objects.requireNonNull(unavailableManager, "unavailableManager")
        );
    }
    //~}

    public void onServerTick() {
        var server = getServer();
        if (server == null) {
            return;
        }
        if (!NetworkManager.INSTANCE.isInit()) {
            NetworkManager.INSTANCE.initGrid();
            PocketNodeManager.INSTANCE.load();
        }
        boolean bindingWindowOpen = false;
        boolean routingWindowOpen = false;
        boolean evaluateWarnings;
        try {
            reconcileMachineChunkResidency();
            advanceDiscoveryThrottleWheel();
            loadPositionLifecycle(server);
            loadCache();
            if (interactionEpoch == Long.MAX_VALUE) {
                throw new IllegalStateException("Machine transfer epoch exhausted");
            }
            warningTickCounter++;
            evaluateWarnings = warningTickCounter % WARNING_EVALUATION_INTERVAL_TICKS == 0L;
            interactionEpoch++;
            if (!EnergyHandlerRuntime.beginBindings(interactionEpoch)) {
                return;
            }
            bindingWindowOpen = true;

            if (evaluateWarnings) {
                clearWarningPositionsScratch();
                refreshWarningObserverChunks(server);
            }
            routingWindowOpen = true;
            beginPersistentRouting();
            tickLocalRoutes();
            tickChannelRoutes();
            try {
                ChargingManager.INSTANCE.onServerTick(server, interactionEpoch);
            } catch (RuntimeException exception) {
                CirculationFlowNetworks.LOGGER.error("Charging tick failed", exception);
            }
            if (evaluateWarnings) {
                prepareAccountWarnings();
            }
        } finally {
            if (routingWindowOpen) {
                endPersistentRouting();
            }
            if (bindingWindowOpen) {
                EnergyHandlerRuntime.endBindings(interactionEpoch);
            }
        }
        if (evaluateWarnings) {
            settleAccountWarnings();
            sendWarningsToNearbyPlayers(server, warningPositionsScratch);
        }
        drainDeferredInteractionQueries();
    }

    /**
     * Queues a one-shot per-machine statistics read for the end of a machine tick that has already accounted for
     * it. Per-machine accounting is demand-driven, so a read issued the moment a player asks for it would observe
     * a tick that was never accounted; submitting the read instead renews the accounting window now and reports
     * one fully accounted tick.
     *
     * @param query server-thread callback that performs the read and its output
     */
    public void submitDeferredInteractionQuery(Runnable query) {
        Objects.requireNonNull(query, "query");
        renewMachineInteractionDemand();
        if (deferredInteractionQueries.size() >= MAX_DEFERRED_INTERACTION_QUERIES) {
            CirculationFlowNetworks.LOGGER.warn(
                "Dropping a deferred machine interaction query because {} are already pending",
                deferredInteractionQueries.size()
            );
            return;
        }
        deferredInteractionQueries.add(query);
    }

    private void drainDeferredInteractionQueries() {
        if (deferredInteractionQueries.isEmpty()) {
            return;
        }
        deferredInteractionQueryDrain.addAll(deferredInteractionQueries);
        deferredInteractionQueries.clear();
        try {
            for (int index = 0; index < deferredInteractionQueryDrain.size(); index++) {
                try {
                    deferredInteractionQueryDrain.get(index).run();
                } catch (RuntimeException exception) {
                    CirculationFlowNetworks.LOGGER.error("Deferred machine interaction query failed", exception);
                }
            }
        } finally {
            deferredInteractionQueryDrain.clear();
        }
    }

    private void beginPersistentRouting() {
        LocalParticipantRoutingIndex localRoutes = LocalParticipantRoutingIndex.INSTANCE;
        for (int index = 0, count = localRoutes.routingGridCount(); index < count; index++) {
            IGrid grid = localRoutes.routingGridAt(index);
            try {
                grid.getParticipantIndex().beginRouting(interactionEpoch);
            } catch (RuntimeException exception) {
                CirculationFlowNetworks.LOGGER.error("Failed to open machine routing for grid {}", grid.getId(), exception);
            }
        }
        ChannelParticipantIndex channels = ChannelParticipantIndex.INSTANCE;
        for (int index = 0, count = channels.routingChannelCount(); index < count; index++) {
            ChannelParticipantIndex.ChannelEntry channel = channels.routingChannelAt(index);
            try {
                channels.beginRouting(channel.channelId(), interactionEpoch);
            } catch (RuntimeException exception) {
                CirculationFlowNetworks.LOGGER.error("Failed to open machine routing for channel {}", channel.channelId(), exception);
            }
        }
    }

    /**
     * Reports whether any of the three transfer phases can move energy at all. Each phase pairs two distinct
     * role indexes, so a set of role indexes with fewer than two populated members has no eligible pair and the
     * whole timed routing block can be skipped.
     */
    private static boolean hasTransferablePair(PriorityRoleIndex send,
                                               PriorityRoleIndex storage,
                                               PriorityRoleIndex receive) {
        int populated = 0;
        if (!send.isEmpty()) {
            populated++;
        }
        if (!storage.isEmpty()) {
            populated++;
        }
        if (!receive.isEmpty()) {
            populated++;
        }
        return populated >= 2;
    }

    private void tickLocalRoutes() {
        LocalParticipantRoutingIndex routes = LocalParticipantRoutingIndex.INSTANCE;
        int routeCount = routes.routingGridCount();
        for (int index = 0; index < routeCount; index++) {
            IGrid grid = routes.routingGridAt(index);
            GridParticipantIndex participants = grid.getParticipantIndex();
            if (!participants.isRoutingActive()
                || !hasTransferablePair(participants.send(), participants.storage(), participants.receive())) {
                continue;
            }
            long startNanos = System.nanoTime();
            try {
                transferEnergy(participants.send(), participants.receive(), Status.INTERACTION, interactionEpoch);
                transferEnergy(participants.storage(), participants.receive(), Status.EXTRACT, interactionEpoch);
                transferEnergy(participants.send(), participants.storage(), Status.RECEIVE, interactionEpoch);
            } catch (RuntimeException exception) {
                CirculationFlowNetworks.LOGGER.error("Machine routing failed for grid {}", grid.getId(), exception);
            } finally {
                recordGridTickTimeNanos(grid, System.nanoTime() - startNanos);
            }
        }
    }

    private void tickChannelRoutes() {
        ChannelParticipantIndex channels = ChannelParticipantIndex.INSTANCE;
        int channelCount = channels.routingChannelCount();
        for (int index = 0; index < channelCount; index++) {
            ChannelParticipantIndex.ChannelEntry channel = channels.routingChannelAt(index);
            if (!channel.isRoutingActive() || channel.routingEpoch() != interactionEpoch
                || !hasTransferablePair(channel.send(), channel.storage(), channel.receive())) {
                continue;
            }
            long startNanos = System.nanoTime();
            try {
                transferEnergy(channel.send(), channel.receive(), Status.INTERACTION, interactionEpoch);
                transferEnergy(channel.storage(), channel.receive(), Status.EXTRACT, interactionEpoch);
                transferEnergy(channel.send(), channel.storage(), Status.RECEIVE, interactionEpoch);
            } catch (RuntimeException exception) {
                CirculationFlowNetworks.LOGGER.error("Machine routing failed for channel {}", channel.channelId(), exception);
            } finally {
                recordDistributedChannelTickTimeNanos(channel, System.nanoTime() - startNanos);
            }
        }
    }

    private void endPersistentRouting() {
        LocalParticipantRoutingIndex localRoutes = LocalParticipantRoutingIndex.INSTANCE;
        for (int index = 0, count = localRoutes.routingGridCount(); index < count; index++) {
            IGrid grid = localRoutes.routingGridAt(index);
            GridParticipantIndex participants = grid.getParticipantIndex();
            if (!participants.isRoutingActive()) {
                continue;
            }
            try {
                participants.endRouting(interactionEpoch);
            } catch (RuntimeException exception) {
                CirculationFlowNetworks.LOGGER.error("Failed to close machine routing for grid {}", grid.getId(), exception);
            }
        }
        ChannelParticipantIndex channels = ChannelParticipantIndex.INSTANCE;
        for (int index = 0, count = channels.routingChannelCount(); index < count; index++) {
            ChannelParticipantIndex.ChannelEntry channel = channels.routingChannelAt(index);
            if (!channel.isRoutingActive()) {
                continue;
            }
            try {
                channels.endRouting(channel.channelId(), interactionEpoch);
            } catch (RuntimeException exception) {
                CirculationFlowNetworks.LOGGER.error("Failed to close machine routing for channel {}", channel.channelId(), exception);
            }
        }
    }

    private synchronized RegistrationResult addMachine(CFNBlockEntityEx blockEntity,
                                                       @Nullable IEnergyHandlerManager excludedManager,
                                                       boolean explicitOwnerInvalidation) {
        if (blockEntity instanceof IMachineNodeBlockEntity) {
            return RegistrationResult.UNSUPPORTED;
        }
        if (!isCurrentBlockEntity(blockEntity)) {
            cancelDiscoveryThrottle(blockEntity);
            removeMachine(blockEntity);
            return RegistrationResult.STALE;
        }
        if (blockEntity.cfn_isEnergyBlacklisted()) {
            cancelDiscoveryThrottle(blockEntity);
            removeMachine(blockEntity);
            return RegistrationResult.UNSUPPORTED;
        }
        var pos = blockEntity.cfn_getBlockPos();
        long chunkCoord = ChunkCoordUtils.mergeChunkCoords(pos);

        var dim = blockEntity.cfn_getDimensionId();
        long packedPosition = packBlockPosition(pos);
        var map = scopeNode.get(dim);
        if (map == scopeNode.defaultReturnValue()) {
            cancelDiscoveryThrottle(blockEntity);
            removeMachine(blockEntity);
            return RegistrationResult.WAITING_FOR_SCOPE;
        }
        ReferenceSet<IEnergySupplyNode> set = map.get(chunkCoord);
        if (set.isEmpty()) {
            cancelDiscoveryThrottle(blockEntity);
            removeMachine(blockEntity);
            return RegistrationResult.WAITING_FOR_SCOPE;
        }

        ReferenceSet<IEnergySupplyNode> candidateNodes = new ReferenceOpenHashSet<>();
        for (var node : set) {
            if (!node.supplyScopeCheck(pos)) continue;
            if (node.isBlacklisted(blockEntity)) continue;
            candidateNodes.add(node);
        }

        if (candidateNodes.isEmpty()) {
            cancelDiscoveryThrottle(blockEntity);
            removeMachine(blockEntity);
            return RegistrationResult.WAITING_FOR_SCOPE;
        }
        if (isDiscoveryThrottled(blockEntity)) {
            return RegistrationResult.WAITING_FOR_READY;
        }
        MachineHandlerRuntime currentRuntime = blockEntity.cfn_getMachineHandlerRuntime();
        ReferenceSet<IEnergySupplyNode> currentNodes = currentRuntime == null
            ? null : machineSupplyNodes.get(blockEntity);
        boolean currentRegistrationValid = currentRuntime != null && currentRuntime.ownerManager() != null
            && currentNodes != null
            && isValidMachineRegistration(
                blockEntity, currentRuntime.ownerManager(), currentNodes, dim, packedPosition
            );
        IEnergyHandlerManager handlerManager = blockEntity.cfn_getEnergyManager(excludedManager);
        if (handlerManager == null) {
            if (!explicitOwnerInvalidation && currentRegistrationValid) {
                rememberMachinePosition(blockEntity, currentRuntime.ownerManager(), false);
                return RegistrationResult.ALREADY_REGISTERED;
            }
            if (explicitOwnerInvalidation || currentRuntime != null) {
                removeMachine(blockEntity);
            }
            rememberMachinePosition(blockEntity, null, false);
            scheduleDiscoveryThrottle(blockEntity);
            return RegistrationResult.WAITING_FOR_READY;
        }
        cancelDiscoveryThrottle(blockEntity);
        MachinePositionRecord coldRecord = machineChunkResidency.get(dim, chunkCoord, packedPosition);
        IEnergyHandlerManager selectedManager = handlerManager;
        if (!explicitOwnerInvalidation && coldRecord != null && coldRecord.ownerManager() != null) {
            selectedManager = selectRegistrationManager(
                coldRecord.ownerManager(), coldRecord.ownerPriority(), handlerManager, false
            );
        }
        if (currentRuntime != null && currentRuntime.ownerManager() != null) {
            if (currentRegistrationValid) {
                ReferenceSet<IEnergySupplyNode> validCurrentNodes = Objects.requireNonNull(
                    currentNodes, "Valid machine registration lost its supply-node index"
                );
                selectedManager = selectRegistrationManager(
                    currentRuntime.ownerManager(), currentRuntime.ownerPriority(), handlerManager,
                    explicitOwnerInvalidation
                );
                if (selectedManager == currentRuntime.ownerManager() && validCurrentNodes.equals(candidateNodes)) {
                    rememberMachinePosition(blockEntity, currentRuntime.ownerManager(), false);
                    return RegistrationResult.ALREADY_REGISTERED;
                }
            }
        }
        rememberMachinePosition(blockEntity, selectedManager, false);
        return replaceMachineRegistration(blockEntity, selectedManager, candidateNodes, dim, packedPosition);
    }

    private boolean isDiscoveryThrottled(CFNBlockEntityEx blockEntity) {
        if (blockEntity.cfn_getEnergyLastThrottleTimer() != 0) {
            if (discoveryThrottleMembership.containsKey(blockEntity)) {
                throw new IllegalStateException("Energy-budget backoff retained a discovery throttle membership");
            }
            return false;
        }
        boolean marked = blockEntity.cfn_getEnergyThrottleTimer() != 0;
        if (marked != discoveryThrottleMembership.containsKey(blockEntity)) {
            throw new IllegalStateException("Discovery throttle marker and wheel membership disagree");
        }
        return marked;
    }

    void scheduleDiscoveryThrottle(CFNBlockEntityEx blockEntity) {
        if (blockEntity.cfn_getEnergyThrottleTimer() != 0
            || blockEntity.cfn_getEnergyLastThrottleTimer() != 0) {
            throw new IllegalStateException("Cannot schedule discovery retry over an existing energy throttle");
        }
        if (discoveryThrottleMembership.containsKey(blockEntity)) {
            throw new IllegalStateException("Discovery throttle already contains the block entity");
        }
        int timer = MIN_DISCOVERY_THROTTLE_TIMER + discoveryThrottleCursor;
        discoveryThrottleCursor++;
        if (discoveryThrottleCursor == DISCOVERY_THROTTLE_TIMER_COUNT) {
            discoveryThrottleCursor = 0;
        }
        int bucketIndex = discoveryThrottleWheelCursor + timer;
        if (bucketIndex >= DISCOVERY_THROTTLE_WHEEL_SIZE) {
            bucketIndex -= DISCOVERY_THROTTLE_WHEEL_SIZE;
        }
        ObjectArrayList<CFNBlockEntityEx> bucket = discoveryThrottleWheel.get(bucketIndex);
        int entryIndex = bucket.size();
        blockEntity.cfn_setEnergyThrottleTimer(timer);
        bucket.add(blockEntity);
        discoveryThrottleMembership.put(blockEntity, packDiscoveryThrottleMembership(bucketIndex, entryIndex));
    }

    void advanceDiscoveryThrottleWheel() {
        if (!discoveryThrottleDrain.isEmpty()) {
            throw new IllegalStateException("Discovery throttle expiry drain was not cleared");
        }
        discoveryThrottleWheelCursor++;
        if (discoveryThrottleWheelCursor == DISCOVERY_THROTTLE_WHEEL_SIZE) {
            discoveryThrottleWheelCursor = 0;
        }
        ObjectArrayList<CFNBlockEntityEx> bucket = discoveryThrottleWheel.get(discoveryThrottleWheelCursor);
        int count = bucket.size();
        if (count == 0) {
            return;
        }
        for (int index = 0; index < count; index++) {
            CFNBlockEntityEx blockEntity = bucket.get(index);
            if (blockEntity.cfn_getEnergyLastThrottleTimer() != 0) {
                throw new IllegalStateException("Discovery throttle acquired an energy-budget backoff stage");
            }
            int timer = blockEntity.cfn_getEnergyThrottleTimer();
            if (timer < MIN_DISCOVERY_THROTTLE_TIMER
                || timer > CFNBlockEntityEx.MAX_STORED_ENERGY_THROTTLE_TIMER) {
                throw new IllegalStateException("Discovery throttle bucket contains an invalid marker: " + timer);
            }
            long expectedMembership = packDiscoveryThrottleMembership(discoveryThrottleWheelCursor, index);
            if (discoveryThrottleMembership.getLong(blockEntity) != expectedMembership) {
                throw new IllegalStateException("Discovery throttle bucket membership is inconsistent");
            }
        }
        discoveryThrottleDrain.addAll(bucket);
        for (int index = 0; index < count; index++) {
            CFNBlockEntityEx blockEntity = bucket.get(index);
            long removed = discoveryThrottleMembership.removeLong(blockEntity);
            long expectedMembership = packDiscoveryThrottleMembership(discoveryThrottleWheelCursor, index);
            if (removed != expectedMembership) {
                throw new IllegalStateException("Discovery throttle membership changed during expiry");
            }
            blockEntity.cfn_setEnergyThrottleTimer(0);
        }
        bucket.clear();
        try {
            for (int index = 0; index < count; index++) {
                submitLifecycle(discoveryThrottleDrain.get(index), MachineLifecyclePositionIndex.Action.DISCOVERY_RETRY);
            }
        } finally {
            discoveryThrottleDrain.clear();
        }
    }

    void cancelDiscoveryThrottle(CFNBlockEntityEx blockEntity) {
        if (blockEntity.cfn_getEnergyLastThrottleTimer() != 0) {
            if (discoveryThrottleMembership.containsKey(blockEntity)) {
                throw new IllegalStateException("Energy-budget backoff retained a discovery throttle membership");
            }
            return;
        }
        int timer = blockEntity.cfn_getEnergyThrottleTimer();
        if (timer == 0) {
            if (discoveryThrottleMembership.containsKey(blockEntity)) {
                throw new IllegalStateException("Discovery throttle membership exists without a marker");
            }
            return;
        }
        long membership = discoveryThrottleMembership.getLong(blockEntity);
        if (membership == NO_DISCOVERY_THROTTLE_MEMBERSHIP) {
            throw new IllegalStateException("Block entity has a discovery marker without a wheel membership");
        }
        removeDiscoveryThrottleMembership(blockEntity, membership);
        blockEntity.cfn_setEnergyThrottleTimer(0);
    }

    private void removeDiscoveryThrottleMembership(CFNBlockEntityEx blockEntity, long membership) {
        int bucketIndex = (int) (membership >>> 32);
        int entryIndex = (int) membership;
        if (bucketIndex < 0 || bucketIndex >= DISCOVERY_THROTTLE_WHEEL_SIZE) {
            throw new IllegalStateException("Discovery throttle membership has an invalid bucket index");
        }
        ObjectArrayList<CFNBlockEntityEx> bucket = discoveryThrottleWheel.get(bucketIndex);
        int lastIndex = bucket.size() - 1;
        if (entryIndex < 0 || entryIndex > lastIndex || bucket.get(entryIndex) != blockEntity) {
            throw new IllegalStateException("Discovery throttle membership does not point to its block entity");
        }
        CFNBlockEntityEx moved = bucket.get(lastIndex);
        if (entryIndex != lastIndex) {
            long expectedMovedMembership = packDiscoveryThrottleMembership(bucketIndex, lastIndex);
            if (discoveryThrottleMembership.getLong(moved) != expectedMovedMembership) {
                throw new IllegalStateException("Discovery throttle moved membership is inconsistent");
            }
        }
        long removed = discoveryThrottleMembership.removeLong(blockEntity);
        if (removed != membership) {
            throw new IllegalStateException("Discovery throttle membership changed during removal");
        }
        if (entryIndex != lastIndex) {
            bucket.set(entryIndex, moved);
            discoveryThrottleMembership.put(moved, packDiscoveryThrottleMembership(bucketIndex, entryIndex));
        }
        bucket.remove(lastIndex);
    }

    private static long packDiscoveryThrottleMembership(int bucketIndex, int entryIndex) {
        return ((long) bucketIndex << 32) | (entryIndex & 0xffffffffL);
    }

    private static <T> void swapRemove(ObjectArrayList<T> list, int index) {
        int lastIndex = list.size() - 1;
        if (index != lastIndex) {
            list.set(index, list.get(lastIndex));
        }
        list.remove(lastIndex);
    }

    private static IEnergyHandlerManager selectRegistrationManager(IEnergyHandlerManager currentOwner,
                                                                   int currentPriority,
                                                                   IEnergyHandlerManager candidate,
                                                                   boolean explicitOwnerInvalidation) {
        Objects.requireNonNull(currentOwner, "currentOwner");
        Objects.requireNonNull(candidate, "candidate");
        if (currentPriority != currentOwner.getPriority()) {
            throw new IllegalStateException("Machine owner priority changed after registration");
        }
        if (explicitOwnerInvalidation || candidate.getPriority() > currentPriority) {
            return candidate;
        }
        return currentOwner;
    }

    static boolean isExactOwner(@Nullable IEnergyHandlerManager currentOwner,
                                IEnergyHandlerManager expectedOwner) {
        return currentOwner == Objects.requireNonNull(expectedOwner, "expectedOwner");
    }

    private RegistrationResult replaceMachineRegistration(CFNBlockEntityEx blockEntity,
                                                          IEnergyHandlerManager handlerManager,
                                                          ReferenceSet<IEnergySupplyNode> candidateNodes,
                                                          int dimensionId,
                                                          long packedPosition) {
        MachineBindingIndex bindingIndex = MachineBindingIndex.INSTANCE;
        boolean transactionOpen = false;
        try {
            evictDisplacedMachineRoute(dimensionId, packedPosition, blockEntity);
            bindingIndex.beginTopologyTransaction();
            transactionOpen = true;
            removeMachine(blockEntity);
            MappedEnergyHandlerProvider mappedProvider = handlerManager instanceof MappedEnergyHandlerProvider provider
                ? provider : null;
            MachineHandlerRuntime runtime = new MachineHandlerRuntime(
                blockEntity, Objects.requireNonNull(handlerManager.newBlockEntityInstance(), "Energy handler manager returned null"),
                mappedProvider, handlerManager, dimensionId, packedPosition
            );
            try {
                blockEntity.cfn_installMachineHandlerRuntime(runtime);
            } catch (RuntimeException | Error exception) {
                runtime.unbind();
                throw exception;
            }
            for (IEnergySupplyNode node : candidateNodes) {
                attachSupplyNodeToMachine(blockEntity, node);
            }
            refreshMachineRoute(blockEntity);
            if (!isValidMachineRegistration(blockEntity, handlerManager, candidateNodes, dimensionId, packedPosition)) {
                throw new IllegalStateException("Machine registration transaction did not produce a complete binding");
            }
            transactionOpen = false;
            bindingIndex.endTopologyTransaction();
            for (IEnergySupplyNode node : candidateNodes) {
                notifyMachineLink(blockEntity, node, true);
            }
            return RegistrationResult.REGISTERED;
        } catch (EnergyHandlerNotReadyException exception) {
            RegistrationRollback rollback = rollbackFailedMachineRegistration(blockEntity, transactionOpen, exception);
            if (!rollback.failed()) {
                return RegistrationResult.WAITING_FOR_READY;
            }
            Throwable failure = rollback.failure();
            CirculationFlowNetworks.LOGGER.error(
                "Failed to roll back not-ready machine registration for {} at dimension {} position {} with handler manager {}",
                blockEntity.cfn_getTypeName(), dimensionId, EnergyHandlerRuntime.formatPosition(packedPosition),
                handlerManager.getClass().getName(), failure
            );
            if (failure instanceof Error error) {
                throw error;
            }
            return RegistrationResult.FAILED;
        } catch (RuntimeException exception) {
            Throwable failure = rollbackFailedMachineRegistration(blockEntity, transactionOpen, exception).failure();
            CirculationFlowNetworks.LOGGER.error(
                "Failed to register machine {} at dimension {} position {} with handler manager {}",
                blockEntity.cfn_getTypeName(), dimensionId, EnergyHandlerRuntime.formatPosition(packedPosition),
                handlerManager.getClass().getName(), failure
            );
            if (failure instanceof Error error) {
                throw error;
            }
            return RegistrationResult.FAILED;
        } catch (Error error) {
            Throwable failure = rollbackFailedMachineRegistration(blockEntity, transactionOpen, error).failure();
            CirculationFlowNetworks.LOGGER.error(
                "Fatal machine registration failure for {} at dimension {} position {} with handler manager {}",
                blockEntity.cfn_getTypeName(), dimensionId, EnergyHandlerRuntime.formatPosition(packedPosition),
                handlerManager.getClass().getName(), failure
            );
            throw (Error) failure;
        }
    }

    private RegistrationRollback rollbackFailedMachineRegistration(CFNBlockEntityEx blockEntity,
                                                                   boolean transactionOpen,
                                                                   Throwable failure) {
        boolean rollbackFailed = false;
        try {
            removeMachine(blockEntity, false);
        } catch (RuntimeException | Error rollbackException) {
            rollbackFailed = true;
            failure = aggregateFailure(failure, rollbackException);
        }
        if (transactionOpen) {
            try {
                MachineBindingIndex.INSTANCE.endTopologyTransaction();
            } catch (RuntimeException | Error rollbackException) {
                rollbackFailed = true;
                failure = aggregateFailure(failure, rollbackException);
            }
        }
        return new RegistrationRollback(failure, rollbackFailed);
    }

    public synchronized void removeMachine(CFNBlockEntityEx blockEntity) {
        removeMachine(blockEntity, true);
    }

    private void removeMachine(CFNBlockEntityEx blockEntity, boolean notify) {
        ReferenceSet<IEnergySupplyNode> supplyNodes = machineSupplyNodes.remove(blockEntity);
        ReferenceSet<INode> connectedNodes = machineGridMap.remove(blockEntity);
        MachineRoute route = machineRoutes.remove(blockEntity);
        if (route != null) {
            unindexMachineRoute(route);
        }
        MachineHandlerRuntime runtime = blockEntity.cfn_removeMachineHandlerRuntime();

        if (supplyNodes == null) {
            supplyNodes = new ReferenceOpenHashSet<>();
        }
        if (connectedNodes == null) {
            connectedNodes = new ReferenceOpenHashSet<>();
        } else {
            for (INode node : connectedNodes) {
                if (node instanceof IEnergySupplyNode supplyNode) {
                    supplyNodes.add(supplyNode);
                }
            }
        }

        CleanupAccumulator cleanup = new CleanupAccumulator();
        for (INode node : connectedNodes) {
            cleanup.run(() -> removeNodeMembership(blockEntity, node));
        }
        for (IEnergySupplyNode node : supplyNodes) {
            cleanup.run(() -> removeSupplyMembership(blockEntity, node));
            if (notify) {
                cleanup.run(() -> notifyMachineLink(blockEntity, node, false));
            }
        }
        if (route != null) {
            cleanup.add(unregisterRouteCompletely(route, null));
        }
        if (runtime != null) {
            cleanup.run(runtime::unbind);
            if (runtime.hasBindingReference()) {
                cleanup.run(runtime::unbind);
            }
        }
        cleanup.finish();
    }

    public synchronized RegistrationResult addMachineNode(CFNBlockEntityEx blockEntity) {
        if (!(blockEntity instanceof IMachineNodeBlockEntity machineNode)) {
            return RegistrationResult.UNSUPPORTED;
        }
        cancelDiscoveryThrottle(blockEntity);
        if (!isCurrentBlockEntity(blockEntity)) {
            removeMachineNode(blockEntity);
            return RegistrationResult.STALE;
        }
        var position = blockEntity.cfn_getBlockPos();
        var dimensionId = blockEntity.cfn_getDimensionId();
        long packedPosition = packBlockPosition(position);
        attachMachineNodeMemberships(blockEntity, machineNode.getNode());
        MachineHandlerRuntime runtime = blockEntity.cfn_getMachineHandlerRuntime();
        MachineRoute route = machineRoutes.get(blockEntity);
        if (runtime != null && runtime.directHandler() == machineNode.getEnergyHandler() && runtime.isBound()
            && route != null && route.isValid(runtime) && route.dimensionId == dimensionId
            && route.packedPosition == packedPosition) {
            return RegistrationResult.ALREADY_REGISTERED;
        }
        boolean transactionOpen = false;
        try {
            evictDisplacedMachineRoute(dimensionId, packedPosition, blockEntity);
            MachineBindingIndex.INSTANCE.beginTopologyTransaction();
            transactionOpen = true;
            removeMachineNode(blockEntity);
            MachineHandlerRuntime replacement = new MachineHandlerRuntime(
                blockEntity, Objects.requireNonNull(machineNode.getEnergyHandler(), "Machine node returned null handler"),
                null, null, dimensionId, packedPosition
            );
            try {
                blockEntity.cfn_installMachineHandlerRuntime(replacement);
            } catch (RuntimeException | Error exception) {
                replacement.unbind();
                throw exception;
            }
            rememberMachinePosition(blockEntity, null, true);
            refreshMachineRoute(blockEntity);
            MachineRoute replacementRoute = machineRoutes.get(blockEntity);
            if (!replacement.isBound() || replacementRoute == null || !replacementRoute.isValid(replacement)) {
                throw new IllegalStateException("Machine-node registration transaction did not produce a complete binding");
            }
            transactionOpen = false;
            MachineBindingIndex.INSTANCE.endTopologyTransaction();
            return RegistrationResult.REGISTERED;
        } catch (RuntimeException exception) {
            Throwable failure = rollbackFailedMachineRegistration(blockEntity, transactionOpen, exception).failure();
            CirculationFlowNetworks.LOGGER.error(
                "Failed to register machine node {} at dimension {} position {}",
                blockEntity.cfn_getTypeName(), dimensionId, EnergyHandlerRuntime.formatPosition(packedPosition), failure
            );
            if (failure instanceof Error error) {
                throw error;
            }
            return RegistrationResult.FAILED;
        } catch (Error error) {
            Throwable failure = rollbackFailedMachineRegistration(blockEntity, transactionOpen, error).failure();
            CirculationFlowNetworks.LOGGER.error(
                "Fatal machine-node registration failure for {} at dimension {} position {}",
                blockEntity.cfn_getTypeName(), dimensionId, EnergyHandlerRuntime.formatPosition(packedPosition), failure
            );
            throw (Error) failure;
        }
    }

    private synchronized void removeMachineNode(CFNBlockEntityEx blockEntity) {
        removeMachine(blockEntity, false);
    }

    private boolean isCurrentBlockEntity(CFNBlockEntityEx blockEntity) {
        var world = blockEntity.cfn_getWorld();
        var position = blockEntity.cfn_getBlockPos();
        if (world == null || position == null || !ChunkCoordUtils.isChunkLoaded(world, position)) {
            return false;
        }
        //~ if >=1.20 '.getTileEntity(' -> '.getBlockEntity(' {
        return world.getTileEntity(position) == blockEntity;
        //~}
    }

    private void attachSupplyNodeToMachine(CFNBlockEntityEx blockEntity, IEnergySupplyNode node) {
        ReferenceSet<IEnergySupplyNode> supplyNodes = machineSupplyNodes.computeIfAbsent(blockEntity, k -> new ReferenceOpenHashSet<>());
        if (!supplyNodes.add(node)) {
            return;
        }

        var reverse = supplyNodeMachines.get(node);
        if (reverse == supplyNodeMachines.defaultReturnValue()) {
            supplyNodeMachines.put(node, reverse = new ReferenceOpenHashSet<>());
        }
        reverse.add(blockEntity);
        attachNodeMembership(blockEntity, node);

        MachineRoute route = machineRoutes.get(blockEntity);
        if (route != null) {
            route.addSupplyNode(node);
        }

    }

    private void attachNodeMembership(CFNBlockEntityEx blockEntity, INode node) {
        machineGridMap.computeIfAbsent(blockEntity, ignored -> new ReferenceOpenHashSet<>()).add(node);
        var machines = gridMachineMap.get(node);
        if (machines == gridMachineMap.defaultReturnValue()) {
            machines = new ReferenceOpenHashSet<>();
            gridMachineMap.put(node, machines);
        }
        machines.add(blockEntity);
    }

    private void attachMachineNodeMemberships(CFNBlockEntityEx blockEntity, INode machineNode) {
        var world = blockEntity.cfn_getWorld();
        if (world == null) {
            throw new IllegalStateException("Cannot attach machine-node membership without a world");
        }
        for (INode candidate : NetworkManager.INSTANCE.getNodesCoveringPosition(
            world, blockEntity.cfn_getBlockPos()
        )) {
            if (candidate.linkScopeCheck(machineNode) != INode.LinkType.DISCONNECT) {
                attachNodeMembership(blockEntity, candidate);
            }
        }
    }

    private void notifyMachineLink(CFNBlockEntityEx blockEntity, IEnergySupplyNode node, boolean added) {
        var grid = node.getGrid();
        if (grid == null) {
            return;
        }
        var players = NodeNetworkRendering.getPlayers(grid);
        if (players == null || players.isEmpty()) {
            return;
        }
        int operation = added ? NodeNetworkRendering.MACHINE_ADD : NodeNetworkRendering.MACHINE_REMOVE;
        for (var player : players) {
            CirculationFlowNetworks.sendToPlayer(new NodeNetworkRendering(player, blockEntity, node, operation), player);
        }
    }

    private void removeSupplyMembership(CFNBlockEntityEx blockEntity, IEnergySupplyNode node) {
        var reverse = supplyNodeMachines.get(node);
        if (reverse != supplyNodeMachines.defaultReturnValue()) {
            reverse.remove(blockEntity);
            if (reverse.isEmpty()) {
                supplyNodeMachines.remove(node);
            }
        }
    }

    private void removeNodeMembership(CFNBlockEntityEx blockEntity, INode node) {
        var machines = gridMachineMap.get(node);
        if (machines != gridMachineMap.defaultReturnValue()) {
            machines.remove(blockEntity);
            if (machines.isEmpty()) {
                gridMachineMap.remove(node);
            }
        }
    }

    private boolean isValidMachineRegistration(CFNBlockEntityEx blockEntity,
                                               @Nullable IEnergyHandlerManager requiredManager,
                                               ReferenceSet<IEnergySupplyNode> expectedNodes,
                                               int dimensionId,
                                               long packedPosition) {
        MachineHandlerRuntime runtime = blockEntity.cfn_getMachineHandlerRuntime();
        MachineRoute route = machineRoutes.get(blockEntity);
        if (runtime == null || !runtime.isBound()
            || (requiredManager != null && runtime.ownerManager() != requiredManager)
            || route == null || !route.isValid(runtime)
            || route.dimensionId != dimensionId || route.packedPosition != packedPosition) {
            return false;
        }
        Long2ObjectMap<MachineRoute> positionedRoutes = machineRoutesByPosition.get(dimensionId);
        if (positionedRoutes == null || positionedRoutes.get(packedPosition) != route) {
            return false;
        }
        ReferenceSet<IEnergySupplyNode> supplyNodes = machineSupplyNodes.get(blockEntity);
        ReferenceSet<INode> legacyNodes = machineGridMap.get(blockEntity);
        if (supplyNodes == null || legacyNodes == null || supplyNodes.size() != expectedNodes.size()
            || legacyNodes.size() != expectedNodes.size() || !supplyNodes.containsAll(expectedNodes)) {
            return false;
        }
        for (IEnergySupplyNode node : expectedNodes) {
            if (!legacyNodes.contains(node) || !route.hasSupplyNode(node)
                || !supplyNodeMachines.get(node).contains(blockEntity)
                || !gridMachineMap.get(node).contains(blockEntity)) {
                return false;
            }
        }
        return true;
    }

    private void rememberMachinePosition(CFNBlockEntityEx blockEntity,
                                         @Nullable IEnergyHandlerManager ownerManager,
                                         boolean directMachineNode) {
        var world = blockEntity.cfn_getWorld();
        if (world == null) {
            throw new IllegalStateException("Cannot retain a machine position without a world");
        }
        var position = blockEntity.cfn_getBlockPos();
        machineChunkResidency.put(
            getDimensionId(world), ChunkCoordUtils.mergeChunkCoords(position),
            new MachinePositionRecord(
                getDimensionId(world), packBlockPosition(position), directMachineNode, ownerManager
            )
        );
    }

    private void rebuildMachineGrids(CFNBlockEntityEx blockEntity) {
        MachineRoute route = machineRoutes.get(blockEntity);
        if (route != null) {
            route.topologyChanged();
        }
    }

    private void refreshMachineRoute(CFNBlockEntityEx blockEntity) {
        MachineHandlerRuntime runtime = blockEntity.cfn_getMachineHandlerRuntime();
        if (runtime == null) {
            removeMachineRoute(blockEntity);
            return;
        }
        MachineRoute current = machineRoutes.get(blockEntity);
        if (current != null && current.runtime() == runtime) {
            current.topologyChanged();
            return;
        }
        removeMachineRoute(blockEntity);
        var world = blockEntity.cfn_getWorld();
        var position = blockEntity.cfn_getBlockPos();
        if (world == null || position == null) {
            CirculationFlowNetworks.LOGGER.error("Cannot register machine route without a world and position for {}",
                blockEntity.cfn_getTypeName());
            return;
        }
        MachineRoute route = new MachineRoute(
            blockEntity, runtime, getDimensionId(world), packBlockPosition(position), position
        );
        evictDisplacedMachineRoute(route.dimensionId, route.packedPosition, blockEntity);
        try {
            route.register();
            indexMachineRoute(route);
            MachineRoute previous = machineRoutes.put(blockEntity, route);
            if (previous != null && previous != route) {
                machineRoutes.put(blockEntity, previous);
                throw new IllegalStateException("Machine route was overwritten without removal");
            }
        } catch (RuntimeException | Error exception) {
            machineRoutes.remove(blockEntity, route);
            unindexMachineRoute(route);
            try {
                route.unregister();
            } catch (RuntimeException | Error rollbackException) {
                exception.addSuppressed(rollbackException);
            }
            CirculationFlowNetworks.LOGGER.error("Failed to register machine route at dimension {} position {}",
                route.dimensionId, EnergyHandlerRuntime.formatPosition(route.packedPosition), exception);
            throw exception;
        }
    }

    private void removeMachineRoute(CFNBlockEntityEx blockEntity) {
        MachineRoute route = machineRoutes.remove(blockEntity);
        if (route != null) {
            unindexMachineRoute(route);
            throwCleanupFailure(unregisterRouteCompletely(route, null));
        }
    }

    private Throwable unregisterRouteCompletely(MachineRoute route, @Nullable Throwable failure) {
        try {
            route.unregister();
        } catch (RuntimeException | Error exception) {
            failure = aggregateFailure(failure, exception);
            try {
                route.unregister();
            } catch (RuntimeException | Error retryException) {
                failure = aggregateFailure(failure, retryException);
            }
        }
        return failure;
    }

    private void indexMachineRoute(MachineRoute route) {
        Long2ObjectMap<MachineRoute> dimensionRoutes = machineRoutesByPosition.computeIfAbsent(
            route.dimensionId,
            ignored -> new Long2ObjectOpenHashMap<>()
        );
        MachineRoute previous = dimensionRoutes.put(route.packedPosition, route);
        if (previous != null && previous != route) {
            dimensionRoutes.put(route.packedPosition, previous);
            throw new IllegalStateException("Machine route position index was overwritten without displacement");
        }
    }

    private void unindexMachineRoute(MachineRoute route) {
        Long2ObjectMap<MachineRoute> dimensionRoutes = machineRoutesByPosition.get(route.dimensionId);
        if (dimensionRoutes == null || dimensionRoutes.get(route.packedPosition) != route) {
            return;
        }
        dimensionRoutes.remove(route.packedPosition);
        if (dimensionRoutes.isEmpty()) {
            machineRoutesByPosition.remove(route.dimensionId);
        }
    }

    private void evictDisplacedMachineRoute(int dimensionId, long packedPosition, CFNBlockEntityEx replacement) {
        Long2ObjectMap<MachineRoute> dimensionRoutes = machineRoutesByPosition.get(dimensionId);
        MachineRoute displaced = dimensionRoutes == null ? null : dimensionRoutes.get(packedPosition);
        if (displaced == null || displaced.tileEntity == replacement) {
            return;
        }
        removeMachine(displaced.tileEntity, true);
    }

    public void addNode(INode node) {
        if (node instanceof IEnergySupplyNode energySupplyNode) {
            int dimId = getDimensionId(node.getWorld());
            LongSet chunksCovered = indexSupplyNodeScope(energySupplyNode, dimId);
            bootstrapLoadedChunks(node.getWorld(), chunksCovered);
        }
    }

    private LongSet indexSupplyNodeScope(IEnergySupplyNode energySupplyNode, int dimensionId) {
        int nodeX = energySupplyNode.getPos().getX();
        int nodeZ = energySupplyNode.getPos().getZ();
        int range = (int) energySupplyNode.getEnergyScope();
        int minChunkX = (nodeX - range) >> 4;
        int maxChunkX = (nodeX + range) >> 4;
        int minChunkZ = (nodeZ - range) >> 4;
        int maxChunkZ = (nodeZ + range) >> 4;
        LongSet chunksCovered = new LongOpenHashSet();
        Long2ObjectMap<ReferenceSet<IEnergySupplyNode>> map = scopeNode.get(dimensionId);
        if (map == scopeNode.defaultReturnValue()) {
            map = new Long2ObjectOpenHashMap<>();
            map.defaultReturnValue(ReferenceSets.emptySet());
            scopeNode.put(dimensionId, map);
        }
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                long chunkCoordinate = ChunkCoordUtils.mergeChunkCoords(chunkX, chunkZ);
                chunksCovered.add(chunkCoordinate);
                ReferenceSet<IEnergySupplyNode> nodes = map.get(chunkCoordinate);
                if (nodes == map.defaultReturnValue()) {
                    nodes = new ReferenceOpenHashSet<>();
                    map.put(chunkCoordinate, nodes);
                }
                nodes.add(energySupplyNode);
            }
        }
        Object2ObjectMap<IEnergySupplyNode, LongSet> nodeScopeMap = nodeScope.get(dimensionId);
        if (nodeScopeMap == nodeScope.defaultReturnValue()) {
            nodeScopeMap = new Object2ObjectOpenHashMap<>();
            nodeScope.put(dimensionId, nodeScopeMap);
        }
        nodeScopeMap.put(energySupplyNode, LongSets.unmodifiable(chunksCovered));
        return chunksCovered;
    }

    //~ if >=1.20 'TileEntity' -> 'BlockEntity' {
    public boolean rescanMachinesAroundNode(IEnergySupplyNode node) {
        //~}
        long currentTick = warningTickCounter;
        long lastTick = nodeRescanTicks.getLong(node);
        if (currentTick - lastTick < NODE_RESCAN_COOLDOWN_TICKS) {
            return false;
        }
        nodeRescanTicks.put(node, currentTick);

        int nodeX = node.getPos().getX();
        int nodeZ = node.getPos().getZ();
        int range = (int) node.getEnergyScope();
        int minChunkX = (nodeX - range) >> 4;
        int maxChunkX = (nodeX + range) >> 4;
        int minChunkZ = (nodeZ - range) >> 4;
        int maxChunkZ = (nodeZ + range) >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; ++cx) {
            for (int cz = minChunkZ; cz <= maxChunkZ; ++cz) {
                //? if <1.20 {
                var chunk = node.getWorld().getChunkProvider().getLoadedChunk(cx, cz);
                if (chunk == null || chunk.isEmpty()) {
                    continue;
                }
                for (var tileEntity : chunk.getTileEntityMap().values()) {
                    onBlockEntityReady(CFNBlockEntityEx.cfn_cast(tileEntity));
                }
                //?} else {
                /*var chunk = node.getWorld().getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (var blockEntity : chunk.getBlockEntities().values()) {
                    onBlockEntityReady(CFNBlockEntityEx.cfn_cast(blockEntity));
                }
                *///?}
            }
        }
        return true;
    }

    void initGrid(Collection<NetworkManager.GridEntry> entries) {
        //~ if >=1.20 'World' -> 'Level' {
        Reference2ObjectMap<World, LongSet> bootstrapChunks = new Reference2ObjectOpenHashMap<>();
        //~}
        for (var entry : entries) {
            var dim = entry.dimId();
            if (entry.grid().getNodes().isEmpty()) continue;
            for (INode node : entry.grid().getNodes()) {
                if (!(node instanceof IEnergySupplyNode energySupplyNode)) continue;
                LongSet chunksCovered = indexSupplyNodeScope(energySupplyNode, dim);
                LongSet worldChunks = bootstrapChunks.computeIfAbsent(node.getWorld(), ignored -> new LongOpenHashSet());
                worldChunks.addAll(chunksCovered);
            }
        }
        for (var entry : bootstrapChunks.reference2ObjectEntrySet()) {
            bootstrapLoadedChunks(entry.getKey(), entry.getValue());
        }
    }

    //~ if >=1.20 'World ' -> 'Level ' {
    private void bootstrapLoadedChunks(World world, LongSet chunkCoordinates) {
        //~}
        ReferenceSet<CFNBlockEntityEx> blockEntities = new ReferenceOpenHashSet<>();
        for (long chunkCoordinate : chunkCoordinates) {
            int chunkX = (int) (chunkCoordinate >> 32);
            int chunkZ = (int) chunkCoordinate;
            //? if <1.20 {
            var chunk = world.getChunkProvider().getLoadedChunk(chunkX, chunkZ);
            if (chunk == null || chunk.isEmpty()) {
                continue;
            }
            markChunkLoaded(getDimensionId(world), chunkX, chunkZ);
            for (var blockEntity : chunk.getTileEntityMap().values()) {
                blockEntities.add(CFNBlockEntityEx.cfn_cast(blockEntity));
            }
            //?} else {
            /*var chunk = world.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                markChunkLoaded(getDimensionId(world), chunkX, chunkZ);
                for (var blockEntity : chunk.getBlockEntities().values()) {
                    blockEntities.add(CFNBlockEntityEx.cfn_cast(blockEntity));
                }
            *///?}
        }
        for (CFNBlockEntityEx blockEntity : blockEntities) {
            submitLifecycle(blockEntity, MachineLifecyclePositionIndex.Action.DISCOVERED);
        }
    }

    public void markChunkLoaded(int dimensionId, int chunkX, int chunkZ) {
        machineChunkResidency.markLoaded(dimensionId, ChunkCoordUtils.mergeChunkCoords(chunkX, chunkZ));
    }

    public void markChunkUnloaded(int dimensionId, int chunkX, int chunkZ) {
        machineChunkResidency.markUnloaded(dimensionId, ChunkCoordUtils.mergeChunkCoords(chunkX, chunkZ));
    }

    public void clearDimensionChunkResidency(int dimensionId) {
        machineChunkResidency.clearDimensionResidency(dimensionId);
    }

    private void reconcileMachineChunkResidency() {
        machineChunkResidency.tick(machineChunkTransitions);
    }

    private void dehydrateMachineChunk(int dimensionId, Collection<MachinePositionRecord> records) {
        synchronized (lifecycleLock) {
            for (MachinePositionRecord record : records) {
                lifecyclePositions.releasePosition(dimensionId, record.packedPosition());
                removeLifecycleCommandsAtPosition(lifecycleInbox, dimensionId, record.packedPosition());
                removeLifecycleCommandsAtPosition(lifecycleDrain, dimensionId, record.packedPosition());
            }
        }
        Throwable failure = null;
        Long2ObjectMap<MachineRoute> dimensionRoutes = machineRoutesByPosition.get(dimensionId);
        for (MachinePositionRecord record : records) {
            MachineRoute route = dimensionRoutes == null ? null : dimensionRoutes.get(record.packedPosition());
            if (route == null) {
                continue;
            }
            try {
                removeMachine(route.tileEntity, false);
            } catch (RuntimeException | Error exception) {
                failure = aggregateFailure(failure, exception);
            }
        }
        if (failure != null) {
            CirculationFlowNetworks.LOGGER.error(
                "Failed to fully dehydrate machines in an unloaded chunk for dimension {}", dimensionId, failure
            );
            if (failure instanceof Error error) {
                throw error;
            }
        }
    }

    private boolean reconcileLoadedMachinePosition(int dimensionId,
                                                   MachinePositionRecord record) {
        MinecraftServer server = getServer();
        if (server == null) {
            return true;
        }
        var world = resolveDimension(server, dimensionId);
        if (world == null) {
            return true;
        }
        BlockPos position = blockPosFromLong(record.packedPosition());
        //~ if >=1.20 '.getTileEntity(' -> '.getBlockEntity(' {
        var nativeBlockEntity = world.getTileEntity(position);
        //~}
        if (!(nativeBlockEntity instanceof CFNBlockEntityEx blockEntity) || blockEntity.cfn_isRemoved()) {
            return hasScopeRetention(dimensionId, position, record);
        }
        if (!(blockEntity instanceof IMachineNodeBlockEntity) && blockEntity.cfn_isEnergyBlacklisted()) {
            return hasScopeRetention(dimensionId, position, record);
        }
        submitLifecycle(world, position, blockEntity, MachineLifecyclePositionIndex.Action.DISCOVERED, null);
        return true;
    }

    private boolean hasScopeRetention(int dimensionId, BlockPos position, MachinePositionRecord record) {
        if (record.directMachineNode()) {
            return false;
        }
        Long2ObjectMap<ReferenceSet<IEnergySupplyNode>> dimensionScopes = scopeNode.get(dimensionId);
        if (dimensionScopes == scopeNode.defaultReturnValue()) {
            return false;
        }
        ReferenceSet<IEnergySupplyNode> nodes = dimensionScopes.get(ChunkCoordUtils.mergeChunkCoords(position));
        if (nodes == null || nodes.isEmpty()) {
            return false;
        }
        for (IEnergySupplyNode node : nodes) {
            if (node.supplyScopeCheck(position)) {
                return true;
            }
        }
        return false;
    }

    static void removeLifecycleCommandsAtPosition(ObjectArrayList<LifecycleCommand> commands,
                                                  int dimensionId,
                                                  long packedPosition) {
        for (int index = commands.size() - 1; index >= 0; index--) {
            LifecycleCommand command = commands.get(index);
            if (command.dimensionId() == dimensionId && command.packedPosition() == packedPosition) {
                commands.remove(index);
            }
        }
    }

    //~ if >=1.20 'World ' -> 'Level ' {
    private void submitPositionLifecycle(World world,
                                         BlockPos position,
                                         MachineLifecyclePositionIndex.Action action,
                                         @Nullable IEnergyHandlerManager excludedManager) {
        if (isClientWorld(world)) {
            return;
        }
        synchronized (lifecycleLock) {
            enqueuePositionLifecycle(getDimensionId(world), packBlockPosition(position), action, excludedManager);
        }
    }
    //~}

    private void loadPositionLifecycle(MinecraftServer server) {
        synchronized (lifecycleLock) {
            if (positionLifecycleInbox.isEmpty()) {
                return;
            }
            positionLifecycleDrain.putAll(positionLifecycleInbox);
            positionLifecycleInbox.clear();
        }
        try {
            for (PositionLifecycleCommand command : positionLifecycleDrain.values()) {
                BlockPos position = blockPosFromLong(command.packedPosition());
                long chunkCoordinate = ChunkCoordUtils.mergeChunkCoords(position);
                if (!machineChunkResidency.isMarkedLoaded(command.dimensionId(), chunkCoordinate)) {
                    continue;
                }
                var world = resolveDimension(server, command.dimensionId());
                if (world == null) {
                    retryPositionReady(command);
                    continue;
                }
                //~ if >=1.20 '.getTileEntity(' -> '.getBlockEntity(' {
                var nativeBlockEntity = world.getTileEntity(position);
                //~}
                if (!(nativeBlockEntity instanceof CFNBlockEntityEx blockEntity) || blockEntity.cfn_isRemoved()) {
                    if (command.action() == MachineLifecyclePositionIndex.Action.READY) {
                        retryPositionReady(command);
                    }
                    continue;
                }
                submitLifecycle(world, position, blockEntity, command.action(), command.excludedManager());
            }
        } finally {
            positionLifecycleDrain.clear();
        }
    }

    private void retryPositionReady(PositionLifecycleCommand command) {
        if (command.action() != MachineLifecyclePositionIndex.Action.READY) {
            return;
        }
        int nextAttempt = command.resolutionAttempts() + 1;
        if (nextAttempt >= POSITION_READY_MAX_RESOLUTION_ATTEMPTS) {
            CirculationFlowNetworks.LOGGER.warn(
                "Dropping unresolved position READY at dimension {} position {} after {} tick-pre attempts",
                command.dimensionId(), EnergyHandlerRuntime.formatPosition(command.packedPosition()), nextAttempt
            );
            return;
        }
        synchronized (lifecycleLock) {
            PositionLifecycleKey key = new PositionLifecycleKey(
                command.dimensionId(), command.packedPosition(), command.action()
            );
            positionLifecycleInbox.putIfAbsent(key, new PositionLifecycleCommand(
                command.dimensionId(), command.packedPosition(), command.action(),
                command.excludedManager(), nextAttempt
            ));
        }
    }

    private void enqueuePositionLifecycle(int dimensionId,
                                          long packedPosition,
                                          MachineLifecyclePositionIndex.Action action,
                                          @Nullable IEnergyHandlerManager excludedManager) {
        if (action == MachineLifecyclePositionIndex.Action.MANAGER_UNAVAILABLE) {
            Objects.requireNonNull(excludedManager, "Manager-unavailable position requires an exact owner");
        } else if (excludedManager != null) {
            throw new IllegalArgumentException("Only manager-unavailable position may exclude a manager");
        }
        PositionLifecycleKey key = new PositionLifecycleKey(dimensionId, packedPosition, action);
        positionLifecycleInbox.put(key, new PositionLifecycleCommand(
            dimensionId, packedPosition, action, excludedManager, 0
        ));
    }

    private void submitLifecycle(BlockEntityLifeCycleEvent event,
                                 MachineLifecyclePositionIndex.Action action) {
        submitLifecycle(event.getWorld(), event.getPos(), CFNBlockEntityEx.cfn_cast(event.getBlockEntity()), action, null);
    }

    private void submitLifecycle(CFNBlockEntityEx blockEntity,
                                 MachineLifecyclePositionIndex.Action action) {
        var world = blockEntity.cfn_getWorld();
        var position = blockEntity.cfn_getBlockPos();
        if (world == null || position == null) {
            CirculationFlowNetworks.LOGGER.error("Cannot submit {} lifecycle for {} without a world and position",
                action, blockEntity.cfn_getTypeName());
            return;
        }
        submitLifecycle(world, position, blockEntity, action, null);
    }

    //~ if >=1.20 'World ' -> 'Level ' {
    private void submitLifecycle(World world,
                                 BlockPos position,
                                 CFNBlockEntityEx blockEntity,
                                 MachineLifecyclePositionIndex.Action action,
                                 @Nullable IEnergyHandlerManager excludedManager) {
        LifecycleCommand command;
        synchronized (lifecycleLock) {
            int dimensionId = getDimensionId(world);
            long packedPosition = packBlockPosition(position);
            long generation = lifecyclePositions.submit(
                dimensionId, packedPosition, blockEntity, action
            );
            if (generation == -1L) {
                return;
            }
            command = new LifecycleCommand(
                dimensionId, packedPosition, blockEntity, generation, action, excludedManager
            );
            lifecycleInbox.add(command);
        }
    }
    //~}

    private boolean isCurrentLifecycleCommand(LifecycleCommand command) {
        synchronized (lifecycleLock) {
            return isCurrentLifecycleCommandLocked(command);
        }
    }

    private boolean isCurrentLifecycleCommandLocked(LifecycleCommand command) {
        return lifecyclePositions.isCurrent(
            command.dimensionId(), command.packedPosition(), command.blockEntity(), command.generation(), command.action()
        );
    }

    private void applyLifecycleCommand(LifecycleCommand command) {
        var blockEntity = command.blockEntity();
        if (command.action() == MachineLifecyclePositionIndex.Action.INVALIDATE) {
            cancelDiscoveryThrottle(blockEntity);
            if (blockEntity instanceof IMachineNodeBlockEntity) {
                removeMachineNode(blockEntity);
            } else {
                removeMachine(blockEntity);
            }
            synchronized (lifecycleLock) {
                lifecyclePositions.removeInvalidated(
                    command.dimensionId(), command.packedPosition(), command.blockEntity(), command.generation()
                );
            }
            long chunkCoordinate = ChunkCoordUtils.mergeChunkCoords(blockPosFromLong(command.packedPosition()));
            if (machineChunkResidency.isMarkedLoaded(command.dimensionId(), chunkCoordinate)) {
                machineChunkResidency.remove(command.dimensionId(), chunkCoordinate, command.packedPosition());
            }
            return;
        }
        var world = blockEntity.cfn_getWorld();
        var position = blockEntity.cfn_getBlockPos();
        if (world == null || position == null
            || getDimensionId(world) != command.dimensionId()
            || packBlockPosition(position) != command.packedPosition()
            || !isCurrentBlockEntity(blockEntity)) {
            return;
        }
        if (command.action() == MachineLifecyclePositionIndex.Action.DISCOVERED) {
            submitLifecycle(
                world, position, blockEntity, MachineLifecyclePositionIndex.Action.DISCOVERY_RETRY, null
            );
            return;
        }
        if (command.action() == MachineLifecyclePositionIndex.Action.READY) {
            cancelDiscoveryThrottle(blockEntity);
        }
        evictDisplacedMachineRoute(command.dimensionId(), command.packedPosition(), blockEntity);
        RegistrationResult result;
        if (blockEntity instanceof IMachineNodeBlockEntity) {
            if (command.action() == MachineLifecyclePositionIndex.Action.MANAGER_UNAVAILABLE) {
                result = RegistrationResult.ALREADY_REGISTERED;
            } else {
                result = addMachineNode(blockEntity);
            }
        } else {
            IEnergyHandlerManager excludedManager = command.excludedManager();
            if (command.action() == MachineLifecyclePositionIndex.Action.MANAGER_UNAVAILABLE) {
                if (excludedManager == null) {
                    throw new IllegalStateException("Manager-unavailable lifecycle is missing its exact owner");
                }
                MachineHandlerRuntime runtime = blockEntity.cfn_getMachineHandlerRuntime();
                if (runtime == null || !isExactOwner(runtime.ownerManager(), excludedManager)) {
                    result = RegistrationResult.ALREADY_REGISTERED;
                } else {
                    result = addMachine(blockEntity, excludedManager, true);
                }
            } else {
                if (excludedManager != null) {
                    throw new IllegalStateException("Only manager-unavailable lifecycle may exclude a manager");
                }
                result = addMachine(blockEntity, null, false);
            }
        }
        if (result == RegistrationResult.UNSUPPORTED
            && command.action() != MachineLifecyclePositionIndex.Action.DISCOVERED) {
            synchronized (lifecycleLock) {
                lifecyclePositions.releaseCurrent(
                    command.dimensionId(), command.packedPosition(), command.blockEntity(), command.generation(),
                    command.action()
                );
            }
        } else if (result == RegistrationResult.REGISTERED || result == RegistrationResult.ALREADY_REGISTERED
            || result == RegistrationResult.UNSUPPORTED) {
            synchronized (lifecycleLock) {
                lifecyclePositions.markApplied(
                    command.dimensionId(), command.packedPosition(), command.blockEntity(), command.generation(),
                    command.action()
                );
            }
        } else if (result == RegistrationResult.STALE) {
            synchronized (lifecycleLock) {
                lifecyclePositions.releaseCurrent(
                    command.dimensionId(), command.packedPosition(), command.blockEntity(), command.generation(),
                    command.action()
                );
            }
        } else {
            deferLifecycle(command);
        }
    }

    private void deferLifecycle(LifecycleCommand command) {
        synchronized (lifecycleLock) {
            lifecyclePositions.markDeferred(
                command.dimensionId(), command.packedPosition(), command.blockEntity(), command.generation(),
                command.action()
            );
        }
    }

    private void loadCache() {
        synchronized (lifecycleLock) {
            if (lifecycleDraining || lifecycleInbox.isEmpty()) {
                return;
            }
            lifecycleDraining = true;
        }
        try {
            synchronized (lifecycleLock) {
                lifecycleDrain.addAll(lifecycleInbox);
                lifecycleInbox.clear();
            }
            for (int index = 0, count = lifecycleDrain.size(); index < count; index++) {
                LifecycleCommand command = lifecycleDrain.get(index);
                if (!isCurrentLifecycleCommand(command)) {
                    continue;
                }
                try {
                    applyLifecycleCommand(command);
                } catch (RuntimeException exception) {
                    CirculationFlowNetworks.LOGGER.error(
                        "Failed to commit block entity lifecycle at dimension {} position {} generation {}",
                        command.dimensionId(), EnergyHandlerRuntime.formatPosition(command.packedPosition()),
                        command.generation(), exception
                    );
                    deferLifecycle(command);
                }
            }
        } finally {
            lifecycleDrain.clear();
            synchronized (lifecycleLock) {
                lifecycleDraining = false;
            }
        }
    }

    public void removeNode(INode node) {
        Throwable failure = null;
        ReferenceSet<CFNBlockEntityEx> connectedMachines = gridMachineMap.remove(node);
        if (connectedMachines != null) {
            for (CFNBlockEntityEx machine : connectedMachines) {
                var connectedNodes = machineGridMap.get(machine);
                if (connectedNodes != null) {
                    connectedNodes.remove(node);
                    if (connectedNodes.isEmpty()) {
                        machineGridMap.remove(machine);
                    }
                }
            }
        }
        if (node instanceof IEnergySupplyNode removedNode) {
            nodeRescanTicks.removeLong(removedNode);
            int dimId = removedNode.getDimensionId();

            var nodeScopeMap = nodeScope.get(dimId);
            if (nodeScopeMap == nodeScope.defaultReturnValue()) return;

            LongSet coveredChunks = nodeScopeMap.remove(removedNode);
            if (coveredChunks == null || coveredChunks.isEmpty()) return;

            var scopeMap = scopeNode.get(dimId);
            if (scopeMap == scopeNode.defaultReturnValue()) return;

            for (long coveredChunk : coveredChunks) {
                var set = scopeMap.get(coveredChunk);
                if (set == scopeMap.defaultReturnValue()) {
                    continue;
                }
                if (set.size() == 1) scopeMap.remove(coveredChunk);
                else set.remove(removedNode);
            }

            ReferenceSet<CFNBlockEntityEx> affectedMachines = new ReferenceOpenHashSet<>();
            var supplied = supplyNodeMachines.remove(removedNode);
            if (supplied != null) {
                affectedMachines.addAll(supplied);
            }
            for (CFNBlockEntityEx machine : affectedMachines) {
                var supplyNodes = machineSupplyNodes.get(machine);
                if (supplyNodes != null) {
                    supplyNodes.remove(removedNode);
                    if (supplyNodes.isEmpty()) {
                        machineSupplyNodes.remove(machine);
                    }
                }
                try {
                    notifyMachineLink(machine, removedNode, false);
                } catch (RuntimeException | Error exception) {
                    failure = aggregateFailure(failure, exception);
                }
                if (!(machine instanceof IMachineNodeBlockEntity) && !machineSupplyNodes.containsKey(machine)) {
                    try {
                        removeMachine(machine, false);
                    } catch (RuntimeException | Error exception) {
                        failure = aggregateFailure(failure, exception);
                    }
                    continue;
                }
                MachineRoute route = machineRoutes.get(machine);
                if (route != null) {
                    try {
                        route.removeSupplyNode(removedNode);
                    } catch (RuntimeException | Error exception) {
                        failure = aggregateFailure(failure, exception);
                    }
                }
                try {
                    rebuildMachineGrids(machine);
                } catch (RuntimeException | Error exception) {
                    failure = aggregateFailure(failure, exception);
                }
            }
            machineChunkResidency.removeIf(record -> record.dimensionId() == dimId
                && !record.directMachineNode()
                && !hasScopeRetention(dimId, blockPosFromLong(record.packedPosition()), record));
            throwCleanupFailure(failure);
            return;
        }
        throwCleanupFailure(failure);
    }

    public void onServerStop() {
        EnergyHandlerRuntime.stopBindings();
        ReferenceSet<CFNBlockEntityEx> runtimeOwners = new ReferenceOpenHashSet<>();
        for (Long2ObjectMap<MachineRoute> routes : machineRoutesByPosition.values()) {
            for (MachineRoute route : routes.values()) {
                runtimeOwners.add(route.tileEntity);
            }
        }
        for (CFNBlockEntityEx blockEntity : runtimeOwners) {
            blockEntity.cfn_removeMachineHandlerRuntime();
        }
        scopeNode.clear();
        nodeScope.clear();
        gridMachineMap.clear();
        machineGridMap.clear();
        machineSupplyNodes.clear();
        supplyNodeMachines.clear();
        machineRoutes.clear();
        machineRoutesByPosition.clear();
        machineChunkResidency.clear();
        for (CFNBlockEntityEx blockEntity : discoveryThrottleMembership.keySet()) {
            blockEntity.cfn_setEnergyThrottleTimer(0);
        }
        for (int index = 0; index < discoveryThrottleWheel.size(); index++) {
            discoveryThrottleWheel.get(index).clear();
        }
        discoveryThrottleDrain.clear();
        discoveryThrottleMembership.clear();
        discoveryThrottleCursor = 0;
        discoveryThrottleWheelCursor = 0;
        deferredInteractionQueries.clear();
        deferredInteractionQueryDrain.clear();
        machineInteractionDemandEpoch = Long.MIN_VALUE;
        nodeRescanTicks.clear();
        warningPositionsScratch.clear();
        warningSnapshots.clear();
        onlineWarningPlayerIdsScratch.clear();
        warningTickCounter = 0L;
        if (warningSessionGeneration == Long.MAX_VALUE) {
            throw new IllegalStateException("Energy warning session generation exhausted");
        }
        warningSessionGeneration++;
        synchronized (lifecycleLock) {
            lifecyclePositions.clear();
            lifecycleInbox.clear();
            lifecycleDrain.clear();
            positionLifecycleInbox.clear();
            positionLifecycleDrain.clear();
            lifecycleDraining = false;
        }
        interactionEpoch = 0L;
    }

    //~ if >=1.20 '(World ' -> '(Level ' {
    public @NotNull ReferenceSet<IEnergySupplyNode> getEnergyNodes(World world, BlockPos pos) {
        return getEnergyNodes(world, pos.getX() >> 4, pos.getZ() >> 4);
    }

    public @NotNull ReferenceSet<IEnergySupplyNode> getEnergyNodes(World world, int chunkX, int chunkZ) {
        var map = scopeNode.get(getDimensionId(world));
        return map.get(ChunkCoordUtils.mergeChunkCoords(chunkX, chunkZ));
    }
    //~}
    public @NotNull Set<CFNBlockEntityEx> getMachinesSuppliedBy(IEnergySupplyNode node) {
        return gridMachineMap.getOrDefault(node, ReferenceSets.emptySet());
    }

    /**
     * Returns the current-tick interaction totals owned by a registered machine route.
     * Transfers through every linked grid contribute to this one tracker.
     *
     * <p>Per-machine accounting is demand-driven, so this call renews the accounting window. A tracker that was
     * not being observed reports the current tick as zero and becomes accurate from the next tick onward.
     */
    @Nullable
    public synchronized Interaction getMachineInteraction(CFNBlockEntityEx blockEntity) {
        Objects.requireNonNull(blockEntity, "blockEntity");
        renewMachineInteractionDemand();
        MachineRoute route = machineRoutes.get(blockEntity);
        return route != null && route.isQueryable() ? route.interaction : null;
    }

    /**
     * Keeps per-machine interaction accounting alive for {@link #MACHINE_INTERACTION_DEMAND_TICKS} more ticks.
     *
     * <p>Reading a machine-scoped tracker renews the window automatically. A consumer whose read is a one-shot
     * action rather than a poll should call this while the read is still upcoming — otherwise the first read
     * after a quiet period reports the current tick as zero.
     */
    public void renewMachineInteractionDemand() {
        long epoch = interactionEpoch;
        long renewed = epoch > Long.MAX_VALUE - MACHINE_INTERACTION_DEMAND_TICKS
            ? Long.MAX_VALUE
            : epoch + MACHINE_INTERACTION_DEMAND_TICKS;
        if (renewed > machineInteractionDemandEpoch) {
            machineInteractionDemandEpoch = renewed;
        }
    }

    /** Reports whether any consumer read a machine-scoped tracker recently enough to require accounting. */
    private boolean isMachineInteractionDemanded() {
        return interactionEpoch <= machineInteractionDemandEpoch;
    }

    /**
     * Returns one consistent priority and binding-identity snapshot for a currently connected machine.
     * The target must still own its registered, bound and routed runtime and at least one active grid slot.
     *
     * @param blockEntity exact block-entity identity being inspected
     * @return current priority state, or {@code null} when the target is not currently configurable
     */
    @Nullable
    public synchronized ConnectedMachinePriority getConnectedMachinePriority(CFNBlockEntityEx blockEntity) {
        Objects.requireNonNull(blockEntity, "blockEntity");
        MachineRoute route = machineRoutes.get(blockEntity);
        if (route == null || !route.isPriorityConfigurable(blockEntity)) {
            return null;
        }
        return new ConnectedMachinePriority(
            blockEntity.cfn_getEnergyPriority(), route.runtime.bindingGeneration()
        );
    }

    /**
     * Queues a runtime priority update and then persists it when the submitted binding identity is still current.
     * The generation check, exact block-entity identity check and route/grid validation execute in this one synchronized
     * critical section so a stale GUI cannot modify a replacement machine at the same position.
     *
     * @param blockEntity exact current block-entity identity resolved by the server
     * @param expectedGeneration binding generation returned by {@link #getConnectedMachinePriority(CFNBlockEntityEx)}
     * @param priority complete signed integer priority to apply
     * @return {@code true} when the matching current binding accepted the update; otherwise {@code false}
     */
    public synchronized boolean setConnectedMachinePriority(CFNBlockEntityEx blockEntity,
                                                            long expectedGeneration,
                                                            int priority) {
        Objects.requireNonNull(blockEntity, "blockEntity");
        MachineRoute route = machineRoutes.get(blockEntity);
        if (route == null || !route.isPriorityConfigurable(blockEntity)
            || route.runtime.bindingGeneration() != expectedGeneration) {
            return false;
        }
        if (blockEntity.cfn_getEnergyPriority() == priority) {
            return true;
        }
        MachineBindingIndex.INSTANCE.updateMachinePriority(
            route.dimensionId, route.packedPosition, route, priority
        );
        blockEntity.cfn_setEnergyPriority(priority);
        return true;
    }

    /** Immutable GUI authority snapshot for one connected machine binding. */
    public static final class ConnectedMachinePriority {
        private final int priority;
        private final long bindingGeneration;

        private ConnectedMachinePriority(int priority, long bindingGeneration) {
            this.priority = priority;
            this.bindingGeneration = bindingGeneration;
        }

        /** Returns the persistent full-range signed machine priority. */
        public int priority() {
            return priority;
        }

        /** Returns the runtime binding identity that must accompany a later priority submission. */
        public long bindingGeneration() {
            return bindingGeneration;
        }
    }

    @Nullable
    private static HubNode.HubMetadata getHubMetadata(@Nullable IGrid grid) {
        if (grid == null) {
            return null;
        }
        IHubNode hubNode = grid.getHubNode();
        return hubNode != null ? hubNode.getHubData() : null;
    }

    static void recordGridTickTimeNanos(@Nullable IGrid grid, long durationNanos) {
        if (grid == null || durationNanos <= 0L) {
            return;
        }
        grid.getInteraction().prepareForTick(INSTANCE.interactionEpoch);
        grid.getInteraction().recordGridTickTimeNanos(durationNanos);
    }

    static void recordDistributedChannelTickTimeNanos(ChannelParticipantIndex.ChannelEntry channel,
                                                      long durationNanos) {
        if (durationNanos <= 0L) {
            return;
        }
        int gridCount = channel.gridCount();
        if (gridCount == 0) {
            return;
        }
        long baseShare = durationNanos / gridCount;
        long remainder = durationNanos % gridCount;
        for (int index = 0; index < gridCount; index++) {
            long share = baseShare + (remainder-- > 0L ? 1L : 0L);
            recordGridTickTimeNanos(channel.gridAt(index), share);
        }
    }

    @Nullable
    private IEnergyHandler.EnergyType getOverride(@Nullable EnergyTypeOverrideManager overrideManager,
                                                  int dimId,
                                                  long posLong) {
        if (overrideManager == null || overrideManager.isEmpty()) {
            return null;
        }
        Long2ObjectMap<IEnergyHandler.EnergyType> dimOverrides = overrideManager.getOverridesForDim(dimId);
        return dimOverrides == null ? null : dimOverrides.get(posLong);
    }

    private void clearWarningPositionsScratch() {
        for (var positions : warningPositionsScratch.values()) {
            positions.clear();
        }
    }

    /**
     * Rebuilds the chunk window that currently has a warning observer. Sampling a warning costs a real handler
     * read, so the render-distance test that {@link #sendWarningsToNearbyPlayers} applies to finished snapshots
     * is hoisted in front of sampling: a machine no player is near is never probed at all.
     */
    private void refreshWarningObserverChunks(MinecraftServer server) {
        for (var chunks : warningObserverChunksScratch.values()) {
            chunks.clear();
        }
        for (var player : server.getPlayerList().getPlayers()) {
            String dimensionKey = getPlayerDimensionKey(player);
            LongSet chunks = warningObserverChunksScratch.get(dimensionKey);
            if (chunks == null) {
                chunks = new LongOpenHashSet();
                warningObserverChunksScratch.put(dimensionKey, chunks);
            }
            BlockPos position = getPlayerBlockPos(player);
            int centerChunkX = ChunkCoordUtils.getChunkX(position);
            int centerChunkZ = ChunkCoordUtils.getChunkZ(position);
            for (int offsetX = -WARNING_OBSERVER_CHUNK_RADIUS; offsetX <= WARNING_OBSERVER_CHUNK_RADIUS; offsetX++) {
                for (int offsetZ = -WARNING_OBSERVER_CHUNK_RADIUS; offsetZ <= WARNING_OBSERVER_CHUNK_RADIUS; offsetZ++) {
                    chunks.add(ChunkCoordUtils.mergeChunkCoords(centerChunkX + offsetX, centerChunkZ + offsetZ));
                }
            }
        }
    }

    private void settleAccountWarnings() {
        LocalParticipantRoutingIndex localRoutes = LocalParticipantRoutingIndex.INSTANCE;
        for (int index = 0, count = localRoutes.routingGridCount(); index < count; index++) {
            collectWarnings(localRoutes.routingGridAt(index).getParticipantIndex().receive());
        }
        ChannelParticipantIndex channels = ChannelParticipantIndex.INSTANCE;
        for (int index = 0, count = channels.routingChannelCount(); index < count; index++) {
            collectWarnings(channels.routingChannelAt(index).receive());
        }
    }

    private void prepareAccountWarnings() {
        LocalParticipantRoutingIndex localRoutes = LocalParticipantRoutingIndex.INSTANCE;
        for (int index = 0, count = localRoutes.routingGridCount(); index < count; index++) {
            prepareWarnings(localRoutes.routingGridAt(index).getParticipantIndex().receive());
        }
        ChannelParticipantIndex channels = ChannelParticipantIndex.INSTANCE;
        for (int index = 0, count = channels.routingChannelCount(); index < count; index++) {
            prepareWarnings(channels.routingChannelAt(index).receive());
        }
    }

    private void prepareWarnings(PriorityRoleIndex receive) {
        warningCursor.prepare(receive);
        try {
            for (MachineTransferSlot slot = warningCursor.next(); slot != null; slot = warningCursor.next()) {
                slot.prepareWarning(interactionEpoch, warningObserverChunksScratch);
            }
        } finally {
            warningCursor.close();
        }
    }

    private void collectWarnings(PriorityRoleIndex receive) {
        warningCursor.prepare(receive);
        try {
            for (MachineTransferSlot slot = warningCursor.next(); slot != null; slot = warningCursor.next()) {
                slot.collectWarning(warningPositionsScratch, interactionEpoch, warningObserverChunksScratch);
            }
        } finally {
            warningCursor.close();
        }
    }

    private void sendWarningsToNearbyPlayers(MinecraftServer server, Object2ObjectMap<String, LongSet> warningPositions) {
        onlineWarningPlayerIdsScratch.clear();
        for (var player : server.getPlayerList().getPlayers()) {
            UUID playerId = getPlayerId(player);
            onlineWarningPlayerIdsScratch.add(playerId);
            String dimensionKey = getPlayerDimensionKey(player);
            LongSet dimWarnings = warningPositions.get(dimensionKey);
            if (dimWarnings == null) {
                dimWarnings = LongSets.EMPTY_SET;
            }
            try {
                warningSnapshots.synchronize(
                    playerId,
                    dimensionKey,
                    warningTickCounter,
                    dimWarnings,
                    posLong -> getPlayerDistanceSq(player, blockPosFromLong(posLong)) <= WARNING_RENDER_DISTANCE_SQ,
                    (snapshotDimensionKey, revision, positions) -> sendWarningSnapshot(
                        player, snapshotDimensionKey, revision, positions)
                );
            } catch (RuntimeException exception) {
                CirculationFlowNetworks.LOGGER.error("Failed to update energy warnings for player {}", playerId, exception);
            }
        }
        warningSnapshots.retainPlayers(onlineWarningPlayerIdsScratch);
    }

    private void sendWarningSnapshot(Object player,
                                     String dimensionKey,
                                     long revision,
                                     LongCollection positions) {
        //? if <1.20 {
        CirculationFlowNetworks.sendToPlayer(
            new EnergyWarningRendering(dimensionKey, warningSessionGeneration, revision, positions),
            (EntityPlayerMP) player);
        //?} else {
        /*CirculationFlowNetworks.sendToPlayer(
            new EnergyWarningRendering(dimensionKey, warningSessionGeneration, revision, positions),
            (ServerPlayer) player);
         *///?}
    }
    enum Status {
        EXTRACT,
        INTERACTION,
        RECEIVE;

        void interaction(EnergyAmount value,
                         @Nullable MachineTransferSlot sender,
                         @Nullable MachineTransferSlot receiver,
                         long epoch) {
            Objects.requireNonNull(value, "value");
            if (!value.isPositive()) {
                throw new IllegalArgumentException("Settled interaction value must be positive");
            }
            if (INSTANCE.isMachineInteractionDemanded()) {
                if (sender != null) {
                    sender.recordMachineOutput(value, epoch);
                }
                if (receiver != null) {
                    receiver.recordMachineInput(value, epoch);
                }
            }
            switch (this) {
                case INTERACTION -> {
                    if (sender != null) {
                        sender.recordGridOutput(value, epoch);
                    }
                    if (receiver != null) {
                        receiver.recordGridInput(value, epoch);
                    }
                }
                case EXTRACT -> {
                    if (sender != null) {
                        sender.recordGridOutput(value, epoch);
                    }
                }
                case RECEIVE -> {
                    if (receiver != null) {
                        receiver.recordGridInput(value, epoch);
                    }
                }
            }
        }

    }

    private static final class PositionLifecycleCommand {
        private final int dimensionId;
        private final long packedPosition;
        private final MachineLifecyclePositionIndex.Action action;
        @Nullable
        private final IEnergyHandlerManager excludedManager;
        private final int resolutionAttempts;

        private PositionLifecycleCommand(int dimensionId,
                                         long packedPosition,
                                         MachineLifecyclePositionIndex.Action action,
                                         @Nullable IEnergyHandlerManager excludedManager,
                                         int resolutionAttempts) {
            this.dimensionId = dimensionId;
            this.packedPosition = packedPosition;
            this.action = Objects.requireNonNull(action, "action");
            this.excludedManager = excludedManager;
            if (resolutionAttempts < 0) {
                throw new IllegalArgumentException("resolutionAttempts must not be negative");
            }
            this.resolutionAttempts = resolutionAttempts;
        }

        private int dimensionId() {
            return dimensionId;
        }

        private long packedPosition() {
            return packedPosition;
        }

        private MachineLifecyclePositionIndex.Action action() {
            return action;
        }

        @Nullable
        private IEnergyHandlerManager excludedManager() {
            return excludedManager;
        }

        private int resolutionAttempts() {
            return resolutionAttempts;
        }
    }

    private static final class PositionLifecycleKey {
        private final int dimensionId;
        private final long packedPosition;
        private final MachineLifecyclePositionIndex.Action action;

        private PositionLifecycleKey(int dimensionId,
                                     long packedPosition,
                                     MachineLifecyclePositionIndex.Action action) {
            this.dimensionId = dimensionId;
            this.packedPosition = packedPosition;
            this.action = Objects.requireNonNull(action, "action");
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof PositionLifecycleKey key)) {
                return false;
            }
            return dimensionId == key.dimensionId
                && packedPosition == key.packedPosition
                && action == key.action;
        }

        @Override
        public int hashCode() {
            int result = Integer.hashCode(dimensionId);
            result = 31 * result + Long.hashCode(packedPosition);
            return 31 * result + action.hashCode();
        }
    }

    private static final class MachinePositionRecord implements MachineChunkResidencyIndex.PositionRecord {
        private final int dimensionId;
        private final long packedPosition;
        private final boolean directMachineNode;
        @Nullable
        private final IEnergyHandlerManager ownerManager;
        private final int ownerPriority;

        private MachinePositionRecord(int dimensionId,
                                      long packedPosition,
                                      boolean directMachineNode,
                                      @Nullable IEnergyHandlerManager ownerManager) {
            this.dimensionId = dimensionId;
            this.packedPosition = packedPosition;
            this.directMachineNode = directMachineNode;
            this.ownerManager = ownerManager;
            ownerPriority = ownerManager == null ? Integer.MIN_VALUE : ownerManager.getPriority();
        }

        @Override
        public long packedPosition() {
            return packedPosition;
        }

        private int dimensionId() {
            return dimensionId;
        }

        private boolean directMachineNode() {
            return directMachineNode;
        }

        @Nullable
        private IEnergyHandlerManager ownerManager() {
            return ownerManager;
        }

        private int ownerPriority() {
            return ownerPriority;
        }
    }

    static final class LifecycleCommand {

        private final int dimensionId;
        private final long packedPosition;
        private final CFNBlockEntityEx blockEntity;
        private final long generation;
        private final MachineLifecyclePositionIndex.Action action;
        @Nullable
        private final IEnergyHandlerManager excludedManager;

        LifecycleCommand(int dimensionId, long packedPosition, CFNBlockEntityEx blockEntity,
                         long generation,
                         MachineLifecyclePositionIndex.Action action,
                         @Nullable IEnergyHandlerManager excludedManager) {
            this.dimensionId = dimensionId;
            this.packedPosition = packedPosition;
            this.blockEntity = blockEntity;
            this.generation = generation;
            this.action = action;
            this.excludedManager = excludedManager;
        }

        private int dimensionId() {
            return dimensionId;
        }

        private long packedPosition() {
            return packedPosition;
        }

        CFNBlockEntityEx blockEntity() {
            return blockEntity;
        }

        private long generation() {
            return generation;
        }

        private MachineLifecyclePositionIndex.Action action() {
            return action;
        }

        @Nullable
        private IEnergyHandlerManager excludedManager() {
            return excludedManager;
        }
    }

    public static final class MachineHandlerRuntime {
        private final CFNBlockEntityEx tileEntity;
        private final IEnergyHandler directHandler;
        @Nullable
        private final IEnergyHandlerManager ownerManager;
        private final int ownerPriority;
        private final long bindingGeneration;
        @Nullable
        private MachineBindingIndex.Binding binding;

        private MachineHandlerRuntime(CFNBlockEntityEx tileEntity,
                                      IEnergyHandler directHandler,
                                      @Nullable MappedEnergyHandlerProvider mappedProvider,
                                      @Nullable IEnergyHandlerManager ownerManager,
                                      int dimensionId,
                                      long packedPosition) {
            this.tileEntity = tileEntity;
            this.directHandler = directHandler;
            this.ownerManager = ownerManager;
            ownerPriority = ownerManager == null ? Integer.MIN_VALUE : ownerManager.getPriority();
            bindingGeneration = EnergyHandlerRuntime.nextBindingGeneration();
            EnergyHandlerRuntime.FailureContext failureContext =
                EnergyHandlerRuntime.machineContext(dimensionId, packedPosition, bindingGeneration);
            binding = EnergyHandlerRuntime.bindBlockEntity(tileEntity, directHandler, mappedProvider, failureContext);
        }

        private IEnergyHandler directHandler() {
            return directHandler;
        }

        @Nullable
        private IEnergyHandlerManager ownerManager() {
            return ownerManager;
        }

        private int ownerPriority() {
            return ownerPriority;
        }

        private boolean isBound() {
            return binding != null && binding.isActive()
                && MachineBindingIndex.INSTANCE.binding(tileEntity) == binding;
        }

        private long bindingGeneration() {
            return bindingGeneration;
        }

        private boolean hasBindingReference() {
            return binding != null;
        }

        private HandlerBindingPolicy policy() {
            if (binding == null) {
                return HandlerBindingPolicy.DEFAULT;
            }
            return EnergyHandlerRuntime.bindingPolicy(binding);
        }

        @Nullable
        private MachineTransferAccount account(EnergyHandlerRuntime.FailureContext context) {
            if (binding == null) {
                return null;
            }
            return EnergyHandlerRuntime.account(binding, directHandler, context);
        }

        private EnergyHandlerRuntime.FailureContext failureContext(int dimensionId, long packedPosition) {
            return EnergyHandlerRuntime.machineContext(dimensionId, packedPosition, bindingGeneration);
        }

        private void unbind() {
            if (binding != null) {
                EnergyHandlerRuntime.unbindBlockEntity(tileEntity);
                if (MachineBindingIndex.INSTANCE.binding(tileEntity) != null) {
                    throw new IllegalStateException("Machine handler binding remained indexed after unbind");
                }
                binding = null;
            }
        }
    }

    private final class MachineRoute implements MachineBindingIndex.Route {
        private final CFNBlockEntityEx tileEntity;
        private final MachineHandlerRuntime runtime;
        private final int dimensionId;
        private final String dimensionKey;
        private final long packedPosition;
        private final BlockPos position;
        private final boolean machineNode;
        private final Interaction interaction = new Interaction(true);
        private final Reference2ObjectMap<IGrid, MachineTransferSlot> slots = new Reference2ObjectOpenHashMap<>();
        private final Reference2ObjectMap<IEnergySupplyNode, SupplyMachineEdge> supplyEdges =
            new Reference2ObjectOpenHashMap<>();
        private final Reference2IntMap<IGrid> effectiveGridRefCounts = new Reference2IntOpenHashMap<>();
        @Nullable
        private MachineTransferAccount account;
        @Nullable
        private IGrid machineNodeGrid;
        private int priority;
        private boolean registered;
        private boolean routed;
        private boolean routeEnabled;
        private boolean disposed;
        private boolean disposing;

        private MachineRoute(CFNBlockEntityEx tileEntity,
                             MachineHandlerRuntime runtime,
                             int dimensionId,
                             long packedPosition,
                             BlockPos position) {
            var world = tileEntity.cfn_getWorld();
            if (world == null) {
                throw new IllegalStateException("Cannot create a machine route without a world");
            }
            this.tileEntity = tileEntity;
            this.runtime = runtime;
            this.dimensionId = dimensionId;
            this.dimensionKey = getDimensionKey(world);
            this.packedPosition = packedPosition;
            this.position = position;
            this.priority = tileEntity.cfn_getEnergyPriority();
            this.machineNode = tileEntity instanceof IMachineNodeBlockEntity;
            effectiveGridRefCounts.defaultReturnValue(0);
        }

        private MachineHandlerRuntime runtime() {
            return runtime;
        }

        private boolean isValid(MachineHandlerRuntime expectedRuntime) {
            return runtime == expectedRuntime && registered && !disposed;
        }

        private boolean isQueryable() {
            return registered && routed && !disposed && runtime.isBound()
                && tileEntity.cfn_getMachineHandlerRuntime() == runtime;
        }

        private boolean isPriorityConfigurable(CFNBlockEntityEx expectedBlockEntity) {
            return tileEntity == expectedBlockEntity && isQueryable() && routeEnabled && !slots.isEmpty();
        }

        private boolean hasSupplyNode(IEnergySupplyNode node) {
            return supplyEdges.containsKey(node);
        }

        private void register() {
            if (registered || disposed) {
                throw new IllegalStateException("Machine route cannot be registered in its current state");
            }
            MachineBindingIndex.INSTANCE.registerMachineRoute(dimensionId, packedPosition, this);
            MachineBindingIndex.INSTANCE.updateMachinePriority(dimensionId, packedPosition, this, priority);
            registered = true;
            if (!machineNode) {
                ReferenceSet<IEnergySupplyNode> supplyNodes = machineSupplyNodes.get(tileEntity);
                if (supplyNodes != null) {
                    for (IEnergySupplyNode node : supplyNodes) {
                        addSupplyNode(node);
                    }
                }
            }
        }

        private void addSupplyNode(IEnergySupplyNode node) {
            if (machineNode) {
                throw new IllegalStateException("Machine-node routes cannot own supply edges");
            }
            if (disposed || supplyEdges.containsKey(node)) {
                return;
            }
            SupplyMachineEdge edge = new SupplyMachineEdge(this, node);
            MachineBindingIndex.RouteHandle handle = MachineBindingIndex.INSTANCE.registerRoute(node, edge);
            edge.attach(handle);
            SupplyMachineEdge previous = supplyEdges.put(node, edge);
            if (previous != null) {
                supplyEdges.put(node, previous);
                edge.unregister();
                throw new IllegalStateException("Supply-machine edge was overwritten without removal");
            }
        }

        private void removeSupplyNode(IEnergySupplyNode node) {
            SupplyMachineEdge edge = supplyEdges.remove(node);
            if (edge != null) {
                Throwable failure = null;
                try {
                    edge.unregister();
                } catch (RuntimeException | Error exception) {
                    failure = aggregateFailure(failure, exception);
                    try {
                        edge.unregister();
                    } catch (RuntimeException | Error retryException) {
                        failure = aggregateFailure(failure, retryException);
                    }
                }
                throwCleanupFailure(failure);
            }
        }

        private void topologyChanged() {
            if (!registered || disposed || !routed) {
                return;
            }
            if (machineNode) {
                refreshMachineNodeGrid();
            }
            rebuildRouteConfiguration();
        }

        private void unregister() {
            if (!registered) {
                if (!disposed) {
                    close();
                }
                return;
            }
            MachineBindingIndex.INSTANCE.unregisterMachineRoute(dimensionId, packedPosition, this);
            registered = false;
        }

        @Override
        public void close() {
            if (disposed) {
                return;
            }
            if (disposing) {
                throw new IllegalStateException("Machine route disposal is already in progress");
            }
            disposing = true;
            Throwable failure = null;
            try {
                SupplyMachineEdge[] edgeSnapshot = supplyEdges.values().toArray(new SupplyMachineEdge[0]);
                supplyEdges.clear();
                for (SupplyMachineEdge edge : edgeSnapshot) {
                    try {
                        edge.unregister();
                    } catch (RuntimeException | Error exception) {
                        failure = aggregateFailure(failure, exception);
                        try {
                            edge.unregister();
                        } catch (RuntimeException | Error retryException) {
                            failure = aggregateFailure(failure, retryException);
                        }
                    }
                }
                failure = disposeSlots(failure);
                routed = false;
                routeEnabled = false;
                machineNodeGrid = null;
                effectiveGridRefCounts.clear();
                account = null;
                disposed = true;
                if (failure != null) {
                    CirculationFlowNetworks.LOGGER.error("Failed to dispose machine route at dimension {} position {}",
                        dimensionId, EnergyHandlerRuntime.formatPosition(packedPosition), failure);
                    throwCleanupFailure(failure);
                }
            } finally {
                disposing = false;
            }
        }

        @Override
        public void revoke() {
            routed = false;
            routeEnabled = false;
            account = null;
            throwCleanupFailure(disposeSlots(null));
        }

        @Override
        public void refresh() {
            if (disposed) {
                return;
            }
            routed = true;
            if (machineNode) {
                refreshMachineNodeGrid();
            }
            rebuildRouteConfiguration();
        }

        @Override
        public void updateMachinePriority(int priority) {
            MachineTransferSlot[] snapshot = slots.values().toArray(new MachineTransferSlot[0]);
            GridParticipantIndex.MoveRollback[] rollbacks = new GridParticipantIndex.MoveRollback[snapshot.length];
            try {
                for (MachineTransferSlot slot : snapshot) {
                    slot.validatePriority(this.priority);
                }
                for (int index = 0; index < snapshot.length; index++) {
                    rollbacks[index] = snapshot[index].updatePriority(priority);
                }
            } catch (RuntimeException exception) {
                boolean rollbackFailed = exception instanceof GridParticipantIndex.MoveRollbackException;
                for (int index = rollbacks.length - 1; index >= 0; index--) {
                    GridParticipantIndex.MoveRollback rollback = rollbacks[index];
                    if (rollback == null) {
                        continue;
                    }
                    try {
                        rollback.owner().rollbackMove(rollback);
                    } catch (RuntimeException rollbackException) {
                        exception.addSuppressed(rollbackException);
                        rollbackFailed = true;
                    }
                }
                if (rollbackFailed) {
                    throw new MachineBindingIndex.PriorityRollbackException(exception);
                }
                throw exception;
            }
            this.priority = priority;
        }

        private void refreshMachineNodeGrid() {
            IMachineNodeBlockEntity machineNodeEntity = (IMachineNodeBlockEntity) tileEntity;
            INode node = machineNodeEntity.getNode();
            IGrid currentGrid = node != null && node.isActive() ? node.getGrid() : null;
            if (machineNodeGrid == currentGrid) {
                return;
            }
            moveGridContribution(machineNodeGrid, currentGrid);
            machineNodeGrid = currentGrid;
        }

        public void moveGridContribution(@Nullable IGrid oldGrid, @Nullable IGrid newGrid) {
            if (oldGrid == newGrid) {
                return;
            }
            if (newGrid != null) {
                incrementGridRefCount(newGrid);
            }
            try {
                if (oldGrid != null) {
                    decrementGridRefCount(oldGrid);
                }
            } catch (RuntimeException | Error exception) {
                if (newGrid != null) {
                    try {
                        decrementGridRefCount(newGrid);
                    } catch (RuntimeException | Error rollbackException) {
                        exception.addSuppressed(rollbackException);
                    }
                }
                throw exception;
            }
        }

        private void incrementGridRefCount(IGrid grid) {
            int current = effectiveGridRefCounts.getInt(grid);
            if (current == Integer.MAX_VALUE) {
                throw new IllegalStateException("Effective machine-grid reference count overflow");
            }
            if (current == 0 && routeEnabled) {
                MachineTransferSlot slot = new MachineTransferSlot(tileEntity, packedPosition, interaction);
                try {
                    slot.attachGridTracking(grid, grid.getInteraction());
                    configureSlot(grid, slot);
                    slots.put(grid, slot);
                } catch (RuntimeException | Error exception) {
                    try {
                        slot.detachGrid();
                    } catch (RuntimeException | Error rollbackException) {
                        exception.addSuppressed(rollbackException);
                    }
                    throw exception;
                }
            }
            effectiveGridRefCounts.put(grid, current + 1);
        }

        private void decrementGridRefCount(IGrid grid) {
            int current = effectiveGridRefCounts.getInt(grid);
            if (current <= 0) {
                throw new IllegalStateException("Effective machine-grid reference count underflow");
            }
            if (current > 1) {
                effectiveGridRefCounts.put(grid, current - 1);
                return;
            }
            MachineTransferSlot slot = slots.get(grid);
            if (slot != null) {
                slot.detachGrid();
                slots.remove(grid, slot);
            }
            effectiveGridRefCounts.removeInt(grid);
        }

        private void rebuildRouteConfiguration() {
            var world = tileEntity.cfn_getWorld();
            routeEnabled = routed && world != null
                && ChunkCoordUtils.isChunkLoaded(world, ChunkCoordUtils.getChunkX(position), ChunkCoordUtils.getChunkZ(position))
                && !CirculationShielderManager.INSTANCE.isBlockedByShielder(dimensionId, position);
            if (!routeEnabled) {
                account = null;
                clearSlots();
                return;
            }
            EnergyHandlerRuntime.FailureContext failureContext = runtime.failureContext(dimensionId, packedPosition);
            account = runtime.account(failureContext);
            if (account != null && !MachineBindingIndex.INSTANCE.isEnergyThrottled(tileEntity)) {
                MachineBindingIndex.INSTANCE.beginEnergyRoleEvaluation(tileEntity);
            }
            for (var entry : effectiveGridRefCounts.reference2IntEntrySet()) {
                if (entry.getIntValue() <= 0 || slots.containsKey(entry.getKey())) {
                    continue;
                }
                IGrid grid = entry.getKey();
                MachineTransferSlot slot = new MachineTransferSlot(tileEntity, packedPosition, interaction);
                slot.attachGridTracking(grid, grid.getInteraction());
                MachineTransferSlot previous = slots.put(grid, slot);
                if (previous != null) {
                    slots.put(grid, previous);
                    slot.detachGrid();
                    throw new IllegalStateException("Machine grid slot was overwritten without removal");
                }
            }
            for (var entry : slots.reference2ObjectEntrySet()) {
                configureSlot(entry.getKey(), entry.getValue());
            }
        }

        private void configureSlot(IGrid grid, MachineTransferSlot slot) {
            slot.requireGridTracking(grid, grid.getInteraction());
            if (account == null) {
                slot.suspendTransfer();
                return;
            }
            if (MachineBindingIndex.INSTANCE.isEnergyThrottled(tileEntity)) {
                MachineBindingIndex.INSTANCE.markEnergyRouteRetry(tileEntity);
                slot.suspendTransfer();
                return;
            }
            EnergyHandlerRuntime.FailureContext failureContext = runtime.failureContext(dimensionId, packedPosition);
            HubNode.HubMetadata hubMetadata = getHubMetadata(grid);
            HandlerBindingPolicy policy = runtime.policy();
            IEnergyHandler.EnergyType override = machineNode
                ? null
                : getOverride(EnergyTypeOverrideManager.get(), dimensionId, packedPosition);
            IEnergyHandler.EnergyType role = slot.resolveRole(
                runtime.directHandler(), policy, hubMetadata, override, failureContext
            );
            if (role == IEnergyHandler.EnergyType.INVALID) {
                MachineBindingIndex.INSTANCE.markEnergyRoleFailure(tileEntity);
                slot.suspendTransfer();
                return;
            }
            slot.configure(grid, account, policy, hubMetadata, grid.getInteraction(), role, priority, failureContext,
                role == IEnergyHandler.EnergyType.RECEIVE ? dimensionKey : null,
                role == IEnergyHandler.EnergyType.RECEIVE ? packedPosition : Long.MIN_VALUE);
        }

        private void clearSlots() {
            for (MachineTransferSlot slot : slots.values()) {
                slot.detachGrid();
            }
            slots.clear();
        }

        private Throwable disposeSlots(@Nullable Throwable failure) {
            int snapshotSize = slots.size();
            IGrid[] gridSnapshot = new IGrid[snapshotSize];
            MachineTransferSlot[] slotSnapshot = new MachineTransferSlot[snapshotSize];
            int snapshotIndex = 0;
            for (var entry : slots.reference2ObjectEntrySet()) {
                gridSnapshot[snapshotIndex] = entry.getKey();
                slotSnapshot[snapshotIndex] = entry.getValue();
                snapshotIndex++;
            }
            if (snapshotIndex != snapshotSize) {
                throw new IllegalStateException("Machine route slot map changed while creating a cleanup snapshot");
            }
            for (int index = 0; index < snapshotSize; index++) {
                IGrid grid = gridSnapshot[index];
                MachineTransferSlot slot = slotSnapshot[index];
                boolean detached = false;
                try {
                    slot.detachGrid();
                    detached = true;
                } catch (RuntimeException | Error exception) {
                    failure = aggregateFailure(failure, exception);
                    try {
                        slot.detachGrid();
                        detached = true;
                    } catch (RuntimeException | Error retryException) {
                        failure = aggregateFailure(failure, retryException);
                    }
                }
                if (detached) {
                    try {
                        if (slots.get(grid) != slot) {
                            throw new IllegalStateException(
                                "Machine route slot identity changed during cleanup"
                            );
                        }
                        slots.remove(grid);
                    } catch (RuntimeException | Error exception) {
                        failure = aggregateFailure(failure, exception);
                    }
                }
            }
            return failure;
        }
    }

    private static Throwable aggregateFailure(@Nullable Throwable current, Throwable added) {
        if (current == null) {
            return added;
        }
        if (added instanceof Error && !(current instanceof Error)) {
            added.addSuppressed(current);
            return added;
        }
        current.addSuppressed(added);
        return current;
    }

    private static final class RegistrationRollback {
        private final Throwable failure;
        private final boolean failed;

        private RegistrationRollback(Throwable failure, boolean failed) {
            this.failure = Objects.requireNonNull(failure, "failure");
            this.failed = failed;
        }

        private Throwable failure() {
            return failure;
        }

        private boolean failed() {
            return failed;
        }
    }

    private static void throwCleanupFailure(@Nullable Throwable failure) {
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure != null) {
            throw new IllegalStateException("Unexpected checked cleanup failure", failure);
        }
    }

    @FunctionalInterface
    interface CleanupAction {
        void run();
    }

    static final class CleanupAccumulator {
        @Nullable
        private Throwable failure;

        void run(CleanupAction action) {
            try {
                action.run();
            } catch (RuntimeException | Error exception) {
                failure = aggregateFailure(failure, exception);
            }
        }

        void add(@Nullable Throwable added) {
            if (added != null) {
                failure = aggregateFailure(failure, added);
            }
        }

        void finish() {
            throwCleanupFailure(failure);
        }
    }

    static final class SupplyMachineEdge implements MachineBindingIndex.Route {
        private final MachineRoute owner;
        private final IEnergySupplyNode node;
        @Nullable
        private MachineBindingIndex.RouteHandle handle;
        @Nullable
        private IGrid effectiveGrid;
        private boolean closed;

        SupplyMachineEdge(MachineRoute owner, IEnergySupplyNode node) {
            this.owner = owner;
            this.node = node;
        }

        void attach(MachineBindingIndex.RouteHandle handle) {
            if (this.handle != null || closed) {
                throw new IllegalStateException("Supply-machine edge cannot attach a route handle");
            }
            this.handle = handle;
        }

        void unregister() {
            if (closed && handle == null) {
                return;
            }
            if (handle == null) {
                close();
                return;
            }
            handle.unregister();
            handle = null;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            if (handle == null && effectiveGrid == null) {
                throw new IllegalStateException("Supply-machine edge has no route handle");
            }
            Throwable failure = null;
            if (effectiveGrid != null) {
                IGrid removedGrid = effectiveGrid;
                effectiveGrid = null;
                try {
                    owner.moveGridContribution(removedGrid, null);
                } catch (RuntimeException | Error exception) {
                    failure = aggregateFailure(failure, exception);
                }
            }
            closed = true;
            throwCleanupFailure(failure);
        }

        @Override
        public void revoke() {
            if (closed || effectiveGrid == null) {
                return;
            }
            IGrid removedGrid = effectiveGrid;
            effectiveGrid = null;
            owner.moveGridContribution(removedGrid, null);
        }

        @Override
        public void refresh() {
            if (closed) {
                return;
            }
            IGrid currentGrid = node.isActive() ? node.getGrid() : null;
            if (effectiveGrid == currentGrid) {
                return;
            }
            owner.moveGridContribution(effectiveGrid, currentGrid);
            effectiveGrid = currentGrid;
        }

        @Override
        public void updateMachinePriority(int priority) {
            throw new IllegalStateException("Supply-machine edges cannot receive machine priority updates");
        }
    }

    static final class MachineTransferSlot {
        private final CFNBlockEntityEx blockEntity;
        private final long packedPosition;
        private final Interaction machineInteraction;
        private final Interaction gridMachineInteraction = new Interaction(true);
        @Nullable
        private MachineTransferAccount account;
        @Nullable
        private HubNode.HubMetadata hubMetadata;
        @Nullable
        private Interaction interaction;
        @Nullable
        private IGrid interactionGrid;
        @Nullable
        private HandlerBindingPolicy endpointPolicy;
        @Nullable
        private IEnergyHandler.EnergyType accountRole;
        private final GridParticipantMembership membership = new GridParticipantMembership();
        private EnergyHandlerRuntime.FailureContext failureContext = EnergyHandlerRuntime.FailureContext.UNKNOWN;
        @Nullable
        private String warningDimensionKey;
        private long warningPosLong;
        private long warningChunkCoordinate;
        private boolean hasWarningTarget;
        private boolean transferActive;

        MachineTransferSlot(CFNBlockEntityEx blockEntity,
                            long packedPosition,
                            Interaction machineInteraction) {
            this.blockEntity = Objects.requireNonNull(blockEntity, "blockEntity");
            this.packedPosition = packedPosition;
            this.machineInteraction = Objects.requireNonNull(machineInteraction, "machineInteraction");
        }

        public GridParticipantMembership membership() {
            return membership;
        }

        IEnergyHandler.EnergyType resolveRole(IEnergyHandler activeHandler,
                                              HandlerBindingPolicy policy,
                                              @Nullable HubNode.HubMetadata hubMetadata,
                                              @Nullable IEnergyHandler.EnergyType override,
                                              EnergyHandlerRuntime.FailureContext failureContext) {
            if (override != null) {
                return override;
            }
            if (membership.isBound() && policy.roleScope() == HandlerBindingPolicy.RoleScope.FIXED) {
                return membership.role();
            }
            return EnergyHandlerRuntime.type(activeHandler, hubMetadata, failureContext);
        }

        void configure(IGrid grid,
                       MachineTransferAccount account,
                       HandlerBindingPolicy endpointPolicy,
                       @Nullable HubNode.HubMetadata hubMetadata,
                       Interaction interaction,
                       IEnergyHandler.EnergyType role,
                       int priority,
                       EnergyHandlerRuntime.FailureContext failureContext,
                       @Nullable String warningDimensionKey,
                       long warningPosLong) {
            requireGridTracking(grid, interaction);
            Objects.requireNonNull(account, "account");
            Objects.requireNonNull(endpointPolicy, "endpointPolicy");
            Objects.requireNonNull(failureContext, "failureContext");
            Objects.requireNonNull(interaction, "interaction");
            transferActive = false;
            this.endpointPolicy = endpointPolicy;
            this.failureContext = failureContext;
            this.hubMetadata = hubMetadata;
            GridParticipantIndex index = grid.getParticipantIndex();
            if (!membership.isBound()) {
                index.add(role, this, priority);
            } else {
                GridParticipantIndex owner = requireMembershipOwner();
                if (owner != index) {
                    owner.remove(this);
                    index.add(role, this, priority);
                } else if (membership.role() != role || membership.priority() != priority) {
                    index.move(this, role, priority);
                }
            }
            updateAccountRole(account, role);
            if (warningDimensionKey == null) {
                hasWarningTarget = false;
            } else {
                this.warningDimensionKey = warningDimensionKey;
                this.warningPosLong = warningPosLong;
                this.warningChunkCoordinate = ChunkCoordUtils.mergeChunkCoords(blockPosFromLong(warningPosLong));
                hasWarningTarget = true;
            }
            transferActive = true;
        }

        @Nullable
        GridParticipantIndex.MoveRollback updatePriority(int priority) {
            if (!membership.isBound()) {
                if (membership.isChannelBound()) {
                    throw new IllegalStateException("Machine slot retains channel membership without grid membership");
                }
                return null;
            }
            GridParticipantIndex owner = requireMembershipOwner();
            IEnergyHandler.EnergyType role = Objects.requireNonNull(
                membership.role(), "Machine slot membership has no transfer role"
            );
            owner.validateMove(this, role, priority);
            if (membership.priority() != priority
                || (membership.isChannelBound() && membership.channelPriority() != priority)) {
                return owner.move(this, priority);
            }
            return null;
        }

        void validatePriority(int expectedPriority) {
            if (!membership.isBound()) {
                if (membership.isChannelBound()) {
                    throw new GridParticipantIndex.MoveRollbackException(
                        new IllegalStateException("Unbound machine slot retains channel membership")
                    );
                }
                return;
            }
            try {
                GridParticipantIndex owner = requireMembershipOwner();
                IEnergyHandler.EnergyType role = Objects.requireNonNull(
                    membership.role(), "Machine slot membership has no transfer role"
                );
                if (membership.priority() != expectedPriority
                    || membership.isChannelBound() && membership.channelPriority() != expectedPriority) {
                    throw new IllegalStateException("Machine slot priority does not match its route priority");
                }
                owner.validateMove(this, role, expectedPriority);
            } catch (RuntimeException exception) {
                if (exception instanceof GridParticipantIndex.MoveRollbackException rollbackException) {
                    throw rollbackException;
                }
                throw new GridParticipantIndex.MoveRollbackException(exception);
            }
        }

        void suspendTransfer() {
            transferActive = false;
            hasWarningTarget = false;
            if (membership.isBound()) {
                requireMembershipOwner().remove(this);
            }
            clearAccountRole();
            account = null;
            endpointPolicy = null;
            hubMetadata = null;
            failureContext = EnergyHandlerRuntime.FailureContext.UNKNOWN;
        }

        void detachGrid() {
            suspendTransfer();
            if (interactionGrid == null) {
                if (interaction != null) {
                    throw new IllegalStateException("Machine slot retains a grid interaction without grid ownership");
                }
                return;
            }
            interactionGrid.getParticipantIndex().unregisterMachineInteraction(packedPosition, gridMachineInteraction);
            interactionGrid = null;
            interaction = null;
        }

        void attachGridTracking(IGrid grid, Interaction gridInteraction) {
            if (interactionGrid == grid) {
                if (interaction != gridInteraction
                    || grid.getParticipantIndex().machineInteraction(packedPosition) != gridMachineInteraction) {
                    throw new IllegalStateException("Machine slot grid interaction identity changed without detach");
                }
                return;
            }
            if (interactionGrid != null) {
                throw new IllegalStateException("Machine slot cannot move interaction tracking between grids");
            }
            grid.getParticipantIndex().registerMachineInteraction(packedPosition, gridMachineInteraction);
            interactionGrid = grid;
            interaction = gridInteraction;
        }

        void requireGridTracking(IGrid grid, Interaction gridInteraction) {
            if (interactionGrid != grid || interaction != gridInteraction
                || grid.getParticipantIndex().machineInteraction(packedPosition) != gridMachineInteraction) {
                throw new IllegalStateException("Machine slot grid interaction tracking is inconsistent");
            }
        }

        private void recordGridInput(EnergyAmount value, long epoch) {
            Interaction gridInteraction = interaction;
            if (interactionGrid == null || gridInteraction == null) {
                throw new IllegalStateException("Machine slot cannot record input without grid interaction tracking");
            }
            gridInteraction.recordPositiveInput(value, epoch);
        }

        private void recordMachineInput(EnergyAmount value, long epoch) {
            gridMachineInteraction.recordPositiveInput(value, epoch);
            machineInteraction.recordPositiveInput(value, epoch);
        }

        private void recordGridOutput(EnergyAmount value, long epoch) {
            Interaction gridInteraction = interaction;
            if (interactionGrid == null || gridInteraction == null) {
                throw new IllegalStateException("Machine slot cannot record output without grid interaction tracking");
            }
            gridInteraction.recordPositiveOutput(value, epoch);
        }

        private void recordMachineOutput(EnergyAmount value, long epoch) {
            gridMachineInteraction.recordPositiveOutput(value, epoch);
            machineInteraction.recordPositiveOutput(value, epoch);
        }

        private void updateAccountRole(MachineTransferAccount nextAccount, IEnergyHandler.EnergyType nextRole) {
            if (account == nextAccount && accountRole == nextRole) {
                return;
            }
            nextAccount.registerRole(nextRole);
            try {
                clearAccountRole();
            } catch (RuntimeException | Error exception) {
                try {
                    nextAccount.unregisterRole(nextRole);
                } catch (RuntimeException | Error rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            }
            account = nextAccount;
            accountRole = nextRole;
        }

        private void clearAccountRole() {
            if (account == null) {
                if (accountRole != null) {
                    throw new IllegalStateException("Machine slot retains a role without a physical account");
                }
                return;
            }
            IEnergyHandler.EnergyType role = Objects.requireNonNull(accountRole, "Physical account role is missing");
            account.unregisterRole(role);
            accountRole = null;
        }

        private GridParticipantIndex requireMembershipOwner() {
            GridParticipantIndex owner = membership.owner();
            if (owner == null) {
                throw new IllegalStateException("Bound machine transfer slot has no owner index");
            }
            return owner;
        }

        boolean isActive(long epoch) {
            if (!transferActive || account == null || MachineBindingIndex.INSTANCE.isEnergyThrottled(blockEntity)) {
                return false;
            }
            account.activate(epoch);
            return account.isActive(epoch);
        }

        void collectWarning(Object2ObjectMap<String, LongSet> warningPositions,
                            long epoch,
                            Object2ObjectMap<String, LongSet> observerChunks) {
            if (!transferActive || !hasWarningTarget || !hasWarningObserver(observerChunks)) {
                return;
            }
            MachineTransferAccount currentAccount = requireAccount();
            if (MachineBindingIndex.INSTANCE.isEnergyThrottled(blockEntity)) {
                if (currentAccount.hasCachedWarning()) {
                    addWarningPosition(warningPositions);
                }
                return;
            }
            if (!currentAccount.isSettled(epoch)) {
                return;
            }
            try {
                currentAccount.recordWarning(epoch, hubMetadata);
                if (!currentAccount.hasCachedWarning()) {
                    return;
                }
            } catch (RuntimeException exception) {
                CirculationFlowNetworks.LOGGER.error(
                    "Failed to settle machine warning at dimension {} position {}",
                    warningDimensionKey, EnergyHandlerRuntime.formatPosition(warningPosLong), exception);
                return;
            }
            addWarningPosition(warningPositions);
        }

        private void addWarningPosition(Object2ObjectMap<String, LongSet> warningPositions) {
            String dimensionKey = Objects.requireNonNull(warningDimensionKey, "warningDimensionKey");
            LongSet dimensionWarnings = warningPositions.get(dimensionKey);
            if (dimensionWarnings == null) {
                dimensionWarnings = new LongOpenHashSet();
                warningPositions.put(dimensionKey, dimensionWarnings);
            }
            dimensionWarnings.add(warningPosLong);
        }

        /**
         * Samples this endpoint's warning state only when a player could actually be shown it. Sampling and
         * collecting share one observer window within a tick, so a skipped sample is always paired with a
         * skipped collection and can never leave settlement without the receive state it requires.
         */
        void prepareWarning(long epoch, Object2ObjectMap<String, LongSet> observerChunks) {
            if (!hasWarningTarget || !hasWarningObserver(observerChunks) || !isActive(epoch)) {
                return;
            }
            requireAccount().sampleWarning(requireRole(), epoch, hubMetadata, blockEntity);
        }

        private boolean hasWarningObserver(Object2ObjectMap<String, LongSet> observerChunks) {
            LongSet chunks = observerChunks.get(
                Objects.requireNonNull(warningDimensionKey, "warningDimensionKey")
            );
            return chunks != null && chunks.contains(warningChunkCoordinate);
        }

        public boolean requiresPairMatch() {
            if (endpointPolicy == null) {
                throw new IllegalStateException("Machine transfer slot has no endpoint policy");
            }
            return endpointPolicy.pairMatching() == HandlerBindingPolicy.PairMatching.REQUIRED;
        }

        IEnergyHandler handler() {
            return requireAccount().handler();
        }

        EnergyAmount canExtractValue(long epoch) {
            return requireAccount().remainingExtract(epoch, hubMetadata);
        }

        EnergyAmount canReceiveValue(long epoch) {
            return requireAccount().remainingReceive(epoch, hubMetadata);
        }

        boolean hasExtractBudget(long epoch) {
            return requireAccount().hasRemainingExtract(epoch, hubMetadata);
        }

        boolean hasReceiveBudget(long epoch) {
            return requireAccount().hasRemainingReceive(epoch, hubMetadata);
        }

        boolean prepareExtractBudget(long epoch) {
            MachineTransferAccount currentAccount = requireAccount();
            return currentAccount.sampleBudget(requireRole(), epoch, hubMetadata, blockEntity)
                && currentAccount.hasRemainingExtract(epoch, hubMetadata);
        }

        boolean prepareReceiveBudget(long epoch) {
            MachineTransferAccount currentAccount = requireAccount();
            return currentAccount.sampleBudget(requireRole(), epoch, hubMetadata, blockEntity)
                && currentAccount.hasRemainingReceive(epoch, hubMetadata);
        }

        boolean claimReceiveCandidate(long passId, long epoch) {
            MachineTransferAccount currentAccount = account;
            return currentAccount != null
                && !MachineBindingIndex.INSTANCE.isEnergyThrottled(blockEntity)
                && currentAccount.claimReceiveCandidate(passId, epoch, hubMetadata, requireRole(), blockEntity);
        }

        boolean hasExtractCandidate(long passId, long epoch) {
            MachineTransferAccount currentAccount = account;
            return currentAccount != null
                && !MachineBindingIndex.INSTANCE.isEnergyThrottled(blockEntity)
                && currentAccount.hasExtractCandidate(passId, epoch, hubMetadata, requireRole(), blockEntity);
        }

        private IEnergyHandler.EnergyType requireRole() {
            IEnergyHandler.EnergyType role = membership.role();
            if (role == null) {
                throw new IllegalStateException("Machine transfer slot has no structural role");
            }
            return role;
        }

        boolean canExtract(MachineTransferSlot receiveSlot) {
            return EnergyHandlerRuntime.canExtract(handler(), receiveSlot.handler(), hubMetadata, failureContext);
        }

        boolean canReceive(MachineTransferSlot sendSlot) {
            return EnergyHandlerRuntime.canReceive(handler(), sendSlot.handler(), hubMetadata, failureContext);
        }

        boolean canExtract(EnergyTransferParticipant receiveParticipant) {
            return EnergyHandlerRuntime.canExtract(handler(), receiveParticipant.handler(), hubMetadata, failureContext);
        }

        EnergyAmount reserveExtractEnergy(EnergyAmount maximum, long epoch) {
            return requireAccount().reserveExtract(maximum, epoch, hubMetadata);
        }

        void commitExtractEnergy(EnergyAmount accepted, long epoch) {
            requireAccount().commitExtract(accepted, epoch);
        }

        void rollbackExtractEnergy(long epoch) {
            requireAccount().rollbackExtract(epoch);
        }

        boolean hasOpenExtractReservation() {
            return requireAccount().hasOpenExtractReservation();
        }

        EnergyAmount receiveEnergy(EnergyAmount maxReceive, long epoch) {
            return requireAccount().receive(maxReceive, epoch, hubMetadata);
        }

        boolean defersReceiveCommit() {
            return requireAccount().defersReceiveCommit();
        }

        EnergyAmount receiveDeferredEnergy(EnergyAmount maximum,
                                           MachineTransferSlot sender,
                                           long epoch,
                                           Status status) {
            return requireAccount().receiveDeferred(
                maximum,
                sender.requireAccount(),
                epoch,
                hubMetadata,
                sender,
                this,
                status
            );
        }

        private MachineTransferAccount requireAccount() {
            if (account == null) {
                throw new IllegalStateException("Machine transfer slot has no physical account");
            }
            return account;
        }
    }

    static void transferEnergy(PriorityRoleIndex send,
                               PriorityRoleIndex receive,
                               Status status,
                               long epoch) {
        if (send.isEmpty() || receive.isEmpty()) {
            return;
        }
        PriorityRoleCursor sendTraversal = INSTANCE.sendCursor;
        PriorityRoleCursor receiveTraversal = INSTANCE.receiveCursor;
        sendTraversal.prepare(send);
        try {
            receiveTraversal.prepare(receive);
            try {
                if (send.hasPairMatchingParticipant() || receive.hasPairMatchingParticipant()) {
                    transferEnergyWithPairMatching(sendTraversal, receiveTraversal, status, epoch);
                } else {
                    transferEnergyLinear(
                        sendTraversal, receiveTraversal, status, epoch, INSTANCE.nextTransferPassId()
                    );
                }
            } finally {
                receiveTraversal.close();
            }
        } finally {
            sendTraversal.close();
        }
    }

    static void transferEnergy(PriorityRoleIndex send,
                               Collection<EnergyTransferParticipant> receive,
                               Status status,
                               long epoch) {
        if (send.isEmpty() || receive.isEmpty()) {
            return;
        }
        PriorityRoleCursor sendTraversal = INSTANCE.itemSendCursor;
        sendTraversal.prepare(send);
        try {
            for (EnergyTransferParticipant receiver : receive) {
                EnergyAmount receivable = receiver.canReceiveValue();
                try {
                    if (!receivable.isPositive()) {
                        continue;
                    }
                    sendTraversal.reset();
                    for (MachineTransferSlot sender = sendTraversal.next(); sender != null; sender = sendTraversal.next()) {
                        if (!sender.isActive(epoch)
                            || (sender.requiresPairMatch() || receiver.requiresPairMatch())
                            && !(sender.canExtract(receiver) && receiver.canReceive(sender))) {
                            continue;
                        }
                        transferToItem(sender, receiver, status, epoch);
                    }
                } finally {
                    receivable.recycle();
                }
            }
        } finally {
            sendTraversal.close();
        }
    }

    /**
     * Keeps the original candidate-reset behavior when an endpoint requires
     * pair matching. Ordinary routes use the linear walk below instead.
     */
    private static void transferEnergyWithPairMatching(PriorityRoleCursor sendTraversal,
                                                       PriorityRoleCursor receiveTraversal,
                                                       Status status,
                                                       long epoch) {
        for (MachineTransferSlot receiver = receiveTraversal.next(); receiver != null; receiver = receiveTraversal.next()) {
            if (!receiver.isActive(epoch) || !receiver.prepareReceiveBudget(epoch)) {
                continue;
            }
            sendTraversal.reset();
            for (MachineTransferSlot sender = sendTraversal.next(); sender != null; sender = sendTraversal.next()) {
                if (!sender.isActive(epoch) || !sender.prepareExtractBudget(epoch)) {
                    continue;
                }
                if ((sender.requiresPairMatch() || receiver.requiresPairMatch())
                    && !(sender.canExtract(receiver) && receiver.canReceive(sender))) {
                    continue;
                }
                transfer(sender, receiver, status, epoch);
                if (!receiver.hasReceiveBudget(epoch)) {
                    break;
                }
            }
        }
    }

    /**
     * Scans ordinary priority routes once. Without pair matching, a source
     * that has no remaining budget cannot become eligible again during this
     * transfer phase, so restarting at the highest sender for every receiver
     * only repeats work.
     */
    private static void transferEnergyLinear(PriorityRoleCursor sendTraversal,
                                             PriorityRoleCursor receiveTraversal,
                                             Status status,
                                             long epoch,
                                             long passId) {
        MachineTransferSlot sender = nextActiveSender(sendTraversal, epoch, passId);
        if (sender == null) {
            return;
        }
        for (MachineTransferSlot receiver = receiveTraversal.next(); receiver != null; receiver = receiveTraversal.next()) {
            if (!receiver.claimReceiveCandidate(passId, epoch)) {
                continue;
            }
            while (sender != null) {
                int outcome = transfer(sender, receiver, status, epoch);
                if ((outcome & TRANSFER_RECEIVER_REJECTED) != 0) {
                    break;
                }
                if ((outcome & TRANSFER_SENDER_UNAVAILABLE) != 0) {
                    sender.hasExtractCandidate(passId, epoch);
                    sender = nextActiveSender(sendTraversal, epoch, passId);
                }
                if ((outcome & TRANSFER_RECEIVER_UNAVAILABLE) != 0) {
                    break;
                }
                if ((outcome & TRANSFER_CLASSIFIED) == 0) {
                    throw new IllegalStateException("Linear machine transfer made no classified progress");
                }
            }
            if (sender == null) {
                return;
            }
        }
    }

    @Nullable
    private static MachineTransferSlot nextActiveSender(PriorityRoleCursor sendTraversal,
                                                        long epoch,
                                                        long passId) {
        for (MachineTransferSlot sender = sendTraversal.next(); sender != null; sender = sendTraversal.next()) {
            if (sender.hasExtractCandidate(passId, epoch)) {
                return sender;
            }
        }
        return null;
    }

    private static final int TRANSFER_PROGRESS = 1;
    private static final int TRANSFER_SENDER_UNAVAILABLE = 1 << 1;
    private static final int TRANSFER_RECEIVER_UNAVAILABLE = 1 << 2;
    private static final int TRANSFER_RECEIVER_REJECTED = 1 << 3;
    private static final int TRANSFER_CLASSIFIED = TRANSFER_PROGRESS
        | TRANSFER_SENDER_UNAVAILABLE
        | TRANSFER_RECEIVER_UNAVAILABLE
        | TRANSFER_RECEIVER_REJECTED;

    private static int transfer(MachineTransferSlot sender,
                                MachineTransferSlot receiver,
                                Status status,
                                long epoch) {
        EnergyAmount extractable = sender.canExtractValue(epoch);
        try {
            EnergyAmount receivable = receiver.canReceiveValue(epoch);
            try {
                if (!extractable.isPositive()) {
                    return TRANSFER_SENDER_UNAVAILABLE;
                }
                if (!receivable.isPositive()) {
                    return TRANSFER_RECEIVER_UNAVAILABLE;
                }
                EnergyAmount maximum = extractable.compareTo(receivable) <= 0 ? extractable : receivable;
                EnergyAmount reserved = sender.reserveExtractEnergy(maximum, epoch);
                try {
                    if (!reserved.isPositive()) {
                        return TRANSFER_SENDER_UNAVAILABLE;
                    }
                    EnergyAmount received = receiver.defersReceiveCommit()
                        ? receiver.receiveDeferredEnergy(reserved, sender, epoch, status)
                        : receiver.receiveEnergy(reserved, epoch);
                    try {
                        if (received.isPositive()) {
                            if (!receiver.defersReceiveCommit()) {
                                sender.commitExtractEnergy(received, epoch);
                                status.interaction(received, sender, receiver, epoch);
                            }
                        } else {
                            return TRANSFER_RECEIVER_REJECTED;
                        }
                    } finally {
                        received.recycle();
                    }
                    int outcome = TRANSFER_PROGRESS;
                    if (!sender.hasExtractBudget(epoch)) {
                        outcome |= TRANSFER_SENDER_UNAVAILABLE;
                    }
                    if (!receiver.hasReceiveBudget(epoch)) {
                        outcome |= TRANSFER_RECEIVER_UNAVAILABLE;
                    }
                    return outcome;
                } finally {
                    if (sender.hasOpenExtractReservation()) {
                        sender.rollbackExtractEnergy(epoch);
                    }
                    reserved.recycle();
                }
            } finally {
                receivable.recycle();
            }
        } finally {
            extractable.recycle();
        }
    }

    private long nextTransferPassId() {
        if (transferPassId == Long.MAX_VALUE) {
            throw new IllegalStateException("Machine transfer pass id exhausted");
        }
        transferPassId++;
        return transferPassId;
    }

    private static void transferToItem(MachineTransferSlot sender,
                                       EnergyTransferParticipant receiver,
                                       Status status,
                                       long epoch) {
        EnergyAmount extractable = sender.canExtractValue(epoch);
        try {
            EnergyAmount receivable = receiver.canReceiveValue();
            try {
                if (!extractable.isPositive() || !receivable.isPositive()) {
                    return;
                }
                EnergyAmount limit = extractable.compareTo(receivable) <= 0
                    ? EnergyAmount.obtain(extractable)
                    : EnergyAmount.obtain(receivable);
                try {
                    EnergyAmount reserved = sender.reserveExtractEnergy(limit, epoch);
                    try {
                        if (!reserved.isPositive()) {
                            return;
                        }
                        EnergyAmount received = receiver.receiveEnergy(reserved);
                        try {
                            if (received.isPositive()) {
                                sender.commitExtractEnergy(received, epoch);
                                status.interaction(received, sender, null, epoch);
                            }
                        } finally {
                            received.recycle();
                        }
                    } finally {
                        if (sender.hasOpenExtractReservation()) {
                            sender.rollbackExtractEnergy(epoch);
                        }
                        reserved.recycle();
                    }
                } finally {
                    limit.recycle();
                }
            } finally {
                receivable.recycle();
            }
        } finally {
            extractable.recycle();
        }
    }

    private static final class PriorityRoleCursor {
        @Nullable
        private PriorityRoleIndex.Bucket firstBucket;
        @Nullable
        private PriorityRoleIndex.Bucket bucket;
        private int index;
        private boolean inUse;

        void prepare(PriorityRoleIndex roleIndex) {
            if (inUse) {
                throw new IllegalStateException("Priority role cursor is already in use");
            }
            inUse = true;
            firstBucket = roleIndex.isEmpty() ? null : roleIndex.firstBucket();
            reset();
        }

        void reset() {
            if (!inUse) {
                throw new IllegalStateException("Priority role cursor is not in use");
            }
            bucket = firstBucket;
            index = 0;
        }

        void close() {
            firstBucket = null;
            bucket = null;
            index = 0;
            inUse = false;
        }

        @Nullable
        MachineTransferSlot next() {
            if (!inUse) {
                throw new IllegalStateException("Priority role cursor is not in use");
            }
            while (bucket != null && index >= bucket.participantCount()) {
                bucket = bucket.next();
                index = 0;
            }
            if (bucket == null) {
                return null;
            }
            MachineTransferSlot participant = bucket.participantAt(index);
            index++;
            return participant;
        }
    }

    /**
     * Runtime input/output totals for one interaction owner. Values are initialized lazily by the first record in an
     * epoch; reads from any other epoch return the immutable zero value without touching retained data.
     *
     * <p>Grid-scoped trackers always accumulate. Machine-scoped trackers are demand-driven: every read renews a
     * short accounting window, and outside that window per-machine totals are not maintained at all so an idle
     * server pays nothing for statistics nobody observes. A first read after a quiet period therefore reports the
     * current tick as zero and becomes accurate from the following tick onward.
     */
    public static class Interaction {
        private final EnergyAmount input = EnergyAmount.obtain(0L);
        private final EnergyAmount output = EnergyAmount.obtain(0L);
        private final boolean machineScoped;
        private long interactionTimeNanos;
        private long preparedEpoch = Long.MIN_VALUE;

        public Interaction() {
            this(false);
        }

        Interaction(boolean machineScoped) {
            this.machineScoped = machineScoped;
        }

        /**
         * Returns a snapshot of settled input for the current machine tick. A current-epoch snapshot is owned by the
         * caller and must be recycled. A stale tracker returns {@link EnergyAmounts#ZERO}, which must not be recycled.
         */
        public EnergyAmount getInput() {
            renewMachineDemand();
            return inputAt(INSTANCE.interactionEpoch);
        }

        /**
         * Returns a snapshot of settled output for the current machine tick. A current-epoch snapshot is owned by the
         * caller and must be recycled. A stale tracker returns {@link EnergyAmounts#ZERO}, which must not be recycled.
         */
        public EnergyAmount getOutput() {
            renewMachineDemand();
            return outputAt(INSTANCE.interactionEpoch);
        }

        /** Returns current-tick settled input as text without creating an owned {@link EnergyAmount} snapshot. */
        public String getInputString() {
            renewMachineDemand();
            return preparedEpoch == INSTANCE.interactionEpoch ? input.toString() : "0";
        }

        /** Returns current-tick settled output as text without creating an owned {@link EnergyAmount} snapshot. */
        public String getOutputString() {
            renewMachineDemand();
            return preparedEpoch == INSTANCE.interactionEpoch ? output.toString() : "0";
        }

        EnergyAmount inputAt(long epoch) {
            return preparedEpoch == epoch ? EnergyAmount.obtain(input) : EnergyAmounts.ZERO;
        }

        EnergyAmount outputAt(long epoch) {
            return preparedEpoch == epoch ? EnergyAmount.obtain(output) : EnergyAmounts.ZERO;
        }

        public String getInteractionTimeMicrosString() {
            return preparedEpoch == INSTANCE.interactionEpoch
                ? Long.toString(interactionTimeNanos / 1_000L)
                : "0";
        }

        private void renewMachineDemand() {
            if (machineScoped) {
                INSTANCE.renewMachineInteractionDemand();
            }
        }

        void recordPositiveInput(EnergyAmount value, long epoch) {
            requirePositiveInteraction(value);
            prepareForTick(epoch);
            input.add(value);
        }

        void recordPositiveOutput(EnergyAmount value, long epoch) {
            requirePositiveInteraction(value);
            prepareForTick(epoch);
            output.add(value);
        }

        private static void requirePositiveInteraction(EnergyAmount value) {
            Objects.requireNonNull(value, "value");
            if (!value.isPositive()) {
                throw new IllegalArgumentException("Settled interaction value must be positive");
            }
        }

        void recordGridTickTimeNanos(long durationNanos) {
            prepareForTick(INSTANCE.interactionEpoch);
            if (durationNanos > 0L) {
                interactionTimeNanos += durationNanos;
            }
        }

        void prepareForTick(long epoch) {
            if (preparedEpoch == epoch) {
                return;
            }
            reset();
            preparedEpoch = epoch;
        }

        private void reset() {
            input.setZero();
            output.setZero();
            interactionTimeNanos = 0L;
        }
    }
}
