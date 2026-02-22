package com.circulation.circulation_networks.api.node;

import net.minecraft.util.math.BlockPos;

/**
 * 标识符，确定节点可用于与设备交互能量
 */
public interface IEnergySupplyNode extends INode {

    double getEnergyScope();

    default boolean supplyScopeCheck(BlockPos pos) {
        return this.distanceSq(pos) <= getEnergyScope() * getEnergyScope();
    }
}
