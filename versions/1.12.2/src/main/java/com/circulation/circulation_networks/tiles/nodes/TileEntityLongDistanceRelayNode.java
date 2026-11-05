package com.circulation.circulation_networks.tiles.nodes;

import com.circulation.circulation_networks.api.node.NodeType;
import com.circulation.circulation_networks.network.nodes.LongDistanceRelayNode;
import com.circulation.circulation_networks.registry.NodeTypes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.AxisAlignedBB;
import org.jetbrains.annotations.NotNull;

public final class TileEntityLongDistanceRelayNode extends BaseNodeTileEntity<LongDistanceRelayNode> {
    private NBTTagCompound savedLinks = new NBTTagCompound();

    @Override
    public @NotNull NodeType<LongDistanceRelayNode> getNodeType() {
        return NodeTypes.LONG_DISTANCE_RELAY_NODE;
    }

    @Override
    protected void onNodeBound(@NotNull LongDistanceRelayNode node) {
        if (!node.hasPersistentState()) node.readLinkData(savedLinks);
    }

    @Override
    public void readFromNBT(@NotNull NBTTagCompound tag) {
        super.readFromNBT(tag);
        savedLinks = tag.getCompoundTag("longDistanceRelay").copy();
    }

    @Override
    public @NotNull NBTTagCompound writeToNBT(@NotNull NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setTag("longDistanceRelay", getNode() == null ? savedLinks.copy()
            : getNode().writeLinkData(new NBTTagCompound()));
        return tag;
    }

    @Override
    public @NotNull AxisAlignedBB getRenderBoundingBox() {
        // Full-size hub rings extend beyond the one-block-wide collision column.
        return new AxisAlignedBB(pos.add(-1, 0, -1), pos.add(2, 3, 2));
    }
}
