package com.circulation.circulation_networks.packets;

import com.circulation.circulation_networks.container.ContainerMachinePriority;
import com.circulation.circulation_networks.utils.Packet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Container-bound client command for committing a machine priority. */
public final class MachinePriorityPackets {

    private MachinePriorityPackets() {
    }

    public static final class Submit implements Packet<Submit> {
        private int sessionId;
        private int priority;

        public Submit() {
        }

        public Submit(int sessionId, int priority) {
            this.sessionId = sessionId;
            this.priority = priority;
        }

        @Override
        public Submit decode(FriendlyByteBuf buf) {
            return new Submit(buf.readInt(), buf.readInt());
        }

        @Override
        public void encode(FriendlyByteBuf buf) {
            buf.writeInt(sessionId);
            buf.writeInt(priority);
        }

        @Override
        public void handle(Submit message, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player != null && player.containerMenu instanceof ContainerMachinePriority container
                    && container.getSessionId() == message.sessionId) {
                    container.applyPriority(message.priority);
                }
            });
            context.setPacketHandled(true);
        }
    }
}
