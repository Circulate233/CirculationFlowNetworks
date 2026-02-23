package com.circulation.circulation_networks.tiles.machines;

import com.circulation.circulation_networks.api.node.IMachineNode;
import com.circulation.circulation_networks.network.nodes.machine_node.GeneratorNode;

public final class TileEntityWindTurbine extends BaseMachineNodeTileEntity {

    private static final long energy = 20;

    @Override
    protected IMachineNode createNode() {
        return new GeneratorNode(this, 5, 5);
    }

    @Override
    public void serverUpdate() {
        super.serverUpdate();
        addEnergy(energy, false);
    }

    @Override
    protected void onValidate() {
        super.onValidate();
        setMaxEnergy(energy);
    }
}