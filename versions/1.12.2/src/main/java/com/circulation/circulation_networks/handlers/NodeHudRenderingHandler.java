package com.circulation.circulation_networks.handlers;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.api.API;
import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.blocks.MultiblockShellBlock;
import com.circulation.circulation_networks.client.render.ClientAnimationTicker;
import com.circulation.circulation_networks.client.render.LegacyWorldRenderStateGuard;
import com.circulation.circulation_networks.gui.GuiHub;
import com.circulation.circulation_networks.gui.component.base.AtlasRegion;
import com.circulation.circulation_networks.gui.component.base.ComponentAtlas;
import com.circulation.circulation_networks.packets.NodeHudRequest;
import com.circulation.circulation_networks.registry.RegistryEnergyHandler;
import com.circulation.circulation_networks.utils.CI18n;
import com.circulation.circulation_networks.utils.FormatNumberUtils;
import com.circulation.circulation_networks.utils.ScrollingTextHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

@SideOnly(Side.CLIENT)
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

    private final FloatBuffer modelViewBuffer = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer projectionBuffer = BufferUtils.createFloatBuffer(16);
    private final IntBuffer viewportBuffer = BufferUtils.createIntBuffer(16);
    private final float[] modelViewMatrix = new float[16];
    private final float[] projectionMatrix = new float[16];
    private final int[] viewport = new int[4];
    private final float[] projectedCorners = new float[8];
    private final int[] scissorBox = new int[4];

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

    static final class HudProjection {

        private HudProjection() {
        }

        static void projectToWindow(float objX, float objY, float[] mv, float[] proj, int[] vp, float[] result, int offset) {
            float eyeX = mv[0] * objX + mv[4] * objY + mv[12];
            float eyeY = mv[1] * objX + mv[5] * objY + mv[13];
            float eyeZ = mv[2] * objX + mv[6] * objY + mv[14];
            float eyeW = mv[3] * objX + mv[7] * objY + mv[15];
            float clipX = proj[0] * eyeX + proj[4] * eyeY + proj[8] * eyeZ + proj[12] * eyeW;
            float clipY = proj[1] * eyeX + proj[5] * eyeY + proj[9] * eyeZ + proj[13] * eyeW;
            float clipW = proj[3] * eyeX + proj[7] * eyeY + proj[11] * eyeZ + proj[15] * eyeW;
            if (clipW == 0.0F || !Float.isFinite(clipW)) {
                throw new IllegalStateException("node HUD projection produced an invalid clip W");
            }
            float ndcX = clipX / clipW;
            float ndcY = clipY / clipW;
            result[offset] = vp[0] + vp[2] * (ndcX + 1) / 2f;
            result[offset + 1] = vp[1] + vp[3] * (ndcY + 1) / 2f;
        }

        static void computeScissorBox(float hudX, float hudY, int width, float[] mv, float[] proj,
                                      int[] vp, float[] corners, int[] result) {
            projectToWindow(hudX, hudY, mv, proj, vp, corners, 0);
            projectToWindow(hudX + width, hudY, mv, proj, vp, corners, 2);
            projectToWindow(hudX + width, hudY + 9, mv, proj, vp, corners, 4);
            projectToWindow(hudX, hudY + 9, mv, proj, vp, corners, 6);
            float minX = Math.min(Math.min(corners[0], corners[2]), Math.min(corners[4], corners[6]));
            float minY = Math.min(Math.min(corners[1], corners[3]), Math.min(corners[5], corners[7]));
            float maxX = Math.max(Math.max(corners[0], corners[2]), Math.max(corners[4], corners[6]));
            float maxY = Math.max(Math.max(corners[1], corners[3]), Math.max(corners[5], corners[7]));
            result[0] = Math.round(minX);
            result[1] = Math.round(minY);
            result[2] = Math.max(Math.round(maxX - minX), 1);
            result[3] = Math.max(Math.round(maxY - minY), 1);
        }
    }

    public void updateData(long posLong, String displayName, String input, String output, String interactionTimeMicros, int nodeCount) {
        this.cachedPosLong = posLong;
        if (displayName == null || displayName.trim().isEmpty()) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.world != null) {
                this.displayName = mc.world.getBlockState(BlockPos.fromLong(posLong)).getBlock().getLocalizedName();
            } else {
                this.displayName = CI18n.format("hud.node.unnamed");
            }
        } else {
            this.displayName = displayName;
        }
        RegistryEnergyHandler.Pair pair = RegistryEnergyHandler.getPair(GuiHub.getEnergyUnitState());
        this.formattedInput = "I:" + formatEnergy(input, pair);
        this.formattedOutput = "O:" + formatEnergy(output, pair);
        this.formattedLatency = CI18n.format("gui.hub.energy_latency", formatLatency(interactionTimeMicros));
        this.formattedNodeCount = CI18n.format("gui.hub.node_count", String.valueOf(nodeCount));
        this.hasData = true;
    }

    private String formatEnergy(String raw, RegistryEnergyHandler.Pair pair) {
        EnergyAmount e = EnergyAmount.obtain(raw);
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
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) {
            hasData = false;
            return;
        }
        if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == RayTraceResult.Type.BLOCK) {
            BlockPos hitPos = mc.objectMouseOver.getBlockPos();
            BlockPos pos = MultiblockShellBlock.resolveRedirectedPos(mc.world, hitPos);
            if (API.getNodeAt(mc.world, pos) != null) {
                long posLong = pos.toLong();
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
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!hasData) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;
        if (mc.objectMouseOver == null || mc.objectMouseOver.typeOfHit != RayTraceResult.Type.BLOCK
            || MultiblockShellBlock.resolveRedirectedPos(mc.world, mc.objectMouseOver.getBlockPos()).toLong() != cachedPosLong) {
            return;
        }

        float partialTick = event.getPartialTicks();
        double cameraX = mc.player.lastTickPosX + (mc.player.posX - mc.player.lastTickPosX) * partialTick;
        double cameraY = mc.player.lastTickPosY + (mc.player.posY - mc.player.lastTickPosY) * partialTick;
        double cameraZ = mc.player.lastTickPosZ + (mc.player.posZ - mc.player.lastTickPosZ) * partialTick;

        BlockPos nodePos = BlockPos.fromLong(cachedPosLong);
        double nodeX = nodePos.getX() + 0.5;
        double nodeY = nodePos.getY() + 0.5;
        double nodeZ = nodePos.getZ() + 0.5;
        double dx = cameraX - nodeX;
        double dy = cameraY - nodeY;
        double dz = cameraZ - nodeZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float distT = (float) Math.min(distance / MAX_SCALE_DISTANCE, 1.0);
        float scaleFactor = MIN_SCALE + distT * (WORLD_SCALE - MIN_SCALE);

        double hitX = mc.objectMouseOver.hitVec.x - cameraX;
        double hitY = mc.objectMouseOver.hitVec.y - cameraY;
        double hitZ = mc.objectMouseOver.hitVec.z - cameraZ;
        double hitDist = Math.sqrt(hitX * hitX + hitY * hitY + hitZ * hitZ);

        ComponentAtlas atlas = ComponentAtlas.INSTANCE;
        atlas.awaitReady();

        try (LegacyWorldRenderStateGuard ignored = LegacyWorldRenderStateGuard.openNodeHudPass("node HUD")) {
            renderHud(mc, atlas, partialTick, hitX, hitY, hitZ, hitDist, scaleFactor);
        }
    }

    private void renderHud(Minecraft mc, ComponentAtlas atlas, float partialTick, double hitX, double hitY,
                           double hitZ, double hitDist, float scaleFactor) {
        GlStateManager.pushMatrix();
        try {
            double factor = hitDist > 1e-6 ? 1.0 - HUD_PULL_DIST / hitDist : 1.0;
            GlStateManager.translate(hitX * factor, hitY * factor, hitZ * factor);
            GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(mc.getRenderManager().playerViewX, 1.0F, 0.0F, 0.0F);
            GlStateManager.rotate(TILT_ANGLE, 0.0F, 1.0F, 0.0F);
            GlStateManager.scale(-scaleFactor, -scaleFactor, scaleFactor);

            float anchorX = 5;
            float anchorY = -HUD_HEIGHT / 2.0f;

            AtlasRegion bgRegion = atlas.getRegion("node_hud_base");
            if (bgRegion != null) {
                atlas.bind();
                GlStateManager.color(1.0F, 1.0F, 1.0F, BG_ALPHA);
                drawWorldQuad(bgRegion, anchorX, anchorY);
            }

            AtlasRegion crystalRegion = atlas.getRegion("node_hud_crystal");
            if (crystalRegion != null) {
                long clientTick = ClientAnimationTicker.ticks();
                float angle = (clientTick + partialTick) * 360.0f / ROTATION_PERIOD_TICKS;
                float cx = anchorX + 20 + CRYSTAL_SIZE / 2.0f;
                float cy = anchorY + 20 + CRYSTAL_SIZE / 2.0f;
                drawRotatedRegion(atlas, crystalRegion, cx, cy, angle);
            }

            String tooltipText = CI18n.format("hud.node.network_data");
            int tooltipWidth = mc.fontRenderer.getStringWidth(tooltipText) + 6;
            int tooltipHeight = 12;
            float tooltipY = anchorY - tooltipHeight - 2;
            drawColoredRect(anchorX, tooltipY, anchorX + tooltipWidth, tooltipY + tooltipHeight);

            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            captureHudProjection();
            int textX1 = (int) (anchorX + 86);
            int textX2 = (int) (anchorX + 90);
            int hudYI = (int) anchorY;
            drawScrollingText(mc.fontRenderer, displayName, 66, textX1, hudYI + 13, partialTick);
            drawScrollingText(mc.fontRenderer, formattedInput, 62, textX2, hudYI + 26, partialTick);
            drawScrollingText(mc.fontRenderer, formattedOutput, 62, textX2, hudYI + 40, partialTick);
            drawScrollingText(mc.fontRenderer, formattedLatency, 62, textX2, hudYI + 54, partialTick);
            drawScrollingText(mc.fontRenderer, formattedNodeCount, 62, textX2, hudYI + 68, partialTick);
            mc.fontRenderer.drawString(tooltipText, (int) (anchorX + 3), (int) (tooltipY + 2), 0xFFFFFF);
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private void drawScrollingText(FontRenderer fr, String text, int maxWidth, int x, int y, float partialTick) {
        int textWidth = fr.getStringWidth(text);
        if (textWidth <= maxWidth) {
            fr.drawString(text, x, y, TEXT_COLOR);
            return;
        }
        float offset = ScrollingTextHelper.getScrollOffset(textWidth, maxWidth, ClientAnimationTicker.ticks(), partialTick);
        enableHudScissor(x, y, maxWidth);
        try {
            fr.drawString(text, x - (int) offset, y, TEXT_COLOR);
        } finally {
            disableHudScissor();
        }
    }

    private void captureHudProjection() {
        modelViewBuffer.clear();
        projectionBuffer.clear();
        viewportBuffer.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelViewBuffer);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projectionBuffer);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewportBuffer);
        modelViewBuffer.get(modelViewMatrix);
        projectionBuffer.get(projectionMatrix);
        viewportBuffer.get(viewport);
    }

    private void enableHudScissor(float hudX, float hudY, int width) {
        HudProjection.computeScissorBox(hudX, hudY, width, modelViewMatrix, projectionMatrix, viewport,
            projectedCorners, scissorBox);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
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
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO
        );
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buf.pos(x0, y0, 0).tex(region.u0(), region.v1()).endVertex();
        buf.pos(x1, y1, 0).tex(region.u1(), region.v1()).endVertex();
        buf.pos(x2, y2, 0).tex(region.u1(), region.v0()).endVertex();
        buf.pos(x3, y3, 0).tex(region.u0(), region.v0()).endVertex();
        tess.draw();
    }

    private void drawWorldQuad(AtlasRegion region, float x, float y) {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buf.pos(x, y + (float) NodeHudRenderingHandler.HUD_HEIGHT, 0).tex(region.u0(), region.v1()).endVertex();
        buf.pos(x + (float) NodeHudRenderingHandler.HUD_WIDTH, y + (float) NodeHudRenderingHandler.HUD_HEIGHT, 0).tex(region.u1(), region.v1()).endVertex();
        buf.pos(x + (float) NodeHudRenderingHandler.HUD_WIDTH, y, 0).tex(region.u1(), region.v0()).endVertex();
        buf.pos(x, y, 0).tex(region.u0(), region.v0()).endVertex();
        tess.draw();
    }

    private void drawColoredRect(float x1, float y1, float x2, float y2) {
        float a = (float) (-1727004656 >> 24 & 255) / 255.0F;
        float r = (float) (-1727004656 >> 16 & 255) / 255.0F;
        float g = (float) (-1727004656 >> 8 & 255) / 255.0F;
        float b = (float) (-1727004656 & 255) / 255.0F;
        GlStateManager.disableTexture2D();
        GlStateManager.color(r, g, b, a);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
        buf.pos(x1, y2, 0).endVertex();
        buf.pos(x2, y2, 0).endVertex();
        buf.pos(x2, y1, 0).endVertex();
        buf.pos(x1, y1, 0).endVertex();
        tess.draw();
        GlStateManager.enableTexture2D();
    }
}
