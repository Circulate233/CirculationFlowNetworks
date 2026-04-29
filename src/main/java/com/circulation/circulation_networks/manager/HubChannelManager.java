package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.api.IGrid;
import com.circulation.circulation_networks.api.INodeBlockEntity;
import com.circulation.circulation_networks.api.hub.ChannelSnapshotEntry;
import com.circulation.circulation_networks.api.hub.ChannelSnapshotList;
import com.circulation.circulation_networks.api.hub.ChargingPreference;
import com.circulation.circulation_networks.api.hub.HubPermissionLevel;
import com.circulation.circulation_networks.api.hub.IHubPlugin;
import com.circulation.circulation_networks.api.hub.PermissionMode;
import com.circulation.circulation_networks.api.hub.PermissionSnapshotEntry;
import com.circulation.circulation_networks.api.hub.PermissionSnapshotList;
import com.circulation.circulation_networks.api.node.IHubNode;
import com.circulation.circulation_networks.events.BlockEntityLifeCycleEvent;
import com.circulation.circulation_networks.items.HubChannelPluginData;
import com.circulation.circulation_networks.network.hub.HubCapabilitys;
import com.circulation.circulation_networks.network.hub.HubChannel;
import com.circulation.circulation_networks.network.nodes.HubNode;
import com.circulation.circulation_networks.utils.HubPlatformServices;
//? if <1.20
import com.github.bsideup.jabel.Desugar;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
//~ mc_imports
import it.unimi.dsi.fastutil.objects.ReferenceSets;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
//? if <1.20 {
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraftforge.common.util.Constants;
//?} else {
/*import net.minecraft.nbt.Tag;
 *///?}

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;

import static com.circulation.circulation_networks.network.nodes.HubNode.EMPTY;
import static com.circulation.circulation_networks.utils.SideCompat.isClientWorld;

public final class HubChannelManager {

    private static final int MAX_CHANNEL_NAME_LENGTH = 32;

    public static final HubChannelManager INSTANCE = new HubChannelManager();

    private final Object2ReferenceMap<UUID, HubChannel> channels = new Object2ReferenceOpenHashMap<>();
    private final Reference2ObjectMap<IHubNode, UUID> hubChannels = new Reference2ObjectOpenHashMap<>();
    private long snapshotVersion = 1L;
    private boolean loaded;
    private boolean dirty;

    private HubChannelManager() {
        hubChannels.defaultReturnValue(EMPTY);
    }

    //~ if >=1.20 'net.minecraft.world.World' -> 'net.minecraft.world.level.Level' {
    public void bindHub(IHubNode hub) {
        if (hub == null || !NetworkManager.isServerAvailable()) {
            return;
        }
        register(hub, hub.getChannelId(), hub.getChannelName(), hub.getPermissionMode());
    }

    public void register(IHubNode hub, UUID channelId, String name, PermissionMode permissionMode) {
        if (hub == null || !NetworkManager.isServerAvailable()) {
            return;
        }
        ensureLoaded();
        if (channelId == null || name == null) {
            return;
        }

        var grid = hub.getGrid();
        if (grid == null) {
            return;
        }

        unregister(hub);
        if (channelId.equals(EMPTY) || name.isEmpty()) {
            return;
        }


        HubChannel channel = channels.get(channelId);
        if (channel == null) {
            channel = new HubChannel(channelId, name, hub.getOwner(), permissionMode, hub.getExplicitPermissions());
            if (hub.getPlayerPreferences() != null) {
                for (var entry : hub.getPlayerPreferences().entrySet()) {
                    channel.setChargingPreference(entry.getKey(), entry.getValue());
                }
            }
            channels.put(channelId, channel);
            markSnapshotsDirty();
            markDirty();
        } else {
            syncHubFromChannel(hub, channel);
        }

        channel.addGrid(grid);
        hubChannels.put(hub, channelId);
    }

