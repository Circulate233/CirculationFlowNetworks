package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.api.IGrid;
import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.network.Grid;
import com.circulation.circulation_networks.packets.NodeNetworkRendering;
import com.circulation.circulation_networks.proxy.CommonProxy;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;
import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import it.unimi.dsi.fastutil.objects.ReferenceSets;
import lombok.Getter;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.List;
import java.util.Queue;

@SuppressWarnings("unused")
public final class NetworkManager {

    public static final NetworkManager INSTANCE = new NetworkManager();

    @Getter
    private final ReferenceSet<INode> activeNodes = new ReferenceOpenHashSet<>();
    private final Int2ObjectMap<IGrid> grids = new Int2ObjectOpenHashMap<>();
    private int nextGridId = 0;

    private final Reference2ObjectMap<World, Object2ObjectMap<ChunkPos, ReferenceSet<INode>>> scopeNode = new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectMap<World, Object2ObjectMap<INode, ObjectSet<ChunkPos>>> nodeScope = new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectMap<World, Object2ObjectMap<ChunkPos, ReferenceSet<INode>>> nodeLocation = new Reference2ObjectLinkedOpenHashMap<>();

    public Collection<IGrid> getAllGrids() {
        return grids.values();
    }

    /**
     * @param world w
     * @param pos   p
     * @return 此位置的节点
     */
    public static @Nullable INode getNodeFromPos(World world, BlockPos pos) {
        var te = world.getTileEntity(pos);
        if (te != null) return te.getCapability(CommonProxy.nodeCapability, null);
        return null;
    }

    public void removeNode(INode removedNode) {
        if (removedNode == null || removedNode.getWorld().isRemote || !activeNodes.remove(removedNode)) return;

        var players = NodeNetworkRendering.getPlayers(removedNode.getGrid());
        if (players != null && !players.isEmpty()) {
            for (var player : players) {
                CirculationFlowNetworks.NET_CHANNEL.sendTo(
                    new NodeNetworkRendering(player, removedNode, NodeNetworkRendering.NODE_REMOVE), player);
            }
        }

        var world = removedNode.getWorld();
        ChunkPos ownChunk = new ChunkPos(removedNode.getPos());
        nodeLocation.get(world).get(ownChunk).remove(removedNode);

        ObjectSet<ChunkPos> coveredChunks = nodeScope.get(world).remove(removedNode);
        if (coveredChunks != null) {
            for (var chunk : coveredChunks) {
                var set = scopeNode.get(world).get(chunk);
                if (set == scopeNode.get(world).defaultReturnValue()) continue;
                if (set.size() == 1) scopeNode.get(world).remove(chunk);
                else set.remove(removedNode);
            }
        }

        IGrid oldGrid = removedNode.getGrid();

        for (INode neighbor : removedNode.getNeighbors()) {
            neighbor.removeNeighbor(removedNode);
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

                for (int i = 1; i < components.size(); i++) {
                    IGrid splitGrid = allocGrid();
                    for (INode n : components.get(i)) {
                        assignNodeToGrid(n, splitGrid);
                    }
                }
            }
        }

