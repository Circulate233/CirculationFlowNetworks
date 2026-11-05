package com.circulation.circulation_networks.client.render;

import com.circulation.circulation_networks.CFNConfig;
import com.circulation.circulation_networks.tiles.nodes.TileEntityLongDistanceRelayNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;

import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.HUB_CRYSTAL;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.HUB_RING_DOWN_BASE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.HUB_RING_DOWN_EMISSIVE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.HUB_RING_UP_BASE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.HUB_RING_UP_EMISSIVE;

@SideOnly(Side.CLIENT)
public final class LongDistanceRelayNodeRenderer extends TileEntitySpecialRenderer<TileEntityLongDistanceRelayNode> {
    private static final float CENTER = 0.5F;

    @Override
    public void render(@NotNull TileEntityLongDistanceRelayNode te, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        if (!te.hasWorld()) return;
        boolean animated = CFNConfig.NODE.rendering.animatedSpecialModels;
        long ticks = animated ? ClientAnimationTicker.ticks() : 0;
        float partial = animated ? partialTicks : 0;
        float upperAngle = NodeRotationAnimation.hubUpperRingAngle(ticks, partial);
        float lowerAngle = NodeRotationAnimation.hubLowerRingAngle(ticks, partial);
        float ringTravel = LongDistanceRelayLayout.ringTravel(ticks, partial);

        try (LegacyWorldRenderStateGuard guard = LegacyWorldRenderStateGuard.openHubPass("long-distance relay TESR")) {
            Minecraft.getMinecraft().entityRenderer.enableLightmap();
            guard.establishHubLightmapProfile();
            RotatingModelRenderHelper.RenderBatch batch = RotatingModelRenderHelper.beginBatch(te, x, y, z, destroyStage);
            if (batch == null) return;
            try {
                renderRing(batch, LongDistanceRelayLayout.MIDDLE_RING_Y_OFFSET + ringTravel,
                    HUB_RING_UP_BASE, HUB_RING_UP_EMISSIVE, upperAngle);
                renderRing(batch, LongDistanceRelayLayout.MIDDLE_RING_Y_OFFSET - ringTravel,
                    HUB_RING_DOWN_BASE, HUB_RING_DOWN_EMISSIVE, lowerAngle);
                GlStateManager.pushMatrix();
                try {
                    GlStateManager.translate(0, LongDistanceRelayLayout.CRYSTAL_Y_OFFSET, 0);
                    batch.renderAroundYAxisFullBright(HUB_CRYSTAL, NodeRotationAnimation.hubCrystalAngle(ticks, partial),
                        CENTER, CENTER, CENTER, te.getPos().up(1));
                } finally {
                    GlStateManager.popMatrix();
                }
            } finally {
                batch.end();
            }
        }
    }

    private static void renderRing(RotatingModelRenderHelper.RenderBatch batch, float yOffset,
                                   ResourceLocation baseModel, ResourceLocation emissiveModel, float angle) {
        var lightSamplePos = batch.getTileEntity().getPos().up(LongDistanceRelayLayout.ringLightSampleY(yOffset));
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(0, yOffset, 0);
            batch.renderAroundAxis(baseModel, angle, CENTER, CENTER, CENTER, 0, 1, 0, false, false, lightSamplePos);
            batch.renderAroundYAxisFullBright(emissiveModel, angle, CENTER, CENTER, CENTER, lightSamplePos);
        } finally {
            GlStateManager.popMatrix();
        }
    }
}
