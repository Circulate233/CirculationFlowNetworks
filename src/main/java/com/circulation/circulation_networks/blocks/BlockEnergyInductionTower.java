package com.circulation.circulation_networks.blocks;

import com.circulation.circulation_networks.blocks.tiles.TileEntityEnergyInductionTower;

public class BlockEnergyInductionTower extends BaseNodeBlock {

    public BlockEnergyInductionTower() {
        super("energy_induction_tower");
        this.setNodeTileClass(TileEntityEnergyInductionTower.class);
    }

}
