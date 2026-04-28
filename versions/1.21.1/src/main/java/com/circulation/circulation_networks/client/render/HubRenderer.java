package com.circulation.circulation_networks.client.render;

import com.circulation.circulation_networks.api.hub.IHubPlugin;
import com.circulation.circulation_networks.network.hub.HubCapabilitys;
import com.circulation.circulation_networks.tiles.nodes.HubBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
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

@OnlyIn(Dist.CLIENT)
public final class HubRenderer implements BlockEntityRenderer<HubBlockEntity> {

    private static final float CENTER = 0.5F;
    private static final ResourceLocation[] DEFAULT_PLUGIN_MODELS = {
        HUB_DEFAULT_PLUGIN_0,
        HUB_DEFAULT_PLUGIN_1,
        HUB_DEFAULT_PLUGIN_2,
        HUB_DEFAULT_PLUGIN_3
    };

    public HubRenderer(BlockEntityRendererProvider.Context context) {
    }

    private static void renderBaseThroughBufferSource(HubBlockEntity hub, PoseStack poseStack, MultiBufferSource bufferSource, BlockState state, long worldTime, float partialTick) {
        float upperAngle = NodeRotationAnimation.hubUpperRingAngle(worldTime, partialTick);
        float lowerAngle = NodeRotationAnimation.hubLowerRingAngle(worldTime, partialTick);
        float crystalAngle = NodeRotationAnimation.hubCrystalAngle(worldTime, partialTick);
        BlockPos ambientLightSamplePos = ambientLightSamplePos(hub);

        RotatingModelVBORenderer.renderAmbientLitThroughBufferSourceAt(poseStack, bufferSource, hub.getLevel(), ambientLightSamplePos, state, HUB_BASE,
            0.0F, CENTER, CENTER, CENTER, 0.0F, 1.0F, 0.0F);
        RotatingModelVBORenderer.renderFullBrightYAxisThroughBufferSource(poseStack, bufferSource, state, HUB_EMISSIVE,
            0.0F, CENTER, CENTER, CENTER);
        renderAmbientRotatingThroughBufferSource(hub, poseStack, bufferSource, state, HUB_RING_UP_BASE, upperAngle, HubRenderLayout.ringYOffset(),
            ambientLightSamplePos);
        renderFullBrightRotatingThroughBufferSource(poseStack, bufferSource, state, HUB_RING_UP_EMISSIVE, upperAngle, HubRenderLayout.ringYOffset());
        renderAmbientRotatingThroughBufferSource(hub, poseStack, bufferSource, state, HUB_RING_DOWN_BASE, lowerAngle, HubRenderLayout.ringYOffset(),
            ambientLightSamplePos);
        renderFullBrightRotatingThroughBufferSource(poseStack, bufferSource, state, HUB_RING_DOWN_EMISSIVE, lowerAngle, HubRenderLayout.ringYOffset());
        poseStack.pushPose();
        poseStack.translate(0.0D, HubRenderLayout.crystalYOffset(), 0.0D);
        RotatingModelVBORenderer.renderFullBrightYAxisThroughBufferSource(poseStack, bufferSource, state, HUB_CRYSTAL,
            crystalAngle, CENTER, CENTER, CENTER);
        poseStack.popPose();
    }

