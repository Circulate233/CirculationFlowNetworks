package com.circulation.circulation_networks.packets;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.container.CFNBaseContainer;
import com.circulation.circulation_networks.utils.Packet;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public final class ContainerProgressBar implements Packet<ContainerProgressBar> {

    public static final Type<ContainerProgressBar> TYPE = new Type<>(
        Identifier.parse(CirculationFlowNetworks.MOD_ID + ":container_progress_bar")
    );
    private final short id;
    private final long numericValue;

    public ContainerProgressBar() {
        this((short) 0, 0L);
    }

    public ContainerProgressBar(short channel, long value) {
        this.id = channel;
        this.numericValue = value;
    }

    public @NonNull ContainerProgressBar decode(@NonNull RegistryFriendlyByteBuf buf) {
        return new ContainerProgressBar(buf.readShort(), buf.readLong());
    }

    public void encode(@NonNull RegistryFriendlyByteBuf buf) {
        buf.writeShort(id);
        buf.writeLong(numericValue);
    }

    public void handle(@NonNull ContainerProgressBar message, @NonNull IPayloadContext context) {
        if (context.player() instanceof ServerPlayer serverPlayer) {
            if (serverPlayer.containerMenu instanceof CFNBaseContainer container) {
                container.init();
            }
            return;
        }

        context.enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player != null && player.containerMenu instanceof CFNBaseContainer container) {
                container.updateFullProgressBar(message.id, message.numericValue);
            }
        });
    }

    @NotNull
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
