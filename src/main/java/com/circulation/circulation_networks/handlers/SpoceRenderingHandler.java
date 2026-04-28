package com.circulation.circulation_networks.handlers;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.api.API;
import com.circulation.circulation_networks.client.compat.RenderSystemCompat;
import com.circulation.circulation_networks.client.render.ClientAnimationTicker;
import com.circulation.circulation_networks.items.CirculationConfiguratorModeModel.InspectionMode;
import com.circulation.circulation_networks.items.CirculationConfiguratorModeModel.ToolFunction;
import com.circulation.circulation_networks.items.CirculationConfiguratorState;
import com.circulation.circulation_networks.math.Vec3d;
import com.circulation.circulation_networks.registry.CFNItems;
import com.circulation.circulation_networks.utils.AnimationUtils;
import com.circulation.circulation_networks.utils.BuckyBallGeometry;
import com.circulation.circulation_networks.utils.RenderingGeometryCore;
import com.circulation.circulation_networks.utils.RenderingUtils;
import com.circulation.circulation_networks.utils.WorldResolveCompat;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Arrays;
import java.util.OptionalInt;

@SuppressWarnings("SameParameterValue")
public class SpoceRenderingHandler {

    private static final float BUCKY_LINE_WIDTH = 2.0F;
    private static final Identifier INTERSECTION_PIPELINE_ID =
        Identifier.fromNamespaceAndPath(CirculationFlowNetworks.MOD_ID, "scope_intersection");
    private static final float[] UNIT_SPHERE_VERTICES = RenderingGeometryCore.buildUnitSphereVertices(32, 32);
    private static final RenderPipeline INTERSECTION_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                                                                              .withLocation(INTERSECTION_PIPELINE_ID)
                                                                              .withVertexShader(Identifier.fromNamespaceAndPath(CirculationFlowNetworks.MOD_ID, "core/sphere_depth"))
                                                                              .withFragmentShader(Identifier.fromNamespaceAndPath(CirculationFlowNetworks.MOD_ID, "core/sphere_depth"))
                                                                              .withSampler("DepthSampler")
                                                                              .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
                                                                              .withCull(false)
                                                                              .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
                                                                              .build();

    public static SpoceRenderingHandler INSTANCE;

    protected BlockPos targetPos;
    protected String targetDimensionId;
    protected float linkScope;
    protected float energyScope;
    protected float chargingScope;

    private float lastAnimProgress;
    private float animProgress;
    private float[] rs;
    private MainTarget depthCopyTarget;
    private float pendingIntersectionR;
    private float pendingIntersectionG;
    private float pendingIntersectionB;

    private static float bright(float value) {
        return Math.min(1.0F, value * 1.3F);
    }

