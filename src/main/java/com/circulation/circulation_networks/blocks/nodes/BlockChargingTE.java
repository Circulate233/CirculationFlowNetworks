package com.circulation.circulation_networks.blocks.nodes;

import com.circulation.circulation_networks.tiles.nodes.TileEntityChargingTE;

public class BlockChargingTE extends BaseNodeBlock {

    public BlockChargingTE() {
        super("block_charging");
        this.setNodeTileClass(TileEntityChargingTE.class);
    }

}
