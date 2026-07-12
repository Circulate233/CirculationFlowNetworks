package com.circulation.circulation_networks.handlers;

import com.circulation.circulation_networks.api.ICirculationShielderBlockEntity;
import com.circulation.circulation_networks.utils.AnimationUtils;
import com.circulation.circulation_networks.utils.RenderingUtils;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import net.minecraft.client.Minecraft;
//? if <1.20 {
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
//?} else if <1.21 {
/*import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
*///?} else {
/*import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
*///?}
//? if <1.20 {
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
//?} else {
/*import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
*///?}
//? if >=1.20 {
/*//~ neo_imports
import net.minecraftforge.client.event.RenderLevelStageEvent;
*///?}
//? if >=1.20 <1.21 {
/*import net.minecraftforge.event.TickEvent;
*///?}
//? if >=1.21 {
/*import net.neoforged.neoforge.client.event.ClientTickEvent;
*///?}

import java.util.Map;
import java.util.WeakHashMap;

//~ if >=1.20 '@SideOnly(Side.CLIENT)' -> '@OnlyIn(Dist.CLIENT)' {
@SideOnly(Side.CLIENT)
//~}
public final class CirculationShielderRenderingHandler {

    public static final CirculationShielderRenderingHandler INSTANCE = new CirculationShielderRenderingHandler();

    private static final float ORANGE_R = 1.0f;
    private static final float ORANGE_G = 0.647f;
    private static final float ORANGE_B = 0.0f;
    private static final float ALPHA = 0.5f;
    private static final float RANGE_EXPANSION = 0.01f;
    private static final float ANIMATION_DURATION = 2.0f;

    private final ReferenceSet<ICirculationShielderBlockEntity> clientShielders = new ReferenceOpenHashSet<>();
    private final Map<ICirculationShielderBlockEntity, Float> animProgress = new WeakHashMap<>();
    private final Map<ICirculationShielderBlockEntity, Float> lastAnimProgress = new WeakHashMap<>();
    //? if >=1.20 {
    /*private PoseStack currentWorldPoseStack;
    *///?}
    //? if >=1.21 {
    /*private org.joml.Matrix4f cachedEventViewMatrix;
    *///?}

    public void clear() {
        clientShielders.clear();
        animProgress.clear();
        lastAnimProgress.clear();
    }

    public void addShielder(ICirculationShielderBlockEntity shielder) {
        clientShielders.add(shielder);
    }

    public void removeShielder(ICirculationShielderBlockEntity shielder) {
        clientShielders.remove(shielder);
    }

    /** Returns whether a client-side shielder can currently request range rendering. */
    public boolean hasClientShielders() {
        return !clientShielders.isEmpty();
    }

    @SubscribeEvent
        //? if <1.21 {
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        //?} else {
        /*public void onClientTick(ClientTickEvent.Pre event) {
         *///?}

        lastAnimProgress.putAll(animProgress);
        animProgress.replaceAll((tile, progress) -> {
            if (tile.isShowingRange()) {
                return AnimationUtils.advanceTowardsOne(progress, 1.0f / (ANIMATION_DURATION * 20.0f));
            } else {
                return 0.0f;
            }
        });
    }

    private void renderShielderRange(ICirculationShielderBlockEntity shielder, double playerX, double playerY, double playerZ, float partialTicks) {
        //? if <1.20 {
        GlStateManager.pushMatrix();
        GlStateManager.translate(-playerX, -playerY, -playerZ);

        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(false);
        //?} else if <1.21 {
        /*PoseStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();
        if (currentWorldPoseStack != null) {
            modelViewStack.last().pose().set(currentWorldPoseStack.last().pose());
            modelViewStack.last().normal().set(currentWorldPoseStack.last().normal());
        }
        modelViewStack.translate(-playerX, -playerY, -playerZ);
        RenderSystem.applyModelViewMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        *///?} else {
        /*var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.set(cachedEventViewMatrix);

        modelViewStack.translate((float) -playerX, (float) -playerY, (float) -playerZ);
        RenderSystem.applyModelViewMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        *///?}

        try {
            int scope = shielder.getScope();
            double x = shielder.getBEPos().getX();
            double y = shielder.getBEPos().getY();
            double z = shielder.getBEPos().getZ();

            float progress = animProgress.getOrDefault(shielder, 0.0f);
            float lastProgress = lastAnimProgress.getOrDefault(shielder, progress);
            float interpolatedProgress = lastProgress + (progress - lastProgress) * partialTicks;
            float easedProgress = AnimationUtils.easeOutCubic(interpolatedProgress);
            float expandedScope = scope * easedProgress + RANGE_EXPANSION;

            RenderingUtils.drawFilledBox(
                x - expandedScope, y - expandedScope, z - expandedScope,
                x + expandedScope + 1, y + expandedScope + 1, z + expandedScope + 1,
                ORANGE_R, ORANGE_G, ORANGE_B, ALPHA
            );
        } finally {
            //? if <1.20 {
            GlStateManager.popMatrix();
            //?} else if <1.21 {
            /*RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
            modelViewStack.popPose();
            RenderSystem.applyModelViewMatrix();
            *///?} else {
            /*RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            *///?}
        }
    }

    //? if >=1.20 {
    /*@SubscribeEvent
    *///?}
        //? if <1.20 {
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) return;

        double playerX = RenderingUtils.getPlayerRenderX(event.getPartialTicks());
        double playerY = RenderingUtils.getPlayerRenderY(event.getPartialTicks());
        double playerZ = RenderingUtils.getPlayerRenderZ(event.getPartialTicks());
        float partialTicks = event.getPartialTicks();
        //?} else if <1.21 {
    /*public void onRenderWorldLast(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        var cameraPos = event.getCamera().getPosition();
        double playerX = cameraPos.x;
        double playerY = cameraPos.y;
        double playerZ = cameraPos.z;
        float partialTicks = event.getPartialTick();
    *///?} else {
    /*public void onRenderWorldLast(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        var cameraPos = event.getCamera().getPosition();
        double playerX = cameraPos.x;
        double playerY = cameraPos.y;
        double playerZ = cameraPos.z;
        float partialTicks = event.getPartialTick().getGameTimeDeltaPartialTick(false);
    *///?}

        if (clientShielders.isEmpty()) return;

        //? if >=1.20 {
        /*currentWorldPoseStack = event.getPoseStack();
        *///?}
        //? if >=1.21 {
        /*cachedEventViewMatrix = new org.joml.Matrix4f(event.getModelViewMatrix());
        *///?}
        for (ICirculationShielderBlockEntity shielder : clientShielders) {
            if (shielder.isShowingRange()) {
                animProgress.putIfAbsent(shielder, 0.0f);
                lastAnimProgress.putIfAbsent(shielder, animProgress.getOrDefault(shielder, 0.0f));
                renderShielderRange(shielder, playerX, playerY, playerZ, partialTicks);
            }
        }
        //? if >=1.20 {
        /*currentWorldPoseStack = null;
        *///?}
    }
}
