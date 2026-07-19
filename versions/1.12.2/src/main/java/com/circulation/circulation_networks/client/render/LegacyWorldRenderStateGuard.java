package com.circulation.circulation_networks.client.render;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.ARBMultitexture;
import org.lwjgl.opengl.ARBVertexArrayObject;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
/**
 * Protects legacy fixed-function world passes from inherited GL state and restores the exact raw state afterward.
 */
public final class LegacyWorldRenderStateGuard implements AutoCloseable {

    private static final int GL_CURRENT_PROGRAM = 35725;
    private static final int GL_VERTEX_ARRAY_BINDING = 34229;
    private static final int GL_ARRAY_BUFFER_BINDING = 34964;
    private static final int GL_BLEND_EQUATION_RGB = 32777;
    private static final int GL_BLEND_EQUATION_ALPHA = 34877;
    private static final int GL_BLEND_SRC_RGB = 32969;
    private static final int GL_BLEND_DST_RGB = 32968;
    private static final int GL_BLEND_SRC_ALPHA = 32971;
    private static final int GL_BLEND_DST_ALPHA = 32970;
    private static final int GL_COLOR_LOGIC_OP = 3058;
    private static final int GL_LOGIC_OP_MODE = 3056;
    private static final int GL_COLOR_MATERIAL_FACE = 2901;
    private static final int GL_COLOR_MATERIAL_PARAMETER = 2902;
    private static final int GL12_RESCALE_NORMAL = 32826;
    private static final int TEXTURE_TARGET_1D_BIT = 1;
    private static final int TEXTURE_TARGET_2D_BIT = 1 << 1;
    private static final int TEXTURE_TARGET_3D_BIT = 1 << 2;
    private static final int TEXTURE_TARGET_CUBE_MAP_BIT = 1 << 3;
    private static final int TEX_GEN_S_BIT = 1;
    private static final int TEX_GEN_T_BIT = 1 << 1;
    private static final int TEX_GEN_R_BIT = 1 << 2;
    private static final int TEX_GEN_Q_BIT = 1 << 3;
    private static final int MINECRAFT_TEXTURE_UNIT_COUNT = 8;
    private static final int TEXTURE_ENV_FIELD_COUNT = 17;
    private static final int[] TEXTURE_ENV_INT_NAMES = {
        GL11.GL_TEXTURE_ENV_MODE,
        GL13.GL_COMBINE_RGB, GL13.GL_COMBINE_ALPHA,
        GL13.GL_SOURCE0_RGB, GL13.GL_SOURCE1_RGB, GL13.GL_SOURCE2_RGB,
        GL13.GL_SOURCE0_ALPHA, GL13.GL_SOURCE1_ALPHA, GL13.GL_SOURCE2_ALPHA,
        GL13.GL_OPERAND0_RGB, GL13.GL_OPERAND1_RGB, GL13.GL_OPERAND2_RGB,
        GL13.GL_OPERAND0_ALPHA, GL13.GL_OPERAND1_ALPHA, GL13.GL_OPERAND2_ALPHA,
        GL13.GL_RGB_SCALE, GL11.GL_ALPHA_SCALE
    };
    private static final float DEFAULT_ALPHA_REF = 0.1F;
    private static final ThreadLocal<LegacyWorldRenderStateGuard> GUARDS =
        ThreadLocal.withInitial(LegacyWorldRenderStateGuard::new);

    private enum Profile {
        HUB("HUB", true, false, false),
        NODE_HUD("NODE_HUD", true, true, true),
        OVERLAY("OVERLAY", false, true, true);

        private final String diagnosticName;
        private final boolean strongTextureIsolation;
        private final boolean forceProgramZero;
        private final boolean disableDepthAndFog;
        private boolean strongEntryScanCompleted;
        private boolean textureDiagnosticLogged;
        private boolean inheritedProgramLogged;

        Profile(String diagnosticName, boolean strongTextureIsolation, boolean forceProgramZero,
                boolean disableDepthAndFog) {
            this.diagnosticName = diagnosticName;
            this.strongTextureIsolation = strongTextureIsolation;
            this.forceProgramZero = forceProgramZero;
            this.disableDepthAndFog = disableDepthAndFog;
        }
    }

    private final Lifecycle lifecycle = new Lifecycle();
    private final MatrixStackAccess modelViewStack = MatrixStackAccess.openGl(GL11.GL_MODELVIEW_STACK_DEPTH);
    private final MatrixStackAccess projectionStack = MatrixStackAccess.openGl(GL11.GL_PROJECTION_STACK_DEPTH);
    private final MatrixStackAccess texture0Stack = MatrixStackAccess.openGl(GL11.GL_TEXTURE_STACK_DEPTH);
    private final MatrixStackAccess texture1Stack = MatrixStackAccess.openGl(GL11.GL_TEXTURE_STACK_DEPTH);
    private final FloatBuffer float16 = directFloatBuffer(16);
    // LWJGL2 validates GL11.glGetFloat against its maximum 16-float return size,
    // even for four-component state such as current color and texture coordinates.
    private final FloatBuffer float4 = directFloatBuffer(16);
    // LWJGL2 applies the same 16-element validation to glGetInteger/glGetBoolean.
    private final IntBuffer int4 = directIntBuffer();
    private final ByteBuffer byte4 = directByteBuffer(16);

    private final boolean[] texture2dEnabled = new boolean[2];
    private final int[] textureBinding = new int[2];
    private final int[] textureStackDepth = new int[2];
    private final float[][] textureMatrix = new float[2][16];
    private int[] strongTextureTargetMasks = new int[0];
    private int[] strongTexGenMasks = new int[0];
    private final TextureEnvironmentState textureEnvironment = new TextureEnvironmentState();
    private final TextureCoordinates texture0Coordinates = new TextureCoordinates();
    private final TextureCoordinates lightmapCoordinates = new TextureCoordinates();
    private final BlendEquations blendEquations = new BlendEquations();
    private final float[] modelViewMatrix = new float[16];
    private final float[] projectionMatrix = new float[16];
    private final int[] viewport = new int[4];
    private final int[] scissorBox = new int[4];
    private final boolean[] colorMask = new boolean[4];
    private final float[] currentColor = new float[4];
    private final float[][] lightState = new float[9][4];

    private ContextCapabilities capabilities;
    private boolean programSupported;
    private boolean vaoSupported;
    private boolean vboSupported;
    private boolean clientAttributesPushed;
    private boolean activeTextureCaptured;
    private Profile profile;
    private boolean hubLightmapEstablished;
    private int maxFixedFunctionTextureUnits;
    private int activeTexture;
    private int matrixMode;
    private int modelViewStackDepth;
    private int projectionStackDepth;
    private int program;
    private int vertexArray;
    private int arrayBuffer;
    private boolean alphaEnabled;
    private int alphaFunc;
    private float alphaRef;
    private boolean blendEnabled;
    private int blendSrcRgb;
    private int blendDstRgb;
    private int blendSrcAlpha;
    private int blendDstAlpha;
    private boolean depthEnabled;
    private int depthFunc;
    private boolean depthMask;
    private boolean cullEnabled;
    private int cullFace;
    private boolean lightingEnabled;
    private boolean light0Enabled;
    private boolean light1Enabled;
    private boolean colorMaterialEnabled;
    private int colorMaterialFace;
    private int colorMaterialParameter;
    private boolean fogEnabled;
    private boolean colorLogicEnabled;
    private int colorLogicOperation;
    private boolean rescaleNormalEnabled;
    private int shadeModel;
    private boolean scissorEnabled;
    private int unexpectedTextureUnit;
    private int unexpectedTextureTargets;
    private int unexpectedTexGenUnit;
    private int unexpectedTexGenCoordinates;

