package com.circulation.circulation_networks.handlers;

import com.circulation.circulation_networks.items.CirculationConfiguratorModeModel.InspectionMode;
import com.circulation.circulation_networks.items.CirculationConfiguratorModeModel.ToolFunction;
import com.circulation.circulation_networks.items.CirculationConfiguratorState;
import com.circulation.circulation_networks.math.Vec3d;
import com.circulation.circulation_networks.registry.CFNItems;
import com.circulation.circulation_networks.utils.RenderingUtils;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.opengl.GL11;

@OnlyIn(Dist.CLIENT)
public final class NodeNetworkRenderingHandler {

    public static final NodeNetworkRenderingHandler INSTANCE = new NodeNetworkRenderingHandler();

    private static final float CORE_RADIUS = 0.04f;
    private static final float GLOW_RADIUS = 0.10f;
    private static final double MAX_RENDER_DISTANCE = 128.0D;
    private static final double MAX_RENDER_DISTANCE_SQ = MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE;

    private static final float SPHERE_CORE_RADIUS = 0.12f;
    private static final float SPHERE_GLOW_RADIUS = 0.28f;
    private final ObjectSet<Line> nodeLinks = new ObjectLinkedOpenHashSet<>();
    private final ObjectSet<Line> machineLinks = new ObjectLinkedOpenHashSet<>();
    private final Multiset<Pos> nodePoss = HashMultiset.create();
    private final Multiset<Pos> machinePoss = HashMultiset.create();

    private static void copyEventPoseToModelView(PoseStack eventPoseStack, PoseStack modelViewStack) {
        if (eventPoseStack == null) {
            return;
        }
        modelViewStack.last().pose().set(eventPoseStack.last().pose());
        modelViewStack.last().normal().set(eventPoseStack.last().normal());
    }

    private static void drawSphere(float r, float g, float b, float radius, float alpha) {
        RenderingUtils.drawSphere(r, g, b, radius, alpha, 16, 16);
    }

