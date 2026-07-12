package com.circulation.circulation_networks.packets;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.api.API;
import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.manager.EnergyMachineManager;
import com.circulation.circulation_networks.manager.PocketNodeManager;
import com.circulation.circulation_networks.registry.PocketNodeItems;
import com.circulation.circulation_networks.utils.Packet;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import org.jetbrains.annotations.Nullable;

public final class NodeHudRequest implements Packet<NodeHudRequest> {

    private long posLong;

    public NodeHudRequest() {
    }

    public NodeHudRequest(long pos) {
        this.posLong = pos;
    }

    public NodeHudRequest(BlockPos pos) {
        this.posLong = pos.toLong();
    }

    private static String resolveDisplayName(EntityPlayerMP sender, BlockPos pos, INode node) {
        String displayName = node.getCustomName();
        if (displayName != null && !displayName.trim().isEmpty()) {
            return displayName;
        }
        if (!PocketNodeManager.INSTANCE.isActivePocketNode(sender.world, pos, node.getNodeType())) {
            return "";
        }
        ItemStack stack = PocketNodeItems.createStack(node.getNodeType());
        return stack.isEmpty() ? "" : stack.getDisplayName();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        posLong = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(posLong);
    }

    @Override
    public @Nullable IMessage onMessage(NodeHudRequest message, MessageContext ctx) {
        EntityPlayerMP sender = ctx.getServerHandler().player;

        BlockPos pos = BlockPos.fromLong(message.posLong);
        if (sender.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64 * 64) return null;

        INode node = API.getNodeAt(sender.world, pos);
        if (node == null) return null;

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
                input = interaction.getInput().toString();
                output = interaction.getOutput().toString();
                interactionTimeMicros = interaction.getInteractionTimeMicrosString();
            }
        }

        CirculationFlowNetworks.sendToPlayer(
            new NodeHudData(pos, displayName, input, output, interactionTimeMicros, nodeCount),
            sender
        );
        return null;
    }
}
