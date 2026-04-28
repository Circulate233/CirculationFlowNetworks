package com.circulation.circulation_networks.client.render;

public final class ClientAnimationTicker {

    private static long ticks;

    private ClientAnimationTicker() {
    }

    public static void tick() {
        ticks++;
    }

    public static long ticks() {
        return ticks;
    }

    public static void reset() {
        ticks = 0L;
    }
}
