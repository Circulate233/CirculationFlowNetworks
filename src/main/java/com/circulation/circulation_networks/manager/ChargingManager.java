package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.IGrid;
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
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
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
    private final ObjectArrayList<IGrid> activeChargeTargetGrids = new ObjectArrayList<>();
    private final ReferenceSet<EnergyTransferParticipant> channelTargetsScratch = new ReferenceOpenHashSet<>();
    private final Object2ObjectMap<UUID, PlayerChargeState> playerStates = new Object2ObjectOpenHashMap<>();
    private final ObjectOpenHashSet<UUID> onlinePlayerIdsScratch = new ObjectOpenHashSet<>();

    //? if <1.20 {
    @Optional.Method(modid = "baubles")
    private static void collectAccessory(Collection<EnergyTransferParticipant> invs,
                                         EntityPlayer player,
                                         IGrid grid,
                                         @Nullable HubNode.HubMetadata hubMetadata) {
        var h = BaublesApi.getBaublesHandler(player);
        for (var i = 0; i < h.getSlots(); i++) {
            var stack = h.getStackInSlot(i);
            var handler = EnergyHandlerRuntime.bindItem(stack, hubMetadata);
            if (handler == null) continue;
            var participant = EnergyTransferParticipant.obtain(handler, grid, hubMetadata);
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
                var energyHandler = EnergyHandlerRuntime.bindItem(stack, hubMetadata);
                if (energyHandler == null) continue;
                var participant = EnergyTransferParticipant.obtain(energyHandler, grid, hubMetadata);
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
        byte preferences = resolveChargingPreferenceMask(grid, player);
        if (preferences == 0) {
            return;
        }

        HubNode.HubMetadata hubMetadata = getHubMetadata(grid);

        if ((preferences & CHARGE_PREF_INVENTORY) != 0) {
            collectFromSlots(result, state.inventory, 9, state.inventory.size(), grid, hubMetadata);
        }
        if ((preferences & CHARGE_PREF_OFF_HAND) != 0) {
            collectFromStack(result, player.getHeldItemOffhand(), grid, hubMetadata);
        }
        if ((preferences & CHARGE_PREF_HOTBAR) != 0) {
            collectFromSlots(result, state.inventory, 0, 9, grid, hubMetadata);
        } else {
            if ((preferences & CHARGE_PREF_MAIN_HAND) != 0) {
                collectFromStack(result, player.getHeldItemMainhand(), grid, hubMetadata);
            }
        }
        if ((preferences & CHARGE_PREF_ARMOR) != 0) {
            collectFromSlots(result, state.armor, 0, state.armor.size(), grid, hubMetadata);
        }
        if (loadAccessoryIntegration && (preferences & CHARGE_PREF_ACCESSORY) != 0) {
            collectAccessory(result, player, grid, hubMetadata);
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
                                         List<ItemStack> items,
                                         int startIndex, int endIndex,
                                         IGrid grid,
                                         @Nullable HubNode.HubMetadata hubMetadata) {
        for (int i = startIndex; i < endIndex; i++) {
            if (i >= items.size()) break;
            collectFromStack(result, items.get(i), grid, hubMetadata);
        }
    }
    //~}
    //~}
    //~}
    //~}
    //~}

    private static void collectFromStack(Collection<EnergyTransferParticipant> result,
                                         ItemStack stack,
                                         IGrid grid,
                                         @Nullable HubNode.HubMetadata hubMetadata) {
        var handler = EnergyHandlerRuntime.bindItem(stack, hubMetadata);
        if (handler == null) {
            return;
        }
        var participant = EnergyTransferParticipant.obtain(handler, grid, hubMetadata);
        if (canReceiveMore(participant)) {
            result.add(participant);
            return;
        }

        participant.recycle();
    }

    private static boolean canReceiveMore(EnergyTransferParticipant participant) {
        EnergyAmount amount = participant.canReceiveValue();
        try {
            return amount.isPositive();
        } finally {
            amount.recycle();
        }
    }

    private void transferEnergyToTargets(long epoch) {
        for (int index = 0, size = activeChargeTargetGrids.size(); index < size; index++) {
            IGrid grid = activeChargeTargetGrids.get(index);
            try {
                transferEnergyForGrid(grid, epoch);
            } catch (RuntimeException exception) {
                CirculationFlowNetworks.LOGGER.error("Charging transfer failed for grid {}", grid.getId(), exception);
            }
        }
    }

    private void transferEnergyForGrid(IGrid grid, long epoch) {
        UUID channelId = grid.getParticipantIndex().channelId();
        if (channelId != null) {
            transferEnergyForChannel(channelId, epoch);
            return;
        }
        ReferenceSet<EnergyTransferParticipant> chargingTargets = tickChargeTargetsByGrid.get(grid);
        if (chargingTargets == null || chargingTargets.isEmpty() || !grid.getParticipantIndex().isRoutingActive()
            || grid.getParticipantIndex().send().isEmpty() && grid.getParticipantIndex().storage().isEmpty()) {
            return;
        }
        long startNanos = System.nanoTime();
        transferEnergy(grid.getParticipantIndex().send(), chargingTargets, EnergyMachineManager.Status.EXTRACT, epoch);
        transferEnergy(grid.getParticipantIndex().storage(), chargingTargets, EnergyMachineManager.Status.EXTRACT, epoch);
        EnergyMachineManager.recordGridTickTimeNanos(grid, System.nanoTime() - startNanos);
    }

    private void transferEnergyForChannel(UUID channelId, long epoch) {
        ChannelParticipantIndex.ChannelEntry channel = ChannelParticipantIndex.INSTANCE.channel(channelId);
        if (channel == null) {
            throw new IllegalStateException("Charging grid references an unindexed channel " + channelId);
        }
        if (!channel.beginChargingEpoch(epoch)) {
            return;
        }
        channelTargetsScratch.clear();
        for (int gridIndex = 0, gridCount = channel.gridCount(); gridIndex < gridCount; gridIndex++) {
            IGrid channelGrid = channel.gridAt(gridIndex);
            ReferenceSet<EnergyTransferParticipant> targets = tickChargeTargetsByGrid.get(channelGrid);
            if (targets != null) {
                channelTargetsScratch.addAll(targets);
            }
        }
        if (channelTargetsScratch.isEmpty() || !channel.isRoutingActive() || channel.routingEpoch() != epoch
            || channel.send().isEmpty() && channel.storage().isEmpty()) {
            return;
        }
        long startNanos = System.nanoTime();
        transferEnergy(channel.send(), channelTargetsScratch, EnergyMachineManager.Status.EXTRACT, epoch);
        transferEnergy(channel.storage(), channelTargetsScratch, EnergyMachineManager.Status.EXTRACT, epoch);
        EnergyMachineManager.recordDistributedChannelTickTimeNanos(channel, System.nanoTime() - startNanos);
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
    void onServerTick(MinecraftServer server, long epoch) {
        var players = server.getPlayerList().getPlayers();
        prepareChargeTargetScratch();
        onlinePlayerIdsScratch.clear();

        for (int i = 0; i < players.size(); i++) {
            var player = players.get(i);
            UUID playerId = player.getUniqueID();
            onlinePlayerIdsScratch.add(playerId);
            PlayerChargeState playerState = playerStates.get(playerId);
            if (playerState == null) {
                playerState = new PlayerChargeState(player);
                playerStates.put(playerId, playerState);
            } else {
                playerState.prepare(player);
            }
        }
        releaseOfflinePlayerStates();
        try {
            for (int i = 0; i < players.size(); i++) {
                var player = players.get(i);
                PlayerChargeState playerState = playerStates.get(player.getUniqueID());
                try {
                    collectPlayerChargeTargets(player, playerState);
                } catch (RuntimeException exception) {
                    CirculationFlowNetworks.LOGGER.error("Charging target collection failed for player {}",
                        player.getUniqueID(), exception);
                }
            }
            collectDimensionalChargeTargets(server, epoch);
            transferEnergyToTargets(epoch);
        } finally {
            recycleChargeTargets();
            for (PlayerChargeState state : playerStates.values()) {
                state.clear();
            }
            channelTargetsScratch.clear();
        }
    }
    //~}

    private void collectDimensionalChargeTargets(MinecraftServer server, long epoch) {
        var players = server.getPlayerList().getPlayers();
        LocalParticipantRoutingIndex localRoutes = LocalParticipantRoutingIndex.INSTANCE;
        for (int index = 0, count = localRoutes.routingGridCount(); index < count; index++) {
            IGrid grid = localRoutes.routingGridAt(index);
            if (grid.getParticipantIndex().isRoutingActive()) {
                collectDimensionalChargeTargetsForGrid(grid, server);
            }
        }
        ChannelParticipantIndex channels = ChannelParticipantIndex.INSTANCE;
        for (int index = 0, count = channels.routingChannelCount(); index < count; index++) {
            ChannelParticipantIndex.ChannelEntry channel = channels.routingChannelAt(index);
            if (!channel.isRoutingActive() || channel.routingEpoch() != epoch) {
                continue;
            }
            for (int gridIndex = 0, gridCount = channel.gridCount(); gridIndex < gridCount; gridIndex++) {
                collectDimensionalChargeTargetsForGrid(channel.gridAt(gridIndex), server);
            }
        }
    }

    //~ if >=1.20 '.getUniqueID()' -> '.getUUID()' {
    private void collectDimensionalChargeTargetsForGrid(IGrid grid, MinecraftServer server) {
        IHubNode hub = grid.getHubNode();
        if (hub == null || !hub.isActive() || getChargingPluginScope(hub) != ChargingPluginScope.DIMENSIONAL) {
            return;
        }
        var players = server.getPlayerList().getPlayers();
        for (int index = 0; index < players.size(); index++) {
            var player = players.get(index);
            PlayerChargeState state = playerStates.get(player.getUniqueID());
            if (state.coveredGrids.contains(grid) || state.reachableGrids.contains(grid)
                || hub.getPermissionLevel(player.getUniqueID()) == HubPermissionLevel.NONE) {
                continue;
            }
            state.scratch.clear();
            collectChargeablesForGrid(grid, player, state, state.scratch);
            if (!state.scratch.isEmpty()) {
                getChargeTargets(grid).addAll(state.scratch);
            }
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
        if (!activeChargeTargetGrids.isEmpty()) {
            recycleChargeTargets();
        }
    }

    private void recycleChargeTargets() {
        for (int index = 0, size = activeChargeTargetGrids.size(); index < size; index++) {
            IGrid grid = activeChargeTargetGrids.get(index);
            ReferenceSet<EnergyTransferParticipant> targets = tickChargeTargetsByGrid.get(grid);
            if (targets == null) {
                continue;
            }
            for (EnergyTransferParticipant participant : targets) {
                participant.recycle();
            }
            targets.clear();
        }
        activeChargeTargetGrids.clear();
    }

    private void releaseOfflinePlayerStates() {
        for (var iterator = playerStates.object2ObjectEntrySet().iterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            if (onlinePlayerIdsScratch.contains(entry.getKey())) {
                continue;
            }
            entry.getValue().clearAndRelease();
            iterator.remove();
        }
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
        recycleChargeTargets();
        tickChargeTargetsByGrid.clear();
        channelTargetsScratch.clear();
        for (PlayerChargeState state : playerStates.values()) {
            state.clearAndRelease();
        }
        playerStates.clear();
        onlinePlayerIdsScratch.clear();
    }

    enum ChargingPluginScope {NONE, WIDE_AREA, DIMENSIONAL}

    private static final class PlayerChargeState {
        UUID playerId;
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
            this.playerId = player.getUniqueID();
            this.inventory = player.inventory.mainInventory;
            this.armor = player.inventory.armorInventory;
        }
        //~}
        //~}
        //~}

        void clear() {
            scratch.clear();
            coveredGrids.clear();
            reachableGrids.clear();
        }

        void clearAndRelease() {
            clear();
            inventory = null;
            armor = null;
            playerId = null;
        }
    }
    //~}
    //~}
    //~}
    //~}
    //~}
}
