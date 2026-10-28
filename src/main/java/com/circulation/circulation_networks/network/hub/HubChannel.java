package com.circulation.circulation_networks.network.hub;

import com.circulation.circulation_networks.api.IGrid;
import com.circulation.circulation_networks.api.hub.ChargingPreference;
import com.circulation.circulation_networks.api.hub.HubPermissionLevel;
import com.circulation.circulation_networks.api.hub.IHubChannel;
import com.circulation.circulation_networks.api.hub.PermissionMode;
import com.circulation.circulation_networks.utils.HubTeamServices;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public class HubChannel implements IHubChannel {

    private final UUID channelId;
    private final ReferenceSet<IGrid> grids = new ReferenceOpenHashSet<>();
    private final Object2ObjectMap<UUID, HubPermissionLevel> explicitPermissions = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<UUID, ChargingPreference> chargingPreferences = new Object2ObjectOpenHashMap<>();
    private String name;
    private PermissionMode permissionMode;
    @Nullable
    private UUID owner;

    public HubChannel(UUID channelId, String name, @Nullable UUID owner, PermissionMode permissionMode, Map<UUID, HubPermissionLevel> explicitPermissions) {
        this.channelId = channelId;
        this.name = name;
        this.owner = owner;
        this.permissionMode = permissionMode;
        this.explicitPermissions.putAll(explicitPermissions);
    }

    public UUID getChannelId() {
        return channelId;
    }

    public ReferenceSet<IGrid> getGrids() {
        return grids;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public PermissionMode getPermissionMode() {
        return permissionMode;
    }

    public void setPermissionMode(PermissionMode permissionMode) {
        this.permissionMode = permissionMode;
    }

    @Override
    public @Nullable UUID getOwner() {
        return owner;
    }

    @Override
    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
    }

    @Override
    public @Nullable HubPermissionLevel getExplicitPermission(UUID playerId) {
        return explicitPermissions.get(playerId);
    }

    @Override
    public Map<UUID, HubPermissionLevel> getExplicitPermissions() {
        return Collections.unmodifiableMap(explicitPermissions);
    }

    @Override
    public void setExplicitPermission(UUID playerId, HubPermissionLevel permissionLevel) {
        explicitPermissions.put(playerId, permissionLevel);
    }

    @Override
    public void removeExplicitPermission(UUID playerId) {
        explicitPermissions.remove(playerId);
    }

    public void replaceExplicitPermissions(Map<UUID, HubPermissionLevel> permissions) {
        explicitPermissions.clear();
        explicitPermissions.putAll(permissions);
    }

    @Override
    public HubPermissionLevel getPermissionLevel(UUID playerId) {
        if (owner != null && owner.equals(playerId)) {
            return HubPermissionLevel.OWNER;
        }

        HubPermissionLevel explicitPermission = explicitPermissions.get(playerId);
        if (explicitPermission != null) {
            return explicitPermission;
        }

        return switch (permissionMode) {
            case PUBLIC -> HubPermissionLevel.MEMBER;
            case TEAM -> owner != null
                && HubTeamServices.isLoaded()
                && HubTeamServices.arePlayersInSameTeam(owner, playerId)
                ? HubPermissionLevel.MEMBER
                : HubPermissionLevel.NONE;
            default -> HubPermissionLevel.NONE;
        };
    }

    @Override
    public boolean canEditPermissions(UUID playerId) {
        return getPermissionLevel(playerId).canEditPermissions();
    }

    public void addGrid(IGrid grid) {
        grids.add(grid);
    }

    public void removeGrid(IGrid grid) {
        grids.remove(grid);
    }

    public ChargingPreference getChargingPreference(UUID playerId) {
        return chargingPreferences.computeIfAbsent(playerId, k -> ChargingPreference.defaultAll());
    }

    public Map<UUID, ChargingPreference> getChargingPreferences() {
        return Collections.unmodifiableMap(chargingPreferences);
    }

    public void setChargingPreference(UUID playerId, ChargingPreference preference) {
        chargingPreferences.put(playerId, preference);
    }
}
