package com.circulation.circulation_networks.api;

import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.api.node.NodeType;
//~ mc_imports
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

public interface INodeBlockEntity<N extends INode> {

    @NotNull
    N getNode();

    /**
     * 返回BlockEntity对应的NodeType
     * 这个返回值必须稳定，不能因为世界状态等进行修改
     * Returns the stable node type for this block entity.
     * The value must not depend on world state, position, or runtime mutation.
     */
    @NotNull
    NodeType<? extends N> getNodeType();

    @NotNull
    BlockPos getNodePos();

    //~ if >=1.20 'World ' -> 'Level ' {
    World getNodeWorld();
    //~}

    void nodeValidate();

    void nodeInvalidate();

    void syncNodeAfterNetworkInit();
}
