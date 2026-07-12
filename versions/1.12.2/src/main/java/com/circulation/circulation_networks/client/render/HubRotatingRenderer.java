package com.circulation.circulation_networks.client.render;

import com.circulation.circulation_networks.api.hub.IHubPlugin;
import com.circulation.circulation_networks.network.hub.HubCapabilitys;
import com.circulation.circulation_networks.tiles.nodes.TileEntityHub;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
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

@SideOnly(Side.CLIENT)
public final class HubRotatingRenderer extends TileEntitySpecialRenderer<TileEntityHub> {

    private static final float CENTER = 0.5F;
    private static final ResourceLocation[] DEFAULT_PLUGIN_MODELS = {
        HUB_DEFAULT_PLUGIN_0,
        HUB_DEFAULT_PLUGIN_1,
        HUB_DEFAULT_PLUGIN_2,
        HUB_DEFAULT_PLUGIN_3
    };

    private static void renderBase(RotatingModelRenderHelper.RenderBatch batch, long clientTicks, float partialTicks) {
        float upperAngle = NodeRotationAnimation.hubUpperRingAngle(clientTicks, partialTicks);
        float lowerAngle = NodeRotationAnimation.hubLowerRingAngle(clientTicks, partialTicks);
        float crystalAngle = NodeRotationAnimation.hubCrystalAngle(clientTicks, partialTicks);
        BlockPos ambientLightSamplePos = ambientLightSamplePos(batch);
        BlockPos ringLightSamplePos = translatedLightSamplePos(batch, HubRenderLayout.ringYOffset());
        BlockPos crystalLightSamplePos = translatedLightSamplePos(batch, HubRenderLayout.crystalYOffset());

        batch.renderAroundAxis(HUB_BASE, 0.0F, CENTER, CENTER, CENTER, 0.0F, 1.0F, 0.0F, false, false, ambientLightSamplePos);
        batch.renderAroundAxis(HUB_EMISSIVE, 0.0F, CENTER, CENTER, CENTER, 0.0F, 1.0F, 0.0F, true, true);
        renderHubRingAtYOffset(batch, HubRenderLayout.ringYOffset(), HUB_RING_UP_BASE, upperAngle, true, ambientLightSamplePos);
        renderHubRingAtYOffset(batch, HubRenderLayout.ringYOffset(), HUB_RING_UP_EMISSIVE, upperAngle, false, ringLightSamplePos);
        renderHubRingAtYOffset(batch, HubRenderLayout.ringYOffset(), HUB_RING_DOWN_BASE, lowerAngle, true, ambientLightSamplePos);
        renderHubRingAtYOffset(batch, HubRenderLayout.ringYOffset(), HUB_RING_DOWN_EMISSIVE, lowerAngle, false, ringLightSamplePos);
        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0D, HubRenderLayout.crystalYOffset(), 0.0D);
        batch.renderAroundYAxisFullBright(HUB_CRYSTAL, crystalAngle, CENTER, CENTER, CENTER, crystalLightSamplePos);
        GlStateManager.popMatrix();
    }

    private static void renderHubRingAtYOffset(RotatingModelRenderHelper.RenderBatch batch, double yOffset, ResourceLocation model, float angle, boolean ambient, BlockPos lightSamplePos) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0D, yOffset, 0.0D);
        if (ambient) {
            batch.renderAroundAxis(model, angle, CENTER, CENTER, CENTER, 0.0F, 1.0F, 0.0F, false, false, lightSamplePos);
        } else {
            batch.renderAroundYAxisFullBright(model, angle, CENTER, CENTER, CENTER, lightSamplePos);
        }
        GlStateManager.popMatrix();
    }

    private static void renderChannel(TileEntityHub hub, RotatingModelRenderHelper.RenderBatch batch, long clientTicks, float partialTicks) {
        if (!hasChannelPlugin(hub.getPlugins().getStackInSlot(0))) {
            return;
        }

        float crystalAngle = NodeRotationAnimation.hubCrystalAngle(clientTicks, partialTicks);
        BlockPos ambientLightSamplePos = ambientLightSamplePos(batch);
        BlockPos channelLightSamplePos = translatedLightSamplePos(batch, HubRenderLayout.channelYOffset());
        BlockPos beamDownLightSamplePos = translatedLightSamplePos(batch, HubRenderLayout.channelBeamDownYOffset());
        BlockPos beamMidLightSamplePos = translatedLightSamplePos(batch, HubRenderLayout.channelBeamMidYOffset());
        BlockPos beamTopLightSamplePos = translatedLightSamplePos(batch, HubRenderLayout.channelBeamTopYOffset());

        renderChannelAtYOffset(batch, HubRenderLayout.channelBeamTopYOffset(), HUB_CHANNEL_BEACON_TOP_INSIDE, 0.0F, false, beamTopLightSamplePos);
        renderChannelAtYOffset(batch, HubRenderLayout.channelBeamTopYOffset(), HUB_CHANNEL_BEACON_TOP_OUTSIDE, crystalAngle, false, beamTopLightSamplePos);
        renderChannelAtYOffset(batch, HubRenderLayout.channelBeamMidYOffset(), HUB_CHANNEL_BEACON_MID_INSIDE, 0.0F, false, beamMidLightSamplePos);
        renderChannelAtYOffset(batch, HubRenderLayout.channelBeamMidYOffset(), HUB_CHANNEL_BEACON_MID_OUTSIDE, crystalAngle, false, beamMidLightSamplePos);
        renderChannelAtYOffset(batch, HubRenderLayout.channelBeamDownYOffset(), HUB_CHANNEL_BEACON_DOWN_INSIDE, 0.0F, false, beamDownLightSamplePos);
        renderChannelAtYOffset(batch, HubRenderLayout.channelBeamDownYOffset(), HUB_CHANNEL_BEACON_DOWN_OUTSIDE, crystalAngle, false, beamDownLightSamplePos);
        renderChannelAtYOffset(batch, HubRenderLayout.channelYOffset(), HUB_CHANNEL_HOLA_TOP, NodeRotationAnimation.hubChannelTopAngle(clientTicks, partialTicks), false, channelLightSamplePos);
        renderChannelAtYOffset(batch, HubRenderLayout.channelYOffset(), HUB_CHANNEL_HOLA_MIDDLE, NodeRotationAnimation.hubChannelMiddleAngle(clientTicks, partialTicks), false, channelLightSamplePos);
        renderChannelAtYOffset(batch, HubRenderLayout.channelYOffset(), HUB_CHANNEL_HOLA_BOTTOM, NodeRotationAnimation.hubChannelBottomAngle(clientTicks, partialTicks), false, channelLightSamplePos);
        renderChannelAtYOffset(batch, HubRenderLayout.channelYOffset(), HUB_CHANNEL_RING_AERIALS_BASE,
            NodeRotationAnimation.hubChannelAerialAngle(clientTicks, partialTicks), true, ambientLightSamplePos);
        renderChannelAtYOffset(batch, HubRenderLayout.channelYOffset(), HUB_CHANNEL_RING_AERIALS_EMISSIVE,
            NodeRotationAnimation.hubChannelAerialAngle(clientTicks, partialTicks), false, channelLightSamplePos);
    }

    private static void renderChannelAtYOffset(RotatingModelRenderHelper.RenderBatch batch, double yOffset, ResourceLocation model, float angle, boolean ambient, BlockPos lightSamplePos) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0D, yOffset, 0.0D);
        if (ambient) {
            batch.renderAroundAxis(model, angle, CENTER, CENTER, CENTER, 0.0F, 1.0F, 0.0F, false, false, lightSamplePos);
        } else {
            batch.renderAroundYAxisFullBright(model, angle, CENTER, CENTER, CENTER, lightSamplePos);
        }
        GlStateManager.popMatrix();
    }

    private static void renderPlugins(TileEntityHub hub, RotatingModelRenderHelper.RenderBatch batch, long clientTicks, float partialTicks) {
        for (int slot = 1; slot <= 4; slot++) {
            ItemStack stack = hub.getPlugins().getStackInSlot(slot);
            var offset = HubRenderLayout.cornerOffsetForSlot(slot);
            BlockPos renderPos = hub.getPos().add(offset.x, offset.y, offset.z);
            int rotationPeriodTicks = resolvePluginRotationPeriodTicks(stack);
            float angle = HubRenderLayout.isClockwiseCornerSlot(slot)
                ? NodeRotationAnimation.hubClockwisePluginAngle(clientTicks, partialTicks, rotationPeriodTicks)
                : NodeRotationAnimation.hubCounterClockwisePluginAngle(clientTicks, partialTicks, rotationPeriodTicks);

            GlStateManager.pushMatrix();
            GlStateManager.translate(offset.x, offset.y, offset.z);
            batch.renderAroundYAxisFullBright(resolvePluginModel(stack, renderPos), angle, CENTER, CENTER, CENTER, renderPos);
            GlStateManager.popMatrix();
        }
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

    private static BlockPos ambientLightSamplePos(RotatingModelRenderHelper.RenderBatch batch) {
        return batch.getTileEntity().getPos().up(2);
    }

    private static BlockPos translatedLightSamplePos(RotatingModelRenderHelper.RenderBatch batch, double yOffset) {
        return batch.getTileEntity().getPos().up((int) Math.round(yOffset));
    }

    @Override
    public void render(@NotNull TileEntityHub hub, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        if (!hub.hasWorld()) {
            return;
        }

        try (LegacyWorldRenderStateGuard guard = LegacyWorldRenderStateGuard.openHubPass("hub TESR")) {
            Minecraft.getMinecraft().entityRenderer.enableLightmap();
            guard.establishHubLightmapProfile();
            RotatingModelRenderHelper.RenderBatch batch = RotatingModelRenderHelper.beginBatch(hub, x, y, z, destroyStage);
            if (batch == null) {
                return;
            }

            try {
                long clientTicks = ClientAnimationTicker.ticks();
                renderBase(batch, clientTicks, partialTicks);
                renderChannel(hub, batch, clientTicks, partialTicks);
                renderPlugins(hub, batch, clientTicks, partialTicks);
            } finally {
                batch.end();
            }
        }
        super.render(hub, x, y, z, partialTicks, destroyStage, alpha);
    }
}
