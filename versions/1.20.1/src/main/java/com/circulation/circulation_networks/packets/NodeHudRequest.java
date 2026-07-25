package com.circulation.circulation_networks.packets;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.api.API;
import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.manager.EnergyMachineManager;
import com.circulation.circulation_networks.manager.PocketNodeManager;
import com.circulation.circulation_networks.registry.PocketNodeItems;
import com.circulation.circulation_networks.utils.Packet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class NodeHudRequest implements Packet<NodeHudRequest> {

    private long posLong;

    public NodeHudRequest() {
    }

    public NodeHudRequest(long pos) {
        this.posLong = pos;
    }

    public NodeHudRequest(BlockPos pos) {
        this.posLong = pos.asLong();
    }

    private static String resolveDisplayName(ServerPlayer sender, BlockPos pos, INode node) {
        String displayName = node.getCustomName();
        if (displayName != null && !displayName.trim().isEmpty()) {
            return displayName;
        }
        if (!PocketNodeManager.INSTANCE.isActivePocketNode(sender.level(), pos, node.getNodeType())) {
            return "";
        }
        ItemStack stack = PocketNodeItems.createStack(node.getNodeType());
        return stack.isEmpty() ? "" : stack.getHoverName().getString();
    }

    @Override
    public NodeHudRequest decode(FriendlyByteBuf buf) {
        NodeHudRequest msg = new NodeHudRequest();
        msg.posLong = buf.readLong();
        return msg;
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeLong(posLong);
    }

    @Override
    public void handle(NodeHudRequest message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) return;

            BlockPos pos = BlockPos.of(message.posLong);
            if (sender.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64 * 64) return;

            INode node = API.getNodeAt(sender.level(), pos);
            if (node == null) return;

            String displayName = resolveDisplayName(sender, pos, node);

            String input = "0";
            String output = "0";
            String interactionTimeMicros = "0";
            int nodeCount = 0;

            if (node.getGrid() != null) {
                nodeCount = node.getGrid().getNodes().size();
                EnergyMachineManager.Interaction interaction =
                    node.getGrid().getInteraction();
                if (interaction != null) {
                    input = interaction.getInputString();
                    output = interaction.getOutputString();
                    interactionTimeMicros = interaction.getInteractionTimeMicrosString();
                }
            }

            CirculationFlowNetworks.sendToPlayer(
                new NodeHudData(pos, displayName, input, output, interactionTimeMicros, nodeCount),
                sender
            );
        });
        context.setPacketHandled(true);
    }
}
