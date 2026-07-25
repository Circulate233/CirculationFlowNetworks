package com.circulation.circulation_networks.container;

import com.circulation.circulation_networks.api.hub.ChannelSnapshotList;
import com.circulation.circulation_networks.api.hub.ChargingPreference;
import com.circulation.circulation_networks.api.hub.NodeSnapshotList;
import com.circulation.circulation_networks.api.hub.PermissionSnapshotList;
import com.circulation.circulation_networks.api.node.IHubNode;
import com.circulation.circulation_networks.manager.EnergyMachineManager;
import com.circulation.circulation_networks.manager.HubChannelManager;
import com.circulation.circulation_networks.network.hub.HubCapabilitys;
import com.circulation.circulation_networks.utils.GuiSync;
import com.circulation.circulation_networks.utils.HubPlatformServices;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

import static com.circulation.circulation_networks.network.nodes.HubNode.EMPTY;

public class ContainerHub extends CFNBaseContainer {

    private static final long ENERGY_REFRESH_INTERVAL = 10L;
    private static final long LATENCY_REFRESH_INTERVAL = 20L;
    private static final byte CHANNEL_CAPABILITY_FLAG = 0x1;
    private static final byte CHANNEL_MANAGEMENT_OVERRIDE_FLAG = 0x2;
    private static final byte CAN_EDIT_PLUGINS_FLAG = 0x4;
    private static final byte[] EMPTY_CHANNEL_SNAPSHOT = ChannelSnapshotList.EMPTY.toBytes();
    private static final byte[] EMPTY_PERMISSION_SNAPSHOT = PermissionSnapshotList.EMPTY.toBytes();
    private static final byte[] EMPTY_NODE_SNAPSHOT = NodeSnapshotList.EMPTY.toBytes();

    public final ComponentSlotLayout playerInvLayout;
    public final ComponentSlotLayout[] slots = new ComponentSlotLayout[5];
    public final IHubNode node;
    private final UUID playerId;
    @GuiSync(0)
    public String input;
    @GuiSync(1)
    public String output;
    @GuiSync(9)
    public String interactionTimeMicros;
    public UUID channelId = EMPTY;
    @GuiSync(2)
    public String channelIdString = EMPTY.toString();
    @GuiSync(3)
    public String channelName = "";
    @GuiSync(4)
    public byte chargingModeByte = (byte) 0b111111;
    @GuiSync(5)
    public byte[] availableChannelsSnapshot;
    @GuiSync(6)
    public byte[] onlinePlayersSnapshot;
    @GuiSync(7)
    public byte[] nodesSnapshot;
    @GuiSync(8)
    public byte channelStateFlags;
    public ChannelSnapshotList availableChannels = ChannelSnapshotList.EMPTY;
    public PermissionSnapshotList onlinePlayers = PermissionSnapshotList.EMPTY;
    public NodeSnapshotList nodes = NodeSnapshotList.EMPTY;
    public ChargingPreference chargingMode = ChargingPreference.defaultAll();
    private long availableChannelsVersion = Long.MIN_VALUE;
    private long onlinePlayersChannelVersion = Long.MIN_VALUE;
    private long onlinePlayersPlatformVersion = Long.MIN_VALUE;
    private long nodeSnapshotVersion = Long.MIN_VALUE;
    private UUID nodeGridId;

    public ContainerHub(MenuType<?> menuType, int containerId, Player player, IHubNode node) {
        super(menuType, containerId, player);
        this.node = node;
        this.playerId = player.getUUID();

        playerInvLayout = ComponentSlotLayout.playerInventory(player.getInventory()).build(this);
        for (int i = 0; i < 5; i++) {
            slots[i] = new ComponentSlotLayout().addPrebuilt(new HubPluginSlot(this::canEditPlugins, node.getPlugins(), i, 0, 0)).build(this);
        }

        input = "0";
        output = "0";
        interactionTimeMicros = "0";
        syncChannelState();
        availableChannels = ChannelSnapshotList.EMPTY;
        onlinePlayers = PermissionSnapshotList.EMPTY;
        availableChannelsSnapshot = EMPTY_CHANNEL_SNAPSHOT.clone();
        onlinePlayersSnapshot = EMPTY_PERMISSION_SNAPSHOT.clone();

        if (!node.getWorld().isClientSide()) {
            nodes = NodeSnapshotList.fromGrid(node.getGrid());
            chargingMode = node.getChargingPreference(playerId);
            nodesSnapshot = nodes.toBytes();
            refreshEnergyStats(true, true);
            syncChannelState();
            refreshSnapshots(true);
        }
    }

    @Override
    public boolean stillValid(@NotNull Player playerIn) {
        return playerIn.distanceToSqr(node.getPos().getX() + 0.5D, node.getPos().getY() + 0.5D, node.getPos().getZ() + 0.5D) <= 128;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (node.getWorld().isClientSide()) return;
        boolean channelChanged = syncChannelState();
        chargingMode = node.getChargingPreference(playerId);
        chargingModeByte = chargingMode.toByte();
        refreshSnapshots(channelChanged);
        long gameTime = node.getWorld().getGameTime();
        boolean refreshEnergy = gameTime % ENERGY_REFRESH_INTERVAL == 0;
        boolean refreshLatency = gameTime % LATENCY_REFRESH_INTERVAL == 0;
        if (!refreshEnergy && !refreshLatency) return;
        refreshEnergyStats(refreshEnergy, refreshLatency);
    }

