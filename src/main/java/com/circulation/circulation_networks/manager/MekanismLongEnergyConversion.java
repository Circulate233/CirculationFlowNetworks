package com.circulation.circulation_networks.manager;

/** Whole native packets for Mekanism's integer joules: 2 FE = 5 J. */
public final class MekanismLongEnergyConversion {
    public static final long MAX_FE = Long.MAX_VALUE / 5L * 2L;
    private MekanismLongEnergyConversion() {}

    public static long toJoules(long fe) {
        return Math.max(0L, Math.min(MAX_FE, fe)) / 2L * 5L;
    }

    public static long toFe(long joules) {
        return Math.max(0L, joules) / 5L * 2L;
    }

    public static long wholeJoules(long joules) {
        return Math.max(0L, joules) / 5L * 5L;
    }
}
