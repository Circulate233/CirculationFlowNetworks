package com.circulation.circulation_networks.api;

import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.api.node.NodeType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public interface INodeBlockEntity {

    @NotNull
    INode getNode();

    @NotNull
    NodeType<?> getNodeType();

    @NotNull
    BlockPos getNodePos();

    Level getNodeWorld();

    void nodeValidate();

    void nodeInvalidate();

    void syncNodeAfterNetworkInit();
}
