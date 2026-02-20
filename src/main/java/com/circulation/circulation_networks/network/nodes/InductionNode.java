package com.circulation.circulation_networks.network.nodes;

import com.circulation.circulation_networks.api.node.IEnergySupplyNode;
import com.circulation.circulation_networks.blocks.tiles.TileEntityEnergyInductionTower;

public final class InductionNode extends Node implements IEnergySupplyNode {

    public InductionNode(TileEntityEnergyInductionTower tileEntity) {
        super(tileEntity);
    }

    @Override
    public double getEnergyScope() {
        return 16;
    }

    @Override
    public double getLinkScope() {
        return 16;
    }

}
