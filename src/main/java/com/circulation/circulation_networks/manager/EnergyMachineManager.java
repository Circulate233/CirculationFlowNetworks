package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.api.IGrid;
import com.circulation.circulation_networks.api.node.IEnergySupplyNode;
import com.circulation.circulation_networks.api.node.IMachineNode;
import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.packets.NodeNetworkRendering;
import com.circulation.circulation_networks.registry.RegistryEnergyHandler;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;
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
import net.minecraft.world.chunk.Chunk;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import static com.circulation.circulation_networks.CirculationFlowNetworks.server;

public final class EnergyMachineManager {

    public static final EnergyMachineManager INSTANCE = new EnergyMachineManager();
    private final Reference2ObjectMap<World, Object2ObjectMap<ChunkPos, ReferenceSet<IEnergySupplyNode>>> scopeNode = new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectMap<World, Object2ObjectMap<IEnergySupplyNode, ObjectSet<ChunkPos>>> nodeScope = new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectMap<INode, Set<TileEntity>> gridMachineMap = new Reference2ObjectOpenHashMap<>();
    @Getter
    private final WeakHashMap<TileEntity, ReferenceSet<INode>> machineGridMap = new WeakHashMap<>();
    @Getter
    private final Reference2ObjectMap<IGrid, Interaction> interaction = new Reference2ObjectOpenHashMap<>();

    {
        gridMachineMap.defaultReturnValue(ReferenceSets.emptySet());
    }

    static void transferEnergy(Collection<IEnergyHandler> send, Collection<IEnergyHandler> receive, Status status, IGrid grid) {
        if (send.isEmpty() || receive.isEmpty()) return;
        var si = send.iterator();
        while (si.hasNext()) {
            var sender = si.next();
            if (receive.isEmpty()) return;
            var ri = receive.iterator();
            while (ri.hasNext()) {
                var receiver = ri.next();
                if (sender.canExtract() && receiver.canReceive()) {
                    var e = sender.canExtractValue();
                    var r = receiver.canReceiveValue();
                    if (e > r) {
                        if (r != 0) status.interaction(receiver.receiveEnergy(sender.extractEnergy(r)), grid);
                        if (receiver.getType() != IEnergyHandler.EnergyType.STORAGE) {
                            receiver.recycle();
                            ri.remove();
                        }
                    } else if (e == r) {
                        if (e != 0) status.interaction(receiver.receiveEnergy(sender.extractEnergy(r)), grid);
                        sender.recycle();
                        si.remove();
                        if (receiver.getType() != IEnergyHandler.EnergyType.STORAGE) {
                            receiver.recycle();
                            ri.remove();
                        }
                        break;
                    } else {
                        if (e != 0) status.interaction(receiver.receiveEnergy(sender.extractEnergy(e)), grid);
                        sender.recycle();
                        si.remove();
                        break;
                    }
                }
            }
        }
    }

    public void onServerTick() {
        if (server == null) return;
        interaction.values().forEach(Interaction::reset);
        var gridMap = new Reference2ObjectOpenHashMap<IGrid, EnumMap<IEnergyHandler.EnergyType, List<IEnergyHandler>>>();
        for (var entry : machineGridMap.entrySet()) {
            var te = entry.getKey();
            var handler = IEnergyHandler.release(te);

            if (handler == null) {
                continue;
            } else if (handler.getType() == IEnergyHandler.EnergyType.INVALID) {
                handler.recycle();
                continue;
            }

            for (var node : entry.getValue()) {
                gridMap.computeIfAbsent(node.getGrid(), g -> new EnumMap<>(IEnergyHandler.EnergyType.class))
                       .computeIfAbsent(handler.getType(), s -> new ObjectArrayList<>())
                       .add(handler);
            }
        }

        for (var e : gridMap.entrySet()) {
            var grid = e.getKey();
            var hanlers = e.getValue();
            var send = hanlers.getOrDefault(IEnergyHandler.EnergyType.SEND, ObjectLists.emptyList());
            var storage = hanlers.getOrDefault(IEnergyHandler.EnergyType.STORAGE, ObjectLists.emptyList());
            var receive = hanlers.getOrDefault(IEnergyHandler.EnergyType.RECEIVE, ObjectLists.emptyList());

            transferEnergy(send, receive, Status.INTERACTION, grid);
            transferEnergy(storage, receive, Status.EXTRACT, grid);
            transferEnergy(send, storage, Status.RECEIVE, grid);
        }

        ChargingManager.INSTANCE.onServerTick(gridMap);

        for (var value : gridMap.values()) {
            for (var handlers : value.values()) {
                for (var handler : handlers) {
                    handler.recycle();
                }
            }
        }
    }

