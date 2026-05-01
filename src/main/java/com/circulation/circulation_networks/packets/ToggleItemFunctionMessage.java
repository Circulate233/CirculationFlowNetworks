package com.circulation.circulation_networks.packets;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.items.ItemCirculationConfigurator;
import com.circulation.circulation_networks.registry.CFNItems;
import com.circulation.circulation_networks.utils.Packet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public final class ToggleItemFunctionMessage implements Packet<ToggleItemFunctionMessage> {

    public static final Type<ToggleItemFunctionMessage> TYPE = new Type<>(
        Identifier.parse(CirculationFlowNetworks.MOD_ID + ":toggle_item_function")
    );

    public ToggleItemFunctionMessage() {
    }

    public @NonNull ToggleItemFunctionMessage decode(@NonNull RegistryFriendlyByteBuf buf) {
        return new ToggleItemFunctionMessage();
    }

    public void encode(@NonNull RegistryFriendlyByteBuf buf) {
    }

    public void handle(@NonNull ToggleItemFunctionMessage message, @NonNull IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        context.enqueueWork(() -> {
            var stack = serverPlayer.getMainHandItem();
            if (stack.getItem() != CFNItems.circulationConfigurator) {
                return;
            }
            var selection = ItemCirculationConfigurator.toggleFunction(stack, serverPlayer);
            ItemCirculationConfigurator.sendModeMessage(serverPlayer, selection);
        });
    }

    @NotNull
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
