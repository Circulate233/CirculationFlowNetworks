package com.circulation.circulation_networks.handlers;

import com.circulation.circulation_networks.client.render.ClientAnimationTicker;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@OnlyIn(Dist.CLIENT)
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
    public void onRenderWorldLast(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || targetPos == null) {
            return;
        }
        if (mc.level.dimension().location().hashCode() != targetDimId) {
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
        var cameraPos = event.getCamera().getPosition();
        renderHighlight(event.getPoseStack(), cameraPos.x, cameraPos.y, cameraPos.z, targetPos);
    }

    private void renderHighlight(PoseStack poseStack, double camX, double camY, double camZ, BlockPos pos) {
        double x = pos.getX() - camX;
        double y = pos.getY() - camY;
        double z = pos.getZ() - camZ;

        poseStack.pushPose();
        poseStack.translate(x, y, z);

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.lineWidth(LINE_WIDTH);
        RenderSystem.setShaderColor(BOX_R, BOX_G, BOX_B, BOX_ALPHA);
        RenderSystem.setShader(GameRenderer::getPositionShader);

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        var matrix = poseStack.last().pose();

        float minX = (float) (0.0D - EXPAND);
        float minY = (float) (0.0D - EXPAND);
        float minZ = (float) (0.0D - EXPAND);
        float maxX = (float) (1.0D + EXPAND);
        float maxY = (float) (1.0D + EXPAND);
        float maxZ = (float) (1.0D + EXPAND);

        buf.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION);
        buf.vertex(matrix, minX, minY, minZ).endVertex();
        buf.vertex(matrix, maxX, minY, minZ).endVertex();
        buf.vertex(matrix, maxX, minY, minZ).endVertex();
        buf.vertex(matrix, maxX, minY, maxZ).endVertex();
        buf.vertex(matrix, maxX, minY, maxZ).endVertex();
        buf.vertex(matrix, minX, minY, maxZ).endVertex();
        buf.vertex(matrix, minX, minY, maxZ).endVertex();
        buf.vertex(matrix, minX, minY, minZ).endVertex();
        buf.vertex(matrix, minX, maxY, minZ).endVertex();
        buf.vertex(matrix, maxX, maxY, minZ).endVertex();
        buf.vertex(matrix, maxX, maxY, minZ).endVertex();
        buf.vertex(matrix, maxX, maxY, maxZ).endVertex();
        buf.vertex(matrix, maxX, maxY, maxZ).endVertex();
        buf.vertex(matrix, minX, maxY, maxZ).endVertex();
        buf.vertex(matrix, minX, maxY, maxZ).endVertex();
        buf.vertex(matrix, minX, maxY, minZ).endVertex();
        buf.vertex(matrix, minX, minY, minZ).endVertex();
        buf.vertex(matrix, minX, maxY, minZ).endVertex();
        buf.vertex(matrix, maxX, minY, minZ).endVertex();
        buf.vertex(matrix, maxX, maxY, minZ).endVertex();
        buf.vertex(matrix, maxX, minY, maxZ).endVertex();
        buf.vertex(matrix, maxX, maxY, maxZ).endVertex();
        buf.vertex(matrix, minX, minY, maxZ).endVertex();
        buf.vertex(matrix, minX, maxY, maxZ).endVertex();
        tess.end();

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();
    }
}
