package com.circulation.circulation_networks.packets;

import com.circulation.circulation_networks.container.ContainerMachinePriority;
import com.circulation.circulation_networks.utils.Packet;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import org.jetbrains.annotations.Nullable;

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
        public void fromBytes(ByteBuf buf) {
            sessionId = buf.readInt();
            priority = buf.readInt();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(sessionId);
            buf.writeInt(priority);
        }

        @Override
        public @Nullable IMessage onMessage(Submit message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (player.openContainer instanceof ContainerMachinePriority container
                    && container.getSessionId() == message.sessionId) {
                    container.applyPriority(message.priority);
                }
            });
            return null;
        }
    }
}
