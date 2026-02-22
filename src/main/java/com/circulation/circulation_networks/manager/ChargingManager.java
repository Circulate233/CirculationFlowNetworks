package com.circulation.circulation_networks.manager;

import baubles.api.BaublesApi;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.api.IGrid;
import com.circulation.circulation_networks.api.node.IChargingNode;
import com.circulation.circulation_networks.api.node.INode;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
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
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;

import static com.circulation.circulation_networks.CirculationFlowNetworks.server;
import static com.circulation.circulation_networks.manager.EnergyMachineManager.transferEnergy;

public final class ChargingManager {

    public static final ChargingManager INSTANCE = new ChargingManager();
    private static final boolean loadBaubles = Loader.isModLoaded("baubles");

    private final Reference2ObjectMap<World, Object2ObjectMap<ChunkPos, ReferenceSet<IChargingNode>>> scopeNode = new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectMap<World, Object2ObjectMap<IChargingNode, ObjectSet<ChunkPos>>> nodeScope = new Reference2ObjectOpenHashMap<>();

    @Optional.Method(modid = "baubles")
    private static void checkBaubles(Collection<IEnergyHandler> invs, EntityPlayer player) {
        var h = BaublesApi.getBaublesHandler(player);
        for (var i = 0; i < h.getSlots(); i++) {
            var stack = h.getStackInSlot(i);
            var handler = IEnergyHandler.release(stack);
            if (handler == null) continue;
            if (handler.canReceive()) {
                invs.add(handler);
                continue;
            }
            handler.recycle();
        }
    }

    public void onServerTick(Reference2ObjectOpenHashMap<IGrid, EnumMap<IEnergyHandler.EnergyType, List<IEnergyHandler>>> machineMap) {
        var gridMap = new Reference2ObjectOpenHashMap<IGrid, List<IEnergyHandler>>();
        gridMap.defaultReturnValue(ObjectLists.emptyList());
        var players = server.getPlayerList().getPlayers();
        p:
        for (var player : players) {
            var map = scopeNode.get(player.getEntityWorld());
            if (map != null) {
                var pos = player.getPosition();
                var set = map.get(new ChunkPos(pos));
                if (set.isEmpty()) continue;
                var invs = new ObjectArrayList<IEnergyHandler>();
                var w = false;
                for (var node : set) {
                    if (gridMap.containsKey(node.getGrid())) continue;
                    if (!node.chargingScopeCheck(pos)) continue;
                    if (!w) {
                        var inv = player.inventory;
                        for (var i = 0; i < inv.getSizeInventory(); i++) {
                            var stack = inv.getStackInSlot(i);
                            var handler = IEnergyHandler.release(stack);
                            if (handler == null) continue;
                            if (handler.canReceive()) {
                                invs.add(handler);
                                continue;
                            }
                            handler.recycle();
                        }
                        if (loadBaubles) checkBaubles(invs, player);
                        if (invs.isEmpty()) continue p;
                        w = true;
                    }
                    gridMap.put(node.getGrid(), invs);
                }
            }
        }

        for (var entry : gridMap.entrySet()) {
            var grid = entry.getKey();
            var receive = entry.getValue();

            var m = machineMap.get(grid);
            if (m != null) {
                var send = m.getOrDefault(IEnergyHandler.EnergyType.SEND, ObjectLists.emptyList());
                transferEnergy(send, receive, EnergyMachineManager.Status.EXTRACT, grid);

                var storage = machineMap.get(grid).getOrDefault(IEnergyHandler.EnergyType.STORAGE, ObjectLists.emptyList());
                transferEnergy(storage, receive, EnergyMachineManager.Status.EXTRACT, grid);
            }
        }

        for (var value : gridMap.values()) {
            for (var handler : value) {
                handler.recycle();
            }
        }
    }

    public void addNode(INode node) {
        if (node instanceof IChargingNode chargingNode) {
            int nodeX = chargingNode.getPos().getX();
            int nodeZ = chargingNode.getPos().getZ();
            int range = (int) chargingNode.getChargingScope();
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
                            var m = new Object2ObjectOpenHashMap<ChunkPos, ReferenceSet<IChargingNode>>();
                            m.defaultReturnValue(ReferenceSets.emptySet());
                            return m;
                        }
                    );
                    var set1 = map.get(chunkPos);
                    if (set1 == map.defaultReturnValue()) {
                        map.put(chunkPos, set1 = new ReferenceOpenHashSet<>());
                    }
                    set1.add(chargingNode);
                }
            }

            nodeScope.computeIfAbsent(
                node.getWorld(), k -> {
                    var m = new Object2ObjectOpenHashMap<IChargingNode, ObjectSet<ChunkPos>>();
                    m.defaultReturnValue(ObjectSets.emptySet());
                    return m;
                }
            ).put(chargingNode, ObjectSets.unmodifiable(chunksCovered));
        }
    }

    public void removeNode(INode node) {
        if (node instanceof IChargingNode chargingNode) {
            var world = chargingNode.getWorld();
            ObjectSet<ChunkPos> coveredChunks = nodeScope.get(world).remove(chargingNode);
            if (coveredChunks == null || coveredChunks.isEmpty()) return;
            for (var coveredChunk : coveredChunks) {
                var set = scopeNode.get(world).get(coveredChunk);
                if (set == null) {
                    continue;
                }
                if (set.size() == 1) scopeNode.get(world).remove(coveredChunk);
                else set.remove(chargingNode);
            }
        }
    }

    public void onServerStop() {
        scopeNode.clear();
        nodeScope.clear();
    }
}
