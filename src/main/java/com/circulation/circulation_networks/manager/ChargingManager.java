package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.api.IGrid;
import com.circulation.circulation_networks.api.hub.ChargingDefinition;
import com.circulation.circulation_networks.api.hub.HubPermissionLevel;
import com.circulation.circulation_networks.api.node.IChargingNode;
import com.circulation.circulation_networks.api.node.IHubNode;
import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.network.hub.HubCapabilitys;
import com.circulation.circulation_networks.network.nodes.HubNode;
import com.circulation.circulation_networks.utils.ChunkCoordUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import it.unimi.dsi.fastutil.objects.ReferenceSets;
//? if <1.20 {
import baubles.api.BaublesApi;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;
//?} else if <1.21 {
/*import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import top.theillusivec4.curios.api.CuriosApi;
*///?} else {
/*import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import top.theillusivec4.curios.api.CuriosApi;
*///?}

import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.Nullable;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

import static com.circulation.circulation_networks.manager.EnergyMachineManager.transferEnergy;

public final class ChargingManager {

    public static final ChargingManager INSTANCE = new ChargingManager();
    //? if <1.20 {
    private static final boolean loadAccessoryIntegration = Loader.isModLoaded("baubles");
    //?} else {
    /*private static final boolean loadAccessoryIntegration = ModList.get().isLoaded("curios");
     *///?}
    private static final byte CHARGE_PREF_INVENTORY = 0x01;
    private static final byte CHARGE_PREF_HOTBAR = 0x02;
    private static final byte CHARGE_PREF_MAIN_HAND = 0x04;
    private static final byte CHARGE_PREF_OFF_HAND = 0x08;
    private static final byte CHARGE_PREF_ARMOR = 0x10;
    private static final byte CHARGE_PREF_ACCESSORY = 0x20;
    private static final byte CHARGE_PREF_ALL = 0b00111111;
    private final Int2ObjectMap<Long2ObjectMap<ReferenceSet<IChargingNode>>> scopeNode = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<Object2ObjectMap<IChargingNode, LongSet>> nodeScope = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<ReferenceSet<IHubNode>> wideAreaHubs = new Int2ObjectOpenHashMap<>();
    private final Reference2ObjectMap<IGrid, ReferenceSet<EnergyTransferParticipant>> tickChargeTargetsByGrid = new Reference2ObjectOpenHashMap<>();
    private final ObjectList<IGrid> activeChargeTargetGrids = new ObjectArrayList<>();
    private final ReferenceSet<IGrid> processedTransferGrids = new ReferenceOpenHashSet<>();
    private final ChannelTransferScratch channelTransferScratch = new ChannelTransferScratch();
    private final ReferenceSet<IGrid> channelTransferGridsScratch = new ReferenceOpenHashSet<>();
    private final ObjectList<PlayerChargeState> playerStates = new ObjectArrayList<>();
    private static final List<EnergyTransferParticipant> EMPTY_HANDLERS = ObjectLists.emptyList();

