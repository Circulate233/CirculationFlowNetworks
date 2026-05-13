package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.api.IGrid;
import com.circulation.circulation_networks.api.hub.HubPermissionLevel;
import com.circulation.circulation_networks.api.node.IChargingNode;
import com.circulation.circulation_networks.api.node.IHubNode;
import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.network.hub.HubCapabilitys;
import com.circulation.circulation_networks.network.nodes.HubNode;
import com.circulation.circulation_networks.utils.AccessoryInventoryCompat;
import com.circulation.circulation_networks.utils.Functions;
import com.circulation.circulation_networks.utils.PlayerInventoryCompat;
import com.circulation.circulation_networks.utils.TombstoneReferenceBag;
import com.circulation.circulation_networks.utils.WorldResolveCompat;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import it.unimi.dsi.fastutil.objects.ReferenceSets;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.circulation.circulation_networks.manager.EnergyMachineManager.transferEnergy;

public final class ChargingManager {

    public static final ChargingManager INSTANCE = new ChargingManager();
    private static final boolean loadAccessoryIntegration = AccessoryInventoryCompat.isAccessoryIntegrationLoaded();
    private static final byte CHARGE_PREF_INVENTORY = 0x01;
    private static final byte CHARGE_PREF_HOTBAR = 0x02;
    private static final byte CHARGE_PREF_MAIN_HAND = 0x04;
    private static final byte CHARGE_PREF_OFF_HAND = 0x08;
    private static final byte CHARGE_PREF_ARMOR = 0x10;
    private static final byte CHARGE_PREF_ACCESSORY = 0x20;
    private static final byte CHARGE_PREF_ALL = 0b00111111;
    private final Object2ObjectMap<String, Long2ObjectMap<ReferenceSet<IChargingNode>>> scopeNode = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<String, Object2ObjectMap<IChargingNode, LongSet>> nodeScope = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<String, ReferenceSet<IHubNode>> wideAreaHubs = new Object2ObjectOpenHashMap<>();
    private final Reference2ObjectMap<IGrid, Set<EnergyTransferParticipant>> tickChargeTargetsByGrid = new Reference2ObjectOpenHashMap<>();
    private final ObjectList<IGrid> activeChargeTargetGrids = new ObjectArrayList<>();
    private final ReferenceSet<IGrid> processedTransferGrids = new ReferenceOpenHashSet<>();
    private final ChannelTransferScratch channelTransferScratch = new ChannelTransferScratch();
    private final ReferenceSet<IGrid> channelTransferGridsScratch = new ReferenceOpenHashSet<>();
    private final ObjectList<PlayerChargeState> playerStates = new ObjectArrayList<>();

    private static void collectChargeablesForGrid(IGrid grid,
                                                  Player player,
                                                  PlayerChargeState state,
                                                  Collection<EnergyTransferParticipant> result) {
        byte preferences = resolveChargingPreferenceMask(grid, player);
        if (preferences == 0) {
            return;
        }

        HubNode.HubMetadata hubMetadata = getHubMetadata(grid);

        if ((preferences & CHARGE_PREF_INVENTORY) != 0) {
            collectFromSlots(result, state.inventory, 9, state.inventory.size(), grid, hubMetadata);
        }
        if ((preferences & CHARGE_PREF_OFF_HAND) != 0) {
            collectFromStack(result, player.getOffhandItem(), grid, hubMetadata);
        }
        if ((preferences & CHARGE_PREF_HOTBAR) != 0) {
            collectFromSlots(result, state.inventory, 0, 9, grid, hubMetadata);
        } else {
            if ((preferences & CHARGE_PREF_MAIN_HAND) != 0) {
                collectFromStack(result, player.getMainHandItem(), grid, hubMetadata);
            }
        }
        if ((preferences & CHARGE_PREF_ARMOR) != 0) {
            collectFromSlots(result, state.armor, 0, state.armor.size(), grid, hubMetadata);
        }
        if (loadAccessoryIntegration && (preferences & CHARGE_PREF_ACCESSORY) != 0) {
            collectAccessory(result, player, grid, hubMetadata);
        }
    }

    private static byte resolveChargingPreferenceMask(IGrid grid, Player player) {
        var hubNode = grid.getHubNode();
        if (hubNode == null) {
            return CHARGE_PREF_ALL;
        }

        if (hubNode.getChannelId().equals(HubNode.EMPTY)) {
            var owner = hubNode.getOwner();
            if (owner != null && !owner.equals(player.getUUID())) {
                return 0;
            }
        }

        if (hubNode.getPermissionLevel(player.getUUID()) == HubPermissionLevel.NONE) {
            return 0;
        }

        return hubNode.getChargingPreference(player.getUUID()).toByte();
    }

