package com.circulation.circulation_networks.network.nodes.machine_node;

import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.api.IMachineNodeTileEntity;
import lombok.Getter;
import lombok.Setter;

public class ConsumerNode extends MachineNode {

    @Getter
    @Setter
    private long maxEnergy;

    public ConsumerNode(IMachineNodeTileEntity tileEntity, double linkScope) {
        super(tileEntity, 0, linkScope);
    }

    @Override
    public IEnergyHandler.EnergyType getType() {
        return IEnergyHandler.EnergyType.RECEIVE;
    }
}