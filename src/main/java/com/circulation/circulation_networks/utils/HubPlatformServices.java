package com.circulation.circulation_networks.utils;

import it.unimi.dsi.fastutil.objects.ObjectLists;

import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.UUID;

public abstract class HubPlatformServices {

    public static HubPlatformServices INSTANCE = new HubPlatformServices() {
        @Override
        public List<PlayerIdentity> getOnlinePlayers() {
            return ObjectLists.emptyList();
        }
    };
    private long onlinePlayersVersion = 1L;

    public abstract List<PlayerIdentity> getOnlinePlayers();

    public boolean hasChannelManagementOverride(
        Player player
    ) {
        return false;
    }

    public long getOnlinePlayersVersion() {
        return onlinePlayersVersion;
    }

    public void markOnlinePlayersDirty() {
        onlinePlayersVersion++;
    }

    public record PlayerIdentity(UUID id, String name) {

    }
}
