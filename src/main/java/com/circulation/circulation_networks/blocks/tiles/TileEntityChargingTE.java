package com.circulation.circulation_networks.blocks.tiles;

import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.network.nodes.ChargingNode;

public class TileEntityChargingTE extends BaseNodeTileEntity {

    @Override
    protected INode createNode() {
        return new ChargingNode(this, 5, 8);
    }

}
