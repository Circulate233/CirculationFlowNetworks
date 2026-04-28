package com.circulation.circulation_networks.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.SortedSet;

@OnlyIn(Dist.CLIENT)
public final class RotatingModelVBORenderer {

    private static final Map<ResourceLocation, VertexBuffer> FULL_BRIGHT_VBOS = new IdentityHashMap<>();
    private static final Object2ObjectMap<AmbientVBOKey, CachedVBO> AMBIENT_VBOS = new Object2ObjectOpenHashMap<>();
    private static final Object2ObjectMap<AmbientLightKey, CachedLightSignature> AMBIENT_LIGHT_SIGNATURES = new Object2ObjectOpenHashMap<>();
    private static final Quaternionf ROTATION = new Quaternionf();
    private static final Matrix4f RENDER_SESSION_MODEL_VIEW = new Matrix4f();
    private static final Matrix4f DRAW_MODEL_VIEW = new Matrix4f();
    private static int renderSessionDepth;
    private static ShaderInstance renderSessionShader;

    private RotatingModelVBORenderer() {
    }

    public static RenderSession beginRenderSession() {
        ShaderInstance shader = GameRenderer.getRendertypeCutoutShader();
        if (shader == null) {
            return RenderSession.NOOP;
        }
        if (renderSessionDepth++ == 0) {
            renderSessionShader = shader;
            RENDER_SESSION_MODEL_VIEW.set(RenderSystem.getModelViewMatrix());
            prepareDrawState(shader);
        }
        return new RenderSession(true);
    }

    public static void renderFullBrightYAxis(
        PoseStack poseStack, BlockState state, ResourceLocation modelLocation,
        float angle, float pivotX, float pivotY, float pivotZ
    ) {
        renderFullBright(poseStack, state, modelLocation, angle, pivotX, pivotY, pivotZ, 0.0F, 1.0F, 0.0F);
    }

    public static void renderFullBright(
        PoseStack poseStack, BlockState state, ResourceLocation modelLocation,
        float angle, float pivotX, float pivotY, float pivotZ,
        float axisX, float axisY, float axisZ
    ) {
        VertexBuffer vbo = FULL_BRIGHT_VBOS.get(modelLocation);
        if (vbo == null) {
            vbo = buildFullBrightVBO(state, modelLocation);
            FULL_BRIGHT_VBOS.put(modelLocation, vbo);
        }
        drawVBO(vbo, poseStack, angle, pivotX, pivotY, pivotZ, axisX, axisY, axisZ);
    }

    public static void renderAmbientLit(
        PoseStack poseStack, BlockAndTintGetter level, BlockPos pos,
        BlockState state, ResourceLocation modelLocation,
        float angle, float pivotX, float pivotY, float pivotZ,
        float axisX, float axisY, float axisZ
    ) {
        renderAmbientLit(poseStack, level, pos, pos, state, modelLocation, angle, pivotX, pivotY, pivotZ, axisX, axisY, axisZ);
    }

    public static void renderAmbientLit(
        PoseStack poseStack, BlockAndTintGetter level, BlockPos originPos, BlockPos lightSamplePos,
        BlockState state, ResourceLocation modelLocation,
        float angle, float pivotX, float pivotY, float pivotZ,
        float axisX, float axisY, float axisZ
    ) {
        AmbientVBOKey key = new AmbientVBOKey(System.identityHashCode(level), originPos.immutable(), lightSamplePos.immutable(), modelLocation);
        int lightSig = resolveLightSignature(level, originPos, lightSamplePos, state);
        CachedVBO cached = AMBIENT_VBOS.get(key);
        if (cached == null || cached.lightSignature != lightSig) {
            if (cached != null) {
                cached.vbo.close();
            }
            VertexBuffer vbo = buildAmbientVBO(level, lightSamplePos, state, modelLocation);
            cached = new CachedVBO(vbo, lightSig);
            AMBIENT_VBOS.put(key, cached);
        }
        drawVBO(cached.vbo, poseStack, angle, pivotX, pivotY, pivotZ, axisX, axisY, axisZ);
    }

