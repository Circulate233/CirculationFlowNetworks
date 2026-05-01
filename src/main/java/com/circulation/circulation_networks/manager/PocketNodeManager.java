package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.api.API;
import com.circulation.circulation_networks.api.INodeBlockEntity;
import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.api.node.NodeType;
import com.circulation.circulation_networks.network.nodes.NodeFactory;
import com.circulation.circulation_networks.packets.PocketNodeRendering;
import com.circulation.circulation_networks.pocket.PocketNodeHost;
import com.circulation.circulation_networks.pocket.PocketNodeHostRules;
import com.circulation.circulation_networks.pocket.PocketNodeRecord;
import com.circulation.circulation_networks.registry.NodeTypes;
import com.circulation.circulation_networks.registry.PocketNodeItems;
import com.circulation.circulation_networks.utils.ChunkCoordUtils;
import static com.circulation.circulation_networks.utils.SideCompat.isClientWorld;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
//~ mc_imports
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
//? if <1.20 {
import net.minecraft.entity.item.EntityItem;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.Constants;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ResourceLocation;
//?} else {
/*import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.World;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.nbt.Tag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
*///?}

import org.jetbrains.annotations.Nullable;
import java.io.File;
import java.util.Objects;

public final class PocketNodeManager {

    public static final PocketNodeManager INSTANCE = new PocketNodeManager();
    private final Int2ObjectMap<Long2ObjectMap<PocketNodeHost>> activeHosts = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<Long2ObjectMap<PocketNodeRecord>> pendingHosts = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<Long2ObjectMap<LongSet>> activeChunkIndex = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<Long2ObjectMap<LongSet>> pendingChunkIndex = new Int2ObjectOpenHashMap<>();
    private final LongArrayList chunkLoadIterationScratch = new LongArrayList();
    private boolean loaded;
    private boolean dirty;
    private PocketNodeManager() {
    }

