package com.circulation.circulation_networks.network;

import com.circulation.circulation_networks.api.IGrid;
import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.registry.RegistryNodes;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import lombok.Getter;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

public class Grid implements IGrid {

    @Getter
    private final int id;
    @Getter
    private final ReferenceSet<INode> nodes = new ReferenceOpenHashSet<>();

    public Grid(int id) {
        this.id = id;
    }

    @Override
    public int hashCode() {
        return id;
    }

    public static Grid deserialize(NBTTagCompound nbt) {
        var grid = new Grid(nbt.getInteger("id"));
        var list = nbt.getTagList("nodes", 10);
        for (var nbtBase : list) {
            var node = RegistryNodes.deserialize((NBTTagCompound) nbtBase);
            if (node != null) {
                grid.nodes.add(node);
            }
        }
        return grid;
    }

    @Override
    public NBTTagCompound serialize() {
        var nbt = new NBTTagCompound();
        var list = new NBTTagList();
        nbt.setInteger("id", id);
        if (!nodes.isEmpty()) {
            for (var node : nodes) {
                nbt.setInteger("dim", node.getWorld().provider.getDimension());
                break;
            }
            for (var node : nodes) {
                list.appendTag(node.serialize());
            }
        }
        nbt.setTag("nodes", list);
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
        return this.id == other.id;
    }
}