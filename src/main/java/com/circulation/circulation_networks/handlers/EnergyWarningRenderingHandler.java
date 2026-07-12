package com.circulation.circulation_networks.handlers;

import com.circulation.circulation_networks.client.render.ClientAnimationTicker;
//? if <1.20 {
import com.circulation.circulation_networks.client.render.LegacyWorldRenderStateGuard;
//?}
import com.circulation.circulation_networks.gui.component.base.AtlasRegion;
import com.circulation.circulation_networks.gui.component.base.ComponentAtlas;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
//~ mc_imports
import net.minecraft.util.math.BlockPos;
//? if <1.20 {
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
//?} else if <1.21 {
/*import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
*///?} else {
/*import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
*///?}

//? if <1.20 {
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.GL11;
//?} else {
/*import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
*///?}
//? if <1.20 {
//?} else if <1.21 {
/*import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
*///?} else {
/*import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
*///?}

import java.util.Objects;

//~ if >=1.20 '@SideOnly(Side' -> '@OnlyIn(Dist' {
@SideOnly(Side.CLIENT)
//~}
public final class EnergyWarningRenderingHandler {

    public static final EnergyWarningRenderingHandler INSTANCE = new EnergyWarningRenderingHandler();
    private static final double MAX_RENDER_DISTANCE_SQ = 48.0D * 48.0D;
    private static final float ICON_SIZE = 0.375F;
    private static final double ICON_HEIGHT = 1.25D;
    private static final String WARNING_SPRITE = "warning";
    private static final long SNAPSHOT_TTL_TICKS = 200L;
    private final Object2ObjectMap<String, Long2LongMap> warnings = new Object2ObjectOpenHashMap<>();
    private final Object2LongOpenHashMap<String> revisions = new Object2LongOpenHashMap<>();
    private final Object2LongOpenHashMap<String> snapshotTicks = new Object2LongOpenHashMap<>();
    private long sessionGeneration = Long.MIN_VALUE;

    private EnergyWarningRenderingHandler() {
        revisions.defaultReturnValue(Long.MIN_VALUE);
        snapshotTicks.defaultReturnValue(Long.MIN_VALUE);
    }

    private static AtlasRegion getWarningRegion() {
        ComponentAtlas atlas = ComponentAtlas.INSTANCE;
        atlas.awaitReady();
        return atlas.getRegion(WARNING_SPRITE);
    }

    /** Atomically applies one complete server snapshot while rejecting stale sessions and revisions. */
    public void applySnapshot(String dimensionKey, long session, long revision, LongCollection positions) {
        Objects.requireNonNull(dimensionKey, "dimensionKey");
        Objects.requireNonNull(positions, "positions");
        if (session < sessionGeneration) {
            return;
        }
        if (session > sessionGeneration) {
            sessionGeneration = session;
            warnings.clear();
            revisions.clear();
            snapshotTicks.clear();
        } else if (revision <= revisions.getLong(dimensionKey)) {
            return;
        }
        long clientTick = ClientAnimationTicker.ticks();
        if (positions.isEmpty()) {
            warnings.remove(dimensionKey);
        } else {
            Long2LongMap updatedWarnings = new Long2LongOpenHashMap(positions.size());
            for (long posLong : positions) {
                updatedWarnings.put(posLong, clientTick);
            }
            warnings.put(dimensionKey, updatedWarnings);
        }
        revisions.put(dimensionKey, revision);
        snapshotTicks.put(dimensionKey, clientTick);
    }

    public void clear() {
        warnings.clear();
        revisions.clear();
        snapshotTicks.clear();
        sessionGeneration = Long.MIN_VALUE;
    }

    /** Returns whether any dimension currently owns a live warning candidate. */
    public boolean hasWarningCandidates() {
        expireStaleSnapshots();
        return !warnings.isEmpty();
    }

    /** Returns whether the current client warning state contains a position in a dimension. */
    boolean hasWarning(String dimensionKey, long posLong) {
        expireStaleSnapshots();
        Long2LongMap dimWarnings = warnings.get(dimensionKey);
        return dimWarnings != null && dimWarnings.containsKey(posLong);
    }

