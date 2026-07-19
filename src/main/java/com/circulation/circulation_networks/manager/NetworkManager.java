package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.api.IGrid;
import com.circulation.circulation_networks.api.INodeBlockEntity;
import com.circulation.circulation_networks.api.CFNBlockEntityEx;
import com.circulation.circulation_networks.api.node.IHubNode;
import com.circulation.circulation_networks.api.node.IMachineNode;
import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.events.BlockEntityLifeCycleEvent;
import com.circulation.circulation_networks.network.Grid;
import com.circulation.circulation_networks.utils.ChunkCoordUtils;
import com.circulation.circulation_networks.utils.NodeEventHooks;
import static com.circulation.circulation_networks.utils.SideCompat.isClientWorld;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.util.UUID;
import java.nio.file.Path;

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
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import it.unimi.dsi.fastutil.objects.ReferenceSets;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import com.circulation.circulation_networks.packets.NodeNetworkRendering;
//~ mc_imports
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
//? if <1.20 {
import com.github.bsideup.jabel.Desugar;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraftforge.common.DimensionManager;
//?} else {
/*import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
*///?}

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private final Int2ObjectMap<String> serializedDimensionKeys = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<Long2ReferenceMap<INode>> posNodes = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<Long2ObjectMap<ReferenceSet<INode>>> scopeNode = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<Object2ObjectMap<INode, LongSet>> nodeScope = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<Long2ObjectMap<ReferenceSet<INode>>> nodeLocation = new Int2ObjectOpenHashMap<>();
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
        //? if <1.20 {
        return CirculationFlowNetworks.server != null;
        //?} else if <1.21 {
        /*return net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer() != null;
        *///?} else {
        /*return net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer() != null;
        *///?}
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
        //? if <1.20 {
        return DimensionManager.getWorld(0)
                               .getSaveHandler()
                               .getWorldDirectory()
                               .toPath()
                               .resolve("circulation_grids");
        //?} else {
        /*return getCurrentServer()
                .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("circulation_grids");
        *///?}
    }

    static void tryCreateDirectories(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            CirculationFlowNetworks.LOGGER.warn("Failed to create grid save directory at {}", path, e);
        }
    }

    //? if <1.20 {
    static @Nullable NBTTagCompound tryReadCompressedNbt(File file, String context) {
        try {
            return readCompressedNbt(file);
        } catch (IOException e) {
            CirculationFlowNetworks.LOGGER.warn("Failed to read {} from {}", context, file.getAbsolutePath(), e);
            if (!file.delete()) {
                CirculationFlowNetworks.LOGGER.warn("Failed to delete unreadable {} file {}", context, file.getAbsolutePath());
            }
            return null;
        }
    }

    static boolean tryWriteCompressedNbt(NBTTagCompound nbt, File file, String context) {
        try {
            writeCompressedNbt(nbt, file);
            return true;
        } catch (IOException e) {
            CirculationFlowNetworks.LOGGER.warn("Failed to write {} to {}", context, file.getAbsolutePath(), e);
            return false;
        }
    }

    static NBTTagCompound readCompressedNbt(File file) throws IOException {
        return CompressedStreamTools.read(file);
    }

    static void writeCompressedNbt(NBTTagCompound nbt, File file) throws IOException {
        synchronized (FILE_IO_LOCK) {
            CompressedStreamTools.safeWrite(nbt, file);
        }
    }
    //?} else {
    /*static @Nullable CompoundTag tryReadCompressedNbt(File file, String context) {
        try {
            return readCompressedNbt(file);
        } catch (IOException e) {
            CirculationFlowNetworks.LOGGER.warn("Failed to read {} from {}", context, file.getAbsolutePath(), e);
            return null;
        }
    }

    static boolean tryWriteCompressedNbt(CompoundTag nbt, File file, String context) {
        try {
            writeCompressedNbt(nbt, file);
            return true;
        } catch (IOException e) {
            CirculationFlowNetworks.LOGGER.warn("Failed to write {} to {}", context, file.getAbsolutePath(), e);
            return false;
        }
    }

    static CompoundTag readCompressedNbt(File file) throws IOException {
        //~ if >=1.21 'NbtIo.readCompressed(file)' -> 'NbtIo.readCompressed(file.toPath(), net.minecraft.nbt.NbtAccounter.unlimitedHeap())' {
        return NbtIo.readCompressed(file);
        //~}
    }

    static void writeCompressedNbt(CompoundTag nbt, File file) throws IOException {
        synchronized (FILE_IO_LOCK) {
            //~ if >=1.21 'NbtIo.writeCompressed(nbt, file)' -> 'NbtIo.writeCompressed(nbt, file.toPath())' {
            NbtIo.writeCompressed(nbt, file);
            //~}
        }
    }
    *///?}

    //? if >=1.20 {
    /*private static @Nullable net.minecraft.server.MinecraftServer getCurrentServer() {
        //~ if >=1.21 'net.minecraftforge.server.ServerLifecycleHooks' -> 'net.neoforged.neoforge.server.ServerLifecycleHooks' {
        return net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        //~}
    }

    private static net.minecraft.resources.ResourceLocation parseDimensionLocation(String dimensionKey) {
        //~ if >=1.21 'new net.minecraft.resources.ResourceLocation(dimensionKey)' -> 'net.minecraft.resources.ResourceLocation.parse(dimensionKey)' {
        return new net.minecraft.resources.ResourceLocation(dimensionKey);
        //~}
    }
    *///?}

    //? if <1.20 {
    private static int getDimensionId(World world) {
        return world.provider.getDimension();
    }

    private static int getDimensionId(INode node) {
        return node.getDimensionId();
    }

    private @Nullable World resolveWorld(int dimId) {
        return DimensionManager.getWorld(dimId);
    }

    private static List<net.minecraft.entity.player.EntityPlayer> getPlayers(World world) {
        return world.playerEntities;
    }

    private static BlockPos blockPosFromLong(long posLong) {
        return BlockPos.fromLong(posLong);
    }

    @Nullable
    private static TileEntity getBlockEntity(World world, BlockPos pos) {
        return world.getTileEntity(pos);
    }

    private static void destroyBlock(World world, BlockPos pos) {
        world.destroyBlock(pos, true);
    }

    private static boolean isRegisteredDimension(int dimId) {
        return DimensionManager.isDimensionRegistered(dimId);
    }
    //?} else {
    /*private static int getDimensionId(Level world) {
        return world.dimension().location().hashCode();
    }

    private static int getDimensionId(INode node) {
        return node.getDimensionId();
    }

    private @Nullable Level resolveWorld(int dimId) {
        var server = getCurrentServer();
        if (server == null) {
            return null;
        }

        String dimensionKey = serializedDimensionKeys.get(dimId);
        if (dimensionKey != null && !dimensionKey.isEmpty()) {
            var level = server.getLevel(
                net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION,
                    parseDimensionLocation(dimensionKey)
                )
            );
            if (level != null) {
                return level;
            }
        }

        for (Level level : server.getAllLevels()) {
            if (level.dimension().location().hashCode() == dimId) {
                return level;
            }
        }
        return null;
    }

    private static List<? extends Player> getPlayers(Level world) {
        return world.players();
    }

    private static BlockPos blockPosFromLong(long posLong) {
        return BlockPos.of(posLong);
    }

    @Nullable
    private static BlockEntity getBlockEntity(Level world, BlockPos pos) {
        return world.getBlockEntity(pos);
    }

    private static void destroyBlock(Level world, BlockPos pos) {
        world.destroyBlock(pos, true, null);
    }

    private static boolean isRegisteredDimension(int dimId) {
        return true;
    }
    *///?}

    public ReferenceSet<INode> getActiveNodes() {
        return activeNodes;
    }

    public boolean isInit() {
        return init;
    }

    @Nullable
    private static Integer resolveGridDimension(IGrid grid) {
        if (grid == null || grid.getNodes().isEmpty()) {
            return null;
        }

        Integer dimId = null;
        for (INode node : grid.getNodes()) {
            if (node == null) {
                continue;
            }
            int nodeDimId = node.getDimensionId();
            if (dimId == null) {
                dimId = nodeDimId;
                continue;
            }
            if (dimId != nodeDimId) {
                return null;
            }
        }
        return dimId;
    }

    private void registerNodeIndices(int dimId, INode node) {
        rememberSerializedDimensionKey(dimId, node);
        BlockPos pos = node.getPos();
        //~ if >=1.20 '.toLong()' -> '.asLong()' {
        long posLong = pos.toLong();
        //~}

        long ownChunkCoord = ChunkCoordUtils.mergeChunkCoords(pos);
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
                long chunkCoord = ChunkCoordUtils.mergeChunkCoords(cx, cz);
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

        var pMap = posNodes.get(dimId);
        if (pMap == posNodes.defaultReturnValue()) {
            posNodes.put(dimId, pMap = new Long2ReferenceOpenHashMap<>());
        }
        INode indexedNode = pMap.get(posLong);
        if (indexedNode != null && indexedNode != node) {
            throw new IllegalStateException("Node position already indexed at dim=" + dimId + " pos=" + pos);
        }
        pMap.put(posLong, node);
    }

    private void rememberSerializedDimensionKey(int dimId, INode node) {
        if (!serializedDimensionKeys.containsKey(dimId)) {
            String dimensionKey = node.getSerializedDimensionKey();
            if (!dimensionKey.isEmpty()) {
                serializedDimensionKeys.put(dimId, dimensionKey);
            }
        }
    }

    private void unregisterNodeIndices(int dimId, INode node) {
        var pMap = posNodes.get(dimId);
        //~ if >=1.20 '.toLong()' -> '.asLong()' {
        long posLong = node.getPos().toLong();
        if (pMap != posNodes.defaultReturnValue() && pMap.get(posLong) == node) {
            pMap.remove(posLong);
        }
        //~}

        long ownChunkCoord = ChunkCoordUtils.mergeChunkCoords(node.getPos());
        var locMap = nodeLocation.get(dimId);
        if (locMap != nodeLocation.defaultReturnValue()) {
            var locSet = locMap.get(ownChunkCoord);
            if (locSet != locMap.defaultReturnValue()) {
                locSet.remove(node);
                if (locSet.isEmpty()) {
                    locMap.remove(ownChunkCoord);
                }
            }
        }

        var sm = scopeNode.get(dimId);
        var nodeScopeMap = nodeScope.get(dimId);
        LongSet coveredChunks = nodeScopeMap == nodeScope.defaultReturnValue() ? null : nodeScopeMap.remove(node);
        if (coveredChunks != null && sm != scopeNode.defaultReturnValue()) {
            for (long chunk : coveredChunks) {
                var set = sm.get(chunk);
                if (set == sm.defaultReturnValue()) continue;
                if (set.size() == 1) sm.remove(chunk);
                else set.remove(node);
            }
        }
    }

    //~ if >=1.20 '(World ' -> '(Level ' {
    //~ if >=1.20 '.toLong()' -> '.asLong()' {
    public @Nullable INode getNodeFromPos(World world, BlockPos pos) {
        return posNodes.get(getDimensionId(world)).get(pos.toLong());
    }
    //~}
    //~}

    public void onBlockEntityValidate(BlockEntityLifeCycleEvent.Validate event) {
        if (isClientWorld(event.getWorld())) return;
        var blockEntity = event.getBlockEntity();
        if (blockEntity instanceof INodeBlockEntity<?> nbe) {
            int dimId = getDimensionId(event.getWorld());
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
        int dimId = getDimensionId(event.getWorld());
        validationTracker.clearEarly(dimId, event.getPos());
        validationTracker.removePending(dimId, event.getPos());
        removeNode(dimId, event.getPos());
    }

    //~ if >=1.20 '(World ' -> '(Level ' {
    public void validatePendingNodesInChunk(World world, int x, int z) {
        validatePendingNodesInChunk(world, ChunkCoordUtils.mergeChunkCoords(x, z));
    }

    public void validatePendingNodesInChunk(World world, long chunkCoord) {
        //~}
        if (isClientWorld(world)) return;

        int dimId = getDimensionId(world);
        LongSet pending = validationTracker.getChunkPositions(dimId, chunkCoord);
        if (pending == null || pending.isEmpty()) {
            return;
        }

        LongArrayList pendingSnapshot = new LongArrayList(pending);
        LongArrayList toRemove = new LongArrayList();
        for (int idx = 0; idx < pendingSnapshot.size(); idx++) {
            long posLong = pendingSnapshot.getLong(idx);
            var pos = blockPosFromLong(posLong);
            INode mapped = posNodes.get(dimId).get(posLong);
            var blockEntity = getBlockEntity(world, pos);

            if (blockEntity instanceof INodeBlockEntity<?> nbe) {
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
                        EnergyMachineManager.INSTANCE.addMachineNode(CFNBlockEntityEx.cfn_cast(blockEntity));
                    }
                    mapped = posNodes.get(dimId).get(posLong);
                }
            }

            if (mapped != null
                && mapped.isActive()
                && pos.equals(mapped.getPos())
                && blockEntity instanceof INodeBlockEntity<?> nbe
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

    //~ if >=1.20 '(World ' -> '(Level ' {
    public void validatePendingNodesInLoadedWorlds() {
        if (!init || validationTracker.isPendingEmpty()) {
            return;
        }

        var pendingByDim = validationTracker.pendingDimensionsSnapshot();
        for (var dimEntry : pendingByDim) {
            int dimId = dimEntry.getIntKey();
            var world = resolveWorld(dimId);
            if (world == null || isClientWorld(world)) {
                continue;
            }

            LongArrayList loadedChunkCoords = new LongArrayList();
            for (long chunkCoord : dimEntry.getValue().keySet()) {
                int chunkX = (int) (chunkCoord >> 32);
                int chunkZ = (int) chunkCoord;
                if (ChunkCoordUtils.isChunkLoaded(world, chunkX, chunkZ)) {
                    loadedChunkCoords.add(chunkCoord);
                }
            }

            for (int i = 0; i < loadedChunkCoords.size(); i++) {
                long chunkCoord = loadedChunkCoords.getLong(i);
                validatePendingNodesInChunk(world, chunkCoord);
            }
        }
    }
    //~}

    //~ if >=1.20 '(World ' -> '(Level ' {
    public @NotNull ReferenceSet<INode> getNodesCoveringPosition(World world, BlockPos pos) {
        return scopeNode.get(getDimensionId(world)).get(ChunkCoordUtils.mergeChunkCoords(pos));
    }

    public @NotNull ReferenceSet<INode> getNodesCoveringPosition(World world, int chunkX, int chunkY) {
        return scopeNode.get(getDimensionId(world)).get(ChunkCoordUtils.mergeChunkCoords(chunkX, chunkY));
    }

    public @NotNull ReferenceSet<INode> getNodesInChunk(World world, int chunkX, int chunkZ) {
        var map = nodeLocation.get(getDimensionId(world));
        return map.get(ChunkCoordUtils.mergeChunkCoords(chunkX, chunkZ));
    }
    //~}

    public synchronized void removeNode(int dim, BlockPos pos) {
        MachineBindingIndex.INSTANCE.beginTopologyTransaction();
        try {
            var pMap = posNodes.get(dim);
            if (pMap != null && pMap != posNodes.defaultReturnValue()) {
                //~ if >=1.20 '.toLong()' -> '.asLong()' {
                INode removedNode = pMap.get(pos.toLong());
                if (removedNode != null) {
                    removeNodeInternal(removedNode);
                }
                //~}
            }
        } finally {
            MachineBindingIndex.INSTANCE.endTopologyTransaction();
        }
    }

    private void removeNodeInternal(INode removedNode) {
        if (!activeNodes.remove(removedNode)) return;

        NodeEventHooks.postRemoveNodePre(removedNode);
        int dimId = getDimensionId(removedNode);
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
                for (INode n : components.get(0)) {
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

    public @NotNull AddNodeResult addNode(INode newNode) {
        return addNode(newNode, null);
    }

    public @NotNull AddNodeResult canAddNode(INode newNode) {
        return canAddNode(newNode, null);
    }

    //~ if >=1.20 'TileEntity' -> 'BlockEntity' {
    public @NotNull AddNodeResult canAddNode(INode newNode, @Nullable TileEntity blockEntity) {
        //~}
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
        if (hasHubConflict(newNode)) {
            return AddNodeResult.failure(AddNodeResult.Status.HUB_CONFLICT);
        }
        if (NodeEventHooks.postAddNodePre(newNode, blockEntity)) {
            return AddNodeResult.failure(AddNodeResult.Status.EVENT_CANCELED);
        }
        return AddNodeResult.success(null);
    }

    //~ if >=1.20 'TileEntity' -> 'BlockEntity' {
    public synchronized @NotNull AddNodeResult addNode(INode newNode, @Nullable TileEntity blockEntity) {
        //~}
        AddNodeResult validation = canAddNode(newNode, blockEntity);
        if (!validation.isSuccess()) {
            return validation;
        }
        MachineBindingIndex.INSTANCE.beginTopologyTransaction();
        try {
            return performAddNode(newNode, blockEntity);
        } finally {
            MachineBindingIndex.INSTANCE.endTopologyTransaction();
        }
    }

    //~ if >=1.20 'TileEntity' -> 'BlockEntity' {
    private @NotNull AddNodeResult performAddNode(INode newNode, @Nullable TileEntity blockEntity) {
        //~}
        int dimId = getDimensionId(newNode);
        INode replacedNode = getIndexedNode(dimId, newNode.getPos());
        if (replacedNode != null && replacedNode != newNode) {
            removeNodeInternal(replacedNode);
        }
        activeNodes.add(newNode);
        try {
            registerNodeIndices(dimId, newNode);
        } catch (RuntimeException e) {
            unregisterNodeIndices(dimId, newNode);
            activeNodes.remove(newNode);
            throw e;
        }

        ReferenceSet<INode> candidates = new ReferenceOpenHashSet<>();
        var scopeMap = scopeNode.get(dimId);
        for (long chunkCoord : nodeScope.get(dimId).get(newNode)) {
            candidates.addAll(scopeMap.get(chunkCoord));
        }
        candidates.remove(newNode);

        Reference2ObjectMap<INode, INode.LinkType> linkCache = new Reference2ObjectOpenHashMap<>();
        for (INode existing : candidates) {
            if (!existing.isActive()) continue;
            var linkType = newNode.linkScopeCheck(existing);
            if (linkType == INode.LinkType.DISCONNECT) continue;
            linkCache.put(existing, linkType);
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

    private boolean hasHubConflict(INode newNode) {
        int hubCount = (newNode instanceof IHubNode) ? 1 : 0;
        INode replacedNode = getIndexedNode(getDimensionId(newNode), newNode.getPos());
        ReferenceSet<IGrid> countedGrids = new ReferenceOpenHashSet<>();
        for (INode existing : getLinkedNodesForNode(newNode)) {
            if (isSamePositionNode(newNode, existing)) {
                continue;
            }
            IGrid grid = existing.getGrid();
            if (grid == null || !countedGrids.add(grid)) {
                continue;
            }
            var hub = grid.getHubNode();
            if (hub != null && hub != replacedNode && hub.isActive()) {
                hubCount++;
                if (hubCount > 1) {
                    return true;
                }
            }
        }
        return false;
    }

    @Nullable
    private INode getIndexedNode(int dimId, BlockPos pos) {
        var pMap = posNodes.get(dimId);
        if (pMap == posNodes.defaultReturnValue()) {
            return null;
        }
        //~ if >=1.20 '.toLong()' -> '.asLong()' {
        return pMap.get(pos.toLong());
        //~}
    }

    private static boolean isSamePositionNode(INode a, INode b) {
        return a != null && b != null && a.getDimensionId() == b.getDimensionId() && a.getPos().equals(b.getPos());
    }

    private @NotNull ReferenceSet<INode> getLinkedNodesForNode(INode newNode) {
        int dimId = getDimensionId(newNode);
        ReferenceSet<INode> linkedNodes = new ReferenceOpenHashSet<>();
        var scopeMap = scopeNode.get(dimId);
        if (scopeMap == scopeNode.defaultReturnValue()) {
            return linkedNodes;
        }
        for (long chunkCoord : getCoveredChunks(newNode)) {
            ReferenceSet<INode> nodes = scopeMap.get(chunkCoord);
            for (INode existing : nodes) {
                if (existing.isActive() && newNode.linkScopeCheck(existing) != INode.LinkType.DISCONNECT) {
                    linkedNodes.add(existing);
                }
            }
        }
        return linkedNodes;
    }

    private @NotNull LongSet getCoveredChunks(INode node) {
        BlockPos pos = node.getPos();
        int range = (int) node.getLinkScope();
        int minChunkX = (pos.getX() - range) >> 4;
        int maxChunkX = (pos.getX() + range) >> 4;
        int minChunkZ = (pos.getZ() - range) >> 4;
        int maxChunkZ = (pos.getZ() + range) >> 4;
        LongSet chunksCovered = new LongOpenHashSet();
        for (int cx = minChunkX; cx <= maxChunkX; ++cx) {
            for (int cz = minChunkZ; cz <= maxChunkZ; ++cz) {
                chunksCovered.add(ChunkCoordUtils.mergeChunkCoords(cx, cz));
            }
        }
        return chunksCovered;
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
        markGridDirty(grid);
        return grid;
    }

    private void destroyGrid(IGrid grid) {
        grids.remove(grid.getId());
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
        serializedDimensionKeys.clear();
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
            finishInitGrid(entries);
            return;
        }

        File[] files = f.listFiles(file -> file.isFile() && file.getName().endsWith(".dat"));
        if (files == null || files.length == 0) {
            finishInitGrid(entries);
            return;
        }

        for (File file : files) {
            var nbt = tryReadCompressedNbt(file, "grid file " + file.getName());
            if (nbt == null) {
                continue;
            }
            //? if <1.20 {
            if (!nbt.hasKey("dim")) continue;
            //?} else {
            /*if (!nbt.contains("dim")) continue;
            var dimLoc = parseDimensionLocation(nbt.getString("dim"));
            var dimResKey = net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, dimLoc);
            var server = getCurrentServer();
            if (server == null || server.getLevel(dimResKey) == null) continue;
            int dimId = dimLoc.hashCode();
            *///?}
            var grid = Grid.deserialize(nbt);
            if (grid == null) continue;
            if (grid.getNodes().isEmpty()) {
                if (!file.delete()) {
                    CirculationFlowNetworks.LOGGER.warn("Failed to delete empty grid file {}", file.getAbsolutePath());
                }
                continue;
            }

            Integer resolvedDimId = resolveGridDimension(grid);
            if (resolvedDimId == null || !isRegisteredDimension(resolvedDimId)) {
                CirculationFlowNetworks.LOGGER.warn("Skipping grid {} due to inconsistent or unavailable dimension data", file.getName());
                continue;
            }
            entries.add(new GridEntry(resolvedDimId, grid));
        }

        for (var entry : entries) {
            var grid = entry.grid();
            grids.put(grid.getId(), grid);

            var registered = new ObjectArrayList<INode>();
            boolean collision = false;
            for (INode node : grid.getNodes()) {
                var l = posNodes.get(entry.dimId);
                //~ if >=1.20 '.toLong()' -> '.asLong()' {
                var p = node.getPos().toLong();
                //~}
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
                grid.getNodes().clear();
                grid.setHubNode(null);
            }
        }
        finishInitGrid(entries);
    }

    private void finishInitGrid(Collection<GridEntry> entries) {
        EnergyMachineManager.INSTANCE.initGrid(entries);
        ChargingManager.INSTANCE.initGrid(entries);
        validationTracker.mergeEarlyIntoPending();
        init = true;
        validatePendingNodesInLoadedWorlds();
    }

    private static final class NodeValidationTracker {
        private final Int2ObjectMap<Long2ObjectMap<LongSet>> pending = new Int2ObjectOpenHashMap<>();
        private final Int2ObjectMap<LongOpenHashSet> early = new Int2ObjectOpenHashMap<>();

        {
            pending.defaultReturnValue(Long2ObjectMaps.emptyMap());
        }

        void markEarly(int dimId, BlockPos pos) {
            var positions = early.get(dimId);
            if (positions == null) {
                early.put(dimId, positions = new LongOpenHashSet());
            }
            //~ if >=1.20 '.toLong()' -> '.asLong()' {
            positions.add(pos.toLong());
            //~}
        }

        void clearEarly(int dimId, BlockPos pos) {
            var positions = early.get(dimId);
            if (positions != null) {
                //~ if >=1.20 '.toLong()' -> '.asLong()' {
                positions.remove(pos.toLong());
                //~}
                if (positions.isEmpty()) {
                    early.remove(dimId);
                }
            }
        }

        void mergeEarlyIntoPending() {
            for (var entry : early.int2ObjectEntrySet()) {
                int dimId = entry.getIntKey();
                for (long posLong : entry.getValue()) {
                    //~ if >=1.20 '.fromLong(' -> '.of(' {
                    markPending(dimId, BlockPos.fromLong(posLong));
                    //~}
                }
            }
            early.clear();
        }

        void markPending(int dimId, BlockPos pos) {
            long chunkCoord = ChunkCoordUtils.mergeChunkCoords(pos);
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
            //~ if >=1.20 '.toLong()' -> '.asLong()' {
            positions.add(pos.toLong());
            //~}
        }

        void removePending(int dimId, BlockPos pos) {
            //~ if >=1.20 '.toLong()' -> '.asLong()' {
            long posLong = pos.toLong();
            //~}
            long chunkCoord = ChunkCoordUtils.mergeChunkCoords(pos);
            removePendingInternal(dimId, posLong, chunkCoord);
        }

        void removePending(int dimId, long posLong) {
            //~ if >=1.20 '.fromLong(' -> '.of(' {
            long chunkCoord = ChunkCoordUtils.mergeChunkCoords(BlockPos.fromLong(posLong));
            //~}
            removePendingInternal(dimId, posLong, chunkCoord);
        }

        private void removePendingInternal(int dimId, long posLong, long chunkCoord) {
            var dimMap = pending.get(dimId);
            if (dimMap == pending.defaultReturnValue()) return;
            var positions = dimMap.get(chunkCoord);
            if (positions == dimMap.defaultReturnValue()) return;
            positions.remove(posLong);
            if (positions.isEmpty()) dimMap.remove(chunkCoord);
            if (dimMap.isEmpty()) pending.remove(dimId);
        }

        void removeIfRegistered(int dimId, BlockPos pos, Long2ReferenceMap<INode> posNodesForDim, INode node) {
            //~ if >=1.20 '.toLong()' -> '.asLong()' {
            if (node != null && posNodesForDim.get(pos.toLong()) == node) {
                //~}
                removePending(dimId, pos);
            }
        }

        @Nullable LongSet getChunkPositions(int dimId, long chunkCoord) {
            var dimMap = pending.get(dimId);
            if (dimMap == pending.defaultReturnValue()) return null;
            var positions = dimMap.get(chunkCoord);
            return positions == dimMap.defaultReturnValue() ? null : positions;
        }

        boolean isPendingEmpty() {
            return pending.isEmpty();
        }

        ObjectArrayList<Int2ObjectMap.Entry<Long2ObjectMap<LongSet>>> pendingDimensionsSnapshot() {
            return new ObjectArrayList<>(pending.int2ObjectEntrySet());
        }

        void batchRemovePending(int dimId, LongArrayList positions) {
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

    //? if <1.20 {
    @Desugar
        //?}
    record GridEntry(int dimId, IGrid grid) {
    }
}
