package com.circulation.circulation_networks.packets;

import com.circulation.circulation_networks.handlers.SpoceRenderingHandler;
import com.circulation.circulation_networks.utils.Packet;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class SpoceRendering implements Packet<SpoceRendering> {

    private BlockPos pos;
    private double l, e, c;

    public SpoceRendering() {

    }

    public SpoceRendering(BlockPos pos, double l, double e, double c) {
        this.pos = pos;
        this.l = l;
        this.e = e;
        this.c = c;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.pos = BlockPos.fromLong(buf.readLong());
        this.l = buf.readDouble();
        this.e = buf.readDouble();
        this.c = buf.readDouble();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        buf.writeDouble(l);
        buf.writeDouble(e);
        buf.writeDouble(c);
    }

    @Override
    public IMessage onMessage(SpoceRendering message, MessageContext ctx) {
        var te = Minecraft.getMinecraft().world.getTileEntity(message.pos);
        if (te != null) {
            SpoceRenderingHandler.INSTANCE.setStaus(te, message.l, message.e, message.c);
        }
        return null;
    }

}
