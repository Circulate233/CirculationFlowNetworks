package com.circulation.circulation_networks.blocks.nodes;

import com.circulation.circulation_networks.tiles.nodes.TileEntityChargingTE;

public final class BlockChargingTower extends BaseNodeBlock {

    public BlockChargingTower() {
        super("charging_tower");
        this.setNodeTileClass(TileEntityChargingTE.class);
    }

}
