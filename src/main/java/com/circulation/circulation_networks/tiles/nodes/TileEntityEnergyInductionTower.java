package com.circulation.circulation_networks.tiles.nodes;

import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.network.nodes.InductionNode;
import org.jetbrains.annotations.NotNull;

public class TileEntityEnergyInductionTower extends BaseNodeTileEntity {

    @Override
    protected @NotNull INode createNode() {
        return new InductionNode(this, 8, 12);
    }

}
