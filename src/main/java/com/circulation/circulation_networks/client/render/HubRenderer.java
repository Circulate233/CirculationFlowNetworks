package com.circulation.circulation_networks.client.render;

import com.circulation.circulation_networks.CFNConfig;
import com.circulation.circulation_networks.api.hub.IHubPlugin;
import com.circulation.circulation_networks.network.hub.HubCapabilitys;
import com.circulation.circulation_networks.tiles.nodes.BlockEntityHub;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.HUB_BASE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.HUB_CHANNEL_BEACON_DOWN_INSIDE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.HUB_CHANNEL_BEACON_DOWN_OUTSIDE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.HUB_CHANNEL_BEACON_MID_INSIDE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.HUB_CHANNEL_BEACON_MID_OUTSIDE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.HUB_CHANNEL_BEACON_TOP_INSIDE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.HUB_CHANNEL_BEACON_TOP_OUTSIDE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.HUB_CHANNEL_HOLA_BOTTOM;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.HUB_CHANNEL_HOLA_MIDDLE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.HUB_CHANNEL_HOLA_TOP;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.HUB_CHANNEL_RING_AERIALS_BASE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.HUB_CHANNEL_RING_AERIALS_EMISSIVE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.HUB_CRYSTAL;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.HUB_DEFAULT_PLUGIN_0;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.HUB_DEFAULT_PLUGIN_1;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.HUB_DEFAULT_PLUGIN_2;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.HUB_DEFAULT_PLUGIN_3;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.HUB_EMISSIVE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.HUB_EMPTY_PLUGIN;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.HUB_RING_DOWN_BASE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.HUB_RING_DOWN_EMISSIVE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.HUB_RING_UP_BASE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.HUB_RING_UP_EMISSIVE;

public final class HubRenderer implements BlockEntityRenderer<BlockEntityHub, CFNBlockEntityRenderState<BlockEntityHub>> {

    private static final float CENTER = 0.5F;
    private static final Identifier[] DEFAULT_PLUGIN_MODELS = {
        HUB_DEFAULT_PLUGIN_0,
        HUB_DEFAULT_PLUGIN_1,
        HUB_DEFAULT_PLUGIN_2,
        HUB_DEFAULT_PLUGIN_3
    };

    public HubRenderer(BlockEntityRendererProvider.Context context) {
    }

    private static void renderBaseCached(BlockEntityHub hub, PoseStack poseStack, BlockState state, long clientTicks, float partialTick) {
        float upperAngle = NodeRotationAnimation.hubUpperRingAngle(clientTicks, partialTick);
        float lowerAngle = NodeRotationAnimation.hubLowerRingAngle(clientTicks, partialTick);
        float crystalAngle = NodeRotationAnimation.hubCrystalAngle(clientTicks, partialTick);
        BlockPos ambientLightSamplePos = ambientLightSamplePos(hub);

        RotatingModelVBORenderer.renderAmbientLitNoCull(poseStack, hub.getLevel(), hub.getBlockPos(), ambientLightSamplePos, state, HUB_BASE,
            0.0F, CENTER, CENTER, CENTER, 0.0F, 1.0F, 0.0F);
        RotatingModelVBORenderer.renderFullBrightYAxis(poseStack, state, HUB_EMISSIVE,
            0.0F, CENTER, CENTER, CENTER);
        renderAmbientRotatingCached(hub, poseStack, state, HUB_RING_UP_BASE, upperAngle, HubRenderLayout.ringYOffset(),
            ambientLightSamplePos);
        renderFullBrightRotatingCached(poseStack, state, HUB_RING_UP_EMISSIVE, upperAngle, HubRenderLayout.ringYOffset());
        renderAmbientRotatingCached(hub, poseStack, state, HUB_RING_DOWN_BASE, lowerAngle, HubRenderLayout.ringYOffset(),
            ambientLightSamplePos);
        renderFullBrightRotatingCached(poseStack, state, HUB_RING_DOWN_EMISSIVE, lowerAngle, HubRenderLayout.ringYOffset());
        poseStack.pushPose();
        poseStack.translate(0.0D, HubRenderLayout.crystalYOffset(), 0.0D);
        RotatingModelVBORenderer.renderFullBrightNoCullYAxis(poseStack, state, HUB_CRYSTAL,
            crystalAngle, CENTER, CENTER, CENTER);
        poseStack.popPose();
    }

