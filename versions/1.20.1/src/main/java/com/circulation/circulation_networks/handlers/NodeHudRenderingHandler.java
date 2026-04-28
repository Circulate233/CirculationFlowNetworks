package com.circulation.circulation_networks.handlers;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.api.API;
import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.blocks.MultiblockShellBlock;
import com.circulation.circulation_networks.client.render.ClientAnimationTicker;
import com.circulation.circulation_networks.gui.GuiHub;
import com.circulation.circulation_networks.gui.component.base.AtlasRegion;
import com.circulation.circulation_networks.gui.component.base.ComponentAtlas;
import com.circulation.circulation_networks.packets.NodeHudRequest;
import com.circulation.circulation_networks.registry.RegistryEnergyHandler;
import com.circulation.circulation_networks.utils.CI18n;
import com.circulation.circulation_networks.utils.FormatNumberUtils;
import com.circulation.circulation_networks.utils.ScrollingTextHelper;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

@OnlyIn(Dist.CLIENT)
public final class NodeHudRenderingHandler {

    public static final NodeHudRenderingHandler INSTANCE = new NodeHudRenderingHandler();

    private static final int HUD_WIDTH = 165;
    private static final int HUD_HEIGHT = 93;
    private static final int CRYSTAL_SIZE = 50;
    private static final int TEXT_COLOR = 0x79d7ff;
    private static final float ROTATION_PERIOD_TICKS = 400.0f;
    private static final int REQUEST_INTERVAL = 20;
    private static final float WORLD_SCALE = 0.01F;
    private static final float MIN_SCALE = 0.006F;
    private static final float MAX_SCALE_DISTANCE = 6.0F;
    private static final float HUD_PULL_DIST = 0.3F;
    private static final float TILT_ANGLE = -10.0F;
    private static final float BG_ALPHA = 0.85F;

    private long cachedPosLong = Long.MIN_VALUE;
    private String displayName = "";
    private String formattedInput = "";
    private String formattedOutput = "";
    private String formattedLatency = "";
    private String formattedNodeCount = "";
    private boolean hasData;

    private long lastTargetPosLong = Long.MIN_VALUE;
    private int requestCooldown;

    private NodeHudRenderingHandler() {
    }

