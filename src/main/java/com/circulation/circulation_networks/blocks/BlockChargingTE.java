package com.circulation.circulation_networks.blocks;

import com.circulation.circulation_networks.blocks.tiles.TileEntityChargingTE;

public class BlockChargingTE extends BaseNodeBlock {

    public BlockChargingTE() {
        super("block_charging");
        this.setNodeTileClass(TileEntityChargingTE.class);
    }

}
