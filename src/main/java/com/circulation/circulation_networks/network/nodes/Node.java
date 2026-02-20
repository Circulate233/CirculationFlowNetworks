package com.circulation.circulation_networks.network.nodes;

import com.circulation.circulation_networks.api.IGrid;
import com.circulation.circulation_networks.api.INodeTileEntity;
import com.circulation.circulation_networks.api.node.INode;
import it.unimi.dsi.fastutil.objects.Reference2DoubleMap;
import it.unimi.dsi.fastutil.objects.Reference2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import it.unimi.dsi.fastutil.objects.ReferenceSets;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import java.lang.ref.WeakReference;

public abstract class Node implements INode {

    @Getter
    private final BlockPos pos;
    private final WeakReference<World> world;
    private final ReferenceSet<INode> neighbors = new ReferenceOpenHashSet<>();
    private final Reference2DoubleMap<INode> distanceMap = new Reference2DoubleOpenHashMap<>();
    @Getter
    private boolean active;
    private IGrid grid;
    @Getter
    @Nonnull
    private INode parent = this;
    @Getter
    @Setter
    private int rank;

    public Node(INodeTileEntity tileEntity) {
        world = new WeakReference<>(tileEntity.getNodeWorld());
        pos = tileEntity.getNodePos();
    }

    public World getWorld() {
        return world.get();
    }

    @Override
    public void setParent(@Nonnull INode parent) {
        if (parent == null) {
            throw new NullPointerException("parent cannot be null");
        }
        this.parent = parent;
    }

    public void setActive(boolean active) {
        this.active = active;
        if (!active) {
            parent = this;
            grid = null;
            clearNeighbors();
        }
    }

    @Override
    public ReferenceSet<INode> getNeighbors() {
        return ReferenceSets.unmodifiable(neighbors);
    }

    @Override
    public void addNeighbor(INode neighbor) {
        if (neighbor == null || !neighbor.isActive()) return;
        neighbors.add(neighbor);
        distanceMap.put(neighbor, distance(neighbor));
    }

    @Override
    public void removeNeighbor(INode neighbor) {
        if (neighbor == null) return;
        neighbors.remove(neighbor);
        distanceMap.remove(neighbor);
    }

    @Override
    public void clearNeighbors() {
        neighbors.clear();
        distanceMap.clear();
    }

    @Override
    public IGrid getGrid() {
        return grid;
    }

    @Override
    public void setGrid(IGrid grid) {
        this.grid = grid;
    }

    @Override
    public TileEntity getTileEntity() {
        var world = this.world.get();
        if (world != null) {
            var te = world.getTileEntity(pos);
            if (te instanceof INodeTileEntity) {
                return te;
            }
        }
        throw new NullPointerException();
    }

    @Override
    public double distance(INode node) {
        if (distanceMap.containsKey(node)) {
            return distanceMap.get(node);
        }
        return this.distance(node.getPos());
    }

    @Override
    public double distance(BlockPos node) {
        return this.getPos().getDistance(node.getX(), node.getY(), node.getZ());
    }

    @Override
    public final LinkType linkScopeCheck(INode node) {
        var dist = this.distance(node);
        boolean canConnectAtoB = dist <= this.getLinkScope();
        boolean canConnectBtoA = dist <= node.getLinkScope();

        if (canConnectAtoB && canConnectBtoA) {
            return LinkType.DOUBLY;
        } else if (canConnectAtoB) {
            return LinkType.A_TO_B;
        } else if (canConnectBtoA) {
            return LinkType.B_TO_A;
        }
        return LinkType.DISCONNECT;
    }

}
