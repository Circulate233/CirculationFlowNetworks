package com.circulation.circulation_networks.tiles.nodes;

import com.circulation.circulation_networks.api.node.NodeType;
import com.circulation.circulation_networks.network.nodes.ChargingNode;
import com.circulation.circulation_networks.registry.CFNBlockEntityTypes;
import com.circulation.circulation_networks.registry.NodeTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public final class BlockEntityChargingNode extends BaseNodeBlockEntity<ChargingNode> {

    public BlockEntityChargingNode(BlockPos pos, BlockState state) {
        super(CFNBlockEntityTypes.CHARGING_NODE, pos, state);
    }

    @Override
    public @NotNull NodeType<? extends ChargingNode> getNodeType() {
        return NodeTypes.CHARGING_NODE;
    }
}
