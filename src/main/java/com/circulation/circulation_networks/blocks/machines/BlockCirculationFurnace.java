package com.circulation.circulation_networks.blocks.machines;

import com.circulation.circulation_networks.tiles.machines.TileEntityCirculationFurnace;

public final class BlockCirculationFurnace extends BaseMachineNodeBlock {

    public BlockCirculationFurnace() {
        super("circulation_furnace");
        this.setNodeTileClass(TileEntityCirculationFurnace.class);
    }

    @Override
    public boolean hasGui() {
        return true;
    }
}
