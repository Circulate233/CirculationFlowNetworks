package com.circulation.circulation_networks.handlers;

import com.circulation.circulation_networks.items.CirculationConfiguratorModeModel.InspectionMode;
import com.circulation.circulation_networks.items.CirculationConfiguratorModeModel.ToolFunction;
import com.circulation.circulation_networks.items.CirculationConfiguratorState;
import com.circulation.circulation_networks.math.Vec3d;
import com.circulation.circulation_networks.registry.CFNItems;
import com.circulation.circulation_networks.utils.RenderingUtils;
import com.github.bsideup.jabel.Desugar;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public final class NodeNetworkRenderingHandler {

    public static final NodeNetworkRenderingHandler INSTANCE = new NodeNetworkRenderingHandler();

    private static final float CORE_RADIUS = 0.04f;
    private static final float GLOW_RADIUS = 0.10f;
    private static final double MAX_RENDER_DISTANCE = 128.0D;
    private static final double MAX_RENDER_DISTANCE_SQ = MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE;

    private static final float SPHERE_CORE_RADIUS = 0.12f;
    private static final float SPHERE_GLOW_RADIUS = 0.28f;
    private static int sphereDisplayList = -1;
    private final ObjectSet<Line> nodeLinks = new ObjectLinkedOpenHashSet<>();
    private final ObjectSet<Line> machineLinks = new ObjectLinkedOpenHashSet<>();
    private final Multiset<Pos> nodePoss = HashMultiset.create();
    private final Multiset<Pos> machinePoss = HashMultiset.create();

    private static void ensureSphereDisplayList() {
        if (sphereDisplayList >= 0) return;
        sphereDisplayList = GL11.glGenLists(1);
        GL11.glNewList(sphereDisplayList, GL11.GL_COMPILE);
        final int slices = 16, stacks = 16;
        for (int i = 0; i < slices; i++) {
            double phi1 = Math.PI * i / slices;
            double phi2 = Math.PI * (i + 1) / slices;
            GL11.glBegin(GL11.GL_QUAD_STRIP);
            for (int j = 0; j <= stacks; j++) {
                double theta = 2.0 * Math.PI * j / stacks;
                GL11.glVertex3f(
                    (float) (Math.sin(phi1) * Math.cos(theta)),
                    (float) Math.cos(phi1),
                    (float) (Math.sin(phi1) * Math.sin(theta))
                );
                GL11.glVertex3f(
                    (float) (Math.sin(phi2) * Math.cos(theta)),
                    (float) Math.cos(phi2),
                    (float) (Math.sin(phi2) * Math.sin(theta))
                );
            }
            GL11.glEnd();
        }
        GL11.glEndList();
    }

    private static void drawSphere(float r, float g, float b, float radius, float alpha) {
        ensureSphereDisplayList();
        GlStateManager.color(r, g, b, alpha);
        GlStateManager.pushMatrix();
        GlStateManager.scale(radius, radius, radius);
        GL11.glCallList(sphereDisplayList);
        GlStateManager.popMatrix();
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
    public void renderWorldLastEvent(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.player;

        var stack = p.getHeldItemMainhand();
        if (!(stack.getItem() == CFNItems.circulationConfigurator
            && CirculationConfiguratorState.getFunction(stack) == ToolFunction.INSPECTION
            && InspectionMode.fromID(CirculationConfiguratorState.getSubMode(stack)).isLinkMode()))
            return;

        InspectionMode currentMode = InspectionMode.fromID(CirculationConfiguratorState.getSubMode(stack));
        boolean showNodes = currentMode.showNodeLinks();
        boolean showMachines = currentMode.showMachineLinks();

        double doubleX = p.lastTickPosX + (p.posX - p.lastTickPosX) * event.getPartialTicks();
        double doubleY = p.lastTickPosY + (p.posY - p.lastTickPosY) * event.getPartialTicks();
        double doubleZ = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * event.getPartialTicks();

        GlStateManager.pushMatrix();
        GlStateManager.translate(-doubleX, -doubleY, -doubleZ);
        GlStateManager.enableBlend();
        GlStateManager.disableDepth();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);

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
                GlStateManager.pushMatrix();
                GlStateManager.translate(pos.x, pos.y, pos.z);
                if (alsoMachine) {
                    drawSphere(1.0f, 0.0f, 1.0f, SPHERE_GLOW_RADIUS, 0.3f);
                    drawSphere(1.0f, 0.0f, 1.0f, SPHERE_CORE_RADIUS, 0.9f);
                } else {
                    drawSphere(0.0f, 0.0f, 1.0f, SPHERE_GLOW_RADIUS, 0.3f);
                    drawSphere(0.0f, 0.0f, 1.0f, SPHERE_CORE_RADIUS, 0.9f);
                }
                GlStateManager.popMatrix();
            }
        }
        if (showMachines) {
            for (var pos : machinePoss.elementSet()) {
                if (showNodes && nodePoss.contains(pos)) continue;
                if (distanceSqToPoint(doubleX, doubleY, doubleZ, pos) > MAX_RENDER_DISTANCE_SQ) continue;
                GlStateManager.pushMatrix();
                GlStateManager.translate(pos.x, pos.y, pos.z);
                drawSphere(1.0f, 0.0f, 0.0f, SPHERE_GLOW_RADIUS, 0.3f);
                drawSphere(1.0f, 0.0f, 0.0f, SPHERE_CORE_RADIUS, 0.9f);
                GlStateManager.popMatrix();
            }
        }

        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.enableDepth();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    @Desugar
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

        private static final int NUM_X_BITS = 1 + MathHelper.log2(MathHelper.smallestEncompassingPowerOfTwo(30000000));
        private static final int NUM_Z_BITS = NUM_X_BITS;
        private static final int NUM_Y_BITS = 64 - NUM_X_BITS - NUM_Z_BITS;
        private static final int Y_SHIFT = NUM_Z_BITS;
        private static final int X_SHIFT = Y_SHIFT + NUM_Y_BITS;
        private final int hash;

        public Pos(int xIn, int yIn, int zIn) {
            this(xIn + 0.5, yIn + 0.5, zIn + 0.5);
        }

        public Pos(double xIn, double yIn, double zIn) {
            super(xIn, yIn, zIn);
            hash = super.hashCode();
        }

        public static Pos fromLong(long serialized) {
            int i = (int) (serialized << 64 - X_SHIFT - NUM_X_BITS >> 64 - NUM_X_BITS);
            int j = (int) (serialized << 64 - Y_SHIFT - NUM_Y_BITS >> 64 - NUM_Y_BITS);
            int k = (int) (serialized << 64 - NUM_Z_BITS >> 64 - NUM_Z_BITS);
            return new Pos(i, j, k);
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
