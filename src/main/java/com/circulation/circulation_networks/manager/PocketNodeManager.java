package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.api.API;
import com.circulation.circulation_networks.api.INodeBlockEntity;
import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.api.node.NodeType;
import com.circulation.circulation_networks.packets.PocketNodeRendering;
import com.circulation.circulation_networks.pocket.PocketNodeHost;
import com.circulation.circulation_networks.pocket.PocketNodeHostRules;
import com.circulation.circulation_networks.pocket.PocketNodeRecord;
import com.circulation.circulation_networks.registry.NodeTypes;
import com.circulation.circulation_networks.registry.PocketNodeItems;
import com.circulation.circulation_networks.utils.Functions;
import com.circulation.circulation_networks.utils.WorldResolveCompat;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Objects;

import static com.circulation.circulation_networks.utils.WorldResolveCompat.isClientWorld;

@SuppressWarnings("resource")
public final class PocketNodeManager {

    public static final PocketNodeManager INSTANCE = new PocketNodeManager();
    private final Object2ObjectMap<String, Long2ObjectMap<PocketNodeHost>> activeHosts = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<String, Long2ObjectMap<PocketNodeRecord>> pendingHosts = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<String, Long2ObjectMap<LongSet>> activeChunkIndex = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<String, Long2ObjectMap<LongSet>> pendingChunkIndex = new Object2ObjectOpenHashMap<>();
    private final LongArrayList chunkLoadIterationScratch = new LongArrayList();
    private boolean loaded;
    private boolean dirty;

    private PocketNodeManager() {
    }

    private static Long2ObjectMap<LongSet> getChunkIndex(Object2ObjectMap<String, Long2ObjectMap<LongSet>> index, String dimId) {
        return index.computeIfAbsent(dimId, ignored -> new Long2ObjectOpenHashMap<>());
    }

    private static void indexChunkPosition(Long2ObjectMap<LongSet> chunkMap, long chunkCoord, long posLong) {
        LongSet positions = chunkMap.get(chunkCoord);
        if (positions == null) {
            positions = new LongOpenHashSet();
            chunkMap.put(chunkCoord, positions);
        }
        positions.add(posLong);
    }

    private static void unindexChunkPosition(@Nullable Long2ObjectMap<LongSet> chunkMap, long chunkCoord, long posLong) {
        if (chunkMap == null) {
            return;
        }
        LongSet positions = chunkMap.get(chunkCoord);
        if (positions == null) {
            return;
        }
        positions.remove(posLong);
        if (positions.isEmpty()) {
            chunkMap.remove(chunkCoord);
        }
    }

    private static @Nullable LongSet getChunkPositions(@Nullable Long2ObjectMap<LongSet> chunkMap, long chunkCoord) {
        return chunkMap == null ? null : chunkMap.get(chunkCoord);
    }

    private static boolean shouldDiscardPendingRecord(Level world, PocketNodeRecord record, INode node, NetworkManager.AddNodeResult addResult) {
        if (!node.isActive()) {
            return true;
        }
        if (isHostChunkLoaded(world, record.pos()) && resolveValidatedRecord(world, record) == null) {
            return true;
        }
        return addResult.getStatus() == NetworkManager.AddNodeResult.Status.HUB_CONFLICT;
    }


    private static boolean canAdoptLoadedPocketNode(Level world, PocketNodeRecord record, INode node) {
        if (node == null || !node.isActive()) {
            return false;
        }
        if (node.getNodeType() != record.nodeType()) {
            return false;
        }
        return resolveValidatedRecord(world, record) != null && isRecoverablePocketNode(world, node);
    }

    private static boolean isRecoverablePocketNode(Level world, INode node) {
        if (world == null || node == null || !node.getNodeType().allowsPocketNode()) {
            return false;
        }
        BlockPos pos = node.getPos();
        if (!isHostChunkLoaded(world, pos) || getCurrentHostBlockId(world, pos) == null) {
            return false;
        }
        return API.getNodeAt(world, pos) == node;
    }

