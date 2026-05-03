package com.circulation.circulation_networks.tiles.nodes;

import com.circulation.circulation_networks.api.API;
import com.circulation.circulation_networks.api.INodeBlockEntity;
import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.api.node.NodeContext;
import com.circulation.circulation_networks.api.node.NodeType;
import com.circulation.circulation_networks.manager.NetworkManager;
import com.circulation.circulation_networks.tiles.BaseCFNBlockEntity;
import com.circulation.circulation_networks.utils.Functions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.NotNull;

public abstract class BaseNodeBlockEntity<N extends INode> extends BaseCFNBlockEntity implements INodeBlockEntity {

    private N node;

    public BaseNodeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public @NotNull N getNode() {
        return node;
    }

    @Override
    @NotNull
    public abstract NodeType<? extends N> getNodeType();

    protected @NotNull NodeContext createNodeContext() {
        return NodeContext.fromWorld(level, worldPosition);
    }

    protected void onNodeBound(@NotNull N node) {
    }

    @Override
    public void nodeValidate() {
        if (level == null) return;
        if (node != null && node.isActive()) return;
        if (!level.isClientSide()) {
            if (!NetworkManager.INSTANCE.isInit()) return;
            onServerValidate();
        } else {
            onClientValidate();
        }
    }

    @Override
    public void nodeInvalidate() {
        if (node == null) return;
        if (level != null && !level.isClientSide()) {
            NetworkManager.INSTANCE.removeNode(node.getDimensionId(), node.getPos());
        } else {
            node.setActive(false);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
    }

    protected void onServerValidate() {
        var nodeType = getNodeType();
        INode existingNode = API.getNodeAt(level, worldPosition);

        if (nodeType.matches(existingNode)) {
            if (node != existingNode) {
                if (node != null) {
                    node.setActive(false);
                }
                node = castNode(nodeType.cast(existingNode));
            }
        } else {
            if (!nodeType.matches(node)) {
                if (existingNode != null) {
                    NetworkManager.INSTANCE.removeNode(existingNode.getDimensionId(), existingNode.getPos());
                }
                node = Functions.createNode(nodeType, createNodeContext());
            } else if (existingNode != null && existingNode != node) {
                NetworkManager.INSTANCE.removeNode(existingNode.getDimensionId(), existingNode.getPos());
            }
        }
        onNodeBound(node);
        node.setActive(true);
    }

    public final void syncNodeAfterNetworkInit() {
        if (level == null || level.isClientSide() || !NetworkManager.INSTANCE.isInit()) {
            return;
        }
        onServerValidate();
    }

    protected void onClientValidate() {
        var nodeType = getNodeType();
        if (node == null || !nodeType.matches(node)) {
            node = Functions.createNode(nodeType, createNodeContext());
        }
        onNodeBound(node);
        node.setActive(true);
    }

    @Override
    public @NotNull BlockPos getNodePos() {
        return this.worldPosition;
    }

    @Override
    public Level getNodeWorld() {
        return this.level;
    }

    @SuppressWarnings("unchecked")
    private N castNode(INode node) {
        return (N) node;
    }
}
