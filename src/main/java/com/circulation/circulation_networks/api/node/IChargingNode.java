package com.circulation.circulation_networks.api.node;

import net.minecraft.util.math.BlockPos;

/**
 * 标识符，确定节点可用于向玩家传输能量
 */
public interface IChargingNode extends INode {

    double getChargingScope();

    default boolean chargingScopeCheck(BlockPos pos) {
        return this.distanceSq(pos) <= getChargingScope() * getChargingScope();
    }

}
