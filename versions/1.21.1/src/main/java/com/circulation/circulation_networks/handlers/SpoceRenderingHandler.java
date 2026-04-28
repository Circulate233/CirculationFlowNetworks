package com.circulation.circulation_networks.handlers;

import com.circulation.circulation_networks.api.API;
import com.circulation.circulation_networks.client.render.ClientAnimationTicker;
import com.circulation.circulation_networks.items.CirculationConfiguratorModeModel.InspectionMode;
import com.circulation.circulation_networks.items.CirculationConfiguratorModeModel.ToolFunction;
import com.circulation.circulation_networks.items.CirculationConfiguratorState;
import com.circulation.circulation_networks.math.Vec3d;
import com.circulation.circulation_networks.registry.CFNItems;
import com.circulation.circulation_networks.utils.AnimationUtils;
import com.circulation.circulation_networks.utils.BuckyBallGeometry;
import com.circulation.circulation_networks.utils.ShaderHelper;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.Arrays;

@OnlyIn(Dist.CLIENT)
public class SpoceRenderingHandler {

    private static final ResourceLocation SHADER_VERT = ResourceLocation.fromNamespaceAndPath("circulation_networks", "shaders/sphere_depth.vsh");
    private static final ResourceLocation SHADER_FRAG = ResourceLocation.fromNamespaceAndPath("circulation_networks", "shaders/sphere_depth.fsh");

    public static SpoceRenderingHandler INSTANCE;

    private final FloatBuffer projBuf = MemoryUtil.memAllocFloat(16);
    private final FloatBuffer mvBuf = MemoryUtil.memAllocFloat(16);

    protected BlockPos targetPos;
    protected int targetDimensionId;
    protected float linkScope;
    protected float energyScope;
    protected float chargingScope;
    protected int currentIntersectionSlot = -1;
    protected float currentIntersectionRadius = 0;
    protected float pendingIntersectionR, pendingIntersectionG, pendingIntersectionB;
    protected int sphereVAO, sphereVBO, sphereVertexCount;
    protected int buckyVAO, buckyVBO, buckyVertexCount;

    private float lastAnimProgress;
    private float animProgress;
    private float[] rs;
    private boolean glInitialized = false;
    private int shaderProgram;
    private int depthTextureId;
    private int lastScreenWidth, lastScreenHeight;
    private boolean shaderInitialized;
    private int loc_ModelViewMatrix, loc_ProjectionMatrix;
    private int loc_DepthTexture, loc_ScreenSize;
    private int loc_Near, loc_Far;
    private int loc_SphereColor, loc_IntersectionColor;
    private int loc_IntersectionWidth;
    private int lineShaderProgram;
    private int lineLoc_ModelViewMatrix, lineLoc_ProjectionMatrix, lineLoc_LineColor;

    private static float bright(float v) {
        return Math.min(1.0f, v * 1.3f);
    }

    protected static FloatBuffer buildSphereDataDirect() {
        final int slices = 32, stacks = 32;
        FloatBuffer buf = MemoryUtil.memAllocFloat(slices * stacks * 6 * 3);
        for (int i = 0; i < slices; i++) {
            double p1 = Math.PI * i / slices, p2 = Math.PI * (i + 1) / slices;
            for (int j = 0; j < stacks; j++) {
                double t1 = 2.0 * Math.PI * j / stacks, t2 = 2.0 * Math.PI * (j + 1) / stacks;
                float x00 = (float) (Math.sin(p1) * Math.cos(t1)), y00 = (float) Math.cos(p1), z00 = (float) (Math.sin(p1) * Math.sin(t1));
                float x10 = (float) (Math.sin(p2) * Math.cos(t1)), y10 = (float) Math.cos(p2), z10 = (float) (Math.sin(p2) * Math.sin(t1));
                float x01 = (float) (Math.sin(p1) * Math.cos(t2)), y01 = (float) Math.cos(p1), z01 = (float) (Math.sin(p1) * Math.sin(t2));
                float x11 = (float) (Math.sin(p2) * Math.cos(t2)), y11 = (float) Math.cos(p2), z11 = (float) (Math.sin(p2) * Math.sin(t2));
                buf.put(x00).put(y00).put(z00).put(x10).put(y10).put(z10).put(x01).put(y01).put(z01);
                buf.put(x10).put(y10).put(z10).put(x11).put(y11).put(z11).put(x01).put(y01).put(z01);
            }
        }
        buf.flip();
        return buf;
    }

