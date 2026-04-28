package com.circulation.circulation_networks.client.render;

import com.circulation.circulation_networks.CFNConfig;
import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.registry.RegistryBlocks;
import com.github.bsideup.jabel.Desugar;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public final class AnimatedNodeItemStackRenderer extends TileEntityItemStackRenderer {

    private static final float CENTER = 0.5F;

    private static final ResourceLocation RELAY_STATIC = model("relay_node/relay_node");
    private static final ResourceLocation RELAY_TOP_SPIRAL_BASE = model("relay_node/relay_node_top_spiral_base");
    private static final ResourceLocation RELAY_TOP_SPIRAL_EMISSIVE = model("relay_node/relay_node_top_spiral_emissive");
    private static final ResourceLocation NODE_CRYSTAL = model("node_crystal");
    private static final ResourceLocation RELAY_BOTTOM_SPIRAL_BASE = model("relay_node/relay_node_bottom_spiral_base");
    private static final ResourceLocation RELAY_BOTTOM_SPIRAL_EMISSIVE = model("relay_node/relay_node_bottom_spiral_emissive");

    private static final ResourceLocation CHARGING_STATIC = model("charging_node/charging_node");
    private static final ResourceLocation CHARGING_IN_BASE = model("charging_node/charging_node_in_base");
    private static final ResourceLocation CHARGING_IN_EMISSIVE = model("charging_node/charging_node_in_emissive");
    private static final ResourceLocation CHARGING_RING_BASE = model("charging_node/charging_node_ring_base");
    private static final ResourceLocation CHARGING_RING_EMISSIVE = model("charging_node/charging_node_ring_emissive");

    private static final ResourceLocation PORT_STATIC = model("port_node/port_node");
    private static final ResourceLocation PORT_IN_BASE = model("port_node/port_node_in_base");
    private static final ResourceLocation PORT_IN_EMISSIVE = model("port_node/port_node_in_emissive");
    private static final ResourceLocation PORT_OUT_BASE = model("port_node/port_node_out_base");
    private static final ResourceLocation PORT_OUT_EMISSIVE = model("port_node/port_node_out_emissive");

    private static final ResourceLocation PEDESTAL_STATIC = model("node_pedestal/node_pedestal");
    private static final ResourceLocation PEDESTAL_BASE = model("node_pedestal/node_pedestal_base");
    private static final ResourceLocation PEDESTAL_BASE_EMISSIVE = model("node_pedestal/node_pedestal_base_emissive");
    private static final ResourceLocation PEDESTAL_FRAME_CLOCKWISE = model("node_pedestal/node_pedestal_frame_clockwise");
    private static final ResourceLocation PEDESTAL_FRAME_COUNTER_CLOCKWISE = model("node_pedestal/node_pedestal_frame_counter_clockwise");

    private static final float CLOCKWISE_PIVOT_X = 8.0F / 16.0F;
    private static final float CLOCKWISE_PIVOT_Y = 5.0F / 16.0F;
    private static final float CLOCKWISE_PIVOT_Z = 8.0F / 16.0F;

    private static final float COUNTER_CLOCKWISE_PIVOT_X = 8.0F / 16.0F;
    private static final float COUNTER_CLOCKWISE_PIVOT_Y = 5.0F / 16.0F;
    private static final float COUNTER_CLOCKWISE_PIVOT_Z = 8.0F / 16.0F;

    public static void bindItemRenderers() {
        AnimatedNodeItemStackRenderer renderer = new AnimatedNodeItemStackRenderer();
        bind(Item.getItemFromBlock(RegistryBlocks.blockRelayNode), renderer);
        bind(Item.getItemFromBlock(RegistryBlocks.blockChargingNode), renderer);
        bind(Item.getItemFromBlock(RegistryBlocks.blockPortNode), renderer);
        bind(Item.getItemFromBlock(RegistryBlocks.blockNodePedestal), renderer);
    }

    private static void bind(Item item, TileEntityItemStackRenderer renderer) {
        if (item != null && item != Items.AIR) {
            item.setTileEntityItemStackRenderer(renderer);
        }
    }

    private static void renderRelayNode(ItemStack stack, long worldTime, float partialTicks) {
        if (!CFNConfig.NODE.rendering.animatedSpecialModels) {
            RotatingItemModelRenderHelper.renderModel(stack, RELAY_STATIC);
            return;
        }

        RotatingItemModelRenderHelper.renderAroundYAxis(stack, RELAY_TOP_SPIRAL_BASE, NodeRotationAnimation.relayTopSpiralAngle(worldTime, partialTicks), CENTER, CENTER, CENTER);
        RotatingItemModelRenderHelper.renderAroundYAxisFullBright(stack, RELAY_TOP_SPIRAL_EMISSIVE, NodeRotationAnimation.relayTopSpiralAngle(worldTime, partialTicks), CENTER, CENTER, CENTER);
        RotatingItemModelRenderHelper.renderAroundYAxisFullBright(stack, NODE_CRYSTAL, NodeRotationAnimation.relayCrystalAngle(worldTime, partialTicks), CENTER, CENTER, CENTER);
        RotatingItemModelRenderHelper.renderAroundYAxis(stack, RELAY_BOTTOM_SPIRAL_BASE, NodeRotationAnimation.relayBottomSpiralAngle(worldTime, partialTicks), CENTER, CENTER, CENTER);
        RotatingItemModelRenderHelper.renderAroundYAxisFullBright(stack, RELAY_BOTTOM_SPIRAL_EMISSIVE, NodeRotationAnimation.relayBottomSpiralAngle(worldTime, partialTicks), CENTER, CENTER, CENTER);
    }

    private static void renderChargingNode(ItemStack stack, long worldTime, float partialTicks) {
        if (!CFNConfig.NODE.rendering.animatedSpecialModels) {
            RotatingItemModelRenderHelper.renderModel(stack, CHARGING_STATIC);
            return;
        }

        RotatingItemModelRenderHelper.renderAroundYAxis(stack, CHARGING_IN_BASE, NodeRotationAnimation.relayBottomSpiralAngle(worldTime, partialTicks), CENTER, CENTER, CENTER);
        RotatingItemModelRenderHelper.renderAroundYAxisFullBright(stack, CHARGING_IN_EMISSIVE, NodeRotationAnimation.relayBottomSpiralAngle(worldTime, partialTicks), CENTER, CENTER, CENTER);
        RotatingItemModelRenderHelper.renderAroundYAxisFullBright(stack, NODE_CRYSTAL, NodeRotationAnimation.relayCrystalAngle(worldTime, partialTicks), CENTER, CENTER, CENTER);
        RotatingItemModelRenderHelper.renderAroundYAxis(stack, CHARGING_RING_BASE, NodeRotationAnimation.relayBottomSpiralAngle(worldTime, partialTicks), CENTER, CENTER, CENTER);
        RotatingItemModelRenderHelper.renderAroundYAxisFullBright(stack, CHARGING_RING_EMISSIVE, NodeRotationAnimation.relayBottomSpiralAngle(worldTime, partialTicks), CENTER, CENTER, CENTER);
    }

    private static void renderPortNode(ItemStack stack, long worldTime, float partialTicks) {
        if (!CFNConfig.NODE.rendering.animatedSpecialModels) {
            RotatingItemModelRenderHelper.renderModel(stack, PORT_STATIC);
            return;
        }

        RotatingItemModelRenderHelper.renderAroundYAxis(stack, PORT_IN_BASE, NodeRotationAnimation.relayBottomSpiralAngle(worldTime, partialTicks), CENTER, CENTER, CENTER);
        RotatingItemModelRenderHelper.renderAroundYAxisFullBright(stack, PORT_IN_EMISSIVE, NodeRotationAnimation.relayBottomSpiralAngle(worldTime, partialTicks), CENTER, CENTER, CENTER);
        RotatingItemModelRenderHelper.renderAroundYAxisFullBright(stack, NODE_CRYSTAL, NodeRotationAnimation.relayCrystalAngle(worldTime, partialTicks), CENTER, CENTER, CENTER);
        RotatingItemModelRenderHelper.renderAroundYAxis(stack, PORT_OUT_BASE, NodeRotationAnimation.relayTopSpiralAngle(worldTime, partialTicks), CENTER, CENTER, CENTER);
        RotatingItemModelRenderHelper.renderAroundYAxisFullBright(stack, PORT_OUT_EMISSIVE, NodeRotationAnimation.relayTopSpiralAngle(worldTime, partialTicks), CENTER, CENTER, CENTER);
    }

    private static void renderNodePedestal(ItemStack stack, long worldTime, float partialTicks) {
        if (!CFNConfig.NODE.rendering.animatedSpecialModels) {
            RotatingItemModelRenderHelper.renderModelCutout(stack, PEDESTAL_STATIC);
            return;
        }

        boolean depthWriteEnabled = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        GlStateManager.depthMask(true);
        try {
            RotatingItemModelRenderHelper.renderModelCutout(stack, PEDESTAL_BASE);
            RotatingItemModelRenderHelper.renderModelFullBright(stack, PEDESTAL_BASE_EMISSIVE);
            RotatingItemModelRenderHelper.renderAroundAxis(
                stack,
                PEDESTAL_FRAME_CLOCKWISE,
                NodeRotationAnimation.pedestalClockwiseFrameAngle(worldTime, partialTicks),
                CLOCKWISE_PIVOT_X,
                CLOCKWISE_PIVOT_Y,
                CLOCKWISE_PIVOT_Z,
                NodeRotationAnimation.tiltedAxisXForZRotation(-22.5F),
                NodeRotationAnimation.tiltedAxisY(),
                NodeRotationAnimation.tiltedAxisZ(),
                true,
                false
            );
            RotatingItemModelRenderHelper.renderAroundAxis(
                stack,
                PEDESTAL_FRAME_COUNTER_CLOCKWISE,
                NodeRotationAnimation.pedestalCounterClockwiseFrameAngle(worldTime, partialTicks),
                COUNTER_CLOCKWISE_PIVOT_X,
                COUNTER_CLOCKWISE_PIVOT_Y,
                COUNTER_CLOCKWISE_PIVOT_Z,
                NodeRotationAnimation.tiltedAxisXForZRotation(22.5F),
                NodeRotationAnimation.tiltedAxisY(),
                NodeRotationAnimation.tiltedAxisZ(),
                true,
                false
            );
        } finally {
            GlStateManager.depthMask(depthWriteEnabled);
        }
    }

    private static AnimationTick resolveAnimationTick(float partialTicks) {
        return new AnimationTick(ClientAnimationTicker.ticks(), partialTicks);
    }

    private static ResourceLocation model(String path) {
        return new ResourceLocation(CirculationFlowNetworks.MOD_ID, "block/" + path);
    }

    @Override
    public void renderByItem(ItemStack stack, float partialTicks) {
        if (stack.isEmpty()) {
            return;
        }

        AnimationTick tick = resolveAnimationTick(partialTicks);
        Item item = stack.getItem();

        if (item == Item.getItemFromBlock(RegistryBlocks.blockRelayNode)) {
            renderRelayNode(stack, tick.worldTime, tick.partialTicks);
            return;
        }

        if (item == Item.getItemFromBlock(RegistryBlocks.blockChargingNode)) {
            renderChargingNode(stack, tick.worldTime, tick.partialTicks);
            return;
        }

        if (item == Item.getItemFromBlock(RegistryBlocks.blockPortNode)) {
            renderPortNode(stack, tick.worldTime, tick.partialTicks);
            return;
        }

        if (item == Item.getItemFromBlock(RegistryBlocks.blockNodePedestal)) {
            renderNodePedestal(stack, tick.worldTime, tick.partialTicks);
        }
    }

    @Desugar
    private record AnimationTick(long worldTime, float partialTicks) {
    }
}
