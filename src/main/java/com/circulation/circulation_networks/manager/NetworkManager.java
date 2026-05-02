package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.api.IGrid;
import com.circulation.circulation_networks.api.INodeBlockEntity;
import com.circulation.circulation_networks.api.node.IHubNode;
import com.circulation.circulation_networks.api.node.IMachineNode;
import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.events.BlockEntityLifeCycleEvent;
import com.circulation.circulation_networks.network.Grid;
import com.circulation.circulation_networks.packets.NodeNetworkRendering;
import com.circulation.circulation_networks.utils.BlockPosCompat;
import com.circulation.circulation_networks.utils.CompressedNbtIoCompat;
import com.circulation.circulation_networks.utils.Functions;
import com.circulation.circulation_networks.utils.NbtCompat;
import com.circulation.circulation_networks.utils.NodeEventHooks;
import com.circulation.circulation_networks.utils.WorldResolveCompat;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMaps;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import it.unimi.dsi.fastutil.objects.ReferenceSets;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.circulation.circulation_networks.utils.WorldResolveCompat.isClientWorld;

@SuppressWarnings("unused")
public final class NetworkManager {

    public static final NetworkManager INSTANCE = new NetworkManager();
    private static final Object FILE_IO_LOCK = new Object();
    private static final ExecutorService FILE_IO_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "cfn-file-io");
        thread.setDaemon(true);
        return thread;
    });
    private static File saveFile;
    private final ReferenceSet<INode> activeNodes = new ReferenceOpenHashSet<>();
    private final Object2ObjectMap<UUID, IGrid> grids = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<String, Long2ReferenceMap<INode>> posNodes = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<String, Long2ObjectMap<ReferenceSet<INode>>> scopeNode = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<String, Object2ObjectMap<INode, LongSet>> nodeScope = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<String, Long2ObjectMap<ReferenceSet<INode>>> nodeLocation = new Object2ObjectOpenHashMap<>();
    private final NodeValidationTracker validationTracker = new NodeValidationTracker();
    private final ObjectSet<IGrid> dirtyGrids = new ObjectOpenHashSet<>();
    private boolean init;

    {
        posNodes.defaultReturnValue(Long2ReferenceMaps.emptyMap());
        scopeNode.defaultReturnValue(Long2ObjectMaps.emptyMap());
        nodeScope.defaultReturnValue(Object2ObjectMaps.emptyMap());
        nodeLocation.defaultReturnValue(Long2ObjectMaps.emptyMap());
    }

    public static File getSaveFile() {
        if (saveFile == null) {
            var path = getGridSavePath();
            tryCreateDirectories(path);
            saveFile = path.toFile();
        }
        return saveFile;
    }

    public static boolean isServerAvailable() {
        return ServerLifecycleHooks.getCurrentServer() != null;
    }

    static void runFileIoAsync(Runnable task) {
        FILE_IO_EXECUTOR.execute(() -> {
            try {
                task.run();
            } catch (Exception e) {
                CirculationFlowNetworks.LOGGER.warn("Asynchronous file task failed", e);
            }
        });
    }

    static void deleteFileAsync(File file) {
        runFileIoAsync(() -> deleteFile(file));
    }

    static boolean deleteFile(File file) {
        synchronized (FILE_IO_LOCK) {
            try {
                return !file.exists() || Files.deleteIfExists(file.toPath());
            } catch (IOException e) {
                CirculationFlowNetworks.LOGGER.warn("Failed to delete file {}", file.getAbsolutePath(), e);
                return false;
            }
        }
    }

    private static Path getGridSavePath() {
        return WorldResolveCompat.getRootSavePath().resolve("circulation_grids");
    }

    static void tryCreateDirectories(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            CirculationFlowNetworks.LOGGER.warn("Failed to create grid save directory at {}", path, e);
        }
    }

    static @Nullable CompoundTag tryReadCompressedNbt(File file, String context) {
        return CompressedNbtIoCompat.tryReadCompressedNbt(file, context);
    }

    static boolean tryWriteCompressedNbt(CompoundTag nbt, File file, String context) {
        return CompressedNbtIoCompat.tryWriteCompressedNbt(nbt, file, context, FILE_IO_LOCK);
    }

    static CompoundTag readCompressedNbt(File file) throws IOException {
        return CompressedNbtIoCompat.readCompressedNbt(file);
    }

    static void writeCompressedNbt(CompoundTag nbt, File file) throws IOException {
        CompressedNbtIoCompat.writeCompressedNbt(nbt, file, FILE_IO_LOCK);
    }

    private static String getDimensionId(Level world) {
        return WorldResolveCompat.getDimensionId(world);
    }

    private static String getDimensionId(INode node) {
        return node.getDimensionId();
    }

    private static boolean isRegisteredDimension(String dimId) {
        return dimId != null && !dimId.isEmpty() && WorldResolveCompat.isRegisteredDimension(dimId);
    }

    @Nullable
    private static String resolveGridDimension(IGrid grid) {
        if (grid == null || grid.getNodes().isEmpty()) {
            return null;
        }

        String dimId = null;
        for (INode node : grid.getNodes()) {
            if (node == null) {
                continue;
            }
            String nodeDimId = node.getDimensionId();
            if (dimId == null) {
                dimId = nodeDimId;
                continue;
            }
            if (!dimId.equals(nodeDimId)) {
                return null;
            }
        }
        return dimId;
    }

    private static void enqueueComponentNeighbor(
        INode candidate,
        ReferenceSet<INode> remaining,
        ReferenceSet<INode> component,
        Queue<INode> queue
    ) {
        if (candidate != null && remaining.remove(candidate)) {
            component.add(candidate);
            queue.add(candidate);
        }
    }

    private static Object2ObjectMap<INode, ReferenceSet<INode>> buildIncomingNeighborMap(ReferenceSet<INode> nodes) {
        Object2ObjectMap<INode, ReferenceSet<INode>> incomingNeighbors = new Object2ObjectOpenHashMap<>();
        for (INode node : nodes) {
            for (INode neighbor : node.getNeighbors()) {
                if (neighbor == null || !nodes.contains(neighbor)) {
                    continue;
                }

                ReferenceSet<INode> incoming = incomingNeighbors.get(neighbor);
                if (incoming == null) {
                    incoming = new ReferenceOpenHashSet<>();
                    incomingNeighbors.put(neighbor, incoming);
                }
                incoming.add(node);
            }
        }
        return incomingNeighbors;
    }

    private @Nullable Level resolveWorld(String dimId) {
        return WorldResolveCompat.resolveWorld(dimId);
    }

    public ReferenceSet<INode> getActiveNodes() {
        return activeNodes;
    }

    public boolean isInit() {
        return init;
    }

    private void registerNodeIndices(String dimId, INode node) {
        BlockPos pos = node.getPos();

        var pMap = posNodes.get(dimId);
        if (pMap == posNodes.defaultReturnValue()) {
            posNodes.put(dimId, pMap = new Long2ReferenceOpenHashMap<>());
        }
        pMap.put(BlockPosCompat.toLong(pos), node);

        long ownChunkCoord = Functions.mergeChunkCoords(pos);
        var locMap = nodeLocation.get(dimId);
        if (locMap == nodeLocation.defaultReturnValue()) {
            locMap = new Long2ObjectOpenHashMap<>();
            locMap.defaultReturnValue(ReferenceSets.emptySet());
            nodeLocation.put(dimId, locMap);
        }
        var locSet = locMap.get(ownChunkCoord);
        if (locSet == locMap.defaultReturnValue()) {
            locMap.put(ownChunkCoord, locSet = new ReferenceOpenHashSet<>());
        }
        locSet.add(node);

        int range = (int) node.getLinkScope();
        int minChunkX = (pos.getX() - range) >> 4, maxChunkX = (pos.getX() + range) >> 4;
        int minChunkZ = (pos.getZ() - range) >> 4, maxChunkZ = (pos.getZ() + range) >> 4;
        LongSet chunksCovered = new LongOpenHashSet();

        var scopeMap = scopeNode.get(dimId);
        if (scopeMap == scopeNode.defaultReturnValue()) {
            scopeMap = new Long2ObjectOpenHashMap<>();
            scopeMap.defaultReturnValue(ReferenceSets.emptySet());
            scopeNode.put(dimId, scopeMap);
        }
        for (int cx = minChunkX; cx <= maxChunkX; ++cx) {
            for (int cz = minChunkZ; cz <= maxChunkZ; ++cz) {
                long chunkCoord = Functions.mergeChunkCoords(cx, cz);
                chunksCovered.add(chunkCoord);
                var sSet = scopeMap.get(chunkCoord);
                if (sSet == scopeMap.defaultReturnValue()) {
                    scopeMap.put(chunkCoord, sSet = new ReferenceOpenHashSet<>());
                }
                sSet.add(node);
            }
        }
        var nodeScopeMap = nodeScope.get(dimId);
        if (nodeScopeMap == nodeScope.defaultReturnValue()) {
            nodeScopeMap = new Object2ObjectOpenHashMap<>();
            nodeScope.put(dimId, nodeScopeMap);
        }
        nodeScopeMap.put(node, LongSets.unmodifiable(chunksCovered));
    }

    private void unregisterNodeIndices(String dimId, INode node) {
        posNodes.get(dimId).remove(BlockPosCompat.toLong(node.getPos()));

        long ownChunkCoord = Functions.mergeChunkCoords(node.getPos());
        nodeLocation.get(dimId).get(ownChunkCoord).remove(node);

        var sm = scopeNode.get(dimId);
        LongSet coveredChunks = nodeScope.get(dimId).remove(node);
        if (coveredChunks != null && sm != scopeNode.defaultReturnValue()) {
            for (long chunk : coveredChunks) {
                var set = sm.get(chunk);
                if (set == sm.defaultReturnValue()) continue;
                if (set.size() == 1) sm.remove(chunk);
                else set.remove(node);
            }
        }
    }

    public @Nullable INode getNodeFromPos(Level world, BlockPos pos) {
        return posNodes.get(getDimensionId(world)).get(BlockPosCompat.toLong(pos));
    }

    public void onBlockEntityValidate(BlockEntityLifeCycleEvent.Validate event) {
        if (isClientWorld(event.getWorld())) return;
        var blockEntity = event.getBlockEntity();
        if (blockEntity instanceof INodeBlockEntity nbe) {
            String dimId = getDimensionId(event.getWorld());
            if (!init) {
                validationTracker.markEarly(dimId, event.getPos());
                return;
            }
            INode current = getNodeFromPos(event.getWorld(), event.getPos());
            INode actual = nbe.getNode();
            if (actual == null) {
                return;
            }
            if (current != null && current != actual) {
                removeNode(dimId, event.getPos());
            }
            addNode(actual, blockEntity);
            validationTracker.removeIfRegistered(dimId, event.getPos(), posNodes.get(dimId), actual);
        }
    }

    public Collection<IGrid> getAllGrids() {
        return grids.values();
    }

    public void onBlockEntityInvalidate(BlockEntityLifeCycleEvent.Invalidate event) {
        if (isClientWorld(event.getWorld())) return;
        String dimId = getDimensionId(event.getWorld());
        validationTracker.clearEarly(dimId, event.getPos());
        validationTracker.removePending(dimId, event.getPos());
        removeNode(dimId, event.getPos());
    }

    public void validatePendingNodesInChunk(Level world, int x, int z) {
        validatePendingNodesInChunk(world, Functions.mergeChunkCoords(x, z));
    }

    public void validatePendingNodesInChunk(Level world, long chunkCoord) {
        if (isClientWorld(world)) return;

        String dimId = getDimensionId(world);
        LongSet pending = validationTracker.getChunkPositions(dimId, chunkCoord);
        if (pending == null || pending.isEmpty()) {
            return;
        }

        LongArrayList pendingSnapshot = new LongArrayList(pending);
        LongArrayList toRemove = new LongArrayList();
        for (int idx = 0; idx < pendingSnapshot.size(); idx++) {
            long posLong = pendingSnapshot.getLong(idx);
            var pos = BlockPosCompat.fromLong(posLong);
            INode mapped = posNodes.get(dimId).get(posLong);
            var blockEntity = WorldResolveCompat.getBlockEntity(world, pos);

            if (blockEntity instanceof INodeBlockEntity nbe) {
                nbe.syncNodeAfterNetworkInit();
                INode actual = nbe.getNode();
                if (actual == null) {
                    continue;
                }
                if (mapped != actual) {
                    if (mapped != null) {
                        removeNode(dimId, pos);
                    }
                    addNode(actual, blockEntity);
                    if (actual instanceof IMachineNode machineNode) {
                        EnergyMachineManager.INSTANCE.addMachineNode(machineNode, blockEntity);
                    }
                    mapped = posNodes.get(dimId).get(posLong);
                }
            }

            if (mapped != null
                && mapped.isActive()
                && pos.equals(mapped.getPos())
                && blockEntity instanceof INodeBlockEntity nbe
                && nbe.getNode() == mapped) {
                toRemove.add(posLong);
            } else if (mapped != null) {
                removeNode(dimId, pos);
            } else {
                toRemove.add(posLong);
            }
        }
        validationTracker.batchRemovePending(dimId, toRemove);
    }

    public void validatePendingNodesInLoadedWorlds() {
        if (!init || validationTracker.isPendingEmpty()) {
            return;
        }

        var pendingByDim = validationTracker.pendingDimensionsSnapshot();
        for (var dimEntry : pendingByDim) {
            String dimId = dimEntry.getKey();
            var world = resolveWorld(dimId);
            if (world == null || isClientWorld(world)) {
                continue;
            }

            LongArrayList loadedChunkCoords = new LongArrayList();
            for (long chunkCoord : dimEntry.getValue().keySet()) {
                int chunkX = (int) (chunkCoord >> 32);
                int chunkZ = (int) chunkCoord;
                if (Functions.isChunkLoaded(world, chunkX, chunkZ)) {
                    loadedChunkCoords.add(chunkCoord);
                }
            }

            for (int i = 0; i < loadedChunkCoords.size(); i++) {
                long chunkCoord = loadedChunkCoords.getLong(i);
                validatePendingNodesInChunk(world, chunkCoord);
            }
        }
    }

    public @NotNull ReferenceSet<INode> getNodesCoveringPosition(Level world, BlockPos pos) {
        return scopeNode.get(getDimensionId(world)).get(Functions.mergeChunkCoords(pos));
    }

    public @NotNull ReferenceSet<INode> getNodesCoveringPosition(Level world, int chunkX, int chunkY) {
        return scopeNode.get(getDimensionId(world)).get(Functions.mergeChunkCoords(chunkX, chunkY));
    }

    public @NotNull ReferenceSet<INode> getNodesInChunk(Level world, int chunkX, int chunkZ) {
        var map = nodeLocation.get(getDimensionId(world));
        return map.get(Functions.mergeChunkCoords(chunkX, chunkZ));
    }

    public void removeNode(String dim, BlockPos pos) {
        var pMap = posNodes.get(dim);
        if (pMap != null && pMap != posNodes.defaultReturnValue()) {
            INode removedNode = pMap.get(BlockPosCompat.toLong(pos));
            if (removedNode != null) {
                removeNodeInternal(removedNode);
            }
        }
    }

    private void removeNodeInternal(INode removedNode) {
        if (!activeNodes.remove(removedNode)) return;

        NodeEventHooks.postRemoveNodePre(removedNode);
        String dimId = getDimensionId(removedNode);
        validationTracker.removePending(dimId, removedNode.getPos());

        var players = NodeNetworkRendering.getPlayers(removedNode.getGrid());
        if (players != null && !players.isEmpty()) {
            for (var player : players) {
                CirculationFlowNetworks.sendToPlayer(
                    new NodeNetworkRendering(player, removedNode, NodeNetworkRendering.NODE_REMOVE), player);
            }
        }

        unregisterNodeIndices(dimId, removedNode);

        IGrid oldGrid = removedNode.getGrid();

        if (removedNode instanceof IHubNode hub) {
            if (oldGrid != null) {
                oldGrid.setHubNode(null);
            }
            HubChannelManager.INSTANCE.unregister(hub);
        }

        if (oldGrid != null) {
            for (INode node : oldGrid.getNodes()) {
                node.removeNeighbor(removedNode);
            }
        }
        removedNode.setActive(false);

        if (oldGrid == null) {
            EnergyMachineManager.INSTANCE.removeNode(removedNode);
            ChargingManager.INSTANCE.removeNode(removedNode);
            return;
        }
        oldGrid.getNodes().remove(removedNode);
        oldGrid.markSnapshotDirty();
        markGridDirty(oldGrid);

        if (oldGrid.getNodes().isEmpty()) {
            destroyGrid(oldGrid);
        } else {
            ReferenceSet<INode> remaining = new ReferenceOpenHashSet<>(oldGrid.getNodes());
            Object2ObjectMap<INode, ReferenceSet<INode>> incomingNeighbors = buildIncomingNeighborMap(oldGrid.getNodes());
            List<ReferenceSet<INode>> components = new ObjectArrayList<>();

            while (!remaining.isEmpty()) {
                ReferenceSet<INode> component = new ReferenceOpenHashSet<>();
                Queue<INode> queue = new ArrayDeque<>();
                INode seed = remaining.iterator().next();
                queue.add(seed);
                component.add(seed);
                remaining.remove(seed);

                while (!queue.isEmpty()) {
                    INode curr = queue.poll();
                    for (INode nb : curr.getNeighbors()) {
                        enqueueComponentNeighbor(nb, remaining, component, queue);
                    }

                    ReferenceSet<INode> incoming = incomingNeighbors.get(curr);
                    if (incoming != null) {
                        for (INode nb : incoming) {
                            enqueueComponentNeighbor(nb, remaining, component, queue);
                        }
                    }
                }
                components.add(component);
            }

            if (components.size() > 1) {
                components.sort((a, b) -> Integer.compare(b.size(), a.size()));

                oldGrid.getNodes().clear();
                oldGrid.setHubNode(null);
                for (INode n : components.getFirst()) {
                    oldGrid.getNodes().add(n);
                    n.setGrid(oldGrid);
                    if (n instanceof IHubNode h) {
                        oldGrid.setHubNode(h);
                    }
                }
                oldGrid.markSnapshotDirty();
                markGridDirty(oldGrid);

                var watchingPlayers = NodeNetworkRendering.getPlayers(oldGrid);
                for (int i = 1; i < components.size(); i++) {
                    IGrid splitGrid = allocGrid();
                    for (INode n : components.get(i)) {
                        assignNodeToGrid(n, splitGrid);
                        if (n instanceof IHubNode h) {
                            splitGrid.setHubNode(h);
                        }
                    }
                    if (watchingPlayers != null) {
                        for (var player : watchingPlayers) {
                            CirculationFlowNetworks.sendToPlayer(
                                new NodeNetworkRendering(player, splitGrid), player);
                        }
                    }
                }
            }
        }

        EnergyMachineManager.INSTANCE.removeNode(removedNode);
        ChargingManager.INSTANCE.removeNode(removedNode);

        NodeEventHooks.postRemoveNodePost(removedNode);
    }

    public @NotNull AddNodeResult addNode(INode newNode) {
        return addNode(newNode, null);
    }

    public @NotNull AddNodeResult addNode(INode newNode, BlockEntity blockEntity) {
        if (newNode == null) {
            return AddNodeResult.failure(AddNodeResult.Status.NULL_NODE);
        }
        if (isClientWorld(newNode.getWorld())) {
            return AddNodeResult.failure(AddNodeResult.Status.CLIENT_WORLD);
        }
        if (!newNode.isActive()) {
            return AddNodeResult.failure(AddNodeResult.Status.NODE_INACTIVE);
        }
        if (activeNodes.contains(newNode)) {
            return AddNodeResult.failure(AddNodeResult.Status.ALREADY_ACTIVE);
        }

        if (NodeEventHooks.postAddNodePre(newNode, blockEntity)) {
            return AddNodeResult.failure(AddNodeResult.Status.EVENT_CANCELED);
        }

        String dimId = getDimensionId(newNode);
        activeNodes.add(newNode);
        registerNodeIndices(dimId, newNode);

        ReferenceSet<INode> candidates = new ReferenceOpenHashSet<>();
        var scopeMap = scopeNode.get(dimId);
        for (long chunkCoord : nodeScope.get(dimId).get(newNode)) {
            candidates.addAll(scopeMap.get(chunkCoord));
        }
        candidates.remove(newNode);

        Reference2ObjectMap<INode, INode.LinkType> linkCache = new Reference2ObjectOpenHashMap<>();
        ReferenceSet<IGrid> linkedGrids = new ReferenceOpenHashSet<>();
        for (INode existing : candidates) {
            if (!existing.isActive()) continue;
            var linkType = newNode.linkScopeCheck(existing);
            if (linkType == INode.LinkType.DISCONNECT) continue;
            linkCache.put(existing, linkType);
            if (existing.getGrid() != null) {
                linkedGrids.add(existing.getGrid());
            }
        }

        boolean hubConflict = false;
        {
            int hubCount = (newNode instanceof IHubNode) ? 1 : 0;
            for (IGrid g : linkedGrids) {
                var h = g.getHubNode();
                if (h != null && h.isActive()) {
                    hubCount++;
                    if (hubCount > 1) {
                        hubConflict = true;
                        break;
                    }
                }
            }
        }

        if (hubConflict) {
            activeNodes.remove(newNode);
            unregisterNodeIndices(dimId, newNode);
            newNode.setActive(false);
            var world = newNode.getWorld();
            var pos = newNode.getPos();
            for (var player : WorldResolveCompat.getPlayers(world)) {
                if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 36) {
                    player.sendSystemMessage(Component.translatable("message.circulation_networks.hub_conflict"));
                }
            }
            if (blockEntity != null) {
                WorldResolveCompat.destroyBlock(world, pos);
            }
            return AddNodeResult.failure(AddNodeResult.Status.HUB_CONFLICT);
        }

        for (var entry : linkCache.reference2ObjectEntrySet()) {
            INode existing = entry.getKey();
            var linkType = entry.getValue();
            switch (linkType) {
                case DOUBLY -> {
                    newNode.addNeighbor(existing);
                    existing.addNeighbor(newNode);
                }
                case A_TO_B -> newNode.addNeighbor(existing);
                case B_TO_A -> existing.addNeighbor(newNode);
            }
            IGrid existingGrid = existing.getGrid();
            IGrid currentGrid = newNode.getGrid();
            if (currentGrid == null) {
                assignNodeToGrid(newNode, existingGrid);
            } else if (existingGrid != null && existingGrid != currentGrid) {
                IGrid dst = currentGrid.getNodes().size() > existingGrid.getNodes().size() ? currentGrid : existingGrid;
                IGrid src = dst == currentGrid ? existingGrid : currentGrid;
                if (src.getHubNode() != null) {
                    dst.setHubNode(src.getHubNode());
                }
                for (INode n : src.getNodes()) {
                    dst.getNodes().add(n);
                    n.setGrid(dst);
                }
                src.getNodes().clear();
                src.setHubNode(null);
                dst.markSnapshotDirty();
                destroyGrid(src);
                markGridDirty(dst);
            }
        }

        if (newNode.getGrid() == null) {
            assignNodeToGrid(newNode, allocGrid());
        }

        if (newNode instanceof IHubNode hub) {
            newNode.getGrid().setHubNode(hub);
        }

        var players = NodeNetworkRendering.getPlayers(newNode.getGrid());
        if (players != null && !players.isEmpty()) {
            for (var player : players) {
                CirculationFlowNetworks.sendToPlayer(
                    new NodeNetworkRendering(player, newNode, NodeNetworkRendering.NODE_ADD), player);
            }
        }
        EnergyMachineManager.INSTANCE.addNode(newNode);
        ChargingManager.INSTANCE.addNode(newNode);

        NodeEventHooks.postAddNodePost(newNode, blockEntity);
        return AddNodeResult.success(newNode.getGrid());
    }

    private void assignNodeToGrid(INode node, IGrid grid) {
        markGridDirty(grid);
        grid.getNodes().add(node);
        node.setGrid(grid);
        grid.markSnapshotDirty();
    }

    private IGrid allocGrid() {
        IGrid grid = new Grid(UUID.randomUUID());
        grids.put(grid.getId(), grid);
        EnergyMachineManager.INSTANCE.getInteraction().put(grid, new EnergyMachineManager.Interaction());
        markGridDirty(grid);
        return grid;
    }

    private void destroyGrid(IGrid grid) {
        grids.remove(grid.getId());
        EnergyMachineManager.INSTANCE.getInteraction().remove(grid);
        dirtyGrids.remove(grid);
        deleteFileAsync(new File(getSaveFile(), grid.getId().toString() + ".dat"));
    }

    public void onServerStop() {
        scopeNode.clear();
        nodeScope.clear();
        nodeLocation.clear();
        activeNodes.clear();
        grids.clear();
        posNodes.clear();
        validationTracker.clear();
        saveFile = null;
        init = false;
    }

    public void markGridDirty(@Nullable IGrid grid) {
        if (grid != null) {
            dirtyGrids.add(grid);
            DatPersistenceScheduler.INSTANCE.markDirty(DatPersistenceScheduler.Target.NETWORK_GRID);
        }
    }

    public boolean saveGrid() {
        File saveDir = NetworkManager.getSaveFile();
        if (dirtyGrids.isEmpty()) {
            return true;
        }

        boolean success = true;
        List<IGrid> snapshot = new ObjectArrayList<>(dirtyGrids);
        for (IGrid grid : snapshot) {
            try {
                if (tryWriteCompressedNbt(grid.serialize(), new File(saveDir, grid.getId().toString() + ".dat"), "grid " + grid.getId())) {
                    dirtyGrids.remove(grid);
                } else {
                    success = false;
                }
            } catch (Exception e) {
                success = false;
                CirculationFlowNetworks.LOGGER.error("Failed to serialize grid {}", grid.getId(), e);
            }
        }
        return success;
    }

    public void initGrid() {
        var f = getSaveFile();
        var entries = new ObjectArrayList<GridEntry>();
        if (!f.exists() || !f.isDirectory()) {
            EnergyMachineManager.INSTANCE.initGrid(entries);
            ChargingManager.INSTANCE.initGrid(entries);
            init = true;
            return;
        }

        File[] files = f.listFiles(file -> file.isFile() && file.getName().endsWith(".dat"));
        if (files == null || files.length == 0) {
            EnergyMachineManager.INSTANCE.initGrid(entries);
            ChargingManager.INSTANCE.initGrid(entries);
            init = true;
            return;
        }

        for (File file : files) {
            var nbt = tryReadCompressedNbt(file, "grid file " + file.getName());
            if (nbt == null) {
                continue;
            }
            if (!nbt.contains("dim")) continue;
            String dimKey = NbtCompat.getStringOr(nbt, "dim", "");
            if (dimKey.isEmpty() || !WorldResolveCompat.isRegisteredDimension(dimKey)) continue;
            var grid = Grid.deserialize(nbt);
            if (grid == null) continue;
            if (grid.getNodes().isEmpty()) {
                if (file.delete()) {
                    continue;
                }
            }

            String resolvedDimId = resolveGridDimension(grid);
            if (!isRegisteredDimension(resolvedDimId)) {
                CirculationFlowNetworks.LOGGER.warn("Skipping grid {} due to inconsistent or unavailable dimension data", file.getName());
                continue;
            }
            entries.add(new GridEntry(resolvedDimId, grid));
        }

        for (var entry : entries) {
            var grid = entry.grid();
            grids.put(grid.getId(), grid);
            EnergyMachineManager.INSTANCE.getInteraction().put(grid, new EnergyMachineManager.Interaction());

            var registered = new ObjectArrayList<INode>();
            boolean collision = false;
            for (INode node : grid.getNodes()) {
                var l = posNodes.get(entry.dimId);
                var p = BlockPosCompat.toLong(node.getPos());
                if (l.containsKey(p)) {
                    collision = true;
                    break;
                }
                activeNodes.add(node);
                registerNodeIndices(entry.dimId, node);
                validationTracker.markPending(entry.dimId, node.getPos());
                registered.add(node);
            }
            if (collision) {
                for (INode node : registered) {
                    activeNodes.remove(node);
                    validationTracker.removePending(entry.dimId, node.getPos());
                    unregisterNodeIndices(entry.dimId, node);
                }
                grids.remove(grid.getId());
                EnergyMachineManager.INSTANCE.getInteraction().remove(grid);
                grid.getNodes().clear();
                grid.setHubNode(null);
            }
        }
        EnergyMachineManager.INSTANCE.initGrid(entries);
        ChargingManager.INSTANCE.initGrid(entries);
        validationTracker.mergeEarlyIntoPending();
        init = true;
        validatePendingNodesInLoadedWorlds();
    }

    private static final class NodeValidationTracker {
        private final Object2ObjectMap<String, Long2ObjectMap<LongSet>> pending = new Object2ObjectOpenHashMap<>();
        private final Object2ObjectMap<String, LongSet> early = new Object2ObjectOpenHashMap<>();

        {
            pending.defaultReturnValue(Long2ObjectMaps.emptyMap());
        }

        void markEarly(String dimId, BlockPos pos) {
            var positions = early.get(dimId);
            if (positions == null) {
                early.put(dimId, positions = new LongOpenHashSet());
            }
            positions.add(BlockPosCompat.toLong(pos));
        }

        void clearEarly(String dimId, BlockPos pos) {
            var positions = early.get(dimId);
            if (positions != null) {
                positions.remove(BlockPosCompat.toLong(pos));
                if (positions.isEmpty()) {
                    early.remove(dimId);
                }
            }
        }

        void mergeEarlyIntoPending() {
            for (var entry : early.object2ObjectEntrySet()) {
                String dimId = entry.getKey();
                for (long posLong : entry.getValue()) {
                    markPending(dimId, BlockPosCompat.fromLong(posLong));
                }
            }
            early.clear();
        }

        void markPending(String dimId, BlockPos pos) {
            long chunkCoord = Functions.mergeChunkCoords(pos);
            var dimMap = pending.get(dimId);
            if (dimMap == pending.defaultReturnValue()) {
                dimMap = new Long2ObjectOpenHashMap<>();
                dimMap.defaultReturnValue(LongSets.EMPTY_SET);
                pending.put(dimId, dimMap);
            }
            var positions = dimMap.get(chunkCoord);
            if (positions == dimMap.defaultReturnValue()) {
                dimMap.put(chunkCoord, positions = new LongOpenHashSet());
            }
            positions.add(BlockPosCompat.toLong(pos));
        }

        void removePending(String dimId, BlockPos pos) {
            long posLong = BlockPosCompat.toLong(pos);
            long chunkCoord = Functions.mergeChunkCoords(pos);
            removePendingInternal(dimId, posLong, chunkCoord);
        }

        void removePending(String dimId, long posLong) {
            long chunkCoord = Functions.mergeChunkCoords(BlockPosCompat.fromLong(posLong));
            removePendingInternal(dimId, posLong, chunkCoord);
        }

        private void removePendingInternal(String dimId, long posLong, long chunkCoord) {
            var dimMap = pending.get(dimId);
            if (dimMap == pending.defaultReturnValue()) return;
            var positions = dimMap.get(chunkCoord);
            if (positions == dimMap.defaultReturnValue()) return;
            positions.remove(posLong);
            if (positions.isEmpty()) dimMap.remove(chunkCoord);
            if (dimMap.isEmpty()) pending.remove(dimId);
        }

        void removeIfRegistered(String dimId, BlockPos pos, Long2ReferenceMap<INode> posNodesForDim, INode node) {
            if (node != null && posNodesForDim.get(BlockPosCompat.toLong(pos)) == node) {
                removePending(dimId, pos);
            }
        }

        @Nullable LongSet getChunkPositions(String dimId, long chunkCoord) {
            var dimMap = pending.get(dimId);
            if (dimMap == pending.defaultReturnValue()) return null;
            var positions = dimMap.get(chunkCoord);
            return positions == dimMap.defaultReturnValue() ? null : positions;
        }

        boolean isPendingEmpty() {
            return pending.isEmpty();
        }

        ObjectList<Object2ObjectMap.Entry<String, Long2ObjectMap<LongSet>>> pendingDimensionsSnapshot() {
            return new ObjectArrayList<>(pending.object2ObjectEntrySet());
        }

        void batchRemovePending(String dimId, LongArrayList positions) {
            for (int i = 0; i < positions.size(); i++) {
                removePending(dimId, positions.getLong(i));
            }
        }

        void clear() {
            pending.clear();
            early.clear();
        }
    }

    public static final class AddNodeResult {

        private final Status status;
        @Nullable
        private final IGrid grid;

        private AddNodeResult(Status status, @Nullable IGrid grid) {
            this.status = status;
            this.grid = grid;
        }

        private static AddNodeResult success(@Nullable IGrid grid) {
            return new AddNodeResult(Status.SUCCESS, grid);
        }

        private static AddNodeResult failure(Status status) {
            return new AddNodeResult(status, null);
        }

        public boolean isSuccess() {
            return status == Status.SUCCESS;
        }

        public @NotNull Status getStatus() {
            return status;
        }

        public @Nullable IGrid getGrid() {
            return grid;
        }

        public enum Status {
            SUCCESS,
            NULL_NODE,
            CLIENT_WORLD,
            NODE_INACTIVE,
            ALREADY_ACTIVE,
            EVENT_CANCELED,
            HUB_CONFLICT
        }
    }

    record GridEntry(String dimId, IGrid grid) {
    }
}
