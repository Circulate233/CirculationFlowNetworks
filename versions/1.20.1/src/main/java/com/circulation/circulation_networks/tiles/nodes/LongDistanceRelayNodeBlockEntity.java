package com.circulation.circulation_networks.tiles.nodes;

import com.circulation.circulation_networks.api.node.NodeType;
import com.circulation.circulation_networks.network.nodes.LongDistanceRelayNode;
import com.circulation.circulation_networks.registry.CFNBlockEntityTypes;
import com.circulation.circulation_networks.registry.NodeTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public final class LongDistanceRelayNodeBlockEntity extends BaseNodeBlockEntity<LongDistanceRelayNode> {
    private CompoundTag savedLinks = new CompoundTag();

    public LongDistanceRelayNodeBlockEntity(BlockPos pos, BlockState state) {
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
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        savedLinks = tag.getCompound("longDistanceRelay").copy();
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("longDistanceRelay", getNode() == null ? savedLinks.copy()
            : getNode().writeLinkData(new CompoundTag()));
    }
}
