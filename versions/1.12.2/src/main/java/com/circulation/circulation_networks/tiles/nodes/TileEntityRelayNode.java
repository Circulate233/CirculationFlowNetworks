package com.circulation.circulation_networks.tiles.nodes;

import com.circulation.circulation_networks.api.node.NodeType;
import com.circulation.circulation_networks.network.nodes.Node;
import com.circulation.circulation_networks.registry.NodeTypes;
import org.jetbrains.annotations.NotNull;

public final class TileEntityRelayNode extends BaseNodeTileEntity<Node> {

    @Override
    public @NotNull NodeType<? extends Node> getNodeType() {
        return NodeTypes.RELAY_NODE;
    }

}
