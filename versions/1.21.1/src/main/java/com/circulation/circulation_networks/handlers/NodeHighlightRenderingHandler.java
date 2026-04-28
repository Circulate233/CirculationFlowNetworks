package com.circulation.circulation_networks.handlers;

import com.circulation.circulation_networks.client.render.ClientAnimationTicker;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

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
    public void onClientTick(ClientTickEvent.Pre event) {
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
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tess = Tesselator.getInstance();
        var matrix = poseStack.last().pose();

        float minX = (float) (0.0D - EXPAND);
        float minY = (float) (0.0D - EXPAND);
        float minZ = (float) (0.0D - EXPAND);
        float maxX = (float) (1.0D + EXPAND);
        float maxY = (float) (1.0D + EXPAND);
        float maxZ = (float) (1.0D + EXPAND);

        int ri = (int) (BOX_R * 255), gi = (int) (BOX_G * 255), bi = (int) (BOX_B * 255), ai = (int) (BOX_ALPHA * 255);

        BufferBuilder buf = tess.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        buf.addVertex(matrix, minX, minY, minZ).setColor(ri, gi, bi, ai);
        buf.addVertex(matrix, maxX, minY, minZ).setColor(ri, gi, bi, ai);
        buf.addVertex(matrix, maxX, minY, minZ).setColor(ri, gi, bi, ai);
        buf.addVertex(matrix, maxX, minY, maxZ).setColor(ri, gi, bi, ai);
        buf.addVertex(matrix, maxX, minY, maxZ).setColor(ri, gi, bi, ai);
        buf.addVertex(matrix, minX, minY, maxZ).setColor(ri, gi, bi, ai);
        buf.addVertex(matrix, minX, minY, maxZ).setColor(ri, gi, bi, ai);
        buf.addVertex(matrix, minX, minY, minZ).setColor(ri, gi, bi, ai);
        buf.addVertex(matrix, minX, maxY, minZ).setColor(ri, gi, bi, ai);
        buf.addVertex(matrix, maxX, maxY, minZ).setColor(ri, gi, bi, ai);
        buf.addVertex(matrix, maxX, maxY, minZ).setColor(ri, gi, bi, ai);
        buf.addVertex(matrix, maxX, maxY, maxZ).setColor(ri, gi, bi, ai);
        buf.addVertex(matrix, maxX, maxY, maxZ).setColor(ri, gi, bi, ai);
        buf.addVertex(matrix, minX, maxY, maxZ).setColor(ri, gi, bi, ai);
        buf.addVertex(matrix, minX, maxY, maxZ).setColor(ri, gi, bi, ai);
        buf.addVertex(matrix, minX, maxY, minZ).setColor(ri, gi, bi, ai);
        buf.addVertex(matrix, minX, minY, minZ).setColor(ri, gi, bi, ai);
        buf.addVertex(matrix, minX, maxY, minZ).setColor(ri, gi, bi, ai);
        buf.addVertex(matrix, maxX, minY, minZ).setColor(ri, gi, bi, ai);
        buf.addVertex(matrix, maxX, maxY, minZ).setColor(ri, gi, bi, ai);
        buf.addVertex(matrix, maxX, minY, maxZ).setColor(ri, gi, bi, ai);
        buf.addVertex(matrix, maxX, maxY, maxZ).setColor(ri, gi, bi, ai);
        buf.addVertex(matrix, minX, minY, maxZ).setColor(ri, gi, bi, ai);
        buf.addVertex(matrix, minX, maxY, maxZ).setColor(ri, gi, bi, ai);
        BufferUploader.drawWithShader(buf.buildOrThrow());

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();
    }
}