    public void updateData(long posLong, String displayName, String input, String output, String interactionTimeMicros, int nodeCount) {
        this.cachedPosLong = posLong;
        if (displayName == null || displayName.trim().isEmpty()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                this.displayName = mc.level.getBlockState(BlockPos.of(posLong)).getBlock().getName().getString();
            } else {
                this.displayName = CI18n.format("hud.node.unnamed");
            }
        } else {
            this.displayName = displayName;
        }
        var pair = RegistryEnergyHandler.getPair(GuiHub.getEnergyUnitState());
        this.formattedInput = "I:" + formatEnergy(input, pair);
        this.formattedOutput = "O:" + formatEnergy(output, pair);
        this.formattedLatency = CI18n.format("gui.hub.energy_latency", formatLatency(interactionTimeMicros));
        this.formattedNodeCount = CI18n.format("gui.hub.node_count", String.valueOf(nodeCount));
        this.hasData = true;
    }

    private String formatEnergy(String raw, RegistryEnergyHandler.Pair pair) {
        var e = EnergyAmount.obtain(raw);
        if (pair.multiplying() != 0) {
            e.divide(pair.multiplying());
        }
        String value = FormatNumberUtils.formatNumber(e) + " " + pair.unit() + "/t";
        e.recycle();
        return value;
    }

    private String formatLatency(String microsStr) {
        long micros;
        try {
            micros = Long.parseLong(microsStr);
        } catch (NumberFormatException e) {
            micros = 0L;
        }
        if (micros >= 100L) {
            return FormatNumberUtils.formatDouble(micros / 1000D, 1) + " ms";
        } else {
            return FormatNumberUtils.formatNumber(micros) + " μs";
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            hasData = false;
            return;
        }
        if (mc.hitResult instanceof BlockHitResult bhr) {
            BlockPos pos = MultiblockShellBlock.resolveRedirectedPos(mc.level, bhr.getBlockPos());
            if (API.getNodeAt(mc.level, pos) != null) {
                long posLong = pos.asLong();
                if (posLong != lastTargetPosLong) {
                    lastTargetPosLong = posLong;
                    requestCooldown = REQUEST_INTERVAL;
                    CirculationFlowNetworks.sendToServer(new NodeHudRequest(posLong));
                } else if (--requestCooldown <= 0) {
                    requestCooldown = REQUEST_INTERVAL;
                    CirculationFlowNetworks.sendToServer(new NodeHudRequest(posLong));
                }
                return;
            }
        }
        lastTargetPosLong = Long.MIN_VALUE;
        hasData = false;
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (!hasData) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (!(mc.hitResult instanceof BlockHitResult bhr)
            || MultiblockShellBlock.resolveRedirectedPos(mc.level, bhr.getBlockPos()).asLong() != cachedPosLong) {
            return;
        }

        var cameraPos = event.getCamera().getPosition();
        double cameraX = cameraPos.x;
        double cameraY = cameraPos.y;
        double cameraZ = cameraPos.z;

        BlockPos nodePos = BlockPos.of(cachedPosLong);
        double nodeX = nodePos.getX() + 0.5;
        double nodeY = nodePos.getY() + 0.5;
        double nodeZ = nodePos.getZ() + 0.5;
        double dx = cameraX - nodeX;
        double dy = cameraY - nodeY;
        double dz = cameraZ - nodeZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float distT = (float) Math.min(distance / MAX_SCALE_DISTANCE, 1.0);
        float scaleFactor = MIN_SCALE + distT * (WORLD_SCALE - MIN_SCALE);

        var hitLoc = bhr.getLocation();
        double hitX = hitLoc.x - cameraX;
        double hitY = hitLoc.y - cameraY;
        double hitZ = hitLoc.z - cameraZ;
        double hitDist = Math.sqrt(hitX * hitX + hitY * hitY + hitZ * hitZ);

        ComponentAtlas atlas = ComponentAtlas.INSTANCE;
        atlas.awaitReady();

        PoseStack mvStack = RenderSystem.getModelViewStack();
        mvStack.pushPose();
        mvStack.last().pose().set(event.getPoseStack().last().pose());
        mvStack.last().normal().set(event.getPoseStack().last().normal());
        double factor = hitDist > 1e-6 ? 1.0 - HUD_PULL_DIST / hitDist : 1.0;
        mvStack.translate(hitX * factor, hitY * factor, hitZ * factor);
        mvStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        mvStack.mulPose(Axis.YP.rotationDegrees(TILT_ANGLE));
        mvStack.scale(-scaleFactor, -scaleFactor, scaleFactor);
        RenderSystem.applyModelViewMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();

        float anchorX = 5;
        float anchorY = -HUD_HEIGHT / 2.0f;

        AtlasRegion bgRegion = atlas.getRegion("node_hud_base");
        if (bgRegion != null) {
            atlas.bind();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, BG_ALPHA);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            drawWorldQuad(bgRegion, anchorX, anchorY);
        }

        AtlasRegion crystalRegion = atlas.getRegion("node_hud_crystal");
        if (crystalRegion != null) {
            float partialTick = event.getPartialTick();
            long clientTick = ClientAnimationTicker.ticks();
            float angle = (clientTick + partialTick) * 360.0f / ROTATION_PERIOD_TICKS;
            float cx = anchorX + 20 + CRYSTAL_SIZE / 2.0f;
            float cy = anchorY + 20 + CRYSTAL_SIZE / 2.0f;
            drawRotatedRegion(atlas, crystalRegion, cx, cy, angle);
        }

        float partialTick = event.getPartialTick();
        Font font = mc.font;
        String tooltipText = CI18n.format("hud.node.network_data");
        int tooltipWidth = font.width(tooltipText) + 6;
        int tooltipHeight = 12;
        float tooltipY = anchorY - tooltipHeight - 2;
        drawColoredRect(anchorX, tooltipY, anchorX + tooltipWidth, tooltipY + tooltipHeight);

        var bufferSource = mc.renderBuffers().bufferSource();
        var textPose = new PoseStack();
        drawScrollingText(font, displayName, 66, anchorX + 86, anchorY + 13, TEXT_COLOR, partialTick, textPose, bufferSource);
        drawScrollingText(font, formattedInput, 62, anchorX + 90, anchorY + 26, TEXT_COLOR, partialTick, textPose, bufferSource);
        drawScrollingText(font, formattedOutput, 62, anchorX + 90, anchorY + 40, TEXT_COLOR, partialTick, textPose, bufferSource);
        drawScrollingText(font, formattedLatency, 62, anchorX + 90, anchorY + 54, TEXT_COLOR, partialTick, textPose, bufferSource);
        drawScrollingText(font, formattedNodeCount, 62, anchorX + 90, anchorY + 68, TEXT_COLOR, partialTick, textPose, bufferSource);
        font.drawInBatch(tooltipText, anchorX + 3, tooltipY + 2, 0xFFFFFF, false, textPose.last().pose(), bufferSource, Font.DisplayMode.SEE_THROUGH, 0, 15728880);
        bufferSource.endBatch();

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        mvStack.popPose();
        RenderSystem.applyModelViewMatrix();
    }

    private void drawScrollingText(Font font, String text, int maxWidth, float x, float y, int color, float partialTick,
                                   PoseStack textPose, MultiBufferSource.BufferSource bufferSource) {
        int textWidth = font.width(text);
        if (textWidth <= maxWidth) {
            font.drawInBatch(text, x, y, color, false, textPose.last().pose(), bufferSource, Font.DisplayMode.SEE_THROUGH, 0, 15728880);
            return;
        }
        bufferSource.endBatch();
        float offset = ScrollingTextHelper.getScrollOffset(textWidth, maxWidth, ClientAnimationTicker.ticks(), partialTick);
        font.drawInBatch(text, x - offset, y, color, false, textPose.last().pose(), bufferSource, Font.DisplayMode.SEE_THROUGH, 0, 15728880);
        enableHudScissor(x, y, maxWidth, 9);
        bufferSource.endBatch();
        disableHudScissor();
    }

    private void enableHudScissor(float hudX, float hudY, int width, int height) {
        Matrix4f mv = new Matrix4f(RenderSystem.getModelViewStack().last().pose());
        Matrix4f mvp = new Matrix4f(RenderSystem.getProjectionMatrix()).mul(mv);
        Vector4f c1 = mvp.transform(new Vector4f(hudX, hudY, 0, 1));
        Vector4f c2 = mvp.transform(new Vector4f(hudX + width, hudY, 0, 1));
        Vector4f c3 = mvp.transform(new Vector4f(hudX + width, hudY + height, 0, 1));
        Vector4f c4 = mvp.transform(new Vector4f(hudX, hudY + height, 0, 1));
        c1.div(c1.w);
        c2.div(c2.w);
        c3.div(c3.w);
        c4.div(c4.w);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        float sx1 = viewport[0] + viewport[2] * (c1.x + 1) / 2f;
        float sy1 = viewport[1] + viewport[3] * (c1.y + 1) / 2f;
        float sx2 = viewport[0] + viewport[2] * (c2.x + 1) / 2f;
        float sy2 = viewport[1] + viewport[3] * (c2.y + 1) / 2f;
        float sx3 = viewport[0] + viewport[2] * (c3.x + 1) / 2f;
        float sy3 = viewport[1] + viewport[3] * (c3.y + 1) / 2f;
        float sx4 = viewport[0] + viewport[2] * (c4.x + 1) / 2f;
        float sy4 = viewport[1] + viewport[3] * (c4.y + 1) / 2f;
        float minX = Math.min(Math.min(sx1, sx2), Math.min(sx3, sx4));
        float minY = Math.min(Math.min(sy1, sy2), Math.min(sy3, sy4));
        float maxX = Math.max(Math.max(sx1, sx2), Math.max(sx3, sx4));
        float maxY = Math.max(Math.max(sy1, sy2), Math.max(sy3, sy4));
        int rx = Math.round(minX);
        int ry = Math.round(minY);
        int rw = Math.round(maxX - minX);
        int rh = Math.round(maxY - minY);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(rx, ry, Math.max(rw, 1), Math.max(rh, 1));
    }

    private void disableHudScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private void drawRotatedRegion(ComponentAtlas atlas, AtlasRegion region,
                                   float cx, float cy, float angleDeg) {
        float rad = (float) Math.toRadians(angleDeg);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        float x0 = cx + (-(float) 25.0 * cos - (float) 25.0 * sin);
        float y0 = cy + (-(float) 25.0 * sin + (float) 25.0 * cos);
        float x1 = cx + ((float) 25.0 * cos - (float) 25.0 * sin);
        float y1 = cy + ((float) 25.0 * sin + (float) 25.0 * cos);
        float x2 = cx + ((float) 25.0 * cos + (float) 25.0 * sin);
        float y2 = cy + ((float) 25.0 * sin - (float) 25.0 * cos);
        float x3 = cx + (-(float) 25.0 * cos + (float) 25.0 * sin);
        float y3 = cy + (-(float) 25.0 * sin - (float) 25.0 * cos);

        atlas.bind();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buf.vertex(x0, y0, 0).uv(region.u0(), region.v1()).endVertex();
        buf.vertex(x1, y1, 0).uv(region.u1(), region.v1()).endVertex();
        buf.vertex(x2, y2, 0).uv(region.u1(), region.v0()).endVertex();
        buf.vertex(x3, y3, 0).uv(region.u0(), region.v0()).endVertex();
        tess.end();
    }

    private void drawWorldQuad(AtlasRegion region, float x, float y) {
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buf.vertex(x, y + (float) NodeHudRenderingHandler.HUD_HEIGHT, 0).uv(region.u0(), region.v1()).endVertex();
        buf.vertex(x + (float) NodeHudRenderingHandler.HUD_WIDTH, y + (float) NodeHudRenderingHandler.HUD_HEIGHT, 0).uv(region.u1(), region.v1()).endVertex();
        buf.vertex(x + (float) NodeHudRenderingHandler.HUD_WIDTH, y, 0).uv(region.u1(), region.v0()).endVertex();
        buf.vertex(x, y, 0).uv(region.u0(), region.v0()).endVertex();
        tess.end();
    }

    private void drawColoredRect(float x1, float y1, float x2, float y2) {
        float a = (float) (-1727004656 >> 24 & 255) / 255.0F;
        float r = (float) (-1727004656 >> 16 & 255) / 255.0F;
        float g = (float) (-1727004656 >> 8 & 255) / 255.0F;
        float b = (float) (-1727004656 & 255) / 255.0F;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buf.vertex(x1, y2, 0).color(r, g, b, a).endVertex();
        buf.vertex(x2, y2, 0).color(r, g, b, a).endVertex();
        buf.vertex(x2, y1, 0).color(r, g, b, a).endVertex();
        buf.vertex(x1, y1, 0).color(r, g, b, a).endVertex();
        tess.end();
    }
}
