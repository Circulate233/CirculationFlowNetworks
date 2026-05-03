package com.circulation.circulation_networks.tiles.nodes;

import com.circulation.circulation_networks.api.node.NodeType;
import com.circulation.circulation_networks.network.nodes.Node;
import com.circulation.circulation_networks.registry.CFNBlockEntityTypes;
import com.circulation.circulation_networks.registry.NodeTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public final class RelayNodeBlockEntity extends BaseNodeBlockEntity<Node> {

    public RelayNodeBlockEntity(BlockPos pos, BlockState state) {
        super(CFNBlockEntityTypes.RELAY_NODE, pos, state);
    }

    @Override
    public @NotNull NodeType<? extends Node> getNodeType() {
        return NodeTypes.RELAY_NODE;
    }
}
