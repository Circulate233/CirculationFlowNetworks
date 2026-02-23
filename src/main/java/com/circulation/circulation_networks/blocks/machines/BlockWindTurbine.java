package com.circulation.circulation_networks.blocks.machines;

import com.circulation.circulation_networks.tiles.machines.TileEntityWindTurbine;

public final class BlockWindTurbine extends BaseMachineNodeBlock {

    public BlockWindTurbine() {
        super("wind_turbine");
        setNodeTileClass(TileEntityWindTurbine.class);
    }

    @Override
    public boolean hasGui() {
        return false;
    }
}