    @Nullable
    private static net.minecraft.core.Direction inferAttachmentFace(Level world, BlockPos pos) {
        if (world == null || !Functions.isChunkLoaded(world, pos)) {
            return null;
        }
        for (net.minecraft.core.Direction face : net.minecraft.core.Direction.values()) {
            BlockPos adjacentPos = pos.relative(face);
            if (!Functions.isChunkLoaded(world, adjacentPos)) {
                continue;
            }
            var adjacentState = world.getBlockState(adjacentPos);
            if (adjacentState.isAir()) {
                return face;
            }
        }
        return net.minecraft.core.Direction.UP;
    }

    private static @Nullable Level resolveWorld(String dimId) {
        return WorldResolveCompat.resolveWorld(dimId);
    }

    private static String getDimensionId(Level world) {
        return world.dimension().identifier().toString();
    }

    private static boolean isHostChunkLoaded(Level world, BlockPos pos) {
        return Functions.isChunkLoaded(world, pos);
    }

    private static boolean hasHostBlock(Level world, BlockPos pos) {
        return getCurrentHostBlockId(world, pos) != null;
    }

    private static boolean isAirBlock(Level world, BlockPos pos) {
        return Functions.isChunkLoaded(world, pos) && world.getBlockState(pos).isAir();
    }

    private static boolean hasNodeBlockEntity(Level world, BlockPos pos) {
        return Functions.isChunkLoaded(world, pos) && world.getBlockEntity(pos) instanceof INodeBlockEntity;
    }

    private static @Nullable String getCurrentHostBlockId(Level world, BlockPos pos) {
        if (!Functions.isChunkLoaded(world, pos)) {
            return null;
        }
        var state = world.getBlockState(pos);
        if (state.isAir() || world.getBlockEntity(pos) instanceof INodeBlockEntity) {
            return null;
        }
        Identifier registryName = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return registryName == null ? null : registryName.toString();
    }

    private static @Nullable PocketNodeRecord resolveValidatedRecord(Level world, PocketNodeRecord record) {
        if (!isHostChunkLoaded(world, record.pos())) {
            return record;
        }
        boolean isAir = isAirBlock(world, record.pos());
        boolean hasNodeBlockEntity = hasNodeBlockEntity(world, record.pos());
        String currentHostBlockId = getCurrentHostBlockId(world, record.pos());
        String resolvedHostBlockId = PocketNodeHostRules.resolveHostBlockId(record.hostBlockId(), currentHostBlockId, isAir, hasNodeBlockEntity);
        if (!PocketNodeHostRules.isHostStateValid(resolvedHostBlockId, currentHostBlockId, isAir, hasNodeBlockEntity)) {
            return null;
        }
        if (Objects.equals(resolvedHostBlockId, record.hostBlockId())) {
            return record;
        }
        return record.withHostBlockId(resolvedHostBlockId);
    }

    public void load() {
        if (loaded) {
            return;
        }

        clearState();
        loaded = true;

        File saveFile = getSaveFile();
        if (!saveFile.exists()) {
            return;
        }

        CompoundTag nbt = NetworkManager.tryReadCompressedNbt(saveFile, "pocket node save");
        if (nbt == null) {
            return;
        }

        ListTag nodes = nbt.getListOrEmpty("nodes");
        for (int i = 0; i < nodes.size(); i++) {
            PocketNodeRecord record = PocketNodeRecord.deserialize(nodes.getCompoundOrEmpty(i));
            if (record != null) {
                putPending(record);
            }
        }

        for (var dimEntry : new ObjectArrayList<>(pendingHosts.object2ObjectEntrySet())) {
            for (var record : new ObjectArrayList<>(dimEntry.getValue().values())) {
                tryActivate(record, true);
            }
        }

        recoverPocketHostsFromLoadedNodes();
    }