    public void unregister(IHubNode hub) {
        if (hub == null || !NetworkManager.isServerAvailable()) {
            return;
        }
        ensureLoaded();
        UUID oldChannelId = hubChannels.remove(hub);
        if (oldChannelId == EMPTY) {
            return;
        }

        HubChannel channel = channels.get(oldChannelId);
        if (channel == null) {
            return;
        }

        IGrid grid = hub.getGrid();
        if (grid != null) {
            channel.removeGrid(grid);
        }
    }

    public void updateChannelFromHub(IHubNode hub) {
        if (hub == null || !NetworkManager.isServerAvailable()) {
            return;
        }
        ensureLoaded();
        UUID boundChannelId = hubChannels.get(hub);
        if (boundChannelId == EMPTY || !boundChannelId.equals(hub.getChannelId())) {
            bindHub(hub);
            return;
        }

        HubChannel channel = channels.get(boundChannelId);
        if (channel == null) {
            bindHub(hub);
            return;
        }

        channel.setName(hub.getChannelName());
        channel.setPermissionMode(hub.getPermissionMode());
        channel.setOwner(hub.getOwner());
        channel.replaceExplicitPermissions(new Object2ReferenceOpenHashMap<>(hub.getExplicitPermissions()));
        markSnapshotsDirty();
        markDirty();
    }

    public long getSnapshotVersion() {
        ensureLoaded();
        return snapshotVersion;
    }

    @Nullable
    public HubChannel getChannel(UUID channelId) {
        ensureLoaded();
        return channels.get(channelId);
    }

    public List<HubChannel> getChannels() {
        ensureLoaded();
        return Collections.unmodifiableList(new ObjectArrayList<>(channels.values()));
    }

    public ChannelSnapshotList getAccessibleChannelSnapshots(UUID playerId) {
        return getAccessibleChannelSnapshots(playerId, null);
    }

    public ChannelSnapshotList getAccessibleChannelSnapshots(UUID playerId, @Nullable UUID connectedChannelId) {
        ensureLoaded();
        List<ChannelSnapshotEntry> entries = new ObjectArrayList<>();
        for (HubChannel channel : channels.values()) {
            HubPermissionLevel permissionLevel = channel.getPermissionLevel(playerId);
            if (permissionLevel == HubPermissionLevel.NONE) {
                continue;
            }
            entries.add(new ChannelSnapshotEntry(
                channel.getChannelId(),
                channel.getName(),
                channel.getPermissionMode(),
                permissionLevel,
                channel.getChannelId().equals(connectedChannelId)
            ));
        }
        entries.sort(Comparator.comparing(ChannelSnapshotEntry::name, String.CASE_INSENSITIVE_ORDER)
                               .thenComparing(entry -> entry.id().toString()));
        return new ChannelSnapshotList(entries);
    }

    public String getAccessibleChannelSnapshotsJson(UUID playerId) {
        return getAccessibleChannelSnapshots(playerId).toJson();
    }

    public PermissionSnapshotList getOnlinePlayerSnapshots(UUID channelId) {
        ensureLoaded();
        HubChannel channel = channels.get(channelId);
        if (channel == null) {
            return PermissionSnapshotList.EMPTY;
        }

        Map<UUID, String> playerNames = new Object2ObjectOpenHashMap<>();
        List<PermissionSnapshotEntry> entries = new ObjectArrayList<>();
        for (HubPlatformServices.PlayerIdentity player : HubPlatformServices.INSTANCE.getOnlinePlayers()) {
            playerNames.put(player.id(), player.name());
            entries.add(new PermissionSnapshotEntry(player.id(), player.name(), channel.getPermissionLevel(player.id())));
        }

        if (channel.getOwner() != null && !playerNames.containsKey(channel.getOwner())) {
            UUID ownerId = channel.getOwner();
            String ownerName = formatOfflinePlayerName(ownerId);
            playerNames.put(ownerId, ownerName);
            entries.add(new PermissionSnapshotEntry(ownerId, ownerName, channel.getPermissionLevel(ownerId)));
        }

        for (UUID playerId : channel.getExplicitPermissions().keySet()) {
            if (playerNames.containsKey(playerId)) {
                continue;
            }
            String playerName = formatOfflinePlayerName(playerId);
            playerNames.put(playerId, playerName);
            entries.add(new PermissionSnapshotEntry(playerId, playerName, channel.getPermissionLevel(playerId)));
        }

        entries.sort(Comparator.comparing(PermissionSnapshotEntry::name, String.CASE_INSENSITIVE_ORDER)
                               .thenComparing(entry -> entry.id().toString()));
        return new PermissionSnapshotList(entries);
    }