    public static void removePosition(int worldId, BlockPos pos) {
        var iter = AMBIENT_VBOS.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            AmbientVBOKey k = entry.getKey();
            if (k.worldId == worldId && k.originPos.equals(pos)) {
                entry.getValue().vbo.close();
                iter.remove();
            }
        }
        var lightIter = AMBIENT_LIGHT_SIGNATURES.entrySet().iterator();
        while (lightIter.hasNext()) {
            var entry = lightIter.next();
            AmbientLightKey key = entry.getKey();
            if (key.worldId == worldId && key.originPos.equals(pos)) {
                lightIter.remove();
            }
        }
    }

    public static void clearAll() {
        for (VertexBuffer vbo : FULL_BRIGHT_VBOS.values()) {
            vbo.close();
        }
        FULL_BRIGHT_VBOS.clear();
        for (CachedVBO cached : AMBIENT_VBOS.values()) {
            cached.vbo.close();
        }
        AMBIENT_VBOS.clear();
        AMBIENT_LIGHT_SIGNATURES.clear();
    }

    private static void drawVBO(
        VertexBuffer vbo, PoseStack poseStack,
        float angle, float pivotX, float pivotY, float pivotZ,
        float axisX, float axisY, float axisZ
    ) {
        ShaderInstance shader = renderSessionShader;
        boolean standaloneDraw = shader == null;
        if (standaloneDraw) {
            shader = GameRenderer.getRendertypeCutoutShader();
            if (shader == null) return;
            prepareDrawState(shader);
        }

        poseStack.pushPose();
        poseStack.translate(pivotX, pivotY, pivotZ);
        ROTATION.rotationAxis((float) Math.toRadians(angle), axisX, axisY, axisZ);
        poseStack.mulPose(ROTATION);
        poseStack.translate(-pivotX, -pivotY, -pivotZ);

        Matrix4f modelView = standaloneDraw
            ? DRAW_MODEL_VIEW.set(RenderSystem.getModelViewMatrix())
            : DRAW_MODEL_VIEW.set(RENDER_SESSION_MODEL_VIEW);
        modelView.mul(poseStack.last().pose());

        vbo.bind();
        vbo.drawWithShader(modelView, RenderSystem.getProjectionMatrix(), shader);

        if (standaloneDraw) {
            finishStandaloneDraw();
        }
        poseStack.popPose();
    }

    private static void prepareDrawState(ShaderInstance shader) {
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        Minecraft.getInstance().gameRenderer.lightTexture().turnOnLightLayer();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515);
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
    }

    private static void finishStandaloneDraw() {
        VertexBuffer.unbind();
        RenderSystem.enableCull();
    }

    private static void endRenderSession() {
        if (renderSessionDepth <= 0) {
            return;
        }
        if (--renderSessionDepth > 0) {
            return;
        }
        renderSessionShader = null;
        VertexBuffer.unbind();
        Minecraft.getInstance().gameRenderer.lightTexture().turnOffLightLayer();
        RenderSystem.enableCull();
    }

    private static VertexBuffer buildFullBrightVBO(BlockState state, ResourceLocation modelLocation) {
        BakedModel model = RotatingBlockModelCache.get(modelLocation);
        BufferBuilder builder = new BufferBuilder(262144);
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);

        PoseStack identity = new PoseStack();
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
            identity.last(), builder, state, model,
            1.0F, 1.0F, 1.0F, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null
        );

        VertexBuffer vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
        BufferBuilder.RenderedBuffer rendered = builder.end();
        vbo.bind();
        vbo.upload(rendered);
        VertexBuffer.unbind();
        return vbo;
    }

    private static VertexBuffer buildAmbientVBO(
        BlockAndTintGetter level, BlockPos pos, BlockState state, ResourceLocation modelLocation
    ) {
        BakedModel model = RotatingBlockModelCache.get(modelLocation);
        BufferBuilder builder = new BufferBuilder(262144);
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);

        PoseStack identity = new PoseStack();
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().tesselateBlock(
            level, model, state, pos, identity, builder, false,
            RandomSource.create(), 42L, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null
        );

        VertexBuffer vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
        BufferBuilder.RenderedBuffer rendered = builder.end();
        vbo.bind();
        vbo.upload(rendered);
        VertexBuffer.unbind();
        return vbo;
    }

    private static int resolveLightSignature(BlockAndTintGetter level, BlockPos originPos, BlockPos lightSamplePos, BlockState state) {
        if (!(level instanceof Level worldLevel)) {
            return computeLightSignature(level, lightSamplePos, state);
        }

        AmbientLightKey key = new AmbientLightKey(System.identityHashCode(level), originPos.immutable(), lightSamplePos.immutable());
        long clientTicks = ClientAnimationTicker.ticks();
        int stateHash = state.hashCode();
        CachedLightSignature cached = AMBIENT_LIGHT_SIGNATURES.get(key);
        if (cached != null && cached.clientTicks == clientTicks && cached.stateHash == stateHash) {
            return cached.signature;
        }

        int signature = computeLightSignature(level, lightSamplePos, state);
        AMBIENT_LIGHT_SIGNATURES.put(key, new CachedLightSignature(clientTicks, stateHash, signature));
        return signature;
    }

    private static int computeLightSignature(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        int signature = state.hashCode();
        signature = 31 * signature + level.getRawBrightness(pos, 0);
        for (Direction dir : Direction.values()) {
            BlockPos sidePos = pos.relative(dir);
            BlockState sideState = level.getBlockState(sidePos);
            signature = 31 * signature + level.getRawBrightness(sidePos, 0);
            signature = 31 * signature + (sideState.canOcclude() ? 1 : 0);
        }
        return signature;
    }

    public static int getDestroyStage(BlockPos pos) {
        LevelRenderer levelRenderer = Minecraft.getInstance().levelRenderer;
        Long2ObjectMap<SortedSet<BlockDestructionProgress>> progress = levelRenderer.destructionProgress;
        SortedSet<BlockDestructionProgress> set = progress.get(pos.asLong());
        if (set == null || set.isEmpty()) {
            return -1;
        }
        return set.last().getProgress();
    }

    public static void renderFullBrightThroughBufferSource(
        PoseStack poseStack, MultiBufferSource bufferSource,
        BlockState state, ResourceLocation modelLocation,
        float angle, float pivotX, float pivotY, float pivotZ,
        float axisX, float axisY, float axisZ
    ) {
        BakedModel model = RotatingBlockModelCache.get(modelLocation);
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.cutout());

        poseStack.pushPose();
        poseStack.translate(pivotX, pivotY, pivotZ);
        ROTATION.rotationAxis((float) Math.toRadians(angle), axisX, axisY, axisZ);
        poseStack.mulPose(ROTATION);
        poseStack.translate(-pivotX, -pivotY, -pivotZ);

        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
            poseStack.last(), consumer, state, model,
            1.0F, 1.0F, 1.0F, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
            ModelData.EMPTY, RenderType.cutout()
        );

        poseStack.popPose();
    }

    public static void renderFullBrightYAxisThroughBufferSource(
        PoseStack poseStack, MultiBufferSource bufferSource,
        BlockState state, ResourceLocation modelLocation,
        float angle, float pivotX, float pivotY, float pivotZ
    ) {
        renderFullBrightThroughBufferSource(
            poseStack, bufferSource, state, modelLocation,
            angle, pivotX, pivotY, pivotZ, 0.0F, 1.0F, 0.0F
        );
    }

    public static void renderAmbientLitThroughBufferSource(
        PoseStack poseStack, MultiBufferSource bufferSource,
        BlockAndTintGetter level, BlockPos pos,
        BlockState state, ResourceLocation modelLocation,
        float angle, float pivotX, float pivotY, float pivotZ,
        float axisX, float axisY, float axisZ
    ) {
        renderAmbientLitThroughBufferSourceAt(poseStack, bufferSource, level, pos, state, modelLocation, angle, pivotX, pivotY, pivotZ, axisX, axisY, axisZ);
    }

    public static void renderAmbientLitThroughBufferSourceAt(
        PoseStack poseStack, MultiBufferSource bufferSource,
        BlockAndTintGetter level, BlockPos lightSamplePos,
        BlockState state, ResourceLocation modelLocation,
        float angle, float pivotX, float pivotY, float pivotZ,
        float axisX, float axisY, float axisZ
    ) {
        BakedModel model = RotatingBlockModelCache.get(modelLocation);
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.cutout());

        poseStack.pushPose();
        poseStack.translate(pivotX, pivotY, pivotZ);
        ROTATION.rotationAxis((float) Math.toRadians(angle), axisX, axisY, axisZ);
        poseStack.mulPose(ROTATION);
        poseStack.translate(-pivotX, -pivotY, -pivotZ);

        Minecraft.getInstance().getBlockRenderer().getModelRenderer().tesselateBlock(
            level, model, state, lightSamplePos, poseStack, consumer, false,
            RandomSource.create(), 42L, OverlayTexture.NO_OVERLAY,
            ModelData.EMPTY, RenderType.cutout()
        );

        poseStack.popPose();
    }

    private record AmbientVBOKey(int worldId, BlockPos originPos, BlockPos lightSamplePos,
                                 ResourceLocation modelLocation) {
    }

    private record CachedVBO(VertexBuffer vbo, int lightSignature) {
    }

    private record AmbientLightKey(int worldId, BlockPos originPos, BlockPos lightSamplePos) {
    }

    private record CachedLightSignature(long clientTicks, int stateHash, int signature) {
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
