package com.circulation.circulation_networks.client.render;

import com.circulation.circulation_networks.CFNConfig;
import com.circulation.circulation_networks.tiles.nodes.PortNodeBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.NODE_CRYSTAL;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.PORT_IN_BASE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.PORT_IN_EMISSIVE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.PORT_OUT_BASE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.PORT_OUT_EMISSIVE;

@OnlyIn(Dist.CLIENT)
public final class PortNodeRenderer implements BlockEntityRenderer<PortNodeBlockEntity> {

    private static final float CENTER = 0.5F;

    public PortNodeRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(@NotNull PortNodeBlockEntity te, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (!CFNConfig.NODE.rendering.animatedSpecialModels) {
            return;
        }

        long clientTicks = ClientAnimationTicker.ticks();
        float topAngle = NodeRotationAnimation.relayBottomSpiralAngle(clientTicks, partialTick);
        float crystalAngle = NodeRotationAnimation.relayCrystalAngle(clientTicks, partialTick);
        float bottomAngle = NodeRotationAnimation.relayTopSpiralAngle(clientTicks, partialTick);

        if (RotatingModelVBORenderer.getDestroyStage(te.getBlockPos()) >= 0) {
            BlockState state = te.getBlockState();
            RotatingModelVBORenderer.renderAmbientLitThroughBufferSource(poseStack, bufferSource, te.getLevel(), te.getBlockPos(), state, PORT_IN_BASE,
                topAngle, CENTER, CENTER, CENTER, 0.0F, 1.0F, 0.0F);
            RotatingModelVBORenderer.renderFullBrightYAxisThroughBufferSource(poseStack, bufferSource, state, PORT_IN_EMISSIVE,
                topAngle, CENTER, CENTER, CENTER);
            poseStack.pushPose();
            poseStack.translate(0.0F, NodeRotationAnimation.bobOffset(clientTicks, partialTick), 0.0F);
            RotatingModelVBORenderer.renderFullBrightYAxisThroughBufferSource(poseStack, bufferSource, state, NODE_CRYSTAL,
                crystalAngle, CENTER, CENTER, CENTER);
            poseStack.popPose();
            RotatingModelVBORenderer.renderAmbientLitThroughBufferSource(poseStack, bufferSource, te.getLevel(), te.getBlockPos(), state, PORT_OUT_BASE,
                bottomAngle, CENTER, CENTER, CENTER, 0.0F, 1.0F, 0.0F);
            RotatingModelVBORenderer.renderFullBrightYAxisThroughBufferSource(poseStack, bufferSource, state, PORT_OUT_EMISSIVE,
                bottomAngle, CENTER, CENTER, CENTER);
        } else {
            try (RotatingModelVBORenderer.RenderSession ignored = RotatingModelVBORenderer.beginRenderSession()) {
                RotatingModelVBORenderer.renderAmbientLit(poseStack, te.getLevel(), te.getBlockPos(), te.getBlockState(), PORT_IN_BASE,
                    topAngle, CENTER, CENTER, CENTER, 0.0F, 1.0F, 0.0F);
                RotatingModelVBORenderer.renderFullBrightYAxis(poseStack, te.getBlockState(), PORT_IN_EMISSIVE,
                    topAngle, CENTER, CENTER, CENTER);
                poseStack.pushPose();
                poseStack.translate(0.0F, NodeRotationAnimation.bobOffset(clientTicks, partialTick), 0.0F);
                RotatingModelVBORenderer.renderFullBrightYAxis(poseStack, te.getBlockState(), NODE_CRYSTAL,
                    crystalAngle, CENTER, CENTER, CENTER);
                poseStack.popPose();
                RotatingModelVBORenderer.renderAmbientLit(poseStack, te.getLevel(), te.getBlockPos(), te.getBlockState(), PORT_OUT_BASE,
                    bottomAngle, CENTER, CENTER, CENTER, 0.0F, 1.0F, 0.0F);
                RotatingModelVBORenderer.renderFullBrightYAxis(poseStack, te.getBlockState(), PORT_OUT_EMISSIVE,
                    bottomAngle, CENTER, CENTER, CENTER);
            }
        }
    }
}
