package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.api.IGrid;
import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.network.Grid;
import com.circulation.circulation_networks.proxy.CommonProxy;
import com.circulation.circulation_networks.utils.UnionFindUtils;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@SuppressWarnings("unused")
public final class NetworkManager {
    public static final NetworkManager INSTANCE = new NetworkManager();
    @Getter
    private final ReferenceSet<INode> activeNodes = new ReferenceOpenHashSet<>();
    private final Int2ObjectMap<IGrid> grids = new Int2ObjectOpenHashMap<>();

    private final Reference2ObjectMap<World, Object2ObjectMap<ChunkPos, ReferenceSet<INode>>> scopeNode = new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectMap<World, Object2ObjectMap<INode, ObjectSet<ChunkPos>>> nodeScope = new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectMap<World, Object2ObjectMap<ChunkPos, ReferenceSet<INode>>> nodeLocation = new Reference2ObjectLinkedOpenHashMap<>();

    public Collection<IGrid> getAllGrids() {
        return grids.values();
    }

    public void addNode(INode newNode) {
        if (newNode == null || newNode.getWorld().isRemote || !newNode.isActive() || activeNodes.contains(newNode))
            return;

        var world = newNode.getWorld();
        activeNodes.add(newNode);
        UnionFindUtils.makeSet(newNode);
        var pos = newNode.getPos();

        ChunkPos ownChunk = new ChunkPos(pos);
        var m = nodeLocation.computeIfAbsent(world, k -> {
            var ma = new Object2ObjectOpenHashMap<ChunkPos, ReferenceSet<INode>>();
            ma.defaultReturnValue(ReferenceSets.emptySet());
            return ma;
        });
        var set = m.get(ownChunk);
        if (set == m.defaultReturnValue()) {
            m.put(ownChunk, set = new ReferenceOpenHashSet<>());
        }
        set.add(newNode);
        int nodeX = newNode.getPos().getX();
        int nodeZ = newNode.getPos().getZ();
        int range = (int) newNode.getLinkScope();
        int minChunkX = (nodeX - range) >> 4;
        int maxChunkX = (nodeX + range) >> 4;
        int minChunkZ = (nodeZ - range) >> 4;
        int maxChunkZ = (nodeZ + range) >> 4;
        ObjectSet<ChunkPos> chunksCovered = new ObjectOpenHashSet<>();
        for (int cx = minChunkX; cx <= maxChunkX; ++cx) {
            for (int cz = minChunkZ; cz <= maxChunkZ; ++cz) {
                chunksCovered.add(new ChunkPos(cx, cz));
            }
        }
        if (chunksCovered.isEmpty()) return;
        var nodes = new ReferenceOpenHashSet<INode>();
        for (var chunkPos : chunksCovered) {
            var m1 = scopeNode.computeIfAbsent(world, l -> {
                var ma = new Object2ObjectOpenHashMap<ChunkPos, ReferenceSet<INode>>();
                ma.defaultReturnValue(ReferenceSets.emptySet());
                return ma;
            });
            var set1 = m1.get(chunkPos);
            nodes.addAll(set1);
            if (set1 == m1.defaultReturnValue()) {
                scopeNode.get(world).put(chunkPos, set1 = new ReferenceOpenHashSet<>());
            }
            set1.add(newNode);
        }
        nodeScope.computeIfAbsent(world, l -> {
            var ma = new Object2ObjectOpenHashMap<INode, ObjectSet<ChunkPos>>();
            ma.defaultReturnValue(ObjectSets.emptySet());
            return ma;
        }).put(newNode, ObjectSets.unmodifiable(chunksCovered));

        List<INode> newNeighbors = new ObjectArrayList<>();
        for (INode existing : nodes) {
            if (existing == newNode || !existing.isActive()) continue;

            switch (newNode.linkScopeCheck(existing)) {
                case DOUBLY -> {
                    newNode.addNeighbor(existing);
                    existing.addNeighbor(newNode);
                    newNeighbors.add(existing);
                }
                case A_TO_B -> {
                    newNode.addNeighbor(existing);
                    newNeighbors.add(existing);
                }
                case B_TO_A -> existing.addNeighbor(newNode);
            }
        }

        for (INode neighbor : newNeighbors) {
            if (UnionFindUtils.find(newNode) != UnionFindUtils.find(neighbor)) {
                UnionFindUtils.union(newNode, neighbor);
            }
        }

        updateNetworks();

        EnergyMachineManager.INSTANCE.addNode(newNode);
        ChargingManager.INSTANCE.addNode(newNode);
    }

