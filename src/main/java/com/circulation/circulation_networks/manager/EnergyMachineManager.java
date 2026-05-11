package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.api.EnergyAmount;
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
import com.circulation.circulation_networks.registry.RegistryEnergyHandler;
import com.circulation.circulation_networks.utils.ChunkCoordUtils;
import com.circulation.circulation_networks.utils.FastSmallElementSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
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
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import it.unimi.dsi.fastutil.objects.ReferenceSets;
//~ mc_imports
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
//? if <1.20 {
import com.github.bsideup.jabel.Desugar;
import net.minecraft.entity.player.EntityPlayerMP;
//?} else {
/*import net.minecraft.server.level.ServerPlayer;
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
    private static final int WARNING_SEND_INTERVAL_TICKS = 20;
    private static final int WARNING_STALE_TICKS = 200;
    private static final long NODE_RESCAN_COOLDOWN_TICKS = 40L;
    private static final double WARNING_RENDER_DISTANCE_SQ = 48.0D * 48.0D;
    private final Int2ObjectMap<Long2ObjectMap<ReferenceSet<IEnergySupplyNode>>> scopeNode = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<Object2ObjectMap<IEnergySupplyNode, LongSet>> nodeScope = new Int2ObjectOpenHashMap<>();
    //~ if >=1.20 'TileEntity' -> 'BlockEntity' {
    private final Reference2ObjectMap<INode, ReferenceSet<TileEntity>> gridMachineMap = new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectMap<TileEntity, ReferenceSet<INode>> machineGridMap = new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectMap<TileEntity, ReferenceSet<IEnergySupplyNode>> machineSupplyNodes = new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectMap<IEnergySupplyNode, ReferenceSet<TileEntity>> supplyNodeMachines = new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectMap<TileEntity, ReferenceSet<IGrid>> machineGrids = new Reference2ObjectOpenHashMap<>();
    private final ReferenceSet<TileEntity> machineNodeTiles = new ReferenceOpenHashSet<>();
    private final Reference2ObjectMap<IGrid, Interaction> interaction = new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectMap<IGrid, GridTickData> tickGridData = new Reference2ObjectOpenHashMap<>();
    private final ObjectList<IGrid> activeTickGrids = new ObjectArrayList<>();
    private final ReferenceSet<IGrid> processedTickGrids = new ReferenceOpenHashSet<>();
    private final Reference2ObjectMap<TileEntity, IEnergyHandler> machineOriginalHandlerCache = new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectMap<TileEntity, IEnergyHandler.EnergyType> machineEnergyTypeCache = new Reference2ObjectOpenHashMap<>();
    private final Reference2LongMap<IEnergySupplyNode> nodeRescanTicks = new Reference2LongOpenHashMap<>();
    private final ReferenceSet<IEnergyHandler> usedHandlersThisTick = new ReferenceOpenHashSet<>();
    private final ReferenceSet<IEnergyHandler> retiredOriginalHandlers = new ReferenceOpenHashSet<>();
    private final Int2ObjectMap<LongSet> warningPositionsScratch = new Int2ObjectOpenHashMap<>();
    private final ChannelMergeScratch channelMergeScratch = new ChannelMergeScratch();
    private final ReferenceSet<IGrid> channelTickGridsScratch = new FastSmallElementSet<>();
    private final ReferenceSet<TileEntity> cache = new ReferenceOpenHashSet<>();
    private final Int2ObjectMap<Long2LongMap> lastWarningTicks = new Int2ObjectOpenHashMap<>();
    private final LongOpenHashSet visibleWarningsScratch = new LongOpenHashSet();
    private long warningTickCounter;
    private long lastWarningCleanupTick;
    private long interactionEpoch;
    private boolean canAddManchine;

    public void setCanAddManchine(boolean canAddManchine) {
        this.canAddManchine = canAddManchine;
    }

    {
        scopeNode.defaultReturnValue(Long2ObjectMaps.emptyMap());
        nodeScope.defaultReturnValue(Object2ObjectMaps.emptyMap());
        gridMachineMap.defaultReturnValue(ReferenceSets.emptySet());
        supplyNodeMachines.defaultReturnValue(ReferenceSets.emptySet());
    }

    static void transferEnergy(Collection<EnergyTransferParticipant> send,
                               Collection<EnergyTransferParticipant> receive,
                               Status status) {
        transferEnergy(send, receive, status, false, false);
    }

    static void transferEnergy(Collection<EnergyTransferParticipant> send,
                               Collection<EnergyTransferParticipant> receive,
                               Status status,
                               boolean sendAreStorage,
                               boolean receiversAreStorage) {
        if (send.isEmpty() || receive.isEmpty()) return;
        var si = send.iterator();
        while (si.hasNext()) {
            if (receive.isEmpty()) return;
            var sender = si.next();
            var ri = receive.iterator();
            EnergyAmount extractable = sender.canExtractValue();
            try {
                if (extractable.isZero()) {
                    if (!sendAreStorage) {
                        si.remove();
                    }
                    continue;
                }
                while (ri.hasNext()) {
                    var receiver = ri.next();
                    if (sender.canExtract(receiver) && receiver.canReceive(sender)) {
                        EnergyAmount receivable = receiver.canReceiveValue();
                        try {
                            if (receivable.isZero()) {
                                if (!receiversAreStorage) {
                                    receiver.recycle();
                                    ri.remove();
                                }
                                continue;
                            }
                            int compare = extractable.compareTo(receivable);
                            EnergyAmount transferLimit = compare <= 0 ? EnergyAmount.obtain(extractable) : EnergyAmount.obtain(receivable);
                            try {
                                EnergyAmount extracted = sender.extractEnergy(transferLimit);
                                try {
                                    if (extracted.isZero()) {
                                        sender.recycle();
                                        si.remove();
                                        break;
                                    }
                                    extractable.subtract(extracted);
                                    EnergyAmount received = receiver.receiveEnergy(extracted);
                                    try {
                                        if (!received.isZero()) {
                                            status.interaction(received, sender.interaction(), receiver.interaction());
                                        }
                                        if (!receiversAreStorage && received.compareTo(receivable) >= 0) {
                                            receiver.recycle();
                                            ri.remove();
                                        }
                                        if (!sendAreStorage && !extractable.isPositive()) {
                                            sender.recycle();
                                            si.remove();
                                            break;
                                        }
                                    } finally {
                                        received.recycle();
                                    }
                                } finally {
                                    extracted.recycle();
                                }
                            } finally {
                                transferLimit.recycle();
                            }
                        } finally {
                            receivable.recycle();
                        }
                    }
                }
            } finally {
                extractable.recycle();
            }
        }
    }
    //~}

    //? if <1.20 {
    private static MinecraftServer getServer() {
        return CirculationFlowNetworks.server;
    }

    private static int getDimensionId(World world) {
        return world.provider.getDimension();
    }

    private static int getPlayerDimensionId(EntityPlayerMP player) {
        return player.dimension;
    }

    private static double getPlayerDistanceSq(EntityPlayerMP player, BlockPos pos) {
        return player.getDistanceSq(pos.getX() + 0.5D, pos.getY() + 1.25D, pos.getZ() + 0.5D);
    }

    private static long getPackedPos(TileEntity blockEntity) {
        return blockEntity.getPos().toLong();
    }
    //?} else if <1.21 {
    /*private static MinecraftServer getServer() {
        return net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
    }

    private static int getDimensionId(Level world) {
        return world.dimension().location().hashCode();
    }

    private static int getPlayerDimensionId(ServerPlayer player) {
        return player.level().dimension().location().hashCode();
    }

    private static double getPlayerDistanceSq(ServerPlayer player, BlockPos pos) {
        double dx = player.getX() - (pos.getX() + 0.5D);
        double dy = player.getY() - (pos.getY() + 1.25D);
        double dz = player.getZ() - (pos.getZ() + 0.5D);
        return dx * dx + dy * dy + dz * dz;
    }
    *///?} else {
    /*private static MinecraftServer getServer() {
        return net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
    }

    private static int getDimensionId(Level world) {
        return world.dimension().location().hashCode();
    }

    private static int getPlayerDimensionId(ServerPlayer player) {
        return player.level().dimension().location().hashCode();
    }

    private static double getPlayerDistanceSq(ServerPlayer player, BlockPos pos) {
        double dx = player.getX() - (pos.getX() + 0.5D);
        double dy = player.getY() - (pos.getY() + 1.25D);
        double dz = player.getZ() - (pos.getZ() + 0.5D);
        return dx * dx + dy * dy + dz * dz;
    }
    *///?}

    //~ if >=1.20 '.fromLong(' -> '.of(' {
    private static BlockPos blockPosFromLong(long posLong) {
        return BlockPos.fromLong(posLong);
    }
    //~}

    //~ if >=1.20 'TileEntity' -> 'BlockEntity' {
    public Map<TileEntity, ReferenceSet<INode>> getMachineGridMap() {
        return machineGridMap;
    }
    //~}

    public Reference2ObjectMap<IGrid, Interaction> getInteraction() {
        return interaction;
    }

    public void onBlockEntityValidate(BlockEntityLifeCycleEvent.Validate event) {
        if (isClientWorld(event.getWorld())) return;
        if (canAddManchine) {
            var blockEntity = event.getBlockEntity();
            if (blockEntity instanceof IMachineNodeBlockEntity) {
                addMachineNode(blockEntity);
            } else {
                addMachine(blockEntity);
            }
        } else cache.add(event.getBlockEntity());
    }

    public void onBlockEntityInvalidate(BlockEntityLifeCycleEvent.Invalidate event) {
        if (isClientWorld(event.getWorld())) return;
        var blockEntity = event.getBlockEntity();
        if (blockEntity instanceof IMachineNodeBlockEntity) {
            removeMachineNode(blockEntity);
        } else {
            removeMachine(blockEntity);
        }
    }

    public void onServerTick() {
        var server = getServer();
        if (server == null) return;
        if (!NetworkManager.INSTANCE.isInit()) {
            NetworkManager.INSTANCE.initGrid();
            PocketNodeManager.INSTANCE.load();
        }
        loadCache();
        warningTickCounter++;
        interactionEpoch++;
        var overrideManager = EnergyTypeOverrideManager.get();
        activeTickGrids.clear();
        processedTickGrids.clear();
        usedHandlersThisTick.clear();
        clearWarningPositionsScratch();
        for (var te : machineNodeTiles) {
            //~ if >=1.20 '.getWorld()' -> '.getLevel()' {
            //~ if >=1.20 '.getPos()' -> '.getBlockPos()' {
            var world = te.getWorld();
            var pos = te.getPos();
            //~}
            //~}
            int dimId = getDimensionId(world);
            var activeShielders = CirculationShielderManager.INSTANCE.getShieldersForDim(dimId);
            if (!ChunkCoordUtils.isChunkLoaded(world, pos) || (activeShielders.length > 0 && CirculationShielderManager.INSTANCE.isBlockedByShielder(dimId, pos))) {
                return;
            }

            var mte = (IMachineNodeBlockEntity) te;
            var grid = mte.getNode().getGrid();
            if (grid == null) {
                return;
            }

            var hubMetadata = getHubMetadata(grid);
            var handler = mte.getEnergyHandler().init(te, hubMetadata);
            usedHandlersThisTick.add(handler);

            var participant = EnergyTransferParticipant.obtain(handler, grid, hubMetadata, getOrCreateInteraction(grid));
            final IEnergyHandler.EnergyType type = participant.getType();
            if (type == IEnergyHandler.EnergyType.INVALID) {
                participant.recycle();
                return;
            }

            var gridData = getTickGridData(grid);
            gridData.handlers(type).add(participant);
            if (type == IEnergyHandler.EnergyType.RECEIVE && gridData.receiveTargets.get(participant) == null) {
                gridData.receiveTargets.put(participant, new WarningTarget(dimId, getPackedPos(te)));
            }
        }
        for (var entry : machineGrids.reference2ObjectEntrySet())  {
            var te = entry.getKey();
            var grids = entry.getValue();
            //~ if >=1.20 '.getWorld()' -> '.getLevel()' {
            //~ if >=1.20 '.getPos()' -> '.getBlockPos()' {
            var world = te.getWorld();
            var pos = te.getPos();
            //~}
            //~}
            int dimId = getDimensionId(world);
            var activeShielders = CirculationShielderManager.INSTANCE.getShieldersForDim(dimId);
            if (ChunkCoordUtils.isChunkLoaded(world, pos) && (activeShielders.length == 0 || !CirculationShielderManager.INSTANCE.isBlockedByShielder(dimId, pos))) {
                WarningTarget warningTarget = null;
                var override = overrideManager == null ? null : overrideManager.getOverride(dimId, pos);
                for (var grid : grids) {
                    if (grid == null) continue;
                    var hubMetadata = getHubMetadata(grid);
                    var handler = getOrCreateTickMachineHandler(te, hubMetadata);
                    if (handler == null) {
                        continue;
                    }
                    var participant = EnergyTransferParticipant.obtain(handler, grid, hubMetadata, getOrCreateInteraction(grid));

                    final IEnergyHandler.EnergyType type = override != null ? override : stabilizeEnergyType(te, participant.getType());
                    if (type == IEnergyHandler.EnergyType.INVALID) {
                        participant.recycle();
                        continue;
                    }

                    var gridData = getTickGridData(grid);
                    gridData.handlers(type).add(participant);
                    if (type == IEnergyHandler.EnergyType.RECEIVE) {
                        if (gridData.receiveTargets.get(participant) == null) {
                            if (warningTarget == null) {
                                warningTarget = new WarningTarget(dimId, getPackedPos(te));
                            }
                            gridData.receiveTargets.put(participant, warningTarget);
                        }
                    }
                }
            }
        }

        for (var grid : activeTickGrids) {
            if (processedTickGrids.contains(grid)) continue;
            var hubNode = grid.getHubNode();
            if (hubNode != null && hubNode.isActive()) {
                var channelId = hubNode.getChannelId();
                if (!channelId.equals(HubNode.EMPTY)) {
                    var channelGrids = collectActiveChannelTickGrids(channelId);
                    if (channelGrids.size() > 1) {
                        var merged = channelMergeScratch.prepare();
                        for (var cg : channelGrids) {
                            var handlers = tickGridData.get(cg);
                            if (handlers != null && handlers.activeThisTick) {
                                merged.send.addAll(handlers.send);
                                handlers.send.clear();
                                merged.storage.addAll(handlers.storage);
                                handlers.storage.clear();
                                merged.receive.addAll(handlers.receive);
                                handlers.receive.clear();
                            }
                            if (handlers != null && !handlers.receiveTargets.isEmpty()) {
                                merged.receiveTargets.putAll(handlers.receiveTargets);
                            }
                            processedTickGrids.add(cg);
                            merged.timedGrids.add(cg);
                        }
                        long startNanos = System.nanoTime();
                        transferEnergy(merged.send, merged.receive, Status.INTERACTION);
                        transferEnergy(merged.storage, merged.receive, Status.EXTRACT, true, false);
                        collectWarningPositions(merged.receive, merged.receiveTargets, warningPositionsScratch);
                        transferEnergy(merged.send, merged.storage, Status.RECEIVE, false, true);
                        recordDistributedGridTickTimeNanos(merged.timedGrids, System.nanoTime() - startNanos);
                        syncBackParticipants(merged.send, merged.storage, merged.receive, tickGridData);
                        continue;
                    }
                }
            }

            processedTickGrids.add(grid);
            var handlers = tickGridData.get(grid);
            if (handlers == null || !handlers.activeThisTick) {
                continue;
            }

            long startNanos = System.nanoTime();
            transferEnergy(handlers.send, handlers.receive, Status.INTERACTION);
            transferEnergy(handlers.storage, handlers.receive, Status.EXTRACT, true, false);
            collectWarningPositions(handlers.receive, handlers.receiveTargets, warningPositionsScratch);
            transferEnergy(handlers.send, handlers.storage, Status.RECEIVE, false, true);
            recordGridTickTimeNanos(grid, System.nanoTime() - startNanos);
        }

        sendWarningsToNearbyPlayers(server, warningPositionsScratch);
        cleanupStaleWarnings();

        ChargingManager.INSTANCE.onServerTick(server, tickGridData);

        for (var grid : activeTickGrids) {
            tickGridData.get(grid).finishTick();
        }
        clearTickMachineHandlers();
        activeTickGrids.clear();
    }

    private ReferenceSet<IGrid> collectActiveChannelTickGrids(UUID channelId) {
        channelTickGridsScratch.clear();
        var i = activeTickGrids.listIterator();
        while (i.hasNext()) {
            var candidate = i.next();
            if (candidate == null) {
                i.remove();
                continue;
            }
            if (processedTickGrids.contains(candidate)) {
                continue;
            }
            var candidateHub = candidate.getHubNode();
            if (candidateHub != null && candidateHub.isActive() && channelId.equals(candidateHub.getChannelId())) {
                channelTickGridsScratch.add(candidate);
            }
        }
        return channelTickGridsScratch;
    }

    //~ if >=1.20 'TileEntity' -> 'BlockEntity' {
    public void addMachine(TileEntity blockEntity) {
        addMachine(blockEntity, false);
        //~}
    }

    //~ if >=1.20 'TileEntity' -> 'BlockEntity' {
    private void addMachine(TileEntity blockEntity, boolean forceRebind) {
        if (blockEntity instanceof IMachineNodeBlockEntity) {
            return;
        }
        //~}
        detachFromNetworks(blockEntity);
        IEnergyHandlerManager handlerManager = RegistryEnergyHandler.getEnergyManager(blockEntity);
        if (handlerManager == null) {
            invalidateMachineBinding(blockEntity);
            return;
        }
        if (RegistryEnergyHandler.isBlack(blockEntity)) {
            invalidateMachineBinding(blockEntity);
            return;
        }
        //~ if >=1.20 '.getPos()' -> '.getBlockPos()' {
        var pos = blockEntity.getPos();
        //~}
        long chunkCoord = ChunkCoordUtils.mergeChunkCoords(pos);

        //~ if >=1.20 '.getWorld()' -> '.getLevel()' {
        var dim = getDimensionId(blockEntity.getWorld());
        //~}
        var map = scopeNode.get(dim);
        if (map == scopeNode.defaultReturnValue()) {
            scopeNode.put(dim, map = new Long2ObjectOpenHashMap<>());
            map.defaultReturnValue(ReferenceSets.emptySet());
        }
        ReferenceSet<IEnergySupplyNode> set = map.get(chunkCoord);
        if (set.isEmpty()) {
            invalidateMachineBinding(blockEntity);
            return;
        }

        for (var node : set) {
            if (!node.supplyScopeCheck(pos)) continue;
            if (node.isBlacklisted(blockEntity)) continue;
            attachSupplyNodeToMachine(blockEntity, node);
        }

        if (!machineSupplyNodes.containsKey(blockEntity)) {
            invalidateMachineBinding(blockEntity);
            return;
        }

        bindMachineHandler(blockEntity, handlerManager, forceRebind);
        machineEnergyTypeCache.remove(blockEntity);
    }

    //~ if >=1.20 'TileEntity' -> 'BlockEntity' {
    public void removeMachine(TileEntity blockEntity) {
        //~}
        detachFromNetworks(blockEntity);
        invalidateMachineBinding(blockEntity);
    }

    //~ if >=1.20 'TileEntity' -> 'BlockEntity' {
    public void addMachineNode(TileEntity blockEntity) {
        machineNodeTiles.add(blockEntity);
        //~}
    }

    //~ if >=1.20 'TileEntity' -> 'BlockEntity' {
    private void removeMachineNode(TileEntity blockEntity) {
        machineNodeTiles.remove(blockEntity);
        //~}
    }

    //~ if >=1.20 'TileEntity' -> 'BlockEntity' {
    private void detachFromNetworks(TileEntity blockEntity) {
        var supplyNodes = machineSupplyNodes.remove(blockEntity);
        var legacyNodes = machineGridMap.remove(blockEntity);
        machineGrids.remove(blockEntity);
        if ((supplyNodes == null || supplyNodes.isEmpty()) && (legacyNodes == null || legacyNodes.isEmpty())) {
            return;
        }
        if (supplyNodes == null) {
            supplyNodes = new ReferenceOpenHashSet<>();
        }
        if (legacyNodes != null) {
            for (var node : legacyNodes) {
                if (node instanceof IEnergySupplyNode energySupplyNode) {
                    supplyNodes.add(energySupplyNode);
                }
            }
        }

        for (var node : supplyNodes) {
            detachSupplyNodeFromMachine(blockEntity, node);
        }
        //~}
    }

    //~ if >=1.20 'TileEntity' -> 'BlockEntity' {
    private void invalidateMachineBinding(TileEntity blockEntity) {
        retireMachineHandler(blockEntity);
        machineEnergyTypeCache.remove(blockEntity);
        //~}
    }

    //~ if >=1.20 'TileEntity' -> 'BlockEntity' {
    private void attachSupplyNodeToMachine(TileEntity blockEntity, IEnergySupplyNode node) {
        ReferenceSet<IEnergySupplyNode> supplyNodes = machineSupplyNodes.computeIfAbsent(blockEntity, k -> new ReferenceOpenHashSet<>());
        if (!supplyNodes.add(node)) {
            return;
        }

        ReferenceSet<INode> legacyNodes = machineGridMap.computeIfAbsent(blockEntity, k -> new ReferenceOpenHashSet<>());
        legacyNodes.add(node);

        var grid = node.getGrid();
        ReferenceSet<IGrid> grids = machineGrids.computeIfAbsent(blockEntity, k -> new FastSmallElementSet<>());
        if (grid != null) {
            grids.add(grid);
        }

        var reverse = supplyNodeMachines.get(node);
        if (reverse == supplyNodeMachines.defaultReturnValue()) {
            supplyNodeMachines.put(node, reverse = new ReferenceOpenHashSet<>());
        }
        reverse.add(blockEntity);

        var legacyReverse = gridMachineMap.get(node);
        if (legacyReverse == gridMachineMap.defaultReturnValue()) {
            gridMachineMap.put(node, legacyReverse = new ReferenceOpenHashSet<>());
        }
        legacyReverse.add(blockEntity);

        if (grid != null) {
            var players = NodeNetworkRendering.getPlayers(grid);
            if (players != null && !players.isEmpty()) {
                for (var player : players) {
                    CirculationFlowNetworks.sendToPlayer(new NodeNetworkRendering(player, blockEntity, node, NodeNetworkRendering.MACHINE_ADD), player);
                }
            }
        }
        //~}
    }

    //~ if >=1.20 'TileEntity' -> 'BlockEntity' {
    private void detachSupplyNodeFromMachine(TileEntity blockEntity, IEnergySupplyNode node) {
        var reverse = supplyNodeMachines.get(node);
        if (reverse != supplyNodeMachines.defaultReturnValue()) {
            reverse.remove(blockEntity);
            if (reverse.isEmpty()) {
                supplyNodeMachines.remove(node);
            }
        }

        var legacyReverse = gridMachineMap.get(node);
        if (legacyReverse != gridMachineMap.defaultReturnValue()) {
            legacyReverse.remove(blockEntity);
            if (legacyReverse.isEmpty()) {
                gridMachineMap.remove(node);
            }
        }

        var grid = node.getGrid();
        if (grid == null) {
            return;
        }
        var players = NodeNetworkRendering.getPlayers(grid);
        if (players != null && !players.isEmpty()) {
            for (var player : players) {
                CirculationFlowNetworks.sendToPlayer(new NodeNetworkRendering(player, blockEntity, node, NodeNetworkRendering.MACHINE_REMOVE), player);
            }
        }
        //~}
    }

    //~ if >=1.20 'TileEntity' -> 'BlockEntity' {
    private void rebuildMachineGrids(TileEntity blockEntity) {
        var supplyNodes = machineSupplyNodes.get(blockEntity);
        if (supplyNodes == null || supplyNodes.isEmpty()) {
            machineGrids.remove(blockEntity);
            return;
        }
        var grids = new FastSmallElementSet<IGrid>();
        for (var node : supplyNodes) {
            var grid = node.getGrid();
            if (grid != null) {
                grids.add(grid);
            }
        }
        machineGrids.put(blockEntity, grids);
        //~}
    }

    public void addNode(INode node) {
        if (node instanceof IEnergySupplyNode energySupplyNode) {
            int nodeX = energySupplyNode.getPos().getX();
            int nodeZ = energySupplyNode.getPos().getZ();
            int range = (int) energySupplyNode.getEnergyScope();
            int minChunkX = (nodeX - range) >> 4;
            int maxChunkX = (nodeX + range) >> 4;
            int minChunkZ = (nodeZ - range) >> 4;
            int maxChunkZ = (nodeZ + range) >> 4;
            LongSet chunksCovered = new LongOpenHashSet();

            int dimId = getDimensionId(node.getWorld());

            Long2ObjectMap<ReferenceSet<IEnergySupplyNode>> map = scopeNode.get(dimId);
            if (map == scopeNode.defaultReturnValue()) {
                Long2ObjectMap<ReferenceSet<IEnergySupplyNode>> newMap = new Long2ObjectOpenHashMap<>();
                newMap.defaultReturnValue(ReferenceSets.emptySet());
                scopeNode.put(dimId, map = newMap);
            }

            for (int cx = minChunkX; cx <= maxChunkX; ++cx) {
                for (int cz = minChunkZ; cz <= maxChunkZ; ++cz) {
                    long chunkCoord = ChunkCoordUtils.mergeChunkCoords(cx, cz);
                    chunksCovered.add(chunkCoord);

                    ReferenceSet<IEnergySupplyNode> set = map.get(chunkCoord);
                    if (set == map.defaultReturnValue()) {
                        map.put(chunkCoord, set = new ReferenceOpenHashSet<>());
                    }
                    set.add(energySupplyNode);

                    //? if <1.20 {
                    var chunk = node.getWorld().getChunkProvider().getLoadedChunk(cx, cz);
                    if (chunk == null || chunk.isEmpty()) {
                        continue;
                    }
                    for (var tileEntity : chunk.getTileEntityMap().values()) {
                        if (tileEntity instanceof IMachineNodeBlockEntity) continue;
                        if (!energySupplyNode.supplyScopeCheck(tileEntity.getPos())) continue;
                        if (RegistryEnergyHandler.isBlack(tileEntity)) continue;
                        if (energySupplyNode.isBlacklisted(tileEntity)) continue;
                        IEnergyHandlerManager handlerManager = RegistryEnergyHandler.getEnergyManager(tileEntity);
                        if (handlerManager != null) {
                            bindMachineHandler(tileEntity, handlerManager);
                            attachSupplyNodeToMachine(tileEntity, energySupplyNode);
                        }
                    }
                    //?} else {
                    /*var chunk = node.getWorld().getChunkSource().getChunkNow(cx, cz);
                    if (chunk == null) {
                        continue;
                    }
                    for (var blockEntity : chunk.getBlockEntities().values()) {
                        if (blockEntity instanceof IMachineNodeBlockEntity) continue;
                        if (!energySupplyNode.supplyScopeCheck(blockEntity.getBlockPos())) continue;
                        if (RegistryEnergyHandler.isBlack(blockEntity)) continue;
                        if (energySupplyNode.isBlacklisted(blockEntity)) continue;
                        IEnergyHandlerManager handlerManager = RegistryEnergyHandler.getEnergyManager(blockEntity);
                        if (handlerManager != null) {
                            bindMachineHandler(blockEntity, handlerManager);
                            attachSupplyNodeToMachine(blockEntity, energySupplyNode);
                        }
                    }
                    *///?}
                }
            }

            Object2ObjectMap<IEnergySupplyNode, LongSet> nodeScopeMap = nodeScope.get(dimId);
            if (nodeScopeMap == nodeScope.defaultReturnValue()) {
                nodeScope.put(dimId, nodeScopeMap = new Object2ObjectOpenHashMap<>());
            }
            nodeScopeMap.put(energySupplyNode, LongSets.unmodifiable(chunksCovered));
        }
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
                    addMachine(tileEntity, true);
                }
                //?} else {
                /*var chunk = node.getWorld().getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (var blockEntity : chunk.getBlockEntities().values()) {
                    addMachine(blockEntity, true);
                }
                *///?}
            }
        }
        return true;
    }

    void initGrid(Collection<NetworkManager.GridEntry> entries) {
        for (var entry : entries) {
            var dim = entry.dimId();
            if (entry.grid().getNodes().isEmpty()) continue;
            for (INode node : entry.grid().getNodes()) {
                if (!(node instanceof IEnergySupplyNode energySupplyNode)) continue;

                int nodeX = energySupplyNode.getPos().getX();
                int nodeZ = energySupplyNode.getPos().getZ();
                int range = (int) energySupplyNode.getEnergyScope();
                int minChunkX = (nodeX - range) >> 4, maxChunkX = (nodeX + range) >> 4;
                int minChunkZ = (nodeZ - range) >> 4, maxChunkZ = (nodeZ + range) >> 4;

                LongSet chunksCovered = new LongOpenHashSet();

                Long2ObjectMap<ReferenceSet<IEnergySupplyNode>> map = scopeNode.get(dim);
                if (map == scopeNode.defaultReturnValue()) {
                    Long2ObjectMap<ReferenceSet<IEnergySupplyNode>> newMap = new Long2ObjectOpenHashMap<>();
                    newMap.defaultReturnValue(ReferenceSets.emptySet());
                    scopeNode.put(dim, map = newMap);
                }

                for (int cx = minChunkX; cx <= maxChunkX; ++cx) {
                    for (int cz = minChunkZ; cz <= maxChunkZ; ++cz) {
                        long chunkCoord = ChunkCoordUtils.mergeChunkCoords(cx, cz);
                        chunksCovered.add(chunkCoord);

                        ReferenceSet<IEnergySupplyNode> set = map.get(chunkCoord);
                        if (set == map.defaultReturnValue()) {
                            map.put(chunkCoord, set = new ReferenceOpenHashSet<>());
                        }
                        set.add(energySupplyNode);
                    }
                }

                Object2ObjectMap<IEnergySupplyNode, LongSet> nodeScopeMap = nodeScope.get(dim);
                if (nodeScopeMap == nodeScope.defaultReturnValue()) {
                    nodeScope.put(dim, nodeScopeMap = new Object2ObjectOpenHashMap<>());
                }
                nodeScopeMap.put(energySupplyNode, LongSets.unmodifiable(chunksCovered));
            }
        }
        loadCache();
    }

    private void loadCache() {
        canAddManchine = true;
        if (cache.isEmpty()) return;
        for (var te : cache) {
            if (te instanceof IMachineNodeBlockEntity) {
                addMachineNode(te);
            } else {
                addMachine(te);
            }
        }
        cache.clear();
    }

    public void removeNode(INode node) {
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

            var c = supplyNodeMachines.remove(removedNode);
            if (c != null && !c.isEmpty()) {
                for (var te : c) {
                    var supplyNodes = machineSupplyNodes.get(te);
                    if (supplyNodes != null) {
                        supplyNodes.remove(removedNode);
                        if (supplyNodes.isEmpty()) {
                            machineSupplyNodes.remove(te);
                        }
                    }

                    var set = machineGridMap.get(te);
                    if (set != null) {
                        set.remove(removedNode);
                        if (set.isEmpty()) {
                            machineGridMap.remove(te);
                        }
                    }

                    rebuildMachineGrids(te);
                    if (!machineSupplyNodes.containsKey(te)) {
                        machineGridMap.remove(te);
                        retireMachineHandler(te);
                        machineEnergyTypeCache.remove(te);
                    }
                }
            }

            var legacy = gridMachineMap.remove(removedNode);
            if (legacy != null) {
                for (var te : legacy) {
                    var set = machineGridMap.get(te);
                    if (set != null) {
                        set.remove(removedNode);
                        if (set.isEmpty()) {
                            machineGridMap.remove(te);
                        }
                    }
                }
            }
        }
    }

    public void onServerStop() {
        scopeNode.clear();
        nodeScope.clear();
        gridMachineMap.clear();
        machineGridMap.clear();
        machineSupplyNodes.clear();
        supplyNodeMachines.clear();
        machineGrids.clear();
        machineNodeTiles.clear();
        interaction.clear();
        tickGridData.clear();
        activeTickGrids.clear();
        processedTickGrids.clear();
        for (var handler : machineOriginalHandlerCache.values()) {
            handler.clear();
        }
        machineOriginalHandlerCache.clear();
        machineEnergyTypeCache.clear();
        usedHandlersThisTick.clear();
        for (var handler : retiredOriginalHandlers) {
            handler.clear();
        }
        retiredOriginalHandlers.clear();
        nodeRescanTicks.clear();
        warningPositionsScratch.clear();
        visibleWarningsScratch.clear();
        lastWarningTicks.clear();
        warningTickCounter = 0L;
        lastWarningCleanupTick = 0L;
        interactionEpoch = 0L;
        cache.clear();
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
    //? if >=1.20 {
    /*private static long getPackedPos(BlockEntity blockEntity) {
        return blockEntity.getBlockPos().asLong();
    }
    *///?}

    //~ if >=1.20 'TileEntity' -> 'BlockEntity' {
    public @NotNull Set<TileEntity> getMachinesSuppliedBy(IEnergySupplyNode node) {
        return supplyNodeMachines.getOrDefault(node, ReferenceSets.emptySet());
    }

    private IEnergyHandler.EnergyType stabilizeEnergyType(TileEntity tileEntity, IEnergyHandler.EnergyType currentType) {
        if (currentType == IEnergyHandler.EnergyType.INVALID) {
            return currentType;
        }
        IEnergyHandler.EnergyType cachedType = machineEnergyTypeCache.get(tileEntity);
        if (cachedType == null) {
            machineEnergyTypeCache.put(tileEntity, currentType);
            return currentType;
        }
        if (cachedType == IEnergyHandler.EnergyType.STORAGE) {
            return IEnergyHandler.EnergyType.STORAGE;
        }
        if (cachedType == currentType) {
            return currentType;
        }
        machineEnergyTypeCache.put(tileEntity, IEnergyHandler.EnergyType.STORAGE);
        return IEnergyHandler.EnergyType.STORAGE;
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
        Objects.requireNonNull(getOrCreateInteraction(grid)).recordGridTickTimeNanos(durationNanos);
    }

    static void recordDistributedGridTickTimeNanos(Collection<? extends IGrid> grids, long durationNanos) {
        if (durationNanos <= 0L) {
            return;
        }
        int gridCount = grids.size();
        if (gridCount == 0) {
            return;
        }
        long baseShare = durationNanos / gridCount;
        long remainder = durationNanos % gridCount;
        for (IGrid grid : grids) {
            if (grid == null) {
                continue;
            }
            long share = baseShare;
            if (remainder > 0L) {
                share++;
                remainder--;
            }
            recordGridTickTimeNanos(grid, share);
        }
    }

    @Nullable
    static Interaction getOrCreateInteraction(@Nullable IGrid grid) {
        if (grid == null) {
            return null;
        }
        Interaction interaction = INSTANCE.interaction.get(grid);
        if (interaction == null) {
            interaction = new Interaction();
            INSTANCE.interaction.put(grid, interaction);
        }
        interaction.prepareForTick(INSTANCE.interactionEpoch);
        return interaction;
    }

    private GridTickData getTickGridData(IGrid grid) {
        GridTickData data = tickGridData.get(grid);
        if (data == null) {
            data = new GridTickData();
            tickGridData.put(grid, data);
        }
        if (!data.activeThisTick) {
            data.prepareForTick();
            activeTickGrids.add(grid);
        }
        return data;
    }

    @Nullable
    private IEnergyHandler getOrCreateTickMachineHandler(TileEntity tileEntity, @Nullable HubNode.HubMetadata hubMetadata) {
        IEnergyHandler original = machineOriginalHandlerCache.get(tileEntity);
        if (original == null) {
            return null;
        }
        IEnergyHandler handler = original.init(tileEntity, hubMetadata);
        if (handler == null) {
            return null;
        }
        usedHandlersThisTick.add(handler);
        return handler;
    }

    private void bindMachineHandler(TileEntity tileEntity, IEnergyHandlerManager manager) {
        bindMachineHandler(tileEntity, manager, false);
    }

    private void bindMachineHandler(TileEntity tileEntity, IEnergyHandlerManager manager, boolean forceRebind) {
        IEnergyHandler original = machineOriginalHandlerCache.get(tileEntity);
        if (!forceRebind && original != null && manager.getEnergyHandlerClass().isInstance(original)) {
            return;
        }
        retireMachineHandler(tileEntity);
        machineOriginalHandlerCache.put(tileEntity, manager.newBlockEntityInstance());
    }

    private void retireMachineHandler(TileEntity tileEntity) {
        IEnergyHandler retired = machineOriginalHandlerCache.remove(tileEntity);
        if (retired != null) {
            retiredOriginalHandlers.add(retired);
        }
    }

    private void clearTickMachineHandlers() {
        for (var handler : retiredOriginalHandlers) {
            handler.clear();
        }
        retiredOriginalHandlers.clear();
        for (var handler : usedHandlersThisTick) {
            handler.clear();
        }
        usedHandlersThisTick.clear();
    }

    private void clearWarningPositionsScratch() {
        for (var positions : warningPositionsScratch.values()) {
            positions.clear();
        }
    }

    private void collectWarningPositions(ReferenceSet<EnergyTransferParticipant> receiveHandlers,
                                         Reference2ObjectMap<EnergyTransferParticipant, WarningTarget> receiveTargets,
                                         Int2ObjectMap<LongSet> warningPositions) {
        if (receiveHandlers.isEmpty() || receiveTargets == null || receiveTargets.isEmpty()) {
            return;
        }
        for (var participant : receiveHandlers) {
            var target = receiveTargets.get(participant);
            if (target == null || !isWarningSendDue(target)) {
                continue;
            }
            EnergyAmount receivable = participant.canReceiveValue();
            try {
                if (receivable.isZero()) {
                    continue;
                }
            } finally {
                receivable.recycle();
            }
            markWarningSent(target);
            LongSet dimWarnings = warningPositions.get(target.dimId);
            if (dimWarnings == null) {
                dimWarnings = new LongOpenHashSet();
                warningPositions.put(target.dimId, dimWarnings);
            }
            dimWarnings.add(target.posLong);
        }
    }

    private @NotNull Long2LongMap getWarningTicksForDimension(int dimId) {
        Long2LongMap dimWarnings = lastWarningTicks.get(dimId);
        if (dimWarnings == null) {
            dimWarnings = new Long2LongOpenHashMap();
            dimWarnings.defaultReturnValue(Long.MIN_VALUE);
            lastWarningTicks.put(dimId, dimWarnings);
        }
        return dimWarnings;
    }

    private boolean isWarningSendDue(WarningTarget target) {
        Long2LongMap dimWarnings = getWarningTicksForDimension(target.dimId);
        long lastTick = dimWarnings.get(target.posLong);
        return lastTick == Long.MIN_VALUE || warningTickCounter - lastTick >= WARNING_SEND_INTERVAL_TICKS;
    }

    private void markWarningSent(WarningTarget target) {
        Long2LongMap dimWarnings = getWarningTicksForDimension(target.dimId);
        dimWarnings.put(target.posLong, warningTickCounter);
    }

    private void sendWarningsToNearbyPlayers(MinecraftServer server, Int2ObjectMap<LongSet> warningPositions) {
        if (warningPositions.isEmpty()) {
            return;
        }
        for (var player : server.getPlayerList().getPlayers()) {
            int dimId = getPlayerDimensionId(player);
            LongSet dimWarnings = warningPositions.get(dimId);
            if (dimWarnings == null || dimWarnings.isEmpty()) {
                continue;
            }
            visibleWarningsScratch.clear();
            for (long posLong : dimWarnings) {
                BlockPos pos = blockPosFromLong(posLong);
                if (getPlayerDistanceSq(player, pos) <= WARNING_RENDER_DISTANCE_SQ) {
                    visibleWarningsScratch.add(posLong);
                }
            }
            if (!visibleWarningsScratch.isEmpty()) {
                CirculationFlowNetworks.sendToPlayer(new EnergyWarningRendering(dimId, visibleWarningsScratch), player);
            }
        }
    }

    private void cleanupStaleWarnings() {
        if (warningTickCounter - lastWarningCleanupTick < WARNING_SEND_INTERVAL_TICKS) {
            return;
        }
        lastWarningCleanupTick = warningTickCounter;
        for (var dimIterator = lastWarningTicks.int2ObjectEntrySet().iterator(); dimIterator.hasNext(); ) {
            var dimEntry = dimIterator.next();
            Long2LongMap dimWarnings = dimEntry.getValue();
            dimWarnings.long2LongEntrySet().removeIf(warningEntry -> warningTickCounter - warningEntry.getLongValue() > WARNING_STALE_TICKS);
            if (dimWarnings.isEmpty()) {
                dimIterator.remove();
            }
        }
    }
    //~}

    enum Status {
        EXTRACT,
        INTERACTION,
        RECEIVE;

        private void interaction(EnergyAmount value,
                                 @Nullable Interaction senderInteraction,
                                 @Nullable Interaction receiverInteraction) {
            switch (this) {
                case INTERACTION -> {
                    if (senderInteraction != null) {
                        senderInteraction.output.add(value);
                    }
                    if (receiverInteraction != null) {
                        receiverInteraction.input.add(value);
                    }
                }
                case EXTRACT -> {
                    if (senderInteraction != null) {
                        senderInteraction.output.add(value);
                    }
                }
                case RECEIVE -> {
                    if (receiverInteraction != null) {
                        receiverInteraction.input.add(value);
                    }
                }
            }
        }
    }

    //? if <1.20
    @Desugar
    private record WarningTarget(int dimId, long posLong) {
    }

    private static void syncBackParticipants(ReferenceSet<EnergyTransferParticipant> send,
                                             ReferenceSet<EnergyTransferParticipant> storage,
                                             ReferenceSet<EnergyTransferParticipant> receive,
                                             Reference2ObjectMap<IGrid, GridTickData> tickGridData) {
        for (var p : send) {
            var h = tickGridData.get(p.grid());
            if (h != null) h.send.add(p);
        }
        for (var p : storage) {
            var h = tickGridData.get(p.grid());
            if (h != null) h.storage.add(p);
        }
        for (var p : receive) {
            var h = tickGridData.get(p.grid());
            if (h != null) h.receive.add(p);
        }
    }

    static final class GridTickData {
        final ReferenceSet<EnergyTransferParticipant> send = new ReferenceOpenHashSet<>();
        final ReferenceSet<EnergyTransferParticipant> storage = new ReferenceOpenHashSet<>();
        final ReferenceSet<EnergyTransferParticipant> receive = new ReferenceOpenHashSet<>();
        final Reference2ObjectMap<EnergyTransferParticipant, WarningTarget> receiveTargets = new Reference2ObjectOpenHashMap<>();
        boolean activeThisTick;

        @NotNull
        ReferenceSet<EnergyTransferParticipant> handlers(IEnergyHandler.EnergyType type) {
            return switch (type) {
                case SEND -> send;
                case STORAGE -> storage;
                case RECEIVE -> receive;
                case INVALID -> throw new IllegalArgumentException(String.valueOf(type));
            };
        }

        void prepareForTick() {
            send.clear();
            storage.clear();
            receive.clear();
            receiveTargets.clear();
            activeThisTick = true;
        }

        void finishTick() {
            recycle(send);
            recycle(storage);
            recycle(receive);
            send.clear();
            storage.clear();
            receive.clear();
            receiveTargets.clear();
            activeThisTick = false;
        }

        private static void recycle(ReferenceSet<EnergyTransferParticipant> handlers) {
            for (var participant : handlers) {
                participant.recycle();
            }
        }
    }

    private static final class ChannelMergeScratch {
        final ReferenceSet<EnergyTransferParticipant> send = new ReferenceOpenHashSet<>();
        final ReferenceSet<EnergyTransferParticipant> storage = new ReferenceOpenHashSet<>();
        final ReferenceSet<EnergyTransferParticipant> receive = new ReferenceOpenHashSet<>();
        final Reference2ObjectMap<EnergyTransferParticipant, WarningTarget> receiveTargets = new Reference2ObjectOpenHashMap<>();
        final ReferenceSet<IGrid> timedGrids = new FastSmallElementSet<>();

        ChannelMergeScratch prepare() {
            send.clear();
            storage.clear();
            receive.clear();
            receiveTargets.clear();
            timedGrids.clear();
            return this;
        }
    }

    public static class Interaction {
        private final EnergyAmount input = EnergyAmount.obtain(0L);
        private final EnergyAmount output = EnergyAmount.obtain(0L);
        private long interactionTimeNanos;
        private long preparedEpoch = Long.MIN_VALUE;

        public EnergyAmount getInput() {
            ensureCurrent();
            return input;
        }

        public EnergyAmount getOutput() {
            ensureCurrent();
            return output;
        }

        public String getInteractionTimeMicrosString() {
            ensureCurrent();
            return Long.toString(interactionTimeNanos / 1_000L);
        }

        long getInteractionTimeNanos() {
            ensureCurrent();
            return interactionTimeNanos;
        }

        void recordGridTickTimeNanos(long durationNanos) {
            ensureCurrent();
            if (durationNanos > 0L) {
                interactionTimeNanos += durationNanos;
            }
        }

        private void prepareForTick(long epoch) {
            if (preparedEpoch == epoch) {
                return;
            }
            reset();
            preparedEpoch = epoch;
        }

        private void ensureCurrent() {
            prepareForTick(INSTANCE.interactionEpoch);
        }

        private void reset() {
            input.setZero();
            output.setZero();
            interactionTimeNanos = 0L;
        }
    }
}