    private LegacyWorldRenderStateGuard() {
    }

    private void captureState() {
        capabilities = GLContext.getCapabilities();
        programSupported = capabilities.OpenGL21 || capabilities.GL_ARB_shader_objects
            && capabilities.GL_ARB_vertex_shader && capabilities.GL_ARB_fragment_shader;
        vaoSupported = capabilities.OpenGL30 || capabilities.GL_ARB_vertex_array_object;
        vboSupported = OpenGlHelper.vboSupported;
        if (profile.strongTextureIsolation) {
            maxFixedFunctionTextureUnits = GL11.glGetInteger(GL13.GL_MAX_TEXTURE_UNITS);
            if (maxFixedFunctionTextureUnits < 2) {
                throw new IllegalStateException(lifecycle.passName() + " requires at least two fixed-function texture units, found "
                    + maxFixedFunctionTextureUnits);
            }
            ensureStrongTextureStateCapacity(maxFixedFunctionTextureUnits);
        }
        GL11.glPushClientAttrib(clientArraySnapshotMask());
        clientAttributesPushed = true;
        activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        activeTextureCaptured = true;
        int activeTextureIndex = activeTexture - OpenGlHelper.defaultTexUnit;
        if (activeTextureIndex < 0 || activeTextureIndex >= MINECRAFT_TEXTURE_UNIT_COUNT) {
            throw new IllegalStateException(lifecycle.passName() + " active texture unit index "
                + activeTextureIndex + " is outside Minecraft's cached range [0, 7]");
        }
        matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        modelViewStackDepth = GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);
        projectionStackDepth = GL11.glGetInteger(GL11.GL_PROJECTION_STACK_DEPTH);
        readMatrix(GL11.GL_MODELVIEW_MATRIX, modelViewMatrix);
        readMatrix(GL11.GL_PROJECTION_MATRIX, projectionMatrix);
        int capturedTextureUnits = profile.strongTextureIsolation ? maxFixedFunctionTextureUnits : 2;
        for (int index = 0; index < capturedTextureUnits; index++) {
            int textureUnit = OpenGlHelper.defaultTexUnit + index;
            OpenGlHelper.setActiveTexture(textureUnit);
            if (profile.strongTextureIsolation) {
                strongTextureTargetMasks[index] = enabledTextureTargetMask();
                strongTexGenMasks[index] = enabledTexGenMask();
            }
            if (index >= 2) {
                continue;
            }
            texture2dEnabled[index] = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
            textureBinding[index] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            textureStackDepth[index] = GL11.glGetInteger(GL11.GL_TEXTURE_STACK_DEPTH);
            readMatrix(GL11.GL_TEXTURE_MATRIX, textureMatrix[index]);
            textureEnvironment.capture(index, float4);
            if (index == 0) {
                texture0Coordinates.capture(float4);
            } else {
                lightmapCoordinates.capture(float4);
            }
        }
        OpenGlHelper.setActiveTexture(activeTexture);

        program = programSupported ? GL11.glGetInteger(GL_CURRENT_PROGRAM) : 0;
        readInt4(GL11.GL_VIEWPORT, viewport);
        vertexArray = vaoSupported ? GL11.glGetInteger(GL_VERTEX_ARRAY_BINDING) : 0;
        arrayBuffer = vboSupported ? GL11.glGetInteger(GL_ARRAY_BUFFER_BINDING) : 0;

