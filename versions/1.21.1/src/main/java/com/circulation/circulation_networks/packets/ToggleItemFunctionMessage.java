package com.circulation.circulation_networks.packets;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.items.ItemCirculationConfigurator;
import com.circulation.circulation_networks.registry.CFNItems;
import com.circulation.circulation_networks.utils.Packet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public final class ToggleItemFunctionMessage implements Packet<ToggleItemFunctionMessage> {

    public static final Type<ToggleItemFunctionMessage> TYPE = new Type<>(
        ResourceLocation.parse(CirculationFlowNetworks.MOD_ID + ":toggle_item_function")
    );

    public ToggleItemFunctionMessage() {
    }

    @Override
    public ToggleItemFunctionMessage decode(RegistryFriendlyByteBuf buf) {
        return new ToggleItemFunctionMessage();
    }

    @Override
    public void encode(RegistryFriendlyByteBuf buf) {
    }

    @Override
    public void handle(ToggleItemFunctionMessage message, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        context.enqueueWork(() -> {
            var stack = serverPlayer.getMainHandItem();
            if (stack.getItem() == CFNItems.circulationConfigurator) {
                ItemCirculationConfigurator.sendModeMessage(
                    serverPlayer,
                    ItemCirculationConfigurator.toggleFunction(stack, serverPlayer)
                );
            }
        });
    }

    @NotNull
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
