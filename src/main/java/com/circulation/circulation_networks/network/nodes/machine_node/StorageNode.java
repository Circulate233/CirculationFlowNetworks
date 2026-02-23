package com.circulation.circulation_networks.network.nodes.machine_node;

import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.api.IMachineNodeTileEntity;
import lombok.Getter;
import lombok.Setter;

public final class StorageNode extends MachineNode {

    @Getter
    @Setter
    private long maxEnergy;

    public StorageNode(IMachineNodeTileEntity tileEntity, double energyScope, double linkScope) {
        super(tileEntity, energyScope, linkScope);
    }

    @Override
    public IEnergyHandler.EnergyType getType() {
        return IEnergyHandler.EnergyType.STORAGE;
    }
}