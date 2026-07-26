package com.circulation.circulation_networks.packets;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.container.ContainerMachinePriority;
import com.circulation.circulation_networks.utils.Packet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/** Container-bound client command for committing a machine priority. */
public final class MachinePriorityPackets {

    private MachinePriorityPackets() {
    }

    public static final class Submit implements Packet<Submit> {
        public static final Type<Submit> TYPE = new Type<>(
            ResourceLocation.parse(CirculationFlowNetworks.MOD_ID + ":submit_machine_priority")
        );

        private int sessionId;
        private int priority;

        public Submit() {
        }

        public Submit(int sessionId, int priority) {
            this.sessionId = sessionId;
            this.priority = priority;
        }

        @Override
        public Submit decode(RegistryFriendlyByteBuf buf) {
            return new Submit(buf.readInt(), buf.readInt());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf) {
            buf.writeInt(sessionId);
            buf.writeInt(priority);
        }

        @Override
        public void handle(Submit message, IPayloadContext context) {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            context.enqueueWork(() -> {
                if (player.containerMenu instanceof ContainerMachinePriority container
                    && container.getSessionId() == message.sessionId) {
                    container.applyPriority(message.priority);
                }
            });
        }

        @NotNull
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
