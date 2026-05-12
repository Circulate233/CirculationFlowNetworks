package com.circulation.circulation_networks.pocket;

import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.client.render.PocketNodeModelCache;
import com.circulation.circulation_networks.network.nodes.NodeFactory;
import com.circulation.circulation_networks.registry.PocketNodeItems;
//~ mc_imports
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public final class PocketNodeClientHost {

    private final PocketNodeRecord record;
    private final ItemStack renderStack;
    private INode node;
    private byte gui3dState = -1;

    public PocketNodeClientHost(PocketNodeRecord record) {
        this.record = record;
        this.renderStack = PocketNodeItems.createStack(record.nodeType());
    }

    public PocketNodeRecord getRecord() {
        return record;
    }

    public ItemStack getRenderStack() {
        return renderStack;
    }

    //~ if >=1.20 'World ' -> 'Level ' {
    public @Nullable INode getNode(World world) {
        if (node == null || node.getWorld() != world || node.getNodeType() != record.nodeType()) {
            try {
                node = NodeFactory.createNode(record.nodeType(), record.createNodeContext(world));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        if (record.customName() != null) {
            node.setCustomName(record.customName());
        }
        node.setActive(true);
        return node;
    }
    //~}

    public void invalidateNode() {
        if (node != null) {
            node.setActive(false);
            node = null;
        }
    }

    public boolean isGui3d() {
        if (gui3dState >= 0) {
            return gui3dState == 1;
        }
        boolean gui3d = PocketNodeModelCache.isGui3d(renderStack);
        gui3dState = (byte) (gui3d ? 1 : 0);
        return gui3d;
    }

}
