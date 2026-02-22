package com.circulation.circulation_networks.network;

import com.circulation.circulation_networks.api.IGrid;
import com.circulation.circulation_networks.api.node.INode;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import lombok.Getter;

public class Grid implements IGrid {

    @Getter
    private final int id;
    private final ReferenceSet<INode> nodes = new ReferenceOpenHashSet<>();

    public Grid(int id) {
        this.id = id;
    }

    @Override
    public ReferenceSet<INode> getNodes() {
        return nodes;
    }

    @Override
    public int hashCode() {
        return id;
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