    protected static FloatBuffer buildBuckyDataDirect() {
        FloatBuffer buf = MemoryUtil.memAllocFloat(BuckyBallGeometry.edges.size() * 2 * 3);
        for (int[] e : BuckyBallGeometry.edges) {
            Vec3d v1 = BuckyBallGeometry.vertices.get(e[0]), v2 = BuckyBallGeometry.vertices.get(e[1]);
            buf.put((float) v1.x).put((float) v1.y).put((float) v1.z);
            buf.put((float) v2.x).put((float) v2.y).put((float) v2.z);
        }
        buf.flip();
        return buf;
    }

    private void draw(float rotation, float r, float g, float b, float radius, float r1, float g1, float b1) {
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        if (shaderProgram <= 0) {
            modelViewStack.popMatrix();
            return;
        }
        drawSphere(r, g, b, radius, 0.2f);
        modelViewStack.rotate(com.mojang.math.Axis.YP.rotationDegrees(rotation));
        modelViewStack.rotate(com.mojang.math.Axis.XP.rotationDegrees(rotation * 0.5F));
        float bR = radius + 0.01f;
        modelViewStack.scale(bR, bR, bR);
        drawBuckyBallWireframe(r1, g1, b1, 0.8f);
        modelViewStack.popMatrix();
    }

    private void drawSphere(float r, float g, float b, float radius, float alpha) {
        if (shaderProgram <= 0) {
            return;
        }

        GlStateManager._glUseProgram(shaderProgram);

        Matrix4fStack mvStack = RenderSystem.getModelViewStack();
        mvStack.pushMatrix();
        mvStack.scale(radius, radius, radius);
        RenderSystem.applyModelViewMatrix();

        mvBuf.clear();
        mvStack.get(mvBuf);
        mvBuf.rewind();

        var projMatrix = RenderSystem.getProjectionMatrix();
        projBuf.clear();
        projMatrix.get(projBuf);
        projBuf.rewind();

        GL20.glUniformMatrix4fv(loc_ProjectionMatrix, false, projBuf);
        GL20.glUniformMatrix4fv(loc_ModelViewMatrix, false, mvBuf);

        float A = projMatrix.m22();
        float B = projMatrix.m32();
        float near = B / (A - 1.0f);
        float far = B / (A + 1.0f);
        GL20.glUniform1f(loc_Near, near);
        GL20.glUniform1f(loc_Far, far);

        GlStateManager._activeTexture(GL13.GL_TEXTURE2);
        GlStateManager._bindTexture(depthTextureId);
        GL20.glUniform1i(loc_DepthTexture, 2);

        GL20.glUniform2f(loc_ScreenSize, lastScreenWidth, lastScreenHeight);

        GL20.glUniform4f(loc_SphereColor, r, g, b, alpha);
        GL20.glUniform4f(loc_IntersectionColor,
            pendingIntersectionR, pendingIntersectionG, pendingIntersectionB, 1.0f);
        GL20.glUniform1f(loc_IntersectionWidth, 0.04f);

        GlStateManager._glBindVertexArray(sphereVAO);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, sphereVertexCount);
        GlStateManager._glBindVertexArray(0);

        mvStack.popMatrix();
        RenderSystem.applyModelViewMatrix();