    private static void renderChannelCached(BlockEntityHub hub, PoseStack poseStack, BlockState state, long clientTicks, float partialTick) {
        if (!hasChannelPlugin(hub.getPlugins().getStackInSlot(0))) {
            return;
        }

        float crystalAngle = NodeRotationAnimation.hubCrystalAngle(clientTicks, partialTick);
        BlockPos ambientLightSamplePos = ambientLightSamplePos(hub);
        renderFullBrightStaticCached(poseStack, state, HUB_CHANNEL_BEACON_TOP_INSIDE, HubRenderLayout.channelBeamTopYOffset());
        renderFullBrightRotatingCached(poseStack, state, HUB_CHANNEL_BEACON_TOP_OUTSIDE, crystalAngle, HubRenderLayout.channelBeamTopYOffset());
        renderFullBrightStaticCached(poseStack, state, HUB_CHANNEL_BEACON_MID_INSIDE, HubRenderLayout.channelBeamMidYOffset());
        renderFullBrightRotatingCached(poseStack, state, HUB_CHANNEL_BEACON_MID_OUTSIDE, crystalAngle, HubRenderLayout.channelBeamMidYOffset());
        renderFullBrightStaticCached(poseStack, state, HUB_CHANNEL_BEACON_DOWN_INSIDE, HubRenderLayout.channelBeamDownYOffset());
        renderFullBrightRotatingCached(poseStack, state, HUB_CHANNEL_BEACON_DOWN_OUTSIDE, crystalAngle, HubRenderLayout.channelBeamDownYOffset());
        renderFullBrightRotatingCached(poseStack, state, HUB_CHANNEL_HOLA_TOP,
            NodeRotationAnimation.hubChannelTopAngle(clientTicks, partialTick), HubRenderLayout.channelYOffset());
        renderFullBrightRotatingCached(poseStack, state, HUB_CHANNEL_HOLA_MIDDLE,
            NodeRotationAnimation.hubChannelMiddleAngle(clientTicks, partialTick), HubRenderLayout.channelYOffset());
        renderFullBrightRotatingCached(poseStack, state, HUB_CHANNEL_HOLA_BOTTOM,
            NodeRotationAnimation.hubChannelBottomAngle(clientTicks, partialTick), HubRenderLayout.channelYOffset());
        renderAmbientRotatingCached(hub, poseStack, state, HUB_CHANNEL_RING_AERIALS_BASE,
            NodeRotationAnimation.hubChannelAerialAngle(clientTicks, partialTick), HubRenderLayout.channelYOffset(),
            ambientLightSamplePos);
        renderFullBrightRotatingCached(poseStack, state, HUB_CHANNEL_RING_AERIALS_EMISSIVE,
            NodeRotationAnimation.hubChannelAerialAngle(clientTicks, partialTick), HubRenderLayout.channelYOffset());
    }

    private static void renderPluginsCached(BlockEntityHub hub, PoseStack poseStack, BlockState state, long clientTicks, float partialTick) {
        for (int slot = 1; slot <= 4; slot++) {
            renderPluginCached(hub, poseStack, state, clientTicks, partialTick, slot);
        }
    }

    private static void renderPluginCached(BlockEntityHub hub, PoseStack poseStack, BlockState state, long clientTicks, float partialTick, int slot) {
        ItemStack stack = hub.getPlugins().getStackInSlot(slot);
        var offset = HubRenderLayout.cornerOffsetForSlot(slot);
        BlockPos renderPos = hub.getBlockPos().offset(offset.x(), offset.y(), offset.z());
        int rotationPeriodTicks = resolvePluginRotationPeriodTicks(stack);
        float angle = HubRenderLayout.isClockwiseCornerSlot(slot)
            ? NodeRotationAnimation.hubClockwisePluginAngle(clientTicks, partialTick, rotationPeriodTicks)
            : NodeRotationAnimation.hubCounterClockwisePluginAngle(clientTicks, partialTick, rotationPeriodTicks);

        poseStack.pushPose();
        poseStack.translate(offset.x(), offset.y(), offset.z());
        RotatingModelVBORenderer.renderFullBrightNoCullYAxis(poseStack, state,
            resolvePluginModel(stack, renderPos), angle, CENTER, CENTER, CENTER);
        poseStack.popPose();
    }

    private static void renderFullBrightStaticCached(PoseStack poseStack, BlockState state, Identifier modelLocation, double yOffset) {
        poseStack.pushPose();
        poseStack.translate(0.0D, yOffset, 0.0D);
        RotatingModelVBORenderer.renderFullBrightNoCullYAxis(poseStack, state, modelLocation,
            0.0F, CENTER, CENTER, CENTER);
        poseStack.popPose();
    }

    private static void renderFullBrightRotatingCached(PoseStack poseStack, BlockState state, Identifier modelLocation, float angle, double yOffset) {
        poseStack.pushPose();
        poseStack.translate(0.0D, yOffset, 0.0D);
        RotatingModelVBORenderer.renderFullBrightNoCullYAxis(poseStack, state, modelLocation,
            angle, CENTER, CENTER, CENTER);
        poseStack.popPose();
    }

