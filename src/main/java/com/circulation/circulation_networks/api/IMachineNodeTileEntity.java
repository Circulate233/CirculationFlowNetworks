package com.circulation.circulation_networks.api;

import com.circulation.circulation_networks.api.node.IMachineNode;
import com.circulation.circulation_networks.energy.handler.CEHandler;

import javax.annotation.Nonnull;

public interface IMachineNodeTileEntity extends INodeTileEntity {

    long getEnergy();

    long getMaxEnergy();

    @Nonnull
    IMachineNode getNode();

    @Nonnull
    CEHandler getCEHandler();
}
