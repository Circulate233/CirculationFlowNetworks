package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.api.API;
import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.api.IEnergyHandlerManager;
import com.circulation.circulation_networks.api.IGrid;
import com.circulation.circulation_networks.api.IMachineNodeBlockEntity;
import com.circulation.circulation_networks.api.node.IEnergySupplyNode;
import com.circulation.circulation_networks.api.node.IHubNode;
import com.circulation.circulation_networks.api.node.IMachineNode;
import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.events.BlockEntityLifeCycleEvent;
import com.circulation.circulation_networks.network.nodes.HubNode;
import com.circulation.circulation_networks.packets.EnergyWarningRendering;
import com.circulation.circulation_networks.packets.NodeNetworkRendering;
import com.circulation.circulation_networks.registry.RegistryEnergyHandler;
import com.circulation.circulation_networks.utils.BlockPosCompat;
import com.circulation.circulation_networks.utils.Functions;
import com.circulation.circulation_networks.utils.WorldResolveCompat;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
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
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class EnergyMachineManager {

    public static final EnergyMachineManager INSTANCE = new EnergyMachineManager();
    private static final int WARNING_SEND_INTERVAL_TICKS = 20;
    private static final int WARNING_STALE_TICKS = 200;
    private static final long NODE_RESCAN_COOLDOWN_TICKS = 40L;
    private static final double WARNING_RENDER_DISTANCE_SQ = 48.0D * 48.0D;
    private final Object2ObjectMap<String, Long2ObjectMap<ReferenceSet<IEnergySupplyNode>>> scopeNode = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<String, Object2ObjectMap<IEnergySupplyNode, LongSet>> nodeScope = new Object2ObjectOpenHashMap<>();
    private final Reference2ObjectMap<INode, Set<BlockEntity>> gridMachineMap = new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectMap<BlockEntity, ReferenceSet<INode>> machineGridMap = new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectMap<IGrid, Interaction> interaction = new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectMap<IGrid, GridTickData> tickGridData = new Reference2ObjectOpenHashMap<>();
    private final ObjectList<IGrid> activeTickGrids = new ObjectArrayList<>();
    private final ReferenceSet<IGrid> processedTickGrids = new ReferenceOpenHashSet<>();
    private final Reference2ObjectMap<BlockEntity, IEnergyHandlerManager> machineHandlerManagerCache = new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectMap<BlockEntity, IEnergyHandler.EnergyType> machineEnergyTypeCache = new Reference2ObjectOpenHashMap<>();
    private final Reference2LongMap<IEnergySupplyNode> nodeRescanTicks = new Reference2LongOpenHashMap<>();
    private final ReferenceSet<IEnergyHandler> tickSharedHandlers = new ReferenceOpenHashSet<>();
    private final Object2ObjectMap<String, LongSet> warningPositionsScratch = new Object2ObjectOpenHashMap<>();
    private final ChannelMergeScratch channelMergeScratch = new ChannelMergeScratch();
    private final ReferenceSet<IGrid> channelTickGridsScratch = new ReferenceOpenHashSet<>();
    private final ReferenceSet<IGrid> dedupGridScratch = new ReferenceOpenHashSet<>();
    private final ReferenceSet<BlockEntity> cache = new ReferenceOpenHashSet<>();
    private final Object2ObjectMap<String, Long2LongMap> lastWarningTicks = new Object2ObjectOpenHashMap<>();
    private final LongSet visibleWarningsScratch = new LongOpenHashSet();
    private long warningTickCounter;
    private long lastWarningCleanupTick;
    private long interactionEpoch;

    {
        gridMachineMap.defaultReturnValue(ReferenceSets.emptySet());
    }

    static void transferEnergy(Collection<EnergyTransferParticipant> send,
                               Collection<EnergyTransferParticipant> receive,
                               Status status,
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
                    si.remove();
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
                                        if (!extractable.isPositive()) {
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

    @SuppressWarnings("unused")
    public Map<BlockEntity, ReferenceSet<INode>> getMachineGridMap() {
        return machineGridMap;
    }

    public Reference2ObjectMap<IGrid, Interaction> getInteraction() {
        return interaction;
    }

    public void onBlockEntityValidate(BlockEntityLifeCycleEvent.Validate event) {
        if (WorldResolveCompat.isClientWorld(event.getWorld())) return;
        if (NetworkManager.INSTANCE.isInit()) {
            addMachine(event.getBlockEntity());
            var node = API.getNodeAt(event.getWorld(), event.getPos());
            if (node instanceof IMachineNode im) {
                addMachineNode(im, event.getBlockEntity());
            }
        } else cache.add(event.getBlockEntity());
    }

    public void onBlockEntityInvalidate(BlockEntityLifeCycleEvent.Invalidate event) {
        if (WorldResolveCompat.isClientWorld(event.getWorld())) return;
        removeMachine(event.getBlockEntity());
    }

    public void onServerTick() {
        var server = WorldResolveCompat.getCurrentServer();
        if (server == null) return;
        if (!NetworkManager.INSTANCE.isInit()) {
            NetworkManager.INSTANCE.initGrid();
            PocketNodeManager.INSTANCE.load();
        }
        warningTickCounter++;
        interactionEpoch++;
        var overrideManager = EnergyTypeOverrideManager.get();
        activeTickGrids.clear();
        processedTickGrids.clear();
        tickSharedHandlers.clear();
        clearWarningPositionsScratch();
        for (var entry : machineGridMap.entrySet()) {
            var te = entry.getKey();
            var world = te.getLevel();
            var pos = te.getBlockPos();
            if (!Functions.isChunkLoaded(world, pos)) continue;
            String dimId = WorldResolveCompat.getDimensionId(world);
            var activeShielders = CirculationShielderManager.INSTANCE.getShieldersForDim(dimId);
            if (activeShielders.length != 0 && CirculationShielderManager.INSTANCE.isBlockedByShielder(pos, activeShielders)) continue;
            WarningTarget warningTarget = null;
            if (te instanceof IMachineNodeBlockEntity mte) {
                var grid = mte.getNode().getGrid();
                var hubMetadata = getHubMetadata(grid);
                var handler = mte.getEnergyHandler().init(te, hubMetadata);
                tickSharedHandlers.add(handler);
                var participant = EnergyTransferParticipant.obtain(handler, grid, hubMetadata, getOrCreateInteraction(grid), false);

                var type = participant.getType();
                if (type == IEnergyHandler.EnergyType.INVALID) {
                    participant.recycle();
                } else {
                    var gridData = getTickGridData(grid);
                    Objects.requireNonNull(gridData.handlers(type)).add(participant);
                    if (type == IEnergyHandler.EnergyType.RECEIVE) {
                        if (gridData.receiveTargets.get(participant) == null) {
                            if (warningTarget == null) {
                                warningTarget = new WarningTarget(dimId, WorldResolveCompat.getPackedPos(te));
                            }
                            gridData.receiveTargets.put(participant, warningTarget);
                        }
                    }
                }
            } else {
                var override = overrideManager == null ? null : overrideManager.getOverride(dimId, pos);
                dedupGridScratch.clear();
                for (var node : entry.getValue()) {
                    var grid = node.getGrid();
                    if (grid == null) continue;
                    if (!dedupGridScratch.add(grid)) continue;

                    var hubMetadata = getHubMetadata(grid);
                    var handler = getOrCreateTickMachineHandler(te, hubMetadata);
                    if (handler == null) {
                        continue;
                    }
                    var participant = EnergyTransferParticipant.obtain(handler, grid, hubMetadata, getOrCreateInteraction(grid), false);

                    var type = override != null ? override : stabilizeEnergyType(te, participant.getType());
                    if (type == IEnergyHandler.EnergyType.INVALID) {
                        participant.recycle();
                        continue;
                    }

                    var gridData = getTickGridData(grid);
                    Objects.requireNonNull(gridData.handlers(type)).add(participant);
                    if (type == IEnergyHandler.EnergyType.RECEIVE) {
                        if (gridData.receiveTargets.get(participant) == null) {
                            if (warningTarget == null) {
                                warningTarget = new WarningTarget(dimId, WorldResolveCompat.getPackedPos(te));
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
                        transferEnergy(merged.send, merged.receive, Status.INTERACTION, false);
                        transferEnergy(merged.storage, merged.receive, Status.EXTRACT, false);
                        collectWarningPositions(merged.receive, merged.receiveTargets, warningPositionsScratch);
                        transferEnergy(merged.send, merged.storage, Status.RECEIVE, true);
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
            transferEnergy(handlers.send, handlers.receive, Status.INTERACTION, false);
            transferEnergy(handlers.storage, handlers.receive, Status.EXTRACT, false);
            collectWarningPositions(handlers.receive, handlers.receiveTargets, warningPositionsScratch);
            transferEnergy(handlers.send, handlers.storage, Status.RECEIVE, true);
            recordGridTickTimeNanos(grid, System.nanoTime() - startNanos);
        }

        sendWarningsToNearbyPlayers(server, warningPositionsScratch);
        cleanupStaleWarnings();

        ChargingManager.INSTANCE.onServerTick(server, tickGridData);

        for (var grid : activeTickGrids) {
            tickGridData.get(grid).finishTick();
        }
        recycleTickMachineHandlers();
        activeTickGrids.clear();
    }

    private ReferenceSet<IGrid> collectActiveChannelTickGrids(UUID channelId) {
        channelTickGridsScratch.clear();
        for (var candidate : activeTickGrids) {
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

    public void addMachine(BlockEntity blockEntity) {
        IEnergyHandlerManager handlerManager = RegistryEnergyHandler.getEnergyManager(blockEntity, machineHandlerManagerCache.get(blockEntity));
        if (handlerManager == null) return;
        if (RegistryEnergyHandler.isBlack(blockEntity)) return;
        var pos = blockEntity.getBlockPos();
        long chunkCoord = Functions.mergeChunkCoords(pos);

        var dim = WorldResolveCompat.getDimensionId(blockEntity.getLevel());
        var map = scopeNode.get(dim);
        if (map == null) {
            scopeNode.put(dim, map = new Long2ObjectOpenHashMap<>());
            map.defaultReturnValue(ReferenceSets.emptySet());
        }
        ReferenceSet<IEnergySupplyNode> set = map.get(chunkCoord);
        if (!set.isEmpty()) {
            var s = machineGridMap.get(blockEntity);
            boolean hasExistingAssociations = s != null;
            if (s == null) s = new ReferenceOpenHashSet<>();
            for (var node : set) {
                if (s.contains(node)) continue;
                if (!node.supplyScopeCheck(pos)) continue;
                if (node.isBlacklisted(blockEntity)) continue;

                var set1 = gridMachineMap.get(node);
                if (set1 == gridMachineMap.defaultReturnValue()) {
                    gridMachineMap.put(node, set1 = new ReferenceOpenHashSet<>());
                }
                s.add(node);
                set1.add(blockEntity);

                var players = NodeNetworkRendering.getPlayers(node.getGrid());
                if (players != null && !players.isEmpty()) {
                    for (var player : players) {
                        CirculationFlowNetworks.sendToPlayer(new NodeNetworkRendering(player, blockEntity, node, NodeNetworkRendering.MACHINE_ADD), player);
                    }
                }
            }
            if (s.isEmpty()) return;
            machineHandlerManagerCache.put(blockEntity, handlerManager);
            if (!hasExistingAssociations) {
                machineGridMap.put(blockEntity, s);
            }
        }
    }

    public void removeMachine(BlockEntity blockEntity) {
        machineEnergyTypeCache.remove(blockEntity);
        machineHandlerManagerCache.remove(blockEntity);
        var set = machineGridMap.remove(blockEntity);
        if (set == null || set.isEmpty()) return;
        for (var node : set) {
            gridMachineMap.get(node).remove(blockEntity);

            var players = NodeNetworkRendering.getPlayers(node.getGrid());
            if (players != null && !players.isEmpty()) {
                for (var player : players) {
                    CirculationFlowNetworks.sendToPlayer(new NodeNetworkRendering(player, blockEntity, node, NodeNetworkRendering.MACHINE_REMOVE), player);
                }
            }
        }
    }

    public void addMachineNode(IMachineNode iMachineNode, BlockEntity blockEntity) {
        var allConnected = new ReferenceOpenHashSet<INode>();
        for (INode candidate : NetworkManager.INSTANCE.getNodesCoveringPosition(iMachineNode.getWorld(), iMachineNode.getPos())) {
            if (candidate.linkScopeCheck(iMachineNode) != INode.LinkType.DISCONNECT) {
                allConnected.add(candidate);
            }
        }

        if (!allConnected.isEmpty()) {
            var s = machineGridMap.get(blockEntity);
            if (s == null) s = new ReferenceOpenHashSet<>();
            for (var node : allConnected) {
                var set1 = gridMachineMap.get(node);
                if (set1 == gridMachineMap.defaultReturnValue()) {
                    gridMachineMap.put(node, set1 = new ReferenceOpenHashSet<>());
                }
                s.add(node);
                set1.add(blockEntity);
            }
            if (s.isEmpty()) return;
            machineGridMap.putIfAbsent(blockEntity, s);
        }
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

            String dimId = WorldResolveCompat.getDimensionId(node.getWorld());

            Long2ObjectMap<ReferenceSet<IEnergySupplyNode>> map = scopeNode.get(dimId);
            if (map == null) {
                Long2ObjectMap<ReferenceSet<IEnergySupplyNode>> newMap = new Long2ObjectOpenHashMap<>();
                newMap.defaultReturnValue(ReferenceSets.emptySet());
                scopeNode.put(dimId, map = newMap);
            }

            for (int cx = minChunkX; cx <= maxChunkX; ++cx) {
                for (int cz = minChunkZ; cz <= maxChunkZ; ++cz) {
                    long chunkCoord = Functions.mergeChunkCoords(cx, cz);
                    chunksCovered.add(chunkCoord);

                    ReferenceSet<IEnergySupplyNode> set = map.get(chunkCoord);
                    if (set == map.defaultReturnValue()) {
                        map.put(chunkCoord, set = new ReferenceOpenHashSet<>());
                    }
                    set.add(energySupplyNode);

                    var set2 = gridMachineMap.get(node);
                    for (var tileEntity : WorldResolveCompat.getLoadedChunkBlockEntities(node.getWorld(), cx, cz)) {
                        if (tileEntity instanceof IMachineNodeBlockEntity mte) {
                            if (node.linkScopeCheck(mte.getNode()) == INode.LinkType.DISCONNECT) continue;
                            if (set2 == gridMachineMap.defaultReturnValue()) {
                                gridMachineMap.put(energySupplyNode, set2 = new ReferenceOpenHashSet<>());
                            }
                            set2.add(tileEntity);
                            var set3 = machineGridMap.get(tileEntity);
                            if (set3 == null) {
                                machineGridMap.put(tileEntity, set3 = new ReferenceOpenHashSet<>());
                            }
                            set3.add(energySupplyNode);
                        } else {
                            if (!energySupplyNode.supplyScopeCheck(tileEntity.getBlockPos())) continue;
                            if (RegistryEnergyHandler.isBlack(tileEntity)) continue;
                            if (energySupplyNode.isBlacklisted(tileEntity)) continue;
                            IEnergyHandlerManager handlerManager = RegistryEnergyHandler.getEnergyManager(tileEntity, machineHandlerManagerCache.get(tileEntity));
                            if (handlerManager != null) {
                                if (set2 == gridMachineMap.defaultReturnValue()) {
                                    gridMachineMap.put(energySupplyNode, set2 = new ReferenceOpenHashSet<>());
                                }
                                machineHandlerManagerCache.put(tileEntity, handlerManager);
                                set2.add(tileEntity);

                                var set3 = machineGridMap.get(tileEntity);
                                if (set3 == null) {
                                    machineGridMap.put(tileEntity, set3 = new ReferenceOpenHashSet<>());
                                }
                                set3.add(energySupplyNode);
                            }
                        }
                    }
                }
            }

            Object2ObjectMap<IEnergySupplyNode, LongSet> nodeScopeMap = nodeScope.get(dimId);
            if (nodeScopeMap == null) {
                nodeScope.put(dimId, nodeScopeMap = new Object2ObjectOpenHashMap<>());
            }
            nodeScopeMap.put(energySupplyNode, LongSets.unmodifiable(chunksCovered));
        }
    }

    public boolean rescanMachinesAroundNode(IEnergySupplyNode node) {
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
                for (var blockEntity : WorldResolveCompat.getLoadedChunkBlockEntities(node.getWorld(), cx, cz)) {
                    addMachine(blockEntity);
                }
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
                if (map == null) {
                    Long2ObjectMap<ReferenceSet<IEnergySupplyNode>> newMap = new Long2ObjectOpenHashMap<>();
                    newMap.defaultReturnValue(ReferenceSets.emptySet());
                    scopeNode.put(dim, map = newMap);
                }

                for (int cx = minChunkX; cx <= maxChunkX; ++cx) {
                    for (int cz = minChunkZ; cz <= maxChunkZ; ++cz) {
                        long chunkCoord = Functions.mergeChunkCoords(cx, cz);
                        chunksCovered.add(chunkCoord);

                        ReferenceSet<IEnergySupplyNode> set = map.get(chunkCoord);
                        if (set == map.defaultReturnValue()) {
                            map.put(chunkCoord, set = new ReferenceOpenHashSet<>());
                        }
                        set.add(energySupplyNode);
                    }
                }

                Object2ObjectMap<IEnergySupplyNode, LongSet> nodeScopeMap = nodeScope.get(dim);
                if (nodeScopeMap == null) {
                    nodeScope.put(dim, nodeScopeMap = new Object2ObjectOpenHashMap<>());
                }
                nodeScopeMap.put(energySupplyNode, LongSets.unmodifiable(chunksCovered));
            }
        }

        for (var te : cache) {
            addMachine(te);
            var node = API.getNodeAt(Objects.requireNonNull(te.getLevel()), te.getBlockPos());
            if (node instanceof IMachineNode im) {
                addMachineNode(im, te);
            }
        }
        cache.clear();
    }

    public void removeNode(INode node) {
        if (node instanceof IEnergySupplyNode removedNode) {
            nodeRescanTicks.removeLong(removedNode);
            String dimId = removedNode.getDimensionId();

            var nodeScopeMap = nodeScope.get(dimId);
            if (nodeScopeMap == null) return;

            LongSet coveredChunks = nodeScopeMap.remove(removedNode);
            if (coveredChunks == null || coveredChunks.isEmpty()) return;

            var scopeMap = scopeNode.get(dimId);
            if (scopeMap == null) return;

            for (long coveredChunk : coveredChunks) {
                var set = scopeMap.get(coveredChunk);
                if (set == scopeMap.defaultReturnValue()) {
                    continue;
                }
                if (set.size() == 1) scopeMap.remove(coveredChunk);
                else set.remove(removedNode);
            }

            var c = gridMachineMap.remove(removedNode);
            if (c == null || c.isEmpty()) return;
            for (var te : c) {
                var set = machineGridMap.get(te);
                if (set == null) {
                    continue;
                }
                if (set.size() == 1) {
                    machineGridMap.remove(te);
                    machineHandlerManagerCache.remove(te);
                    machineEnergyTypeCache.remove(te);
                } else {
                    set.remove(removedNode);
                }
            }
        }
    }

    public void onServerStop() {
        scopeNode.clear();
        nodeScope.clear();
        gridMachineMap.clear();
        machineGridMap.clear();
        interaction.clear();
        tickGridData.clear();
        activeTickGrids.clear();
        processedTickGrids.clear();
        channelTickGridsScratch.clear();
        machineHandlerManagerCache.clear();
        machineEnergyTypeCache.clear();
        nodeRescanTicks.clear();
        warningPositionsScratch.clear();
        visibleWarningsScratch.clear();
        lastWarningTicks.clear();
        warningTickCounter = 0L;
        lastWarningCleanupTick = 0L;
        interactionEpoch = 0L;
    }

    public @NotNull ReferenceSet<IEnergySupplyNode> getEnergyNodes(Level world, BlockPos pos) {
        return getEnergyNodes(world, pos.getX() >> 4, pos.getZ() >> 4);
    }

    public @NotNull ReferenceSet<IEnergySupplyNode> getEnergyNodes(Level world, int chunkX, int chunkZ) {
        var map = scopeNode.get(WorldResolveCompat.getDimensionId(world));
        if (map == null) return ReferenceSets.emptySet();
        return map.get(Functions.mergeChunkCoords(chunkX, chunkZ));
    }

    public @NotNull Set<BlockEntity> getMachinesSuppliedBy(IEnergySupplyNode node) {
        return gridMachineMap.getOrDefault(node, Collections.emptySet());
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

    private IEnergyHandler.EnergyType stabilizeEnergyType(BlockEntity blockEntity, IEnergyHandler.EnergyType currentType) {
        if (currentType == IEnergyHandler.EnergyType.INVALID) {
            return currentType;
        }
        IEnergyHandler.EnergyType cachedType = machineEnergyTypeCache.get(blockEntity);
        if (cachedType == null) {
            machineEnergyTypeCache.put(blockEntity, currentType);
            return currentType;
        }
        if (cachedType == IEnergyHandler.EnergyType.STORAGE) {
            return IEnergyHandler.EnergyType.STORAGE;
        }
        if (cachedType == currentType) {
            return currentType;
        }
        machineEnergyTypeCache.put(blockEntity, IEnergyHandler.EnergyType.STORAGE);
        return IEnergyHandler.EnergyType.STORAGE;
    }

    @Nullable
    private IEnergyHandler getOrCreateTickMachineHandler(BlockEntity blockEntity, @Nullable HubNode.HubMetadata hubMetadata) {
        IEnergyHandlerManager manager = RegistryEnergyHandler.getEnergyManager(blockEntity, machineHandlerManagerCache.get(blockEntity));
        if (manager == null) {
            machineHandlerManagerCache.remove(blockEntity);
            return null;
        }
        machineHandlerManagerCache.put(blockEntity, manager);
        IEnergyHandler handler = IEnergyHandler.release(blockEntity, manager, hubMetadata);
        if (handler == null) {
            return null;
        }
        tickSharedHandlers.add(handler);
        return handler;
    }

    private void recycleTickMachineHandlers() {
        for (var handler : tickSharedHandlers) {
            handler.recycle();
        }
        tickSharedHandlers.clear();
    }

    private void clearWarningPositionsScratch() {
        for (var positions : warningPositionsScratch.values()) {
            positions.clear();
        }
    }

    private void collectWarningPositions(Set<EnergyTransferParticipant> receiveHandlers,
                                         Reference2ObjectMap<EnergyTransferParticipant, WarningTarget> receiveTargets,
                                         Object2ObjectMap<String, LongSet> warningPositions) {
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

    private @NotNull Long2LongMap getWarningTicksForDimension(String dimId) {
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

    private void sendWarningsToNearbyPlayers(MinecraftServer server, Object2ObjectMap<String, LongSet> warningPositions) {
        if (warningPositions.isEmpty()) {
            return;
        }
        for (var player : WorldResolveCompat.getServerPlayers(server)) {
            String dimId = WorldResolveCompat.getPlayerDimensionId(player);
            LongSet dimWarnings = warningPositions.get(dimId);
            if (dimWarnings == null || dimWarnings.isEmpty()) {
                continue;
            }
            visibleWarningsScratch.clear();
            for (long posLong : dimWarnings) {
                BlockPos pos = BlockPosCompat.fromLong(posLong);
                if (WorldResolveCompat.getPlayerDistanceSq(player, pos) <= WARNING_RENDER_DISTANCE_SQ) {
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
        for (var dimIterator = lastWarningTicks.object2ObjectEntrySet().iterator(); dimIterator.hasNext(); ) {
            var dimEntry = dimIterator.next();
            Long2LongMap dimWarnings = dimEntry.getValue();
            dimWarnings.long2LongEntrySet().removeIf(warningEntry -> warningTickCounter - warningEntry.getLongValue() > WARNING_STALE_TICKS);
            if (dimWarnings.isEmpty()) {
                dimIterator.remove();
            }
        }
    }

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

    private record WarningTarget(String dimId, long posLong) {
    }

    static final class GridTickData {
        final ReferenceSet<EnergyTransferParticipant> send = new ReferenceOpenHashSet<>();
        final ReferenceSet<EnergyTransferParticipant> storage = new ReferenceOpenHashSet<>();
        final ReferenceSet<EnergyTransferParticipant> receive = new ReferenceOpenHashSet<>();
        final Reference2ObjectMap<EnergyTransferParticipant, WarningTarget> receiveTargets = new Reference2ObjectOpenHashMap<>();
        boolean activeThisTick;

        private static void recycle(ReferenceSet<EnergyTransferParticipant> handlers) {
            for (var participant : handlers) {
                participant.recycle();
            }
        }

        ReferenceSet<EnergyTransferParticipant> handlers(IEnergyHandler.EnergyType type) {
            return switch (type) {
                case SEND -> send;
                case STORAGE -> storage;
                case RECEIVE -> receive;
                case INVALID -> null;
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
    }

    private static final class ChannelMergeScratch {
        final ReferenceSet<EnergyTransferParticipant> send = new ReferenceOpenHashSet<>();
        final ReferenceSet<EnergyTransferParticipant> storage = new ReferenceOpenHashSet<>();
        final ReferenceSet<EnergyTransferParticipant> receive = new ReferenceOpenHashSet<>();
        final Reference2ObjectMap<EnergyTransferParticipant, WarningTarget> receiveTargets = new Reference2ObjectOpenHashMap<>();
        final ReferenceSet<IGrid> timedGrids = new ReferenceOpenHashSet<>();

        ChannelMergeScratch prepare() {
            send.clear();
            storage.clear();
            receive.clear();
            receiveTargets.clear();
            timedGrids.clear();
            return this;
        }
    }

    @SuppressWarnings("unused")
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