    public boolean bindHubToChannel(IHubNode hub, UUID playerId, UUID channelId) {
        return bindHubToChannel(hub, playerId, channelId, false);
    }

    public boolean bindHubToChannel(IHubNode hub, UUID playerId, UUID channelId, boolean privilegedOverride) {
        ensureLoaded();
        if (hub == null || playerId == null || channelId == null || channelId.equals(EMPTY)) {
            return false;
        }

        HubChannel currentChannel = getCurrentChannel(hub);
        if (currentChannel != null) {
            if (!privilegedOverride && !currentChannel.canEditPermissions(playerId)) {
                return false;
            }
        }

        HubChannel channel = channels.get(channelId);
        if (channel == null || channel.getPermissionLevel(playerId) == HubPermissionLevel.NONE) {
            return false;
        }

        unregister(hub);
        applyChannelToHub(hub, channel);
        IGrid grid = hub.getGrid();
        if (grid != null) {
            channel.addGrid(grid);
        }
        hubChannels.put(hub, channelId);
        persistChannelPluginData(hub);
        return true;
    }

    public boolean createChannel(IHubNode hub, UUID playerId, String rawName, PermissionMode permissionMode) {
        ensureLoaded();
        if (hub == null || playerId == null) {
            return false;
        }

        String normalizedName = normalizeChannelName(rawName);
        if (normalizedName.isEmpty()) {
            return false;
        }

        unregister(hub);

        HubChannel channel = new HubChannel(
            UUID.randomUUID(),
            normalizedName,
            playerId,
            permissionMode != null ? permissionMode : PermissionMode.PRIVATE,
            Collections.emptyMap()
        );
        channels.put(channel.getChannelId(), channel);
        applyChannelToHub(hub, channel);
        IGrid grid = hub.getGrid();
        if (grid != null) {
            channel.addGrid(grid);
        }
        hubChannels.put(hub, channel.getChannelId());
        markSnapshotsDirty();
        markDirty();
        return true;
    }

    public boolean updateChannelSettings(IHubNode hub, UUID playerId, String rawName, PermissionMode permissionMode) {
        return updateChannelSettings(hub, playerId, rawName, permissionMode, false);
    }

    public boolean updateChannelSettings(IHubNode hub, UUID playerId, String rawName, PermissionMode permissionMode, boolean privilegedOverride) {
        ensureLoaded();
        HubChannel channel = getCurrentChannel(hub);
        if (channel == null) {
            return false;
        }

        HubPermissionLevel playerPermission = channel.getPermissionLevel(playerId);
        boolean canRename = privilegedOverride || playerPermission == HubPermissionLevel.OWNER || playerPermission == HubPermissionLevel.ADMIN;
        boolean canChangeMode = privilegedOverride || playerPermission == HubPermissionLevel.OWNER;
        if (!canRename) {
            return false;
        }

        String normalizedName = normalizeChannelName(rawName);
        if (normalizedName.isEmpty()) {
            return false;
        }

        channel.setName(normalizedName);
        if (canChangeMode && permissionMode != null) {
            channel.setPermissionMode(permissionMode);
        }
        syncBoundHubs(channel);
        markSnapshotsDirty();
        markDirty();
        return true;
    }

    public boolean deleteChannel(IHubNode hub, UUID playerId) {
        return deleteChannel(hub, playerId, false);
    }

