package com.circulation.circulation_networks.client.render;

import com.circulation.circulation_networks.CFNConfig;
import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.tiles.TileEntityNodePedestal;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;

@SideOnly(Side.CLIENT)
public final class NodePedestalRotatingRenderer extends TileEntitySpecialRenderer<TileEntityNodePedestal> {

    private static final float CENTER = 0.5F;
    private static final ResourceLocation BASE = model("node_pedestal/node_pedestal_base");
    private static final ResourceLocation BASE_EMISSIVE = model("node_pedestal/node_pedestal_base_emissive");
    private static final ResourceLocation CLOCKWISE_FRAME = model("node_pedestal/node_pedestal_frame_clockwise");
    private static final ResourceLocation COUNTER_CLOCKWISE_FRAME = model("node_pedestal/node_pedestal_frame_counter_clockwise");

    private static final float CLOCKWISE_PIVOT_X = 8.0F / 16.0F;
    private static final float CLOCKWISE_PIVOT_Y = 5.0F / 16.0F;
    private static final float CLOCKWISE_PIVOT_Z = 8.0F / 16.0F;

    private static final float COUNTER_CLOCKWISE_PIVOT_X = 8.0F / 16.0F;
    private static final float COUNTER_CLOCKWISE_PIVOT_Y = 5.0F / 16.0F;
    private static final float COUNTER_CLOCKWISE_PIVOT_Z = 8.0F / 16.0F;

    private static ResourceLocation model(String path) {
        return new ResourceLocation(CirculationFlowNetworks.MOD_ID, "block/" + path);
    }

    @Override
    public void render(@NotNull TileEntityNodePedestal te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        if (te == null || !te.hasWorld() || !CFNConfig.NODE.rendering.animatedSpecialModels) {
            return;
        }

        RotatingModelRenderHelper.RenderBatch batch = RotatingModelRenderHelper.beginBatch(te, x, y, z, destroyStage);
        if (batch == null) return;
        try {
            long worldTime = ClientAnimationTicker.ticks();
            batch.renderAroundAxis(BASE, 0.0F, CENTER, CENTER, CENTER, 0.0F, 1.0F, 0.0F, false, false);
            batch.renderAroundYAxisFullBright(BASE_EMISSIVE, 0.0F, CENTER, CENTER, CENTER);
            GlStateManager.pushMatrix();
            GlStateManager.translate(0.0F, NodeRotationAnimation.bobOffset(worldTime, partialTicks), 0.0F);
            batch.renderAroundAxis(
                CLOCKWISE_FRAME,
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
            batch.renderAroundAxis(
                COUNTER_CLOCKWISE_FRAME,
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
            GlStateManager.popMatrix();
        } finally {
            batch.end();
        }
        super.render(te, x, y, z, partialTicks, destroyStage, alpha);
    }
}