    public boolean save() {
        if (!loaded || (!dirty && activeHosts.isEmpty() && pendingHosts.isEmpty())) {
            return true;
        }

        File saveFile = getSaveFile();
        CompoundTag nbt = new CompoundTag();
        ListTag list = new ListTag();

        for (var dimEntry : activeHosts.object2ObjectEntrySet()) {
            for (var host : dimEntry.getValue().values()) {
                list.add(host.toRecord().serialize());
            }
        }
        for (var dimEntry : pendingHosts.object2ObjectEntrySet()) {
            for (var record : dimEntry.getValue().values()) {
                list.add(record.serialize());
            }
        }

        nbt.put("nodes", list);

        if (NetworkManager.tryWriteCompressedNbt(nbt, saveFile, "pocket node save")) {
            dirty = false;
            return true;
        }
        return false;
    }

    public void onServerStop() {
        clearState();
        loaded = false;
        dirty = false;
    }

    public void markDirty() {
        if (loaded) {
            dirty = true;
            DatPersistenceScheduler.INSTANCE.markDirty(DatPersistenceScheduler.Target.POCKET_NODE);
        }
    }

    public void onChunkLoad(Level world, int chunkX, int chunkZ) {
        if (!loaded || isClientWorld(world)) {
            return;
        }
        String dimId = getDimensionId(world);
        long chunkCoord = Functions.mergeChunkCoords(chunkX, chunkZ);

        LongSet activePositions = getChunkPositions(activeChunkIndex.get(dimId), chunkCoord);
        if (activePositions != null && !activePositions.isEmpty()) {
            Long2ObjectMap<PocketNodeHost> activeDimMap = activeHosts.get(dimId);
            chunkLoadIterationScratch.clear();
            chunkLoadIterationScratch.addAll(activePositions);
            for (int i = 0; i < chunkLoadIterationScratch.size(); i++) {
                long posLong = chunkLoadIterationScratch.getLong(i);
                PocketNodeHost host = activeDimMap == null ? null : activeDimMap.get(posLong);
                if (host == null) {
                    continue;
                }
                PocketNodeRecord resolvedRecord = resolveValidatedRecord(world, host.record());
                if (resolvedRecord == null) {
                    removeActiveHost(dimId, posLong);
                    NetworkManager.INSTANCE.removeNode(host.node());
                    syncRemove(dimId, host.record().pos());
                    markDirty();
                    continue;
                }
                if (resolvedRecord != host.record()) {
                    activeDimMap.put(posLong, new PocketNodeHost(resolvedRecord, host.node()));
                    markDirty();
                }
            }
        }

        LongSet positions = getChunkPositions(pendingChunkIndex.get(dimId), chunkCoord);
        if (positions == null || positions.isEmpty()) {
            return;
        }
        chunkLoadIterationScratch.clear();
        chunkLoadIterationScratch.addAll(positions);
        for (int i = 0; i < chunkLoadIterationScratch.size(); i++) {
            long posLong = chunkLoadIterationScratch.getLong(i);
            PocketNodeRecord record = getPendingDimMap(dimId).get(posLong);
            if (record != null) {
                tryActivate(record, true);
            }
        }
    }

    public RegisterPocketNodeResult registerPocketNodeDetailed(Level world, BlockPos pos, NodeType<?> nodeType, @Nullable net.minecraft.core.Direction attachmentFace, @Nullable String customName) {
        if (isClientWorld(world) || nodeType == null || !nodeType.allowsPocketNode() || NodeTypes.getById(nodeType.id()) != nodeType) {
            return RegisterPocketNodeResult.FAILED;
        }

        String dimId = getDimensionId(world);
        long posLong = pos.asLong();
        if (getActiveDimMap(dimId).containsKey(posLong) || getPendingDimMap(dimId).containsKey(posLong)) {
            return RegisterPocketNodeResult.OCCUPIED;
        }
        if (API.getNodeAt(world, pos) != null) {
            return RegisterPocketNodeResult.OCCUPIED;
        }
        if (!isHostChunkLoaded(world, pos) || !hasHostBlock(world, pos)) {
            return RegisterPocketNodeResult.FAILED;
        }

        String hostBlockId = getCurrentHostBlockId(world, pos);
        if (hostBlockId == null) {
            return RegisterPocketNodeResult.FAILED;
        }

        PocketNodeRecord record = new PocketNodeRecord(dimId, pos, nodeType, attachmentFace, customName, hostBlockId);
        RegisterPocketNodeResult activated = tryActivate(record, false);
        if (activated.isSuccess()) {
            markDirty();
            syncAdd(record);
        }
        return activated;
    }

