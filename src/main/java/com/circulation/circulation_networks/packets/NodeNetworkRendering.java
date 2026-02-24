package com.circulation.circulation_networks.packets;

import com.circulation.circulation_networks.api.IGrid;
import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.handlers.NodeNetworkRenderingHandler;
import com.circulation.circulation_networks.manager.EnergyMachineManager;
import com.circulation.circulation_networks.manager.NetworkManager;
import com.circulation.circulation_networks.proxy.CommonProxy;
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
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import it.unimi.dsi.fastutil.objects.ReferenceSets;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.List;
import java.util.function.IntSupplier;

public final class NodeNetworkRendering implements Packet<NodeNetworkRendering> {

    public static final int SET = 0;
    public static final int NODE_ADD = 1;
    public static final int NODE_REMOVE = 2;
    public static final int MACHINE_ADD = 3;
    public static final int MACHINE_REMOVE = 4;
    public static final int CLEAR = 5;

    private static final Object2ReferenceMap<IGrid, ReferenceLinkedOpenHashSet<EntityPlayerMP>> gridPlayers = new Object2ReferenceOpenHashMap<>();
    private static final Reference2ReferenceMap<EntityPlayerMP, IGrid> playerGrid = new Reference2ReferenceOpenHashMap<>();

    private int dim;
    private IGrid grid;
    private ReferenceSet<INode> nodes;
    private INode targetNode;
    private List<Pair> entryList;
    private final int mode;

    private transient int parsedMode;
    private transient long[] parsedNodeLinks;
    private transient long[] parsedNodeRemoveMachineLinks;
    private transient long[] parsedMachineLinks;

    public NodeNetworkRendering(EntityPlayer player, IGrid grid) {
        this.dim = player.getEntityWorld().provider.getDimension();
        this.grid = grid;
        this.nodes = grid.getNodes();
        this.mode = SET;
        this.entryList = new ObjectArrayList<>();
        for (var e : EnergyMachineManager.INSTANCE.getMachineGridMap().entrySet()) {
            if (e.getKey().hasCapability(CommonProxy.ceHandlerCapability, null)) continue;
            for (var node : e.getValue()) {
                entryList.add(new Pair(e.getKey(), node));
            }
        }
    }

    public NodeNetworkRendering(EntityPlayer player, INode node, int mode) {
        this.dim = player.getEntityWorld().provider.getDimension();
        this.grid = node.getGrid();
        this.mode = mode;
        this.targetNode = node;

        ReferenceSet<INode> relevant = new ReferenceOpenHashSet<>();

        relevant.addAll(node.getNeighbors());

        for (INode other : NetworkManager.INSTANCE.getNodesCoveringPosition(node.getWorld(), node.getPos())) {
            if (node.linkScopeCheck(other) != INode.LinkType.DISCONNECT) {
                relevant.add(other);
            }
        }

        this.nodes = ReferenceSets.unmodifiable(relevant);
    }

    public NodeNetworkRendering(EntityPlayer player, TileEntity te, INode node, int mode) {
        this.dim = player.getEntityWorld().provider.getDimension();
        this.grid = node.getGrid();
        this.mode = mode;
        this.entryList = ObjectLists.singleton(new Pair(te, node));
    }

    public NodeNetworkRendering() {
        this.mode = CLEAR;
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

    private static void writeLinks(ByteBuf buf, IntSupplier writer) {
        int pos = buf.writerIndex();
        buf.writeInt(0);
        int count = writer.getAsInt();
        buf.setInt(pos, count);
    }

    private static long[] readLongPairs(ByteBuf buf) {
        int count = buf.readInt();
        long[] data = new long[count * 2];
        for (int i = 0; i < count * 2; i++) {
            data[i] = buf.readLong();
        }
        return data;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        parsedMode = buf.readByte();
        if (parsedMode == CLEAR) return;

        if (parsedMode == SET || parsedMode == NODE_ADD || parsedMode == NODE_REMOVE) {
            parsedNodeLinks = readLongPairs(buf);
        }
        if (parsedMode == NODE_REMOVE) {
            parsedNodeRemoveMachineLinks = readLongPairs(buf);
        }
        if (parsedMode == SET || parsedMode == MACHINE_ADD || parsedMode == MACHINE_REMOVE) {
            parsedMachineLinks = readLongPairs(buf);
        }
    }

    @Override
    public IMessage onMessage(NodeNetworkRendering message, MessageContext ctx) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            var handler = NodeNetworkRenderingHandler.INSTANCE;

            if (message.parsedMode == CLEAR) {
                handler.clearLinks();
                return;
            }

            if (message.parsedMode == SET) {
                handler.clearLinks();
            }

            if (message.parsedNodeLinks != null) {
                boolean remove = message.parsedMode == NODE_REMOVE;
                for (int i = 0; i < message.parsedNodeLinks.length; i += 2) {
                    if (remove) {
                        handler.removeNodeLink(message.parsedNodeLinks[i], message.parsedNodeLinks[i + 1]);
                    } else {
                        handler.addNodeLink(message.parsedNodeLinks[i], message.parsedNodeLinks[i + 1]);
                    }
                }
            }

            if (message.parsedNodeRemoveMachineLinks != null) {
                for (int i = 0; i < message.parsedNodeRemoveMachineLinks.length; i += 2) {
                    handler.removeMachineLink(message.parsedNodeRemoveMachineLinks[i], message.parsedNodeRemoveMachineLinks[i + 1]);
                }
            }

            if (message.parsedMachineLinks != null) {
                boolean remove = message.parsedMode == MACHINE_REMOVE;
                for (int i = 0; i < message.parsedMachineLinks.length; i += 2) {
                    if (remove) {
                        handler.removeMachineLink(message.parsedMachineLinks[i], message.parsedMachineLinks[i + 1]);
                    } else {
                        handler.addMachineLink(message.parsedMachineLinks[i], message.parsedMachineLinks[i + 1]);
                    }
                }
            }
        });
        return null;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(mode);

        if (mode == CLEAR) return;

        if (mode == SET || mode == NODE_ADD || mode == NODE_REMOVE) {
            writeLinks(buf, () -> {
                int count = 0;
                LongSet processedLinks = new LongOpenHashSet();
                if (mode == SET) {
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
                } else {
                    long posB = targetNode.getPos().toLong();
                    for (var node : nodes) {
                        long posA = node.getPos().toLong();
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

        if (mode == NODE_REMOVE) {
            writeLinks(buf, () -> {
                int count = 0;
                var set = EnergyMachineManager.INSTANCE.getTileEntities(targetNode);
                for (var te : set) {
                    buf.writeLong(te.getPos().toLong());
                    buf.writeLong(targetNode.getPos().toLong());
                    count++;
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

    @Desugar
    private record Pair(TileEntity tileEntity, INode node) {
    }
}