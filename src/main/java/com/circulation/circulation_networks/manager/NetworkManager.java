package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.api.IGrid;
import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.network.Grid;
import com.circulation.circulation_networks.packets.NodeNetworkRendering;
import com.circulation.circulation_networks.proxy.CommonProxy;
import com.circulation.circulation_networks.utils.TileEntityLifeCycleEvent;
import com.github.bsideup.jabel.Desugar;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMaps;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import it.unimi.dsi.fastutil.objects.ReferenceSets;
import lombok.Getter;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@SuppressWarnings("unused")
public final class NetworkManager {

    public static final NetworkManager INSTANCE = new NetworkManager();

    @Getter
    private final ReferenceSet<INode> activeNodes = new ReferenceOpenHashSet<>();
    private final Int2ObjectMap<IGrid> grids = new Int2ObjectOpenHashMap<>();
    private static File saveFile;
    private final Int2ObjectMap<Long2ReferenceMap<INode>> posNodes = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<Object2ObjectMap<ChunkPos, ReferenceSet<INode>>> scopeNode = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<Object2ObjectMap<INode, ObjectSet<ChunkPos>>> nodeScope = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<Object2ObjectMap<ChunkPos, ReferenceSet<INode>>> nodeLocation = new Int2ObjectOpenHashMap<>();
    private final ObjectSet<IGrid> markGird = new ObjectOpenHashSet<>();
    private final Queue<IGrid> emptyGird = new ArrayDeque<>();
    private int nextGridId = 0;

    {
        posNodes.defaultReturnValue(Long2ReferenceMaps.emptyMap());
    }

    public static File getSaveFile() {
        if (saveFile == null) {
            var path = DimensionManager.getWorld(0)
                                       .getSaveHandler()
                                       .getWorldDirectory()
                                       .toPath()
                                       .resolve("circulation_grids");
            try {
                Files.createDirectories(path);
            } catch (IOException ignored) {
            }
            saveFile = path.toFile();
        }
        return saveFile;
    }

    private void registerNodeIndices(int dimId, INode node) {
        BlockPos pos = node.getPos();

        var pMap = posNodes.get(dimId);
        if (pMap == posNodes.defaultReturnValue()) {
            posNodes.put(dimId, pMap = new Long2ReferenceOpenHashMap<>());
        }
        pMap.put(pos.toLong(), node);

        ChunkPos ownChunk = new ChunkPos(pos);
        var locMap = nodeLocation.computeIfAbsent(dimId, k -> {
            var ma = new Object2ObjectOpenHashMap<ChunkPos, ReferenceSet<INode>>();
            ma.defaultReturnValue(ReferenceSets.emptySet());
            return ma;
        });
        var locSet = locMap.get(ownChunk);
        if (locSet == locMap.defaultReturnValue()) {
            locMap.put(ownChunk, locSet = new ReferenceOpenHashSet<>());
        }
        locSet.add(node);

        int range = (int) node.getLinkScope();
        int minChunkX = (pos.getX() - range) >> 4, maxChunkX = (pos.getX() + range) >> 4;
        int minChunkZ = (pos.getZ() - range) >> 4, maxChunkZ = (pos.getZ() + range) >> 4;
        ObjectSet<ChunkPos> chunksCovered = new ObjectOpenHashSet<>();

        var scopeMap = scopeNode.computeIfAbsent(dimId, l -> {
            var ma = new Object2ObjectOpenHashMap<ChunkPos, ReferenceSet<INode>>();
            ma.defaultReturnValue(ReferenceSets.emptySet());
            return ma;
        });
        for (int cx = minChunkX; cx <= maxChunkX; ++cx) {
            for (int cz = minChunkZ; cz <= maxChunkZ; ++cz) {
                ChunkPos cp = new ChunkPos(cx, cz);
                chunksCovered.add(cp);
                var sSet = scopeMap.get(cp);
                if (sSet == scopeMap.defaultReturnValue()) {
                    scopeMap.put(cp, sSet = new ReferenceOpenHashSet<>());
                }
                sSet.add(node);
            }
        }
        nodeScope.computeIfAbsent(dimId, l -> {
            var ma = new Object2ObjectOpenHashMap<INode, ObjectSet<ChunkPos>>();
            ma.defaultReturnValue(ObjectSets.emptySet());
            return ma;
        }).put(node, ObjectSets.unmodifiable(chunksCovered));
    }

    private void unregisterNodeIndices(int dimId, INode node) {
        posNodes.get(dimId).remove(node.getPos().toLong());

        ChunkPos ownChunk = new ChunkPos(node.getPos());
        nodeLocation.get(dimId).get(ownChunk).remove(node);

        var sm = scopeNode.get(dimId);
        ObjectSet<ChunkPos> coveredChunks = nodeScope.get(dimId).remove(node);
        if (coveredChunks != null && sm != null) {
            for (var chunk : coveredChunks) {
                var set = sm.get(chunk);
                if (set == sm.defaultReturnValue()) continue;
                if (set.size() == 1) sm.remove(chunk);
                else set.remove(node);
            }
        }
    }

