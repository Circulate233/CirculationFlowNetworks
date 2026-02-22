package com.circulation.circulation_networks.utils;

import com.circulation.circulation_networks.api.node.IMachineNode;
import com.circulation.circulation_networks.proxy.CommonProxy;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.tileentity.TileEntity;

public class CirculationEnergy {

    private final IMachineNode node;
    @Setter
    @Getter
    private long energy;

    private CirculationEnergy(TileEntity te) {
        node = (IMachineNode) te.getCapability(CommonProxy.nodeCapability, null);
    }

    public static CirculationEnergy create(TileEntity te) {
        if (te.getCapability(CommonProxy.nodeCapability, null) == null) return null;
        return new CirculationEnergy(te);
    }

    public long extractEnergy(long amount, boolean simulate) {
        var o = Math.min(canExtractValue(), amount);
        if (!simulate) energy -= o;
        return o;
    }

    public long receiveEnergy(long amount, boolean simulate) {
        var i = Math.min(canReceiveValue(), amount);
        if (!simulate) energy += i;
        return i;
    }

    public long canExtractValue() {
        return energy;
    }

    public long canReceiveValue() {
        return node.getMaxEnergy() - energy;
    }
}
