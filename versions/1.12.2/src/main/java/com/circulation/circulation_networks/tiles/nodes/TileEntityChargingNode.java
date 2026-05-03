package com.circulation.circulation_networks.tiles.nodes;

import com.circulation.circulation_networks.api.node.NodeType;
import com.circulation.circulation_networks.network.nodes.ChargingNode;
import com.circulation.circulation_networks.registry.NodeTypes;
import org.jetbrains.annotations.NotNull;

public final class TileEntityChargingNode extends BaseNodeTileEntity<ChargingNode> {

    @Override
    public @NotNull NodeType<? extends ChargingNode> getNodeType() {
        return NodeTypes.CHARGING_NODE;
    }
}