        GlStateManager._glUseProgram(0);
        GlStateManager._activeTexture(GL13.GL_TEXTURE2);
        GlStateManager._bindTexture(0);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private void drawBuckyBallWireframe(float r, float g, float b, float alpha) {
        if (lineShaderProgram <= 0 || buckyVAO == 0) return;

        GlStateManager._glUseProgram(lineShaderProgram);
        RenderSystem.lineWidth(2.0f);

        Matrix4fStack mvStack = RenderSystem.getModelViewStack();
        mvBuf.clear();
        mvStack.get(mvBuf);
        mvBuf.rewind();

        projBuf.clear();
        RenderSystem.getProjectionMatrix().get(projBuf);
        projBuf.rewind();

        GL20.glUniformMatrix4fv(lineLoc_ModelViewMatrix, false, mvBuf);
        GL20.glUniformMatrix4fv(lineLoc_ProjectionMatrix, false, projBuf);
        GL20.glUniform4f(lineLoc_LineColor, r, g, b, alpha);

        GlStateManager._glBindVertexArray(buckyVAO);
        GL11.glDrawArrays(GL11.GL_LINES, 0, buckyVertexCount);
        GlStateManager._glBindVertexArray(0);

        GlStateManager._glUseProgram(0);
    }

    public void setStaus(BlockEntity te, double linkScope, double energyScope, double chargingScope) {
        if (te == null || te.getLevel() == null) {
            clear();
            return;
        }
        setStaus(te.getLevel().dimension().location().hashCode(), te.getBlockPos(), linkScope, energyScope, chargingScope);
    }

    public void setStaus(int dimensionId, BlockPos pos, double linkScope, double energyScope, double chargingScope) {
        this.targetDimensionId = dimensionId;
        this.targetPos = pos == null ? null : pos.immutable();
        this.linkScope = (float) linkScope;
        this.energyScope = (float) energyScope;
        this.chargingScope = (float) chargingScope;
        this.animProgress = 0;
        this.lastAnimProgress = 0;

        Integer[] indices = {0, 1, 2};
        float[] scopes = {this.linkScope, this.energyScope, this.chargingScope};
        Arrays.sort(indices, (a, b) -> Float.compare(scopes[b], scopes[a]));
        this.rs = new float[3];
        this.rs[indices[0]] = 1.0f;
        this.rs[indices[1]] = -1.0f;
        this.rs[indices[2]] = 1.0f;
    }

