package com.circulation.circulation_networks.handlers;

import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.client.render.PocketNodeModelCache;
import com.circulation.circulation_networks.pocket.PocketNodeClientHost;
import com.circulation.circulation_networks.pocket.PocketNodeRecord;
import com.circulation.circulation_networks.utils.WorldResolveCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;

public final class PocketNodeRenderingHandler {

    public static final PocketNodeRenderingHandler INSTANCE = new PocketNodeRenderingHandler();
    private static final double FACE_OFFSET = 0.501D;
    private static final float FACE_SCALE = 0.03125F;
    private static final double MAX_RENDER_DISTANCE_SQ = 96.0D * 96.0D;
    private static final ItemFeatureRenderer ITEM_FEATURE_RENDERER = new ItemFeatureRenderer();
    private static final CustomFeatureRenderer CUSTOM_FEATURE_RENDERER = new CustomFeatureRenderer();
    private final Object2ObjectMap<String, Long2ObjectMap<PocketNodeClientHost>> hosts = new Object2ObjectOpenHashMap<>();

    private PocketNodeRenderingHandler() {
    }

    private static void unregisterHost(PocketNodeClientHost host) {
        if (host != null) {
            host.invalidateNode();
        }
    }

    private static long pack(BlockPos pos) {
        return pos.asLong();
    }

    private static String getDimensionId(
        Level world
    ) {
        return WorldResolveCompat.getDimensionId(world);
    }

    private static void applyFaceTransform(PoseStack poseStack, Direction face) {
        Direction resolved = face == null ? Direction.UP : face;
        switch (resolved) {
            case DOWN -> {
                poseStack.translate(0.0D, -FACE_OFFSET, 0.0D);
                poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
            }
            case NORTH -> {
                poseStack.translate(0.0D, 0.0D, -FACE_OFFSET);
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            }
            case SOUTH -> poseStack.translate(0.0D, 0.0D, FACE_OFFSET);
            case WEST -> {
                poseStack.translate(-FACE_OFFSET, 0.0D, 0.0D);
                poseStack.mulPose(Axis.YN.rotationDegrees(90.0F));
            }
            case EAST -> {
                poseStack.translate(FACE_OFFSET, 0.0D, 0.0D);
                poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            }
            default -> {
                poseStack.translate(0.0D, FACE_OFFSET, 0.0D);
                poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            }
        }
        poseStack.scale(FACE_SCALE, FACE_SCALE, FACE_SCALE);
    }

    private Long2ObjectMap<PocketNodeClientHost> getDimHosts(String dimId) {
        return hosts.computeIfAbsent(dimId, ignored -> new Long2ObjectOpenHashMap<>());
    }

    @Nullable
    private PocketNodeClientHost getHost(
        Level world,
        BlockPos pos
    ) {
        if (pos == null) {
            return null;
        }
        Long2ObjectMap<PocketNodeClientHost> dimHosts = hosts.get(getDimensionId(world));
        return dimHosts == null ? null : dimHosts.get(pack(pos));
    }

    public void setDimensionState(String dimId, ObjectList<PocketNodeRecord> records) {
        clearDimension(dimId);
        for (var record : records) {
            add(record);
        }
    }

    public void add(PocketNodeRecord record) {
        long posLong = pack(record.pos());
        Long2ObjectMap<PocketNodeClientHost> dimHosts = getDimHosts(record.dimensionId());
        unregisterHost(dimHosts.put(posLong, new PocketNodeClientHost(record)));
    }

    public void remove(String dimId, BlockPos pos) {
        Long2ObjectMap<PocketNodeClientHost> dimHosts = hosts.get(dimId);
        if (dimHosts == null) {
            return;
        }
        unregisterHost(dimHosts.remove(pack(pos)));
        if (dimHosts.isEmpty()) {
            hosts.remove(dimId);
        }
    }

    public void clearDimension(String dimId) {
        Long2ObjectMap<PocketNodeClientHost> dimHosts = hosts.remove(dimId);
        if (dimHosts == null) {
            return;
        }
        for (var host : dimHosts.values()) {
            unregisterHost(host);
        }
    }

    public void clear() {
        for (var dimHosts : hosts.values()) {
            for (var host : dimHosts.values()) {
                unregisterHost(host);
            }
        }
        hosts.clear();
    }

    @SubscribeEvent
    public void renderWorldLastEvent(RenderLevelStageEvent.AfterTranslucentParticles event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }

        String dimId = getDimensionId(mc.level);
        Long2ObjectMap<PocketNodeClientHost> dimHosts = hosts.get(dimId);
        if (dimHosts == null || dimHosts.isEmpty()) {
            return;
        }

        var cameraPos = mc.gameRenderer.getMainCamera().position();
        double cameraX = cameraPos.x;
        double cameraY = cameraPos.y;
        double cameraZ = cameraPos.z;

        SubmitNodeStorage submitNodeStorage = new SubmitNodeStorage();
        submitNodeStorage.order(0);
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        OutlineBufferSource outlineBufferSource = mc.renderBuffers().outlineBufferSource();
        boolean renderedAny = false;
        for (var host : dimHosts.values()) {
            if (host.getRenderStack().isEmpty()) {
                continue;
            }
            BlockPos pos = host.getRecord().getPos();
            double dx = pos.getX() + 0.5D - cameraX;
            double dy = pos.getY() + 0.5D - cameraY;
            double dz = pos.getZ() + 0.5D - cameraZ;
            if (dx * dx + dy * dy + dz * dz > MAX_RENDER_DISTANCE_SQ) {
                continue;
            }
            PoseStack poseStack = event.getPoseStack();
            poseStack.pushPose();
            poseStack.translate(pos.getX() + 0.5D - cameraX, pos.getY() + 0.5D - cameraY, pos.getZ() + 0.5D - cameraZ);
            applyFaceTransform(poseStack, host.getRecord().getAttachmentFace());
            poseStack.scale(16.0F, 16.0F, 16.0F);
            if (host.isGui3d()) {
                poseStack.scale(1.0F, 1.0F, 0.002F);
            }

            ItemStackRenderState itemState = PocketNodeModelCache.get(host.getRenderStack());
            if (itemState.isEmpty()) {
                poseStack.popPose();
                continue;
            }

            itemState.submit(poseStack, submitNodeStorage, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
            renderedAny = true;
            poseStack.popPose();
        }
        if (renderedAny) {
            for (SubmitNodeCollection submitNodeCollection : submitNodeStorage.getSubmitsPerOrder().values()) {
                CUSTOM_FEATURE_RENDERER.renderSolid(submitNodeCollection, bufferSource);
                ITEM_FEATURE_RENDERER.renderSolid(submitNodeCollection, bufferSource, outlineBufferSource);
                CUSTOM_FEATURE_RENDERER.renderTranslucent(submitNodeCollection, bufferSource);
                ITEM_FEATURE_RENDERER.renderTranslucent(submitNodeCollection, bufferSource, outlineBufferSource);
            }
            bufferSource.endBatch();
            outlineBufferSource.endOutlineBatch();
        }
    }

    @Nullable
    public INode getNode(
        Level world,
        BlockPos pos
    ) {
        PocketNodeClientHost host = getHost(world, pos);
        return host == null ? null : host.getNode(world);
    }
}
