package com.circulation.circulation_networks.client.render;

public final class NodeRotationAnimation {

    private static final float DEGREES_PER_ROTATION = 360.0F;
    private static final float TICKS_PER_SECOND = 20.0F;
    private static final float RELAY_TOP_SPIRAL_DEGREES_PER_TICK = degreesPerTickForPeriodSeconds(10.0F);
    private static final float RELAY_CRYSTAL_DEGREES_PER_TICK = degreesPerTickForPeriodSeconds(40.0F);
    private static final float RELAY_RING_DEGREES_PER_TICK = degreesPerTickForPeriodSeconds(10.0F);
    private static final float HUB_CRYSTAL_DEGREES_PER_TICK = degreesPerTickForPeriodSeconds(20.0F);
    private static final float HUB_ROTATION_DEGREES_PER_TICK = degreesPerTickForPeriodSeconds(10.0F);
    private static final float PEDESTAL_ROTATION_DEGREES_PER_TICK = 1.25F;

    private static final float BOBBING_PERIOD_TICKS = 80.0F;
    private static final float BOBBING_ANGULAR_VELOCITY = (float) (2.0 * Math.PI / BOBBING_PERIOD_TICKS);
    private static final float BOBBING_AMPLITUDE = 0.75F / 16.0F;

    private static final float SIN_22_5 = 0.38268343F;
    private static final float COS_22_5 = 0.9238795F;

    private NodeRotationAnimation() {
    }

    public static float relayTopSpiralAngle(long clientTicks, float partialTicks) {
        return clockwiseDegrees(clientTicks, partialTicks, RELAY_TOP_SPIRAL_DEGREES_PER_TICK);
    }

    public static float relayCrystalAngle(long clientTicks, float partialTicks) {
        return clockwiseDegrees(clientTicks, partialTicks, RELAY_CRYSTAL_DEGREES_PER_TICK);
    }

    public static float relayBottomSpiralAngle(long clientTicks, float partialTicks) {
        return counterClockwiseDegrees(clientTicks, partialTicks, RELAY_TOP_SPIRAL_DEGREES_PER_TICK);
    }

    public static float hubCrystalAngle(long clientTicks, float partialTicks) {
        return clockwiseDegrees(clientTicks, partialTicks, HUB_CRYSTAL_DEGREES_PER_TICK);
    }

    public static float hubUpperRingAngle(long clientTicks, float partialTicks) {
        return clockwiseDegrees(clientTicks, partialTicks, HUB_ROTATION_DEGREES_PER_TICK);
    }

    public static float hubLowerRingAngle(long clientTicks, float partialTicks) {
        return counterClockwiseDegrees(clientTicks, partialTicks, HUB_ROTATION_DEGREES_PER_TICK);
    }

    public static float hubChannelTopAngle(long clientTicks, float partialTicks) {
        return clockwiseDegrees(clientTicks, partialTicks, HUB_ROTATION_DEGREES_PER_TICK);
    }

    public static float hubChannelMiddleAngle(long clientTicks, float partialTicks) {
        return counterClockwiseDegrees(clientTicks, partialTicks, HUB_ROTATION_DEGREES_PER_TICK);
    }

    public static float hubChannelBottomAngle(long clientTicks, float partialTicks) {
        return clockwiseDegrees(clientTicks, partialTicks, HUB_ROTATION_DEGREES_PER_TICK);
    }

    public static float hubChannelAerialAngle(long clientTicks, float partialTicks) {
        return clockwiseDegrees(clientTicks, partialTicks, HUB_ROTATION_DEGREES_PER_TICK);
    }

    public static float hubClockwisePluginAngle(long clientTicks, float partialTicks) {
        return clockwiseDegrees(clientTicks, partialTicks, HUB_ROTATION_DEGREES_PER_TICK);
    }

    public static float hubCounterClockwisePluginAngle(long clientTicks, float partialTicks) {
        return counterClockwiseDegrees(clientTicks, partialTicks, HUB_ROTATION_DEGREES_PER_TICK);
    }

    public static float hubClockwisePluginAngle(long clientTicks, float partialTicks, int rotationPeriodTicks) {
        return clockwiseDegrees(clientTicks, partialTicks, degreesPerTickForPeriodTicks(rotationPeriodTicks));
    }

    public static float hubCounterClockwisePluginAngle(long clientTicks, float partialTicks, int rotationPeriodTicks) {
        return counterClockwiseDegrees(clientTicks, partialTicks, degreesPerTickForPeriodTicks(rotationPeriodTicks));
    }

    public static float pedestalClockwiseFrameAngle(long clientTicks, float partialTicks) {
        return clockwiseDegrees(clientTicks, partialTicks, PEDESTAL_ROTATION_DEGREES_PER_TICK);
    }

    public static float pedestalCounterClockwiseFrameAngle(long clientTicks, float partialTicks) {
        return counterClockwiseDegrees(clientTicks, partialTicks, PEDESTAL_ROTATION_DEGREES_PER_TICK);
    }

    public static float tiltedAxisXForZRotation(float zRotationDegrees) {
        if (zRotationDegrees < 0.0F) {
            return SIN_22_5;
        }
        return -SIN_22_5;
    }

    public static float tiltedAxisY() {
        return COS_22_5;
    }

    public static float tiltedAxisZ() {
        return 0.0F;
    }

    public static float bobOffset(long clientTicks, float partialTicks) {
        return (float) Math.sin((clientTicks + partialTicks) * BOBBING_ANGULAR_VELOCITY) * BOBBING_AMPLITUDE;
    }

    private static float clockwiseDegrees(long clientTicks, float partialTicks, float speed) {
        return -rotationDegrees(clientTicks, partialTicks, speed);
    }

    private static float counterClockwiseDegrees(long clientTicks, float partialTicks, float speed) {
        return rotationDegrees(clientTicks, partialTicks, speed);
    }

    private static float rotationDegrees(long clientTicks, float partialTicks, float speed) {
        return (clientTicks + partialTicks) * speed;
    }

    private static float degreesPerTickForPeriodSeconds(float secondsPerRotation) {
        return DEGREES_PER_ROTATION / (secondsPerRotation * TICKS_PER_SECOND);
    }

    private static float degreesPerTickForPeriodTicks(int ticksPerRotation) {
        return DEGREES_PER_ROTATION / ticksPerRotation;
    }
}
