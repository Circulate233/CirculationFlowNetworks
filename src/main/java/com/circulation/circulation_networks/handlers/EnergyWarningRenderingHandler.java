package com.circulation.circulation_networks.handlers;

import com.circulation.circulation_networks.client.compat.RenderSystemCompat;
import com.circulation.circulation_networks.client.render.ClientAnimationTicker;
import com.circulation.circulation_networks.gui.component.base.AtlasRegion;
import com.circulation.circulation_networks.gui.component.base.AtlasRenderHelper;
import com.circulation.circulation_networks.gui.component.base.ComponentAtlas;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class EnergyWarningRenderingHandler {

    public static final EnergyWarningRenderingHandler INSTANCE = new EnergyWarningRenderingHandler();
    private static final long WARNING_TTL_TICKS = 40L;
    private static final double MAX_RENDER_DISTANCE_SQ = 48.0D * 48.0D;
    private static final float ICON_SIZE = 0.375F;
    private static final double ICON_HEIGHT = 1.25D;
    private static final String WARNING_SPRITE = "warning";
    private final Object2ObjectMap<String, Long2LongMap> warnings = new Object2ObjectOpenHashMap<>();

    private EnergyWarningRenderingHandler() {
    }

    private static AtlasRegion getWarningRegion() {
        ComponentAtlas atlas = ComponentAtlas.INSTANCE;
        atlas.awaitReady();
        return atlas.getRegion(WARNING_SPRITE);
    }

    private static double distanceSqToPlayer(Minecraft mc, BlockPos pos) {
        assert mc.player != null;
        double dx = mc.player.getX() - (pos.getX() + 0.5D);
        double dy = mc.player.getY() - (pos.getY() + ICON_HEIGHT);
        double dz = mc.player.getZ() - (pos.getZ() + 0.5D);
        return dx * dx + dy * dy + dz * dz;
    }

    public void refreshWarnings(String dimId, LongCollection positions) {
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
    public void onClientTick(ClientTickEvent.Pre event) {
        //if (event.phase != TickEvent.Phase.START) return;
        cleanupExpired();
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderLevelStageEvent.AfterTranslucentParticles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        Long2LongMap dimWarnings = warnings.get(com.circulation.circulation_networks.utils.WorldResolveCompat.getDimensionId(mc.level));
        if (dimWarnings == null || dimWarnings.isEmpty()) {
            return;
        }

        var cameraPos = mc.gameRenderer.getMainCamera().position();
        double cameraX = cameraPos.x;
        double cameraY = cameraPos.y;
        double cameraZ = cameraPos.z;
        AtlasRegion warningRegion = getWarningRegion();
        if (warningRegion == null) {
            return;
        }
        long clientTick = ClientAnimationTicker.ticks();

        var mvStack = RenderSystem.getModelViewStack();
        mvStack.pushMatrix();
        mvStack.set(event.getModelViewMatrix());
        mvStack.translate((float) -cameraX, (float) -cameraY, (float) -cameraZ);
        RenderSystemCompat.applyModelViewMatrix();

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

        mvStack.popMatrix();
        RenderSystemCompat.applyModelViewMatrix();
    }

    private void cleanupExpired() {
        long clientTick = ClientAnimationTicker.ticks();
        for (var dimIterator = warnings.object2ObjectEntrySet().iterator(); dimIterator.hasNext(); ) {
            var dimEntry = dimIterator.next();
            Long2LongMap dimWarnings = dimEntry.getValue();
            dimWarnings.long2LongEntrySet().removeIf(warningEntry -> clientTick - warningEntry.getLongValue() > WARNING_TTL_TICKS);
            if (dimWarnings.isEmpty()) {
                dimIterator.remove();
            }
        }
    }

    private void renderWarning(AtlasRegion warningRegion, BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        var mvStack = RenderSystem.getModelViewStack();
        mvStack.pushMatrix();
        mvStack.translate(pos.getX() + 0.5F, (float) (pos.getY() + ICON_HEIGHT), pos.getZ() + 0.5F);
        mvStack.rotate(RenderSystemCompat.getCameraOrientation(mc));
        mvStack.scale(-ICON_SIZE, -ICON_SIZE, ICON_SIZE);
        AtlasRenderHelper.drawRegion(ComponentAtlas.INSTANCE, warningRegion, -1.0F, -1.0F, 2.0F, 2.0F);
        mvStack.popMatrix();
    }
}