    private static void collectFromSlots(Collection<EnergyTransferParticipant> result,
                                         List<ItemStack> items,
                                         int startIndex, int endIndex,
                                         IGrid grid,
                                         @Nullable HubNode.HubMetadata hubMetadata) {
        for (int i = startIndex; i < endIndex; i++) {
            if (i >= items.size()) break;
            var stack = items.get(i);
            var handler = IEnergyHandler.release(stack, hubMetadata);
            if (handler != null) {
                var participant = EnergyTransferParticipant.obtain(handler, grid, hubMetadata, EnergyMachineManager.getOrCreateInteraction(grid));
                if (canReceiveMore(participant)) {
                    result.add(participant);
                } else {
                    participant.recycle();
                }
            }
        }
    }

    private static void collectFromStack(Collection<EnergyTransferParticipant> result,
                                         ItemStack stack,
                                         IGrid grid,
                                         @Nullable HubNode.HubMetadata hubMetadata) {
        var handler = IEnergyHandler.release(stack, hubMetadata);
        if (handler == null) {
            return;
        }

        var participant = EnergyTransferParticipant.obtain(handler, grid, hubMetadata, EnergyMachineManager.getOrCreateInteraction(grid));
        if (canReceiveMore(participant)) {
            result.add(participant);
            return;
        }

        participant.recycle();
    }

    private static void collectAccessory(Collection<EnergyTransferParticipant> result,
                                         Player player,
                                         IGrid grid,
                                         @Nullable HubNode.HubMetadata hubMetadata) {
        AccessoryInventoryCompat.collectAccessoryItems(player, stack -> {
            var energyHandler = IEnergyHandler.release(stack, hubMetadata);
            if (energyHandler == null) {
                return;
            }
            var participant = EnergyTransferParticipant.obtain(energyHandler, grid, hubMetadata, EnergyMachineManager.getOrCreateInteraction(grid));
            if (canReceiveMore(participant)) {
                result.add(participant);
                return;
            }
            participant.recycle();
        });
    }

    private static boolean canReceiveMore(EnergyTransferParticipant participant) {
        EnergyAmount amount = participant.canReceiveValue();
        try {
            return amount.isPositive();
        } finally {
            amount.recycle();
        }
    }

    private static void transferEnergyToTargets(Reference2ObjectMap<IGrid, Set<EnergyTransferParticipant>> chargeTargetsByGrid,
                                                Reference2ObjectMap<IGrid, EnergyMachineManager.GridTickData> machineMap) {
        for (var entry : chargeTargetsByGrid.entrySet()) {
            var grid = entry.getKey();
            if (INSTANCE.processedTransferGrids.contains(grid)) {
                continue;
            }
            transferEnergyForGrid(grid, chargeTargetsByGrid, machineMap, INSTANCE.processedTransferGrids);
        }
    }

    private static void transferEnergyForGrid(IGrid grid,
                                              Reference2ObjectMap<IGrid, Set<EnergyTransferParticipant>> chargeTargetsByGrid,
                                              Reference2ObjectMap<IGrid, EnergyMachineManager.GridTickData> machineMap,
                                              ReferenceSet<IGrid> processedGrids) {
        processedGrids.add(grid);
        var chargingTargets = chargeTargetsByGrid.getOrDefault(grid, Collections.emptySet());

        var hubNode = grid.getHubNode();
        if (hubNode != null && hubNode.isActive() && !hubNode.getChannelId().equals(HubNode.EMPTY)) {
            var channelGrids = INSTANCE.collectActiveChannelTransferGrids(hubNode.getChannelId(), grid, machineMap, processedGrids);
            if (channelGrids.size() > 1) {
                var merged = INSTANCE.channelTransferScratch.prepare();

                for (var channelGrid : channelGrids) {
                    processedGrids.add(channelGrid);
                    var gridTargets = chargeTargetsByGrid.get(channelGrid);
                    if (gridTargets != null) {
                        merged.targets.addAll(gridTargets);
                        gridTargets.clear();
                    }
                }
                merged.bind(channelGrids, machineMap);

                if (merged.targets.isEmpty()) {
                    return;
                }

                long startNanos = System.nanoTime();
                transferEnergy(merged.send, merged.targets, EnergyMachineManager.Status.EXTRACT);
                transferEnergy(merged.storage, merged.targets, EnergyMachineManager.Status.EXTRACT);
                EnergyMachineManager.recordDistributedGridTickTimeNanos(channelGrids, System.nanoTime() - startNanos);
                for (var participant : merged.targets) {
                    participant.recycle();
                }
                return;
            }
        }

        if (chargingTargets.isEmpty()) {
            return;
        }

        var handlers = machineMap.get(grid);
        if (handlers != null && handlers.activeThisTick) {
            long startNanos = System.nanoTime();
            transferEnergy(handlers.send, chargingTargets, EnergyMachineManager.Status.EXTRACT);
            transferEnergy(handlers.storage, chargingTargets, EnergyMachineManager.Status.EXTRACT);
            EnergyMachineManager.recordGridTickTimeNanos(grid, System.nanoTime() - startNanos);
        }
    }