        EnergyMachineManager.INSTANCE.removeNode(removedNode);
        ChargingManager.INSTANCE.removeNode(removedNode);
    }

    public void onServerStop() {
        scopeNode.clear();
        nodeScope.clear();
        nodeLocation.clear();
        activeNodes.clear();
        grids.clear();
        nextGridId = 0;
    }

    /**
     * @param world w
     * @param pos pos
     * @return 可能链接到此位置的所有节点
     */
    public @Nonnull ReferenceSet<INode> getNodesCoveringPosition(World world, BlockPos pos) {
        return getNodesCoveringPosition(world, new ChunkPos(pos));
    }

    /**
     * @param world w
     * @param pos p
     * @return 可能链接到此区块位置的所有节点
     */
    public @Nonnull ReferenceSet<INode> getNodesCoveringPosition(World world, ChunkPos pos) {
        return scopeNode.computeIfAbsent(world, l -> {
            var ma = new Object2ObjectOpenHashMap<ChunkPos, ReferenceSet<INode>>();
            ma.defaultReturnValue(ReferenceSets.emptySet());
            return ma;
        }).get(pos);
    }

    private static void assignNodeToGrid(INode node, IGrid grid) {
        grid.getNodes().add(node);
        node.setGrid(grid);
    }

    /**
     * @param world w
     * @param chunk c
     * @return 区块内的所有节点
     */
    public @Nonnull ReferenceSet<INode> getNodesInChunk(World world, ChunkPos chunk) {
        return nodeLocation.computeIfAbsent(world, l -> {
            var ma = new Object2ObjectOpenHashMap<ChunkPos, ReferenceSet<INode>>();
            ma.defaultReturnValue(ReferenceSets.emptySet());
            return ma;
        }).get(chunk);
    }

    /**
     * @param node n
     * @return 被节点范围覆盖的区块
     */
    public @Nonnull ObjectSet<ChunkPos> getCoveredChunks(INode node) {
        return nodeScope.get(node.getWorld()).get(node);
    }

    private IGrid allocGrid() {
        IGrid grid = new Grid(nextGridId++);
        grids.put(grid.getId(), grid);
        EnergyMachineManager.INSTANCE.getInteraction().put(grid, new EnergyMachineManager.Interaction());
        return grid;
    }

    public void addNode(INode newNode, TileEntity te) {
        if (newNode == null || newNode.getWorld().isRemote || !newNode.isActive() || activeNodes.contains(newNode))
            return;

        var world = newNode.getWorld();
        activeNodes.add(newNode);
        var pos = newNode.getPos();

        ChunkPos ownChunk = new ChunkPos(pos);
        var locMap = nodeLocation.computeIfAbsent(world, k -> {
            var ma = new Object2ObjectOpenHashMap<ChunkPos, ReferenceSet<INode>>();
            ma.defaultReturnValue(ReferenceSets.emptySet());
            return ma;
        });
        var locSet = locMap.get(ownChunk);
        if (locSet == locMap.defaultReturnValue()) {
            locMap.put(ownChunk, locSet = new ReferenceOpenHashSet<>());
        }
        locSet.add(newNode);

        int nodeX = pos.getX();
        int nodeZ = pos.getZ();
        int range = (int) newNode.getLinkScope();
        int minChunkX = (nodeX - range) >> 4, maxChunkX = (nodeX + range) >> 4;
        int minChunkZ = (nodeZ - range) >> 4, maxChunkZ = (nodeZ + range) >> 4;
        ObjectSet<ChunkPos> chunksCovered = new ObjectOpenHashSet<>();
        for (int cx = minChunkX; cx <= maxChunkX; ++cx)
            for (int cz = minChunkZ; cz <= maxChunkZ; ++cz)
                chunksCovered.add(new ChunkPos(cx, cz));

        ReferenceSet<INode> candidates = new ReferenceOpenHashSet<>();
        for (var chunkPos : chunksCovered) {
            var scopeMap = scopeNode.computeIfAbsent(world, l -> {
                var ma = new Object2ObjectOpenHashMap<ChunkPos, ReferenceSet<INode>>();
                ma.defaultReturnValue(ReferenceSets.emptySet());
                return ma;
            });
            var set1 = scopeMap.get(chunkPos);
            candidates.addAll(set1);
            if (set1 == scopeMap.defaultReturnValue()) {
                scopeMap.put(chunkPos, set1 = new ReferenceOpenHashSet<>());
            }
            set1.add(newNode);
        }
        nodeScope.computeIfAbsent(world, l -> {
            var ma = new Object2ObjectOpenHashMap<INode, ObjectSet<ChunkPos>>();
            ma.defaultReturnValue(ObjectSets.emptySet());
            return ma;
        }).put(newNode, ObjectSets.unmodifiable(chunksCovered));

        assignNodeToGrid(newNode, allocGrid());

        for (INode existing : candidates) {
            if (existing == newNode || !existing.isActive()) continue;
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
            IGrid ng = existing.getGrid();
            IGrid cg = newNode.getGrid();
            if (ng != null && ng != cg) {
                IGrid dst = cg.getNodes().size() >= ng.getNodes().size() ? cg : ng;
                IGrid src = dst == cg ? ng : cg;
                for (INode n : src.getNodes()) {
                    dst.getNodes().add(n);
                    n.setGrid(dst);
                }
                src.getNodes().clear();
                destroyGrid(src);
            }
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

    private void destroyGrid(IGrid grid) {
        grids.remove(grid.getId());
        EnergyMachineManager.INSTANCE.getInteraction().remove(grid);
    }
}