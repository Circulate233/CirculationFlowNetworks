package com.circulation.circulation_networks.network.nodes.machine_node;


import com.circulation.circulation_networks.api.IMachineNodeTileEntity;
import com.circulation.circulation_networks.api.node.IMachineNode;
import com.circulation.circulation_networks.network.nodes.Node;

public abstract class MachineNode extends Node implements IMachineNode {

    protected final double energyScope;
    protected final double linkScope;

    public MachineNode(IMachineNodeTileEntity tileEntity, double energyScope, double linkScope) {
        super(tileEntity);
        this.energyScope = energyScope;
        this.linkScope = linkScope;
    }

    @Override
    public double getLinkScope() {
        return linkScope;
    }

    @Override
    public double getEnergyScope() {
        return energyScope;
    }

}
