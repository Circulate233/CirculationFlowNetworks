package com.circulation.circulation_networks.network;

import com.circulation.circulation_networks.packets.BindHubChannel;
import com.circulation.circulation_networks.packets.CirculationShielderSyncPacket;
import com.circulation.circulation_networks.packets.ConfigOverrideRendering;
import com.circulation.circulation_networks.packets.ConfiguratorInteractionReport;
import com.circulation.circulation_networks.packets.ContainerProgressBar;
import com.circulation.circulation_networks.packets.ContainerValueConfig;
import com.circulation.circulation_networks.packets.CreateHubChannel;
import com.circulation.circulation_networks.packets.DeleteHubChannel;
import com.circulation.circulation_networks.packets.EnergyWarningRendering;
import com.circulation.circulation_networks.packets.HubPluginSyncData;
import com.circulation.circulation_networks.packets.HubPluginSyncRequest;
import com.circulation.circulation_networks.packets.MachinePriorityPackets;
import com.circulation.circulation_networks.packets.NodeHudData;
import com.circulation.circulation_networks.packets.NodeHudRequest;
import com.circulation.circulation_networks.packets.NodeNetworkRendering;
import com.circulation.circulation_networks.packets.PocketNodeRendering;
import com.circulation.circulation_networks.packets.RenderingClear;
import com.circulation.circulation_networks.packets.SpoceRendering;
import com.circulation.circulation_networks.packets.ToggleItemFunctionMessage;
import com.circulation.circulation_networks.packets.UpdateHubChannelPermission;
import com.circulation.circulation_networks.packets.UpdateHubChannelSettings;
import com.circulation.circulation_networks.packets.UpdateItemModeMessage;
import com.circulation.circulation_networks.packets.UpdateNodeCustomName;
import com.circulation.circulation_networks.packets.UpdatePlayerChargingMode;
import com.circulation.circulation_networks.utils.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class CFNNetwork {

    private CFNNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registerPlayBidirectional(registrar, ContainerProgressBar.class);
        registerPlayToServer(registrar, UpdateItemModeMessage.class);
        registerPlayToServer(registrar, ToggleItemFunctionMessage.class);
        registerPlayToServer(registrar, BindHubChannel.class);
        registerPlayToServer(registrar, CirculationShielderSyncPacket.class);
        registerPlayToServer(registrar, CreateHubChannel.class);
        registerPlayToServer(registrar, DeleteHubChannel.class);
        registerPlayToServer(registrar, UpdateHubChannelPermission.class);
        registerPlayToServer(registrar, UpdateHubChannelSettings.class);
        registerPlayToServer(registrar, UpdateNodeCustomName.class);
        registerPlayToServer(registrar, UpdatePlayerChargingMode.class);
        registerPlayToServer(registrar, NodeHudRequest.class);
        registerPlayToServer(registrar, HubPluginSyncRequest.class);
        registerPlayToServer(registrar, new MachinePriorityPackets.Submit());

        registerPlayToClient(registrar, SpoceRendering.class);
        registerPlayToClient(registrar, NodeNetworkRendering.class);
        registerPlayToClient(registrar, EnergyWarningRendering.class);
        registerPlayToClient(registrar, ConfigOverrideRendering.class);
        registerPlayToClient(registrar, ConfiguratorInteractionReport.class);
        registerPlayToClient(registrar, ContainerValueConfig.class);
        registerPlayToClient(registrar, PocketNodeRendering.class);
        registerPlayToClient(registrar, RenderingClear.INSTANCE);
        registerPlayToClient(registrar, NodeHudData.class);
        registerPlayToClient(registrar, HubPluginSyncData.class);
    }

    public static <T extends Packet<T>> void sendToPlayer(T packet, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static <T extends Packet<T>> void sendToServer(T packet) {
        PacketDistributor.sendToServer(packet);
    }

    public static <T extends Packet<T>> void registerPlayToClient(PayloadRegistrar registrar, Class<T> packetClass) {
        registerPlayToClient(registrar, createPacket(packetClass));
    }

    public static <T extends Packet<T>> void registerPlayToClient(PayloadRegistrar registrar, T packet) {
        registrar.playToClient(payloadType(packet), packet.streamCodec(), packet::handle);
    }

    public static <T extends Packet<T>> void registerPlayBidirectional(PayloadRegistrar registrar, Class<T> packetClass) {
        registerPlayBidirectional(registrar, createPacket(packetClass));
    }

    public static <T extends Packet<T>> void registerPlayBidirectional(PayloadRegistrar registrar, T packet) {
        registrar.playBidirectional(payloadType(packet), packet.streamCodec(), packet::handle);
    }

    public static <T extends Packet<T>> void registerPlayToServer(PayloadRegistrar registrar, Class<T> packetClass) {
        registerPlayToServer(registrar, createPacket(packetClass));
    }

    public static <T extends Packet<T>> void registerPlayToServer(PayloadRegistrar registrar, T packet) {
        registrar.playToServer(payloadType(packet), packet.streamCodec(), packet::handle);
    }

    private static <T extends Packet<T>> T createPacket(Class<T> packetClass) {
        try {
            return packetClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to create packet prototype for " + packetClass.getName(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Packet<T>> CustomPacketPayload.Type<T> payloadType(T packet) {
        return (CustomPacketPayload.Type<T>) packet.type();
    }
}