    public void clear() {
        cleanupGL();
        glInitialized = false;
        targetPos = null;
        targetDimensionId = 0;
        linkScope = energyScope = chargingScope = 0;
        animProgress = lastAnimProgress = 0;
        rs = null;
        sphereVertexCount = 0;
        buckyVertexCount = 0;
        lastScreenWidth = 0;
        lastScreenHeight = 0;
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Pre event) {
        if (targetPos == null) return;

        lastAnimProgress = animProgress;
        if (animProgress < 1.0f) {
            animProgress = AnimationUtils.advanceTowardsOne(animProgress, 0.025f);
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (targetPos == null) return;
        if (rs == null) {
            clear();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        BlockPos pos = targetPos;
        if (p == null || mc.level == null || mc.level.dimension().location().hashCode() != targetDimensionId || pos.distToCenterSqr(p.getX(), p.getY(), p.getZ()) > 2500) {
            clear();
            return;
        }
        if (!isTargetStillPresent(mc.level)) {
            clear();
            return;
        }

        var stack = p.getMainHandItem();
        if (!(stack.getItem() == CFNItems.circulationConfigurator
            && CirculationConfiguratorState.getFunction(stack) == ToolFunction.INSPECTION
            && InspectionMode.fromID(CirculationConfiguratorState.getSubMode(stack)).isMode(InspectionMode.SPOCE)))
            return;

        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var cameraPos = event.getCamera().getPosition();
        double renderPosX = cameraPos.x;
        double renderPosY = cameraPos.y;
        double renderPosZ = cameraPos.z;

        float tx = (float) (pos.getX() + 0.5 - renderPosX);
        float ty = (float) (pos.getY() + 0.5 - renderPosY);
        float tz = (float) (pos.getZ() + 0.5 - renderPosZ);

        float interpFactor = AnimationUtils.easeOutCubic(lastAnimProgress + (animProgress - lastAnimProgress) * partial);
        Level world = mc.level;

        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.set(event.getModelViewMatrix());
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        modelViewStack.translate(tx, ty, tz);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);

        if (!glInitialized) {
            initGL();
            glInitialized = true;
        }

        onPreRender();
        if (shaderProgram <= 0) {
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            return;
        }

        float time = ClientAnimationTicker.ticks() + partial;
        float rotation = time * 0.8f;

        if (linkScope > 0) {
            final float radius = linkScope * interpFactor;
            final float wr = 0.4f, wg = 0.8f, wb = 1.0f;
            currentIntersectionSlot = 0;
            currentIntersectionRadius = radius;
            pendingIntersectionR = bright(wr);
            pendingIntersectionG = bright(wg);
            pendingIntersectionB = bright(wb);
            RenderSystem.defaultBlendFunc();
            draw(rotation * rs[0], 0, 0.4f, 0.8f, radius, wr, wg, wb);
        }

        if (energyScope > 0) {
            final float radius = energyScope * interpFactor;
            final float wr = 0.8f, wg = 0.6f, wb = 1.0f;
            currentIntersectionSlot = 1;
            currentIntersectionRadius = radius;
            pendingIntersectionR = bright(wr);
            pendingIntersectionG = bright(wg);
            pendingIntersectionB = bright(wb);
            RenderSystem.defaultBlendFunc();
            draw(rotation * rs[1], 0.4f, 0.2f, 0.8f, radius, wr, wg, wb);
        }

        if (chargingScope > 0) {
            final float radius = chargingScope * interpFactor;
            final float wr = 0.4f, wg = 1.0f, wb = 0.4f;
            currentIntersectionSlot = 2;
            currentIntersectionRadius = radius;
            pendingIntersectionR = bright(wr);
            pendingIntersectionG = bright(wg);
            pendingIntersectionB = bright(wb);
            RenderSystem.defaultBlendFunc();
            draw(rotation * rs[2], 0, 0.5f, 0.1f, radius, wr, wg, wb);
        }

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        modelViewStack.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    protected void initGL() {
        FloatBuffer sd = buildSphereDataDirect();
        try {
            sphereVertexCount = sd.limit() / 3;
            sphereVBO = GlStateManager._glGenBuffers();
            sphereVAO = GL30.glGenVertexArrays();
            GlStateManager._glBindVertexArray(sphereVAO);
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, sphereVBO);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, sd, GL15.GL_STATIC_DRAW);
            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 0, 0L);
            GL20.glEnableVertexAttribArray(0);
            GlStateManager._glBindVertexArray(0);
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        } finally {
            MemoryUtil.memFree(sd);
        }