    public boolean deleteChannel(IHubNode hub, UUID playerId, boolean privilegedOverride) {
        ensureLoaded();
        HubChannel channel = getCurrentChannel(hub);
        if (channel == null) {
            return false;
        }
        if (!privilegedOverride && channel.getPermissionLevel(playerId) != HubPermissionLevel.OWNER) {
            return false;
        }

        UUID channelId = channel.getChannelId();
        List<IHubNode> boundHubs = getBoundHubs(channelId);
        for (IHubNode boundHub : boundHubs) {
            clearChannelBinding(boundHub);
            hubChannels.remove(boundHub);
        }
        channels.remove(channelId);
        markSnapshotsDirty();
        markDirty();
        return true;
    }

    public boolean updateExplicitPermission(IHubNode hub, UUID actorId, UUID targetId, @Nullable HubPermissionLevel permissionLevel) {
        return updateExplicitPermission(hub, actorId, targetId, permissionLevel, false);
    }

    public boolean updateExplicitPermission(IHubNode hub, UUID actorId, UUID targetId, @Nullable HubPermissionLevel permissionLevel, boolean privilegedOverride) {
        ensureLoaded();
        if (actorId == null || targetId == null) {
            return false;
        }

        HubChannel channel = getCurrentChannel(hub);
        if (channel == null) {
            return false;
        }

        HubPermissionLevel actorPermission = channel.getPermissionLevel(actorId);
        HubPermissionLevel targetPermission = channel.getPermissionLevel(targetId);
        if (!canChangePermission(channel, actorId, actorPermission, targetId, targetPermission, permissionLevel, privilegedOverride)) {
            return false;
        }

        if (permissionLevel == null || permissionLevel == HubPermissionLevel.NONE) {
            channel.removeExplicitPermission(targetId);
        } else {
            channel.setExplicitPermission(targetId, permissionLevel);
        }

        syncBoundHubs(channel);
        markSnapshotsDirty();
        markDirty();
        return true;
    }

    public String getOnlinePlayerSnapshotsJson(UUID channelId) {
        return getOnlinePlayerSnapshots(channelId).toJson();
    }

    @NotNull
    public ReferenceSet<IGrid> getChannelGrids(UUID channelId) {
        ensureLoaded();
        HubChannel channel = channels.get(channelId);
        if (channel == null) {
            return ReferenceSets.emptySet();
        }
        refreshChannelGrids(channel);
        return channel.getGrids();
    }

    public void load() {
        ensureLoaded();
    }

    public boolean save() {
        if (!loaded) {
            return true;
        }
        return saveToFile();
    }

    public void onBlockEntityValidate(BlockEntityLifeCycleEvent.Validate event) {
        if (isClientWorld(event.getWorld())) {
            return;
        }
        var te = event.getBlockEntity();
        if (te instanceof INodeBlockEntity hubTE) {
            var node = hubTE.getNode();
            if (node instanceof IHubNode hub) {
                UUID channelId = hub.getChannelId();
                String channelName = hub.getChannelName();
                PermissionMode permissionMode = hub.getPermissionMode();
                if (!channelId.equals(EMPTY) && !channelName.isEmpty()) {
                    register(hub, channelId, channelName, permissionMode);
                }
            }
        }
    }

    public void onBlockEntityInvalidate(BlockEntityLifeCycleEvent.Invalidate event) {
        if (isClientWorld(event.getWorld())) {
            return;
        }
        var te = event.getBlockEntity();
        if (te instanceof INodeBlockEntity hubTE) {
            var node = hubTE.getNode();
            if (node instanceof IHubNode hub) {
                unregister(hub);
            }
        }
    }

    public void onServerStop() {
        channels.clear();
        hubChannels.clear();
        snapshotVersion++;
        loaded = false;
        dirty = false;
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        loadFromFile();
        snapshotVersion++;
        loaded = true;
    }

    private void markSnapshotsDirty() {
        snapshotVersion++;
    }

    public void markChargingDirty() {
        markDirty();
    }

    public void markDirty() {
        if (!loaded) {
            return;
        }
        dirty = true;
        DatPersistenceScheduler.INSTANCE.markDirty(DatPersistenceScheduler.Target.HUB_CHANNEL);
    }