        alphaEnabled = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        alphaFunc = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
        alphaRef = GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
        blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        blendEquations.set(GL11.glGetInteger(GL_BLEND_EQUATION_RGB), GL11.glGetInteger(GL_BLEND_EQUATION_ALPHA));
        blendSrcRgb = GL11.glGetInteger(GL_BLEND_SRC_RGB);
        blendDstRgb = GL11.glGetInteger(GL_BLEND_DST_RGB);
        blendSrcAlpha = GL11.glGetInteger(GL_BLEND_SRC_ALPHA);
        blendDstAlpha = GL11.glGetInteger(GL_BLEND_DST_ALPHA);
        depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        cullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        cullFace = GL11.glGetInteger(GL11.GL_CULL_FACE_MODE);
        lightingEnabled = GL11.glIsEnabled(GL11.GL_LIGHTING);
        light0Enabled = GL11.glIsEnabled(GL11.GL_LIGHT0);
        light1Enabled = GL11.glIsEnabled(GL11.GL_LIGHT1);
        colorMaterialEnabled = GL11.glIsEnabled(GL11.GL_COLOR_MATERIAL);
        colorMaterialFace = GL11.glGetInteger(GL_COLOR_MATERIAL_FACE);
        colorMaterialParameter = GL11.glGetInteger(GL_COLOR_MATERIAL_PARAMETER);
        fogEnabled = GL11.glIsEnabled(GL11.GL_FOG);
        colorLogicEnabled = GL11.glIsEnabled(GL_COLOR_LOGIC_OP);
        colorLogicOperation = GL11.glGetInteger(GL_LOGIC_OP_MODE);
        rescaleNormalEnabled = GL11.glIsEnabled(GL12_RESCALE_NORMAL);
        shadeModel = GL11.glGetInteger(GL11.GL_SHADE_MODEL);
        scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        readInt4(GL11.GL_SCISSOR_BOX, scissorBox);
        readBoolean4(colorMask);
        readFloat4(GL11.GL_CURRENT_COLOR, currentColor);
        readLightingState();
        logInheritedHubProgram();
        if (profile.strongTextureIsolation && !profile.strongEntryScanCompleted) {
            detectAndLogUnexpectedTextureState();
        }
    }

    public static LegacyWorldRenderStateGuard openHubPass(String passName) {
        return open(passName, Profile.HUB);
    }

    public static LegacyWorldRenderStateGuard openNodeHudPass(String passName) {
        return open(passName, Profile.NODE_HUD);
    }

    public static LegacyWorldRenderStateGuard openHudPass(String passName) {
        return open(passName, Profile.OVERLAY);
    }

    private static LegacyWorldRenderStateGuard open(String passName, Profile profile) {
        LegacyWorldRenderStateGuard guard = GUARDS.get();
        Thread currentThread = Thread.currentThread();
        try {
            guard.lifecycle.acquire(passName, currentThread);
        } catch (IllegalStateException failure) {
            CirculationFlowNetworks.LOGGER.error("Render state guard lifecycle violation", failure);
            throw failure;
        }
        boolean captured = false;
        try {
            guard.prepareCapture(profile);
            guard.hubLightmapEstablished = false;
            guard.captureState();
            captured = true;
            guard.applyProfile();
            return guard;
        } catch (RuntimeException | Error failure) {
            if (captured) {
                try {
                    guard.close();
                } catch (RuntimeException | Error restoreFailure) {
                    failure.addSuppressed(restoreFailure);
                }
            } else {
                try {
                    guard.abortCapture();
                } catch (RuntimeException | Error restoreFailure) {
                    failure.addSuppressed(restoreFailure);
                }
                try {
                    guard.clearPassState();
                } catch (RuntimeException | Error clearFailure) {
                    failure.addSuppressed(clearFailure);
                }
                try {
                    guard.lifecycle.release(currentThread);
                } catch (RuntimeException | Error releaseFailure) {
                    failure.addSuppressed(releaseFailure);
                }
            }
            CirculationFlowNetworks.LOGGER.error("Failed to establish {} render profile", passName, failure);
            throw failure;
        }
    }

    private void applyProfile() {
        if (shouldBindProgramZero(profile.forceProgramZero, programSupported)) {
            OpenGlHelper.glUseProgram(0);
        }
        bindVertexArray(0);
        if (vboSupported) {
            OpenGlHelper.glBindBuffer(OpenGlHelper.GL_ARRAY_BUFFER, 0);
        }
        establishClientArrayProfile();

        setTextureProfile(OpenGlHelper.defaultTexUnit, true);
        setTextureProfile(OpenGlHelper.lightmapTexUnit, false);
        if (profile.strongTextureIsolation) {
            for (int index = 2; index < maxFixedFunctionTextureUnits; index++) {
                OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit + index);
                applyRawStrongTextureTargets(false);
            }
        }
        syncActiveTexture(OpenGlHelper.defaultTexUnit);
        setMatrixMode(GL11.GL_MODELVIEW);

        syncColor(1.0F, 1.0F, 1.0F, 1.0F);
        syncColorMask(true, true, true, true);
        syncAlpha(true, GL11.GL_GREATER, DEFAULT_ALPHA_REF);
        syncBlend(true, GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD,
            GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        syncDepth(!profile.disableDepthAndFog, GL11.GL_LEQUAL, !profile.disableDepthAndFog);
        syncCull(false, GL11.GL_BACK);
        syncLighting(false, false, false, false);
        syncRescaleNormal(false);
        syncShadeModel(GL11.GL_FLAT);
        syncScissor(false, 0, 0, 1, 1);

        if (shouldDisableFogForProfile(profile.disableDepthAndFog)) {
            syncFog(false);
        }
        syncColorLogic(false, GL11.GL_COPY);
    }

    static boolean shouldBindProgramZero(boolean forceProgramZero, boolean shadersAvailable) {
        return forceProgramZero && shadersAvailable;
    }

    static boolean shouldDisableFogForProfile(boolean disableDepthAndFog) {
        return disableDepthAndFog;
    }

    static boolean shouldDisableVanillaLightmapTracker(boolean hubProfile, boolean lightmapEstablished,
                                                       boolean entryLightmapEnabled) {
        return hubProfile && lightmapEstablished && !entryLightmapEnabled;
    }

    static int clientArraySnapshotMask() {
        return GL11.GL_CLIENT_VERTEX_ARRAY_BIT;
    }

    private void prepareCapture(Profile requestedProfile) {
        profile = requestedProfile;
        clientAttributesPushed = false;
        activeTextureCaptured = false;
        hubLightmapEstablished = false;
        maxFixedFunctionTextureUnits = 0;
        unexpectedTextureUnit = -1;
        unexpectedTextureTargets = 0;
        unexpectedTexGenUnit = -1;
        unexpectedTexGenCoordinates = 0;
    }

    private void logInheritedHubProgram() {
        if (profile != Profile.HUB || program == 0 || profile.inheritedProgramLogged) {
            return;
        }
        profile.inheritedProgramLogged = true;
        CirculationFlowNetworks.LOGGER.info(
            "HUB preserving inherited non-zero program {} for {}", program, lifecycle.passName());
    }

    private void detectAndLogUnexpectedTextureState() {
        for (int index = 0; index < maxFixedFunctionTextureUnits; index++) {
            int textureTargets = unexpectedTextureTargetMask(index, strongTextureTargetMasks[index]);
            if (textureTargets != 0 && unexpectedTextureUnit < 0) {
                unexpectedTextureUnit = index;
                unexpectedTextureTargets = textureTargets;
            }
            int texGenCoordinates = strongTexGenMasks[index];
            if (texGenCoordinates != 0 && unexpectedTexGenUnit < 0) {
                unexpectedTexGenUnit = index;
                unexpectedTexGenCoordinates = texGenCoordinates;
            }
        }
        profile.strongEntryScanCompleted = true;

        if (profile.textureDiagnosticLogged
            || !isUnexpectedStrongProfileState(profile.forceProgramZero, program,
            activeTexture, OpenGlHelper.defaultTexUnit,
            currentColor[0], currentColor[1], currentColor[2], currentColor[3],
            lightmapCoordinates.x, lightmapCoordinates.y, lightmapCoordinates.z, lightmapCoordinates.w,
            OpenGlHelper.lastBrightnessX, OpenGlHelper.lastBrightnessY,
            unexpectedTextureTargets, unexpectedTexGenCoordinates)) {
            return;
        }

        profile.textureDiagnosticLogged = true;
        CirculationFlowNetworks.LOGGER.warn(
            "Unexpected inherited GL state for {}: program={}, activeTexture={}, color=({}, {}, {}, {}), "
                + "lightmap=({}, {}, {}, {}), textureTargets(unit={}, mask={}, 1D={}, 2D={}, 3D={}, cube={}), "
                + "texGen(unit={}, mask={}, S={}, T={}, R={}, Q={})",
            profile.diagnosticName, program, activeTexture,
            currentColor[0], currentColor[1], currentColor[2], currentColor[3],
            lightmapCoordinates.x, lightmapCoordinates.y, lightmapCoordinates.z, lightmapCoordinates.w,
            unexpectedTextureUnit, unexpectedTextureTargets,
            (unexpectedTextureTargets & TEXTURE_TARGET_1D_BIT) != 0,
            (unexpectedTextureTargets & TEXTURE_TARGET_2D_BIT) != 0,
            (unexpectedTextureTargets & TEXTURE_TARGET_3D_BIT) != 0,
            (unexpectedTextureTargets & TEXTURE_TARGET_CUBE_MAP_BIT) != 0,
            unexpectedTexGenUnit, unexpectedTexGenCoordinates,
            (unexpectedTexGenCoordinates & TEX_GEN_S_BIT) != 0,
            (unexpectedTexGenCoordinates & TEX_GEN_T_BIT) != 0,
            (unexpectedTexGenCoordinates & TEX_GEN_R_BIT) != 0,
            (unexpectedTexGenCoordinates & TEX_GEN_Q_BIT) != 0);
    }

    static boolean isUnexpectedStrongProfileState(boolean forceProgramZero, int program,
                                                   int activeTexture, int defaultTexture,
                                                   float red, float green, float blue, float alpha,
                                                   float lightmapX, float lightmapY, float lightmapZ, float lightmapW,
                                                   float trackedLightmapX, float trackedLightmapY,
                                                   int unexpectedTextureTargets, int texGenCoordinates) {
        return (forceProgramZero && program != 0) || activeTexture != defaultTexture
            || Float.compare(red, 1.0F) != 0 || Float.compare(green, 1.0F) != 0
            || Float.compare(blue, 1.0F) != 0 || Float.compare(alpha, 1.0F) != 0
            || Float.compare(lightmapX, trackedLightmapX) != 0
            || Float.compare(lightmapY, trackedLightmapY) != 0
            || Float.compare(lightmapZ, 0.0F) != 0 || Float.compare(lightmapW, 1.0F) != 0
            || unexpectedTextureTargets != 0 || texGenCoordinates != 0;
    }

    static int unexpectedTextureTargetMask(int textureUnitIndex, int enabledTextureTargets) {
        if (textureUnitIndex < 0) {
            throw new IllegalArgumentException("Texture unit index must not be negative: " + textureUnitIndex);
        }
        if (textureUnitIndex < 2) {
            return enabledTextureTargets & ~TEXTURE_TARGET_2D_BIT;
        }
        return enabledTextureTargets;
    }

    private static int enabledTextureTargetMask() {
        int mask = 0;
        if (GL11.glIsEnabled(GL11.GL_TEXTURE_1D)) mask |= TEXTURE_TARGET_1D_BIT;
        if (GL11.glIsEnabled(GL11.GL_TEXTURE_2D)) mask |= TEXTURE_TARGET_2D_BIT;
        if (GL11.glIsEnabled(GL12.GL_TEXTURE_3D)) mask |= TEXTURE_TARGET_3D_BIT;
        if (GL11.glIsEnabled(GL13.GL_TEXTURE_CUBE_MAP)) mask |= TEXTURE_TARGET_CUBE_MAP_BIT;
        return mask;
    }

    private static int enabledTexGenMask() {
        int mask = 0;
        if (GL11.glIsEnabled(GL11.GL_TEXTURE_GEN_S)) mask |= TEX_GEN_S_BIT;
        if (GL11.glIsEnabled(GL11.GL_TEXTURE_GEN_T)) mask |= TEX_GEN_T_BIT;
        if (GL11.glIsEnabled(GL11.GL_TEXTURE_GEN_R)) mask |= TEX_GEN_R_BIT;
        if (GL11.glIsEnabled(GL11.GL_TEXTURE_GEN_Q)) mask |= TEX_GEN_Q_BIT;
        return mask;
    }

    private void ensureStrongTextureStateCapacity(int textureUnits) {
        if (strongTextureTargetMasks.length >= textureUnits) {
            return;
        }
        strongTextureTargetMasks = new int[textureUnits];
        strongTexGenMasks = new int[textureUnits];
    }

    private static void applyTextureTargetMask(int mask) {
        setCapability(GL11.GL_TEXTURE_1D, (mask & TEXTURE_TARGET_1D_BIT) != 0);
        setCapability(GL11.GL_TEXTURE_2D, (mask & TEXTURE_TARGET_2D_BIT) != 0);
        setCapability(GL12.GL_TEXTURE_3D, (mask & TEXTURE_TARGET_3D_BIT) != 0);
        setCapability(GL13.GL_TEXTURE_CUBE_MAP, (mask & TEXTURE_TARGET_CUBE_MAP_BIT) != 0);
    }

    private static void applyTexGenMask(int mask) {
        setCapability(GL11.GL_TEXTURE_GEN_S, (mask & TEX_GEN_S_BIT) != 0);
        setCapability(GL11.GL_TEXTURE_GEN_T, (mask & TEX_GEN_T_BIT) != 0);
        setCapability(GL11.GL_TEXTURE_GEN_R, (mask & TEX_GEN_R_BIT) != 0);
        setCapability(GL11.GL_TEXTURE_GEN_Q, (mask & TEX_GEN_Q_BIT) != 0);
    }

    private static void establishClientArrayProfile() {
        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
        GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
        GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
    }

    private void applyTextureEnvironmentProfile(int textureIndex) {
        for (int field = 0; field < TEXTURE_ENV_FIELD_COUNT; field++) {
            int name = TEXTURE_ENV_INT_NAMES[field];
            int value = textureEnvironmentProfileValue(textureIndex, name);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, name, value);
            GlStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, name, value);
        }
        float4.clear();
        float4.put(1.0F).put(1.0F).put(1.0F).put(1.0F).flip();
        GL11.glTexEnv(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_COLOR, float4);
    }

    static int textureEnvironmentProfileValue(int textureIndex, int name) {
        if (textureIndex < 0 || textureIndex > 1) {
            throw new IllegalArgumentException("Unknown texture unit profile " + textureIndex);
        }
        if (name == GL11.GL_TEXTURE_ENV_MODE) return GL13.GL_COMBINE;
        if (name == GL13.GL_COMBINE_RGB || name == GL13.GL_COMBINE_ALPHA) return GL11.GL_MODULATE;
        if (name == GL13.GL_SOURCE0_RGB || name == GL13.GL_SOURCE0_ALPHA) return GL11.GL_TEXTURE;
        if (name == GL13.GL_SOURCE1_RGB || name == GL13.GL_SOURCE1_ALPHA) {
            return textureIndex == 0 ? GL13.GL_PRIMARY_COLOR : GL13.GL_PREVIOUS;
        }
        if (name == GL13.GL_SOURCE2_RGB || name == GL13.GL_SOURCE2_ALPHA) return GL13.GL_CONSTANT;
        if (name == GL13.GL_OPERAND0_RGB || name == GL13.GL_OPERAND1_RGB) return GL11.GL_SRC_COLOR;
        if (name == GL13.GL_OPERAND2_RGB) return GL11.GL_SRC_ALPHA;
        if (name == GL13.GL_OPERAND0_ALPHA || name == GL13.GL_OPERAND1_ALPHA
            || name == GL13.GL_OPERAND2_ALPHA) return GL11.GL_SRC_ALPHA;
        if (name == GL13.GL_RGB_SCALE || name == GL11.GL_ALPHA_SCALE) return 1;
        throw new IllegalArgumentException("Unknown texture environment field " + name);
    }

    private void setTextureProfile(int textureUnit, boolean enabled) {
        int textureIndex = textureUnit == OpenGlHelper.defaultTexUnit ? 0 : 1;
        syncActiveTexture(textureUnit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GlStateManager.bindTexture(0);
        if (profile.strongTextureIsolation) {
            applyRawStrongTextureTargets(enabled);
        }
        syncTexture2d(enabled);
        setMatrixMode(GL11.GL_TEXTURE);
        GL11.glLoadIdentity();
        GlStateManager.loadIdentity();
        applyTextureEnvironmentProfile(textureIndex);
    }

    private static void applyRawStrongTextureTargets(boolean texture2dEnabled) {
        GL11.glDisable(GL11.GL_TEXTURE_1D);
        setCapability(GL11.GL_TEXTURE_2D, texture2dEnabled);
        GL11.glDisable(GL12.GL_TEXTURE_3D);
        GL11.glDisable(GL13.GL_TEXTURE_CUBE_MAP);
        GL11.glDisable(GL11.GL_TEXTURE_GEN_S);
        GL11.glDisable(GL11.GL_TEXTURE_GEN_T);
        GL11.glDisable(GL11.GL_TEXTURE_GEN_R);
        GL11.glDisable(GL11.GL_TEXTURE_GEN_Q);
    }

    /** Reasserts fixed-function texture state after vanilla has installed the world lightmap texture and matrix. */
    public void establishHubLightmapProfile() {
        Thread currentThread = Thread.currentThread();
        lifecycle.requireClose(currentThread);
        if (profile != Profile.HUB) {
            throw new IllegalStateException(lifecycle.passName() + " cannot establish a hub lightmap profile");
        }
        hubLightmapEstablished = true;
        syncActiveTexture(OpenGlHelper.defaultTexUnit);
        applyRawStrongTextureTargets(true);
        syncTexture2d(true);
        applyTextureEnvironmentProfile(0);
        syncActiveTexture(OpenGlHelper.lightmapTexUnit);
        applyRawStrongTextureTargets(true);
        syncTexture2d(true);
        applyTextureEnvironmentProfile(1);
        syncActiveTexture(OpenGlHelper.defaultTexUnit);
        setMatrixMode(GL11.GL_MODELVIEW);
        syncColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public void close() {
        Thread currentThread = Thread.currentThread();
        try {
            lifecycle.requireClose(currentThread);
        } catch (IllegalStateException failure) {
            CirculationFlowNetworks.LOGGER.error("Render state guard lifecycle violation", failure);
            throw failure;
        }
        try {
            Throwable restorationFailure = null;
            int actualModelViewDepth = modelViewStackDepth;
            int actualProjectionDepth = projectionStackDepth;
            int actualTexture0Depth = textureStackDepth[0];
            int actualTexture1Depth = textureStackDepth[1];

            try {
                restoreVanillaLightmapTracker();
            } catch (RuntimeException | Error failure) {
                restorationFailure = appendFailure(restorationFailure, failure);
            }

            try {
                restoreClientArrays();
            } catch (RuntimeException | Error failure) {
                restorationFailure = appendFailure(restorationFailure, failure);
            }

            try {
                actualModelViewDepth = restoreMatrixStack(
                    GL11.GL_MODELVIEW, modelViewStack, modelViewStackDepth, modelViewMatrix, -1, 0);
            } catch (RuntimeException | Error failure) {
                restorationFailure = appendFailure(restorationFailure, failure);
            }
            try {
                actualProjectionDepth = restoreMatrixStack(
                    GL11.GL_PROJECTION, projectionStack, projectionStackDepth, projectionMatrix, -1, 1);
            } catch (RuntimeException | Error failure) {
                restorationFailure = appendFailure(restorationFailure, failure);
            }

            if (profile.strongTextureIsolation) {
                try {
                    restoreStrongTextureState();
                } catch (RuntimeException | Error failure) {
                    restorationFailure = appendFailure(restorationFailure, failure);
                }
            }

            try {
                restoreLightingState();
            } catch (RuntimeException | Error failure) {
                restorationFailure = appendFailure(restorationFailure, failure);
            }
            try {
                syncFog(fogEnabled);
            } catch (RuntimeException | Error failure) {
                restorationFailure = appendFailure(restorationFailure, failure);
            }
            try {
                syncColorLogic(colorLogicEnabled, colorLogicOperation);
            } catch (RuntimeException | Error failure) {
                restorationFailure = appendFailure(restorationFailure, failure);
            }
            try {
                syncRescaleNormal(rescaleNormalEnabled);
            } catch (RuntimeException | Error failure) {
                restorationFailure = appendFailure(restorationFailure, failure);
            }
            try {
                syncShadeModel(shadeModel);
            } catch (RuntimeException | Error failure) {
                restorationFailure = appendFailure(restorationFailure, failure);
            }
            try {
                syncCull(cullEnabled, cullFace);
            } catch (RuntimeException | Error failure) {
                restorationFailure = appendFailure(restorationFailure, failure);
            }
            try {
                syncDepth(depthEnabled, depthFunc, depthMask);
            } catch (RuntimeException | Error failure) {
                restorationFailure = appendFailure(restorationFailure, failure);
            }
            try {
                syncBlend(blendEnabled, blendEquations.rgb(), blendEquations.alpha(),
                    blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
            } catch (RuntimeException | Error failure) {
                restorationFailure = appendFailure(restorationFailure, failure);
            }
            try {
                syncAlpha(alphaEnabled, alphaFunc, alphaRef);
            } catch (RuntimeException | Error failure) {
                restorationFailure = appendFailure(restorationFailure, failure);
            }
            try {
                syncColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
            } catch (RuntimeException | Error failure) {
                restorationFailure = appendFailure(restorationFailure, failure);
            }
            try {
                syncColor(currentColor[0], currentColor[1], currentColor[2], currentColor[3]);
            } catch (RuntimeException | Error failure) {
                restorationFailure = appendFailure(restorationFailure, failure);
            }
            try {
                syncScissor(scissorEnabled, scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
            } catch (RuntimeException | Error failure) {
                restorationFailure = appendFailure(restorationFailure, failure);
            }

            try {
                actualTexture0Depth = restoreMatrixStack(
                    GL11.GL_TEXTURE, texture0Stack, textureStackDepth[0], textureMatrix[0],
                    OpenGlHelper.defaultTexUnit, 2);
            } catch (RuntimeException | Error failure) {
                restorationFailure = appendFailure(restorationFailure, failure);
            }
            try {
                actualTexture1Depth = restoreMatrixStack(
                    GL11.GL_TEXTURE, texture1Stack, textureStackDepth[1], textureMatrix[1],
                    OpenGlHelper.lightmapTexUnit, 3);
            } catch (RuntimeException | Error failure) {
                restorationFailure = appendFailure(restorationFailure, failure);
            }
            try {
                restoreTextureUnit(0, OpenGlHelper.defaultTexUnit);
            } catch (RuntimeException | Error failure) {
                restorationFailure = appendFailure(restorationFailure, failure);
            }
            try {
                restoreTextureUnit(1, OpenGlHelper.lightmapTexUnit);
            } catch (RuntimeException | Error failure) {
                restorationFailure = appendFailure(restorationFailure, failure);
            }
            try {
                restoreTextureCoordinates();
            } catch (RuntimeException | Error failure) {
                restorationFailure = appendFailure(restorationFailure, failure);
            }
            try {
                restoreOriginalActiveTexture();
            } catch (RuntimeException | Error failure) {
                restorationFailure = appendFailure(restorationFailure, failure);
            }
            try {
                setMatrixMode(matrixMode);
            } catch (RuntimeException | Error failure) {
                restorationFailure = appendFailure(restorationFailure, failure);
            }

            try {
                if (vboSupported) {
                    OpenGlHelper.glBindBuffer(OpenGlHelper.GL_ARRAY_BUFFER, arrayBuffer);
                }
            } catch (RuntimeException | Error failure) {
                restorationFailure = appendFailure(restorationFailure, failure);
            }
            try {
                bindVertexArray(vertexArray);
            } catch (RuntimeException | Error failure) {
                restorationFailure = appendFailure(restorationFailure, failure);
            }
            try {
                GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
                GlStateManager.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            } catch (RuntimeException | Error failure) {
                restorationFailure = appendFailure(restorationFailure, failure);
            }
            try {
                if (programSupported) {
                    OpenGlHelper.glUseProgram(program);
                }
            } catch (RuntimeException | Error failure) {
                restorationFailure = appendFailure(restorationFailure, failure);
            }

            if (actualModelViewDepth != modelViewStackDepth
                || actualProjectionDepth != projectionStackDepth
                || actualTexture0Depth != textureStackDepth[0]
                || actualTexture1Depth != textureStackDepth[1]) {
                restorationFailure = appendFailure(restorationFailure,
                    matrixImbalanceFailure(actualModelViewDepth, actualProjectionDepth,
                        actualTexture0Depth, actualTexture1Depth));
            }
            if (restorationFailure != null) {
                CirculationFlowNetworks.LOGGER.error(
                    "Failed to restore {} render state", lifecycle.passName(), restorationFailure);
                rethrowUnchecked(restorationFailure);
            }
        } finally {
            try {
                clearPassState();
            } finally {
                lifecycle.release(currentThread);
            }
        }
    }

    private void restoreVanillaLightmapTracker() {
        if (shouldDisableVanillaLightmapTracker(
            profile == Profile.HUB, hubLightmapEstablished, texture2dEnabled[1])) {
            Minecraft.getMinecraft().entityRenderer.disableLightmap();
        }
    }

    private void restoreStrongTextureState() {
        for (int index = 0; index < maxFixedFunctionTextureUnits; index++) {
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit + index);
            applyTextureTargetMask(strongTextureTargetMasks[index]);
            applyTexGenMask(strongTexGenMasks[index]);
        }
    }

    private void restoreOriginalActiveTexture() {
        syncActiveTexture(activeTexture);
    }

    private static Throwable appendFailure(Throwable primary, Throwable next) {
        if (primary == null) {
            return next;
        }
        primary.addSuppressed(next);
        return primary;
    }

    private static void rethrowUnchecked(Throwable failure) {
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        throw (Error) failure;
    }

    private int restoreMatrixStack(int mode, MatrixStackAccess stack, int expectedDepth, float[] matrix,
                                   int textureUnit, int stackId) {
        if (textureUnit >= 0) {
            syncActiveTexture(textureUnit);
        }
        setMatrixMode(mode);
        int actualDepth;
        try {
            actualDepth = restoreStackDepth(expectedDepth, stack);
        } catch (IllegalStateException failure) {
            throw matrixRecoveryFailure(stackId, failure.getMessage());
        }
        loadMatrix(matrix);
        return actualDepth;
    }

    private void restoreTextureUnit(int index, int textureUnit) {
        syncActiveTexture(textureUnit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureBinding[index]);
        GlStateManager.bindTexture(textureBinding[index]);
        syncTexture2d(texture2dEnabled[index]);
        textureEnvironment.restore(index);
    }

    private void restoreClientArrays() {
        Throwable restorationFailure = null;
        try {
            bindVertexArray(vertexArray);
        } catch (RuntimeException | Error failure) {
            restorationFailure = appendFailure(restorationFailure, failure);
        }
        if (clientAttributesPushed) {
            try {
                GL11.glPopClientAttrib();
                clientAttributesPushed = false;
            } catch (RuntimeException | Error failure) {
                restorationFailure = appendFailure(restorationFailure, failure);
            }
        }
        try {
            if (vboSupported) {
                OpenGlHelper.glBindBuffer(OpenGlHelper.GL_ARRAY_BUFFER, arrayBuffer);
            }
        } catch (RuntimeException | Error failure) {
            restorationFailure = appendFailure(restorationFailure, failure);
        }
        try {
            bindVertexArray(vertexArray);
        } catch (RuntimeException | Error failure) {
            restorationFailure = appendFailure(restorationFailure, failure);
        }
        if (restorationFailure != null) {
            rethrowUnchecked(restorationFailure);
        }
    }

    private void abortCapture() {
        Throwable restorationFailure = null;
        if (activeTextureCaptured) {
            try {
                int activeTextureIndex = activeTexture - OpenGlHelper.defaultTexUnit;
                if (activeTextureIndex >= 0 && activeTextureIndex < MINECRAFT_TEXTURE_UNIT_COUNT) {
                    restoreOriginalActiveTexture();
                } else {
                    OpenGlHelper.setActiveTexture(activeTexture);
                }
            } catch (RuntimeException | Error failure) {
                restorationFailure = appendFailure(restorationFailure, failure);
            }
        }
        if (clientAttributesPushed) {
            try {
                GL11.glPopClientAttrib();
                clientAttributesPushed = false;
            } catch (RuntimeException | Error failure) {
                restorationFailure = appendFailure(restorationFailure, failure);
            }
        }
        if (restorationFailure != null) {
            rethrowUnchecked(restorationFailure);
        }
    }

    private void clearPassState() {
        capabilities = null;
        clientAttributesPushed = false;
        activeTextureCaptured = false;
        profile = null;
        hubLightmapEstablished = false;
        maxFixedFunctionTextureUnits = 0;
        unexpectedTextureUnit = -1;
        unexpectedTextureTargets = 0;
        unexpectedTexGenUnit = -1;
        unexpectedTexGenCoordinates = 0;
    }

    private void restoreTextureCoordinates() {
        restoreTextureCoordinates(OpenGlHelper.defaultTexUnit, texture0Coordinates);
        restoreTextureCoordinates(OpenGlHelper.lightmapTexUnit, lightmapCoordinates);
        OpenGlHelper.lastBrightnessX = lightmapCoordinates.x;
        OpenGlHelper.lastBrightnessY = lightmapCoordinates.y;
    }

    private void restoreTextureCoordinates(int textureUnit, TextureCoordinates coordinates) {
        if (capabilities.OpenGL13) {
            GL13.glMultiTexCoord4f(textureUnit, coordinates.x, coordinates.y, coordinates.z, coordinates.w);
        } else {
            ARBMultitexture.glMultiTexCoord4fARB(
                textureUnit, coordinates.x, coordinates.y, coordinates.z, coordinates.w);
        }
    }

    private IllegalStateException matrixRecoveryFailure(int stackId, String reason) {
        return new IllegalStateException(lifecycle.passName() + " " + stackName(stackId)
            + " matrix stack " + reason + " while recovering");
    }

    private IllegalStateException matrixImbalanceFailure(int actualModelViewDepth, int actualProjectionDepth,
                                                          int actualTexture0Depth, int actualTexture1Depth) {
        StringBuilder message = new StringBuilder(lifecycle.passName()).append(" matrix stack imbalance:");
        appendMatrixImbalance(message, " model-view", modelViewStackDepth, actualModelViewDepth);
        appendMatrixImbalance(message, " projection", projectionStackDepth, actualProjectionDepth);
        appendMatrixImbalance(message, " texture unit 0", textureStackDepth[0], actualTexture0Depth);
        appendMatrixImbalance(message, " texture unit 1", textureStackDepth[1], actualTexture1Depth);
        return new IllegalStateException(message.toString());
    }

    private static void appendMatrixImbalance(StringBuilder message, String stackName, int expected, int actual) {
        if (expected != actual) {
            message.append(stackName).append(" expected ").append(expected).append(" but was ").append(actual).append(';');
        }
    }

    private static String stackName(int stackId) {
        return switch (stackId) {
            case 0 -> "model-view";
            case 1 -> "projection";
            case 2 -> "texture unit 0";
            case 3 -> "texture unit 1";
            default -> throw new IllegalArgumentException("Unknown matrix stack " + stackId);
        };
    }

    static int restoreStackDepth(int expectedDepth, MatrixStackAccess stack) {
        int actualDepth = stack.depth();
        int currentDepth = actualDepth;
        while (currentDepth > expectedDepth) {
            stack.pop();
            int nextDepth = stack.depth();
            if (nextDepth >= currentDepth) {
                throw new IllegalStateException("did not shrink while recovering");
            }
            currentDepth = nextDepth;
        }
        while (currentDepth < expectedDepth) {
            stack.push();
            int nextDepth = stack.depth();
            if (nextDepth <= currentDepth) {
                throw new IllegalStateException("did not grow while recovering");
            }
            currentDepth = nextDepth;
        }
        return actualDepth;
    }

    private void readLightingState() {
        readLight(GL11.GL_LIGHT0, GL11.GL_POSITION, lightState[0]);
        readLight(GL11.GL_LIGHT0, GL11.GL_AMBIENT, lightState[1]);
        readLight(GL11.GL_LIGHT0, GL11.GL_DIFFUSE, lightState[2]);
        readLight(GL11.GL_LIGHT0, GL11.GL_SPECULAR, lightState[3]);
        readLight(GL11.GL_LIGHT1, GL11.GL_POSITION, lightState[4]);
        readLight(GL11.GL_LIGHT1, GL11.GL_AMBIENT, lightState[5]);
        readLight(GL11.GL_LIGHT1, GL11.GL_DIFFUSE, lightState[6]);
        readLight(GL11.GL_LIGHT1, GL11.GL_SPECULAR, lightState[7]);
        readFloat4(GL11.GL_LIGHT_MODEL_AMBIENT, lightState[8]);
    }

    private void restoreLightingState() {
        setMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GlStateManager.loadIdentity();
        writeLight(GL11.GL_LIGHT0, GL11.GL_POSITION, lightState[0]);
        writeLight(GL11.GL_LIGHT0, GL11.GL_AMBIENT, lightState[1]);
        writeLight(GL11.GL_LIGHT0, GL11.GL_DIFFUSE, lightState[2]);
        writeLight(GL11.GL_LIGHT0, GL11.GL_SPECULAR, lightState[3]);
        writeLight(GL11.GL_LIGHT1, GL11.GL_POSITION, lightState[4]);
        writeLight(GL11.GL_LIGHT1, GL11.GL_AMBIENT, lightState[5]);
        writeLight(GL11.GL_LIGHT1, GL11.GL_DIFFUSE, lightState[6]);
        writeLight(GL11.GL_LIGHT1, GL11.GL_SPECULAR, lightState[7]);
        loadMatrix(modelViewMatrix);
        fillFloat4(lightState[8]);
        GL11.glLightModel(GL11.GL_LIGHT_MODEL_AMBIENT, float4);
        GlStateManager.glLightModel(GL11.GL_LIGHT_MODEL_AMBIENT, float4);
        GL11.glColorMaterial(colorMaterialFace, colorMaterialParameter);
        GlStateManager.colorMaterial(colorMaterialFace, colorMaterialParameter);
        syncLighting(lightingEnabled, light0Enabled, light1Enabled, colorMaterialEnabled);
    }

    private void readLight(int light, int property, float[] target) {
        float4.clear();
        GL11.glGetLight(light, property, float4);
        float4.get(target);
    }

    private void writeLight(int light, int property, float[] value) {
        fillFloat4(value);
        GL11.glLight(light, property, float4);
        GlStateManager.glLight(light, property, float4);
    }

    private void readMatrix(int name, float[] target) {
        float16.clear();
        GL11.glGetFloat(name, float16);
        float16.get(target);
    }

    private void loadMatrix(float[] matrix) {
        float16.clear();
        float16.put(matrix).flip();
        GL11.glLoadMatrix(float16);
        GlStateManager.loadIdentity();
        float16.rewind();
        GlStateManager.multMatrix(float16);
    }

    private void readFloat4(int name, float[] target) {
        float4.clear();
        GL11.glGetFloat(name, float4);
        float4.get(target);
    }

    private void fillFloat4(float[] values) {
        float4.clear();
        float4.put(values).flip();
    }

    private void readInt4(int name, int[] target) {
        int4.clear();
        GL11.glGetInteger(name, int4);
        int4.get(target);
    }

    private void readBoolean4(boolean[] target) {
        byte4.clear();
        GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, byte4);
        for (int index = 0; index < target.length; index++) {
            target[index] = byte4.get(index) != 0;
        }
    }

    private static void setMatrixMode(int mode) {
        GL11.glMatrixMode(mode);
        GlStateManager.matrixMode(mode);
    }

    private static void syncActiveTexture(int texture) {
        OpenGlHelper.setActiveTexture(texture);
        GlStateManager.setActiveTexture(texture);
    }

    private static void syncTexture2d(boolean enabled) {
        setCapability(GL11.GL_TEXTURE_2D, enabled);
        if (enabled) {
            GlStateManager.enableTexture2D();
        } else {
            GlStateManager.disableTexture2D();
        }
    }

    private static void syncAlpha(boolean enabled, int function, float reference) {
        setCapability(GL11.GL_ALPHA_TEST, enabled);
        if (enabled) {
            GlStateManager.enableAlpha();
        } else {
            GlStateManager.disableAlpha();
        }
        GL11.glAlphaFunc(function, reference);
        GlStateManager.alphaFunc(function, reference);
    }

    private static void syncBlend(boolean enabled, int equationRgb, int equationAlpha,
                                  int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        setCapability(GL11.GL_BLEND, enabled);
        if (enabled) {
            GlStateManager.enableBlend();
        } else {
            GlStateManager.disableBlend();
        }
        GL20.glBlendEquationSeparate(equationRgb, equationAlpha);
        GlStateManager.glBlendEquation(equationRgb);
        if (equationAlpha != equationRgb) {
            GL20.glBlendEquationSeparate(equationRgb, equationAlpha);
        }
        OpenGlHelper.glBlendFunc(srcRgb, dstRgb, srcAlpha, dstAlpha);
        GlStateManager.tryBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
    }

    private static void syncDepth(boolean enabled, int function, boolean mask) {
        setCapability(GL11.GL_DEPTH_TEST, enabled);
        if (enabled) {
            GlStateManager.enableDepth();
        } else {
            GlStateManager.disableDepth();
        }
        GL11.glDepthFunc(function);
        GlStateManager.depthFunc(function);
        GL11.glDepthMask(mask);
        GlStateManager.depthMask(mask);
    }

    private static void syncCull(boolean enabled, int face) {
        setCapability(GL11.GL_CULL_FACE, enabled);
        if (enabled) {
            GlStateManager.enableCull();
        } else {
            GlStateManager.disableCull();
        }
        GL11.glCullFace(face);
        if (face == GL11.GL_FRONT) {
            GlStateManager.cullFace(GlStateManager.CullFace.FRONT);
        } else if (face == GL11.GL_BACK) {
            GlStateManager.cullFace(GlStateManager.CullFace.BACK);
        } else if (face == GL11.GL_FRONT_AND_BACK) {
            GlStateManager.cullFace(GlStateManager.CullFace.FRONT_AND_BACK);
        }
    }

    private static void syncLighting(boolean lighting, boolean light0, boolean light1, boolean colorMaterial) {
        setCapability(GL11.GL_LIGHTING, lighting);
        setCapability(GL11.GL_LIGHT0, light0);
        setCapability(GL11.GL_LIGHT1, light1);
        setCapability(GL11.GL_COLOR_MATERIAL, colorMaterial);
        if (lighting) GlStateManager.enableLighting(); else GlStateManager.disableLighting();
        if (light0) GlStateManager.enableLight(0); else GlStateManager.disableLight(0);
        if (light1) GlStateManager.enableLight(1); else GlStateManager.disableLight(1);
        if (colorMaterial) GlStateManager.enableColorMaterial(); else GlStateManager.disableColorMaterial();
    }

    private static void syncFog(boolean enabled) {
        setCapability(GL11.GL_FOG, enabled);
        if (enabled) GlStateManager.enableFog(); else GlStateManager.disableFog();
    }

    private static void syncColorLogic(boolean enabled, int operation) {
        setCapability(GL_COLOR_LOGIC_OP, enabled);
        if (enabled) GlStateManager.enableColorLogic(); else GlStateManager.disableColorLogic();
        GL11.glLogicOp(operation);
        GlStateManager.colorLogicOp(operation);
    }

    private static void syncRescaleNormal(boolean enabled) {
        setCapability(GL12_RESCALE_NORMAL, enabled);
        if (enabled) GlStateManager.enableRescaleNormal(); else GlStateManager.disableRescaleNormal();
    }

    private static void syncShadeModel(int model) {
        GL11.glShadeModel(model);
        GlStateManager.shadeModel(model);
    }

    private static void syncColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        GL11.glColorMask(red, green, blue, alpha);
        GlStateManager.colorMask(red, green, blue, alpha);
    }

    private static void syncColor(float red, float green, float blue, float alpha) {
        GL11.glColor4f(red, green, blue, alpha);
        GlStateManager.color(red, green, blue, alpha);
    }

    private static void syncScissor(boolean enabled, int x, int y, int width, int height) {
        setCapability(GL11.GL_SCISSOR_TEST, enabled);
        GL11.glScissor(x, y, width, height);
    }

    private void bindVertexArray(int vertexArray) {
        if (!vaoSupported) {
            return;
        }
        if (capabilities.OpenGL30) {
            GL30.glBindVertexArray(vertexArray);
        } else {
            ARBVertexArrayObject.glBindVertexArray(vertexArray);
        }
    }

    private static void setCapability(int capability, boolean enabled) {
        if (enabled) {
            GL11.glEnable(capability);
        } else {
            GL11.glDisable(capability);
        }
    }

    private static ByteBuffer directByteBuffer(int elements) {
        return ByteBuffer.allocateDirect(elements).order(ByteOrder.nativeOrder());
    }

    private static FloatBuffer directFloatBuffer(int elements) {
        return directByteBuffer(elements * Float.BYTES).asFloatBuffer();
    }

    private static IntBuffer directIntBuffer() {
        return directByteBuffer(16 * Integer.BYTES).asIntBuffer();
    }

    static final class TextureEnvironmentState {

        private final int[][] values = new int[2][TEXTURE_ENV_FIELD_COUNT];
        private final float[][] colors = new float[2][4];
        private final FloatBuffer colorBuffer = directFloatBuffer(4);

        void capture(int textureIndex, FloatBuffer colorBuffer) {
            for (int field = 0; field < TEXTURE_ENV_FIELD_COUNT; field++) {
                values[textureIndex][field] = GL11.glGetTexEnvi(GL11.GL_TEXTURE_ENV, TEXTURE_ENV_INT_NAMES[field]);
            }
            colorBuffer.clear();
            GL11.glGetTexEnv(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_COLOR, colorBuffer);
            colorBuffer.get(colors[textureIndex]);
        }

        void restore(int textureIndex) {
            for (int field = 0; field < TEXTURE_ENV_FIELD_COUNT; field++) {
                int name = TEXTURE_ENV_INT_NAMES[field];
                int value = values[textureIndex][field];
                GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, name, value);
                GlStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, name, value);
            }
            colorBuffer.clear();
            colorBuffer.put(colors[textureIndex]).flip();
            GL11.glTexEnv(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_COLOR, colorBuffer);
        }
    }

    static final class TextureCoordinates {
        private float x;
        private float y;
        private float z;
        private float w;

        void capture(FloatBuffer buffer) {
            buffer.clear();
            GL11.glGetFloat(GL11.GL_CURRENT_TEXTURE_COORDS, buffer);
            x = buffer.get(0);
            y = buffer.get(1);
            z = buffer.get(2);
            w = buffer.get(3);
        }
    }

    static final class BlendEquations {

        private int rgb;
        private int alpha;

        void set(int rgb, int alpha) {
            this.rgb = rgb;
            this.alpha = alpha;
        }

        int rgb() {
            return rgb;
        }

        int alpha() {
            return alpha;
        }
    }

    static class MatrixStackAccess {

        private final int depthName;
        private final boolean simulated;
        private int simulatedDepth;
        private final boolean adjustDepth = true;

        private MatrixStackAccess(int depthName, boolean simulated, int simulatedDepth) {
            this.depthName = depthName;
            this.simulated = simulated;
            this.simulatedDepth = simulatedDepth;
        }

        static MatrixStackAccess openGl(int depthName) {
            return new MatrixStackAccess(depthName, false, 0);
        }

        int depth() {
            return simulated ? simulatedDepth : GL11.glGetInteger(depthName);
        }

        void push() {
            if (simulated) {
                if (adjustDepth) simulatedDepth++;
            } else {
                GlStateManager.pushMatrix();
            }
        }

        void pop() {
            if (simulated) {
                if (adjustDepth) simulatedDepth--;
            } else {
                GlStateManager.popMatrix();
            }
        }
    }

    static final class Lifecycle {

        private boolean active;
        private Thread ownerThread;
        private String passName;

        void acquire(String requestedPassName, Thread requestedOwnerThread) {
            if (active) {
                throw new IllegalStateException(passName + " render state guard is already active; nested pass "
                    + requestedPassName + " is forbidden");
            }
            active = true;
            ownerThread = requestedOwnerThread;
            passName = requestedPassName;
        }

        void requireClose(Thread closingThread) {
            if (!active) {
                throw new IllegalStateException(passName + " render state guard is not active; repeated close is forbidden");
            }
            if (ownerThread != closingThread) {
                throw new IllegalStateException(passName + " render state guard cannot be closed from thread "
                    + closingThread.getName());
            }
        }

        void release(Thread releasingThread) {
            requireClose(releasingThread);
            active = false;
            ownerThread = null;
        }

        String passName() {
            return passName;
        }
    }
}
