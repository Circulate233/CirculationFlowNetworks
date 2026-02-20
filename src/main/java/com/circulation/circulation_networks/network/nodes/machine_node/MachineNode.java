package com.circulation.circulation_networks.network.nodes.machine_node;


import com.circulation.circulation_networks.api.IMachineNodeTileEntity;
import com.circulation.circulation_networks.api.node.IMachineNode;
import com.circulation.circulation_networks.network.nodes.Node;
import net.minecraft.util.math.BlockPos;

public abstract class MachineNode extends Node implements IMachineNode {

    public MachineNode(IMachineNodeTileEntity tileEntity) {
        super(tileEntity);
    }

    @Override
    public final double getEnergyScope() {
        return IMachineNode.super.getEnergyScope();
    }

    @Override
    public final boolean supplyScopeCheck(BlockPos pos) {
        return IMachineNode.super.supplyScopeCheck(pos);
    }

}
