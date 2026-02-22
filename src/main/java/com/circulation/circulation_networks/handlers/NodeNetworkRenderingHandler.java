package com.circulation.circulation_networks.handlers;

import com.circulation.circulation_networks.items.ItemInspectionTool;
import com.circulation.circulation_networks.registry.RegistryItems;
import com.github.bsideup.jabel.Desugar;
import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Multiset;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public final class NodeNetworkRenderingHandler {

    public static final NodeNetworkRenderingHandler INSTANCE = new NodeNetworkRenderingHandler();

    private static final int CYLINDER_SIDES = 8;
    private static final float CORE_RADIUS = 0.04f;
    private static final float GLOW_RADIUS = 0.10f;

    private static final float SPHERE_CORE_RADIUS = 0.12f;
    private static final float SPHERE_GLOW_RADIUS = 0.28f;

    private final ObjectSet<Line> nodeLinks = new ObjectLinkedOpenHashSet<>();
    private final ObjectSet<Line> machineLinks = new ObjectLinkedOpenHashSet<>();
    private final Multiset<Pos> nodePoss = ConcurrentHashMultiset.create();
    private final Multiset<Pos> machinePoss = ConcurrentHashMultiset.create();
    private boolean lock;

    public void addNodeLink(long a, long b) {
        lock = true;
        var l = Line.create(a, b);
        nodeLinks.add(l);
        nodePoss.add(l.from);
        nodePoss.add(l.to);
        lock = false;
    }

    public void addMachineLink(long a, long b) {
        lock = true;
        var l = Line.create(a, b);
        machineLinks.add(l);
        machinePoss.add(l.from);
        machinePoss.add(l.to);
        lock = false;
    }

    public void removeNodeLink(long a, long b) {
        lock = true;
        var l = Line.create(a, b);
        nodeLinks.remove(l);
        nodePoss.remove(l.from);
        nodePoss.remove(l.to);
        lock = false;
    }

    public void removeMachineLink(long a, long b) {
        lock = true;
        var l = Line.create(a, b);
        machineLinks.remove(l);
        machinePoss.remove(l.from);
        machinePoss.remove(l.to);
        lock = false;
    }

    public void clearLinks() {
        lock = true;
        nodeLinks.clear();
        machineLinks.clear();
        nodePoss.clear();
        machinePoss.clear();
        lock = false;
    }

    @SubscribeEvent
    public void renderWorldLastEvent(RenderWorldLastEvent event) {
        if (lock) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.player;

        var stack = p.getHeldItemMainhand();
        if (!(stack.getItem() == RegistryItems.inspectionTool
            && RegistryItems.inspectionTool.getMode(stack).isMode(ItemInspectionTool.Mode.Link))) return;

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

        for (var link : nodeLinks) {
            drawLaserCylinder(link.from, link.to, GLOW_RADIUS, 0.3f, 0.3f, 1.0f, 0.25f);
            drawLaserCylinder(link.from, link.to, CORE_RADIUS, 0.3f, 0.3f, 1.0f, 1.0f);
        }
        for (var link : machineLinks) {
            drawLaserCylinder(link.from, link.to, GLOW_RADIUS, 1.0f, 0.3f, 0.3f, 0.25f);
            drawLaserCylinder(link.from, link.to, CORE_RADIUS, 1.0f, 0.3f, 0.3f, 1.0f);
        }

        for (var pos : nodePoss.elementSet()) {
            boolean alsoMachine = machinePoss.contains(pos);
            float r = alsoMachine ? 1.0f : 0.0f;
            float g = 0.0f;
            float b = 1.0f;
            GlStateManager.pushMatrix();
            GlStateManager.translate(pos.x, pos.y, pos.z);
            drawSphere(r, g, b, SPHERE_GLOW_RADIUS, 0.3f);
            drawSphere(r, g, b, SPHERE_CORE_RADIUS, 0.9f);
            GlStateManager.popMatrix();
        }
        for (var pos : machinePoss.elementSet()) {
            if (nodePoss.contains(pos)) continue;
            GlStateManager.pushMatrix();
            GlStateManager.translate(pos.x, pos.y, pos.z);
            drawSphere(1.0f, 0.0f, 0.0f, SPHERE_GLOW_RADIUS, 0.3f);
            drawSphere(1.0f, 0.0f, 0.0f, SPHERE_CORE_RADIUS, 0.9f);
            GlStateManager.popMatrix();
        }

        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private void drawLaserCylinder(Pos from, Pos to, float radius, float r, float g, float b, float alpha) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6) return;

        double ax = dx / len, ay = dy / len, az = dz / len;

        double bx, by, bz;
        if (Math.abs(ax) <= Math.abs(ay) && Math.abs(ax) <= Math.abs(az)) {
            bx = 0;
            by = -az;
            bz = ay;
        } else if (Math.abs(ay) <= Math.abs(az)) {
            bx = -az;
            by = 0;
            bz = ax;
        } else {
            bx = -ay;
            by = ax;
            bz = 0;
        }
        double bLen = Math.sqrt(bx * bx + by * by + bz * bz);
        bx /= bLen;
        by /= bLen;
        bz /= bLen;

        double cx = ay * bz - az * by;
        double cy = az * bx - ax * bz;
        double cz = ax * by - ay * bx;

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();

        GlStateManager.color(r, g, b, alpha);
        buf.begin(GL11.GL_QUAD_STRIP, DefaultVertexFormats.POSITION);

        for (int i = 0; i <= CYLINDER_SIDES; i++) {
            double angle = 2.0 * Math.PI * i / CYLINDER_SIDES;
            double cos = Math.cos(angle), sin = Math.sin(angle);
            double nx = radius * (cos * bx + sin * cx);
            double ny = radius * (cos * by + sin * cy);
            double nz = radius * (cos * bz + sin * cz);
            buf.pos(from.x + nx, from.y + ny, from.z + nz).endVertex();
            buf.pos(to.x + nx, to.y + ny, to.z + nz).endVertex();
        }
        tess.draw();
    }

    @Desugar
    private record Line(Pos from, Pos to, int hash) {

        private static Line create(long from, long to) {
            var fromP = Pos.fromLong(from);
            var toP = Pos.fromLong(to);
            int h1 = fromP.hashCode();
            int h2 = toP.hashCode();
            int mixedHash = (h1 < h2) ? (31 * h1 + h2) : (31 * h2 + h1);
            return new Line(fromP, toP, mixedHash);
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

    private void drawSphere(float r, float g, float b, float radius, float alpha) {
        GlStateManager.color(r, g, b, alpha);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        final int slices = 24, stacks = 24;
        for (int i = 0; i < slices; i++) {
            double phi1 = Math.PI * i / slices;
            double phi2 = Math.PI * (i + 1) / slices;
            buf.begin(GL11.GL_QUAD_STRIP, DefaultVertexFormats.POSITION_NORMAL);
            for (int j = 0; j <= stacks; j++) {
                double theta = 2.0 * Math.PI * j / stacks;
                float x1 = (float) (radius * Math.sin(phi1) * Math.cos(theta));
                float y1 = (float) (radius * Math.cos(phi1));
                float z1 = (float) (radius * Math.sin(phi1) * Math.sin(theta));
                buf.pos(x1, y1, z1).normal(x1 / radius, y1 / radius, z1 / radius).endVertex();
                float x2 = (float) (radius * Math.sin(phi2) * Math.cos(theta));
                float y2 = (float) (radius * Math.cos(phi2));
                float z2 = (float) (radius * Math.sin(phi2) * Math.sin(theta));
                buf.pos(x2, y2, z2).normal(x2 / radius, y2 / radius, z2 / radius).endVertex();
            }
            tess.draw();
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