package com.circulation.circulation_networks.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.NeoForgeRenderTypes;
import org.joml.Quaternionf;

import java.util.List;

public final class RotatingModelVBORenderer {

    private static final Quaternionf ROTATION = new Quaternionf();
    private static final QuadInstance FULL_BRIGHT_QUAD = new QuadInstance();
    private static final RenderType WORLD_CUTOUT_RENDER_TYPE = Sheets.cutoutBlockSheet();
    private static final RenderType WORLD_TRANSLUCENT_RENDER_TYPE = NeoForgeRenderTypes.BLOCK_ITEM_UNSORTED_TRANSLUCENT.get();
    private static final RenderType WORLD_CUTOUT_NO_CULL_RENDER_TYPE = RenderTypes.entityCutout(Sheets.BLOCKS_MAPPER.sheet());
    private static final RenderType WORLD_TRANSLUCENT_NO_CULL_RENDER_TYPE = RenderTypes.entityTranslucent(Sheets.BLOCKS_MAPPER.sheet());
    private static final RenderType ITEM_CUTOUT_RENDER_TYPE = Sheets.cutoutBlockItemSheet();
    private static final RenderType ITEM_TRANSLUCENT_RENDER_TYPE = NeoForgeRenderTypes.BLOCK_ITEM_UNSORTED_TRANSLUCENT.get();
    private static int renderSessionDepth;
    private static SubmitNodeCollector renderSessionCollector;

    static {
        FULL_BRIGHT_QUAD.setColor(-1);
        FULL_BRIGHT_QUAD.setLightCoords(LightCoordsUtil.FULL_BRIGHT);
    }

    private RotatingModelVBORenderer() {
    }

    public static RenderSession beginRenderSession(SubmitNodeCollector submitNodeCollector) {
        if (submitNodeCollector == null) {
            return RenderSession.NOOP;
        }
        if (renderSessionDepth++ == 0) {
            renderSessionCollector = submitNodeCollector;
        }
        return new RenderSession(true);
    }

    public static void renderFullBrightYAxis(PoseStack poseStack, BlockState state, Identifier modelLocation,
                                             float angle, float pivotX, float pivotY, float pivotZ) {
        renderFullBright(poseStack, state, modelLocation, angle, pivotX, pivotY, pivotZ, 0.0F, 1.0F, 0.0F);
    }

    public static void renderFullBright(PoseStack poseStack, BlockState state, Identifier modelLocation,
                                        float angle, float pivotX, float pivotY, float pivotZ,
                                        float axisX, float axisY, float axisZ) {
        renderFullBright(poseStack, state, modelLocation, angle, pivotX, pivotY, pivotZ, axisX, axisY, axisZ, false);
    }

    public static void renderFullBrightNoCullYAxis(PoseStack poseStack, BlockState state, Identifier modelLocation,
                                                   float angle, float pivotX, float pivotY, float pivotZ) {
        renderFullBrightNoCull(poseStack, state, modelLocation, angle, pivotX, pivotY, pivotZ, 0.0F, 1.0F, 0.0F);
    }

    public static void renderFullBrightNoCull(PoseStack poseStack, BlockState state, Identifier modelLocation,
                                              float angle, float pivotX, float pivotY, float pivotZ,
                                              float axisX, float axisY, float axisZ) {
        renderFullBright(poseStack, state, modelLocation, angle, pivotX, pivotY, pivotZ, axisX, axisY, axisZ, true);
    }

    private static void renderFullBright(PoseStack poseStack, BlockState state, Identifier modelLocation,
                                         float angle, float pivotX, float pivotY, float pivotZ,
                                         float axisX, float axisY, float axisZ, boolean noCull) {
        SubmitNodeCollector submitNodeCollector = renderSessionCollector;
        if (submitNodeCollector == null) {
            return;
        }
        BlockStateModel model = RotatingBlockModelCache.get(modelLocation);
        ObjectList<BlockStateModelPart> parts = collectParts(model);
        submitModelPasses(
            poseStack,
            submitNodeCollector,
            noCull ? WORLD_CUTOUT_NO_CULL_RENDER_TYPE : WORLD_CUTOUT_RENDER_TYPE,
            noCull ? WORLD_TRANSLUCENT_NO_CULL_RENDER_TYPE : WORLD_TRANSLUCENT_RENDER_TYPE,
            angle,
            pivotX,
            pivotY,
            pivotZ,
            axisX,
            axisY,
            axisZ,
            (pose, buffer, translucentPass) -> renderFullBrightModel(pose, buffer, parts, translucentPass)
        );
    }

