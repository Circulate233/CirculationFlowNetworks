package com.circulation.circulation_networks.network;

import com.circulation.circulation_networks.packets.ConfigOverrideRendering;
import com.circulation.circulation_networks.packets.ConfiguratorInteractionReport;
import com.circulation.circulation_networks.packets.ContainerProgressBar;
import com.circulation.circulation_networks.packets.ContainerValueConfig;
import com.circulation.circulation_networks.packets.EnergyWarningRendering;
import com.circulation.circulation_networks.packets.HubPluginSyncData;
import com.circulation.circulation_networks.packets.NodeHudData;
import com.circulation.circulation_networks.packets.NodeNetworkRendering;
import com.circulation.circulation_networks.packets.PocketNodeRendering;
import com.circulation.circulation_networks.packets.RenderingClear;
import com.circulation.circulation_networks.packets.SpoceRendering;

import java.util.List;

final class CFNNetworkPackets {

    private CFNNetworkPackets() {
    }

    static List<Object> playToClientPacketEntries() {
        return List.of(
            SpoceRendering.class,
            NodeNetworkRendering.class,
            EnergyWarningRendering.class,
            ConfigOverrideRendering.class,
            ConfiguratorInteractionReport.class,
            ContainerProgressBar.class,
            ContainerValueConfig.class,
            PocketNodeRendering.class,
            RenderingClear.INSTANCE,
            NodeHudData.class,
            HubPluginSyncData.class
        );
    }
}
