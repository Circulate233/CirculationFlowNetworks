package com.circulation.circulation_networks.network.nodes;

import com.circulation.circulation_networks.api.IGrid;
import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.api.node.NodeContext;
import com.circulation.circulation_networks.api.node.NodeType;
import com.circulation.circulation_networks.manager.MachineBindingIndex;
import com.circulation.circulation_networks.math.Vec3d;
import com.circulation.circulation_networks.math.Vec3i;
import com.circulation.circulation_networks.registry.NodeTypes;
import com.circulation.circulation_networks.utils.BlockPosCompat;
import com.circulation.circulation_networks.utils.NbtCompat;
import com.circulation.circulation_networks.utils.WorldResolveCompat;
import it.unimi.dsi.fastutil.objects.Reference2DoubleMap;
import it.unimi.dsi.fastutil.objects.Reference2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import it.unimi.dsi.fastutil.objects.ReferenceSets;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Objects;

import static com.circulation.circulation_networks.utils.SideCompat.isServerWorld;

public class Node implements INode {

    private static final int MAX_CUSTOM_NAME_LENGTH = 32;
    private final NodeType<?> nodeType;
    private final BlockPos pos;
    private final Vec3d vec3d;
    private final WeakReference<Level> world;
    private final String dimensionKey;
    private final ReferenceSet<INode> neighbors = new ReferenceOpenHashSet<>();
    private final Reference2DoubleMap<INode> distanceMap = new Reference2DoubleOpenHashMap<>();
    private final double linkScope;
    private final double linkScopeSq;
    private final String visualId;
    @Nullable
    private String customName;
    private boolean active;
    private IGrid grid;

    public Node(CompoundTag nbt) {
        this(resolveNodeType(nbt), nbt);
    }

    public Node(NodeType<?> nodeType, CompoundTag nbt) {
        this.nodeType = nodeType;
        this.dimensionKey = NbtCompat.getStringOr(nbt, "dim", "minecraft:overworld");
        this.world = new WeakReference<>(WorldResolveCompat.resolveWorld(dimensionKey));
        this.pos = BlockPosCompat.fromLong(NbtCompat.getLongOr(nbt, "pos", 0L));
        this.vec3d = Vec3d.ofCenter(new Vec3i(pos.getX(), pos.getY(), pos.getZ()));
        this.linkScope = NbtCompat.getDoubleOr(nbt, "linkScope", 0.0D);
        this.linkScopeSq = linkScope * linkScope;
        this.visualId = normalizeVisualId(NbtCompat.contains(nbt, "visualId") ? NbtCompat.getStringOr(nbt, "visualId", "") : null);
        this.customName = normalizeCustomName(NbtCompat.contains(nbt, "customName") ? NbtCompat.getStringOr(nbt, "customName", "") : null);
    }

    public Node(NodeType<?> nodeType, NodeContext context, double linkScope) {
        this.nodeType = nodeType;
        this.dimensionKey = WorldResolveCompat.getDimensionId(context.getWorld());
        this.world = new WeakReference<>(context.getWorld());
        this.pos = context.getPos();
        this.vec3d = Vec3d.ofCenter(new Vec3i(pos.getX(), pos.getY(), pos.getZ()));
        this.linkScope = linkScope;
        this.linkScopeSq = linkScope * linkScope;
        this.visualId = normalizeVisualId(context.getVisualId());
        this.customName = normalizeCustomName(context.getDefaultName());
    }

    @Nullable
    private static String normalizeCustomName(@Nullable String customName) {
        if (customName == null) {
            return null;
        }

        String trimmedName = customName.trim();
        if (trimmedName.length() > MAX_CUSTOM_NAME_LENGTH) {
            trimmedName = trimmedName.substring(0, MAX_CUSTOM_NAME_LENGTH);
        }
        return trimmedName.isEmpty() ? null : trimmedName;
    }

    @NotNull
    private static String normalizeVisualId(@Nullable String visualId) {
        if (visualId == null) {
            return "";
        }
        String trimmedId = visualId.trim();
        return trimmedId.isEmpty() ? "" : trimmedId;
    }

    private static NodeType<?> resolveNodeType(CompoundTag nbt) {
        NodeType<?> resolved = NodeTypes.getById(NbtCompat.getStringOr(nbt, "type", ""));
        return resolved != null ? resolved : NodeTypes.RELAY_NODE;
    }

    public @NotNull BlockPos getPos() {
        return pos;
    }

    public @NotNull Vec3d getVec3d() {
        return vec3d;
    }

    @Override
    public @NotNull NodeType<?> getNodeType() {
        return nodeType;
    }

    @Override
    public @NotNull String getVisualId() {
        return visualId;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        if (this.active == active) {
            return;
        }
        boolean oldActive = this.active;
        IGrid oldGrid = grid;
        if (isServerWorld(getWorld())) {
            MachineBindingIndex.INSTANCE.onNodeActiveChanged(this, oldActive, active, oldGrid);
        }
        this.active = active;
        if (!active) {
            setGrid(null);
            clearNeighbors();
        }
    }

    @Override
    public CompoundTag serialize() {
        var nbt = new CompoundTag();
        NbtCompat.putString(nbt, "type", nodeType.id());
        NbtCompat.putLong(nbt, "pos", BlockPosCompat.toLong(pos));
        NbtCompat.putString(nbt, "dim", dimensionKey);
        var list = new ListTag();
        neighbors.forEach(neighbor -> list.add(LongTag.valueOf(BlockPosCompat.toLong(neighbor.getPos()))));
        nbt.put("neighbors", list);
        NbtCompat.putDouble(nbt, "linkScope", linkScope);
        if (!visualId.isEmpty()) {
            NbtCompat.putString(nbt, "visualId", visualId);
        }
        if (customName != null) {
            NbtCompat.putString(nbt, "customName", customName);
        }
        return nbt;
    }

    public @NotNull Level getWorld() {
        var cachedWorld = world.get();
        if (cachedWorld != null) {
            return cachedWorld;
        }
        var resolvedWorld = WorldResolveCompat.resolveWorld(dimensionKey);
        if (resolvedWorld != null) {
            return resolvedWorld;
        }
        throw new IllegalStateException("World is null");
    }

    @Override
    public @NotNull String getDimensionId() {
        return dimensionKey;
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
        distanceMap.removeDouble(neighbor);
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
        IGrid oldGrid = this.grid;
        if (oldGrid == grid) {
            return;
        }
        if (isServerWorld(getWorld())) {
            MachineBindingIndex.INSTANCE.onNodeGridChanged(this, oldGrid, grid);
        }
        this.grid = grid;
    }

    @Override
    public @Nullable String getCustomName() {
        return customName;
    }

    @Override
    public void setCustomName(@Nullable String customName) {
        String normalizedName = normalizeCustomName(customName);
        if (Objects.equals(this.customName, normalizedName)) {
            return;
        }
        this.customName = normalizedName;
        if (grid != null) {
            grid.markSnapshotDirty();
        }
    }

    @Override
    public double distanceSq(INode node) {
        if (distanceMap.containsKey(node)) {
            return distanceMap.getDouble(node);
        }
        return this.distanceSq(node.getVec3d());
    }

    @Override
    public double getLinkScope() {
        return linkScope;
    }

    @Override
    public double getLinkScopeSq() {
        return linkScopeSq;
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
        boolean canConnectAtoB = dist <= this.getLinkScopeSq();
        boolean canConnectBtoA = dist <= node.getLinkScopeSq();

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