    public static void renderFullBrightYAxis(PoseStack poseStack, Identifier modelLocation,
                                             float angle, float pivotX, float pivotY, float pivotZ) {
        renderFullBright(poseStack, modelLocation, angle, pivotX, pivotY, pivotZ, 0.0F, 1.0F, 0.0F);
    }

    public static void renderFullBright(PoseStack poseStack, Identifier modelLocation,
                                        float angle, float pivotX, float pivotY, float pivotZ,
                                        float axisX, float axisY, float axisZ) {
        SubmitNodeCollector submitNodeCollector = renderSessionCollector;
        if (submitNodeCollector == null) {
            return;
        }
        BlockStateModel model = RotatingBlockModelCache.get(modelLocation);
        ObjectList<BlockStateModelPart> parts = collectParts(model);
        submitModelPasses(
            poseStack,
            submitNodeCollector,
            ITEM_CUTOUT_RENDER_TYPE,
            ITEM_TRANSLUCENT_RENDER_TYPE,
            angle,
            pivotX,
            pivotY,
            pivotZ,
            axisX,
            axisY,
            axisZ,
            (pose, buffer, translucentPass) -> renderFullBrightModel(pose, buffer, parts, translucentPass)
        );
    }

    public static void renderAmbientLit(PoseStack poseStack, Level level, BlockPos pos,
                                        BlockState state, Identifier modelLocation,
                                        float angle, float pivotX, float pivotY, float pivotZ,
                                        float axisX, float axisY, float axisZ) {
        renderAmbientLit(poseStack, level, pos, pos, state, modelLocation, angle, pivotX, pivotY, pivotZ, axisX, axisY, axisZ, false);
    }

    public static void renderAmbientLitNoCull(PoseStack poseStack, Level level, BlockPos originPos, BlockPos lightSamplePos,
                                              BlockState state, Identifier modelLocation,
                                              float angle, float pivotX, float pivotY, float pivotZ,
                                              float axisX, float axisY, float axisZ) {
        renderAmbientLit(poseStack, level, originPos, lightSamplePos, state, modelLocation, angle, pivotX, pivotY, pivotZ, axisX, axisY, axisZ, true);
    }

    private static void renderAmbientLit(PoseStack poseStack, Level level, BlockPos originPos, BlockPos lightSamplePos,
                                         BlockState state, Identifier modelLocation,
                                         float angle, float pivotX, float pivotY, float pivotZ,
                                         float axisX, float axisY, float axisZ, boolean noCull) {
        SubmitNodeCollector submitNodeCollector = renderSessionCollector;
        if (submitNodeCollector == null || !(level instanceof ClientLevel clientLevel)) {
            return;
        }
        BlockStateModel model = RotatingBlockModelCache.get(modelLocation);
        ObjectList<BlockStateModelPart> parts = collectParts(model);
        int lightCoords = LevelRenderer.getLightCoords(clientLevel, lightSamplePos);
        submitModelPasses(
            poseStack,
            submitNodeCollector,
            noCull ? WORLD_CUTOUT_NO_CULL_RENDER_TYPE : WORLD_CUTOUT_RENDER_TYPE,
            noCull ? WORLD_TRANSLUCENT_NO_CULL_RENDER_TYPE : WORLD_TRANSLUCENT_RENDER_TYPE,
            angle,
            pivotX,
            pivotY,
            pivotZ,
            axisX,
            axisY,
            axisZ,
            (pose, buffer, translucentPass) -> renderLitModel(pose, buffer, parts, lightCoords, translucentPass)
        );
    }

    public static void removePosition(int worldId, BlockPos pos) {
    }

    public static void clearAll() {
    }

    public static void renderLitYAxis(PoseStack poseStack, int lightCoords, Identifier modelLocation,
                                      float angle, float pivotX, float pivotY, float pivotZ) {
        renderLit(poseStack, lightCoords, modelLocation, angle, pivotX, pivotY, pivotZ, 0.0F, 1.0F, 0.0F);
    }

    public static void renderLit(PoseStack poseStack, int lightCoords, Identifier modelLocation,
                                 float angle, float pivotX, float pivotY, float pivotZ,
                                 float axisX, float axisY, float axisZ) {
        SubmitNodeCollector submitNodeCollector = renderSessionCollector;
        if (submitNodeCollector == null) {
            return;
        }
        BlockStateModel model = RotatingBlockModelCache.get(modelLocation);
        ObjectList<BlockStateModelPart> parts = collectParts(model);
        submitModelPasses(
            poseStack,
            submitNodeCollector,
            ITEM_CUTOUT_RENDER_TYPE,
            ITEM_TRANSLUCENT_RENDER_TYPE,
            angle,
            pivotX,
            pivotY,
            pivotZ,
            axisX,
            axisY,
            axisZ,
            (pose, buffer, translucentPass) -> renderLitModel(pose, buffer, parts, lightCoords, translucentPass)
        );
    }