        FloatBuffer bd = buildBuckyDataDirect();
        try {
            buckyVertexCount = bd.limit() / 3;
            buckyVBO = GlStateManager._glGenBuffers();
            buckyVAO = GL30.glGenVertexArrays();
            GlStateManager._glBindVertexArray(buckyVAO);
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, buckyVBO);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, bd, GL15.GL_STATIC_DRAW);
            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 0, 0L);
            GL20.glEnableVertexAttribArray(0);
            GlStateManager._glBindVertexArray(0);
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        } finally {
            MemoryUtil.memFree(bd);
        }
    }

    private void cleanupGL() {
        if (sphereVBO != 0) {
            try {
                GlStateManager._glDeleteBuffers(sphereVBO);
            } catch (Exception ignored) {
            }
            sphereVBO = 0;
        }
        if (sphereVAO != 0) {
            try {
                GL30.glDeleteVertexArrays(sphereVAO);
            } catch (Exception ignored) {
            }
            sphereVAO = 0;
        }
        if (buckyVBO != 0) {
            try {
                GlStateManager._glDeleteBuffers(buckyVBO);
            } catch (Exception ignored) {
            }
            buckyVBO = 0;
        }
        if (buckyVAO != 0) {
            try {
                GL30.glDeleteVertexArrays(buckyVAO);
            } catch (Exception ignored) {
            }
            buckyVAO = 0;
        }
        cleanupShaderResources();
    }

    private void onPreRender() {
        if (!shaderInitialized) {
            initShaderResources();
            shaderInitialized = true;
        }
        if (shaderProgram > 0) {
            captureSceneDepth();
        }
    }

    private boolean isTargetStillPresent(Level world) {
        if (world == null || targetPos == null) {
            return false;
        }
        return API.getNodeAt(world, targetPos) != null;
    }

    private void initShaderResources() {
        shaderProgram = ShaderHelper.loadShader(SHADER_VERT, SHADER_FRAG, "inPosition");
        if (shaderProgram != 0) {
            loc_ModelViewMatrix = GL20.glGetUniformLocation(shaderProgram, "u_ModelViewMatrix");
            loc_ProjectionMatrix = GL20.glGetUniformLocation(shaderProgram, "u_ProjectionMatrix");
            loc_DepthTexture = GL20.glGetUniformLocation(shaderProgram, "u_DepthTexture");
            loc_ScreenSize = GL20.glGetUniformLocation(shaderProgram, "u_ScreenSize");
            loc_Near = GL20.glGetUniformLocation(shaderProgram, "u_Near");
            loc_Far = GL20.glGetUniformLocation(shaderProgram, "u_Far");
            loc_SphereColor = GL20.glGetUniformLocation(shaderProgram, "u_SphereColor");
            loc_IntersectionColor = GL20.glGetUniformLocation(shaderProgram, "u_IntersectionColor");
            loc_IntersectionWidth = GL20.glGetUniformLocation(shaderProgram, "u_IntersectionWidth");
            depthTextureId = GlStateManager._genTexture();
        }

        lineShaderProgram = ShaderHelper.compileInline(
            "#version 150\nin vec3 Position;\nuniform mat4 ModelViewMat;\nuniform mat4 ProjMat;\nvoid main(){gl_Position=ProjMat*ModelViewMat*vec4(Position,1.0);}",
            "#version 150\nuniform vec4 LineColor;\nout vec4 fragColor;\nvoid main(){fragColor=LineColor;}",
            "Position"
        );
        if (lineShaderProgram != 0) {
            lineLoc_ModelViewMatrix = GL20.glGetUniformLocation(lineShaderProgram, "ModelViewMat");
            lineLoc_ProjectionMatrix = GL20.glGetUniformLocation(lineShaderProgram, "ProjMat");
            lineLoc_LineColor = GL20.glGetUniformLocation(lineShaderProgram, "LineColor");
        }
    }

    private void captureSceneDepth() {
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getWidth();
        int h = mc.getWindow().getHeight();

        if (w <= 0 || h <= 0) return;

        GlStateManager._activeTexture(GL13.GL_TEXTURE2);
        GlStateManager._bindTexture(depthTextureId);

        if (w != lastScreenWidth || h != lastScreenHeight) {
            lastScreenWidth = w;
            lastScreenHeight = h;
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_DEPTH_COMPONENT32F,
                w, h, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (FloatBuffer) null);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        }

        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);
        GlStateManager._bindTexture(0);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
    }

    private void cleanupShaderResources() {
        if (shaderProgram != 0) {
            try {
                ShaderHelper.deleteProgram(shaderProgram);
            } catch (Exception ignored) {
            }
            shaderProgram = 0;
        }
        if (lineShaderProgram != 0) {
            try {
                ShaderHelper.deleteProgram(lineShaderProgram);
            } catch (Exception ignored) {
            }
            lineShaderProgram = 0;
        }
        if (depthTextureId != 0) {
            try {
                GlStateManager._deleteTexture(depthTextureId);
            } catch (Exception ignored) {
            }
            depthTextureId = 0;
        }
        shaderInitialized = false;
    }
}
