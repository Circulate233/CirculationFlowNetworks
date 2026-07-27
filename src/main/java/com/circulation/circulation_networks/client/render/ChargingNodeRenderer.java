package com.circulation.circulation_networks.client.render;

import com.circulation.circulation_networks.CFNConfig;
import com.circulation.circulation_networks.tiles.nodes.BlockEntityChargingNode;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.CHARGING_IN_BASE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.CHARGING_IN_EMISSIVE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.CHARGING_RING_BASE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.CHARGING_RING_EMISSIVE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.NODE_CRYSTAL;

public final class ChargingNodeRenderer implements BlockEntityRenderer<BlockEntityChargingNode, CFNBlockEntityRenderState<BlockEntityChargingNode>> {

    private static final float CENTER = 0.5F;

    public ChargingNodeRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public @NotNull CFNBlockEntityRenderState<BlockEntityChargingNode> createRenderState() {
        return new CFNBlockEntityRenderState<>();
    }

    @Override
    public void extractRenderState(@NotNull BlockEntityChargingNode blockEntity, @NotNull CFNBlockEntityRenderState<BlockEntityChargingNode> renderState,
                                   float partialTick, @NotNull Vec3 cameraPos,
                                   ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderState.extractBase(blockEntity, renderState, crumblingOverlay);
        renderState.blockEntity = blockEntity;
        renderState.partialTick = partialTick;
    }

    @Override
    public void submit(@NotNull CFNBlockEntityRenderState<BlockEntityChargingNode> renderState, @NotNull PoseStack poseStack,
                       @NotNull SubmitNodeCollector submitNodeCollector, @NotNull CameraRenderState cameraRenderState) {
        BlockEntityChargingNode te = renderState.blockEntity;
        if (te == null || te.getLevel() == null) {
            return;
        }

        if (!CFNConfig.NODE.rendering.animatedSpecialModels) {
            return;
        }

        long clientTicks = ClientAnimationTicker.ticks();
        float topAngle = NodeRotationAnimation.relayBottomSpiralAngle(clientTicks, renderState.partialTick);
        float crystalAngle = NodeRotationAnimation.relayCrystalAngle(clientTicks, renderState.partialTick);
        float bottomAngle = NodeRotationAnimation.relayBottomSpiralAngle(clientTicks, renderState.partialTick);

        try (RotatingModelVBORenderer.RenderSession ignored = RotatingModelVBORenderer.beginRenderSession(submitNodeCollector)) {
            BlockState state = te.getBlockState();
            RotatingModelVBORenderer.renderAmbientLit(poseStack, te.getLevel(), te.getBlockPos(), state, CHARGING_IN_BASE,
                topAngle, CENTER, CENTER, CENTER, 0.0F, 1.0F, 0.0F);
            RotatingModelVBORenderer.renderFullBrightYAxis(poseStack, state, CHARGING_IN_EMISSIVE,
                topAngle, CENTER, CENTER, CENTER);
            poseStack.pushPose();
            poseStack.translate(0.0F, NodeRotationAnimation.bobOffset(clientTicks, renderState.partialTick), 0.0F);
            RotatingModelVBORenderer.renderFullBrightYAxis(poseStack, state, NODE_CRYSTAL,
                crystalAngle, CENTER, CENTER, CENTER);
            poseStack.popPose();
            RotatingModelVBORenderer.renderAmbientLit(poseStack, te.getLevel(), te.getBlockPos(), state, CHARGING_RING_BASE,
                bottomAngle, CENTER, CENTER, CENTER, 0.0F, 1.0F, 0.0F);
            RotatingModelVBORenderer.renderFullBrightYAxis(poseStack, state, CHARGING_RING_EMISSIVE,
                bottomAngle, CENTER, CENTER, CENTER);
        }
    }
}
