package com.circulation.circulation_networks.packets;

import com.circulation.circulation_networks.api.CFNBlockEntityEx;
import com.circulation.circulation_networks.api.IGrid;
import com.circulation.circulation_networks.api.IMachineNodeBlockEntity;
import com.circulation.circulation_networks.api.node.IEnergySupplyNode;
import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.handlers.NodeNetworkRenderingHandler;
import com.circulation.circulation_networks.manager.EnergyMachineManager;
import com.circulation.circulation_networks.manager.NetworkManager;
import com.circulation.circulation_networks.utils.Packet;
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
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class NodeNetworkRendering implements Packet<NodeNetworkRendering> {

    public static final int SET = 0;
    public static final int NODE_ADD = 1;
    public static final int NODE_REMOVE = 2;
    public static final int MACHINE_ADD = 3;
    public static final int MACHINE_REMOVE = 4;

    private static final Object2ReferenceMap<IGrid, ReferenceLinkedOpenHashSet<ServerPlayer>> GRID_PLAYERS = new Object2ReferenceOpenHashMap<>();
    private static final Reference2ReferenceMap<ServerPlayer, IGrid> PLAYER_GRID = new Reference2ReferenceOpenHashMap<>();

    private int mode;
    private int dim;
    private IGrid grid;
    private ReferenceSet<INode> nodes;
    private INode targetNode;
    private List<Pair> entryList;
    private transient int parsedMode;
    private transient long[] parsedNodeLinks;
    private transient long[] parsedNodeRemoveMachineLinks;
    private transient long[] parsedMachineLinks;

    public NodeNetworkRendering() {
    }

    public NodeNetworkRendering(Player player, IGrid grid) {
        this.dim = player.level().dimension().location().hashCode();
        this.grid = grid;
        this.nodes = grid.getNodes();
        this.mode = SET;
        this.entryList = new ObjectArrayList<>();
        for (var node : this.nodes) {
            if (!(node instanceof IEnergySupplyNode energySupplyNode)) {
                continue;
            }
            for (var machine : EnergyMachineManager.INSTANCE.getMachinesSuppliedBy(energySupplyNode)) {
                if (machine instanceof IMachineNodeBlockEntity) {
                    continue;
                }
                entryList.add(new Pair(machine, node));
            }
        }
    }

    public NodeNetworkRendering(Player player, INode node, int mode) {
        this.dim = player.level().dimension().location().hashCode();
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

    public NodeNetworkRendering(Player player, CFNBlockEntityEx blockEntity, INode node, int mode) {
        this.dim = player.level().dimension().location().hashCode();
        this.grid = node.getGrid();
        this.mode = mode;
        this.entryList = ObjectLists.singleton(new Pair(blockEntity, node));
    }

    public static void addPlayer(IGrid grid, ServerPlayer player) {
        removePlayer(player);
        GRID_PLAYERS.computeIfAbsent(grid, ignored -> new ReferenceLinkedOpenHashSet<>()).add(player);
        PLAYER_GRID.put(player, grid);
    }

    public static void removePlayer(ServerPlayer player) {
        var grid = PLAYER_GRID.remove(player);
        if (grid == null) {
            return;
        }
        var players = GRID_PLAYERS.get(grid);
        if (players == null) {
            return;
        }
        if (players.size() == 1 && players.contains(player)) {
            GRID_PLAYERS.remove(grid);
        } else {
            players.remove(player);
        }
    }

    public static ReferenceSet<ServerPlayer> getPlayers(IGrid grid) {
        return GRID_PLAYERS.get(grid);
    }

    private static void writeLinks(FriendlyByteBuf buf, IntSupplier writer) {
        int pos = buf.writerIndex();
        buf.writeInt(0);
        int count = writer.getAsInt();
        buf.setInt(pos, count);
    }

    private static long[] readLongPairs(FriendlyByteBuf buf) {
        int count = buf.readInt();
        long[] data = new long[count * 2];
        for (int i = 0; i < data.length; i++) {
            data[i] = buf.readLong();
        }
        return data;
    }

    public NodeNetworkRendering decode(FriendlyByteBuf buf) {
        NodeNetworkRendering message = new NodeNetworkRendering();
        message.parsedMode = buf.readByte();
        if (message.parsedMode == SET || message.parsedMode == NODE_ADD || message.parsedMode == NODE_REMOVE) {
            message.parsedNodeLinks = readLongPairs(buf);
        }
        if (message.parsedMode == NODE_REMOVE) {
            message.parsedNodeRemoveMachineLinks = readLongPairs(buf);
        }
        if (message.parsedMode == SET || message.parsedMode == MACHINE_ADD || message.parsedMode == MACHINE_REMOVE) {
            message.parsedMachineLinks = readLongPairs(buf);
        }
        return message;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(mode);

        if (mode == SET || mode == NODE_ADD || mode == NODE_REMOVE) {
            writeLinks(buf, () -> {
                int count = 0;
                LongSet processedLinks = new LongOpenHashSet();
                if (mode == SET) {
                    for (var node : nodes) {
                        if (dim != node.getWorld().dimension().location().hashCode()) {
                            continue;
                        }
                        long posA = node.getPos().asLong();
                        for (var neighbor : node.getNeighbors()) {
                            long posB = neighbor.getPos().asLong();
                            long min = Math.min(posA, posB);
                            long max = Math.max(posA, posB);
                            if (processedLinks.add(min ^ Long.rotateLeft(max, 32))) {
                                buf.writeLong(posA);
                                buf.writeLong(posB);
                                count++;
                            }
                        }
                    }
                } else {
                    long posB = targetNode.getPos().asLong();
                    for (var node : nodes) {
                        long posA = node.getPos().asLong();
                        long min = Math.min(posA, posB);
                        long max = Math.max(posA, posB);
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
                if (!(targetNode instanceof IEnergySupplyNode supplyNode)) {
                    return 0;
                }
                int count = 0;
                for (var blockEntity : EnergyMachineManager.INSTANCE.getMachinesSuppliedBy(supplyNode)) {
                    buf.writeLong(blockEntity.cfn_getBlockPos().asLong());
                    buf.writeLong(targetNode.getPos().asLong());
                    count++;
                }
                return count;
            });
        }

        if (mode == SET || mode == MACHINE_ADD || mode == MACHINE_REMOVE) {
            writeLinks(buf, () -> {
                int count = 0;
                for (var entry : entryList) {
                    if (dim != entry.blockEntity.cfn_getDimensionId()) {
                        continue;
                    }
                    var node = entry.node;
                    if (node.getGrid() != grid) {
                        continue;
                    }
                    buf.writeLong(entry.blockEntity.cfn_getBlockPos().asLong());
                    buf.writeLong(node.getPos().asLong());
                    count++;
                }
                return count;
            });
        }
    }

    public void handle(NodeNetworkRendering message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (!FMLEnvironment.dist.isClient()) {
                return;
            }
            if (Minecraft.getInstance().player == null) {
                return;
            }
            var handler = NodeNetworkRenderingHandler.INSTANCE;
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
        context.setPacketHandled(true);
    }

    private record Pair(CFNBlockEntityEx blockEntity, INode node) {
    }
}
