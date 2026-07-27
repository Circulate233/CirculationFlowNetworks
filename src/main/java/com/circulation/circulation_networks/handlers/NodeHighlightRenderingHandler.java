package com.circulation.circulation_networks.handlers;

import com.circulation.circulation_networks.client.render.ClientAnimationTicker;
import com.circulation.circulation_networks.utils.WorldResolveCompat;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.Optional;

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
    private static final RenderPipeline HIGHLIGHT_LINES_PIPELINE = RenderPipelines.LINES.toBuilder()
                                                                                        .withLocation("pipeline/cfn_node_highlight_lines")
                                                                                        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                                                                                        .withDepthStencilState(Optional.empty())
                                                                                        .build();
    private static final RenderType HIGHLIGHT_RENDER_TYPE = RenderType.create(
        "cfn_node_highlight_lines",
        RenderSetup.builder(HIGHLIGHT_LINES_PIPELINE).createRenderSetup()
    );
    private BlockPos targetPos;
    private String targetDimId;
    private long startTick;

    private NodeHighlightRenderingHandler() {
    }

    private static void addLine(VertexConsumer builder, PoseStack.Pose pose, Matrix4f matrix,
                                float x1, float y1, float z1, float x2, float y2, float z2,
                                int r, int g, int b, int a) {
        float nx = x2 - x1;
        float ny = y2 - y1;
        float nz = z2 - z1;
        builder.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setNormal(pose, nx, ny, nz).setLineWidth(LINE_WIDTH);
        builder.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setNormal(pose, nx, ny, nz).setLineWidth(LINE_WIDTH);
    }

    public void highlight(BlockPos pos, String dimId) {
        this.targetPos = pos;
        this.targetDimId = dimId;
        this.startTick = ClientAnimationTicker.ticks();
    }

    public void clear() {
        targetPos = null;
        targetDimId = null;
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Pre event) {
        long clientTick = ClientAnimationTicker.ticks();
        if (targetPos != null && clientTick - startTick > HIGHLIGHT_DURATION_TICKS) {
            targetPos = null;
            targetDimId = null;
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderLevelStageEvent.AfterTranslucentParticles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || targetPos == null) {
            return;
        }
        if (!WorldResolveCompat.getDimensionId(mc.level).equals(targetDimId)) {
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
        var cameraPos = mc.gameRenderer.getMainCamera().position();
        renderHighlight(event.getPoseStack(), mc.renderBuffers().bufferSource(), cameraPos.x, cameraPos.y, cameraPos.z, targetPos);
    }

    private void renderHighlight(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, double camX, double camY, double camZ, BlockPos pos) {
        double x = pos.getX() - camX;
        double y = pos.getY() - camY;
        double z = pos.getZ() - camZ;

        poseStack.pushPose();
        poseStack.translate(x, y, z);

        var matrix = poseStack.last().pose();
        var pose = poseStack.last();

        float minX = (float) (0.0D - EXPAND);
        float minY = (float) (0.0D - EXPAND);
        float minZ = (float) (0.0D - EXPAND);
        float maxX = (float) (1.0D + EXPAND);
        float maxY = (float) (1.0D + EXPAND);
        float maxZ = (float) (1.0D + EXPAND);

        int ri = (int) (BOX_R * 255), gi = (int) (BOX_G * 255), bi = (int) (BOX_B * 255), ai = (int) (BOX_ALPHA * 255);

        VertexConsumer builder = bufferSource.getBuffer(HIGHLIGHT_RENDER_TYPE);
        addLine(builder, pose, matrix, minX, minY, minZ, maxX, minY, minZ, ri, gi, bi, ai);
        addLine(builder, pose, matrix, maxX, minY, minZ, maxX, minY, maxZ, ri, gi, bi, ai);
        addLine(builder, pose, matrix, maxX, minY, maxZ, minX, minY, maxZ, ri, gi, bi, ai);
        addLine(builder, pose, matrix, minX, minY, maxZ, minX, minY, minZ, ri, gi, bi, ai);
        addLine(builder, pose, matrix, minX, maxY, minZ, maxX, maxY, minZ, ri, gi, bi, ai);
        addLine(builder, pose, matrix, maxX, maxY, minZ, maxX, maxY, maxZ, ri, gi, bi, ai);
        addLine(builder, pose, matrix, maxX, maxY, maxZ, minX, maxY, maxZ, ri, gi, bi, ai);
        addLine(builder, pose, matrix, minX, maxY, maxZ, minX, maxY, minZ, ri, gi, bi, ai);
        addLine(builder, pose, matrix, minX, minY, minZ, minX, maxY, minZ, ri, gi, bi, ai);
        addLine(builder, pose, matrix, maxX, minY, minZ, maxX, maxY, minZ, ri, gi, bi, ai);
        addLine(builder, pose, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, ri, gi, bi, ai);
        addLine(builder, pose, matrix, minX, minY, maxZ, minX, maxY, maxZ, ri, gi, bi, ai);
        bufferSource.endBatch(HIGHLIGHT_RENDER_TYPE);
        poseStack.popPose();
    }
}
