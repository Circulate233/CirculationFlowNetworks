package com.circulation.circulation_networks.api.node;

import com.circulation.circulation_networks.api.IEnergyHandler;
import net.minecraft.util.math.BlockPos;

public interface IMachineNode extends IEnergySupplyNode {

    IEnergyHandler.EnergyType getType();

    default double getEnergyScope() {
        return 0;
    }

    default boolean supplyScopeCheck(BlockPos pos) {
        return this.getPos().equals(pos);
    }

}
