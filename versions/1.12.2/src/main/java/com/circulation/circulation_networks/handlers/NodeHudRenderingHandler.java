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

    private static float[] projectToWindow(float objX, float objY, FloatBuffer mv, FloatBuffer proj, IntBuffer vp) {
        float eyeX = mv.get(0) * objX + mv.get(4) * objY + mv.get(12);
        float eyeY = mv.get(1) * objX + mv.get(5) * objY + mv.get(13);
        float eyeZ = mv.get(2) * objX + mv.get(6) * objY + mv.get(14);
        float eyeW = mv.get(3) * objX + mv.get(7) * objY + mv.get(15);
        float clipX = proj.get(0) * eyeX + proj.get(4) * eyeY + proj.get(8) * eyeZ + proj.get(12) * eyeW;
        float clipY = proj.get(1) * eyeX + proj.get(5) * eyeY + proj.get(9) * eyeZ + proj.get(13) * eyeW;
        float clipW = proj.get(3) * eyeX + proj.get(7) * eyeY + proj.get(11) * eyeZ + proj.get(15) * eyeW;
        float ndcX = clipX / clipW;
        float ndcY = clipY / clipW;
        return new float[]{vp.get(0) + vp.get(2) * (ndcX + 1) / 2f, vp.get(1) + vp.get(3) * (ndcY + 1) / 2f};
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

        GlStateManager.pushMatrix();
        double factor = hitDist > 1e-6 ? 1.0 - HUD_PULL_DIST / hitDist : 1.0;
        GlStateManager.translate(hitX * factor, hitY * factor, hitZ * factor);
        GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(mc.getRenderManager().playerViewX, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(TILT_ANGLE, 0.0F, 1.0F, 0.0F);
        GlStateManager.scale(-scaleFactor, -scaleFactor, scaleFactor);

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO
        );
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.disableDepth();

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
        int textX1 = (int) (anchorX + 86);
        int textX2 = (int) (anchorX + 90);
        int hudYI = (int) anchorY;
        drawScrollingText(mc.fontRenderer, displayName, 66, textX1, hudYI + 13, TEXT_COLOR, partialTick);
        drawScrollingText(mc.fontRenderer, formattedInput, 62, textX2, hudYI + 26, TEXT_COLOR, partialTick);
        drawScrollingText(mc.fontRenderer, formattedOutput, 62, textX2, hudYI + 40, TEXT_COLOR, partialTick);
        drawScrollingText(mc.fontRenderer, formattedLatency, 62, textX2, hudYI + 54, TEXT_COLOR, partialTick);
        drawScrollingText(mc.fontRenderer, formattedNodeCount, 62, textX2, hudYI + 68, TEXT_COLOR, partialTick);
        mc.fontRenderer.drawString(tooltipText, (int) (anchorX + 3), (int) (tooltipY + 2), 0xFFFFFF);

        GlStateManager.enableDepth();
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private void drawScrollingText(FontRenderer fr, String text, int maxWidth, int x, int y, int color, float partialTick) {
        int textWidth = fr.getStringWidth(text);
        if (textWidth <= maxWidth) {
            fr.drawString(text, x, y, color);
            return;
        }
        float offset = ScrollingTextHelper.getScrollOffset(textWidth, maxWidth, ClientAnimationTicker.ticks(), partialTick);
        enableHudScissor(x, y, maxWidth, 9);
        fr.drawString(text, x - (int) offset, y, color);
        disableHudScissor();
    }

    private void enableHudScissor(float hudX, float hudY, int width, int height) {
        FloatBuffer mv = BufferUtils.createFloatBuffer(16);
        FloatBuffer proj = BufferUtils.createFloatBuffer(16);
        IntBuffer vp = BufferUtils.createIntBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, mv);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, proj);
        GL11.glGetInteger(GL11.GL_VIEWPORT, vp);
        float[] c1 = projectToWindow(hudX, hudY, mv, proj, vp);
        float[] c2 = projectToWindow(hudX + width, hudY, mv, proj, vp);
        float[] c3 = projectToWindow(hudX + width, hudY + height, mv, proj, vp);
        float[] c4 = projectToWindow(hudX, hudY + height, mv, proj, vp);
        float minX = Math.min(Math.min(c1[0], c2[0]), Math.min(c3[0], c4[0]));
        float minY = Math.min(Math.min(c1[1], c2[1]), Math.min(c3[1], c4[1]));
        float maxX = Math.max(Math.max(c1[0], c2[0]), Math.max(c3[0], c4[0]));
        float maxY = Math.max(Math.max(c1[1], c2[1]), Math.max(c3[1], c4[1]));
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