    public void removeNode(INode removedNode) {
        if (removedNode == null || removedNode.getWorld().isRemote || !activeNodes.remove(removedNode)) return;

        var world = removedNode.getWorld();
        var pos = removedNode.getPos();
        ChunkPos ownChunk = new ChunkPos(pos);
        nodeLocation.get(world).get(ownChunk).remove(removedNode);

        ObjectSet<ChunkPos> coveredChunks = nodeScope.get(world).remove(removedNode);
        if (coveredChunks == null || coveredChunks.isEmpty()) return;
        for (var coveredChunk : coveredChunks) {
            var set = scopeNode.get(world).get(coveredChunk);
            if (set == scopeNode.get(world).defaultReturnValue()) {
                continue;
            }
            if (set.size() == 1) scopeNode.get(world).remove(coveredChunk);
            else set.remove(removedNode);
        }

        for (INode neighbor : removedNode.getNeighbors()) {
            neighbor.removeNeighbor(removedNode);
        }
        removedNode.clearNeighbors();

        Set<INode> affected = new ReferenceOpenHashSet<>();
        if (removedNode.getParent() == removedNode) {
            removedNode.getGrid().getNodes().forEach(node -> {
                node.setParent(node);
                affected.add(node);
            });
        }

        removedNode.setActive(false);

        for (INode neighbor : removedNode.getNeighbors()) {
            if (neighbor.isActive()) {
                affected.add(neighbor);
            }
        }

        for (INode node : affected) {
            UnionFindUtils.makeSet(node);
            for (INode neighbor : node.getNeighbors()) {
                if (neighbor.isActive() &&
                    UnionFindUtils.find(node) != UnionFindUtils.find(neighbor)) {
                    UnionFindUtils.union(node, neighbor);
                }
            }
        }

        updateNetworks();

        EnergyMachineManager.INSTANCE.removeNode(removedNode);
        ChargingManager.INSTANCE.removeNode(removedNode);
    }

    public void onServerStop() {
        scopeNode.clear();
        nodeScope.clear();
        nodeLocation.clear();
        activeNodes.clear();
        grids.clear();
    }

    /**
     * 获取可能覆盖指定位置的节点
     *
     * @param pos 目标位置
     * @return 可能覆盖该位置的节点集合
     */
    public @Nonnull ReferenceSet<INode> getNodesCoveringPosition(World world, BlockPos pos) {
        return getNodesCoveringPosition(world, new ChunkPos(pos));
    }

    /**
     * 获取可能覆盖指定区块的节点
     *
     * @param pos 目标位置
     * @return 可能覆盖该区块的节点集合
     */
    public @Nonnull ReferenceSet<INode> getNodesCoveringPosition(World world, ChunkPos pos) {
        return scopeNode.computeIfAbsent(world, l -> {
            var ma = new Object2ObjectOpenHashMap<ChunkPos, ReferenceSet<INode>>();
            ma.defaultReturnValue(ReferenceSets.emptySet());
            return ma;
        }).get(pos);
    }

    /**
     * @param pos 节点位置
     * @return 指定坐标可能存在的节点
     */
    public @Nullable INode getNodeFromPos(World world, BlockPos pos) {
        var te = world.getTileEntity(pos);
        if (te != null) {
            return te.getCapability(CommonProxy.nodeCapability, null);
        }
        return null;
    }

    /**
     * 获取指定区块内的节点
     *
     * @param chunk 目标区块
     * @return 位于该区块的节点集合
     */
    public @Nonnull ReferenceSet<INode> getNodesInChunk(World world, ChunkPos chunk) {
        return nodeLocation.computeIfAbsent(world, l -> {
            var ma = new Object2ObjectOpenHashMap<ChunkPos, ReferenceSet<INode>>();
            ma.defaultReturnValue(ReferenceSets.emptySet());
            return ma;
        }).get(chunk);
    }

    /**
     * 获取节点覆盖的区块
     *
     * @param node 目标节点
     * @return 该节点覆盖的区块集合
     */
    public @Nonnull ObjectSet<ChunkPos> getCoveredChunks(INode node) {
        return nodeScope.get(node.getWorld()).get(node);
    }

    private void updateNetworks() {
        grids.clear();
        EnergyMachineManager.INSTANCE.getInteraction().clear();

        for (INode node : activeNodes) {
            INode root = UnionFindUtils.find(node);
            if (root == null) continue;

            int gridId = root.hashCode();
            IGrid grid = getGrid(gridId);
            grid.getNodes().add(node);
            node.setGrid(grid);
        }
        for (var value : grids.values()) {
            EnergyMachineManager.INSTANCE.getInteraction().put(value, new EnergyMachineManager.Interaction());
        }
    }

    private IGrid getGrid(int i) {
        var o = grids.get(i);
        if (o == null) {
            grids.put(i, o = new Grid(i));
        }
        return o;
    }
}