    static ChargingPluginScope getChargingPluginScope(IHubNode hub) {
        Boolean dimensional = hub.getPluginCapabilityData(HubCapabilitys.CHARGE_CAPABILITY);
        if (dimensional == null) {
            return ChargingPluginScope.NONE;
        }
        return dimensional ? ChargingPluginScope.DIMENSIONAL : ChargingPluginScope.WIDE_AREA;
    }

    @Nullable
    private static HubNode.HubMetadata getHubMetadata(@Nullable IGrid grid) {
        if (grid == null) {
            return null;
        }
        IHubNode hubNode = grid.getHubNode();
        return hubNode != null ? hubNode.getHubData() : null;
    }

    private ReferenceSet<IGrid> collectActiveChannelTransferGrids(UUID channelId,
                                                                  IGrid rootGrid,
                                                                  Reference2ObjectMap<IGrid, EnergyMachineManager.GridTickData> machineMap,
                                                                  ReferenceSet<IGrid> processedGrids) {
        channelTransferGridsScratch.clear();
        for (var candidate : machineMap.keySet()) {
            collectActiveChannelTransferGrid(channelId, rootGrid, candidate, processedGrids);
        }
        for (var candidate : activeChargeTargetGrids) {
            collectActiveChannelTransferGrid(channelId, rootGrid, candidate, processedGrids);
        }
        return channelTransferGridsScratch;
    }

    private void collectActiveChannelTransferGrid(UUID channelId, IGrid rootGrid, IGrid candidate, ReferenceSet<IGrid> processedGrids) {
        if (candidate != rootGrid && processedGrids.contains(candidate)) {
            return;
        }
        var candidateHub = candidate.getHubNode();
        if (candidateHub != null && candidateHub.isActive() && channelId.equals(candidateHub.getChannelId())) {
            channelTransferGridsScratch.add(candidate);
        }
    }

    void onServerTick(MinecraftServer server, Reference2ObjectMap<IGrid, EnergyMachineManager.GridTickData> machineMap) {
        var players = WorldResolveCompat.getServerPlayers(server);
        prepareChargeTargetScratch();
        processedTransferGrids.clear();
        playerStates.clear();
        if (playerStates instanceof ObjectArrayList<PlayerChargeState> objectArrayList) {
            objectArrayList.ensureCapacity(players.size());
        }

        for (var player : players) {
            var playerState = new PlayerChargeState(player);
            playerStates.add(playerState);
            collectPlayerChargeTargets(player, playerState);
        }

        // Plugin-based remote charging: dimensional scope only (wide-area handled in collectPlayerChargeTargets)
        for (var grid : machineMap.keySet()) {
            var hub = grid.getHubNode();
            if (hub == null || !hub.isActive()) continue;
            if (getChargingPluginScope(hub) != ChargingPluginScope.DIMENSIONAL) continue;

            for (int i = 0; i < players.size(); i++) {
                var player = players.get(i);
                var playerState = playerStates.get(i);
                if (playerState.coveredGrids.contains(grid) || playerState.reachableGrids.contains(grid)) continue;

                if (hub.getPermissionLevel(player.getUUID()) == HubPermissionLevel.NONE) continue;

                playerState.scratch.clear();
                collectChargeablesForGrid(grid, player, playerState, playerState.scratch);
                if (!playerState.scratch.isEmpty()) {
                    getChargeTargets(grid).addAll(playerState.scratch);
                }
            }
        }

        transferEnergyToTargets(tickChargeTargetsByGrid, machineMap);

        for (var grid : activeChargeTargetGrids) {
            var handlers = tickChargeTargetsByGrid.get(grid);
            for (var participant : handlers) {
                participant.recycle();
            }
            handlers.clear();
        }
        activeChargeTargetGrids.clear();
        for (var playerState : playerStates) {
            playerState.clear();
        }
    }