    private static double distanceSqToPoint(double x, double y, double z, Pos pos) {
        double dx = pos.x - x;
        double dy = pos.y - y;
        double dz = pos.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static double distanceSqToSegment(double x, double y, double z, Line line) {
        double abX = line.to.x - line.from.x;
        double abY = line.to.y - line.from.y;
        double abZ = line.to.z - line.from.z;
        double apX = x - line.from.x;
        double apY = y - line.from.y;
        double apZ = z - line.from.z;
        double abLengthSq = abX * abX + abY * abY + abZ * abZ;
        if (abLengthSq <= 1.0E-6D) {
            return distanceSqToPoint(x, y, z, line.from);
        }
        double t = (apX * abX + apY * abY + apZ * abZ) / abLengthSq;
        t = Math.max(0.0D, Math.min(1.0D, t));
        double closestX = line.from.x + abX * t;
        double closestY = line.from.y + abY * t;
        double closestZ = line.from.z + abZ * t;
        double dx = closestX - x;
        double dy = closestY - y;
        double dz = closestZ - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static boolean canLineReachRenderDistance(double x, double y, double z, Line line) {
        double dx = line.centerX - x;
        double dy = line.centerY - y;
        double dz = line.centerZ - z;
        return dx * dx + dy * dy + dz * dz <= line.maxReachSq;
    }

    public void addNodeLink(long a, long b) {
        var l = Line.create(a, b);
        if (nodeLinks.add(l)) {
            nodePoss.add(l.from);
            nodePoss.add(l.to);
        }
    }

    public void addMachineLink(long a, long b) {
        var l = Line.create(a, b);
        if (machineLinks.add(l)) {
            machinePoss.add(l.from);
            machinePoss.add(l.to);
        }
    }

    public void removeNodeLink(long a, long b) {
        var l = Line.create(a, b);
        if (nodeLinks.remove(l)) {
            nodePoss.remove(l.from);
            nodePoss.remove(l.to);
        }
    }

    public void removeMachineLink(long a, long b) {
        var l = Line.create(a, b);
        if (machineLinks.remove(l)) {
            machinePoss.remove(l.from);
            machinePoss.remove(l.to);
        }
    }

    public void clearLinks() {
        nodeLinks.clear();
        machineLinks.clear();
        nodePoss.clear();
        machinePoss.clear();
    }

    @SubscribeEvent
    public void renderWorldLastEvent(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;

        var stack = p.getMainHandItem();
        if (!(stack.getItem() == CFNItems.circulationConfigurator
            && CirculationConfiguratorState.getFunction(stack) == ToolFunction.INSPECTION
            && InspectionMode.fromID(CirculationConfiguratorState.getSubMode(stack)).isLinkMode()))
            return;

        InspectionMode currentMode = InspectionMode.fromID(CirculationConfiguratorState.getSubMode(stack));
        boolean showNodes = currentMode.showNodeLinks();
        boolean showMachines = currentMode.showMachineLinks();

        var cameraPos = event.getCamera().getPosition();
        double doubleX = cameraPos.x;
        double doubleY = cameraPos.y;
        double doubleZ = cameraPos.z;

        PoseStack mvStack = RenderSystem.getModelViewStack();
        mvStack.pushPose();
        copyEventPoseToModelView(event.getPoseStack(), mvStack);
        mvStack.translate(-doubleX, -doubleY, -doubleZ);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        if (showNodes) {
            for (var link : nodeLinks) {
                if (!canLineReachRenderDistance(doubleX, doubleY, doubleZ, link)
                    || distanceSqToSegment(doubleX, doubleY, doubleZ, link) > MAX_RENDER_DISTANCE_SQ) continue;
                RenderingUtils.drawLaserCylinder(link.from.x, link.from.y, link.from.z, link.to.x, link.to.y, link.to.z, GLOW_RADIUS, 0.3f, 0.3f, 1.0f, 0.25f);
                RenderingUtils.drawLaserCylinder(link.from.x, link.from.y, link.from.z, link.to.x, link.to.y, link.to.z, CORE_RADIUS, 0.3f, 0.3f, 1.0f, 1.0f);
            }
        }
        if (showMachines) {
            for (var link : machineLinks) {
                if (!canLineReachRenderDistance(doubleX, doubleY, doubleZ, link)
                    || distanceSqToSegment(doubleX, doubleY, doubleZ, link) > MAX_RENDER_DISTANCE_SQ) continue;
                RenderingUtils.drawLaserCylinder(link.from.x, link.from.y, link.from.z, link.to.x, link.to.y, link.to.z, GLOW_RADIUS, 1.0f, 0.3f, 0.3f, 0.25f);
                RenderingUtils.drawLaserCylinder(link.from.x, link.from.y, link.from.z, link.to.x, link.to.y, link.to.z, CORE_RADIUS, 1.0f, 0.3f, 0.3f, 1.0f);
            }
        }

        if (showNodes) {
            for (var pos : nodePoss.elementSet()) {
                if (distanceSqToPoint(doubleX, doubleY, doubleZ, pos) > MAX_RENDER_DISTANCE_SQ) continue;
                boolean alsoMachine = showMachines && machinePoss.contains(pos);
                RenderSystem.getModelViewStack().pushPose();
                RenderSystem.getModelViewStack().translate(pos.x, pos.y, pos.z);
                RenderSystem.applyModelViewMatrix();
                if (alsoMachine) {
                    drawSphere(1.0f, 0.0f, 1.0f, SPHERE_GLOW_RADIUS, 0.3f);
                    drawSphere(1.0f, 0.0f, 1.0f, SPHERE_CORE_RADIUS, 0.9f);
                } else {
                    drawSphere(0.0f, 0.0f, 1.0f, SPHERE_GLOW_RADIUS, 0.3f);
                    drawSphere(0.0f, 0.0f, 1.0f, SPHERE_CORE_RADIUS, 0.9f);
                }
                RenderSystem.getModelViewStack().popPose();
                RenderSystem.applyModelViewMatrix();
            }
        }
        if (showMachines) {
            for (var pos : machinePoss.elementSet()) {
                if (showNodes && nodePoss.contains(pos)) continue;
                if (distanceSqToPoint(doubleX, doubleY, doubleZ, pos) > MAX_RENDER_DISTANCE_SQ) continue;
                RenderSystem.getModelViewStack().pushPose();
                RenderSystem.getModelViewStack().translate(pos.x, pos.y, pos.z);
                RenderSystem.applyModelViewMatrix();
                drawSphere(1.0f, 0.0f, 0.0f, SPHERE_GLOW_RADIUS, 0.3f);
                drawSphere(1.0f, 0.0f, 0.0f, SPHERE_CORE_RADIUS, 0.9f);
                RenderSystem.getModelViewStack().popPose();
                RenderSystem.applyModelViewMatrix();
            }
        }

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.getModelViewStack().popPose();
        RenderSystem.applyModelViewMatrix();
    }

    private record Line(Pos from, Pos to, int hash, double centerX, double centerY, double centerZ, double maxReachSq) {

        private static Line create(long from, long to) {
            var fromP = Pos.fromLong(from);
            var toP = Pos.fromLong(to);
            int h1 = fromP.hashCode();
            int h2 = toP.hashCode();
            int mixedHash = (h1 < h2) ? (31 * h1 + h2) : (31 * h2 + h1);
            double centerX = (fromP.x + toP.x) * 0.5D;
            double centerY = (fromP.y + toP.y) * 0.5D;
            double centerZ = (fromP.z + toP.z) * 0.5D;
            double dx = toP.x - fromP.x;
            double dy = toP.y - fromP.y;
            double dz = toP.z - fromP.z;
            double halfLength = Math.sqrt(dx * dx + dy * dy + dz * dz) * 0.5D;
            double maxReach = MAX_RENDER_DISTANCE + halfLength;
            return new Line(fromP, toP, mixedHash, centerX, centerY, centerZ, maxReach * maxReach);
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Line line = (Line) o;
            if (this.hash != line.hash) return false;
            return (from.equals(line.from) && to.equals(line.to)) || (from.equals(line.to) && to.equals(line.from));
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private static class Pos extends Vec3d {

        private final int hash;

        public Pos(int xIn, int yIn, int zIn) {
            this(xIn + 0.5, yIn + 0.5, zIn + 0.5);
        }

        public Pos(double xIn, double yIn, double zIn) {
            super(xIn, yIn, zIn);
            hash = super.hashCode();
        }

        public static Pos fromLong(long serialized) {
            return new Pos(BlockPos.getX(serialized), BlockPos.getY(serialized), BlockPos.getZ(serialized));
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            var pos = (Pos) o;
            return this.x == pos.x && this.y == pos.y && this.z == pos.z;
        }
    }
}
