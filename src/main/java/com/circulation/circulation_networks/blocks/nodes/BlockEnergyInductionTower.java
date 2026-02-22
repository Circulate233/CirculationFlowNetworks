package com.circulation.circulation_networks.blocks.nodes;

import com.circulation.circulation_networks.tiles.nodes.TileEntityEnergyInductionTower;

public class BlockEnergyInductionTower extends BaseNodeBlock {

    public BlockEnergyInductionTower() {
        super("energy_induction_tower");
        this.setNodeTileClass(TileEntityEnergyInductionTower.class);
    }

}