    private void collectPlayerChargeTargets(Player player,
                                            PlayerChargeState playerState) {
        var coveredGrids = playerState.coveredGrids;
        var reachableGrids = playerState.reachableGrids;
        coveredGrids.clear();
        reachableGrids.clear();

        String dimId = WorldResolveCompat.getPlayerDimensionId(player);
        var map = scopeNode.get(dimId);
        if (map != null && !map.isEmpty()) {
            var pos = player.blockPosition();
            var nodeSet = map.get(Functions.mergeChunkCoords(pos));
            if (nodeSet != null && !nodeSet.isEmpty()) {
                for (var node : nodeSet) {
                    if (!node.chargingScopeCheck(pos)) continue;
                    var grid = node.getGrid();
                    if (grid != null) {
                        reachableGrids.add(grid);
                    }
                }
            }
        }

        var wideHubs = wideAreaHubs.get(dimId);
        if (wideHubs != null) {
            for (var hub : wideHubs) {
                if (!hub.isActive()) continue;
                var grid = hub.getGrid();
                if (grid == null || reachableGrids.contains(grid)) continue;
                if (hub.getPermissionLevel(player.getUUID()) == HubPermissionLevel.NONE) continue;
                reachableGrids.add(grid);
            }
        }

        if (reachableGrids.isEmpty()) {
            return;
        }

        for (var grid : reachableGrids) {
            playerState.scratch.clear();
            collectChargeablesForGrid(grid, player, playerState, playerState.scratch);
            if (!playerState.scratch.isEmpty()) {
                getChargeTargets(grid).addAll(playerState.scratch);
                coveredGrids.add(grid);
            }
        }
    }

    private void prepareChargeTargetScratch() {
        for (var grid : activeChargeTargetGrids) {
            tickChargeTargetsByGrid.get(grid).clear();
        }
        activeChargeTargetGrids.clear();
    }

    private Set<EnergyTransferParticipant> getChargeTargets(IGrid grid) {
        Set<EnergyTransferParticipant> targets = tickChargeTargetsByGrid.get(grid);
        if (targets == null) {
            targets = new ReferenceOpenHashSet<>();
            tickChargeTargetsByGrid.put(grid, targets);
        }
        if (targets.isEmpty()) {
            activeChargeTargetGrids.add(grid);
        }
        return targets;
    }

    public void addNode(INode node) {
        if (!(node instanceof IChargingNode chargingNode)) {
            return;
        }

        int nodeX = chargingNode.getPos().getX();
        int nodeZ = chargingNode.getPos().getZ();
        int range = (int) chargingNode.getChargingScope();
        int minChunkX = (nodeX - range) >> 4;
        int maxChunkX = (nodeX + range) >> 4;
        int minChunkZ = (nodeZ - range) >> 4;
        int maxChunkZ = (nodeZ + range) >> 4;

        String dimId = node.getDimensionId();

        Long2ObjectMap<ReferenceSet<IChargingNode>> dimScopeMap = scopeNode.get(dimId);
        if (dimScopeMap == null) {
            dimScopeMap = new Long2ObjectOpenHashMap<>();
            dimScopeMap.defaultReturnValue(ReferenceSets.emptySet());
            scopeNode.put(dimId, dimScopeMap);
        }

        LongSet coveredChunks = new LongOpenHashSet();
        for (int cx = minChunkX; cx <= maxChunkX; ++cx) {
            for (int cz = minChunkZ; cz <= maxChunkZ; ++cz) {
                long chunkCoord = Functions.mergeChunkCoords(cx, cz);
                coveredChunks.add(chunkCoord);

                ReferenceSet<IChargingNode> chunkNodeSet = dimScopeMap.get(chunkCoord);
                if (chunkNodeSet == dimScopeMap.defaultReturnValue()) {
                    chunkNodeSet = new ReferenceOpenHashSet<>();
                    dimScopeMap.put(chunkCoord, chunkNodeSet);
                }
                chunkNodeSet.add(chargingNode);
            }
        }

        Object2ObjectMap<IChargingNode, LongSet> dimNodeScopeMap = nodeScope.get(dimId);
        if (dimNodeScopeMap == null) {
            dimNodeScopeMap = new Object2ObjectOpenHashMap<>();
            nodeScope.put(dimId, dimNodeScopeMap);
        }
        dimNodeScopeMap.put(chargingNode, LongSets.unmodifiable(coveredChunks));

        if (chargingNode instanceof IHubNode hubNode) {
            var scope = getChargingPluginScope(hubNode);
            if (scope == ChargingPluginScope.WIDE_AREA || scope == ChargingPluginScope.DIMENSIONAL) {
                addWideAreaHub(hubNode, dimId);
            }
        }
    }

