package com.circulation.circulation_networks.tiles.nodes;

import com.circulation.circulation_networks.api.node.NodeType;
import com.circulation.circulation_networks.network.nodes.PortNode;
import com.circulation.circulation_networks.registry.CFNBlockEntityTypes;
import com.circulation.circulation_networks.registry.NodeTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public final class BlockEntityPortNode extends BaseNodeBlockEntity<PortNode> {

    public BlockEntityPortNode(BlockPos pos, BlockState state) {
        super(CFNBlockEntityTypes.PORT_NODE, pos, state);
    }

    @Override
    public @NotNull NodeType<? extends PortNode> getNodeType() {
        return NodeTypes.PORT_NODE;
    }
}