    //? if <1.20 {
    @Optional.Method(modid = "baubles")
    private static void collectAccessory(Collection<EnergyTransferParticipant> invs,
                                         EntityPlayer player,
                                         IGrid grid,
                                         @Nullable HubNode.HubMetadata hubMetadata) {
        var h = BaublesApi.getBaublesHandler(player);
        for (var i = 0; i < h.getSlots(); i++) {
            var stack = h.getStackInSlot(i);
            var handler = IEnergyHandler.release(stack, hubMetadata);
            if (handler == null) continue;
            var participant = EnergyTransferParticipant.obtain(handler, grid, hubMetadata, EnergyMachineManager.getOrCreateInteraction(grid));
            if (canReceiveMore(participant)) {
                invs.add(participant);
                continue;
            }
            participant.recycle();
        }
    }
    //?} else {
    /*private static void collectAccessory(Collection<EnergyTransferParticipant> invs,
                                         Player player,
                                         IGrid grid,
                                         @Nullable HubNode.HubMetadata hubMetadata) {
        CuriosApi.getCuriosInventory(player).ifPresent(curiosHandler -> {
            var equippedCurios = curiosHandler.getEquippedCurios();
            for (int i = 0; i < equippedCurios.getSlots(); i++) {
                var stack = equippedCurios.getStackInSlot(i);
                var energyHandler = IEnergyHandler.release(stack, hubMetadata);
                if (energyHandler == null) continue;
                var participant = EnergyTransferParticipant.obtain(energyHandler, grid, hubMetadata, EnergyMachineManager.getOrCreateInteraction(grid));
                if (canReceiveMore(participant)) {
                    invs.add(participant);
                    continue;
                }
                participant.recycle();
            }
        });
     }
    *///?}
    //~ if >=1.20 ' EntityPlayer player' -> ' Player player' {
    //~ if >=1.20 '.getHeldItemOffhand()' -> '.getOffhandItem()' {
    //~ if >=1.20 '.getHeldItemMainhand()' -> '.getMainHandItem()' {
    //~ if >=1.20 '.inventory.armorInventory' -> '.getInventory().armor' {
    //~ if >=1.20 '.getUniqueID()' -> '.getUUID()' {
    private static void collectChargeablesForGrid(IGrid grid,
                                                  EntityPlayer player,
                                                  PlayerChargeState state,
                                                  Collection<EnergyTransferParticipant> result) {
        state.cache.clear();
        byte preferences = resolveChargingPreferenceMask(grid, player);
        if (preferences == 0) {
            return;
        }

        HubNode.HubMetadata hubMetadata = getHubMetadata(grid);

        if ((preferences & CHARGE_PREF_INVENTORY) != 0) {
            collectFromSlots(result, state.cache, ChargingDefinition.INVENTORY, state.inventory, 9, state.inventory.size(), grid, hubMetadata);
        }
        if ((preferences & CHARGE_PREF_OFF_HAND) != 0) {
            collectFromStackWithCache(result, state.cache, ChargingDefinition.OFF_HAND, player.getHeldItemOffhand(), grid, hubMetadata);
        }
        if ((preferences & CHARGE_PREF_HOTBAR) != 0) {
            collectFromSlots(result, state.cache, ChargingDefinition.HOTBAR, state.inventory, 0, 9, grid, hubMetadata);
        } else {
            if ((preferences & CHARGE_PREF_MAIN_HAND) != 0) {
                collectFromStackWithCache(result, state.cache, ChargingDefinition.MAIN_HAND, player.getHeldItemMainhand(), grid, hubMetadata);
            }
        }
        if ((preferences & CHARGE_PREF_ARMOR) != 0) {
            collectFromSlots(result, state.cache, ChargingDefinition.ARMOR, state.armor, 0, state.armor.size(), grid, hubMetadata);
        }
        if (loadAccessoryIntegration && (preferences & CHARGE_PREF_ACCESSORY) != 0) {
            collectAccessoryWithCache(result, state.cache, player, grid, hubMetadata);
        }
    }

    private static byte resolveChargingPreferenceMask(IGrid grid, EntityPlayer player) {
        var hubNode = grid.getHubNode();
        if (hubNode == null) {
            return CHARGE_PREF_ALL;
        }

        if (hubNode.getChannelId().equals(HubNode.EMPTY)) {
            var owner = hubNode.getOwner();
            if (owner != null && !owner.equals(player.getUniqueID())) {
                return 0;
            }
        }

        if (hubNode.getPermissionLevel(player.getUniqueID()) == HubPermissionLevel.NONE) {
            return 0;
        }

        return hubNode.getChargingPreference(player.getUniqueID()).toByte();
    }

    private static void collectFromSlots(Collection<EnergyTransferParticipant> result,
                                         EnumMap<ChargingDefinition, List<EnergyTransferParticipant>> cache,
                                         ChargingDefinition definition,
                                         List<ItemStack> items,
                                         int startIndex, int endIndex,
                                         IGrid grid,
                                         @Nullable HubNode.HubMetadata hubMetadata) {
        var cached = cache.get(definition);
        if (cached != null) {
            result.addAll(cached);
            return;
        }

        ObjectList<EnergyTransferParticipant> handlers = null;
        for (int i = startIndex; i < endIndex; i++) {
            if (i >= items.size()) break;
            var stack = items.get(i);
            var handler = IEnergyHandler.release(stack, hubMetadata);
            if (handler != null) {
                var participant = EnergyTransferParticipant.obtain(handler, grid, hubMetadata, EnergyMachineManager.getOrCreateInteraction(grid));
                if (canReceiveMore(participant)) {
                    if (handlers == null) {
                        handlers = new ObjectArrayList<>();
                    }
                    handlers.add(participant);
                    result.add(participant);
                } else {
                    participant.recycle();
                }
            }
        }
        cache.put(definition, handlers == null ? EMPTY_HANDLERS : handlers);
    }
    //~}
    //~}
    //~}
    //~}
    //~}

