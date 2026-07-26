package com.circulation.circulation_networks.network;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.packets.BindHubChannel;
import com.circulation.circulation_networks.packets.CirculationShielderSyncPacket;
import com.circulation.circulation_networks.packets.ContainerProgressBar;
import com.circulation.circulation_networks.packets.CreateHubChannel;
import com.circulation.circulation_networks.packets.DeleteHubChannel;
import com.circulation.circulation_networks.packets.HubPluginSyncRequest;
import com.circulation.circulation_networks.packets.MachinePriorityPackets;
import com.circulation.circulation_networks.packets.NodeHudRequest;
import com.circulation.circulation_networks.packets.ToggleItemFunctionMessage;
import com.circulation.circulation_networks.packets.UpdateHubChannelPermission;
import com.circulation.circulation_networks.packets.UpdateHubChannelSettings;
import com.circulation.circulation_networks.packets.UpdateItemModeMessage;
import com.circulation.circulation_networks.packets.UpdateNodeCustomName;
import com.circulation.circulation_networks.packets.UpdatePlayerChargingMode;
import com.circulation.circulation_networks.utils.Packet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class CFNNetwork {

    private static final String PROTOCOL_VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(CirculationFlowNetworks.MOD_ID, "main"),
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    );
    private static boolean registered;
    private static int nextMessageId;

    private CFNNetwork() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        nextMessageId = 0;

        registerMessage(ContainerProgressBar.class, NetworkDirection.PLAY_TO_SERVER);
        registerMessage(UpdateItemModeMessage.class, NetworkDirection.PLAY_TO_SERVER);
        registerMessage(ToggleItemFunctionMessage.class, NetworkDirection.PLAY_TO_SERVER);
        registerMessage(BindHubChannel.class, NetworkDirection.PLAY_TO_SERVER);
        registerMessage(CirculationShielderSyncPacket.class, NetworkDirection.PLAY_TO_SERVER);
        registerMessage(CreateHubChannel.class, NetworkDirection.PLAY_TO_SERVER);
        registerMessage(DeleteHubChannel.class, NetworkDirection.PLAY_TO_SERVER);
        registerMessage(UpdateHubChannelPermission.class, NetworkDirection.PLAY_TO_SERVER);
        registerMessage(UpdateHubChannelSettings.class, NetworkDirection.PLAY_TO_SERVER);
        registerMessage(UpdateNodeCustomName.class, NetworkDirection.PLAY_TO_SERVER);
        registerMessage(UpdatePlayerChargingMode.class, NetworkDirection.PLAY_TO_SERVER);
        registerMessage(NodeHudRequest.class, NetworkDirection.PLAY_TO_SERVER);
        registerMessage(HubPluginSyncRequest.class, NetworkDirection.PLAY_TO_SERVER);
        registerMessage(new MachinePriorityPackets.Submit(), NetworkDirection.PLAY_TO_SERVER);

        for (var packetEntry : CFNNetworkPackets.playToClientPacketEntries()) {
            registerPlayToClient(packetEntry);
        }
    }

    public static <T extends Packet<T>> void sendToPlayer(T packet, ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static <T extends Packet<T>> void sendToServer(T packet) {
        CHANNEL.sendToServer(packet);
    }

    public static <T extends Packet<T>> void registerMessage(Class<T> packetClass, NetworkDirection direction) {
        registerMessage(createPacket(packetClass), direction);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerPlayToClient(Object packetEntry) {
        if (packetEntry instanceof Class<?> packetClass) {
            registerMessage((Class) packetClass, NetworkDirection.PLAY_TO_CLIENT);
        } else if (packetEntry instanceof Packet<?> packet) {
            registerMessage((Packet) packet, NetworkDirection.PLAY_TO_CLIENT);
        } else {
            throw new IllegalArgumentException("Unsupported packet entry: " + packetEntry);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends Packet<T>> void registerMessage(T packet, NetworkDirection direction) {
        Class<T> packetClass = (Class<T>) packet.getClass();
        CHANNEL.messageBuilder(packetClass, nextMessageId++, direction)
               .encoder(Packet::encode)
               .decoder(packet::decode)
               .consumerMainThread(packet::handle)
               .add();
    }

    private static <T extends Packet<T>> T createPacket(Class<T> packetClass) {
        try {
            return packetClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to create packet prototype for " + packetClass.getName(), e);
        }
    }
}
