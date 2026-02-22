package com.circulation.circulation_networks.tiles.nodes;

import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.network.nodes.ChargingNode;

public class TileEntityChargingTE extends BaseNodeTileEntity {

    @Override
    protected INode createNode() {
        return new ChargingNode(this, 5, 8);
    }

}
