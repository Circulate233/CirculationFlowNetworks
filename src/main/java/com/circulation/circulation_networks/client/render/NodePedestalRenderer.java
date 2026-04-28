package com.circulation.circulation_networks.client.render;

import com.circulation.circulation_networks.CFNConfig;
import com.circulation.circulation_networks.tiles.BlockEntityNodePedestal;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.PEDESTAL_BASE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.PEDESTAL_BASE_EMISSIVE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.PEDESTAL_FRAME_CLOCKWISE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.PEDESTAL_FRAME_COUNTER_CLOCKWISE;

public final class NodePedestalRenderer implements BlockEntityRenderer<BlockEntityNodePedestal, CFNBlockEntityRenderState<BlockEntityNodePedestal>> {

    private static final float CENTER = 0.5F;

    private static final float FRAME_PIVOT_X = 8.0F / 16.0F;
    private static final float FRAME_PIVOT_Y = 5.0F / 16.0F;
    private static final float FRAME_PIVOT_Z = 8.0F / 16.0F;

    public NodePedestalRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public @NotNull CFNBlockEntityRenderState<BlockEntityNodePedestal> createRenderState() {
        return new CFNBlockEntityRenderState<>();
    }

    @Override
    public void extractRenderState(@NotNull BlockEntityNodePedestal blockEntity, @NotNull CFNBlockEntityRenderState<BlockEntityNodePedestal> renderState,
                                   float partialTick, @NotNull net.minecraft.world.phys.Vec3 cameraPos,
                                   ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderState.extractBase(blockEntity, renderState, crumblingOverlay);
        renderState.blockEntity = blockEntity;
        renderState.partialTick = partialTick;
    }

    @Override
    public void submit(@NotNull CFNBlockEntityRenderState<BlockEntityNodePedestal> renderState, @NotNull PoseStack poseStack,
                       @NotNull SubmitNodeCollector submitNodeCollector, @NotNull CameraRenderState cameraRenderState) {
        BlockEntityNodePedestal te = renderState.blockEntity;
        if (te == null || te.getLevel() == null) {
            return;
        }

        if (!CFNConfig.NODE.rendering.animatedSpecialModels) {
            return;
        }

        long clientTicks = ClientAnimationTicker.ticks();

        try (RotatingModelVBORenderer.RenderSession ignored = RotatingModelVBORenderer.beginRenderSession(submitNodeCollector)) {
            BlockState state = te.getBlockState();
            RotatingModelVBORenderer.renderAmbientLit(poseStack, te.getLevel(), te.getBlockPos(), state, PEDESTAL_BASE,
                0.0F, CENTER, CENTER, CENTER, 0.0F, 1.0F, 0.0F);
            RotatingModelVBORenderer.renderFullBrightYAxis(poseStack, state, PEDESTAL_BASE_EMISSIVE,
                0.0F, CENTER, CENTER, CENTER);

            poseStack.pushPose();
            poseStack.translate(0.0F, NodeRotationAnimation.bobOffset(clientTicks, renderState.partialTick), 0.0F);

            RotatingModelVBORenderer.renderFullBright(poseStack, state, PEDESTAL_FRAME_CLOCKWISE,
                NodeRotationAnimation.pedestalClockwiseFrameAngle(clientTicks, renderState.partialTick),
                FRAME_PIVOT_X, FRAME_PIVOT_Y, FRAME_PIVOT_Z,
                NodeRotationAnimation.tiltedAxisXForZRotation(-22.5F),
                NodeRotationAnimation.tiltedAxisY(),
                NodeRotationAnimation.tiltedAxisZ());
            RotatingModelVBORenderer.renderFullBright(poseStack, state, PEDESTAL_FRAME_COUNTER_CLOCKWISE,
                NodeRotationAnimation.pedestalCounterClockwiseFrameAngle(clientTicks, renderState.partialTick),
                FRAME_PIVOT_X, FRAME_PIVOT_Y, FRAME_PIVOT_Z,
                NodeRotationAnimation.tiltedAxisXForZRotation(22.5F),
                NodeRotationAnimation.tiltedAxisY(),
                NodeRotationAnimation.tiltedAxisZ());

            poseStack.popPose();
        }
    }
}
