package com.circulation.circulation_networks.client.render;

import com.circulation.circulation_networks.CFNConfig;
import com.circulation.circulation_networks.tiles.nodes.RelayNodeBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.NODE_CRYSTAL;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.RELAY_BOTTOM_SPIRAL_BASE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.RELAY_BOTTOM_SPIRAL_EMISSIVE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.RELAY_TOP_SPIRAL_BASE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.RELAY_TOP_SPIRAL_EMISSIVE;

@OnlyIn(Dist.CLIENT)
public final class RelayNodeRenderer implements BlockEntityRenderer<RelayNodeBlockEntity> {

    private static final float CENTER = 0.5F;

    public RelayNodeRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(@NotNull RelayNodeBlockEntity te, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (!CFNConfig.NODE.rendering.animatedSpecialModels) {
            return;
        }

        long clientTicks = ClientAnimationTicker.ticks();
        float topAngle = NodeRotationAnimation.relayTopSpiralAngle(clientTicks, partialTick);
        float crystalAngle = NodeRotationAnimation.relayCrystalAngle(clientTicks, partialTick);
        float bottomAngle = NodeRotationAnimation.relayBottomSpiralAngle(clientTicks, partialTick);

        if (RotatingModelVBORenderer.getDestroyStage(te.getBlockPos()) >= 0) {
            BlockState state = te.getBlockState();
            RotatingModelVBORenderer.renderAmbientLitThroughBufferSource(poseStack, bufferSource, te.getLevel(), te.getBlockPos(), state, RELAY_TOP_SPIRAL_BASE,
                topAngle, CENTER, CENTER, CENTER, 0.0F, 1.0F, 0.0F);
            RotatingModelVBORenderer.renderFullBrightYAxisThroughBufferSource(poseStack, bufferSource, state, RELAY_TOP_SPIRAL_EMISSIVE,
                topAngle, CENTER, CENTER, CENTER);
            poseStack.pushPose();
            poseStack.translate(0.0F, NodeRotationAnimation.bobOffset(clientTicks, partialTick), 0.0F);
            RotatingModelVBORenderer.renderFullBrightYAxisThroughBufferSource(poseStack, bufferSource, state, NODE_CRYSTAL,
                crystalAngle, CENTER, CENTER, CENTER);
            poseStack.popPose();
            RotatingModelVBORenderer.renderAmbientLitThroughBufferSource(poseStack, bufferSource, te.getLevel(), te.getBlockPos(), state, RELAY_BOTTOM_SPIRAL_BASE,
                bottomAngle, CENTER, CENTER, CENTER, 0.0F, 1.0F, 0.0F);
            RotatingModelVBORenderer.renderFullBrightYAxisThroughBufferSource(poseStack, bufferSource, state, RELAY_BOTTOM_SPIRAL_EMISSIVE,
                bottomAngle, CENTER, CENTER, CENTER);
        } else {
            try (RotatingModelVBORenderer.RenderSession ignored = RotatingModelVBORenderer.beginRenderSession()) {
                RotatingModelVBORenderer.renderAmbientLit(poseStack, te.getLevel(), te.getBlockPos(), te.getBlockState(), RELAY_TOP_SPIRAL_BASE,
                    topAngle, CENTER, CENTER, CENTER, 0.0F, 1.0F, 0.0F);
                RotatingModelVBORenderer.renderFullBrightYAxis(poseStack, te.getBlockState(), RELAY_TOP_SPIRAL_EMISSIVE,
                    topAngle, CENTER, CENTER, CENTER);
                poseStack.pushPose();
                poseStack.translate(0.0F, NodeRotationAnimation.bobOffset(clientTicks, partialTick), 0.0F);
                RotatingModelVBORenderer.renderFullBrightYAxis(poseStack, te.getBlockState(), NODE_CRYSTAL,
                    crystalAngle, CENTER, CENTER, CENTER);
                poseStack.popPose();
                RotatingModelVBORenderer.renderAmbientLit(poseStack, te.getLevel(), te.getBlockPos(), te.getBlockState(), RELAY_BOTTOM_SPIRAL_BASE,
                    bottomAngle, CENTER, CENTER, CENTER, 0.0F, 1.0F, 0.0F);
                RotatingModelVBORenderer.renderFullBrightYAxis(poseStack, te.getBlockState(), RELAY_BOTTOM_SPIRAL_EMISSIVE,
                    bottomAngle, CENTER, CENTER, CENTER);
            }
        }
    }
}