    private static void collectFromStackWithCache(Collection<EnergyTransferParticipant> result,
                                                  EnumMap<ChargingDefinition, List<EnergyTransferParticipant>> cache,
                                                  ChargingDefinition definition,
                                                  ItemStack stack,
                                                  IGrid grid,
                                                  @Nullable HubNode.HubMetadata hubMetadata) {
        var cached = cache.get(definition);
        if (cached != null) {
            result.addAll(cached);
            return;
        }

        var handler = IEnergyHandler.release(stack, hubMetadata);
        if (handler == null) {
            cache.put(definition, EMPTY_HANDLERS);
            return;
        }

        var participant = EnergyTransferParticipant.obtain(handler, grid, hubMetadata, EnergyMachineManager.getOrCreateInteraction(grid));
        if (canReceiveMore(participant)) {
            var handlers = ObjectLists.singleton(participant);
            cache.put(definition, handlers);
            result.add(participant);
            return;
        }

        participant.recycle();
        cache.put(definition, EMPTY_HANDLERS);
    }

    //~ if >=1.20 ' EntityPlayer player' -> ' Player player' {
    private static void collectAccessoryWithCache(Collection<EnergyTransferParticipant> result,
                                                  EnumMap<ChargingDefinition, List<EnergyTransferParticipant>> cache,
                                                  EntityPlayer player,
                                                  IGrid grid,
                                                  @Nullable HubNode.HubMetadata hubMetadata) {
        var cached = cache.get(ChargingDefinition.ACCESSORY);
        if (cached != null) {
            result.addAll(cached);
            return;
        }

        var handlers = new ObjectArrayList<EnergyTransferParticipant>();
        //? if <1.20 {
        collectAccessory(handlers, player, grid, hubMetadata);
        //?} else {
        /*collectAccessory(handlers, player, grid, hubMetadata);
         *///?}
        cache.put(ChargingDefinition.ACCESSORY, handlers.isEmpty() ? EMPTY_HANDLERS : handlers);
        result.addAll(handlers);
    }
    //~}

    private static boolean canReceiveMore(EnergyTransferParticipant participant) {
        EnergyAmount amount = participant.canReceiveValue();
        try {
            return amount.isPositive();
        } finally {
            amount.recycle();
        }
    }

    private static void transferEnergyToTargets(Reference2ObjectMap<IGrid, ReferenceSet<EnergyTransferParticipant>> chargeTargetsByGrid,
                                                 Reference2ObjectMap<IGrid, EnergyMachineManager.GridTickData> machineMap) {
        for (var entry : chargeTargetsByGrid.entrySet()) {
            var grid = entry.getKey();
            if (INSTANCE.processedTransferGrids.contains(grid)) {
                continue;
            }
            transferEnergyForGrid(grid, chargeTargetsByGrid, machineMap, INSTANCE.processedTransferGrids);
        }
    }

    private static void syncBackSenders(ReferenceSet<EnergyTransferParticipant> send,
                                        ReferenceSet<EnergyTransferParticipant> storage,
                                        Reference2ObjectMap<IGrid, EnergyMachineManager.GridTickData> machineMap) {
        for (var p : send) {
            var h = machineMap.get(p.grid());
            if (h != null) h.send.add(p);
        }
        for (var p : storage) {
            var h = machineMap.get(p.grid());
            if (h != null) h.storage.add(p);
        }
    }

