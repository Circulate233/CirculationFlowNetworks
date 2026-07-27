package com.circulation.circulation_networks.handlers;

import com.circulation.circulation_networks.api.ICirculationShielderBlockEntity;
import com.circulation.circulation_networks.client.compat.RenderSystemCompat;
import com.circulation.circulation_networks.utils.AnimationUtils;
import com.circulation.circulation_networks.utils.RenderingUtils;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.WeakHashMap;

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
    private Matrix4f cachedEventViewMatrix;

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

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Pre event) {

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
        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.set(cachedEventViewMatrix);

        modelViewStack.translate((float) -playerX, (float) -playerY, (float) -playerZ);
        RenderSystemCompat.applyModelViewMatrix();

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

            RenderingUtils.drawFilledBoxDoubleSided(
                x - expandedScope, y - expandedScope, z - expandedScope,
                x + expandedScope + 1, y + expandedScope + 1, z + expandedScope + 1,
                ORANGE_R, ORANGE_G, ORANGE_B, ALPHA
            );
        } finally {
            modelViewStack.popMatrix();
            RenderSystemCompat.applyModelViewMatrix();
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderLevelStageEvent.AfterTranslucentParticles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        var cameraPos = mc.gameRenderer.getMainCamera().position();
        double playerX = cameraPos.x;
        double playerY = cameraPos.y;
        double playerZ = cameraPos.z;
        float partialTicks = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);

        if (clientShielders.isEmpty()) return;

        cachedEventViewMatrix = new Matrix4f(event.getModelViewMatrix());
        for (ICirculationShielderBlockEntity shielder : clientShielders) {
            if (shielder.isShowingRange()) {
                animProgress.putIfAbsent(shielder, 0.0f);
                lastAnimProgress.putIfAbsent(shielder, animProgress.getOrDefault(shielder, 0.0f));
                renderShielderRange(shielder, playerX, playerY, playerZ, partialTicks);
            }
        }
    }
}
