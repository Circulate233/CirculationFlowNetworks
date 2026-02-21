package com.circulation.circulation_networks.network.nodes;

import com.circulation.circulation_networks.api.INodeTileEntity;
import com.circulation.circulation_networks.api.node.IEnergySupplyNode;

public final class InductionNode extends Node implements IEnergySupplyNode {

    private final double energyScope;
    private final double linkScope;

    public InductionNode(INodeTileEntity tileEntity, double energyScope, double linkScope) {
        super(tileEntity);
        this.energyScope = energyScope;
        this.linkScope = linkScope;
    }

    @Override
    public double getEnergyScope() {
        return energyScope;
    }

    @Override
    public double getLinkScope() {
        return linkScope;
    }

}