    private static void renderBaseCached(HubBlockEntity hub, PoseStack poseStack, BlockState state, long worldTime, float partialTick) {
        float upperAngle = NodeRotationAnimation.hubUpperRingAngle(worldTime, partialTick);
        float lowerAngle = NodeRotationAnimation.hubLowerRingAngle(worldTime, partialTick);
        float crystalAngle = NodeRotationAnimation.hubCrystalAngle(worldTime, partialTick);
        BlockPos ambientLightSamplePos = ambientLightSamplePos(hub);

        RotatingModelVBORenderer.renderAmbientLit(poseStack, hub.getLevel(), hub.getBlockPos(), ambientLightSamplePos, state, HUB_BASE,
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
        RotatingModelVBORenderer.renderFullBrightYAxis(poseStack, state, HUB_CRYSTAL,
            crystalAngle, CENTER, CENTER, CENTER);
        poseStack.popPose();
    }

    private static void renderChannelThroughBufferSource(HubBlockEntity hub, PoseStack poseStack, MultiBufferSource bufferSource, BlockState state, long worldTime, float partialTick) {
        if (!hasChannelPlugin(hub.getPlugins().getStackInSlot(0))) {
            return;
        }

        float crystalAngle = NodeRotationAnimation.hubCrystalAngle(worldTime, partialTick);
        BlockPos ambientLightSamplePos = ambientLightSamplePos(hub);
        renderFullBrightStaticThroughBufferSource(poseStack, bufferSource, state, HUB_CHANNEL_BEACON_TOP_INSIDE, HubRenderLayout.channelBeamTopYOffset());
        renderFullBrightRotatingThroughBufferSource(poseStack, bufferSource, state, HUB_CHANNEL_BEACON_TOP_OUTSIDE, crystalAngle, HubRenderLayout.channelBeamTopYOffset());
        renderFullBrightStaticThroughBufferSource(poseStack, bufferSource, state, HUB_CHANNEL_BEACON_MID_INSIDE, HubRenderLayout.channelBeamMidYOffset());
        renderFullBrightRotatingThroughBufferSource(poseStack, bufferSource, state, HUB_CHANNEL_BEACON_MID_OUTSIDE, crystalAngle, HubRenderLayout.channelBeamMidYOffset());
        renderFullBrightStaticThroughBufferSource(poseStack, bufferSource, state, HUB_CHANNEL_BEACON_DOWN_INSIDE, HubRenderLayout.channelBeamDownYOffset());
        renderFullBrightRotatingThroughBufferSource(poseStack, bufferSource, state, HUB_CHANNEL_BEACON_DOWN_OUTSIDE, crystalAngle, HubRenderLayout.channelBeamDownYOffset());
        renderFullBrightRotatingThroughBufferSource(poseStack, bufferSource, state, HUB_CHANNEL_HOLA_TOP,
            NodeRotationAnimation.hubChannelTopAngle(worldTime, partialTick), HubRenderLayout.channelYOffset());
        renderFullBrightRotatingThroughBufferSource(poseStack, bufferSource, state, HUB_CHANNEL_HOLA_MIDDLE,
            NodeRotationAnimation.hubChannelMiddleAngle(worldTime, partialTick), HubRenderLayout.channelYOffset());
        renderFullBrightRotatingThroughBufferSource(poseStack, bufferSource, state, HUB_CHANNEL_HOLA_BOTTOM,
            NodeRotationAnimation.hubChannelBottomAngle(worldTime, partialTick), HubRenderLayout.channelYOffset());
        renderAmbientRotatingThroughBufferSource(hub, poseStack, bufferSource, state, HUB_CHANNEL_RING_AERIALS_BASE,
            NodeRotationAnimation.hubChannelAerialAngle(worldTime, partialTick), HubRenderLayout.channelYOffset(),
            ambientLightSamplePos);
        renderFullBrightRotatingThroughBufferSource(poseStack, bufferSource, state, HUB_CHANNEL_RING_AERIALS_EMISSIVE,
            NodeRotationAnimation.hubChannelAerialAngle(worldTime, partialTick), HubRenderLayout.channelYOffset());
    }

    private static void renderChannelCached(HubBlockEntity hub, PoseStack poseStack, BlockState state, long worldTime, float partialTick) {
        if (!hasChannelPlugin(hub.getPlugins().getStackInSlot(0))) {
            return;
        }

        float crystalAngle = NodeRotationAnimation.hubCrystalAngle(worldTime, partialTick);
        BlockPos ambientLightSamplePos = ambientLightSamplePos(hub);
        renderFullBrightStaticCached(poseStack, state, HUB_CHANNEL_BEACON_TOP_INSIDE, HubRenderLayout.channelBeamTopYOffset());
        renderFullBrightRotatingCached(poseStack, state, HUB_CHANNEL_BEACON_TOP_OUTSIDE, crystalAngle, HubRenderLayout.channelBeamTopYOffset());
        renderFullBrightStaticCached(poseStack, state, HUB_CHANNEL_BEACON_MID_INSIDE, HubRenderLayout.channelBeamMidYOffset());
        renderFullBrightRotatingCached(poseStack, state, HUB_CHANNEL_BEACON_MID_OUTSIDE, crystalAngle, HubRenderLayout.channelBeamMidYOffset());
        renderFullBrightStaticCached(poseStack, state, HUB_CHANNEL_BEACON_DOWN_INSIDE, HubRenderLayout.channelBeamDownYOffset());
        renderFullBrightRotatingCached(poseStack, state, HUB_CHANNEL_BEACON_DOWN_OUTSIDE, crystalAngle, HubRenderLayout.channelBeamDownYOffset());
        renderFullBrightRotatingCached(poseStack, state, HUB_CHANNEL_HOLA_TOP,
            NodeRotationAnimation.hubChannelTopAngle(worldTime, partialTick), HubRenderLayout.channelYOffset());
        renderFullBrightRotatingCached(poseStack, state, HUB_CHANNEL_HOLA_MIDDLE,
            NodeRotationAnimation.hubChannelMiddleAngle(worldTime, partialTick), HubRenderLayout.channelYOffset());
        renderFullBrightRotatingCached(poseStack, state, HUB_CHANNEL_HOLA_BOTTOM,
            NodeRotationAnimation.hubChannelBottomAngle(worldTime, partialTick), HubRenderLayout.channelYOffset());
        renderAmbientRotatingCached(hub, poseStack, state, HUB_CHANNEL_RING_AERIALS_BASE,
            NodeRotationAnimation.hubChannelAerialAngle(worldTime, partialTick), HubRenderLayout.channelYOffset(),
            ambientLightSamplePos);
        renderFullBrightRotatingCached(poseStack, state, HUB_CHANNEL_RING_AERIALS_EMISSIVE,
            NodeRotationAnimation.hubChannelAerialAngle(worldTime, partialTick), HubRenderLayout.channelYOffset());
    }

    private static void renderPluginsThroughBufferSource(HubBlockEntity hub, PoseStack poseStack, MultiBufferSource bufferSource, BlockState state, long worldTime, float partialTick) {
        for (int slot = 1; slot <= 4; slot++) {
            renderPluginThroughBufferSource(hub, poseStack, bufferSource, state, worldTime, partialTick, slot);
        }
    }

    private static void renderPluginsCached(HubBlockEntity hub, PoseStack poseStack, BlockState state, long worldTime, float partialTick) {
        for (int slot = 1; slot <= 4; slot++) {
            renderPluginCached(hub, poseStack, state, worldTime, partialTick, slot);
        }
    }

    private static void renderPluginThroughBufferSource(HubBlockEntity hub, PoseStack poseStack, MultiBufferSource bufferSource, BlockState state, long worldTime, float partialTick, int slot) {
        ItemStack stack = hub.getPlugins().getStackInSlot(slot);
        var offset = HubRenderLayout.cornerOffsetForSlot(slot);
        BlockPos renderPos = hub.getBlockPos().offset(offset.x(), offset.y(), offset.z());
        int rotationPeriodTicks = resolvePluginRotationPeriodTicks(stack);
        float angle = HubRenderLayout.isClockwiseCornerSlot(slot)
            ? NodeRotationAnimation.hubClockwisePluginAngle(worldTime, partialTick, rotationPeriodTicks)
            : NodeRotationAnimation.hubCounterClockwisePluginAngle(worldTime, partialTick, rotationPeriodTicks);

        poseStack.pushPose();
        poseStack.translate(offset.x(), offset.y(), offset.z());
        RotatingModelVBORenderer.renderFullBrightYAxisThroughBufferSource(poseStack, bufferSource, state,
            resolvePluginModel(stack, renderPos), angle, CENTER, CENTER, CENTER);
        poseStack.popPose();
    }

    private static void renderPluginCached(HubBlockEntity hub, PoseStack poseStack, BlockState state, long worldTime, float partialTick, int slot) {
        ItemStack stack = hub.getPlugins().getStackInSlot(slot);
        var offset = HubRenderLayout.cornerOffsetForSlot(slot);
        BlockPos renderPos = hub.getBlockPos().offset(offset.x(), offset.y(), offset.z());
        int rotationPeriodTicks = resolvePluginRotationPeriodTicks(stack);
        float angle = HubRenderLayout.isClockwiseCornerSlot(slot)
            ? NodeRotationAnimation.hubClockwisePluginAngle(worldTime, partialTick, rotationPeriodTicks)
            : NodeRotationAnimation.hubCounterClockwisePluginAngle(worldTime, partialTick, rotationPeriodTicks);

        poseStack.pushPose();
        poseStack.translate(offset.x(), offset.y(), offset.z());
        RotatingModelVBORenderer.renderFullBrightYAxis(poseStack, state,
            resolvePluginModel(stack, renderPos), angle, CENTER, CENTER, CENTER);
        poseStack.popPose();
    }

    private static void renderFullBrightStaticThroughBufferSource(PoseStack poseStack, MultiBufferSource bufferSource, BlockState state, ResourceLocation modelLocation, double yOffset) {
        poseStack.pushPose();
        poseStack.translate(0.0D, yOffset, 0.0D);
        RotatingModelVBORenderer.renderFullBrightYAxisThroughBufferSource(poseStack, bufferSource, state, modelLocation,
            0.0F, CENTER, CENTER, CENTER);
        poseStack.popPose();
    }

    private static void renderFullBrightStaticCached(PoseStack poseStack, BlockState state, ResourceLocation modelLocation, double yOffset) {
        poseStack.pushPose();
        poseStack.translate(0.0D, yOffset, 0.0D);
        RotatingModelVBORenderer.renderFullBrightYAxis(poseStack, state, modelLocation,
            0.0F, CENTER, CENTER, CENTER);
        poseStack.popPose();
    }

    private static void renderFullBrightRotatingThroughBufferSource(PoseStack poseStack, MultiBufferSource bufferSource, BlockState state, ResourceLocation modelLocation, float angle, double yOffset) {
        poseStack.pushPose();
        poseStack.translate(0.0D, yOffset, 0.0D);
        RotatingModelVBORenderer.renderFullBrightYAxisThroughBufferSource(poseStack, bufferSource, state, modelLocation,
            angle, CENTER, CENTER, CENTER);
        poseStack.popPose();
    }

    private static void renderFullBrightRotatingCached(PoseStack poseStack, BlockState state, ResourceLocation modelLocation, float angle, double yOffset) {
        poseStack.pushPose();
        poseStack.translate(0.0D, yOffset, 0.0D);
        RotatingModelVBORenderer.renderFullBrightYAxis(poseStack, state, modelLocation,
            angle, CENTER, CENTER, CENTER);
        poseStack.popPose();
    }

    private static void renderAmbientRotatingThroughBufferSource(HubBlockEntity hub, PoseStack poseStack, MultiBufferSource bufferSource, BlockState state,
                                                                 ResourceLocation modelLocation, float angle, double yOffset, BlockPos lightSamplePos) {
        poseStack.pushPose();
        poseStack.translate(0.0D, yOffset, 0.0D);
        RotatingModelVBORenderer.renderAmbientLitThroughBufferSourceAt(poseStack, bufferSource, hub.getLevel(), lightSamplePos, state, modelLocation,
            angle, CENTER, CENTER, CENTER, 0.0F, 1.0F, 0.0F);
        poseStack.popPose();
    }

    private static void renderAmbientRotatingCached(HubBlockEntity hub, PoseStack poseStack, BlockState state,
                                                    ResourceLocation modelLocation, float angle, double yOffset, BlockPos lightSamplePos) {
        poseStack.pushPose();
        poseStack.translate(0.0D, yOffset, 0.0D);
        RotatingModelVBORenderer.renderAmbientLit(poseStack, hub.getLevel(), hub.getBlockPos(), lightSamplePos, state, modelLocation,
            angle, CENTER, CENTER, CENTER, 0.0F, 1.0F, 0.0F);
        poseStack.popPose();
    }

    private static BlockPos ambientLightSamplePos(HubBlockEntity hub) {
        return hub.getBlockPos().above(2);
    }

    private static boolean hasChannelPlugin(ItemStack stack) {
        return !stack.isEmpty()
            && stack.getItem() instanceof IHubPlugin plugin
            && plugin.getCapability() == HubCapabilitys.CHANNEL_CAPABILITY;
    }

    private static ResourceLocation resolvePluginModel(ItemStack stack, BlockPos renderPos) {
        if (stack.isEmpty()) {
            return HUB_EMPTY_PLUGIN;
        }
        if (stack.getItem() instanceof IHubPlugin plugin) {
            ResourceLocation explicitModel = plugin.getHubModelLocation();
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
    public void render(@NotNull HubBlockEntity hub, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (hub.getLevel() == null) {
            return;
        }

        long worldTime = ClientAnimationTicker.ticks();
        boolean breaking = RotatingModelVBORenderer.getDestroyStage(hub.getBlockPos()) >= 0;
        BlockState state = hub.getBlockState();

        if (breaking) {
            renderBaseThroughBufferSource(hub, poseStack, bufferSource, state, worldTime, partialTick);
            renderChannelThroughBufferSource(hub, poseStack, bufferSource, state, worldTime, partialTick);
            renderPluginsThroughBufferSource(hub, poseStack, bufferSource, state, worldTime, partialTick);
            return;
        }

        try (RotatingModelVBORenderer.RenderSession ignored = RotatingModelVBORenderer.beginRenderSession()) {
            renderBaseCached(hub, poseStack, state, worldTime, partialTick);
            renderChannelCached(hub, poseStack, state, worldTime, partialTick);
            renderPluginsCached(hub, poseStack, state, worldTime, partialTick);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(@NotNull HubBlockEntity blockEntity) {
        return true;
    }

    @Override
    public @NotNull AABB getRenderBoundingBox(@NotNull HubBlockEntity blockEntity) {
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
    public boolean shouldRender(@NotNull HubBlockEntity blockEntity, @NotNull Vec3 cameraPos) {
        return BlockEntityRenderer.super.shouldRender(blockEntity, cameraPos);
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
