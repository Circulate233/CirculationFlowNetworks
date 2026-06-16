package com.circulation.circulation_networks.network;

import com.circulation.circulation_networks.api.IGrid;
import com.circulation.circulation_networks.api.node.IHubNode;
import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.manager.EnergyMachineManager;
import com.circulation.circulation_networks.registry.NodeTypes;
import com.circulation.circulation_networks.utils.NbtCompat;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class Grid implements IGrid {

    private final UUID id;
    private final ReferenceSet<INode> nodes = new ReferenceOpenHashSet<>();
    private long snapshotVersion = 1L;
    @Nullable
    private IHubNode hubNode;
    // Runtime-only per-tick energy pipeline state, stored here so the server tick hot path
    // reads it by reference instead of via an identity-keyed map. Never serialized.
    @Nullable
    private transient EnergyMachineManager.GridTickData energyTickData;

    public Grid(UUID id) {
        this.id = id;
    }

    public static Grid deserialize(CompoundTag nbt) {
        UUID id = NbtCompat.getUuidOrNull(nbt, "id");
        if (id == null) {
            throw new IllegalArgumentException("Missing UUID tag: id");
        }
        var grid = new Grid(id);
        var list = NbtCompat.getListOrEmpty(nbt, "nodes");

        var posMap = new Long2ReferenceOpenHashMap<INode>();
        for (var nbtBase : list) {
            var nodeNbt = (CompoundTag) nbtBase;
            var node = NodeTypes.deserialize(nodeNbt);
            if (node != null) {
                node.setGrid(grid);
                node.setActive(true);
                grid.nodes.add(node);
                posMap.put(NbtCompat.getLongOr(nodeNbt, "pos", 0L), node);
                if (node instanceof IHubNode hub) {
                    grid.setHubNode(hub);
                }
            }
        }
        for (var nbtBase : list) {
            var nodeNbt = (CompoundTag) nbtBase;
            var node = posMap.get(NbtCompat.getLongOr(nodeNbt, "pos", 0L));
            if (node == null) continue;
            var neighborList = NbtCompat.getListOrEmpty(nodeNbt, "neighbors");
            for (var nb : neighborList) {
                var neighbor = posMap.get(NbtCompat.getLongValue(nb));
                if (neighbor != null) {
                    node.addNeighbor(neighbor);
                }
            }
        }

        return grid;
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
    @Nullable
    public EnergyMachineManager.GridTickData getEnergyTickData() {
        return energyTickData;
    }

    @Override
    public void setEnergyTickData(@Nullable EnergyMachineManager.GridTickData data) {
        this.energyTickData = data;
    }

    @Override
    public CompoundTag serialize() {
        var nbt = new CompoundTag();
        var list = new ListTag();
        NbtCompat.putUuid(nbt, "id", id);
        if (!nodes.isEmpty()) {
            for (var node : nodes) {
                nbt.putString("dim", node.getDimensionId());
                break;
            }
            for (var node : nodes) {
                list.add(node.serialize());
            }
        }
        nbt.put("nodes", list);
        return nbt;
    }

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