    private void refreshSnapshots(boolean force) {
        long channelSnapshotVersion = HubChannelManager.INSTANCE.getSnapshotVersion();
        if (force || availableChannelsVersion != channelSnapshotVersion) {
            availableChannels = HubChannelManager.INSTANCE.getAccessibleChannelSnapshots(playerId, channelId);
            availableChannelsSnapshot = availableChannels.toBytes();
            availableChannelsVersion = channelSnapshotVersion;
        }

        if (channelId.equals(EMPTY)) {
            if (force || onlinePlayers != PermissionSnapshotList.EMPTY || onlinePlayersSnapshot != EMPTY_PERMISSION_SNAPSHOT) {
                onlinePlayers = PermissionSnapshotList.EMPTY;
                onlinePlayersSnapshot = EMPTY_PERMISSION_SNAPSHOT.clone();
            }
            onlinePlayersChannelVersion = channelSnapshotVersion;
            onlinePlayersPlatformVersion = HubPlatformServices.INSTANCE.getOnlinePlayersVersion();
        } else {
            long platformVersion = HubPlatformServices.INSTANCE.getOnlinePlayersVersion();
            if (force || onlinePlayersChannelVersion != channelSnapshotVersion || onlinePlayersPlatformVersion != platformVersion) {
                onlinePlayers = HubChannelManager.INSTANCE.getOnlinePlayerSnapshots(channelId);
                onlinePlayersSnapshot = onlinePlayers.toBytes();
                onlinePlayersChannelVersion = channelSnapshotVersion;
                onlinePlayersPlatformVersion = platformVersion;
            }
        }

        var grid = node.getGrid();
        UUID currentGridId = grid != null ? grid.getId() : null;
        long currentNodeSnapshotVersion = grid != null ? grid.getSnapshotVersion() : 0L;
        if (force || nodeSnapshotVersion != currentNodeSnapshotVersion || !Objects.equals(nodeGridId, currentGridId)) {
            nodes = NodeSnapshotList.fromGrid(grid);
            nodesSnapshot = nodes.toBytes();
            nodeSnapshotVersion = currentNodeSnapshotVersion;
            nodeGridId = currentGridId;
        }
    }

    private boolean syncChannelState() {
        UUID currentChannelId = node.getChannelId();
        if (currentChannelId == null) {
            currentChannelId = EMPTY;
        }
        String currentChannelName = node.getChannelName();
        if (currentChannelName == null) {
            currentChannelName = "";
        }
        byte currentChannelStateFlags = getChannelStateFlags();
        boolean changed = !Objects.equals(channelId, currentChannelId)
            || !Objects.equals(channelName, currentChannelName)
            || channelStateFlags != currentChannelStateFlags;
        channelId = currentChannelId;
        channelIdString = channelId.toString();
        channelName = currentChannelName;
        channelStateFlags = currentChannelStateFlags;
        return changed;
    }

    private void refreshEnergyStats(boolean refreshEnergy, boolean refreshLatency) {
        var grid = node.getGrid();
        var energy = grid == null ? null : grid.getInteraction();
        if (energy == null) {
            if (refreshEnergy) {
                input = "0";
                output = "0";
            }
            if (refreshLatency) {
                interactionTimeMicros = "0";
            }
            return;
        }
        if (refreshEnergy) {
            input = energy.getInputString();
            output = energy.getOutputString();
        }
        if (refreshLatency) {
            interactionTimeMicros = energy.getInteractionTimeMicrosString();
        }
    }

    private byte getChannelStateFlags() {
        byte flags = 0;
        if (node.hasPluginCapability(HubCapabilitys.CHANNEL_CAPABILITY)) {
            flags |= CHANNEL_CAPABILITY_FLAG;
        }
        if (HubPlatformServices.INSTANCE.hasChannelManagementOverride(player)) {
            flags |= CHANNEL_MANAGEMENT_OVERRIDE_FLAG;
        }
        if ((flags & CHANNEL_MANAGEMENT_OVERRIDE_FLAG) != 0 || channelId.equals(EMPTY) || node.canEditPermissions(playerId)) {
            flags |= CAN_EDIT_PLUGINS_FLAG;
        }
        return flags;
    }

    @Override
    public void onUpdate(String field, Object oldValue, Object newValue) {
        if ("channelIdString".equals(field)) {
            channelId = UUID.fromString(newValue.toString());
        } else if ("chargingModeByte".equals(field)) {
            chargingMode.setPrefs((byte) newValue);
        } else if ("availableChannelsSnapshot".equals(field)) {
            availableChannels = ChannelSnapshotList.fromBytes((byte[]) newValue);
        } else if ("onlinePlayersSnapshot".equals(field)) {
            onlinePlayers = PermissionSnapshotList.fromBytes((byte[]) newValue);
        } else if ("nodesSnapshot".equals(field)) {
            nodes = NodeSnapshotList.fromBytes((byte[]) newValue);
        }
    }

    public boolean hasChannel() {
        return !EMPTY.equals(channelId);
    }

    public boolean hasChannelCapability() {
        return node.hasPluginCapability(HubCapabilitys.CHANNEL_CAPABILITY);
    }

    public boolean hasChannelManagementOverride() {
        return (channelStateFlags & CHANNEL_MANAGEMENT_OVERRIDE_FLAG) != 0;
    }

    public boolean canEditPlugins() {
        return (channelStateFlags & CAN_EDIT_PLUGINS_FLAG) != 0;
    }

    public boolean canOpenChannelList() {
        return hasChannelCapability();
    }

    public boolean canOpenChannelDetails() {
        return hasChannelCapability() && hasChannel();
    }
}
