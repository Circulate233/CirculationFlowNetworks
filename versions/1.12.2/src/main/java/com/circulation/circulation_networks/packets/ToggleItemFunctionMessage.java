package com.circulation.circulation_networks.packets;

import com.circulation.circulation_networks.items.ItemCirculationConfigurator;
import com.circulation.circulation_networks.registry.CFNItems;
import com.circulation.circulation_networks.utils.Packet;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class ToggleItemFunctionMessage implements Packet<ToggleItemFunctionMessage> {

    public ToggleItemFunctionMessage() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
    }

    @Override
    public void toBytes(ByteBuf buf) {
    }

    @Override
    public IMessage onMessage(ToggleItemFunctionMessage message, MessageContext ctx) {
        ItemStack stack = ctx.getServerHandler().player.getHeldItemMainhand();
        if (stack.getItem() == CFNItems.circulationConfigurator) {
            ItemCirculationConfigurator.sendModeMessage(
                ctx.getServerHandler().player,
                ItemCirculationConfigurator.toggleFunction(stack, ctx.getServerHandler().player)
            );
        }
        return null;
    }
}
