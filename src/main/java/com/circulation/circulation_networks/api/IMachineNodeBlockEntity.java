package com.circulation.circulation_networks.api;

import com.circulation.circulation_networks.api.node.IMachineNode;
import org.jetbrains.annotations.NotNull;

public interface IMachineNodeBlockEntity extends INodeBlockEntity<IMachineNode> {

    @NotNull
    IEnergyHandler getEnergyHandler();
}