    private void syncHubFromChannel(IHubNode hub, HubChannel channel) {
        if (hub instanceof HubNode hubNode) {
            hubNode.syncFromChannel(channel);
            persistChannelPluginData(hubNode);
            return;
        }

        hub.setChannelId(channel.getChannelId());
        hub.setPermissionMode(channel.getPermissionMode());
        hub.setOwner(channel.getOwner());
        hub.setChannelName(channel.getName());
        for (UUID playerId : new ObjectArrayList<>(hub.getExplicitPermissions().keySet())) {
            hub.removeExplicitPermission(playerId);
        }
        for (var entry : channel.getExplicitPermissions().entrySet()) {
            hub.setExplicitPermission(entry.getKey(), entry.getValue());
        }
        persistChannelPluginData(hub);
    }

    private void clearChannelBinding(IHubNode hub) {
        if (hub instanceof HubNode hubNode) {
            hubNode.clearChannelBinding();
            persistChannelPluginData(hubNode);
            return;
        }

        for (UUID playerId : new ObjectArrayList<>(hub.getExplicitPermissions().keySet())) {
            hub.removeExplicitPermission(playerId);
        }
        hub.setOwner(null);
        hub.setPermissionMode(PermissionMode.PUBLIC);
        hub.setChannelName("");
        hub.setChannelId(EMPTY);
        persistChannelPluginData(hub);
    }

    private void applyChannelToHub(IHubNode hub, HubChannel channel) {
        syncHubFromChannel(hub, channel);
    }

    private void syncBoundHubs(HubChannel channel) {
        for (IHubNode boundHub : getBoundHubs(channel.getChannelId())) {
            applyChannelToHub(boundHub, channel);
        }
    }

    private List<IHubNode> getBoundHubs(UUID channelId) {
        List<IHubNode> boundHubs = new ArrayList<>();
        for (var entry : hubChannels.reference2ObjectEntrySet()) {
            if (channelId.equals(entry.getValue())) {
                boundHubs.add(entry.getKey());
            }
        }
        return boundHubs;
    }

