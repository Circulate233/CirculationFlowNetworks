package com.circulation.circulation_networks.api;

/** Structural external energy permissions, independent of a container being empty or full. */
public interface MekanismEnergyPermissions {
    boolean cfn_canExtractEnergy();
    boolean cfn_canInsertEnergy();
}
