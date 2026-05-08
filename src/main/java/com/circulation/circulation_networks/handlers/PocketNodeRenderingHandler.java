package com.circulation.circulation_networks.handlers;

import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.client.render.PocketNodeModelCache;
import com.circulation.circulation_networks.client.render.PocketNodeItemStackRenderer;
import com.circulation.circulation_networks.manager.MachineTickManager;
import com.circulation.circulation_networks.pocket.PocketNodeClientHost;
import com.circulation.circulation_networks.pocket.PocketNodeRecord;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.client.Minecraft;
//~ mc_imports
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
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
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.GL11;
//?} else {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
//~ neo_imports
import net.minecraftforge.client.event.RenderLevelStageEvent;
*///?}
import org.jetbrains.annotations.Nullable;

//~ if >=1.20 '@SideOnly(Side.CLIENT)' -> '@OnlyIn(Dist.CLIENT)' {
@SideOnly(Side.CLIENT)
//~}
public final class PocketNodeRenderingHandler {

    public static final PocketNodeRenderingHandler INSTANCE = new PocketNodeRenderingHandler();
    // Keep a visible but tiny bias away from the host face to avoid Z-fighting.
    private static final double FACE_OFFSET = 0.503D;
    private static final float FACE_SCALE = 0.03125F;
    private final Int2ObjectMap<Long2ObjectMap<PocketNodeClientHost>> hosts = new Int2ObjectOpenHashMap<>();

    private PocketNodeRenderingHandler() {
    }

    private static void unregisterHost(PocketNodeClientHost host) {
        if (host != null) {
            host.invalidateNode();
            MachineTickManager.INSTANCE.unregisterClientMachine(host);
        }
    }

    private static long pack(BlockPos pos) {
        //? if <1.20 {
        return pos.toLong();
        //?} else {
        /*return pos.asLong();
         *///?}
    }

