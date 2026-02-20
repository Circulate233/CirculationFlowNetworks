package com.circulation.circulation_networks.api.node;

import com.circulation.circulation_networks.api.IEnergyHandler;

/**
 * 标识符，机器节点
 */
public interface IMachineNode extends IEnergySupplyNode {

    IEnergyHandler.EnergyType getType();

    long getMaxEnergy();

}
