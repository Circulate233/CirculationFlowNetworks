package com.circulation.circulation_networks.tiles.nodes;

import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.network.nodes.InductionNode;

public class TileEntityEnergyInductionTower extends BaseNodeTileEntity {

    @Override
    protected INode createNode() {
        return new InductionNode(this, 8, 12);
    }

}