    public boolean removePocketNode(Level world, BlockPos pos, boolean dropItem) {
        if (!loaded) {
            return false;
        }
        String dimId = getDimensionId(world);
        long posLong = pos.asLong();
        PocketNodeHost activeHost = removeActiveHost(dimId, posLong);
        if (activeHost != null) {
            NetworkManager.INSTANCE.removeNode(activeHost.node());
            if (dropItem) {
                dropItem(world, activeHost.record());
            }
            syncRemove(dimId, pos);
            markDirty();
            return true;
        }
        PocketNodeRecord pendingRecord = removePending(dimId, posLong);
        if (pendingRecord != null) {
            if (dropItem) {
                dropItem(world, pendingRecord);
            }
            syncRemove(dimId, pos);
            markDirty();
            return true;
        }
        return false;
    }

    public void onHostBlockBroken(Level world, BlockPos pos) {
        if (!loaded || isClientWorld(world)) {
            return;
        }
        onHostBlockStateChanged(world, pos);
    }

    public void onHostBlockStateChanged(Level world, BlockPos pos) {
        if (!loaded || isClientWorld(world)) {
            return;
        }
        String dimId = getDimensionId(world);
        long posLong = pos.asLong();

        Long2ObjectMap<PocketNodeHost> activeDimMap = activeHosts.get(dimId);
        PocketNodeHost activeHost = activeDimMap == null ? null : activeDimMap.get(posLong);
        if (activeHost != null) {
            PocketNodeRecord resolvedRecord = resolveValidatedRecord(world, activeHost.record());
            if (resolvedRecord == null) {
                removePocketNode(world, pos, true);
                return;
            }
            if (resolvedRecord != activeHost.record()) {
                activeDimMap.put(posLong, new PocketNodeHost(resolvedRecord, activeHost.node()));
                markDirty();
            }
        }

        Long2ObjectMap<PocketNodeRecord> pendingDimMap = pendingHosts.get(dimId);
        PocketNodeRecord pendingRecord = pendingDimMap == null ? null : pendingDimMap.get(posLong);
        if (pendingRecord == null) {
            return;
        }
        PocketNodeRecord resolvedPendingRecord = resolveValidatedRecord(world, pendingRecord);
        if (resolvedPendingRecord == null) {
            removePending(dimId, posLong);
            markDirty();
            return;
        }
        if (resolvedPendingRecord != pendingRecord) {
            pendingDimMap.put(posLong, resolvedPendingRecord);
            markDirty();
        }
    }

    public ObjectList<PocketNodeRecord> getActiveRecords(String dimId) {
        ObjectList<PocketNodeRecord> result = new ObjectArrayList<>();
        Long2ObjectMap<PocketNodeHost> dimMap = activeHosts.get(dimId);
        if (dimMap == null) {
            return result;
        }
        for (var host : dimMap.values()) {
            result.add(host.toRecord());
        }
        return result;
    }

    public boolean isActivePocketNode(Level world, BlockPos pos, @Nullable NodeType<?> nodeType) {
        if (!loaded || world == null || isClientWorld(world)) {
            return false;
        }
        return isActivePocketNode(getDimensionId(world), pos, nodeType);
    }

    public boolean isActivePocketNode(String dimId, BlockPos pos, @Nullable NodeType<?> nodeType) {
        if (!loaded || pos == null) {
            return false;
        }
        Long2ObjectMap<PocketNodeHost> dimMap = activeHosts.get(dimId);
        if (dimMap == null) {
            return false;
        }
        PocketNodeHost host = dimMap.get(pos.asLong());
        if (host == null) {
            return false;
        }
        return nodeType == null || host.record().nodeType() == nodeType;
    }

