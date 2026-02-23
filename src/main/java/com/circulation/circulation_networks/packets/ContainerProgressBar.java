package com.circulation.circulation_networks.packets;

import com.circulation.circulation_networks.container.CFNBaseContainer;
import com.circulation.circulation_networks.utils.Packet;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class ContainerProgressBar implements Packet<ContainerProgressBar> {
    private short id;
    private long value;

    public ContainerProgressBar() {

    }

    public ContainerProgressBar(short channel, long val) {
        this.id = channel;
        this.value = val;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.id = buf.readShort();
        this.value = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeShort(this.id);
        buf.writeLong(this.value);
    }

    @Override
    public IMessage onMessage(ContainerProgressBar message, MessageContext ctx) {
        var c = Minecraft.getMinecraft().player.openContainer;
        if (c instanceof CFNBaseContainer cc) {
            cc.updateFullProgressBar(this.id, this.value);
        }
        return null;
    }
}