    private static Long2ObjectMap<LongSet> getChunkIndex(Int2ObjectMap<Long2ObjectMap<LongSet>> index, int dimId) {
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

    //~ if >=1.20 'World ' -> 'Level ' {
    private static boolean shouldDiscardPendingRecord(World world, PocketNodeRecord record, INode node, NetworkManager.AddNodeResult addResult) {
        if (!node.isActive()) {
            return true;
        }
        if (isHostChunkLoaded(world, record.pos()) && resolveValidatedRecord(world, record) == null) {
            return true;
        }
        return addResult.getStatus() == NetworkManager.AddNodeResult.Status.HUB_CONFLICT;
    }

    //~}

    //~ if >=1.20 '(World ' -> '(Level ' {
    private static boolean canAdoptLoadedPocketNode(World world, PocketNodeRecord record, INode node) {
        if (node == null || !node.isActive()) {
            return false;
        }
        if (node.getNodeType() != record.nodeType()) {
            return false;
        }
        return resolveValidatedRecord(world, record) != null && isRecoverablePocketNode(world, node);
    }

    private static boolean isRecoverablePocketNode(World world, INode node) {
        if (world == null || node == null || !node.getNodeType().allowsPocketNode()) {
            return false;
        }
        BlockPos pos = node.getPos();
        if (!isHostChunkLoaded(world, pos) || getCurrentHostBlockId(world, pos) == null) {
            return false;
        }
        return API.getNodeAt(world, pos) == node;
    }
    //~}

    //? if <1.20 {
    @Nullable
    private static net.minecraft.util.EnumFacing inferAttachmentFace(World world, BlockPos pos) {
        if (world == null || !ChunkCoordUtils.isChunkLoaded(world, pos)) {
            return null;
        }
        for (net.minecraft.util.EnumFacing face : net.minecraft.util.EnumFacing.values()) {
            BlockPos adjacentPos = pos.offset(face);
            if (!ChunkCoordUtils.isChunkLoaded(world, adjacentPos)) {
                continue;
            }
            var adjacentState = world.getBlockState(adjacentPos);
            if (adjacentState.getBlock().isAir(adjacentState, world, adjacentPos)) {
                return face;
            }
        }
        return net.minecraft.util.EnumFacing.UP;
    }
    //?} else {
    /*@Nullable
    private static net.minecraft.core.Direction inferAttachmentFace(Level world, BlockPos pos) {
        if (world == null || !ChunkCoordUtils.isChunkLoaded(world, pos)) {
            return null;
        }
        for (net.minecraft.core.Direction face : net.minecraft.core.Direction.values()) {
            BlockPos adjacentPos = pos.relative(face);
            if (!ChunkCoordUtils.isChunkLoaded(world, adjacentPos)) {
                continue;
            }
            var adjacentState = world.getBlockState(adjacentPos);
            if (adjacentState.isAir()) {
                return face;
            }
        }
        return net.minecraft.core.Direction.UP;
    }
    *///?}

    //? if >=1.20 {
    /*private static @Nullable MinecraftServer getCurrentServer() {
        //~ if >=1.21 'net.minecraftforge.server.ServerLifecycleHooks' -> 'net.neoforged.neoforge.server.ServerLifecycleHooks' {
        return net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        //~}
    }
    *///?}

    //? if <1.20 {
    private static @Nullable World resolveWorld(int dimId) {
        return DimensionManager.getWorld(dimId);
    }

    private static int getDimensionId(World world) {
        return world.provider.getDimension();
    }

    private static boolean isHostChunkLoaded(World world, BlockPos pos) {
        return ChunkCoordUtils.isChunkLoaded(world, pos);
    }

    private static boolean hasHostBlock(World world, BlockPos pos) {
        return getCurrentHostBlockId(world, pos) != null;
    }

    private static boolean isAirBlock(World world, BlockPos pos) {
        if (!ChunkCoordUtils.isChunkLoaded(world, pos)) {
            return false;
        }
        var state = world.getBlockState(pos);
        return state.getBlock().isAir(state, world, pos);
    }

    private static boolean hasNodeBlockEntity(World world, BlockPos pos) {
        return ChunkCoordUtils.isChunkLoaded(world, pos) && world.getTileEntity(pos) instanceof INodeBlockEntity;
    }

    private static @Nullable String getCurrentHostBlockId(World world, BlockPos pos) {
        if (!ChunkCoordUtils.isChunkLoaded(world, pos)) {
            return null;
        }
        var state = world.getBlockState(pos);
        if (state.getBlock().isAir(state, world, pos) || world.getTileEntity(pos) instanceof INodeBlockEntity) {
            return null;
        }
        ResourceLocation registryName = state.getBlock().getRegistryName();
        return registryName == null ? null : registryName.toString();
    }

    private static @Nullable PocketNodeRecord resolveValidatedRecord(World world, PocketNodeRecord record) {
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
    //?} else {
    /*private static @Nullable Level resolveWorld(int dimId) {
        MinecraftServer server = getCurrentServer();
        if (server == null) {
            return null;
        }
        for (Level level : server.getAllLevels()) {
            if (level.dimension().location().hashCode() == dimId) {
                return level;
            }
        }
        return null;
    }

    private static int getDimensionId(Level world) {
        return world.dimension().location().hashCode();
    }

    private static boolean isHostChunkLoaded(Level world, BlockPos pos) {
        return ChunkCoordUtils.isChunkLoaded(world, pos);
    }

    private static boolean hasHostBlock(Level world, BlockPos pos) {
        return getCurrentHostBlockId(world, pos) != null;
    }

    private static boolean isAirBlock(Level world, BlockPos pos) {
        return ChunkCoordUtils.isChunkLoaded(world, pos) && world.getBlockState(pos).isAir();
    }

    private static boolean hasNodeBlockEntity(Level world, BlockPos pos) {
        return ChunkCoordUtils.isChunkLoaded(world, pos) && world.getBlockEntity(pos) instanceof INodeBlockEntity;
    }

    private static @Nullable String getCurrentHostBlockId(Level world, BlockPos pos) {
        if (!ChunkCoordUtils.isChunkLoaded(world, pos)) {
            return null;
        }
        var state = world.getBlockState(pos);
        if (state.isAir() || world.getBlockEntity(pos) instanceof INodeBlockEntity) {
            return null;
        }
        ResourceLocation registryName = BuiltInRegistries.BLOCK.getKey(state.getBlock());
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
    *///?}

    //~ if >=1.20 'NBTTagList' -> 'ListTag' {
    //~ if >=1.20 '.tagCount()' -> '.size()' {
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

        //~ if >=1.20 'NBTTagCompound ' -> 'CompoundTag ' {
        NBTTagCompound nbt = NetworkManager.tryReadCompressedNbt(saveFile, "pocket node save");
        //~}
        if (nbt == null) {
            return;
        }

        //? if <1.20 {
        NBTTagList nodes = nbt.getTagList("nodes", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < nodes.tagCount(); i++) {
            PocketNodeRecord record = PocketNodeRecord.deserialize(nodes.getCompoundTagAt(i));
            //?} else {
        /*NBTTagList nodes = nbt.getList("nodes", Tag.TAG_COMPOUND);
        for (int i = 0; i < nodes.tagCount(); i++) {
            PocketNodeRecord record = PocketNodeRecord.deserialize(nodes.getCompound(i));
        *///?}
            if (record != null) {
                putPending(record);
            }
        }

        for (var dimEntry : new ObjectArrayList<>(pendingHosts.int2ObjectEntrySet())) {
            for (var record : new ObjectArrayList<>(dimEntry.getValue().values())) {
                tryActivate(record, true);
            }
        }

        recoverPocketHostsFromLoadedNodes();
    }
    //~}
    //~}

    //~ if >=1.20 'NBTTagCompound' -> 'CompoundTag' {
    //~ if >=1.20 'NBTTagList' -> 'ListTag' {
    //~ if >=1.20 '.appendTag(' -> '.add(' {
    //~ if >=1.20 '.setTag(' -> '.put(' {
    public boolean save() {
        if (!loaded || (!dirty && activeHosts.isEmpty() && pendingHosts.isEmpty())) {
            return true;
        }

        File saveFile = getSaveFile();
        NBTTagCompound nbt = new NBTTagCompound();
        NBTTagList list = new NBTTagList();

        for (var dimEntry : activeHosts.int2ObjectEntrySet()) {
            for (var host : dimEntry.getValue().values()) {
                list.appendTag(host.toRecord().serialize());
            }
        }
        for (var dimEntry : pendingHosts.int2ObjectEntrySet()) {
            for (var record : dimEntry.getValue().values()) {
                list.appendTag(record.serialize());
            }
        }

        nbt.setTag("nodes", list);

        if (NetworkManager.tryWriteCompressedNbt(nbt, saveFile, "pocket node save")) {
            dirty = false;
            return true;
        }
        return false;
    }
    //~}
    //~}
    //~}
    //~}

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

    //~ if >=1.20 'World ' -> 'Level ' {
    public void onChunkLoad(World world, int chunkX, int chunkZ) {
        if (!loaded || isClientWorld(world)) {
            return;
        }
        int dimId = getDimensionId(world);
        long chunkCoord = ChunkCoordUtils.mergeChunkCoords(chunkX, chunkZ);

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
                    NetworkManager.INSTANCE.removeNode(dimId, host.record().pos());
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
    //~}

    //~ if >=1.20 'net.minecraft.util.EnumFacing' -> 'net.minecraft.core.Direction' {
    //~ if >=1.20 'World ' -> 'Level ' {
    //~ if >=1.20 '.toLong()' -> '.asLong()' {
    public RegisterPocketNodeResult registerPocketNodeDetailed(World world, BlockPos pos, NodeType<?> nodeType, @Nullable net.minecraft.util.EnumFacing attachmentFace, @Nullable String customName) {
        if (isClientWorld(world) || nodeType == null || !nodeType.allowsPocketNode() || NodeTypes.getById(nodeType.id()) != nodeType) {
            return RegisterPocketNodeResult.FAILED;
        }

        int dimId = getDimensionId(world);
        long posLong = pos.toLong();
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
    //~}
    //~}
    //~}

    //~ if >=1.20 'World ' -> 'Level ' {
    //~ if >=1.20 '.toLong()' -> '.asLong()' {
    public boolean removePocketNode(World world, BlockPos pos, boolean dropItem) {
        if (!loaded) {
            return false;
        }
        int dimId = getDimensionId(world);
        long posLong = pos.toLong();
        PocketNodeHost activeHost = removeActiveHost(dimId, posLong);
        if (activeHost != null) {
            NetworkManager.INSTANCE.removeNode(dimId, activeHost.record().pos());
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
    //~}
    //~}

    //~ if >=1.20 'World ' -> 'Level ' {
    public void onHostBlockBroken(World world, BlockPos pos) {
        if (!loaded || isClientWorld(world)) {
            return;
        }
        onHostBlockStateChanged(world, pos);
    }

    //~ if >=1.20 '.toLong()' -> '.asLong()' {
    public void onHostBlockStateChanged(World world, BlockPos pos) {
        if (!loaded || isClientWorld(world)) {
            return;
        }
        int dimId = getDimensionId(world);
        long posLong = pos.toLong();

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
    //~}

    public ObjectList<PocketNodeRecord> getActiveRecords(int dimId) {
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
    //~}

    //~ if >=1.20 'World ' -> 'Level ' {
    //~ if >=1.20 '.toLong()' -> '.asLong()' {
    public boolean isActivePocketNode(World world, BlockPos pos, @Nullable NodeType<?> nodeType) {
        if (!loaded || world == null || isClientWorld(world)) {
            return false;
        }
        return isActivePocketNode(getDimensionId(world), pos, nodeType);
    }

    public boolean isActivePocketNode(int dimId, BlockPos pos, @Nullable NodeType<?> nodeType) {
        if (!loaded || pos == null) {
            return false;
        }
        Long2ObjectMap<PocketNodeHost> dimMap = activeHosts.get(dimId);
        if (dimMap == null) {
            return false;
        }
        PocketNodeHost host = dimMap.get(pos.toLong());
        if (host == null) {
            return false;
        }
        return nodeType == null || host.record().nodeType() == nodeType;
    }
    //~}
    //~}

    //~ if >=1.20 '.toLong()' -> '.asLong()' {
    private RegisterPocketNodeResult tryActivate(PocketNodeRecord record, boolean dropItemOnConflict) {
        var world = resolveWorld(record.dimensionId());
        if (world == null) {
            return RegisterPocketNodeResult.FAILED;
        }

        int dimId = record.dimensionId();
        long posLong = record.pos().toLong();

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
            node = NodeFactory.createNode(record.nodeType(), record.createNodeContext(world));
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
    //~}

    //~ if >=1.20 '.toLong()' -> '.asLong()' {
    private void putActive(PocketNodeHost host) {
        int dimId = host.record().dimensionId();
        long posLong = host.record().pos().toLong();
        long chunkCoord = ChunkCoordUtils.mergeChunkCoords(host.record().pos());
        getActiveDimMap(dimId).put(posLong, host);
        indexChunkPosition(getChunkIndex(activeChunkIndex, dimId), chunkCoord, posLong);
    }

    private @org.jetbrains.annotations.Nullable PocketNodeHost removeActiveHost(int dimId, long posLong) {
        Long2ObjectMap<PocketNodeHost> dimMap = activeHosts.get(dimId);
        if (dimMap == null) {
            return null;
        }
        PocketNodeHost removed = dimMap.remove(posLong);
        if (removed != null) {
            unindexChunkPosition(activeChunkIndex.get(dimId), ChunkCoordUtils.mergeChunkCoords(removed.record().pos()), posLong);
            if (dimMap.isEmpty()) {
                activeHosts.remove(dimId);
                activeChunkIndex.remove(dimId);
            }
        }
        return removed;
    }
    //~}

    //~ if >=1.20 '.toLong()' -> '.asLong()' {
    private void putPending(PocketNodeRecord record) {
        int dimId = record.dimensionId();
        long posLong = record.pos().toLong();
        long chunkCoord = ChunkCoordUtils.mergeChunkCoords(record.pos());
        getPendingDimMap(dimId).put(posLong, record);
        indexChunkPosition(getChunkIndex(pendingChunkIndex, dimId), chunkCoord, posLong);
    }

    private @org.jetbrains.annotations.Nullable PocketNodeRecord removePending(int dimId, long posLong) {
        Long2ObjectMap<PocketNodeRecord> dimMap = pendingHosts.get(dimId);
        if (dimMap == null) {
            return null;
        }
        PocketNodeRecord removed = dimMap.remove(posLong);
        if (removed != null) {
            unindexChunkPosition(pendingChunkIndex.get(dimId), ChunkCoordUtils.mergeChunkCoords(removed.pos()), posLong);
            if (dimMap.isEmpty()) {
                pendingHosts.remove(dimId);
                pendingChunkIndex.remove(dimId);
            }
        }
        return removed;
    }

    private Long2ObjectMap<PocketNodeHost> getActiveDimMap(int dimId) {
        return activeHosts.computeIfAbsent(dimId, ignored -> new Long2ObjectOpenHashMap<>());
    }

    private Long2ObjectMap<PocketNodeRecord> getPendingDimMap(int dimId) {
        return pendingHosts.computeIfAbsent(dimId, ignored -> new Long2ObjectOpenHashMap<>());
    }
    //~}

    //~ if >=1.20 'World ' -> 'Level ' {
    private void dropItem(World world, PocketNodeRecord record) {
        ItemStack stack = PocketNodeItems.createStack(record.nodeType());
        if (stack.isEmpty()) {
            return;
        }
        double x = record.pos().getX() + 0.5D;
        double y = record.pos().getY() + 0.5D;
        double z = record.pos().getZ() + 0.5D;
        //? if <1.20 {
        world.spawnEntity(new EntityItem(world, x, y, z, stack));
        //?} else {
        /*world.addFreshEntity(new ItemEntity(world, x, y, z, stack));
         *///?}
    }
    //~}

    private void syncAdd(PocketNodeRecord record) {
        syncToDimensionPlayers(record.dimensionId(), new PocketNodeRendering(record));
    }

    private void syncRemove(int dimId, BlockPos pos) {
        syncToDimensionPlayers(dimId, new PocketNodeRendering(dimId, pos));
    }

    //~ if >=1.20 'EntityPlayerMP' -> 'ServerPlayer' {
    private void syncToDimensionPlayers(int dimId, PocketNodeRendering packet) {
        var world = resolveWorld(dimId);
        if (world == null) {
            return;
        }
        //? if <1.20 {
        for (var player : world.playerEntities) {
            if (player instanceof EntityPlayerMP serverPlayer) {
                CirculationFlowNetworks.sendToPlayer(packet, serverPlayer);
            }
        }
        //?} else {
        /*for (var player : world.players()) {
            if (player instanceof EntityPlayerMP serverPlayer) {
                CirculationFlowNetworks.sendToPlayer(packet, serverPlayer);
            }
        }
        *///?}
    }
    //~}

    //~ if >=1.20 '.toLong()' -> '.asLong()' {
    private void recoverPocketHostsFromLoadedNodes() {
        for (INode node : new ObjectArrayList<>(NetworkManager.INSTANCE.getActiveNodes())) {
            var world = node.getWorld();
            if (world == null || isClientWorld(world) || !isRecoverablePocketNode(world, node)) {
                continue;
            }
            int dimId = getDimensionId(world);
            long posLong = node.getPos().toLong();
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
    //~}

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
