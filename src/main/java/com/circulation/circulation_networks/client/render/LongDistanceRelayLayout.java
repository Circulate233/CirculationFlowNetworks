package com.circulation.circulation_networks.client.render;

/** All coordinates are relative to the bottom (node) block. */
public final class LongDistanceRelayLayout {
    public static final int HEIGHT = 3;

    // The original hub crystal spans Y = -1..2; translate it into the three-block column.
    public static final float CRYSTAL_Y_OFFSET = 1.0F;
    public static final float MIDDLE_RING_Y_OFFSET = 1.0F;
    // The original ring extends 0.28125 blocks from its center. This travel leaves
    // 0.75 blocks (one quarter of the structure height) clear at both outer ends.
    private static final float MAX_RING_TRAVEL = 0.46875F;
    private static final int MOTION_PERIOD_TICKS = 120;

    private LongDistanceRelayLayout() {
    }

    /** The upper and lower halves separate from the middle, then return smoothly. */
    public static float ringTravel(long ticks, float partialTicks) {
        double phase = ((ticks % MOTION_PERIOD_TICKS) + partialTicks) * (2.0D * Math.PI / MOTION_PERIOD_TICKS);
        return (float) ((1.0D - Math.cos(phase)) * 0.5D * MAX_RING_TRAVEL);
    }

    public static int ringLightSampleY(float yOffset) {
        return Math.max(0, Math.min(HEIGHT - 1, (int) Math.floor(yOffset + 0.5F)));
    }
}