    private static void renderAmbientRotatingCached(BlockEntityHub hub, PoseStack poseStack, BlockState state,
                                                    Identifier modelLocation, float angle, double yOffset, BlockPos lightSamplePos) {
        poseStack.pushPose();
        poseStack.translate(0.0D, yOffset, 0.0D);
        RotatingModelVBORenderer.renderAmbientLitNoCull(poseStack, hub.getLevel(), hub.getBlockPos(), lightSamplePos, state, modelLocation,
            angle, CENTER, CENTER, CENTER, 0.0F, 1.0F, 0.0F);
        poseStack.popPose();
    }

    private static BlockPos ambientLightSamplePos(BlockEntityHub hub) {
        return hub.getBlockPos().above(2);
    }

    private static boolean hasChannelPlugin(ItemStack stack) {
        return !stack.isEmpty()
            && stack.getItem() instanceof IHubPlugin plugin
            && plugin.getCapability() == HubCapabilitys.CHANNEL_CAPABILITY;
    }

    private static Identifier resolvePluginModel(ItemStack stack, BlockPos renderPos) {
        if (stack.isEmpty()) {
            return HUB_EMPTY_PLUGIN;
        }
        if (stack.getItem() instanceof IHubPlugin plugin) {
            Identifier explicitModel = plugin.getHubModelLocation();
            if (explicitModel != null) {
                return explicitModel;
            }
        }
        return DEFAULT_PLUGIN_MODELS[HubRenderLayout.defaultPluginModelIndex(renderPos.getX(), renderPos.getY(), renderPos.getZ())];
    }

    private static int resolvePluginRotationPeriodTicks(ItemStack stack) {
        if (!stack.isEmpty() && stack.getItem() instanceof IHubPlugin plugin) {
            return Math.max(1, plugin.getHubRotationPeriodTicks());
        }
        return IHubPlugin.DEFAULT_HUB_ROTATION_PERIOD_TICKS;
    }

    @Override
    public @NotNull CFNBlockEntityRenderState<BlockEntityHub> createRenderState() {
        return new CFNBlockEntityRenderState<>();
    }

    @Override
    public void extractRenderState(@NotNull BlockEntityHub blockEntity, @NotNull CFNBlockEntityRenderState<BlockEntityHub> renderState,
                                   float partialTick, @NotNull Vec3 cameraPos,
                                   ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderState.extractBase(blockEntity, renderState, crumblingOverlay);
        renderState.blockEntity = blockEntity;
        renderState.partialTick = partialTick;
    }

    @Override
    public void submit(@NotNull CFNBlockEntityRenderState<BlockEntityHub> renderState, @NotNull PoseStack poseStack,
                       @NotNull SubmitNodeCollector submitNodeCollector, @NotNull CameraRenderState cameraRenderState) {
        BlockEntityHub hub = renderState.blockEntity;
        if (hub == null) {
            return;
        }
        if (hub.getLevel() == null) {
            return;
        }
        if (!CFNConfig.NODE.rendering.animatedSpecialModels) {
            return;
        }

        long clientTicks = ClientAnimationTicker.ticks();
        BlockState state = hub.getBlockState();

        try (RotatingModelVBORenderer.RenderSession ignored = RotatingModelVBORenderer.beginRenderSession(submitNodeCollector)) {
            renderBaseCached(hub, poseStack, state, clientTicks, renderState.partialTick);
            renderChannelCached(hub, poseStack, state, clientTicks, renderState.partialTick);
            renderPluginsCached(hub, poseStack, state, clientTicks, renderState.partialTick);
        }
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public boolean shouldRender(@NotNull BlockEntityHub blockEntity, @NotNull Vec3 cameraPos) {
        return BlockEntityRenderer.super.shouldRender(blockEntity, cameraPos);
    }

    @Override
    public @NotNull AABB getRenderBoundingBox(@NotNull BlockEntityHub blockEntity) {
        BlockPos pos = blockEntity.getBlockPos();
        return new AABB(
            pos.getX() + HubRenderLayout.renderBoundsMinXZ(),
            pos.getY() + HubRenderLayout.renderBoundsMinY(),
            pos.getZ() + HubRenderLayout.renderBoundsMinXZ(),
            pos.getX() + HubRenderLayout.renderBoundsMaxXZ(),
            pos.getY() + HubRenderLayout.renderBoundsMaxY(),
            pos.getZ() + HubRenderLayout.renderBoundsMaxXZ()
        );
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
