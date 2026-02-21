package com.circulation.circulation_networks.network.nodes;

import com.circulation.circulation_networks.api.INodeTileEntity;
import com.circulation.circulation_networks.api.node.IChargingNode;

public final class ChargingNode extends Node implements IChargingNode {


    private final double chargingScope;
    private final double linkScope;

    public ChargingNode(INodeTileEntity tileEntity, double chargingScope, double linkScope) {
        super(tileEntity);
        this.chargingScope = chargingScope;
        this.linkScope = linkScope;
    }

    @Override
    public double getChargingScope() {
        return chargingScope;
    }

    @Override
    public double getLinkScope() {
        return linkScope;
    }

}