    private void refreshChannelGrids(HubChannel channel) {
        var grids = channel.getGrids();
        grids.clear();
        UUID channelId = channel.getChannelId();
        var iterator = hubChannels.reference2ObjectEntrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            IHubNode hub = entry.getKey();
            if (hub == null || !hub.isActive()) {
                iterator.remove();
                continue;
            }
            if (!channelId.equals(entry.getValue())) {
                continue;
            }
            IGrid grid = hub.getGrid();
            if (grid != null) {
                grids.add(grid);
            }
        }
    }

    @Nullable
    private HubChannel getCurrentChannel(IHubNode hub) {
        if (hub == null) {
            return null;
        }
        UUID channelId = hub.getChannelId();
        return channelId == null || channelId.equals(EMPTY) ? null : channels.get(channelId);
    }

    private static String normalizeChannelName(String rawName) {
        if (rawName == null) {
            return "";
        }

        String trimmed = rawName.trim();
        if (trimmed.length() <= MAX_CHANNEL_NAME_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_CHANNEL_NAME_LENGTH);
    }

    private static String formatOfflinePlayerName(UUID playerId) {
        String raw = playerId != null ? playerId.toString() : "unknown";
        return "#" + raw.substring(0, Math.min(8, raw.length())).toLowerCase(Locale.ROOT);
    }

    private static boolean canChangePermission(HubChannel channel,
                                               UUID actorId,
                                               HubPermissionLevel actorPermission,
                                               UUID targetId,
                                               HubPermissionLevel targetPermission,
                                               @Nullable HubPermissionLevel requestedPermission,
                                               boolean privilegedOverride) {
        if (actorId.equals(targetId)) {
            return false;
        }
        if (channel.getOwner() != null && channel.getOwner().equals(targetId)) {
            return false;
        }
        if (requestedPermission == HubPermissionLevel.OWNER) {
            return false;
        }
        if (privilegedOverride) {
            return true;
        }

        return switch (actorPermission) {
            case OWNER -> true;
            case ADMIN -> targetPermission != HubPermissionLevel.ADMIN
                && targetPermission != HubPermissionLevel.OWNER
                && requestedPermission != HubPermissionLevel.ADMIN;
            default -> false;
        };
    }

    private static void persistChannelPluginData(IHubNode hub) {
        if (hub == null || !hub.hasPluginCapability(HubCapabilitys.CHANNEL_CAPABILITY)) {
            return;
        }

        var plugins = hub.getPlugins();
        if (plugins == null) {
            return;
        }

        HubChannelPluginData.ChannelInfo channelInfo = HubChannelPluginData.getChannelInfo(hub);
        for (int slot = 0; slot < plugins.getSlots(); slot++) {
            var stack = plugins.getStackInSlot(slot);
            if (stack.isEmpty() || !(stack.getItem() instanceof IHubPlugin plugin)) {
                continue;
            }
            if (plugin.getCapability() != HubCapabilitys.CHANNEL_CAPABILITY) {
                continue;
            }
            HubCapabilitys.CHANNEL_CAPABILITY.saveData(channelInfo, stack);
            break;
        }
    }

    private File getSaveFile() {
        return new File(NetworkManager.getSaveFile(), "HubChannels.dat");
    }

    private List<ChannelDataSnapshot> snapshotChannels() {
        List<ChannelDataSnapshot> snapshot = new ObjectArrayList<>(channels.size());
        for (HubChannel channel : channels.values()) {
            Map<UUID, ChargingPreference> chargingCopy = new Object2ObjectOpenHashMap<>();
            for (var entry : channel.getChargingPreferences().entrySet()) {
                chargingCopy.put(entry.getKey(), new ChargingPreference(entry.getValue().toByte()));
            }
            snapshot.add(new ChannelDataSnapshot(
                channel.getChannelId(),
                channel.getName(),
                channel.getPermissionMode(),
                channel.getOwner(),
                new Object2ReferenceOpenHashMap<>(channel.getExplicitPermissions()),
                chargingCopy
            ));
        }
        return snapshot;
    }

    //? if <1.20 {
    private void loadFromFile() {
        File saveFile = new File(NetworkManager.getSaveFile(), "HubChannels.dat");
        if (!saveFile.exists()) {
            return;
        }

        try {
            NBTTagCompound nbt = CompressedStreamTools.read(saveFile);
            if (nbt == null) {
                return;
            }

            channels.clear();
            NBTTagList channelList = nbt.getTagList("channels", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < channelList.tagCount(); i++) {
                NBTTagCompound channelNbt = channelList.getCompoundTagAt(i);
                if (!channelNbt.hasKey("channelId") || !channelNbt.hasKey("name")) {
                    continue;
                }
                try {
                    UUID channelId = UUID.fromString(channelNbt.getString("channelId"));
                    String name = channelNbt.getString("name");
                    PermissionMode permissionMode = PermissionMode.fromId(channelNbt.getInteger("permissionMode"));
                    UUID owner = null;
                    if (channelNbt.hasKey("ownerUUID")) {
                        owner = UUID.fromString(channelNbt.getString("ownerUUID"));
                    }
                    HubChannel channel = new HubChannel(channelId, name, owner, permissionMode, Collections.emptyMap());
                    if (channelNbt.hasKey("permissions", Constants.NBT.TAG_LIST)) {
                        NBTTagList permissionList = channelNbt.getTagList("permissions", Constants.NBT.TAG_COMPOUND);
                        for (int j = 0; j < permissionList.tagCount(); j++) {
                            NBTTagCompound permissionNbt = permissionList.getCompoundTagAt(j);
                            if (!permissionNbt.hasKey("playerUUID")) {
                                continue;
                            }
                            UUID playerId = UUID.fromString(permissionNbt.getString("playerUUID"));
                            HubPermissionLevel permission = HubPermissionLevel.fromId(permissionNbt.getInteger("permission"));
                            channel.setExplicitPermission(playerId, permission);
                        }
                    }
                    if (channelNbt.hasKey("chargingPreferences", Constants.NBT.TAG_LIST)) {
                        NBTTagList chargingList = channelNbt.getTagList("chargingPreferences", Constants.NBT.TAG_COMPOUND);
                        for (int j = 0; j < chargingList.tagCount(); j++) {
                            NBTTagCompound chargingNbt = chargingList.getCompoundTagAt(j);
                            if (!chargingNbt.hasKey("playerUUID")) {
                                continue;
                            }
                            UUID playerId = UUID.fromString(chargingNbt.getString("playerUUID"));
                            ChargingPreference pref = new ChargingPreference(chargingNbt.getByte("prefs"));
                            channel.setChargingPreference(playerId, pref);
                        }
                    }
                    channels.put(channelId, channel);
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
    }

    private boolean saveToFile() {
        if (!dirty) {
            return true;
        }

        File saveFile = getSaveFile();
        List<ChannelDataSnapshot> snapshot = snapshotChannels();
        if (snapshot.isEmpty()) {
            if (NetworkManager.deleteFile(saveFile)) {
                dirty = false;
                return true;
            }
            return false;
        }

        if (writeSnapshot(saveFile, snapshot)) {
            dirty = false;
            return true;
        }
        return false;
    }
    //?} else {
    /*private void loadFromFile() {
        File saveFile = new File(NetworkManager.getSaveFile(), "HubChannels.dat");
        if (!saveFile.exists()) {
            return;
        }

        try {
            CompoundTag nbt = NetworkManager.readCompressedNbt(saveFile);
            if (nbt == null) {
                return;
            }

            channels.clear();
            ListTag channelList = nbt.getList("channels", Tag.TAG_COMPOUND);
            for (int i = 0; i < channelList.size(); i++) {
                CompoundTag channelNbt = channelList.getCompound(i);
                if (!channelNbt.contains("channelId") || !channelNbt.contains("name")) {
                    continue;
                }
                try {
                    UUID channelId = UUID.fromString(channelNbt.getString("channelId"));
                    String name = channelNbt.getString("name");
                    PermissionMode permissionMode = PermissionMode.fromId(channelNbt.getInt("permissionMode"));
                    UUID owner = null;
                    if (channelNbt.contains("ownerUUID")) {
                        owner = UUID.fromString(channelNbt.getString("ownerUUID"));
                    }
                    HubChannel channel = new HubChannel(channelId, name, owner, permissionMode, Collections.emptyMap());
                    if (channelNbt.contains("permissions", Tag.TAG_LIST)) {
                        ListTag permissionList = channelNbt.getList("permissions", Tag.TAG_COMPOUND);
                        for (int j = 0; j < permissionList.size(); j++) {
                            CompoundTag permissionNbt = permissionList.getCompound(j);
                            if (!permissionNbt.contains("playerUUID")) {
                                continue;
                            }
                            UUID playerId = UUID.fromString(permissionNbt.getString("playerUUID"));
                            HubPermissionLevel permission = HubPermissionLevel.fromId(permissionNbt.getInt("permission"));
                            channel.setExplicitPermission(playerId, permission);
                        }
                    }
                    if (channelNbt.contains("chargingPreferences", Tag.TAG_LIST)) {
                        ListTag chargingList = channelNbt.getList("chargingPreferences", Tag.TAG_COMPOUND);
                        for (int j = 0; j < chargingList.size(); j++) {
                            CompoundTag chargingNbt = chargingList.getCompound(j);
                            if (!chargingNbt.contains("playerUUID")) {
                                continue;
                            }
                            UUID playerId = UUID.fromString(chargingNbt.getString("playerUUID"));
                            ChargingPreference pref = new ChargingPreference(chargingNbt.getByte("prefs"));
                            channel.setChargingPreference(playerId, pref);
                        }
                    }
                    channels.put(channelId, channel);
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
    }

    private boolean saveToFile() {
        if (!dirty) {
            return true;
        }

        File saveFile = getSaveFile();
        List<ChannelDataSnapshot> snapshot = snapshotChannels();
        if (snapshot.isEmpty()) {
            if (NetworkManager.deleteFile(saveFile)) {
                dirty = false;
                return true;
            }
            return false;
        }

        if (writeSnapshot(saveFile, snapshot)) {
            dirty = false;
            return true;
        }
        return false;
    }

    private boolean writeSnapshot(File saveFile, List<ChannelDataSnapshot> snapshot) {
        CompoundTag nbt = new CompoundTag();
        ListTag channelList = new ListTag();
        for (ChannelDataSnapshot channel : snapshot) {
            CompoundTag channelNbt = new CompoundTag();
            channelNbt.putString("channelId", channel.channelId.toString());
            channelNbt.putString("name", channel.name);
            channelNbt.putInt("permissionMode", channel.permissionMode.getId());
            if (channel.owner != null) {
                channelNbt.putString("ownerUUID", channel.owner.toString());
            }

            ListTag permissionList = new ListTag();
            for (var entry : channel.explicitPermissions.entrySet()) {
                CompoundTag permissionNbt = new CompoundTag();
                permissionNbt.putString("playerUUID", entry.getKey().toString());
                permissionNbt.putInt("permission", entry.getValue().getId());
                permissionList.add(permissionNbt);
            }
            channelNbt.put("permissions", permissionList);

            ListTag chargingList = new ListTag();
            for (var entry : channel.chargingPreferences.entrySet()) {
                CompoundTag chargingNbt = new CompoundTag();
                chargingNbt.putString("playerUUID", entry.getKey().toString());
                chargingNbt.putByte("prefs", entry.getValue().toByte());
                chargingList.add(chargingNbt);
            }
            channelNbt.put("chargingPreferences", chargingList);

            channelList.add(channelNbt);
        }
        nbt.put("channels", channelList);

        try {
            NetworkManager.writeCompressedNbt(nbt, saveFile);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }
    *///?}

    //? if <1.20 {
    private boolean writeSnapshot(File saveFile, List<ChannelDataSnapshot> snapshot) {
        NBTTagCompound nbt = new NBTTagCompound();
        NBTTagList channelList = new NBTTagList();
        for (ChannelDataSnapshot channel : snapshot) {
            NBTTagCompound channelNbt = new NBTTagCompound();
            channelNbt.setString("channelId", channel.channelId.toString());
            channelNbt.setString("name", channel.name);
            channelNbt.setInteger("permissionMode", channel.permissionMode.getId());
            if (channel.owner != null) {
                channelNbt.setString("ownerUUID", channel.owner.toString());
            }

            NBTTagList permissionList = new NBTTagList();
            for (var entry : channel.explicitPermissions.entrySet()) {
                NBTTagCompound permissionNbt = new NBTTagCompound();
                permissionNbt.setString("playerUUID", entry.getKey().toString());
                permissionNbt.setInteger("permission", entry.getValue().getId());
                permissionList.appendTag(permissionNbt);
            }
            channelNbt.setTag("permissions", permissionList);

            NBTTagList chargingList = new NBTTagList();
            for (var entry : channel.chargingPreferences.entrySet()) {
                NBTTagCompound chargingNbt = new NBTTagCompound();
                chargingNbt.setString("playerUUID", entry.getKey().toString());
                chargingNbt.setByte("prefs", entry.getValue().toByte());
                chargingList.appendTag(chargingNbt);
            }
            channelNbt.setTag("chargingPreferences", chargingList);

            channelList.appendTag(channelNbt);
        }
        nbt.setTag("channels", channelList);

        return NetworkManager.tryWriteCompressedNbt(nbt, saveFile, "hub channel save");
    }
    //?}

    //? if <1.20
    @Desugar
    private record ChannelDataSnapshot(UUID channelId, String name, PermissionMode permissionMode, @Nullable UUID owner,
                                       Map<UUID, HubPermissionLevel> explicitPermissions,
                                       Map<UUID, ChargingPreference> chargingPreferences) {
    }
    //~}
}
