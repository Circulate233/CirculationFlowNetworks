package com.circulation.circulation_networks.packets;

import com.circulation.circulation_networks.api.IGrid;
import com.circulation.circulation_networks.handlers.NodeNetworkRenderingHandler;
import com.circulation.circulation_networks.manager.EnergyMachineManager;
import com.circulation.circulation_networks.utils.Packet;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class NodeNetworkRendering implements Packet<NodeNetworkRendering> {

    private int dim;
    private IGrid grid;

    public NodeNetworkRendering() {

    }

    public NodeNetworkRendering(EntityPlayer player, IGrid grid) {
        this.dim = player.getEntityWorld().provider.getDimension();
        this.grid = grid;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        NodeNetworkRenderingHandler.INSTANCE.clearLinks();
        int nodeCount = buf.readInt();
        for (int i = 0; i < nodeCount; i++) {
            NodeNetworkRenderingHandler.INSTANCE.addNodeLink(buf.readLong(), buf.readLong());
        }

        int machineCount = buf.readInt();
        for (int i = 0; i < machineCount; i++) {
            NodeNetworkRenderingHandler.INSTANCE.addMachineLink(buf.readLong(), buf.readLong());
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        var activeNodes = grid.getNodes();
        int nodeLinkPos = buf.writerIndex();
        buf.writeInt(0);
        int nodeLinkCount = 0;

        LongSet processedLinks = new LongOpenHashSet();

        for (var node : activeNodes) {
            if (dim != node.getWorld().provider.getDimension()) continue;
            long posA = node.getPos().toLong();
            for (var neighbor : node.getNeighbors()) {
                long posB = neighbor.getPos().toLong();
                long min = Math.min(posA, posB);
                long max = Math.max(posA, posB);
                long linkKey = min ^ Long.rotateLeft(max, 32);
                if (processedLinks.add(linkKey)) {
                    buf.writeLong(posA);
                    buf.writeLong(posB);
                    nodeLinkCount++;
                }
            }
        }
        buf.setInt(nodeLinkPos, nodeLinkCount);

        int machineLinkPos = buf.writerIndex();
        buf.writeInt(0);
        int machineLinkCount = 0;

        for (var entry : EnergyMachineManager.INSTANCE.getMachineGridMap().entrySet()) {
            if (dim != entry.getKey().getWorld().provider.getDimension()) continue;
            for (var node : entry.getValue()) {
                if (node.getGrid() != grid) continue;
                buf.writeLong(entry.getKey().getPos().toLong());
                buf.writeLong(node.getPos().toLong());
                machineLinkCount++;
            }
        }
        buf.setInt(machineLinkPos, machineLinkCount);
    }

    @Override
    public IMessage onMessage(NodeNetworkRendering message, MessageContext ctx) {
        return null;
    }
}