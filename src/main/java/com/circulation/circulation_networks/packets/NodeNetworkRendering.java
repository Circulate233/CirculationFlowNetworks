package com.circulation.circulation_networks.packets;

import com.circulation.circulation_networks.api.IGrid;
import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.handlers.NodeNetworkRenderingHandler;
import com.circulation.circulation_networks.manager.EnergyMachineManager;
import com.circulation.circulation_networks.utils.Packet;
import com.github.bsideup.jabel.Desugar;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import it.unimi.dsi.fastutil.objects.ReferenceSets;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.List;
import java.util.function.BiConsumer;

public class NodeNetworkRendering implements Packet<NodeNetworkRendering> {

    public static final int SET = 0;
    public static final int NODE_ADD = 1;
    private int dim;
    private IGrid grid;
    public static final int NODE_REMOVE = 2;
    public static final int MACHINE_ADD = 3;
    public static final int MACHINE_REMOVE = 4;
    private static final Object2ReferenceMap<IGrid, ReferenceLinkedOpenHashSet<EntityPlayerMP>> gridPlayers = new Object2ReferenceOpenHashMap<>();
    private static final Reference2ReferenceMap<EntityPlayerMP, IGrid> playerGrid = new Reference2ReferenceOpenHashMap<>();
    private ReferenceSet<INode> nodes;
    private List<Pair> entryList;
    private int mode;

    public NodeNetworkRendering(EntityPlayer player, IGrid grid) {
        this.dim = player.getEntityWorld().provider.getDimension();
        this.grid = grid;
        this.nodes = grid.getNodes();
        this.mode = SET;
        this.entryList = new ObjectArrayList<>();
        for (var e : EnergyMachineManager.INSTANCE.getMachineGridMap().entrySet()) {
            for (var node : e.getValue()) {
                entryList.add(new Pair(e.getKey(), node));
            }
        }
    }

    public NodeNetworkRendering(EntityPlayer player, INode node, int mode) {
        this.dim = player.getEntityWorld().provider.getDimension();
        this.grid = node.getGrid();
        this.nodes = ReferenceSets.singleton(node);
        this.mode = mode;
    }

    public NodeNetworkRendering(EntityPlayer player, TileEntity te, INode node, int mode) {
        this.dim = player.getEntityWorld().provider.getDimension();
        this.grid = node.getGrid();
        this.mode = mode;
        this.entryList = ObjectLists.singleton(new Pair(te, node));
    }

    public NodeNetworkRendering() {

    }

    public static void addPlayer(IGrid grid, EntityPlayerMP player) {
        removePlayer(player);
        gridPlayers.computeIfAbsent(grid, k -> new ReferenceLinkedOpenHashSet<>()).add(player);
        playerGrid.put(player, grid);
    }

    public static void removePlayer(EntityPlayerMP player) {
        var g = playerGrid.remove(player);
        if (g != null) {
            var s = gridPlayers.get(g);
            if (s.contains(player) && s.size() == 1) {
                gridPlayers.remove(g);
            } else {
                s.remove(player);
            }
        }
    }
    public static ReferenceSet<EntityPlayerMP> getPlayers(IGrid grid) {
        return gridPlayers.get(grid);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int mode = buf.readByte();

        if (mode == SET) {
            NodeNetworkRenderingHandler.INSTANCE.clearLinks();
        }

        if (mode == SET || mode == NODE_ADD || mode == NODE_REMOVE) {
            int count = buf.readInt();
            LongBiConsumer handler = (mode == NODE_REMOVE)
                ? NodeNetworkRenderingHandler.INSTANCE::removeNodeLink
                : NodeNetworkRenderingHandler.INSTANCE::addNodeLink;
            for (int i = 0; i < count; i++) {
                handler.accept(buf.readLong(), buf.readLong());
            }
        }

        if (mode == SET || mode == MACHINE_ADD || mode == MACHINE_REMOVE) {
            int count = buf.readInt();
            LongBiConsumer handler = (mode == MACHINE_REMOVE)
                ? NodeNetworkRenderingHandler.INSTANCE::removeMachineLink
                : NodeNetworkRenderingHandler.INSTANCE::addMachineLink;

            for (int i = 0; i < count; i++) {
                handler.accept(buf.readLong(), buf.readLong());
            }
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(mode);

        if (mode == SET || mode == NODE_ADD || mode == NODE_REMOVE) {
            writeLinks(buf, () -> {
                int count = 0;
                LongSet processedLinks = new LongOpenHashSet();
                for (var node : nodes) {
                    if (dim != node.getWorld().provider.getDimension()) continue;
                    long posA = node.getPos().toLong();
                    for (var neighbor : node.getNeighbors()) {
                        long posB = neighbor.getPos().toLong();
                        long min = Math.min(posA, posB), max = Math.max(posA, posB);
                        if (processedLinks.add(min ^ Long.rotateLeft(max, 32))) {
                            buf.writeLong(posA);
                            buf.writeLong(posB);
                            count++;
                        }
                    }
                }
                return count;
            });
        }

        if (mode == SET || mode == MACHINE_ADD || mode == MACHINE_REMOVE) {
            writeLinks(buf, () -> {
                int count = 0;
                for (var entry : entryList) {
                    if (dim != entry.tileEntity.getWorld().provider.getDimension()) continue;
                    var node = entry.node;
                    if (node.getGrid() != grid) continue;
                    buf.writeLong(entry.tileEntity.getPos().toLong());
                    buf.writeLong(node.getPos().toLong());
                    count++;
                }
                return count;
            });
        }
    }

    private void writeLinks(ByteBuf buf, java.util.function.Supplier<Integer> writer) {
        int pos = buf.writerIndex();
        buf.writeInt(0);
        int count = writer.get();
        buf.setInt(pos, count);
    }

    private interface LongBiConsumer extends BiConsumer<Long, Long> {

        default void accept(Long t, Long u) {
            accept(t.longValue(), u.longValue());
        }

        void accept(long t, long u);
    }

    @Desugar
    private record Pair(TileEntity tileEntity, INode node) {
    }

    @Override
    public IMessage onMessage(NodeNetworkRendering message, MessageContext ctx) {
        return null;
    }
}