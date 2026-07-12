// ...existing code...
package com.circulation.circulation_networks.network.nodes;

import com.circulation.circulation_networks.api.hub.ChargingDefinition;
import com.circulation.circulation_networks.api.hub.ChargingPreference;
import com.circulation.circulation_networks.api.hub.HubPermissionLevel;
import com.circulation.circulation_networks.api.hub.PermissionMode;
import com.circulation.circulation_networks.api.node.IHubNode;
import com.circulation.circulation_networks.api.node.NodeContext;
import com.circulation.circulation_networks.manager.HubChannelManager;
import com.circulation.circulation_networks.network.hub.HubChannel;
import com.circulation.circulation_networks.network.hub.HubPluginCapability;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import com.circulation.circulation_networks.registry.NodeTypes;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import static com.circulation.circulation_networks.utils.SideCompat.isServerWorld;
//~ mc_imports
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
//? if <1.20 {
import net.minecraftforge.common.util.Constants;
//?} else {
/*import net.minecraft.nbt.Tag;
 *///?}
//? if <1.21 {
import net.minecraftforge.items.IItemHandler;
//?} else {
/*import net.neoforged.neoforge.items.IItemHandler;
 *///?}
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public final class HubNode extends Node implements IHubNode {

    @Override
    public Map<UUID, ChargingPreference> getPlayerPreferences() {
        return Collections.unmodifiableMap(playerPreferences);
    }

    public static final UUID EMPTY = new UUID(0, 0);
    private final double energyScope;
    private final double energyScopeSq;
    private final double chargingScope;
    private final double chargingScopeSq;

    private final Object2ObjectMap<UUID, ChargingPreference> playerPreferences = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<UUID, HubPermissionLevel> explicitPermissions = new Object2ObjectOpenHashMap<>();
    private final HubMetadata hubData = new HubMetadata();
    private PermissionMode permissionMode = PermissionMode.PUBLIC;
    @Nullable
    private UUID owner;
    @NotNull
    private UUID channelId = EMPTY;
    @NotNull
    private String channelName = "";
    private IItemHandler plugins = EmptyItemHandler.INSTANCE;
    private boolean syncingChannelState;

    private void markGridSnapshotDirty() {
        if (getGrid() != null) {
            getGrid().markSnapshotDirty();
        }
    }

    //~ if >=1.20 'NBTTagCompound' -> 'CompoundTag' {
    public HubNode(NBTTagCompound tag) {
        //~}
        super(NodeTypes.HUB, tag);
        this.energyScope = tag.getDouble("energyScope");
        this.energyScopeSq = energyScope * energyScope;
        this.chargingScope = tag.getDouble("chargingScope");
        this.chargingScopeSq = chargingScope * chargingScope;
        deserializeHubData(tag);
    }

    public HubNode(NodeContext context, double energyScope, double chargingScope, double linkScope) {
        super(NodeTypes.HUB, context, linkScope);
        this.energyScope = energyScope;
        this.energyScopeSq = energyScope * energyScope;
        this.chargingScope = chargingScope;
        this.chargingScopeSq = chargingScope * chargingScope;
    }

    public void bindPlugins(@Nullable IItemHandler plugins) {
        this.plugins = plugins != null ? plugins : EmptyItemHandler.INSTANCE;
    }

    @Override
    public double getEnergyScope() {
        return energyScope;
    }

    @Override
    public double getEnergyScopeSq() {
        return energyScopeSq;
    }

    @Override
    public double getChargingScope() {
        return chargingScope;
    }

    @Override
    public double getChargingScopeSq() {
        return chargingScopeSq;
    }

    @Override
    public PermissionMode getPermissionMode() {
        return permissionMode;
    }

    @Override
    public void setPermissionMode(PermissionMode mode) {
        this.permissionMode = mode;
        markGridSnapshotDirty();
        if (!syncingChannelState && shouldSyncChannelManager()) {
            HubChannelManager.INSTANCE.updateChannelFromHub(this);
        }
    }

    @Override
    public IItemHandler getPlugins() {
        return plugins;
    }

    @Override
    public HubMetadata getHubData() {
        return hubData;
    }

    public void putPluginDataIfAbsent(HubPluginCapability<?> capability, ItemStack stack) {
        if (capability == null || stack.isEmpty() || hubData.hasKey(capability)) {
            return;
        }
        hubData.put(capability, stack);
        markGridSnapshotDirty();
    }

    public void removePluginData(HubPluginCapability<?> capability) {
        hubData.remove(capability);
        markGridSnapshotDirty();
    }

    @Override
    public @NotNull UUID getChannelId() {
        return channelId;
    }

    @Override
    public void setChannelId(@NotNull UUID channelId) {
        this.channelId = channelId != null ? channelId : EMPTY;
        markGridSnapshotDirty();
        if (!syncingChannelState && shouldSyncChannelManager()) {
            HubChannelManager.INSTANCE.bindHub(this);
        }
    }

    @Override
    public @NotNull String getChannelName() {
        return channelName;
    }

    @Override
    public void setChannelName(@NotNull String channelName) {
        this.channelName = channelName != null ? channelName : "";
        markGridSnapshotDirty();
        if (!syncingChannelState && shouldSyncChannelManager()) {
            HubChannelManager.INSTANCE.updateChannelFromHub(this);
        }
    }

    @Override
    public @NotNull ChargingPreference getChargingPreference(UUID playerId) {
        if (shouldSyncChannelManager()) {
            HubChannel channel = HubChannelManager.INSTANCE.getChannel(channelId);
            if (channel != null) {
                return channel.getChargingPreference(playerId);
            }
        }
        return playerPreferences.computeIfAbsent(playerId, k -> ChargingPreference.defaultAll());
    }

    @Override
    public void setChargingPreference(UUID playerId, ChargingPreference preference) {
        if (shouldSyncChannelManager()) {
            HubChannel channel = HubChannelManager.INSTANCE.getChannel(channelId);
            if (channel != null) {
                channel.setChargingPreference(playerId, preference);
            }
        }
        playerPreferences.put(playerId, preference);
        markGridSnapshotDirty();
    }

    @Override
    public boolean getChargingState(UUID playerId, ChargingDefinition chargingDefinition) {
        return getChargingPreference(playerId).getPreference(chargingDefinition);
    }

    @Override
    public void setChargingState(UUID playerId, ChargingDefinition chargingDefinition, boolean value) {
        getChargingPreference(playerId).setPreference(chargingDefinition, value);
    }

    @Override
    public @Nullable UUID getOwner() {
        return owner;
    }

    @Override
    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        markGridSnapshotDirty();
        if (!syncingChannelState && shouldSyncChannelManager()) {
            HubChannelManager.INSTANCE.updateChannelFromHub(this);
        }
    }

    @Override
    public @Nullable HubPermissionLevel getExplicitPermission(UUID playerId) {
        if (shouldSyncChannelManager()) {
            HubChannel channel = HubChannelManager.INSTANCE.getChannel(channelId);
            if (channel != null) return channel.getExplicitPermission(playerId);
        }
        return explicitPermissions.get(playerId);
    }

    @Override
    public Map<UUID, HubPermissionLevel> getExplicitPermissions() {
        if (shouldSyncChannelManager()) {
            HubChannel channel = HubChannelManager.INSTANCE.getChannel(channelId);
            if (channel != null) {
                return channel.getExplicitPermissions();
            }
        }
        return Collections.unmodifiableMap(explicitPermissions);
    }

    @Override
    public void setExplicitPermission(UUID playerId, HubPermissionLevel permissionLevel) {
        explicitPermissions.put(playerId, permissionLevel);
        markGridSnapshotDirty();
        if (!syncingChannelState && shouldSyncChannelManager()) {
            HubChannelManager.INSTANCE.updateChannelFromHub(this);
        }
    }

    @Override
    public void removeExplicitPermission(UUID playerId) {
        explicitPermissions.remove(playerId);
        markGridSnapshotDirty();
        if (!syncingChannelState && shouldSyncChannelManager()) {
            HubChannelManager.INSTANCE.updateChannelFromHub(this);
        }
    }

    @Override
    public HubPermissionLevel getPermissionLevel(UUID playerId) {
        if (shouldSyncChannelManager()) {
            HubChannel channel = HubChannelManager.INSTANCE.getChannel(channelId);
            if (channel != null) {
                return channel.getPermissionLevel(playerId);
            }
        }

        if (owner == null) return HubPermissionLevel.MEMBER;

        return owner.equals(playerId) ? HubPermissionLevel.OWNER : HubPermissionLevel.MEMBER;
    }

    @Override
    public boolean canEditPermissions(UUID playerId) {
        return getPermissionLevel(playerId).canEditPermissions();
    }

    public void syncFromChannel(HubChannel channel) {
        syncingChannelState = true;
        try {
            channelId = channel.getChannelId();
            permissionMode = channel.getPermissionMode();
            owner = channel.getOwner();
            channelName = channel.getName();
            explicitPermissions.clear();
            explicitPermissions.putAll(channel.getExplicitPermissions());
            markGridSnapshotDirty();
        } finally {
            syncingChannelState = false;
        }
    }

    public void clearChannelBinding() {
        syncingChannelState = true;
        try {
            channelId = EMPTY;
            channelName = "";
            permissionMode = PermissionMode.PUBLIC;
            explicitPermissions.clear();
            markGridSnapshotDirty();
        } finally {
            syncingChannelState = false;
        }
    }

    private boolean shouldSyncChannelManager() {
        try {
            return isServerWorld(getWorld());
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    //? if <1.20 {
    @Override
    public NBTTagCompound serialize() {
        var nbt = super.serialize();
        nbt.setDouble("energyScope", energyScope);
        nbt.setDouble("chargingScope", chargingScope);
        serializeHubData(nbt);
        return nbt;
    }

    private void serializeHubData(NBTTagCompound nbt) {
        nbt.setInteger("permissionMode", permissionMode.getId());

        if (owner != null) {
            nbt.setString("ownerUUID", owner.toString());
        }

        var permissionList = new NBTTagList();
        for (var entry : explicitPermissions.entrySet()) {
            var permissionNbt = new NBTTagCompound();
            permissionNbt.setString("playerUUID", entry.getKey().toString());
            permissionNbt.setInteger("permission", entry.getValue().getId());
            permissionList.appendTag(permissionNbt);
        }
        nbt.setTag("hubPermissions", permissionList);

        var prefList = new NBTTagList();
        for (var entry : playerPreferences.entrySet()) {
            var prefNbt = entry.getValue().serialize();
            prefNbt.setString("playerUUID", entry.getKey().toString());
            prefList.appendTag(prefNbt);
        }
        nbt.setTag("chargingPreferences", prefList);
    }

    private void deserializeHubData(NBTTagCompound nbt) {
        permissionMode = PermissionMode.fromId(nbt.getInteger("permissionMode"));

        if (nbt.hasKey("ownerUUID")) {
            try {
                owner = UUID.fromString(nbt.getString("ownerUUID"));
            } catch (IllegalArgumentException ignored) {
                owner = null;
            }
        }

        explicitPermissions.clear();
        if (nbt.hasKey("hubPermissions", Constants.NBT.TAG_LIST)) {
            var permissionList = nbt.getTagList("hubPermissions", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < permissionList.tagCount(); i++) {
                var permissionNbt = permissionList.getCompoundTagAt(i);
                if (!permissionNbt.hasKey("playerUUID")) {
                    continue;
                }
                try {
                    UUID playerId = UUID.fromString(permissionNbt.getString("playerUUID"));
                    HubPermissionLevel permission = HubPermissionLevel.fromId(permissionNbt.getInteger("permission"));
                    explicitPermissions.put(playerId, permission);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        playerPreferences.clear();
        if (nbt.hasKey("chargingPreferences", Constants.NBT.TAG_LIST)) {
            var prefList = nbt.getTagList("chargingPreferences", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < prefList.tagCount(); i++) {
                var prefNbt = prefList.getCompoundTagAt(i);
                if (prefNbt.hasKey("playerUUID")) {
                    try {
                        var playerId = UUID.fromString(prefNbt.getString("playerUUID"));
                        playerPreferences.put(playerId, ChargingPreference.deserialize(prefNbt));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }

        channelId = EMPTY;
        channelName = "";
    }
    //?} else if <1.21 {
    /*@Override
    public CompoundTag serialize() {
        var nbt = super.serialize();
        nbt.putDouble("energyScope", energyScope);
        nbt.putDouble("chargingScope", chargingScope);
        serializeHubData(nbt);
        return nbt;
    }

    private void serializeHubData(CompoundTag nbt) {
        nbt.putInt("permissionMode", permissionMode.getId());

        if (owner != null) {
            nbt.putString("ownerUUID", owner.toString());
        }

        var permissionList = new ListTag();
        for (var entry : explicitPermissions.entrySet()) {
            var permissionNbt = new CompoundTag();
            permissionNbt.putString("playerUUID", entry.getKey().toString());
            permissionNbt.putInt("permission", entry.getValue().getId());
            permissionList.add(permissionNbt);
        }
        nbt.put("hubPermissions", permissionList);

        var prefList = new ListTag();
        for (var entry : playerPreferences.entrySet()) {
            var prefNbt = entry.getValue().serialize();
            prefNbt.putString("playerUUID", entry.getKey().toString());
            prefList.add(prefNbt);
        }
        nbt.put("chargingPreferences", prefList);
    }

    private void deserializeHubData(CompoundTag nbt) {
        permissionMode = PermissionMode.fromId(nbt.getInt("permissionMode"));

        if (nbt.contains("ownerUUID")) {
            try {
                owner = UUID.fromString(nbt.getString("ownerUUID"));
            } catch (IllegalArgumentException ignored) {
                owner = null;
            }
        }

        explicitPermissions.clear();
        if (nbt.contains("hubPermissions", Tag.TAG_LIST)) {
            var permissionList = nbt.getList("hubPermissions", Tag.TAG_COMPOUND);
            for (int i = 0; i < permissionList.size(); i++) {
                var permissionNbt = permissionList.getCompound(i);
                if (!permissionNbt.contains("playerUUID")) {
                    continue;
                }
                try {
                    UUID playerId = UUID.fromString(permissionNbt.getString("playerUUID"));
                    HubPermissionLevel permission = HubPermissionLevel.fromId(permissionNbt.getInt("permission"));
                    explicitPermissions.put(playerId, permission);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        playerPreferences.clear();
        if (nbt.contains("chargingPreferences", Tag.TAG_LIST)) {
            var prefList = nbt.getList("chargingPreferences", Tag.TAG_COMPOUND);
            for (int i = 0; i < prefList.size(); i++) {
                var prefNbt = prefList.getCompound(i);
                if (prefNbt.contains("playerUUID")) {
                    try {
                        var playerId = UUID.fromString(prefNbt.getString("playerUUID"));
                        playerPreferences.put(playerId, ChargingPreference.deserialize(prefNbt));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }

        channelId = EMPTY;
        channelName = "";
    }
    *///?} else {
    /*@Override
    public CompoundTag serialize() {
        var nbt = super.serialize();
        nbt.putDouble("energyScope", energyScope);
        nbt.putDouble("chargingScope", chargingScope);
        serializeHubData(nbt);
        return nbt;
    }

    private void serializeHubData(CompoundTag nbt) {
        nbt.putInt("permissionMode", permissionMode.getId());

        if (owner != null) {
            nbt.putString("ownerUUID", owner.toString());
        }

        var permissionList = new ListTag();
        for (var entry : explicitPermissions.entrySet()) {
            var permissionNbt = new CompoundTag();
            permissionNbt.putString("playerUUID", entry.getKey().toString());
            permissionNbt.putInt("permission", entry.getValue().getId());
            permissionList.add(permissionNbt);
        }
        nbt.put("hubPermissions", permissionList);

        var prefList = new ListTag();
        for (var entry : playerPreferences.entrySet()) {
            var prefNbt = entry.getValue().serialize();
            prefNbt.putString("playerUUID", entry.getKey().toString());
            prefList.add(prefNbt);
        }
        nbt.put("chargingPreferences", prefList);
    }

    private void deserializeHubData(CompoundTag nbt) {
        permissionMode = PermissionMode.fromId(nbt.getInt("permissionMode"));

        if (nbt.contains("ownerUUID")) {
            try {
                owner = UUID.fromString(nbt.getString("ownerUUID"));
            } catch (IllegalArgumentException ignored) {
                owner = null;
            }
        }

        explicitPermissions.clear();
        if (nbt.contains("hubPermissions", Tag.TAG_LIST)) {
            var permissionList = nbt.getList("hubPermissions", Tag.TAG_COMPOUND);
            for (int i = 0; i < permissionList.size(); i++) {
                var permissionNbt = permissionList.getCompound(i);
                if (!permissionNbt.contains("playerUUID")) {
                    continue;
                }
                try {
                    UUID playerId = UUID.fromString(permissionNbt.getString("playerUUID"));
                    HubPermissionLevel permission = HubPermissionLevel.fromId(permissionNbt.getInt("permission"));
                    explicitPermissions.put(playerId, permission);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        playerPreferences.clear();
        if (nbt.contains("chargingPreferences", Tag.TAG_LIST)) {
            var prefList = nbt.getList("chargingPreferences", Tag.TAG_COMPOUND);
            for (int i = 0; i < prefList.size(); i++) {
                var prefNbt = prefList.getCompound(i);
                if (prefNbt.contains("playerUUID")) {
                    try {
                        var playerId = UUID.fromString(prefNbt.getString("playerUUID"));
                        playerPreferences.put(playerId, ChargingPreference.deserialize(prefNbt));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }

        channelId = EMPTY;
        channelName = "";
    }
    *///?}

    public final static class HubMetadata {

        private final Reference2ReferenceMap<HubPluginCapability<?>, Object> capacityMap = new Reference2ReferenceOpenHashMap<>();

        public boolean hasKey(HubPluginCapability<?> capability) {
            return capacityMap.containsKey(capability);
        }

        @SuppressWarnings("unchecked")
        public <T> T get(HubPluginCapability<T> capability) {
            return (T) capacityMap.get(capability);
        }

        void put(HubPluginCapability<?> capability, ItemStack stack) {
            capacityMap.put(capability, capability.newPluginData(stack));
        }

        void remove(HubPluginCapability<?> capability) {
            capacityMap.remove(capability);
        }
    }

    private static final class EmptyItemHandler implements IItemHandler {

        private static final EmptyItemHandler INSTANCE = new EmptyItemHandler();

        @Override
        public int getSlots() {
            return 0;
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 0;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return false;
        }
    }
}
