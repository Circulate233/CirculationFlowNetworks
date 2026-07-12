package com.circulation.circulation_networks.network.nodes;

import com.circulation.circulation_networks.api.IGrid;
import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.api.node.NodeContext;
import com.circulation.circulation_networks.api.node.NodeType;
import com.circulation.circulation_networks.manager.MachineBindingIndex;
import com.circulation.circulation_networks.math.Vec3d;
import com.circulation.circulation_networks.math.Vec3i;
import it.unimi.dsi.fastutil.objects.Reference2DoubleMap;
import it.unimi.dsi.fastutil.objects.Reference2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import it.unimi.dsi.fastutil.objects.ReferenceSets;
//~ mc_imports
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
//? if <1.20
import net.minecraftforge.common.DimensionManager;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import java.lang.ref.WeakReference;
import java.util.Objects;

import static com.circulation.circulation_networks.utils.SideCompat.isServerWorld;

public class Node implements INode {

    private static final int MAX_CUSTOM_NAME_LENGTH = 32;
    private final NodeType<?> nodeType;
    private final BlockPos pos;
    private final Vec3d vec3d;
    //~ if >=1.20 '<World>' -> '<Level>' {
    private final WeakReference<World> world;
    //~}
    private final int dimensionId;
    //? if >=1.20
    //private final String dimensionKey;
    private final ReferenceSet<INode> neighbors = new ReferenceOpenHashSet<>();
    private final Reference2DoubleMap<INode> distanceMap = new Reference2DoubleOpenHashMap<>();
    private final double linkScope;
    private final double linkScopeSq;
    private final String visualId;
    @Nullable
    private String customName;
    private boolean active;
    private IGrid grid;

    //~ if >=1.20 'NBTTagCompound' -> 'CompoundTag' {
    //~ if >=1.20 'BlockPos.fromLong(' -> 'BlockPos.of(' {
    //~ if >=1.20 '.hasKey(' -> '.contains(' {
    public Node(NBTTagCompound nbt) {
        this(resolveNodeType(nbt), nbt);
    }

    public Node(NodeType<?> nodeType, NBTTagCompound nbt) {
        this.nodeType = nodeType;
        //? if <1.20 {
        this.dimensionId = nbt.getInteger("dim");
        this.world = new WeakReference<>(resolveWorld(dimensionId));
        //?} else {
        /*this.dimensionKey = nbt.getString("dim");
        //~ if >=1.21 'new net.minecraft.resources.ResourceLocation(' -> 'net.minecraft.resources.ResourceLocation.parse(' {
        this.dimensionId = new net.minecraft.resources.ResourceLocation(dimensionKey).hashCode();
        //~}
        this.world = new WeakReference<>(resolveWorld(dimensionKey));
        *///?}
        this.pos = BlockPos.fromLong(nbt.getLong("pos"));
        this.vec3d = Vec3d.ofCenter(new Vec3i(pos.getX(), pos.getY(), pos.getZ()));
        this.linkScope = nbt.getDouble("linkScope");
        this.linkScopeSq = linkScope * linkScope;
        this.visualId = normalizeVisualId(nbt.hasKey("visualId") ? nbt.getString("visualId") : null);
        this.customName = normalizeCustomName(nbt.hasKey("customName") ? nbt.getString("customName") : null);
    }
    //~}
    //~}
    //~}

    public Node(NodeType<?> nodeType, NodeContext context, double linkScope) {
        this.nodeType = nodeType;
        this.dimensionId = getDimensionId(context.getWorld());
        //? if >=1.20
        //this.dimensionKey = context.getWorld().dimension().location().toString();
        this.world = new WeakReference<>(context.getWorld());
        this.pos = context.getPos();
        this.vec3d = Vec3d.ofCenter(new Vec3i(pos.getX(), pos.getY(), pos.getZ()));
        this.linkScope = linkScope;
        this.linkScopeSq = linkScope * linkScope;
        this.visualId = normalizeVisualId(context.getVisualId());
        this.customName = normalizeCustomName(context.getDefaultName());
    }

    //? if <1.20 {
    private static World resolveWorld(int dimensionId) {
        return DimensionManager.getWorld(dimensionId);
    }
    //?} else if <1.21 {
    /*private static Level resolveWorld(String dimensionKey) {
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        return server.getLevel(
            net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                new net.minecraft.resources.ResourceLocation(dimensionKey)
            )
        );
    }
    *///?} else {
    /*private static Level resolveWorld(String dimensionKey) {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        return server.getLevel(
            net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                net.minecraft.resources.ResourceLocation.parse(dimensionKey)
            )
        );
    }
    *///?}

    //~ if >=1.20 'World ' -> 'Level ' {
    //~ if >=1.20 '.provider.getDimension()' -> '.dimension().location().hashCode()' {
    private static int getDimensionId(World world) {
        return world.provider.getDimension();
    }
    //~}
    //~}

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

    //~ if >=1.20 'NBTTagCompound' -> 'CompoundTag' {
    private static NodeType<?> resolveNodeType(NBTTagCompound nbt) {
        NodeType<?> resolved = com.circulation.circulation_networks.registry.NodeTypes.getById(nbt.getString("type"));
        return resolved != null ? resolved : com.circulation.circulation_networks.registry.NodeTypes.RELAY_NODE;
    }
    //~}

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

    //~ if >=1.20 'NBTTagCompound' -> 'CompoundTag' {
    //~ if >=1.20 '.toLong()' -> '.asLong()' {
    //~ if >=1.20 'NBTTagList' -> 'ListTag' {
    //~ if >=1.20 '.appendTag(new NBTTagLong(' -> '.add(LongTag.valueOf(' {
    //~ if >=1.20 '.set' -> '.put' {
    //~ if >=1.20 '.setTag("nei' -> '.put("nei' {
    @Override
    public NBTTagCompound serialize() {
        var nbt = new NBTTagCompound();
        nbt.setString("type", nodeType.id());
        nbt.setLong("pos", pos.toLong());
        //? if <1.20 {
        nbt.setInteger("dim", dimensionId);
        //?} else {
        /*nbt.setString("dim", dimensionKey);
         *///?}
        var list = new NBTTagList();
        neighbors.forEach(neighbor -> list.appendTag(new NBTTagLong(neighbor.getPos().toLong())));
        nbt.setTag("neighbors", list);
        nbt.setDouble("linkScope", linkScope);
        if (!visualId.isEmpty()) {
            nbt.setString("visualId", visualId);
        }
        if (customName != null) {
            nbt.setString("customName", customName);
        }
        return nbt;
    }
    //~}
    //~}
    //~}
    //~}
    //~}
    //~}

    //~ if >=1.20 ' World ' -> ' Level ' {
    public @NotNull World getWorld() {
        var cachedWorld = world.get();
        if (cachedWorld != null) {
            return cachedWorld;
        }
        //? if <1.20 {
        var resolvedWorld = resolveWorld(dimensionId);
        //?} else {
        /*var resolvedWorld = resolveWorld(dimensionKey);
         *///?}
        if (resolvedWorld != null) {
            return resolvedWorld;
        }
        throw new IllegalStateException("World is null");
    }
    //~}

    @Override
    public int getDimensionId() {
        return dimensionId;
    }

    @Override
    public @NotNull String getSerializedDimensionKey() {
        //? if <1.20 {
        return Integer.toString(dimensionId);
        //?} else {
        /*return dimensionKey;
         *///?}
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
            return distanceMap.get(node);
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
