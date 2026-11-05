package com.circulation.circulation_networks.tiles.nodes;

import com.circulation.circulation_networks.api.node.NodeType;
import com.circulation.circulation_networks.network.nodes.LongDistanceRelayNode;
import com.circulation.circulation_networks.registry.CFNBlockEntityTypes;
import com.circulation.circulation_networks.registry.NodeTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.NotNull;

public final class BlockEntityLongDistanceRelayNode extends BaseNodeBlockEntity<LongDistanceRelayNode> {
    private CompoundTag savedLinks = new CompoundTag();

    public BlockEntityLongDistanceRelayNode(BlockPos pos, BlockState state) {
        super(CFNBlockEntityTypes.LONG_DISTANCE_RELAY_NODE, pos, state);
    }

    @Override
    public @NotNull NodeType<LongDistanceRelayNode> getNodeType() {
        return NodeTypes.LONG_DISTANCE_RELAY_NODE;
    }

    @Override
    protected void onNodeBound(@NotNull LongDistanceRelayNode node) {
        if (!node.hasPersistentState()) node.readLinkData(savedLinks);
    }

    @Override
    protected void loadAdditional(@NotNull ValueInput input) {
        super.loadAdditional(input);
        savedLinks = input.read("longDistanceRelay", CompoundTag.CODEC).orElseGet(CompoundTag::new);
    }

    @Override
    protected void saveAdditional(@NotNull ValueOutput output) {
        super.saveAdditional(output);
        output.store("longDistanceRelay", CompoundTag.CODEC, getNode() == null ? savedLinks.copy()
            : getNode().writeLinkData(new CompoundTag()));
    }
}
