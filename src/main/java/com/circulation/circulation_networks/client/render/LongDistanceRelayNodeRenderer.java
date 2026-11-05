package com.circulation.circulation_networks.client.render;

import com.circulation.circulation_networks.CFNConfig;
import com.circulation.circulation_networks.tiles.nodes.BlockEntityLongDistanceRelayNode;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.HUB_CRYSTAL;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.HUB_RING_DOWN_BASE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.HUB_RING_DOWN_EMISSIVE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.HUB_RING_UP_BASE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.HUB_RING_UP_EMISSIVE;

public final class LongDistanceRelayNodeRenderer implements BlockEntityRenderer<BlockEntityLongDistanceRelayNode, CFNBlockEntityRenderState<BlockEntityLongDistanceRelayNode>> {
    private static final float CENTER = 0.5F;

    public LongDistanceRelayNodeRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public @NotNull CFNBlockEntityRenderState<BlockEntityLongDistanceRelayNode> createRenderState() {
        return new CFNBlockEntityRenderState<>();
    }

    @Override
    public void extractRenderState(@NotNull BlockEntityLongDistanceRelayNode blockEntity,
                                   @NotNull CFNBlockEntityRenderState<BlockEntityLongDistanceRelayNode> renderState,
                                   float partialTick, @NotNull Vec3 cameraPos,
                                   ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderState.extractBase(blockEntity, renderState, crumblingOverlay);
        renderState.blockEntity = blockEntity;
        renderState.partialTick = partialTick;
    }

    @Override
    public void submit(@NotNull CFNBlockEntityRenderState<BlockEntityLongDistanceRelayNode> renderState,
                       @NotNull PoseStack poseStack, @NotNull SubmitNodeCollector submitNodeCollector,
                       @NotNull CameraRenderState cameraRenderState) {
        BlockEntityLongDistanceRelayNode relay = renderState.blockEntity;
        if (relay == null || relay.getLevel() == null) return;
        boolean animated = CFNConfig.NODE.rendering.animatedSpecialModels;
        long ticks = animated ? ClientAnimationTicker.ticks() : 0;
        float partialTick = animated ? renderState.partialTick : 0;
        float travel = LongDistanceRelayLayout.ringTravel(ticks, partialTick);
        BlockState state = relay.getBlockState();

        try (RotatingModelVBORenderer.RenderSession ignored = RotatingModelVBORenderer.beginRenderSession(submitNodeCollector)) {
            renderRing(relay, poseStack, state, HUB_RING_UP_BASE, HUB_RING_UP_EMISSIVE,
                NodeRotationAnimation.hubUpperRingAngle(ticks, partialTick),
                LongDistanceRelayLayout.MIDDLE_RING_Y_OFFSET + travel);
            renderRing(relay, poseStack, state, HUB_RING_DOWN_BASE, HUB_RING_DOWN_EMISSIVE,
                NodeRotationAnimation.hubLowerRingAngle(ticks, partialTick),
                LongDistanceRelayLayout.MIDDLE_RING_Y_OFFSET - travel);
            poseStack.pushPose();
            poseStack.translate(0.0D, LongDistanceRelayLayout.CRYSTAL_Y_OFFSET, 0.0D);
            RotatingModelVBORenderer.renderFullBrightNoCullYAxis(poseStack, state, HUB_CRYSTAL,
                NodeRotationAnimation.hubCrystalAngle(ticks, partialTick), CENTER, CENTER, CENTER);
            poseStack.popPose();
        }
    }

    private static void renderRing(BlockEntityLongDistanceRelayNode relay, PoseStack poseStack, BlockState state,
                                   Identifier base, Identifier emissive, float angle, float yOffset) {
        poseStack.pushPose();
        poseStack.translate(0.0D, yOffset, 0.0D);
        BlockPos lightSamplePos = relay.getBlockPos().above(LongDistanceRelayLayout.ringLightSampleY(yOffset));
        RotatingModelVBORenderer.renderAmbientLitNoCull(poseStack, relay.getLevel(), relay.getBlockPos(), lightSamplePos,
            state, base, angle, CENTER, CENTER, CENTER, 0.0F, 1.0F, 0.0F);
        RotatingModelVBORenderer.renderFullBrightYAxis(poseStack, state, emissive, angle, CENTER, CENTER, CENTER);
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public @NotNull AABB getRenderBoundingBox(@NotNull BlockEntityLongDistanceRelayNode blockEntity) {
        BlockPos pos = blockEntity.getBlockPos();
        return new AABB(pos.getX() - 1, pos.getY(), pos.getZ() - 1,
            pos.getX() + 2, pos.getY() + LongDistanceRelayLayout.HEIGHT, pos.getZ() + 2);
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
