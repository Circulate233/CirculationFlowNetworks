package com.circulation.circulation_networks.pocket;

import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.client.render.PocketNodeModelCache;
import com.circulation.circulation_networks.registry.PocketNodeItems;
import com.circulation.circulation_networks.utils.Functions;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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

    public @Nullable INode getNode(Level world) {
        if (node == null || node.getWorld() != world || node.getNodeType() != record.nodeType()) {
            try {
                node = Functions.createNode(record.nodeType(), record.createNodeContext(world));
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
