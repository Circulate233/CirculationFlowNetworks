package com.circulation.circulation_networks.network.nodes.machine_node;


import com.circulation.circulation_networks.api.IMachineNodeTileEntity;
import com.circulation.circulation_networks.api.node.IMachineNode;
import com.circulation.circulation_networks.network.nodes.Node;

public abstract class MachineNode extends Node implements IMachineNode {

    public MachineNode(IMachineNodeTileEntity tileEntity) {
        super(tileEntity);
    }

    @Override
    public double getEnergyScope() {
        return 4;
    }

}