    public void addMachine(TileEntity tileEntity) {
        if (!RegistryEnergyHandler.isEnergyTileEntity(tileEntity)) return;
        if (RegistryEnergyHandler.isBlack(tileEntity)) return;
        var pos = tileEntity.getPos();
        var chunkPos = new ChunkPos(pos);
        ReferenceSet<IEnergySupplyNode> set = scopeNode.computeIfAbsent(
            tileEntity.getWorld(), k -> {
                var m = new Object2ObjectOpenHashMap<ChunkPos, ReferenceSet<IEnergySupplyNode>>();
                m.defaultReturnValue(ReferenceSets.emptySet());
                return m;
            }
        ).get(chunkPos);
        if (!set.isEmpty()) {
            var s = machineGridMap.get(tileEntity);
            if (s == null) s = new ReferenceOpenHashSet<>();
            for (var node : set) {
                if (!node.supplyScopeCheck(pos)) continue;

                var set1 = gridMachineMap.get(node);
                if (set1 == gridMachineMap.defaultReturnValue()) {
                    gridMachineMap.put(node, set1 = Collections.newSetFromMap(new WeakHashMap<>()));
                }
                s.add(node);
                set1.add(tileEntity);

                var players = NodeNetworkRendering.getPlayers(node.getGrid());
                if (players != null && !players.isEmpty()) {
                    for (var player : players) {
                        CirculationFlowNetworks.NET_CHANNEL.sendTo(new NodeNetworkRendering(player, tileEntity, node, NodeNetworkRendering.MACHINE_ADD), player);
                    }
                }
            }
            if (s.isEmpty()) return;
            machineGridMap.putIfAbsent(tileEntity, s);
        }
    }

    public void removeMachine(TileEntity tileEntity) {
        var set = machineGridMap.remove(tileEntity);
        if (set == null || set.isEmpty()) return;
        for (var node : set) {
            gridMachineMap.get(node).remove(tileEntity);

            var players = NodeNetworkRendering.getPlayers(node.getGrid());
            if (players != null && !players.isEmpty()) {
                for (var player : players) {
                    CirculationFlowNetworks.NET_CHANNEL.sendTo(new NodeNetworkRendering(player, tileEntity, node, NodeNetworkRendering.MACHINE_REMOVE), player);
                }
            }
        }
    }

