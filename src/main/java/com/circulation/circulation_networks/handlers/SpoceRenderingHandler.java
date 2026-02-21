package com.circulation.circulation_networks.handlers;

import com.circulation.circulation_networks.items.ItemInspectionTool;
import com.circulation.circulation_networks.registry.RegistryItems;
import com.circulation.circulation_networks.utils.BuckyBallGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

import java.util.Arrays;

public final class SpoceRenderingHandler {

    public static final SpoceRenderingHandler INSTANCE = new SpoceRenderingHandler();

    private TileEntity te;
    private float linkScope;
    private float energyScope;
    private float chargingScope;

    private float lastAnimProgress;
    private float animProgress;
    private float[] rs;

    public void setStaus(TileEntity te, double linkScope, double energyScope, double chargingScope) {
        this.te = te;
        this.linkScope = (float) linkScope;
        this.energyScope = (float) energyScope;
        this.chargingScope = (float) chargingScope;
        this.animProgress = 0;
        this.lastAnimProgress = 0;

        Integer[] indices = {0, 1, 2};
        float[] scopes = {this.linkScope, this.energyScope, this.chargingScope};

        Arrays.sort(indices, (a, b) -> Float.compare(scopes[b], scopes[a]));

        this.rs = new float[3];
        this.rs[indices[0]] = 1.0f;
        this.rs[indices[1]] = -1.0f;
        this.rs[indices[2]] = 1.0f;
    }

    private void clear() {
        this.te = null;
        this.linkScope = 0;
        this.energyScope = 0;
        this.chargingScope = 0;
        this.animProgress = 0;
        this.lastAnimProgress = 0;
        this.rs = null;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START && te != null) {
            lastAnimProgress = animProgress;
            if (animProgress < 1.0f) {
                animProgress = Math.min(animProgress + 0.025f, 1.0f);
            }
        }
    }

    private float easeOutCubic(float t) {
        return (float) (1 - Math.pow(1 - t, 3));
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (te == null) return;
        if (te.isInvalid()) {
            clear();
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.player;
        var pos = te.getPos();
        if (pos.distanceSq(p.posX, p.posY, p.posZ) > 2500) {
            clear();
            return;
        }
        var stack = p.getHeldItemMainhand();
        if (stack.getItem() == RegistryItems.inspectionTool
            && RegistryItems.inspectionTool.getMode(stack).isMode(ItemInspectionTool.Mode.Spoce)) {
            double renderPosX = p.lastTickPosX + (p.posX - p.lastTickPosX) * event.getPartialTicks();
            double renderPosY = p.lastTickPosY + (p.posY - p.lastTickPosY) * event.getPartialTicks();
            double renderPosZ = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * event.getPartialTicks();

            double x = pos.getX() + 0.5D - renderPosX;
            double y = pos.getY() + 0.5D - renderPosY;
            double z = pos.getZ() + 0.5D - renderPosZ;

            GlStateManager.pushMatrix();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.translate(x, y, z);

            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

            GlStateManager.disableTexture2D();
            GlStateManager.disableLighting();
            GlStateManager.disableCull();
            GlStateManager.depthMask(false);

            float time = mc.world.getTotalWorldTime() + event.getPartialTicks();
            float rotation = time * 0.8F;

            float currentFactor = easeOutCubic(lastAnimProgress + (animProgress - lastAnimProgress) * event.getPartialTicks());

            if (linkScope > 0) {
                final var radius = linkScope * currentFactor;
                draw(rotation * rs[0], 0, 0.4f, 0.8f, radius, 0.4f, 0.8f, 1);
            }
            if (energyScope > 0) {
                final var radius = energyScope * currentFactor;
                draw(rotation * rs[1], 0.4f, 0.2f, 0.8f, radius, 0.8f, 0.6f, 1);
            }
            if (chargingScope > 0) {
                final var radius = chargingScope * currentFactor;
                draw(rotation * rs[2], 0, 0.5f, 0.1f, radius, 0.4f, 1, 0.4f);
            }

            GlStateManager.depthMask(true);
            GlStateManager.enableCull();
            GlStateManager.enableLighting();
            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();

            GlStateManager.popMatrix();
        }
    }

    private void draw(float rotation, float r, float g, float b, float radius, float r1, float g1, float b1) {
        GlStateManager.pushMatrix();
        GlStateManager.rotate(rotation, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(rotation * 0.5F, 1.0F, 0.0F, 0.0F);
        drawSphere(r, g, b, radius, 0.2f);
        drawBuckyBallWireframe(r1, g1, b1, radius + 0.01f, 0.8f);
        GlStateManager.popMatrix();
    }

    private void drawSphere(float r, float g, float b, float radius, float alpha) {
        GlStateManager.color(r, g, b, alpha);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        final int slices = 32;
        final int stacks = 32;
        for (int i = 0; i < slices; i++) {
            double phi1 = Math.PI * (double) i / slices;
            double phi2 = Math.PI * (double) (i + 1) / slices;

            buffer.begin(GL11.GL_QUAD_STRIP, DefaultVertexFormats.POSITION_NORMAL);
            for (int j = 0; j <= stacks; j++) {
                double theta = 2.0 * Math.PI * (double) j / stacks;

                float x1 = (float) (radius * Math.sin(phi1) * Math.cos(theta));
                float y1 = (float) (radius * Math.cos(phi1));
                float z1 = (float) (radius * Math.sin(phi1) * Math.sin(theta));
                buffer.pos(x1, y1, z1).normal(x1 / radius, y1 / radius, z1 / radius).endVertex();

                float x2 = (float) (radius * Math.sin(phi2) * Math.cos(theta));
                float y2 = (float) (radius * Math.cos(phi2));
                float z2 = (float) (radius * Math.sin(phi2) * Math.sin(theta));
                buffer.pos(x2, y2, z2).normal(x2 / radius, y2 / radius, z2 / radius).endVertex();
            }
            tessellator.draw();
        }
    }

    private void drawBuckyBallWireframe(float r, float g, float b, float radius, float alpha) {
        GlStateManager.color(r, g, b, alpha);
        GlStateManager.glLineWidth(2.0f);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_NORMAL);

        for (int[] edge : BuckyBallGeometry.edges) {
            Vec3d v1 = BuckyBallGeometry.vertices.get(edge[0]);
            Vec3d v2 = BuckyBallGeometry.vertices.get(edge[1]);

            buffer.pos(v1.x * radius, v1.y * radius, v1.z * radius)
                  .normal((float) v1.x, (float) v1.y, (float) v1.z).endVertex();

            buffer.pos(v2.x * radius, v2.y * radius, v2.z * radius)
                  .normal((float) v2.x, (float) v2.y, (float) v2.z).endVertex();
        }

        tessellator.draw();
    }

}