package com.circulation.circulation_networks.api.node;

import com.circulation.circulation_networks.api.IGrid;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import javax.annotation.Nonnull;

public interface INode {

    @Nonnull
    BlockPos getPos();

    @Nonnull
    Vec3d getVec3d();

    @Nonnull
    World getWorld();

    boolean isActive();

    void setActive(boolean active);

    double getLinkScope();

    ReferenceSet<INode> getNeighbors();

    void addNeighbor(INode neighbor);

    void removeNeighbor(INode neighbor);

    void clearNeighbors();

    IGrid getGrid();

    void setGrid(IGrid grid);

    TileEntity getTileEntity();

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
