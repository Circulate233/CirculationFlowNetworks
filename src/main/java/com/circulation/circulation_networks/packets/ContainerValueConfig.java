package com.circulation.circulation_networks.packets;

import com.circulation.circulation_networks.container.CFNBaseContainer;
import com.circulation.circulation_networks.utils.Packet;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class ContainerValueConfig implements Packet<ContainerValueConfig> {

    private short Name;
    private String Value;

    public ContainerValueConfig() {
    }

    public ContainerValueConfig(short name, String value) {
        this.Name = name;
        this.Value = value;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.Name = buf.readShort();
        this.Value = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeShort(Name);
        ByteBufUtils.writeUTF8String(buf, this.Value);
    }

    @Override
    public IMessage onMessage(ContainerValueConfig message, MessageContext ctx) {
        var c = Minecraft.getMinecraft().player.openContainer;
        if (c instanceof CFNBaseContainer cc) {
            cc.stringSync(message.Name, message.Value);
        }
        return null;
    }
}
