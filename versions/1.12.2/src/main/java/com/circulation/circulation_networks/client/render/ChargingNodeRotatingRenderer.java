package com.circulation.circulation_networks.client.render;

import com.circulation.circulation_networks.CFNConfig;
import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.tiles.nodes.TileEntityChargingNode;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;

@SideOnly(Side.CLIENT)
public final class ChargingNodeRotatingRenderer extends TileEntitySpecialRenderer<TileEntityChargingNode> {

    private static final float CENTER = 0.5F;

    private static final ResourceLocation IN_BASE = model("charging_node/charging_node_in_base");
    private static final ResourceLocation IN_EMISSIVE = model("charging_node/charging_node_in_emissive");
    private static final ResourceLocation CRYSTAL = model("node_crystal");
    private static final ResourceLocation RING_BASE = model("charging_node/charging_node_ring_base");
    private static final ResourceLocation RING_EMISSIVE = model("charging_node/charging_node_ring_emissive");

    private static ResourceLocation model(String path) {
        return new ResourceLocation(CirculationFlowNetworks.MOD_ID, "block/" + path);
    }

    @Override
    public void render(@NotNull TileEntityChargingNode te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        if (!te.hasWorld() || !CFNConfig.NODE.rendering.animatedSpecialModels) {
            return;
        }

        RotatingModelRenderHelper.RenderBatch batch = RotatingModelRenderHelper.beginBatch(te, x, y, z, destroyStage);
        if (batch == null) return;
        try {
            long clientTicks = ClientAnimationTicker.ticks();
            float topAngle = NodeRotationAnimation.relayBottomSpiralAngle(clientTicks, partialTicks);
            float crystalAngle = NodeRotationAnimation.relayCrystalAngle(clientTicks, partialTicks);
            float bottomAngle = NodeRotationAnimation.relayBottomSpiralAngle(clientTicks, partialTicks);
            batch.renderAroundAxis(IN_BASE, topAngle, CENTER, CENTER, CENTER, 0.0F, 1.0F, 0.0F, false, false);
            batch.renderAroundYAxisFullBright(IN_EMISSIVE, topAngle, CENTER, CENTER, CENTER);
            GlStateManager.pushMatrix();
            GlStateManager.translate(0.0F, NodeRotationAnimation.bobOffset(clientTicks, partialTicks), 0.0F);
            batch.renderAroundYAxisFullBright(CRYSTAL, crystalAngle, CENTER, CENTER, CENTER);
            GlStateManager.popMatrix();
            batch.renderAroundAxis(RING_BASE, bottomAngle, CENTER, CENTER, CENTER, 0.0F, 1.0F, 0.0F, false, false);
            batch.renderAroundYAxisFullBright(RING_EMISSIVE, bottomAngle, CENTER, CENTER, CENTER);
        } finally {
            batch.end();
        }
        super.render(te, x, y, z, partialTicks, destroyStage, alpha);
    }
}