    public void removeNode(INode node) {
        if (!(node instanceof IChargingNode chargingNode)) {
            return;
        }

        String dimId = node.getDimensionId();

        Object2ObjectMap<IChargingNode, LongSet> dimNodeScopeMap = nodeScope.get(dimId);
        if (dimNodeScopeMap == null) {
            return;
        }

        LongSet coveredChunks = dimNodeScopeMap.remove(chargingNode);
        if (coveredChunks == null || coveredChunks.isEmpty()) {
            return;
        }

        Long2ObjectMap<ReferenceSet<IChargingNode>> dimScopeMap = scopeNode.get(dimId);
        if (dimScopeMap == null) {
            return;
        }

        for (long chunkCoord : coveredChunks) {
            ReferenceSet<IChargingNode> chunkNodeSet = dimScopeMap.get(chunkCoord);
            if (chunkNodeSet == dimScopeMap.defaultReturnValue()) {
                continue;
            }
            if (chunkNodeSet.size() == 1) {
                dimScopeMap.remove(chunkCoord);
            } else {
                chunkNodeSet.remove(chargingNode);
            }
        }

        if (chargingNode instanceof IHubNode hubNode) {
            removeWideAreaHub(hubNode, dimId);
        }
    }

    private void addWideAreaHub(IHubNode hub, String dimId) {
        ReferenceSet<IHubNode> dimSet = wideAreaHubs.get(dimId);
        if (dimSet == null) {
            dimSet = new ReferenceOpenHashSet<>();
            wideAreaHubs.put(dimId, dimSet);
        }
        dimSet.add(hub);
    }

    private void removeWideAreaHub(IHubNode hub, String dimId) {
        ReferenceSet<IHubNode> dimSet = wideAreaHubs.get(dimId);
        if (dimSet == null) return;
        dimSet.remove(hub);
        if (dimSet.isEmpty()) {
            wideAreaHubs.remove(dimId);
        }
    }

    public void refreshWideAreaState(IHubNode hub) {
        String dimId = hub.getDimensionId();
        removeWideAreaHub(hub, dimId);
        var scope = getChargingPluginScope(hub);
        if (scope == ChargingPluginScope.WIDE_AREA || scope == ChargingPluginScope.DIMENSIONAL) {
            addWideAreaHub(hub, dimId);
        }
    }

    void initGrid(Collection<NetworkManager.GridEntry> entries) {
        for (var entry : entries) {
            if (entry.grid().getNodes().isEmpty()) continue;
            for (INode node : entry.grid().getNodes()) {
                addNode(node);
            }
        }
    }

    public void onServerStop() {
        scopeNode.clear();
        nodeScope.clear();
        wideAreaHubs.clear();
        tickChargeTargetsByGrid.clear();
        activeChargeTargetGrids.clear();
        processedTransferGrids.clear();
        channelTransferGridsScratch.clear();
        playerStates.clear();
    }

    enum ChargingPluginScope {NONE, WIDE_AREA, DIMENSIONAL}

    private static final class ChannelTransferScratch {
        final ObjectList<TombstoneReferenceBag<EnergyTransferParticipant>> send = new ObjectArrayList<>();
        final ObjectList<TombstoneReferenceBag<EnergyTransferParticipant>> storage = new ObjectArrayList<>();
        final ReferenceSet<EnergyTransferParticipant> targets = new ReferenceOpenHashSet<>();

        ChannelTransferScratch prepare() {
            send.clear();
            storage.clear();
            targets.clear();
            return this;
        }

        void bind(Collection<IGrid> grids,
                  Reference2ObjectMap<IGrid, EnergyMachineManager.GridTickData> machineMap) {
            for (var grid : grids) {
                var handlers = machineMap.get(grid);
                if (handlers == null || !handlers.activeThisTick) {
                    continue;
                }
                if (!handlers.send.isEmpty()) {
                    send.add(handlers.send);
                }
                if (!handlers.storage.isEmpty()) {
                    storage.add(handlers.storage);
                }
            }
        }
    }

    private static final class PlayerChargeState {
        final List<ItemStack> inventory;
        final List<ItemStack> armor;
        final ObjectList<EnergyTransferParticipant> scratch = new ObjectArrayList<>();
        final ReferenceSet<IGrid> coveredGrids = new ReferenceOpenHashSet<>();
        final ReferenceSet<IGrid> reachableGrids = new ReferenceOpenHashSet<>();

        PlayerChargeState(Player player) {
            this.inventory = PlayerInventoryCompat.getMainInventory(player);
            this.armor = PlayerInventoryCompat.getArmorInventory(player);
        }

        void clear() {
            scratch.clear();
            coveredGrids.clear();
            reachableGrids.clear();
        }
    }
}