    public @Nullable INode getNodeFromPos(World world, BlockPos pos) {
        return posNodes.get(world.provider.getDimension()).get(pos.toLong());
    }

    public void onTileEntityValidate(TileEntityLifeCycleEvent.Validate event) {
        int dimId = event.getWorld().provider.getDimension();
        if (event.getWorld().isRemote) return;
        var tileEntity = event.getTileEntity();
        addNode(tileEntity.getCapability(CommonProxy.nodeCapability, null), tileEntity);
    }

    public Collection<IGrid> getAllGrids() {
        return grids.values();
    }

    public void onTileEntityInvalidate(TileEntityLifeCycleEvent.Invalidate event) {
        if (event.getWorld().isRemote) return;
        removeNode(event.getWorld().provider.getDimension(), event.getPos());
    }

    public @Nonnull ReferenceSet<INode> getNodesCoveringPosition(World world, BlockPos pos) {
        return getNodesCoveringPosition(world, new ChunkPos(pos));
    }

    public @Nonnull ReferenceSet<INode> getNodesCoveringPosition(World world, ChunkPos pos) {
        return scopeNode.computeIfAbsent(world.provider.getDimension(), l -> {
            var ma = new Object2ObjectOpenHashMap<ChunkPos, ReferenceSet<INode>>();
            ma.defaultReturnValue(ReferenceSets.emptySet());
            return ma;
        }).get(pos);
    }

    public @Nonnull ReferenceSet<INode> getNodesInChunk(World world, ChunkPos chunk) {
        return nodeLocation.computeIfAbsent(world.provider.getDimension(), l -> {
            var ma = new Object2ObjectOpenHashMap<ChunkPos, ReferenceSet<INode>>();
            ma.defaultReturnValue(ReferenceSets.emptySet());
            return ma;
        }).get(chunk);
    }

    public @Nonnull ObjectSet<ChunkPos> getCoveredChunks(INode node) {
        return nodeScope.get(node.getWorld().provider.getDimension()).get(node);
    }

    public void removeNode(int dim, BlockPos pos) {
        var pMap = posNodes.get(dim);
        if (pMap != null && pMap != posNodes.defaultReturnValue()) {
            removeNode(pMap.get(pos.toLong()));
        }
    }

