package com.circulation.circulation_networks.packets;

import com.circulation.circulation_networks.items.ItemInspectionTool;
import com.circulation.circulation_networks.utils.Functions;
import com.circulation.circulation_networks.utils.Packet;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class UpdateItemModeMessage implements Packet<UpdateItemModeMessage> {

    private byte mode;

    public UpdateItemModeMessage() {
    }

    public UpdateItemModeMessage(int mode) {
        this.mode = (byte) mode;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        mode = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(mode);
    }

    @Override
    public IMessage onMessage(UpdateItemModeMessage message, MessageContext ctx) {
        ItemStack stack = ctx.getServerHandler().player.getHeldItemMainhand();

        if (stack.getItem() instanceof ItemInspectionTool && stack.getTagCompound() != null) {
            Functions.getOrCreateTagCompound(stack).setInteger("mode", Math.floorMod(message.mode, ItemInspectionTool.Mode.values().length));
        }
        return null;
    }

}