    public static void onRegisterRenderPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(INTERSECTION_PIPELINE);
    }

    private void onPreRender() {
        captureSceneDepth();
    }

    private void captureSceneDepth() {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget mainRenderTarget = mc.getMainRenderTarget();
        if (mainRenderTarget == null || mainRenderTarget.getDepthTexture() == null) {
            return;
        }

        int width = mainRenderTarget.width;
        int height = mainRenderTarget.height;
        if (width <= 0 || height <= 0) {
            return;
        }

        ensureDepthCopyTarget(width, height);
        if (depthCopyTarget == null) {
            return;
        }

        depthCopyTarget.copyDepthFrom(mainRenderTarget);
    }

    private void ensureDepthCopyTarget(int width, int height) {
        if (depthCopyTarget != null && depthCopyTarget.width == width && depthCopyTarget.height == height) {
            return;
        }
        if (depthCopyTarget != null) {
            depthCopyTarget.destroyBuffers();
        }
        depthCopyTarget = new MainTarget(width, height);
    }

    private void draw(float rotation, float r, float g, float b, float radius, float wireR, float wireG, float wireB) {
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        drawSphere(r, g, b, radius, 0.2F);
        modelViewStack.rotate(com.mojang.math.Axis.YP.rotationDegrees(rotation));
        modelViewStack.rotate(com.mojang.math.Axis.XP.rotationDegrees(rotation * 0.5F));
        float wireRadius = radius + 0.01F;
        modelViewStack.scale(wireRadius, wireRadius, wireRadius);
        drawBuckyBallWireframe(wireR, wireG, wireB, 0.8F);
        modelViewStack.popMatrix();
    }

    private void drawSphere(float r, float g, float b, float radius, float alpha) {
        RenderingUtils.drawSphere(r, g, b, radius, alpha);
        drawIntersectionOverlay(radius);
    }

    private void drawIntersectionOverlay(float radius) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget mainRenderTarget = mc.getMainRenderTarget();
        if (depthCopyTarget == null
            || depthCopyTarget.getDepthTextureView() == null
            || mainRenderTarget == null
            || mainRenderTarget.getColorTextureView() == null) {
            return;
        }

        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        Matrix4f modelView = new Matrix4f(modelViewStack).scale(radius);

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        int ri = RenderingGeometryCore.toColorComponent(pendingIntersectionR);
        int gi = RenderingGeometryCore.toColorComponent(pendingIntersectionG);
        int bi = RenderingGeometryCore.toColorComponent(pendingIntersectionB);
        int ai = 242;

        for (int i = 0; i < UNIT_SPHERE_VERTICES.length; i += 3) {
            buffer.addVertex(
                UNIT_SPHERE_VERTICES[i],
                UNIT_SPHERE_VERTICES[i + 1],
                UNIT_SPHERE_VERTICES[i + 2]
            ).setColor(ri, gi, bi, ai);
        }

        try (MeshData mesh = buffer.buildOrThrow()) {
            var vertices = DefaultVertexFormat.POSITION_COLOR.uploadImmediateVertexBuffer(mesh.vertexBuffer());
            var dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
                modelView,
                new Vector4f(1.0F, 1.0F, 1.0F, 1.0F),
                new Vector3f(),
                new Matrix4f()
            );
            try (var renderPass = RenderSystem.getDevice()
                                              .createCommandEncoder()
                                              .createRenderPass(
                                                  () -> "CFN scope intersection",
                                                  mainRenderTarget.getColorTextureView(),
                                                  OptionalInt.empty()
                                              )) {
                renderPass.setPipeline(INTERSECTION_PIPELINE);
                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.setUniform("DynamicTransforms", dynamicTransforms);
                renderPass.bindTexture(
                    "DepthSampler",
                    depthCopyTarget.getDepthTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
                );
                renderPass.setVertexBuffer(0, vertices);
                renderPass.draw(0, mesh.drawState().vertexCount());
            }
        }
    }

    private void drawBuckyBallWireframe(float r, float g, float b, float alpha) {
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH);
        int ri = RenderingGeometryCore.toColorComponent(r);
        int gi = RenderingGeometryCore.toColorComponent(g);
        int bi = RenderingGeometryCore.toColorComponent(b);
        int ai = RenderingGeometryCore.toColorComponent(alpha);
        for (int[] edge : BuckyBallGeometry.edges) {
            Vec3d v1 = BuckyBallGeometry.vertices.get(edge[0]);
            Vec3d v2 = BuckyBallGeometry.vertices.get(edge[1]);
            float nx = (float) (v2.x - v1.x);
            float ny = (float) (v2.y - v1.y);
            float nz = (float) (v2.z - v1.z);
            buffer.addVertex((float) v1.x, (float) v1.y, (float) v1.z).setColor(ri, gi, bi, ai).setNormal(nx, ny, nz).setLineWidth(BUCKY_LINE_WIDTH);
            buffer.addVertex((float) v2.x, (float) v2.y, (float) v2.z).setColor(ri, gi, bi, ai).setNormal(nx, ny, nz).setLineWidth(BUCKY_LINE_WIDTH);
        }
        RenderTypes.linesTranslucent().draw(buffer.buildOrThrow());
    }

    public void setStaus(BlockEntity te, double linkScope, double energyScope, double chargingScope) {
        if (te == null || te.getLevel() == null) {
            clear();
            return;
        }
        setStaus(WorldResolveCompat.getDimensionId(te.getLevel()), te.getBlockPos(), linkScope, energyScope, chargingScope);
    }

    public void setStaus(String dimensionId, BlockPos pos, double linkScope, double energyScope, double chargingScope) {
        this.targetDimensionId = dimensionId;
        this.targetPos = pos == null ? null : pos.immutable();
        this.linkScope = (float) linkScope;
        this.energyScope = (float) energyScope;
        this.chargingScope = (float) chargingScope;
        this.animProgress = 0.0F;
        this.lastAnimProgress = 0.0F;

        Integer[] indices = {0, 1, 2};
        float[] scopes = {this.linkScope, this.energyScope, this.chargingScope};
        Arrays.sort(indices, (a, b) -> Float.compare(scopes[b], scopes[a]));
        this.rs = new float[3];
        this.rs[indices[0]] = 1.0F;
        this.rs[indices[1]] = -1.0F;
        this.rs[indices[2]] = 1.0F;
    }

    public void clear() {
        targetPos = null;
        targetDimensionId = null;
        linkScope = 0.0F;
        energyScope = 0.0F;
        chargingScope = 0.0F;
        animProgress = 0.0F;
        lastAnimProgress = 0.0F;
        rs = null;
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Pre event) {
        if (targetPos == null) {
            return;
        }

        lastAnimProgress = animProgress;
        if (animProgress < 1.0F) {
            animProgress = AnimationUtils.advanceTowardsOne(animProgress, 0.025F);
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderLevelStageEvent.AfterTranslucentParticles event) {
        if (targetPos == null || rs == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        BlockPos pos = targetPos;
        if (player == null || mc.level == null || !WorldResolveCompat.getDimensionId(mc.level).equals(targetDimensionId)) {
            clear();
            return;
        }
        if (pos.distToCenterSqr(player.getX(), player.getY(), player.getZ()) > 2500.0D) {
            clear();
            return;
        }
        if (!isTargetStillPresent(mc.level)) {
            clear();
            return;
        }

        var stack = player.getMainHandItem();
        if (!(stack.getItem() == CFNItems.circulationConfigurator
            && CirculationConfiguratorState.getFunction(stack) == ToolFunction.INSPECTION
            && InspectionMode.fromID(CirculationConfiguratorState.getSubMode(stack)).isMode(InspectionMode.SPOCE))) {
            return;
        }

        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        var cameraPos = mc.gameRenderer.getMainCamera().position();
        float tx = (float) (pos.getX() + 0.5D - cameraPos.x);
        float ty = (float) (pos.getY() + 0.5D - cameraPos.y);
        float tz = (float) (pos.getZ() + 0.5D - cameraPos.z);
        float interpFactor = AnimationUtils.easeOutCubic(lastAnimProgress + (animProgress - lastAnimProgress) * partial);

        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.set(event.getModelViewMatrix());
        modelViewStack.translate(tx, ty, tz);
        RenderSystemCompat.applyModelViewMatrix();
        onPreRender();

        float time = ClientAnimationTicker.ticks() + partial;
        float rotation = time * 0.8F;

        if (linkScope > 0.0F) {
            float radius = linkScope * interpFactor;
            float wr = 0.4F;
            float wg = 0.8F;
            float wb = 1.0F;
            pendingIntersectionR = bright(wr);
            pendingIntersectionG = bright(wg);
            pendingIntersectionB = bright(wb);
            draw(rotation * rs[0], 0.0F, 0.4F, 0.8F, radius, wr, wg, wb);
        }

        if (energyScope > 0.0F) {
            float radius = energyScope * interpFactor;
            float wr = 0.8F;
            float wg = 0.6F;
            float wb = 1.0F;
            pendingIntersectionR = bright(wr);
            pendingIntersectionG = bright(wg);
            pendingIntersectionB = bright(wb);
            draw(rotation * rs[1], 0.4F, 0.2F, 0.8F, radius, wr, wg, wb);
        }

        if (chargingScope > 0.0F) {
            float radius = chargingScope * interpFactor;
            float wr = 0.4F;
            float wg = 1.0F;
            float wb = 0.4F;
            pendingIntersectionR = bright(wr);
            pendingIntersectionG = bright(wg);
            pendingIntersectionB = bright(wb);
            draw(rotation * rs[2], 0.0F, 0.5F, 0.1F, radius, wr, wg, wb);
        }

        modelViewStack.popMatrix();
        RenderSystemCompat.applyModelViewMatrix();
    }

    private boolean isTargetStillPresent(Level level) {
        return level != null && targetPos != null && API.getNodeAt(level, targetPos) != null;
    }
}
