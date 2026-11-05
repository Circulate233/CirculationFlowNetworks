package com.circulation.circulation_networks.api.node;

import com.circulation.circulation_networks.api.IGrid;
import com.circulation.circulation_networks.math.Vec3d;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
//~ mc_imports
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface INode {

    @NotNull
    BlockPos getPos();

    @NotNull
    Vec3d getVec3d();

    //~ if >=1.20 'World ' -> 'Level ' {
    //~ if >=1.20 'NBTTagCompound ' -> 'CompoundTag ' {
    @NotNull
    World getWorld();

    //~ if >=1.20 '.provider.getDimension()' -> '.dimension().location().hashCode()' {
    default int getDimensionId() {
        return getWorld().provider.getDimension();
    }
    //~}

    @NotNull
    default String getSerializedDimensionKey() {
        //? if <1.20 {
        return Integer.toString(getWorld().provider.getDimension());
        //?} else {
        /*return getWorld().dimension().location().toString();
         *///?}
    }

    @NotNull
    NodeType<?> getNodeType();

    @NotNull
    String getVisualId();

    NBTTagCompound serialize();
    //~}
    //~}

    boolean isActive();

    void setActive(boolean active);

    double getLinkScope();

    double getLinkScopeSq();

    /** Whether this node initiates a link to the other node. */
    default boolean canLinkTo(INode other) {
        return getDimensionId() == other.getDimensionId() && distanceSq(other) <= getLinkScopeSq();
    }

    ReferenceSet<INode> getNeighbors();

    void addNeighbor(INode neighbor);

    void removeNeighbor(INode neighbor);

    void clearNeighbors();

    IGrid getGrid();

    void setGrid(IGrid grid);

    @Nullable
    String getCustomName();

    void setCustomName(@Nullable String customName);

    double distanceSq(INode node);

    double distanceSq(BlockPos node);

    double distanceSq(Vec3d node);

    LinkType linkScopeCheck(INode node);

    enum LinkType {
        DOUBLY,
        A_TO_B,
        B_TO_A,
        DISCONNECT
    }
}
