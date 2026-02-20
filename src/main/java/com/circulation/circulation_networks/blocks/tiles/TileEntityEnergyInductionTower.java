package com.circulation.circulation_networks.blocks.tiles;

import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.network.nodes.InductionNode;

public class TileEntityEnergyInductionTower extends BaseNodeTileEntity {

    @Override
    protected INode createNode() {
        return new InductionNode(this);
    }

}
