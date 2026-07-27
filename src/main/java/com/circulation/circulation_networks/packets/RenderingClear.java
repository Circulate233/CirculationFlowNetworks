package com.circulation.circulation_networks.packets;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.handlers.CirculationShielderRenderingHandler;
import com.circulation.circulation_networks.handlers.ConfigOverrideRenderingHandler;
import com.circulation.circulation_networks.handlers.EnergyWarningRenderingHandler;
import com.circulation.circulation_networks.handlers.NodeHighlightRenderingHandler;
import com.circulation.circulation_networks.handlers.NodeNetworkRenderingHandler;
import com.circulation.circulation_networks.handlers.PocketNodeRenderingHandler;
import com.circulation.circulation_networks.handlers.SpoceRenderingHandler;
import com.circulation.circulation_networks.utils.Packet;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public final class RenderingClear implements Packet<RenderingClear> {

    public static final RenderingClear INSTANCE = new RenderingClear();
    public static final Type<RenderingClear> TYPE = new Type<>(
        Identifier.parse(CirculationFlowNetworks.MOD_ID + ":rendering_clear")
    );

    public RenderingClear decode(@NonNull RegistryFriendlyByteBuf buf) {
        return INSTANCE;
    }

    public void encode(@NonNull RegistryFriendlyByteBuf buf) {
    }

    public void handle(@NonNull RenderingClear message, @NonNull IPayloadContext context) {
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().player == null) {
                return;
            }
            NodeNetworkRenderingHandler.INSTANCE.clearLinks();
            EnergyWarningRenderingHandler.INSTANCE.clear();
            ConfigOverrideRenderingHandler.INSTANCE.clear();
            PocketNodeRenderingHandler.INSTANCE.clear();
            NodeHighlightRenderingHandler.INSTANCE.clear();
            CirculationShielderRenderingHandler.INSTANCE.clear();
            if (SpoceRenderingHandler.INSTANCE != null) {
                SpoceRenderingHandler.INSTANCE.clear();
            }
        });
    }

    @NotNull
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