    void expireStaleSnapshots() {
        long clientTick = ClientAnimationTicker.ticks();
        for (var iterator = snapshotTicks.object2LongEntrySet().iterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            long snapshotTick = entry.getLongValue();
            if (snapshotTick != Long.MIN_VALUE && clientTick - snapshotTick > SNAPSHOT_TTL_TICKS) {
                warnings.remove(entry.getKey());
                iterator.remove();
            }
        }
    }

    //? if <1.20 {
    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        expireStaleSnapshots();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) {
            return;
        }
        Long2LongMap dimWarnings = warnings.get("legacy:" + mc.player.dimension);
        if (dimWarnings == null || dimWarnings.isEmpty()) {
            return;
        }

        double cameraX = mc.player.lastTickPosX + (mc.player.posX - mc.player.lastTickPosX) * event.getPartialTicks();
        double cameraY = mc.player.lastTickPosY + (mc.player.posY - mc.player.lastTickPosY) * event.getPartialTicks();
        double cameraZ = mc.player.lastTickPosZ + (mc.player.posZ - mc.player.lastTickPosZ) * event.getPartialTicks();
        AtlasRegion warningRegion = getWarningRegion();
        if (warningRegion == null) {
            return;
        }
        try (LegacyWorldRenderStateGuard ignored = LegacyWorldRenderStateGuard.openHudPass("energy warning")) {
            for (var entry : dimWarnings.long2LongEntrySet()) {
                BlockPos pos = BlockPos.fromLong(entry.getLongKey());
                if (mc.player.getDistanceSq(pos.getX() + 0.5D, pos.getY() + ICON_HEIGHT, pos.getZ() + 0.5D) > MAX_RENDER_DISTANCE_SQ) {
                    continue;
                }
                renderWarning(warningRegion, cameraX, cameraY, cameraZ, pos);
            }
        }
    }
    //?} else {
    /*@SubscribeEvent
    public void onRenderWorldLast(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        expireStaleSnapshots();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        Long2LongMap dimWarnings = warnings.get(mc.level.dimension().location().toString());
        if (dimWarnings == null || dimWarnings.isEmpty()) {
            return;
        }

        var cameraPos = event.getCamera().getPosition();
        double cameraX = cameraPos.x;
        double cameraY = cameraPos.y;
        double cameraZ = cameraPos.z;
        AtlasRegion warningRegion = getWarningRegion();
        if (warningRegion == null) {
            return;
        }
        //? if <1.21 {
        PoseStack mvStack = RenderSystem.getModelViewStack();
        mvStack.pushPose();
        mvStack.last().pose().set(event.getPoseStack().last().pose());
        mvStack.last().normal().set(event.getPoseStack().last().normal());
        mvStack.translate(-cameraX, -cameraY, -cameraZ);
        //?} else {
        /^var mvStack = RenderSystem.getModelViewStack();
        mvStack.pushMatrix();
        mvStack.set(event.getModelViewMatrix());
        mvStack.translate((float) -cameraX, (float) -cameraY, (float) -cameraZ);
        ^///?}
        RenderSystem.applyModelViewMatrix();

        for (var entry : dimWarnings.long2LongEntrySet()) {
            BlockPos pos = BlockPos.of(entry.getLongKey());
            if (distanceSqToPlayer(mc, pos) > MAX_RENDER_DISTANCE_SQ) {
                continue;
            }
            renderWarning(warningRegion, pos);
        }

        //? if <1.21 {
        mvStack.popPose();
        //?} else {
        /^mvStack.popMatrix();
        ^///?}
        RenderSystem.applyModelViewMatrix();
    }
    *///?}

    //? if <1.20 {
    private void renderWarning(AtlasRegion warningRegion, double cameraX, double cameraY, double cameraZ, BlockPos pos) {
        Minecraft mc = Minecraft.getMinecraft();
        ComponentAtlas.INSTANCE.bind();

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(pos.getX() + 0.5D - cameraX, pos.getY() + ICON_HEIGHT - cameraY, pos.getZ() + 0.5D - cameraZ);
            GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(mc.getRenderManager().playerViewX, 1.0F, 0.0F, 0.0F);
            GlStateManager.scale(-ICON_SIZE, -ICON_SIZE, ICON_SIZE);
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            GlStateManager.disableLighting();
            GlStateManager.disableCull();
            GlStateManager.disableDepth();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

            Tessellator tess = Tessellator.getInstance();
            BufferBuilder buffer = tess.getBuffer();
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
            buffer.pos(-1.0D, 1.0D, 0.0D).tex(warningRegion.u0(), warningRegion.v1()).endVertex();
            buffer.pos(1.0D, 1.0D, 0.0D).tex(warningRegion.u1(), warningRegion.v1()).endVertex();
            buffer.pos(1.0D, -1.0D, 0.0D).tex(warningRegion.u1(), warningRegion.v0()).endVertex();
            buffer.pos(-1.0D, -1.0D, 0.0D).tex(warningRegion.u0(), warningRegion.v0()).endVertex();
            tess.draw();
        } finally {
            GlStateManager.popMatrix();
        }
    }
    //?} else {
    /*private static double distanceSqToPlayer(Minecraft mc, BlockPos pos) {
        double dx = mc.player.getX() - (pos.getX() + 0.5D);
        double dy = mc.player.getY() - (pos.getY() + ICON_HEIGHT);
        double dz = mc.player.getZ() - (pos.getZ() + 0.5D);
        return dx * dx + dy * dy + dz * dz;
    }

    private void renderWarning(AtlasRegion warningRegion, BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        //? if <1.21 {
        PoseStack mvStack = RenderSystem.getModelViewStack();
        mvStack.pushPose();
        mvStack.translate(pos.getX() + 0.5D, pos.getY() + ICON_HEIGHT, pos.getZ() + 0.5D);
        mvStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        mvStack.scale(-ICON_SIZE, -ICON_SIZE, ICON_SIZE);
        //?} else {
        /^var mvStack = RenderSystem.getModelViewStack();
        mvStack.pushMatrix();
        mvStack.translate(pos.getX() + 0.5F, (float) (pos.getY() + ICON_HEIGHT), pos.getZ() + 0.5F);
        mvStack.rotate(mc.getEntityRenderDispatcher().cameraOrientation());
        mvStack.scale(-ICON_SIZE, -ICON_SIZE, ICON_SIZE);
        ^///?}
        RenderSystem.applyModelViewMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        ComponentAtlas.INSTANCE.bind();

        Tesselator tess = Tesselator.getInstance();
        //~ if >=1.21 'BufferBuilder buffer = tess.getBuilder();' -> 'BufferBuilder buffer = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);' {
        BufferBuilder buffer = tess.getBuilder();
        //~}
        //? if <1.21 {
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        //?}
        //~ if >=1.21 '.vertex(' -> '.addVertex(' {
        //~ if >=1.21 '.uv(' -> '.setUv(' {
        //~ if >=1.21 ').endVertex();' -> ');' {
        buffer.vertex(-1.0F, 1.0F, 0.0F).uv(warningRegion.u0(), warningRegion.v1()).endVertex();
        buffer.vertex(1.0F, 1.0F, 0.0F).uv(warningRegion.u1(), warningRegion.v1()).endVertex();
        buffer.vertex(1.0F, -1.0F, 0.0F).uv(warningRegion.u1(), warningRegion.v0()).endVertex();
        buffer.vertex(-1.0F, -1.0F, 0.0F).uv(warningRegion.u0(), warningRegion.v0()).endVertex();
        //~}
        //~}
        //~}
        //~ if >=1.21 'tess.end();' -> 'com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(buffer.buildOrThrow());' {
        tess.end();
        //~}

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        //? if <1.21 {
        mvStack.popPose();
        //?} else {
        /^mvStack.popMatrix();
        ^///?}
        RenderSystem.applyModelViewMatrix();
    }
    *///?}
}