    private static void submitModelPasses(
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector,
        RenderType cutoutRenderType,
        RenderType translucentRenderType,
        float angle,
        float pivotX,
        float pivotY,
        float pivotZ,
        float axisX,
        float axisY,
        float axisZ,
        PassRenderer renderer
    ) {
        submitRotated(
            poseStack,
            submitNodeCollector,
            cutoutRenderType,
            angle,
            pivotX,
            pivotY,
            pivotZ,
            axisX,
            axisY,
            axisZ,
            (pose, buffer) -> renderer.render(pose, buffer, false)
        );
        submitRotated(
            poseStack,
            submitNodeCollector,
            translucentRenderType,
            angle,
            pivotX,
            pivotY,
            pivotZ,
            axisX,
            axisY,
            axisZ,
            (pose, buffer) -> renderer.render(pose, buffer, true)
        );
    }

    private static void submitRotated(
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector,
        RenderType renderType,
        float angle,
        float pivotX,
        float pivotY,
        float pivotZ,
        float axisX,
        float axisY,
        float axisZ,
        SubmitNodeCollector.CustomGeometryRenderer renderer
    ) {
        poseStack.pushPose();
        poseStack.translate(pivotX, pivotY, pivotZ);
        ROTATION.rotationAxis((float) Math.toRadians(angle), axisX, axisY, axisZ);
        poseStack.mulPose(ROTATION);
        poseStack.translate(-pivotX, -pivotY, -pivotZ);
        submitNodeCollector.submitCustomGeometry(poseStack, renderType, renderer);
        poseStack.popPose();
    }

    @SuppressWarnings("deprecation")
    private static ObjectList<BlockStateModelPart> collectParts(BlockStateModel model) {
        ObjectList<BlockStateModelPart> parts = new ObjectArrayList<>();
        model.collectParts(RandomSource.create(42L), parts);
        return parts;
    }

    private static void renderFullBrightModel(PoseStack.Pose pose, VertexConsumer buffer, List<BlockStateModelPart> parts, boolean translucentPass) {
        for (BlockStateModelPart part : parts) {
            emitFullBrightQuads(pose, buffer, part.getQuads(null), translucentPass);
            for (Direction direction : Direction.values()) {
                emitFullBrightQuads(pose, buffer, part.getQuads(direction), translucentPass);
            }
        }
    }

    private static void renderLitModel(
        PoseStack.Pose pose,
        VertexConsumer buffer,
        List<BlockStateModelPart> parts,
        int lightCoords,
        boolean translucentPass
    ) {
        QuadInstance litQuad = new QuadInstance();
        litQuad.setColor(-1);
        litQuad.setLightCoords(lightCoords);
        for (BlockStateModelPart part : parts) {
            emitLitQuads(pose, buffer, part.getQuads(null), litQuad, translucentPass);
            for (Direction direction : Direction.values()) {
                emitLitQuads(pose, buffer, part.getQuads(direction), litQuad, translucentPass);
            }
        }
    }

    private static void emitFullBrightQuads(PoseStack.Pose pose, VertexConsumer buffer, List<BakedQuad> quads, boolean translucentPass) {
        for (BakedQuad quad : quads) {
            if (quad.materialInfo().layer().translucent() != translucentPass) {
                continue;
            }
            buffer.putBakedQuad(pose, quad, FULL_BRIGHT_QUAD);
        }
    }

    private static void emitLitQuads(PoseStack.Pose pose, VertexConsumer buffer, List<BakedQuad> quads, QuadInstance litQuad, boolean translucentPass) {
        for (BakedQuad quad : quads) {
            if (quad.materialInfo().layer().translucent() != translucentPass) {
                continue;
            }
            buffer.putBakedQuad(pose, quad, litQuad);
        }
    }

    private static void endRenderSession() {
        if (renderSessionDepth <= 0) {
            return;
        }
        if (--renderSessionDepth > 0) {
            return;
        }
        renderSessionCollector = null;
    }

    @FunctionalInterface
    private interface PassRenderer {
        void render(PoseStack.Pose pose, VertexConsumer buffer, boolean translucentPass);
    }

    public static final class RenderSession implements AutoCloseable {

        private static final RenderSession NOOP = new RenderSession(false);

        private final boolean active;
        private boolean closed;

        private RenderSession(boolean active) {
            this.active = active;
        }

        @Override
        public void close() {
            if (closed || !active) {
                return;
            }
            closed = true;
            endRenderSession();
        }
    }
}