    //? if <1.20 {
    private static void applyWorldLight(Minecraft mc, BlockPos pos) {
        int packedLight = mc.world.getCombinedLight(pos, 0);
        int blockLight = packedLight % 65536;
        int skyLight = packedLight / 65536;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, blockLight, skyLight);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
    }

    private static void applyFullBrightLight() {
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
    }

    //?}

    //? if <1.20 {
    private static void applyFaceTransform(EnumFacing face) {
        EnumFacing resolved = face == null ? EnumFacing.UP : face;
        switch (resolved) {
            case DOWN -> {
                GlStateManager.translate(0.0D, -FACE_OFFSET, 0.0D);
                GlStateManager.rotate(90.0F, 1.0F, 0.0F, 0.0F);
                GlStateManager.rotate(180.0F, 0.0F, 0.0F, 1.0F);
            }
            case NORTH -> {
                GlStateManager.translate(0.0D, 0.0D, -FACE_OFFSET);
                GlStateManager.rotate(180.0F, 0.0F, 1.0F, 0.0F);
            }
            case SOUTH -> GlStateManager.translate(0.0D, 0.0D, FACE_OFFSET);
            case WEST -> {
                GlStateManager.translate(-FACE_OFFSET, 0.0D, 0.0D);
                GlStateManager.rotate(-90.0F, 0.0F, 1.0F, 0.0F);
            }
            case EAST -> {
                GlStateManager.translate(FACE_OFFSET, 0.0D, 0.0D);
                GlStateManager.rotate(90.0F, 0.0F, 1.0F, 0.0F);
            }
            default -> {
                GlStateManager.translate(0.0D, FACE_OFFSET, 0.0D);
                GlStateManager.rotate(-90.0F, 1.0F, 0.0F, 0.0F);
            }
        }
        GlStateManager.scale(FACE_SCALE, FACE_SCALE, FACE_SCALE);
    }
    //?}

    private Long2ObjectMap<PocketNodeClientHost> getDimHosts(int dimId) {
        return hosts.computeIfAbsent(dimId, ignored -> new Long2ObjectOpenHashMap<>());
    }

    private static int getDimensionId(
        //? if <1.20 {
        World world
        //?} else {
        /*Level world
        *///?}
    ) {
        //? if <1.20 {
        return world.provider.getDimension();
        //?} else {
        /*return world.dimension().location().hashCode();
        *///?}
    }

    @Nullable
    private PocketNodeClientHost getHost(
        //? if <1.20 {
        World world,
        //?} else {
        /*Level world,
        *///?}
        BlockPos pos
    ) {
        if (pos == null) {
            return null;
        }
        Long2ObjectMap<PocketNodeClientHost> dimHosts = hosts.get(getDimensionId(world));
        return dimHosts == null ? null : dimHosts.get(pack(pos));
    }

    public void setDimensionState(int dimId, ObjectList<PocketNodeRecord> records) {
        clearDimension(dimId);
        for (var record : records) {
            add(record);
        }
    }

    public void add(PocketNodeRecord record) {
        long posLong = pack(record.pos());
        Long2ObjectMap<PocketNodeClientHost> dimHosts = getDimHosts(record.dimensionId());
        unregisterHost(dimHosts.put(posLong, new PocketNodeClientHost(record)));
        MachineTickManager.INSTANCE.registerClientMachine(dimHosts.get(posLong));
    }

    public void remove(int dimId, BlockPos pos) {
        Long2ObjectMap<PocketNodeClientHost> dimHosts = hosts.get(dimId);
        if (dimHosts == null) {
            return;
        }
        unregisterHost(dimHosts.remove(pack(pos)));
        if (dimHosts.isEmpty()) {
            hosts.remove(dimId);
        }
    }

    public void clearDimension(int dimId) {
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

    //? if >=1.20 {
    /*private static final double MAX_RENDER_DISTANCE_SQ = 96.0D * 96.0D;

    @SubscribeEvent
    public void renderWorldLastEvent(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }

        int dimId = mc.level.dimension().location().hashCode();
        Long2ObjectMap<PocketNodeClientHost> dimHosts = hosts.get(dimId);
        if (dimHosts == null || dimHosts.isEmpty()) {
            return;
        }

        var cameraPos = event.getCamera().getPosition();
        double cameraX = cameraPos.x;
        double cameraY = cameraPos.y;
        double cameraZ = cameraPos.z;

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
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

            BakedModel itemModel = PocketNodeItemStackRenderer.toBrightItemModel(PocketNodeModelCache.get(host.getRenderStack()));
            itemModel.getTransforms().getTransform(ItemDisplayContext.GUI).apply(false, poseStack);
            poseStack.translate(-0.5F, -0.5F, -0.5F);
            VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
            mc.getBlockRenderer().getModelRenderer().renderModel(
                poseStack.last(), consumer, null, itemModel, 1.0F, 1.0F, 1.0F,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY
            );
            renderedAny = true;
            poseStack.popPose();
        }
        if (renderedAny) {
            bufferSource.endBatch();
        }
    }
    *///?}

    @Nullable
    public INode getNode(
        //? if <1.20 {
        World world,
        //?} else {
        /*Level world,
        *///?}
        BlockPos pos
    ) {
        PocketNodeClientHost host = getHost(world, pos);
        return host == null ? null : host.getNode(world);
    }

    //? if <1.20 {
    @SubscribeEvent
    public void renderWorldLastEvent(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null || mc.world == null) {
            return;
        }

        Long2ObjectMap<PocketNodeClientHost> dimHosts = hosts.get(player.dimension);
        if (dimHosts == null || dimHosts.isEmpty()) {
            return;
        }

        double cameraX = player.lastTickPosX + (player.posX - player.lastTickPosX) * event.getPartialTicks();
        double cameraY = player.lastTickPosY + (player.posY - player.lastTickPosY) * event.getPartialTicks();
        double cameraZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * event.getPartialTicks();

        for (var host : dimHosts.values()) {
            if (host.getRenderStack().isEmpty()) {
                continue;
            }

            BlockPos pos = host.getRecord().pos();
            EnumFacing face = host.getRecord().attachmentFace();
            GlStateManager.pushMatrix();
            GlStateManager.enableRescaleNormal();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ZERO
            );
            GlStateManager.disableCull();
            GlStateManager.depthMask(true);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.translate(pos.getX() + 0.5D - cameraX, pos.getY() + 0.5D - cameraY, pos.getZ() + 0.5D - cameraZ);
            applyFaceTransform(face);
            mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            GlStateManager.enableAlpha();
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
            GlStateManager.scale(16.0F, 16.0F, 16.0F);
            if (host.isGui3d()) {
                GlStateManager.scale(1.0F, 1.0F, 0.002F);
            }
            IBakedModel model = PocketNodeItemStackRenderer.toBrightItemModel(PocketNodeModelCache.get(host.getRenderStack()));
            model = ForgeHooksClient.handleCameraTransforms(model, ItemCameraTransforms.TransformType.GUI, false);
            RenderHelper.disableStandardItemLighting();
            applyFullBrightLight();
            mc.getRenderItem().renderItem(host.getRenderStack(), model);
            GlStateManager.depthMask(true);
            GlStateManager.enableCull();
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        }

        applyWorldLight(mc, player.getPosition());
    }
    //?} else {
    /*private static void applyFaceTransform(PoseStack poseStack, Direction face) {
        Direction resolved = face == null ? Direction.UP : face;
        switch (resolved) {
            case DOWN -> {
                poseStack.translate(0.0D, -FACE_OFFSET, 0.0D);
                poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90.0F));
                poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(180.0F));
            }
            case NORTH -> {
                poseStack.translate(0.0D, 0.0D, -FACE_OFFSET);
                poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F));
            }
            case SOUTH -> poseStack.translate(0.0D, 0.0D, FACE_OFFSET);
            case WEST -> {
                poseStack.translate(-FACE_OFFSET, 0.0D, 0.0D);
                poseStack.mulPose(com.mojang.math.Axis.YN.rotationDegrees(90.0F));
            }
            case EAST -> {
                poseStack.translate(FACE_OFFSET, 0.0D, 0.0D);
                poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90.0F));
            }
            default -> {
                poseStack.translate(0.0D, FACE_OFFSET, 0.0D);
                poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90.0F));
            }
        }
        poseStack.scale(FACE_SCALE, FACE_SCALE, FACE_SCALE);
    }
    *///?}
}