    private RegisterPocketNodeResult tryActivate(PocketNodeRecord record, boolean dropItemOnConflict) {
        var world = resolveWorld(record.dimensionId());
        if (world == null) {
            return RegisterPocketNodeResult.FAILED;
        }

        String dimId = record.dimensionId();
        long posLong = record.pos().asLong();

        PocketNodeRecord resolvedRecord = resolveValidatedRecord(world, record);
        if (resolvedRecord == null) {
            removePending(dimId, posLong);
            markDirty();
            return RegisterPocketNodeResult.FAILED;
        }
        record = resolvedRecord;

        INode mappedNode = API.getNodeAt(world, record.pos());
        if (mappedNode != null) {
            if (canAdoptLoadedPocketNode(world, record, mappedNode)) {
                removePending(dimId, posLong);
                putActive(new PocketNodeHost(record, mappedNode));
                markDirty();
                return RegisterPocketNodeResult.SUCCESS;
            }
            removePending(dimId, posLong);
            markDirty();
            return RegisterPocketNodeResult.OCCUPIED;
        }

        INode node;
        try {
            node = Functions.createNode(record.nodeType(), record.createNodeContext(world));
        } catch (IllegalArgumentException ex) {
            removePending(dimId, posLong);
            markDirty();
            CirculationFlowNetworks.LOGGER.warn(
                "Skipping legacy pocket node record with unsupported type={} pos={} dim={}",
                record.nodeType().id(),
                record.pos(),
                record.dimensionId(),
                ex
            );
            return RegisterPocketNodeResult.FAILED;
        }
        if (record.customName() != null) {
            node.setCustomName(record.customName());
        }
        node.setActive(true);
        NetworkManager.AddNodeResult addResult = NetworkManager.INSTANCE.addNode(node);
        if (!addResult.isSuccess()) {
            node.setActive(false);
            if (shouldDiscardPendingRecord(world, record, node, addResult)) {
                removePending(dimId, posLong);
                if (dropItemOnConflict
                    && addResult.getStatus() == NetworkManager.AddNodeResult.Status.HUB_CONFLICT
                    && isHostChunkLoaded(world, record.pos())) {
                    dropItem(world, record);
                }
                markDirty();
            }
            CirculationFlowNetworks.LOGGER.warn(
                "Failed to activate pocket node type={} pos={} dim={} status={}",
                record.nodeType().id(),
                record.pos(),
                record.dimensionId(),
                addResult.getStatus()
            );
            return addResult.getStatus() == NetworkManager.AddNodeResult.Status.HUB_CONFLICT
                ? RegisterPocketNodeResult.HUB_CONFLICT
                : RegisterPocketNodeResult.FAILED;
        }
        removePending(dimId, posLong);
        putActive(new PocketNodeHost(record, node));
        return RegisterPocketNodeResult.SUCCESS;
    }

    private void putActive(PocketNodeHost host) {
        String dimId = host.record().dimensionId();
        long posLong = host.record().pos().asLong();
        long chunkCoord = Functions.mergeChunkCoords(host.record().pos());
        getActiveDimMap(dimId).put(posLong, host);
        indexChunkPosition(getChunkIndex(activeChunkIndex, dimId), chunkCoord, posLong);
    }

    private @org.jetbrains.annotations.Nullable PocketNodeHost removeActiveHost(String dimId, long posLong) {
        Long2ObjectMap<PocketNodeHost> dimMap = activeHosts.get(dimId);
        if (dimMap == null) {
            return null;
        }
        PocketNodeHost removed = dimMap.remove(posLong);
        if (removed != null) {
            unindexChunkPosition(activeChunkIndex.get(dimId), Functions.mergeChunkCoords(removed.record().pos()), posLong);
            if (dimMap.isEmpty()) {
                activeHosts.remove(dimId);
                activeChunkIndex.remove(dimId);
            }
        }
        return removed;
    }