    private static void transferEnergyForGrid(IGrid grid,
                                               Reference2ObjectMap<IGrid, ReferenceSet<EnergyTransferParticipant>> chargeTargetsByGrid,
                                               Reference2ObjectMap<IGrid, EnergyMachineManager.GridTickData> machineMap,
                                               ReferenceSet<IGrid> processedGrids) {
        processedGrids.add(grid);
        var chargingTargets = chargeTargetsByGrid.getOrDefault(grid, ReferenceSets.emptySet());

        var hubNode = grid.getHubNode();
        if (hubNode != null && hubNode.isActive() && !hubNode.getChannelId().equals(HubNode.EMPTY)) {
            var channelGrids = INSTANCE.collectActiveChannelTransferGrids(hubNode.getChannelId(), grid, machineMap, processedGrids);
            if (channelGrids.size() > 1) {
                var merged = INSTANCE.channelTransferScratch.prepare();

                for (var channelGrid : channelGrids) {
                    processedGrids.add(channelGrid);
                    merged.timedGrids.add(channelGrid);
                    var handlers = machineMap.get(channelGrid);
                    if (handlers != null && handlers.activeThisTick) {
                        merged.send.addAll(handlers.send);
                        handlers.send.clear();
                        merged.storage.addAll(handlers.storage);
                        handlers.storage.clear();
                    }
                    var gridTargets = chargeTargetsByGrid.get(channelGrid);
                    if (gridTargets != null) {
                        merged.targets.addAll(gridTargets);
                        gridTargets.clear();
                    }
                }

                if (merged.targets.isEmpty()) {
                    syncBackSenders(merged.send, merged.storage, machineMap);
                    return;
                }

                long startNanos = System.nanoTime();
                transferEnergy(merged.send, merged.targets, EnergyMachineManager.Status.EXTRACT, false);
                transferEnergy(merged.storage, merged.targets, EnergyMachineManager.Status.EXTRACT, false);
                EnergyMachineManager.recordDistributedGridTickTimeNanos(merged.timedGrids, System.nanoTime() - startNanos);
                syncBackSenders(merged.send, merged.storage, machineMap);
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
            transferEnergy(handlers.send, chargingTargets, EnergyMachineManager.Status.EXTRACT, false);
            transferEnergy(handlers.storage, chargingTargets, EnergyMachineManager.Status.EXTRACT, false);
            EnergyMachineManager.recordGridTickTimeNanos(grid, System.nanoTime() - startNanos);
        }
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

    static ChargingPluginScope getChargingPluginScope(IHubNode hub) {
        Boolean dimensional = hub.getPluginCapabilityData(HubCapabilitys.CHARGE_CAPABILITY);
        if (dimensional == null) {
            return ChargingPluginScope.NONE;
        }
        return dimensional ? ChargingPluginScope.DIMENSIONAL : ChargingPluginScope.WIDE_AREA;
    }

    private static int getDimensionId(INode node) {
        //? if <1.20 {
        return node.getDimensionId();
        //?} else {
        /*return node.getWorld().dimension().location().hashCode();
         *///?}
    }

    @Nullable
    private static HubNode.HubMetadata getHubMetadata(@Nullable IGrid grid) {
        if (grid == null) {
            return null;
        }
        IHubNode hubNode = grid.getHubNode();
        return hubNode != null ? hubNode.getHubData() : null;
    }

    //~ if >=1.20 '.getUniqueID()' -> '.getUUID()' {
    void onServerTick(MinecraftServer server, Reference2ObjectMap<IGrid, EnergyMachineManager.GridTickData> machineMap) {
        var players = server.getPlayerList().getPlayers();
        prepareChargeTargetScratch();
        processedTransferGrids.clear();
        if (playerStates instanceof ObjectArrayList) {
            ((ObjectArrayList<PlayerChargeState>) playerStates).ensureCapacity(players.size());
        }

        for (int i = 0; i < players.size(); i++) {
            var player = players.get(i);
            PlayerChargeState playerState;
            if (i < playerStates.size()) {
                playerState = playerStates.get(i);
                playerState.prepare(player);
            } else {
                playerState = new PlayerChargeState(player);
                playerStates.add(playerState);
            }
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

                if (hub.getPermissionLevel(player.getUniqueID()) == HubPermissionLevel.NONE) continue;

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
        for (int i = 0; i < players.size(); i++) {
            playerStates.get(i).clear();
        }
    }
    //~}

    //~ if >=1.20 '(EntityPlayer player' -> '(Player player' {
    //~ if >=1.20 'player.dimension)' -> 'player.level().dimension().location().hashCode())' {
    //~ if >=1.20 '.getPosition()' -> '.blockPosition()' {
    //~ if >=1.20 '.inventory.mainInventory' -> '.getInventory().items' {
    //~ if >=1.20 '.getUniqueID()' -> '.getUUID()' {
    private void collectPlayerChargeTargets(EntityPlayer player,
                                            PlayerChargeState playerState) {
        var coveredGrids = playerState.coveredGrids;
        var reachableGrids = playerState.reachableGrids;
        coveredGrids.clear();
        reachableGrids.clear();

        var map = scopeNode.get(player.dimension);
        if (map != null && !map.isEmpty()) {
            var pos = player.getPosition();
            var nodeSet = map.get(ChunkCoordUtils.mergeChunkCoords(pos));
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

        var wideHubs = wideAreaHubs.get(player.dimension);
        if (wideHubs != null) {
            for (var hub : wideHubs) {
                if (!hub.isActive()) continue;
                var grid = hub.getGrid();
                if (grid == null || reachableGrids.contains(grid)) continue;
                if (hub.getPermissionLevel(player.getUniqueID()) == HubPermissionLevel.NONE) continue;
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

    private ReferenceSet<EnergyTransferParticipant> getChargeTargets(IGrid grid) {
        ReferenceSet<EnergyTransferParticipant> targets = tickChargeTargetsByGrid.get(grid);
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

        int dimId = getDimensionId(node);

        Long2ObjectMap<ReferenceSet<IChargingNode>> dimScopeMap = scopeNode.get(dimId);
        if (dimScopeMap == null) {
            dimScopeMap = new Long2ObjectOpenHashMap<>();
            dimScopeMap.defaultReturnValue(ReferenceSets.emptySet());
            scopeNode.put(dimId, dimScopeMap);
        }

        LongSet coveredChunks = new LongOpenHashSet();
        for (int cx = minChunkX; cx <= maxChunkX; ++cx) {
            for (int cz = minChunkZ; cz <= maxChunkZ; ++cz) {
                long chunkCoord = ChunkCoordUtils.mergeChunkCoords(cx, cz);
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

        int dimId = getDimensionId(node);

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

    private void addWideAreaHub(IHubNode hub, int dimId) {
        ReferenceSet<IHubNode> dimSet = wideAreaHubs.get(dimId);
        if (dimSet == null) {
            dimSet = new ReferenceOpenHashSet<>();
            wideAreaHubs.put(dimId, dimSet);
        }
        dimSet.add(hub);
    }

    private void removeWideAreaHub(IHubNode hub, int dimId) {
        ReferenceSet<IHubNode> dimSet = wideAreaHubs.get(dimId);
        if (dimSet == null) return;
        dimSet.remove(hub);
        if (dimSet.isEmpty()) {
            wideAreaHubs.remove(dimId);
        }
    }

    public void refreshWideAreaState(IHubNode hub) {
        int dimId = getDimensionId(hub);
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
        final ReferenceSet<EnergyTransferParticipant> send = new ReferenceOpenHashSet<>();
        final ReferenceSet<EnergyTransferParticipant> storage = new ReferenceOpenHashSet<>();
        final ReferenceSet<EnergyTransferParticipant> targets = new ReferenceOpenHashSet<>();
        final ReferenceSet<IGrid> timedGrids = new ReferenceOpenHashSet<>();

        ChannelTransferScratch prepare() {
            send.clear();
            storage.clear();
            targets.clear();
            timedGrids.clear();
            return this;
        }
    }

    private static final class PlayerChargeState {
        final EnumMap<ChargingDefinition, List<EnergyTransferParticipant>> cache = new EnumMap<>(ChargingDefinition.class);
        List<ItemStack> inventory;
        List<ItemStack> armor;
        final ObjectList<EnergyTransferParticipant> scratch = new ObjectArrayList<>();
        final ReferenceSet<IGrid> coveredGrids = new ReferenceOpenHashSet<>();
        final ReferenceSet<IGrid> reachableGrids = new ReferenceOpenHashSet<>();

        //~ if >=1.20 '(EntityPlayer player' -> '(Player player' {
        //~ if >=1.20 '.inventory.mainInventory' -> '.getInventory().items' {
        //~ if >=1.20 '.inventory.armorInventory' -> '.getInventory().armor' {
        PlayerChargeState(EntityPlayer player) {
            prepare(player);
        }

        void prepare(EntityPlayer player) {
            this.inventory = player.inventory.mainInventory;
            this.armor = player.inventory.armorInventory;
        }
        //~}
        //~}
        //~}

        void clear() {
            cache.clear();
            scratch.clear();
            coveredGrids.clear();
            reachableGrids.clear();
        }
    }
    //~}
    //~}
    //~}
    //~}
    //~}
}
