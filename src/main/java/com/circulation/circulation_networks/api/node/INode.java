package com.circulation.circulation_networks.api.node;

import com.circulation.circulation_networks.api.IGrid;
import com.circulation.circulation_networks.math.Vec3d;
import com.circulation.circulation_networks.utils.WorldResolveCompat;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface INode {

    @NotNull
    BlockPos getPos();

    @NotNull
    Vec3d getVec3d();

    @NotNull
    Level getWorld();

    @NotNull
    default String getDimensionId() {
        return WorldResolveCompat.getDimensionId(getWorld());
    }

    @NotNull
    NodeType<?> getNodeType();

    @NotNull
    String getVisualId();

    CompoundTag serialize();

    boolean isActive();

    void setActive(boolean active);

    double getLinkScope();

    double getLinkScopeSq();

    /** Whether this node initiates a link to the other node. */
    default boolean canLinkTo(INode other) {
        return getDimensionId().equals(other.getDimensionId()) && distanceSq(other) <= getLinkScopeSq();
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
