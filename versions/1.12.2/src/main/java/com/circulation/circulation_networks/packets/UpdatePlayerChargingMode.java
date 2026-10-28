package com.circulation.circulation_networks.packets;

import com.circulation.circulation_networks.api.hub.ChargingDefinition;
import com.circulation.circulation_networks.container.ContainerHub;
import com.circulation.circulation_networks.manager.HubChannelManager;
import com.circulation.circulation_networks.manager.NetworkManager;
import com.circulation.circulation_networks.network.nodes.HubNode;
import com.circulation.circulation_networks.utils.Packet;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class UpdatePlayerChargingMode implements Packet<UpdatePlayerChargingMode> {

    private static final ChargingDefinition[] CHARGING_DEFINITIONS = ChargingDefinition.values();

    private byte bytes;
    private byte mode;

    public UpdatePlayerChargingMode() {
    }

    public UpdatePlayerChargingMode(ChargingDefinition index) {
        this.bytes = (byte) index.ordinal();
    }

    public UpdatePlayerChargingMode(byte modes) {
        this.mode = modes;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        bytes = buf.readByte();
        mode = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(bytes);
        buf.writeByte(mode);
    }

    @Override
    public IMessage onMessage(UpdatePlayerChargingMode message, MessageContext ctx) {
        if (ctx.getServerHandler().player.openContainer instanceof ContainerHub containerHub) {
            switch (message.mode) {
                case 0 -> {
                    var cd = CHARGING_DEFINITIONS[message.bytes & 0x7F];
                    containerHub.chargingMode.setPreference(cd, !containerHub.chargingMode.getPreference(cd));
                }
                case 1 -> containerHub.chargingMode.setPrefs((byte) 0b00111111);
                case 2 -> containerHub.chargingMode.setPrefs((byte) 0);
            }
            NetworkManager.INSTANCE.markGridDirty(containerHub.node.getGrid());
            if (!containerHub.node.getChannelId().equals(HubNode.EMPTY)) {
                HubChannelManager.INSTANCE.markChargingDirty();
            }
        }
        return null;
    }

}