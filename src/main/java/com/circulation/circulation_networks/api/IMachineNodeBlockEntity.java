package com.circulation.circulation_networks.api;

import com.circulation.circulation_networks.api.node.IMachineNode;
import org.jetbrains.annotations.NotNull;

public interface IMachineNodeBlockEntity extends INodeBlockEntity {

    @NotNull
    IMachineNode getNode();

    @NotNull
    IEnergyHandler getEnergyHandler();
}
