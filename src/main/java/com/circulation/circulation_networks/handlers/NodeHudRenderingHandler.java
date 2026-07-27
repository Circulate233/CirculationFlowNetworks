package com.circulation.circulation_networks.handlers;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.api.API;
import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.blocks.MultiblockShellBlock;
import com.circulation.circulation_networks.client.compat.RenderSystemCompat;
import com.circulation.circulation_networks.client.render.ClientAnimationTicker;
import com.circulation.circulation_networks.gui.GuiHub;
import com.circulation.circulation_networks.gui.component.base.AtlasRegion;
import com.circulation.circulation_networks.gui.component.base.AtlasRenderHelper;
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
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector4f;

public final class NodeHudRenderingHandler {

    public static final NodeHudRenderingHandler INSTANCE = new NodeHudRenderingHandler();

    private static final int HUD_WIDTH = 165;
    private static final int HUD_HEIGHT = 93;
    private static final int CRYSTAL_SIZE = 50;
    private static final int TEXT_COLOR = 0xFF79d7ff;
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
    public void onClientTick(ClientTickEvent.Post event) {
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
    public void onRenderWorldLast(RenderLevelStageEvent.AfterTranslucentParticles event) {
        if (!hasData) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (!(mc.hitResult instanceof BlockHitResult bhr)
            || MultiblockShellBlock.resolveRedirectedPos(mc.level, bhr.getBlockPos()).asLong() != cachedPosLong) {
            return;
        }

        var cameraPos = mc.gameRenderer.getMainCamera().position();
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
        if (!atlas.isReady()) return;

        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.set(event.getModelViewMatrix());
        double factor = hitDist > 1e-6 ? 1.0 - HUD_PULL_DIST / hitDist : 1.0;
        modelViewStack.translate((float) (hitX * factor), (float) (hitY * factor), (float) (hitZ * factor));
        modelViewStack.rotate(RenderSystemCompat.getCameraOrientation(mc));
        modelViewStack.rotate(Axis.YP.rotationDegrees(TILT_ANGLE));
        modelViewStack.scale(scaleFactor, -scaleFactor, scaleFactor);

        float anchorX = 5;
        float anchorY = -HUD_HEIGHT / 2.0f;

        AtlasRegion bgRegion = atlas.getRegion("node_hud_base");
        if (bgRegion != null) {
            AtlasRenderHelper.drawRegion(atlas, bgRegion, anchorX, anchorY, HUD_WIDTH, HUD_HEIGHT, 255, 255, 255, Math.round(BG_ALPHA * 255.0F));
        }

        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        long clientTick = ClientAnimationTicker.ticks();

        AtlasRegion crystalRegion = atlas.getRegion("node_hud_crystal");
        if (crystalRegion != null) {
            float angle = (clientTick + partialTick) * 360.0f / ROTATION_PERIOD_TICKS;
            float cx = anchorX + 20 + CRYSTAL_SIZE / 2.0f;
            float cy = anchorY + 20 + CRYSTAL_SIZE / 2.0f;
            drawRotatedRegion(atlas, crystalRegion, cx, cy, angle);
        }

        Font font = mc.font;
        String tooltipText = CI18n.format("hud.node.network_data");
        int tooltipWidth = font.width(tooltipText) + 6;
        int tooltipHeight = 12;
        float tooltipY = anchorY - tooltipHeight - 2;
        AtlasRenderHelper.drawColoredQuad(anchorX, tooltipY, anchorX + tooltipWidth, tooltipY + tooltipHeight, -1727004656);

        var bufferSource = mc.renderBuffers().bufferSource();
        var textPose = new PoseStack();
        drawScrollingText(font, displayName, 66, anchorX + 86, anchorY + 13, clientTick, partialTick, textPose, bufferSource);
        drawScrollingText(font, formattedInput, 62, anchorX + 90, anchorY + 26, clientTick, partialTick, textPose, bufferSource);
        drawScrollingText(font, formattedOutput, 62, anchorX + 90, anchorY + 40, clientTick, partialTick, textPose, bufferSource);
        drawScrollingText(font, formattedLatency, 62, anchorX + 90, anchorY + 54, clientTick, partialTick, textPose, bufferSource);
        drawScrollingText(font, formattedNodeCount, 62, anchorX + 90, anchorY + 68, clientTick, partialTick, textPose, bufferSource);
        font.drawInBatch(tooltipText, anchorX + 3, tooltipY + 2, 0xFFFFFFFF, false, textPose.last().pose(), bufferSource, Font.DisplayMode.SEE_THROUGH, 0, 15728880);
        bufferSource.endBatch();

        modelViewStack.popMatrix();
    }

    private void drawScrollingText(Font font, String text, int maxWidth, float x, float y, long clientTick, float partialTick,
                                   PoseStack textPose, MultiBufferSource.BufferSource bufferSource) {
        int textWidth = font.width(text);
        if (textWidth <= maxWidth) {
            font.drawInBatch(text, x, y, NodeHudRenderingHandler.TEXT_COLOR, false, textPose.last().pose(), bufferSource, Font.DisplayMode.SEE_THROUGH, 0, 15728880);
            return;
        }
        bufferSource.endBatch();
        float offset = ScrollingTextHelper.getScrollOffset(textWidth, maxWidth, clientTick, partialTick);
        font.drawInBatch(text, x - offset, y, NodeHudRenderingHandler.TEXT_COLOR, false, textPose.last().pose(), bufferSource, Font.DisplayMode.SEE_THROUGH, 0, 15728880);
        enableHudScissor(x, y, maxWidth);
        bufferSource.endBatch();
        disableHudScissor();
    }

    private void enableHudScissor(float hudX, float hudY, int width) {
        Matrix4f mv = new Matrix4f(RenderSystem.getModelViewStack());
        Matrix4f mvp = RenderSystemCompat.getProjectionMatrix().mul(mv);
        Vector4f c1 = mvp.transform(new Vector4f(hudX, hudY, 0, 1));
        Vector4f c2 = mvp.transform(new Vector4f(hudX + width, hudY, 0, 1));
        Vector4f c3 = mvp.transform(new Vector4f(hudX + width, hudY + 9, 0, 1));
        Vector4f c4 = mvp.transform(new Vector4f(hudX, hudY + 9, 0, 1));
        c1.div(c1.w);
        c2.div(c2.w);
        c3.div(c3.w);
        c4.div(c4.w);
        Minecraft minecraft = Minecraft.getInstance();
        int viewportWidth = minecraft.getWindow().getWidth();
        int viewportHeight = minecraft.getWindow().getHeight();
        float sx1 = viewportWidth * (c1.x + 1.0F) * 0.5F;
        float sy1 = viewportHeight * (c1.y + 1.0F) * 0.5F;
        float sx2 = viewportWidth * (c2.x + 1.0F) * 0.5F;
        float sy2 = viewportHeight * (c2.y + 1.0F) * 0.5F;
        float sx3 = viewportWidth * (c3.x + 1.0F) * 0.5F;
        float sy3 = viewportHeight * (c3.y + 1.0F) * 0.5F;
        float sx4 = viewportWidth * (c4.x + 1.0F) * 0.5F;
        float sy4 = viewportHeight * (c4.y + 1.0F) * 0.5F;
        float minX = Math.min(Math.min(sx1, sx2), Math.min(sx3, sx4));
        float minY = Math.min(Math.min(sy1, sy2), Math.min(sy3, sy4));
        float maxX = Math.max(Math.max(sx1, sx2), Math.max(sx3, sx4));
        float maxY = Math.max(Math.max(sy1, sy2), Math.max(sy3, sy4));
        int rx = Math.round(minX);
        int ry = Math.round(minY);
        int rw = Math.round(maxX - minX);
        int rh = Math.round(maxY - minY);
        RenderSystem.enableScissorForRenderTypeDraws(rx, ry, Math.max(rw, 1), Math.max(rh, 1));
    }

    private void disableHudScissor() {
        RenderSystem.disableScissorForRenderTypeDraws();
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

        BufferBuilder buffer = Tesselator.getInstance()
                                                                                             .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buffer.addVertex(x0, y0, 0.0F).setUv(region.u0(), region.v1()).setColor(255, 255, 255, 255);
        buffer.addVertex(x1, y1, 0.0F).setUv(region.u1(), region.v1()).setColor(255, 255, 255, 255);
        buffer.addVertex(x2, y2, 0.0F).setUv(region.u1(), region.v0()).setColor(255, 255, 255, 255);
        buffer.addVertex(x3, y3, 0.0F).setUv(region.u0(), region.v0()).setColor(255, 255, 255, 255);
        atlas.guiRenderType().draw(buffer.buildOrThrow());
    }
}
