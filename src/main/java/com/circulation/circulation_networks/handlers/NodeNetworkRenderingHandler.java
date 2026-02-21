package com.circulation.circulation_networks.handlers;

import com.circulation.circulation_networks.items.ItemInspectionTool;
import com.circulation.circulation_networks.registry.RegistryItems;
import com.github.bsideup.jabel.Desugar;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Objects;

public final class NodeNetworkRenderingHandler {

    public static final NodeNetworkRenderingHandler INSTANCE = new NodeNetworkRenderingHandler();

    private final List<Line> nodeLinks = new ObjectArrayList<>();
    private final List<Line> machineLinks = new ObjectArrayList<>();

    public void addNodeLink(long a, long b) {
        nodeLinks.add(new Line(a, b));
    }

    public void addMachineLink(long a, long b) {
        machineLinks.add(new Line(a, b));
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
            GlStateManager.glLineWidth(3);
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
    private void renderEntityConnectionLine(BufferBuilder buffer, BlockPos pos, BlockPos pos1, float r, float g, float b) {
        buffer.pos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
              .color(r, g, b, 0.5f)
              .endVertex();

        buffer.pos(pos1.getX() + 0.5, pos1.getY() + 0.5, pos1.getZ() + 0.5)
              .color(r, g, b, 0.5f)
              .endVertex();
    }

    @Desugar
    private record Line(BlockPos from, BlockPos to) {
        private Line(long from, long to) {
            this(BlockPos.fromLong(from), BlockPos.fromLong(to));
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Line line = (Line) o;
            var a = from.toLong();
            var b = to.toLong();
            var c = line.from.toLong();
            var d = line.to.toLong();
            return (a == c && b == d) || (a == d && b == c);
        }

        @Override
        public int hashCode() {
            return Objects.hash(from.toLong() * to.toLong());
        }
    }

}