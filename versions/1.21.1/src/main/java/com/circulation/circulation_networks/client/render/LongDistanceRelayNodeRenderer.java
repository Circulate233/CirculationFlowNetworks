package com.circulation.circulation_networks.client.render;

import com.circulation.circulation_networks.CFNConfig;
import com.circulation.circulation_networks.tiles.nodes.LongDistanceRelayNodeBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.HUB_CRYSTAL;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.HUB_RING_DOWN_BASE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.HUB_RING_DOWN_EMISSIVE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.HUB_RING_UP_BASE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.HUB_RING_UP_EMISSIVE;

public final class LongDistanceRelayNodeRenderer implements BlockEntityRenderer<LongDistanceRelayNodeBlockEntity> {
    private static final float CENTER = 0.5F;

    public LongDistanceRelayNodeRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(@NotNull LongDistanceRelayNodeBlockEntity te, float partialTick, @NotNull PoseStack poseStack,
                       @NotNull MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (te.getLevel() == null) return;
        boolean animated = CFNConfig.NODE.rendering.animatedSpecialModels;
        long ticks = animated ? ClientAnimationTicker.ticks() : 0;
        float partial = animated ? partialTick : 0;
        float upperAngle = NodeRotationAnimation.hubUpperRingAngle(ticks, partial);
        float lowerAngle = NodeRotationAnimation.hubLowerRingAngle(ticks, partial);
        float ringTravel = LongDistanceRelayLayout.ringTravel(ticks, partial);

        renderRing(te, poseStack, bufferSource, LongDistanceRelayLayout.MIDDLE_RING_Y_OFFSET + ringTravel,
            HUB_RING_UP_BASE, HUB_RING_UP_EMISSIVE, upperAngle);
        renderRing(te, poseStack, bufferSource, LongDistanceRelayLayout.MIDDLE_RING_Y_OFFSET - ringTravel,
            HUB_RING_DOWN_BASE, HUB_RING_DOWN_EMISSIVE, lowerAngle);
        poseStack.pushPose();
        try {
            poseStack.translate(0, LongDistanceRelayLayout.CRYSTAL_Y_OFFSET, 0);
            RotatingModelVBORenderer.renderFullBrightYAxisThroughBufferSource(poseStack, bufferSource, te.getBlockState(),
                HUB_CRYSTAL, NodeRotationAnimation.hubCrystalAngle(ticks, partial), CENTER, CENTER, CENTER);
        } finally {
            poseStack.popPose();
        }
    }

    private static void renderRing(LongDistanceRelayNodeBlockEntity te, PoseStack poseStack, MultiBufferSource bufferSource,
                                   float yOffset, ResourceLocation baseModel, ResourceLocation emissiveModel, float angle) {
        var lightSamplePos = te.getBlockPos().above(LongDistanceRelayLayout.ringLightSampleY(yOffset));
        poseStack.pushPose();
        try {
            poseStack.translate(0, yOffset, 0);
            RotatingModelVBORenderer.renderAmbientLitThroughBufferSource(poseStack, bufferSource, te.getLevel(), lightSamplePos,
                te.getBlockState(), baseModel, angle, CENTER, CENTER, CENTER, 0, 1, 0);
            RotatingModelVBORenderer.renderFullBrightYAxisThroughBufferSource(poseStack, bufferSource, te.getBlockState(),
                emissiveModel, angle, CENTER, CENTER, CENTER);
        } finally {
            poseStack.popPose();
        }
    }

    @Override
    public boolean shouldRenderOffScreen(@NotNull LongDistanceRelayNodeBlockEntity te) {
        return true;
    }
}
