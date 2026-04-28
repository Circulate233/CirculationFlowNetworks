package com.circulation.circulation_networks.handlers;

import com.circulation.circulation_networks.client.render.ClientAnimationTicker;
import com.circulation.circulation_networks.gui.component.base.AtlasRegion;
import com.circulation.circulation_networks.gui.component.base.ComponentAtlas;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongCollection;
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
import net.minecraftforge.fml.common.gameevent.TickEvent;
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

//~ if >=1.20 '@SideOnly(Side' -> '@OnlyIn(Dist' {
@SideOnly(Side.CLIENT)
//~}
public final class EnergyWarningRenderingHandler {

    public static final EnergyWarningRenderingHandler INSTANCE = new EnergyWarningRenderingHandler();
    private static final long WARNING_TTL_TICKS = 40L;
    private static final double MAX_RENDER_DISTANCE_SQ = 48.0D * 48.0D;
    private static final float ICON_SIZE = 0.375F;
    private static final double ICON_HEIGHT = 1.25D;
    private static final String WARNING_SPRITE = "warning";
    private final Int2ObjectMap<Long2LongMap> warnings = new Int2ObjectOpenHashMap<>();

    private EnergyWarningRenderingHandler() {
    }

    private static AtlasRegion getWarningRegion() {
        ComponentAtlas atlas = ComponentAtlas.INSTANCE;
        atlas.awaitReady();
        return atlas.getRegion(WARNING_SPRITE);
    }

    public void refreshWarnings(int dimId, LongCollection positions) {
        if (positions == null || positions.isEmpty()) {
            return;
        }
        Long2LongMap dimWarnings = warnings.get(dimId);
        if (dimWarnings == null) {
            dimWarnings = new Long2LongOpenHashMap();
            warnings.put(dimId, dimWarnings);
        }
        long clientTick = ClientAnimationTicker.ticks();
        for (long posLong : positions) {
            dimWarnings.put(posLong, clientTick);
        }
    }

    public void clear() {
        warnings.clear();
    }

    @SubscribeEvent
    //~ if >=1.21 'TickEvent.ClientTickEvent' -> 'ClientTickEvent.Pre' {
    public void onClientTick(TickEvent.ClientTickEvent event) {
    //~}
        //? if <1.21
        if (event.phase != TickEvent.Phase.START) return;
        cleanupExpired();
    }

    //? if <1.20 {
    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) {
            return;
        }
        Long2LongMap dimWarnings = warnings.get(mc.player.dimension);
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
        long clientTick = ClientAnimationTicker.ticks();

        for (var entry : dimWarnings.long2LongEntrySet()) {
            if (clientTick - entry.getLongValue() > WARNING_TTL_TICKS) {
                continue;
            }
            BlockPos pos = BlockPos.fromLong(entry.getLongKey());
            if (mc.player.getDistanceSq(pos.getX() + 0.5D, pos.getY() + ICON_HEIGHT, pos.getZ() + 0.5D) > MAX_RENDER_DISTANCE_SQ) {
                continue;
            }
            renderWarning(warningRegion, cameraX, cameraY, cameraZ, pos);
        }
    }
    //?} else {
    /*@SubscribeEvent
    public void onRenderWorldLast(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        Long2LongMap dimWarnings = warnings.get(mc.level.dimension().location().hashCode());
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
        long clientTick = ClientAnimationTicker.ticks();

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
            if (clientTick - entry.getLongValue() > WARNING_TTL_TICKS) {
                continue;
            }
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

    private void cleanupExpired() {
        long clientTick = ClientAnimationTicker.ticks();
        for (var dimIterator = warnings.int2ObjectEntrySet().iterator(); dimIterator.hasNext(); ) {
            var dimEntry = dimIterator.next();
            Long2LongMap dimWarnings = dimEntry.getValue();
            dimWarnings.long2LongEntrySet().removeIf(warningEntry -> clientTick - warningEntry.getLongValue() > WARNING_TTL_TICKS);
            if (dimWarnings.isEmpty()) {
                dimIterator.remove();
            }
        }
    }

    //? if <1.20 {
    private void renderWarning(AtlasRegion warningRegion, double cameraX, double cameraY, double cameraZ, BlockPos pos) {
        Minecraft mc = Minecraft.getMinecraft();
        ComponentAtlas.INSTANCE.bind();

        GlStateManager.pushMatrix();
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

        GlStateManager.enableDepth();
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
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
