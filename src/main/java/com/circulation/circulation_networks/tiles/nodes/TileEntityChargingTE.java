package com.circulation.circulation_networks.tiles.nodes;

import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.network.nodes.ChargingNode;
import org.jetbrains.annotations.NotNull;

public class TileEntityChargingTE extends BaseNodeTileEntity {

    @Override
    protected @NotNull INode createNode() {
        return new ChargingNode(this, 5, 8);
    }

}