    public void addMachineNode(IMachineNode iMachineNode) {
        var te = iMachineNode.getTileEntity();
        var set = iMachineNode.getNeighbors();
        if (!set.isEmpty()) {
            var s = machineGridMap.get(te);
            if (s == null) s = new ReferenceOpenHashSet<>();
            for (var node : set) {
                var set1 = gridMachineMap.get(node);
                if (set1 == gridMachineMap.defaultReturnValue()) {
                    gridMachineMap.put(node, set1 = Collections.newSetFromMap(new WeakHashMap<>()));
                }
                s.add(node);
                set1.add(te);
            }
            if (s.isEmpty()) return;
            machineGridMap.putIfAbsent(te, s);
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
            ObjectSet<ChunkPos> chunksCovered = new ObjectOpenHashSet<>();
            for (int cx = minChunkX; cx <= maxChunkX; ++cx) {
                for (int cz = minChunkZ; cz <= maxChunkZ; ++cz) {
                    var chunkPos = new ChunkPos(cx, cz);
                    chunksCovered.add(chunkPos);

                    var map = scopeNode.computeIfAbsent(
                        node.getWorld(), k -> {
                            var m = new Object2ObjectOpenHashMap<ChunkPos, ReferenceSet<IEnergySupplyNode>>();
                            m.defaultReturnValue(ReferenceSets.emptySet());
                            return m;
                        }
                    );
                    var set1 = map.get(chunkPos);
                    if (set1 == map.defaultReturnValue()) {
                        map.put(chunkPos, set1 = new ReferenceOpenHashSet<>());
                    }
                    set1.add(energySupplyNode);

                    Chunk chunk = node.getWorld().getChunkProvider().getLoadedChunk(chunkPos.x, chunkPos.z);
                    if (chunk == null || chunk.isEmpty()) {
                        continue;
                    }
                    var set2 = gridMachineMap.get(node);
                    for (TileEntity tileEntity : chunk.getTileEntityMap().values()) {
                        if (!energySupplyNode.supplyScopeCheck(tileEntity.getPos())) continue;
                        if (RegistryEnergyHandler.isBlack(tileEntity)) continue;
                        if (RegistryEnergyHandler.isEnergyTileEntity(tileEntity)) {
                            if (set2 == gridMachineMap.defaultReturnValue()) {
                                gridMachineMap.put(energySupplyNode, set2 = Collections.newSetFromMap(new WeakHashMap<>()));
                            }
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

            nodeScope.computeIfAbsent(
                node.getWorld(), k -> {
                    var m = new Object2ObjectOpenHashMap<IEnergySupplyNode, ObjectSet<ChunkPos>>();
                    m.defaultReturnValue(ObjectSets.emptySet());
                    return m;
                }
            ).put(energySupplyNode, ObjectSets.unmodifiable(chunksCovered));

            if (energySupplyNode instanceof IMachineNode node1) addMachineNode(node1);
        }
    }

    public void removeNode(INode node) {
        if (node instanceof IEnergySupplyNode removedNode) {
            var world = removedNode.getWorld();
            ObjectSet<ChunkPos> coveredChunks = nodeScope.get(world).remove(removedNode);
            if (coveredChunks == null || coveredChunks.isEmpty()) return;
            for (var coveredChunk : coveredChunks) {
                var set = scopeNode.get(world).get(coveredChunk);
                if (set == null) {
                    continue;
                }
                if (set.size() == 1) scopeNode.get(world).remove(coveredChunk);
                else set.remove(removedNode);
            }

            Set<TileEntity> c = gridMachineMap.remove(removedNode);
            if (c == null || c.isEmpty()) return;
            for (var te : c) {
                var set = machineGridMap.get(te);
                if (set == null) {
                    continue;
                }
                if (set.size() == 1) machineGridMap.remove(te);
                else set.remove(removedNode);
            }
        }
    }

    public void onServerStop() {
        scopeNode.clear();
        nodeScope.clear();
        gridMachineMap.clear();
        machineGridMap.clear();
        interaction.clear();
    }

    /**
     * 获取范围包含此位置所在区块的所有节点
     *
     * @param pos 目标位置
     * @return 可能覆盖该位置的节点集合
     */
    public @Nonnull ReferenceSet<IEnergySupplyNode> getEnergyNodes(World world, BlockPos pos) {
        return getEnergyNodes(world, new ChunkPos(pos));
    }

    /**
     * 获取范围包含此区块的所有节点
     *
     * @param pos 目标位置
     * @return 可能覆盖该位置的节点集合
     */
    public @Nonnull ReferenceSet<IEnergySupplyNode> getEnergyNodes(World world, ChunkPos pos) {
        return scopeNode.getOrDefault(world, Object2ObjectMaps.emptyMap()).getOrDefault(pos, ReferenceSets.emptySet());
    }

    enum Status {
        EXTRACT,
        INTERACTION,
        RECEIVE;

        private void interaction(long value, IGrid grid) {
            var i = EnergyMachineManager.INSTANCE.interaction.get(grid);
            switch (this) {
                case INTERACTION -> {
                    i.input += value;
                    i.output += value;
                }
                case EXTRACT -> i.output += value;
                case RECEIVE -> i.input += value;
            }
        }
    }

    public static class Interaction {
        @Getter
        private long input = 0;
        @Getter
        private long output = 0;

        private void reset() {
            input = 0;
            output = 0;
        }
    }

}