    private void putPending(PocketNodeRecord record) {
        String dimId = record.dimensionId();
        long posLong = record.pos().asLong();
        long chunkCoord = Functions.mergeChunkCoords(record.pos());
        getPendingDimMap(dimId).put(posLong, record);
        indexChunkPosition(getChunkIndex(pendingChunkIndex, dimId), chunkCoord, posLong);
    }

    private @org.jetbrains.annotations.Nullable PocketNodeRecord removePending(String dimId, long posLong) {
        Long2ObjectMap<PocketNodeRecord> dimMap = pendingHosts.get(dimId);
        if (dimMap == null) {
            return null;
        }
        PocketNodeRecord removed = dimMap.remove(posLong);
        if (removed != null) {
            unindexChunkPosition(pendingChunkIndex.get(dimId), Functions.mergeChunkCoords(removed.pos()), posLong);
            if (dimMap.isEmpty()) {
                pendingHosts.remove(dimId);
                pendingChunkIndex.remove(dimId);
            }
        }
        return removed;
    }

    private Long2ObjectMap<PocketNodeHost> getActiveDimMap(String dimId) {
        return activeHosts.computeIfAbsent(dimId, ignored -> new Long2ObjectOpenHashMap<>());
    }

    private Long2ObjectMap<PocketNodeRecord> getPendingDimMap(String dimId) {
        return pendingHosts.computeIfAbsent(dimId, ignored -> new Long2ObjectOpenHashMap<>());
    }

    private void dropItem(Level world, PocketNodeRecord record) {
        ItemStack stack = PocketNodeItems.createStack(record.nodeType());
        if (stack.isEmpty()) {
            return;
        }
        double x = record.pos().getX() + 0.5D;
        double y = record.pos().getY() + 0.5D;
        double z = record.pos().getZ() + 0.5D;
        world.addFreshEntity(new ItemEntity(world, x, y, z, stack));
    }

    private void syncAdd(PocketNodeRecord record) {
        syncToDimensionPlayers(record.dimensionId(), new PocketNodeRendering(record));
    }

    private void syncRemove(String dimId, BlockPos pos) {
        syncToDimensionPlayers(dimId, new PocketNodeRendering(dimId, pos));
    }

    private void syncToDimensionPlayers(String dimId, PocketNodeRendering packet) {
        var world = resolveWorld(dimId);
        if (world == null) {
            return;
        }
        for (var player : world.players()) {
            if (player instanceof ServerPlayer serverPlayer) {
                CirculationFlowNetworks.sendToPlayer(packet, serverPlayer);
            }
        }
    }

    private void recoverPocketHostsFromLoadedNodes() {
        for (INode node : new ObjectArrayList<>(NetworkManager.INSTANCE.getActiveNodes())) {
            var world = node.getWorld();
            if (world == null || isClientWorld(world) || !isRecoverablePocketNode(world, node)) {
                continue;
            }
            String dimId = getDimensionId(world);
            long posLong = node.getPos().asLong();
            if (getActiveDimMap(dimId).containsKey(posLong) || getPendingDimMap(dimId).containsKey(posLong)) {
                continue;
            }
            PocketNodeRecord record = new PocketNodeRecord(
                dimId,
                node.getPos(),
                node.getNodeType(),
                inferAttachmentFace(world, node.getPos()),
                node.getCustomName(),
                getCurrentHostBlockId(world, node.getPos())
            );
            putActive(new PocketNodeHost(record, node));
            markDirty();
            CirculationFlowNetworks.LOGGER.warn(
                "Recovered pocket node state from loaded grid node type={} pos={} dim={}",
                node.getNodeType().id(),
                node.getPos(),
                dimId
            );
        }
    }

    private void clearState() {
        activeHosts.clear();
        pendingHosts.clear();
        activeChunkIndex.clear();
        pendingChunkIndex.clear();
    }

    private File getSaveFile() {
        return new File(NetworkManager.getSaveFile(), "PocketNodes.dat");
    }

    public enum RegisterPocketNodeResult {
        SUCCESS,
        OCCUPIED,
        HUB_CONFLICT,
        FAILED;

        public boolean isSuccess() {
            return this == SUCCESS;
        }
    }
}
