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
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;

public abstract class Node implements INode {

    @Getter
    private final BlockPos pos;
    @Getter
    private final Vec3d vec3d;
    private final WeakReference<World> world;
    private final ReferenceSet<INode> neighbors = new ReferenceOpenHashSet<>();
    private final Reference2DoubleMap<INode> distanceMap = new Reference2DoubleOpenHashMap<>();
    @Getter
    private boolean active;
    private IGrid grid;

    public Node(NBTTagCompound nbt) {
        world = new WeakReference<>(DimensionManager.getWorld(nbt.getInteger("dim")));
        pos = BlockPos.fromLong(nbt.getLong("pos"));
        vec3d = new Vec3d(pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d);
    }

    @Override
    public NBTTagCompound serialize() {
        var nbt = new NBTTagCompound();
        nbt.setString("name", this.getClass().getName());
        nbt.setLong("pos", pos.toLong());
        nbt.setInteger("dim", getWorld().provider.getDimension());
        var list = new NBTTagList();
        neighbors.forEach(neighbor -> list.appendTag(new NBTTagLong(neighbor.getPos().toLong())));
        nbt.setTag("neighbors", list);
        return nbt;
    }

    public Node(INodeTileEntity tileEntity) {
        world = new WeakReference<>(tileEntity.getNodeWorld());
        pos = tileEntity.getNodePos();
        vec3d = new Vec3d(pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d);
    }

    public @NotNull World getWorld() {
        var world = this.world.get();
        if (world != null) {
            return world;
        }
        throw new IllegalStateException("World is null");
    }

    public void setActive(boolean active) {
        this.active = active;
        if (!active) {
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
        distanceMap.put(neighbor, distanceSq(neighbor));
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
    public double distanceSq(INode node) {
        if (distanceMap.containsKey(node)) {
            return distanceMap.get(node);
        }
        return this.distanceSq(node.getVec3d());
    }

    @Override
    public double distanceSq(BlockPos node) {
        return this.vec3d.squareDistanceTo(node.getX() + 0.5d, node.getY() + 0.5d, node.getZ() + 0.5d);
    }

    @Override
    public double distanceSq(Vec3d pos) {
        return this.vec3d.squareDistanceTo(pos);
    }

    @Override
    public final LinkType linkScopeCheck(INode node) {
        var dist = this.distanceSq(node);
        boolean canConnectAtoB = dist <= this.getLinkScope() * this.getLinkScope();
        boolean canConnectBtoA = dist <= node.getLinkScope() * node.getLinkScope();

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
