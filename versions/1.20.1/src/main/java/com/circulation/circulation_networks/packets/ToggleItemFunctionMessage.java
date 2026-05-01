package com.circulation.circulation_networks.packets;

import com.circulation.circulation_networks.items.ItemCirculationConfigurator;
import com.circulation.circulation_networks.registry.CFNItems;
import com.circulation.circulation_networks.utils.Packet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class ToggleItemFunctionMessage implements Packet<ToggleItemFunctionMessage> {

    public ToggleItemFunctionMessage() {
    }

    @Override
    public ToggleItemFunctionMessage decode(FriendlyByteBuf buf) {
        return new ToggleItemFunctionMessage();
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
    }

    @Override
    public void handle(ToggleItemFunctionMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            var stack = sender.getMainHandItem();
            if (stack.getItem() == CFNItems.circulationConfigurator) {
                ItemCirculationConfigurator.sendModeMessage(
                    sender,
                    ItemCirculationConfigurator.toggleFunction(stack, sender)
                );
            }
        });
        context.setPacketHandled(true);
    }
}
