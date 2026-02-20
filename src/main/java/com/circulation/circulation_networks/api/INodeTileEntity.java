package com.circulation.circulation_networks.api;

import com.circulation.circulation_networks.api.node.INode;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nonnull;

public interface INodeTileEntity {

    @Nonnull
    INode getNode();

    @Nonnull
    BlockPos getNodePos();

    World getNodeWorld();

    default double getEnergyScope() {
        return 0;
    }

    double getLinkScope();

}