    public void removeNode(INode removedNode) {
        if (removedNode == null || removedNode.getWorld().isRemote || !activeNodes.remove(removedNode)) return;

        int dimId = removedNode.getWorld().provider.getDimension();

        var players = NodeNetworkRendering.getPlayers(removedNode.getGrid());
        if (players != null && !players.isEmpty()) {
            for (var player : players) {
                CirculationFlowNetworks.NET_CHANNEL.sendTo(
                    new NodeNetworkRendering(player, removedNode, NodeNetworkRendering.NODE_REMOVE), player);
            }
        }

        unregisterNodeIndices(dimId, removedNode);

        IGrid oldGrid = removedNode.getGrid();

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
        markGird.add(oldGrid);

        if (oldGrid.getNodes().isEmpty()) {
            destroyGrid(oldGrid);
        } else {
            ReferenceSet<INode> remaining = new ReferenceOpenHashSet<>(oldGrid.getNodes());
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
                        if (remaining.remove(nb)) {
                            component.add(nb);
                            queue.add(nb);
                        }
                    }
                    var iter = remaining.iterator();
                    while (iter.hasNext()) {
                        INode nb = iter.next();
                        if (nb.getNeighbors().contains(curr)) {
                            iter.remove();
                            component.add(nb);
                            queue.add(nb);
                        }
                    }
                }
                components.add(component);
            }

            if (components.size() > 1) {
                components.sort((a, b) -> b.size() - a.size());

                oldGrid.getNodes().clear();
                for (INode n : components.get(0)) {
                    oldGrid.getNodes().add(n);
                    n.setGrid(oldGrid);
                }
                markGird.add(oldGrid);

                var watchingPlayers = NodeNetworkRendering.getPlayers(oldGrid);
                for (int i = 1; i < components.size(); i++) {
                    IGrid splitGrid = allocGrid();
                    for (INode n : components.get(i)) {
                        assignNodeToGrid(n, splitGrid);
                    }
                    if (watchingPlayers != null) {
                        for (var player : watchingPlayers) {
                            CirculationFlowNetworks.NET_CHANNEL.sendTo(
                                new NodeNetworkRendering(player, splitGrid), player);
                        }
                    }
                }
            }
        }

        EnergyMachineManager.INSTANCE.removeNode(removedNode);
        ChargingManager.INSTANCE.removeNode(removedNode);
    }

    public void addNode(INode newNode, TileEntity te) {
        if (newNode == null || newNode.getWorld().isRemote || !newNode.isActive() || activeNodes.contains(newNode))
            return;

        int dimId = newNode.getWorld().provider.getDimension();
        activeNodes.add(newNode);
        registerNodeIndices(dimId, newNode);

        ReferenceSet<INode> candidates = new ReferenceOpenHashSet<>();
        var scopeMap = scopeNode.get(dimId);
        for (var chunkPos : nodeScope.get(dimId).get(newNode)) {
            candidates.addAll(scopeMap.get(chunkPos));
        }
        candidates.remove(newNode);

        for (INode existing : candidates) {
            if (!existing.isActive()) continue;
            var linkType = newNode.linkScopeCheck(existing);
            if (linkType == INode.LinkType.DISCONNECT) continue;
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
                for (INode n : src.getNodes()) {
                    dst.getNodes().add(n);
                    n.setGrid(dst);
                }
                src.getNodes().clear();
                destroyGrid(src);
                markGird.add(dst);
            }
        }

        if (newNode.getGrid() == null) {
            assignNodeToGrid(newNode, allocGrid());
        }

        var players = NodeNetworkRendering.getPlayers(newNode.getGrid());
        if (players != null && !players.isEmpty()) {
            for (var player : players) {
                CirculationFlowNetworks.NET_CHANNEL.sendTo(
                    new NodeNetworkRendering(player, newNode, NodeNetworkRendering.NODE_ADD), player);
            }
        }
        EnergyMachineManager.INSTANCE.addNode(newNode, te);
        ChargingManager.INSTANCE.addNode(newNode);
    }


    private void assignNodeToGrid(INode node, IGrid grid) {
        markGird.add(grid);
        grid.getNodes().add(node);
        node.setGrid(grid);
    }

    private IGrid allocGrid() {
        IGrid grid;
        if (!emptyGird.isEmpty()) {
            grid = emptyGird.poll();
            grid.getNodes().clear();
        } else {
            grid = new Grid(nextGridId++);
        }
        grids.put(grid.getId(), grid);
        EnergyMachineManager.INSTANCE.getInteraction().put(grid, new EnergyMachineManager.Interaction());
        markGird.add(grid);
        return grid;
    }

    private void destroyGrid(IGrid grid) {
        grids.remove(grid.getId());
        EnergyMachineManager.INSTANCE.getInteraction().remove(grid);
        markGird.add(grid);
        emptyGird.add(grid);
    }


    public void onServerStop() {
        scopeNode.clear();
        nodeScope.clear();
        nodeLocation.clear();
        activeNodes.clear();
        markGird.clear();
        emptyGird.clear();
        grids.clear();
        posNodes.clear();
        nextGridId = 0;
        saveFile = null;
    }

    public void saveGrid() {
        if (markGird.isEmpty()) return;
        markGird.parallelStream().forEach(grid -> {
            try {
                CompressedStreamTools.safeWrite(grid.serialize(), new File(NetworkManager.getSaveFile(), grid.getId() + ".dat"));
            } catch (IOException ignored) {
            }
        });
        markGird.clear();
    }

    public void initGrid() {
        var f = getSaveFile();
        if (!f.exists() || !f.isDirectory()) return;

        File[] files = f.listFiles(file -> file.isFile() && file.getName().endsWith(".dat"));
        if (files == null || files.length == 0) return;

        var nbts = new ConcurrentLinkedQueue<NBTTagCompound>();

        var entries = new ConcurrentLinkedQueue<GridEntry>();
        Arrays.stream(files).parallel().forEach(file -> {
            try {
                var nbt = CompressedStreamTools.read(file);
                if (nbt == null) return;
                int dimId = nbt.getInteger("dim");
                if (!DimensionManager.isDimensionRegistered(dimId)) return;
                var grid = Grid.deserialize(nbt);
                if (grid == null) return;
                entries.add(new GridEntry(dimId, grid));
            } catch (IOException ignored) {
            }
        });

        int maxId = 0;
        for (var entry : entries) {
            var grid = entry.grid();
            int gridId = grid.getId();
            if (gridId > maxId) maxId = gridId;
            grids.put(gridId, grid);
            EnergyMachineManager.INSTANCE.getInteraction().put(grid, new EnergyMachineManager.Interaction());

            if (grid.getNodes().isEmpty()) {
                emptyGird.add(grid);
            } else {
                for (INode node : grid.getNodes()) {
                    activeNodes.add(node);
                    registerNodeIndices(entry.dimId(), node);
                }
            }
        }
        nextGridId = maxId + 1;
    }

    @Desugar
    private record GridEntry(int dimId, IGrid grid) {
    }
}