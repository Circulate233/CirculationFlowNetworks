package com.circulation.circulation_networks.handlers;

import com.circulation.circulation_networks.client.render.ClientAnimationTicker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public final class NodeHighlightRenderingHandler {

    public static final NodeHighlightRenderingHandler INSTANCE = new NodeHighlightRenderingHandler();
    private static final long HIGHLIGHT_DURATION_TICKS = 200L;
    private static final int BLINK_HALF_PERIOD = 5;
    private static final float LINE_WIDTH = 2.5F;
    private static final float BOX_R = 1.0F;
    private static final float BOX_G = 1.0F;
    private static final float BOX_B = 0.0F;
    private static final float BOX_ALPHA = 0.85F;
    private static final double EXPAND = 0.002D;
    private BlockPos targetPos;
    private int targetDimId;
    private long startTick;

    private NodeHighlightRenderingHandler() {
    }

    public void highlight(BlockPos pos, int dimId) {
        this.targetPos = pos;
        this.targetDimId = dimId;
        this.startTick = ClientAnimationTicker.ticks();
    }

    public void clear() {
        targetPos = null;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        long clientTick = ClientAnimationTicker.ticks();
        if (targetPos != null && clientTick - startTick > HIGHLIGHT_DURATION_TICKS) {
            targetPos = null;
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null || targetPos == null) {
            return;
        }
        if (mc.player.dimension != targetDimId) {
            return;
        }
        long clientTick = ClientAnimationTicker.ticks();
        long elapsed = clientTick - startTick;
        if (elapsed > HIGHLIGHT_DURATION_TICKS) {
            return;
        }
        int blinkPhase = (int) (elapsed % (BLINK_HALF_PERIOD * 2));
        if (blinkPhase >= BLINK_HALF_PERIOD) {
            return;
        }
        float partialTicks = event.getPartialTicks();
        double camX = mc.player.lastTickPosX + (mc.player.posX - mc.player.lastTickPosX) * partialTicks;
        double camY = mc.player.lastTickPosY + (mc.player.posY - mc.player.lastTickPosY) * partialTicks;
        double camZ = mc.player.lastTickPosZ + (mc.player.posZ - mc.player.lastTickPosZ) * partialTicks;
        renderHighlight(camX, camY, camZ, targetPos);
    }

    private void renderHighlight(double camX, double camY, double camZ, BlockPos pos) {
        double x = pos.getX() - camX;
        double y = pos.getY() - camY;
        double z = pos.getZ() - camZ;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
            GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
            GL11.GL_ONE, GL11.GL_ZERO
        );
        GlStateManager.glLineWidth(LINE_WIDTH);
        GlStateManager.color(BOX_R, BOX_G, BOX_B, BOX_ALPHA);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION);

        double minX = 0.0D - EXPAND;
        double minY = 0.0D - EXPAND;
        double minZ = 0.0D - EXPAND;
        double maxX = 1.0D + EXPAND;
        double maxY = 1.0D + EXPAND;
        double maxZ = 1.0D + EXPAND;

        // Bottom face edges
        buf.pos(minX, minY, minZ).endVertex();
        buf.pos(maxX, minY, minZ).endVertex();
        buf.pos(maxX, minY, minZ).endVertex();
        buf.pos(maxX, minY, maxZ).endVertex();
        buf.pos(maxX, minY, maxZ).endVertex();
        buf.pos(minX, minY, maxZ).endVertex();
        buf.pos(minX, minY, maxZ).endVertex();
        buf.pos(minX, minY, minZ).endVertex();
        // Top face edges
        buf.pos(minX, maxY, minZ).endVertex();
        buf.pos(maxX, maxY, minZ).endVertex();
        buf.pos(maxX, maxY, minZ).endVertex();
        buf.pos(maxX, maxY, maxZ).endVertex();
        buf.pos(maxX, maxY, maxZ).endVertex();
        buf.pos(minX, maxY, maxZ).endVertex();
        buf.pos(minX, maxY, maxZ).endVertex();
        buf.pos(minX, maxY, minZ).endVertex();
        // Vertical edges
        buf.pos(minX, minY, minZ).endVertex();
        buf.pos(minX, maxY, minZ).endVertex();
        buf.pos(maxX, minY, minZ).endVertex();
        buf.pos(maxX, maxY, minZ).endVertex();
        buf.pos(maxX, minY, maxZ).endVertex();
        buf.pos(maxX, maxY, maxZ).endVertex();
        buf.pos(minX, minY, maxZ).endVertex();
        buf.pos(minX, maxY, maxZ).endVertex();

        tess.draw();

        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }
}
