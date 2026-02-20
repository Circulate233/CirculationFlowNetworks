package com.circulation.circulation_networks.utils;

import lombok.Getter;
import lombok.Setter;

public class CirculationEnergy {

    @Getter
    private final long maxEnergy;
    @Setter
    @Getter
    private long energy;

    public CirculationEnergy(long maxEnergy) {
        this.maxEnergy = maxEnergy;
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
        return maxEnergy - energy;
    }
}
