package com.circulation.circulation_networks.network;

import com.circulation.circulation_networks.api.IGrid;
import com.circulation.circulation_networks.api.node.IHubNode;
import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.manager.EnergyMachineManager;
import com.circulation.circulation_networks.manager.GridParticipantIndex;
import com.circulation.circulation_networks.manager.PocketNodeManager;
import com.circulation.circulation_networks.registry.NodeTypes;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
//~ mc_imports
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
//? if <1.20 {
import net.minecraftforge.common.util.Constants;
//?} else {
/*import net.minecraft.nbt.Tag;
 *///?}

import org.jetbrains.annotations.Nullable;
import java.util.UUID;

public final class Grid implements IGrid {

    private final UUID id;
    private final ReferenceSet<INode> nodes = new ReferenceOpenHashSet<>();
    private final transient EnergyMachineManager.Interaction interaction = new EnergyMachineManager.Interaction();
    private final transient GridParticipantIndex participantIndex = new GridParticipantIndex(this);
    private long snapshotVersion = 1L;
    @Nullable
    private IHubNode hubNode;

    public Grid(UUID id) {
        this.id = id;
    }

    //? if <1.20 {
    public static Grid deserialize(NBTTagCompound nbt) {
        var grid = new Grid(nbt.getUniqueId("id"));
        var list = nbt.getTagList("nodes", 10);

        var posMap = new Long2ReferenceOpenHashMap<INode>();
        for (var nbtBase : list) {
            var nodeNbt = (NBTTagCompound) nbtBase;
            var node = NodeTypes.deserialize(nodeNbt);
            if (node != null) {
                node.setGrid(grid);
                node.setActive(true);
                grid.nodes.add(node);
                posMap.put(nodeNbt.getLong("pos"), node);
                if (node instanceof IHubNode hub) {
                    grid.setHubNode(hub);
                }
            }
        }
        for (var nbtBase : list) {
            var nodeNbt = (NBTTagCompound) nbtBase;
            var node = posMap.get(nodeNbt.getLong("pos"));
            if (node == null) continue;
            var neighborList = nodeNbt.getTagList("neighbors", Constants.NBT.TAG_LONG);
            for (var nb : neighborList) {
                var neighbor = posMap.get(((NBTTagLong) nb).getLong());
                if (neighbor != null) {
                    node.addNeighbor(neighbor);
                }
            }
        }

        return grid;
    }
    //?} else {
    /*public static Grid deserialize(CompoundTag nbt) {
        var grid = new Grid(nbt.getUUID("id"));
        var list = nbt.getList("nodes", Tag.TAG_COMPOUND);

        var posMap = new Long2ReferenceOpenHashMap<INode>();
        for (var nbtBase : list) {
            var nodeNbt = (CompoundTag) nbtBase;
            var node = NodeTypes.deserialize(nodeNbt);
            if (node != null) {
                node.setGrid(grid);
                node.setActive(true);
                grid.nodes.add(node);
                posMap.put(nodeNbt.getLong("pos"), node);
                if (node instanceof IHubNode hub) {
                    grid.setHubNode(hub);
                }
            }
        }
        for (var nbtBase : list) {
            var nodeNbt = (CompoundTag) nbtBase;
            var node = posMap.get(nodeNbt.getLong("pos"));
            if (node == null) continue;
            var neighborList = nodeNbt.getList("neighbors", Tag.TAG_LONG);
            for (var nb : neighborList) {
                var neighbor = posMap.get(((LongTag) nb).getAsLong());
                if (neighbor != null) {
                    node.addNeighbor(neighbor);
                }
            }
        }

        return grid;
    }
    *///?}

    //~ if >=1.20 '.provider.getDimension()' -> '.dimension().location().hashCode()' {
    private static int getDimensionId(INode node) {
        return node.getDimensionId();
    }
    //~}

    private static boolean shouldSerializeNode(INode node) {
        return node != null
            && !PocketNodeManager.INSTANCE.isActivePocketNode(node.getDimensionId(), node.getPos(), node.getNodeType());
    }

    public UUID getId() {
        return id;
    }

    public ReferenceSet<INode> getNodes() {
        return nodes;
    }

    @Nullable
    public IHubNode getHubNode() {
        return hubNode;
    }

    public void setHubNode(@Nullable IHubNode hubNode) {
        this.hubNode = hubNode;
        markSnapshotDirty();
    }

    @Override
    public long getSnapshotVersion() {
        return snapshotVersion;
    }

    @Override
    public void markSnapshotDirty() {
        snapshotVersion++;
    }

    @Override
    public EnergyMachineManager.Interaction getInteraction() {
        return interaction;
    }

    @Override
    public GridParticipantIndex getParticipantIndex() {
        return participantIndex;
    }

    //? if <1.20 {
    @Override
    public NBTTagCompound serialize() {
        var nbt = new NBTTagCompound();
        var list = new NBTTagList();
        nbt.setUniqueId("id", id);
        if (!nodes.isEmpty()) {
            for (var node : nodes) {
                nbt.setInteger("dim", getDimensionId(node));
                break;
            }
            for (var node : nodes) {
                if (shouldSerializeNode(node)) {
                    list.appendTag(node.serialize());
                }
            }
        }
        nbt.setTag("nodes", list);
        return nbt;
    }
    //?} else {
    /*@Override
    public CompoundTag serialize() {
        var nbt = new CompoundTag();
        var list = new ListTag();
        nbt.putUUID("id", id);
        if (!nodes.isEmpty()) {
            for (var node : nodes) {
                nbt.putString("dim", node.getSerializedDimensionKey());
                break;
            }
            for (var node : nodes) {
                list.add(node.serialize());
            }
        }
        nbt.put("nodes", list);
        return nbt;
    }
    *///?}

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Grid other = (Grid) obj;
        return this.id.equals(other.id);
    }
}
