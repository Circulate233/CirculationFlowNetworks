package com.circulation.circulation_networks.handlers;

import com.circulation.circulation_networks.items.ItemInspectionTool;
import com.circulation.circulation_networks.registry.RegistryItems;
import com.github.bsideup.jabel.Desugar;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3i;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

public final class NodeNetworkRenderingHandler {

    public static final NodeNetworkRenderingHandler INSTANCE = new NodeNetworkRenderingHandler();

    private final ObjectSet<Line> nodeLinks = new ObjectLinkedOpenHashSet<>();
    private final ObjectSet<Line> machineLinks = new ObjectLinkedOpenHashSet<>();

    public void addNodeLink(long a, long b) {
        nodeLinks.add(Line.create(a, b));
    }

    public void addMachineLink(long a, long b) {
        machineLinks.add(Line.create(a, b));
    }

    public void removeNodeLink(long a, long b) {
        nodeLinks.remove(Line.create(a, b));
    }

    public void removeMachineLink(long a, long b) {
        machineLinks.remove(Line.create(a, b));
    }

    public void clearLinks() {
        nodeLinks.clear();
        machineLinks.clear();
    }

    @SubscribeEvent
    public void renderWorldLastEvent(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.player;

        var stack = p.getHeldItemMainhand();
        if (stack.getItem() == RegistryItems.inspectionTool
            && RegistryItems.inspectionTool.getMode(stack).isMode(ItemInspectionTool.Mode.Link)) {
            double doubleX = p.lastTickPosX + (p.posX - p.lastTickPosX) * event.getPartialTicks();
            double doubleY = p.lastTickPosY + (p.posY - p.lastTickPosY) * event.getPartialTicks();
            double doubleZ = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * event.getPartialTicks();

            GlStateManager.pushMatrix();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.glLineWidth(8);
            GlStateManager.translate(-doubleX, -doubleY, -doubleZ);

            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            GlStateManager.disableDepth();
            GlStateManager.disableTexture2D();
            GlStateManager.disableLighting();

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();

            buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);

            for (var nodeLink : nodeLinks) {
                renderEntityConnectionLine(buffer, nodeLink.from, nodeLink.to, 0, 0, 1);
            }

            for (var machineLink : machineLinks) {
                renderEntityConnectionLine(buffer, machineLink.from, machineLink.to, 1, 0, 0);
            }

            tessellator.draw();

            GlStateManager.enableLighting();
            GlStateManager.enableTexture2D();
            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        }
    }

    @SuppressWarnings("SameParameterValue")
    private void renderEntityConnectionLine(BufferBuilder buffer, Pos pos, Pos pos1, float r, float g, float b) {
        buffer.pos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
              .color(r, g, b, 0.5f)
              .endVertex();

        buffer.pos(pos1.getX() + 0.5, pos1.getY() + 0.5, pos1.getZ() + 0.5)
              .color(r, g, b, 0.5f)
              .endVertex();
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

    private static class Pos extends Vec3i {

        private static final int NUM_X_BITS = 1 + MathHelper.log2(MathHelper.smallestEncompassingPowerOfTwo(30000000));
        private static final int NUM_Z_BITS = NUM_X_BITS;
        private static final int NUM_Y_BITS = 64 - NUM_X_BITS - NUM_Z_BITS;
        private static final int Y_SHIFT = NUM_Z_BITS;
        private static final int X_SHIFT = Y_SHIFT + NUM_Y_BITS;
        private final int hash;

        public Pos(int xIn, int yIn, int zIn) {
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
            return this.getX() == pos.getX() && this.getY() == pos.getY() && this.getZ() == pos.getZ();
        }
    }
}