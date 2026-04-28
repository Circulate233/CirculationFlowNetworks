package com.circulation.circulation_networks.client.render;

import com.github.bsideup.jabel.Desugar;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@SideOnly(Side.CLIENT)
@MethodsReturnNonnullByDefault
public final class RotatingModelRenderHelper {

    private static final int FULL_BRIGHT_LIGHTMAP = 15728880;
    private static final int DESTROY_STAGE_COUNT = 10;
    private static final Map<IBakedModel, IBakedModel> FULL_BRIGHT_MODELS = new IdentityHashMap<>();
    private static final Map<List<BakedQuad>, List<BakedQuad>> FULL_BRIGHT_QUAD_LISTS = new IdentityHashMap<>();
    private static final Map<BakedQuad, BakedQuad> FULL_BRIGHT_QUADS = new IdentityHashMap<>();
    private static final Map<ResourceLocation, Integer> FULL_BRIGHT_DISPLAY_LISTS = new IdentityHashMap<>();
    private static final Map<IBakedModel, IBakedModel> NO_DIFFUSE_MODELS = new IdentityHashMap<>();
    private static final Map<List<BakedQuad>, List<BakedQuad>> NO_DIFFUSE_QUAD_LISTS = new IdentityHashMap<>();
    private static final Map<BakedQuad, BakedQuad> NO_DIFFUSE_QUADS = new IdentityHashMap<>();
    private static final Object2ObjectMap<NoDiffuseDisplayListKey, CachedDisplayList> NO_DIFFUSE_DISPLAY_LISTS = new Object2ObjectOpenHashMap<>();
    private static final Object2ObjectMap<NoDiffuseDisplayListKey, CachedDisplayList> NORMAL_DISPLAY_LISTS = new Object2ObjectOpenHashMap<>();
    private static final Object2ObjectMap<LightSignatureKey, CachedLightSignature> LIGHT_SIGNATURES = new Object2ObjectOpenHashMap<>();

    private RotatingModelRenderHelper() {
    }

    public static void clearDisplayLists() {
        for (Integer listId : FULL_BRIGHT_DISPLAY_LISTS.values()) {
            if (listId != null && listId > 0) {
                GLAllocation.deleteDisplayLists(listId);
            }
        }
        FULL_BRIGHT_DISPLAY_LISTS.clear();
        for (CachedDisplayList cached : NO_DIFFUSE_DISPLAY_LISTS.values()) {
            if (cached != null && cached.id > 0) {
                GLAllocation.deleteDisplayLists(cached.id);
            }
        }
        NO_DIFFUSE_DISPLAY_LISTS.clear();
        for (CachedDisplayList cached : NORMAL_DISPLAY_LISTS.values()) {
            if (cached != null && cached.id > 0) {
                GLAllocation.deleteDisplayLists(cached.id);
            }
        }
        NORMAL_DISPLAY_LISTS.clear();
        LIGHT_SIGNATURES.clear();
    }

    public static void removeDisplayLists(int worldId, @NotNull BlockPos pos) {
        BlockPos immutablePos = pos.toImmutable();
        removeDisplayLists(NO_DIFFUSE_DISPLAY_LISTS, worldId, immutablePos);
        removeDisplayLists(NORMAL_DISPLAY_LISTS, worldId, immutablePos);
        var lightIter = LIGHT_SIGNATURES.entrySet().iterator();
        while (lightIter.hasNext()) {
            var entry = lightIter.next();
            LightSignatureKey key = entry.getKey();
            if (key.worldId == worldId && key.pos.equals(immutablePos)) {
                lightIter.remove();
            }
        }
    }

    /**
     * Begins a batched render session for a single TileEntity, performing GL state setup once
     * instead of per-part. Call {@link RenderBatch#end()} when done.
     */
    public static @Nullable RenderBatch beginBatch(@NotNull TileEntity tileEntity, double x, double y, double z, int destroyStage) {
        if (tileEntity == null || !tileEntity.hasWorld()) {
            return null;
        }
        return new RenderBatch(tileEntity, x, y, z, destroyStage);
    }

    private static void renderNoDiffuseDisplayList(
        @NotNull Minecraft minecraft,
        @NotNull Tessellator tessellator,
        @NotNull BufferBuilder buffer,
        @NotNull ResourceLocation modelLocation,
        @NotNull IBakedModel model,
        @NotNull IBlockState state,
        @NotNull TileEntity tileEntity,
        @NotNull BlockPos lightSamplePos,
        @NotNull BlockRenderLayer renderLayer,
        double blockX,
        double blockY,
        double blockZ
    ) {
        NoDiffuseDisplayListKey key = new NoDiffuseDisplayListKey(System.identityHashCode(tileEntity.getWorld()), tileEntity.getPos().toImmutable(), lightSamplePos.toImmutable(), modelLocation);
        int lightSignature = resolveNoDiffuseLightSignature(tileEntity, state, lightSamplePos);
        CachedDisplayList cached = NO_DIFFUSE_DISPLAY_LISTS.get(key);
        if (cached == null || cached.lightSignature != lightSignature) {
            if (cached != null && cached.id > 0) {
                GLAllocation.deleteDisplayLists(cached.id);
            }
            int displayList = compileFullBrightDisplayList(
                minecraft,
                tessellator,
                buffer,
                tileEntity.getWorld(),
                model,
                state,
                tileEntity,
                lightSamplePos,
                renderLayer,
                blockX,
                blockY,
                blockZ
            );
            cached = new CachedDisplayList(displayList, lightSignature);
            NO_DIFFUSE_DISPLAY_LISTS.put(key, cached);
        }
        GlStateManager.callList(cached.id);
    }

    private static void renderNormalDisplayList(
        @NotNull Minecraft minecraft,
        @NotNull Tessellator tessellator,
        @NotNull BufferBuilder buffer,
        @NotNull IBlockAccess lightAccess,
        @NotNull ResourceLocation modelLocation,
        @NotNull IBakedModel model,
        @NotNull IBlockState state,
        @NotNull TileEntity tileEntity,
        @NotNull BlockPos lightSamplePos,
        @NotNull BlockRenderLayer renderLayer,
        double blockX,
        double blockY,
        double blockZ
    ) {
        NoDiffuseDisplayListKey key = new NoDiffuseDisplayListKey(System.identityHashCode(tileEntity.getWorld()), tileEntity.getPos().toImmutable(), lightSamplePos.toImmutable(), modelLocation);
        int lightSignature = resolveNoDiffuseLightSignature(tileEntity, state, lightSamplePos);
        CachedDisplayList cached = NORMAL_DISPLAY_LISTS.get(key);
        if (cached == null || cached.lightSignature != lightSignature) {
            if (cached != null && cached.id > 0) {
                GLAllocation.deleteDisplayLists(cached.id);
            }
            int displayList = compileFullBrightDisplayList(
                minecraft,
                tessellator,
                buffer,
                lightAccess,
                model,
                state,
                tileEntity,
                lightSamplePos,
                renderLayer,
                blockX,
                blockY,
                blockZ
            );
            cached = new CachedDisplayList(displayList, lightSignature);
            NORMAL_DISPLAY_LISTS.put(key, cached);
        }
        GlStateManager.callList(cached.id);
    }

    private static void renderFullBrightDisplayList(
        @NotNull Minecraft minecraft,
        @NotNull Tessellator tessellator,
        @NotNull BufferBuilder buffer,
        @NotNull IBlockAccess lightAccess,
        @NotNull ResourceLocation modelLocation,
        @NotNull IBakedModel model,
        @NotNull IBlockState state,
        @NotNull TileEntity tileEntity,
        @NotNull BlockPos lightSamplePos,
        @NotNull BlockRenderLayer renderLayer,
        double blockX,
        double blockY,
        double blockZ
    ) {
        int displayList = FULL_BRIGHT_DISPLAY_LISTS.computeIfAbsent(
            modelLocation,
            ignored -> compileFullBrightDisplayList(minecraft, tessellator, buffer, lightAccess, model, state, tileEntity, lightSamplePos, renderLayer, blockX, blockY, blockZ)
        );
        GlStateManager.callList(displayList);
    }

    private static int compileFullBrightDisplayList(
        @NotNull Minecraft minecraft,
        @NotNull Tessellator tessellator,
        @NotNull BufferBuilder buffer,
        @NotNull IBlockAccess lightAccess,
        @NotNull IBakedModel model,
        @NotNull IBlockState state,
        @NotNull TileEntity tileEntity,
        @NotNull BlockPos lightSamplePos,
        @NotNull BlockRenderLayer renderLayer,
        double blockX,
        double blockY,
        double blockZ
    ) {
        int displayList = GLAllocation.generateDisplayLists(1);
        GlStateManager.glNewList(displayList, GL11.GL_COMPILE);
        try {
            renderModelPass(minecraft, tessellator, buffer, lightAccess, model, state, tileEntity, lightSamplePos, renderLayer, blockX, blockY, blockZ);
        } finally {
            GlStateManager.glEndList();
        }
        return displayList;
    }

    private static int resolveNoDiffuseLightSignature(@NotNull TileEntity tileEntity, @NotNull IBlockState state, @NotNull BlockPos lightSamplePos) {
        BlockPos pos = tileEntity.getPos().toImmutable();
        int worldId = System.identityHashCode(tileEntity.getWorld());
        long clientTicks = ClientAnimationTicker.ticks();
        int stateHash = state.hashCode();
        LightSignatureKey key = new LightSignatureKey(worldId, pos, lightSamplePos.toImmutable());
        CachedLightSignature cached = LIGHT_SIGNATURES.get(key);
        if (cached != null && cached.clientTicks == clientTicks && cached.stateHash == stateHash) {
            return cached.signature;
        }

        int signature = computeNoDiffuseLightSignature(tileEntity, state, lightSamplePos);
        LIGHT_SIGNATURES.put(key, new CachedLightSignature(clientTicks, stateHash, signature));
        return signature;
    }

    private static int computeNoDiffuseLightSignature(@NotNull TileEntity tileEntity, @NotNull IBlockState state, @NotNull BlockPos pos) {
        int signature = state.hashCode();
        signature = 31 * signature + tileEntity.getWorld().getCombinedLight(pos, state.getLightValue(tileEntity.getWorld(), pos));
        for (EnumFacing facing : EnumFacing.values()) {
            BlockPos sidePos = pos.offset(facing);
            IBlockState sideState = tileEntity.getWorld().getBlockState(sidePos);
            signature = 31 * signature + tileEntity.getWorld().getCombinedLight(sidePos, sideState.getLightValue(tileEntity.getWorld(), sidePos));
            signature = 31 * signature + sideState.getLightOpacity(tileEntity.getWorld(), sidePos);
            signature = 31 * signature + (sideState.isOpaqueCube() ? 1 : 0);
        }
        return signature;
    }

    private static void renderModelPass(
        @NotNull Minecraft minecraft,
        @NotNull Tessellator tessellator,
        @NotNull BufferBuilder buffer,
        @NotNull IBlockAccess lightAccess,
        @NotNull IBakedModel model,
        @NotNull IBlockState state,
        @NotNull TileEntity tileEntity,
        @NotNull BlockPos lightSamplePos,
        @NotNull BlockRenderLayer renderLayer,
        double blockX,
        double blockY,
        double blockZ
    ) {
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
        buffer.setTranslation(-blockX, -blockY, -blockZ);
        try {
            ForgeHooksClient.setRenderLayer(renderLayer);
            minecraft.getBlockRendererDispatcher().getBlockModelRenderer().renderModel(
                lightAccess,
                model,
                state,
                tileEntity.getPos(),
                buffer,
                false
            );
            tessellator.draw();
        } finally {
            buffer.setTranslation(0.0D, 0.0D, 0.0D);
            ForgeHooksClient.setRenderLayer(null);
        }
    }

    private static void renderDamagePass(
        @NotNull Minecraft minecraft,
        @NotNull Tessellator tessellator,
        @NotNull BufferBuilder buffer,
        @NotNull IBlockAccess lightAccess,
        @NotNull IBakedModel damageModel,
        @NotNull IBlockState state,
        @NotNull TileEntity tileEntity,
        @NotNull BlockPos lightSamplePos,
        @NotNull BlockRenderLayer renderLayer,
        double blockX,
        double blockY,
        double blockZ
    ) {
        try {
            minecraft.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
            buffer.setTranslation(-blockX, -blockY, -blockZ);
            buffer.noColor();
            ForgeHooksClient.setRenderLayer(renderLayer);
            minecraft.getBlockRendererDispatcher().getBlockModelRenderer().renderModel(
                lightAccess,
                damageModel,
                state,
                tileEntity.getPos(),
                buffer,
                false
            );
            tessellator.draw();
        } finally {
            buffer.setTranslation(0.0D, 0.0D, 0.0D);
            ForgeHooksClient.setRenderLayer(null);
        }
    }

    private static @Nullable IBakedModel createDamageModel(@NotNull IBakedModel model, @NotNull IBlockState state, @NotNull TileEntity tileEntity, int destroyStage) {
        if (destroyStage < 0 || destroyStage >= DESTROY_STAGE_COUNT) {
            return null;
        }
        TextureAtlasSprite damageSprite = Minecraft.getMinecraft()
                                                   .getTextureMapBlocks()
                                                   .getAtlasSprite("minecraft:blocks/destroy_stage_" + destroyStage);
        return ForgeHooksClient.getDamageModel(model, damageSprite, state, tileEntity.getWorld(), tileEntity.getPos());
    }

    private static IBakedModel toFullBrightModel(@NotNull IBakedModel model) {
        return FULL_BRIGHT_MODELS.computeIfAbsent(model, FullBrightBakedModel::new);
    }

    private static IBakedModel toNoDiffuseModel(@NotNull IBakedModel model) {
        return NO_DIFFUSE_MODELS.computeIfAbsent(model, NoDiffuseBakedModel::new);
    }

    private static void removeDisplayLists(@NotNull Map<NoDiffuseDisplayListKey, CachedDisplayList> cache, int worldId, @NotNull BlockPos pos) {
        var iter = cache.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            NoDiffuseDisplayListKey key = entry.getKey();
            if (key.worldId == worldId && key.pos.equals(pos)) {
                CachedDisplayList cached = entry.getValue();
                if (cached != null && cached.id > 0) {
                    GLAllocation.deleteDisplayLists(cached.id);
                }
                iter.remove();
            }
        }
    }

    public static final class RenderBatch {

        private final TileEntity tileEntity;
        private final IBlockState state;
        private final double x, y, z;
        private final int destroyStage;
        private final Minecraft minecraft;
        private final Tessellator tessellator;
        private final BufferBuilder buffer;
        private final BlockRenderLayer renderLayer;
        private final double blockX, blockY, blockZ;
        private final LightSamplingBlockAccess fullBrightAccess;
        private final BlockPos renderPos;
        private @Nullable BlockPos remappedLightSamplePos;
        private @Nullable LightSamplingBlockAccess remappedLightAccess;
        private @Nullable BlockPos remappedFullBrightSamplePos;
        private @Nullable LightSamplingBlockAccess remappedFullBrightAccess;

        private RenderBatch(@NotNull TileEntity tileEntity, double x, double y, double z, int destroyStage) {
            this.tileEntity = tileEntity;
            this.renderPos = tileEntity.getPos().toImmutable();
            this.state = tileEntity.getWorld().getBlockState(renderPos);
            this.x = x;
            this.y = y;
            this.z = z;
            this.destroyStage = destroyStage;
            this.minecraft = Minecraft.getMinecraft();
            this.tessellator = Tessellator.getInstance();
            this.buffer = tessellator.getBuffer();
            this.renderLayer = state.getBlock().getRenderLayer();
            this.blockX = renderPos.getX();
            this.blockY = renderPos.getY();
            this.blockZ = renderPos.getZ();
            this.fullBrightAccess = new LightSamplingBlockAccess(tileEntity.getWorld(), renderPos, renderPos, true);

            boolean smoothShading = Minecraft.isAmbientOcclusionEnabled()
                && state.getLightValue(tileEntity.getWorld(), renderPos) == 0;
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            RenderHelper.disableStandardItemLighting();
            GlStateManager.enableRescaleNormal();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            GlStateManager.disableCull();
            GlStateManager.shadeModel(smoothShading ? 7425 : 7424);
            minecraft.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        }

        public TileEntity getTileEntity() {
            return tileEntity;
        }

        public void renderAroundYAxisFullBright(@NotNull ResourceLocation modelLocation, float angle, float pivotX, float pivotY, float pivotZ) {
            renderAroundAxis(modelLocation, angle, pivotX, pivotY, pivotZ, 0.0F, 1.0F, 0.0F, true, false);
        }

        public void renderAroundYAxisFullBright(
            @NotNull ResourceLocation modelLocation,
            float angle,
            float pivotX,
            float pivotY,
            float pivotZ,
            @NotNull BlockPos lightSamplePos
        ) {
            renderAroundAxis(modelLocation, angle, pivotX, pivotY, pivotZ, 0.0F, 1.0F, 0.0F, true, false, lightSamplePos);
        }

        public void renderAroundAxis(
            @NotNull ResourceLocation modelLocation,
            float angle,
            float pivotX,
            float pivotY,
            float pivotZ,
            float axisX,
            float axisY,
            float axisZ,
            boolean fullBright,
            boolean noDiffuse
        ) {
            renderAroundAxis(modelLocation, angle, pivotX, pivotY, pivotZ, axisX, axisY, axisZ, fullBright, noDiffuse, renderPos);
        }

        public void renderAroundAxis(
            @NotNull ResourceLocation modelLocation,
            float angle,
            float pivotX,
            float pivotY,
            float pivotZ,
            float axisX,
            float axisY,
            float axisZ,
            boolean fullBright,
            boolean noDiffuse,
            @NotNull BlockPos lightSamplePos
        ) {
            IBlockAccess lightAccess = resolveLightAccess(fullBright, lightSamplePos);
            IBakedModel baseModel = RotatingBlockModelCache.get(modelLocation);
            IBakedModel model = fullBright
                ? toFullBrightModel(baseModel)
                : (noDiffuse ? toNoDiffuseModel(baseModel) : baseModel);

            GlStateManager.pushMatrix();
            GlStateManager.translate(x, y, z);
            GlStateManager.translate(pivotX, pivotY, pivotZ);
            GlStateManager.rotate(angle, axisX, axisY, axisZ);
            GlStateManager.translate(-pivotX, -pivotY, -pivotZ);

            if (fullBright && destroyStage < 0) {
                renderFullBrightDisplayList(minecraft, tessellator, buffer, lightAccess, modelLocation, model, state, tileEntity, lightSamplePos, renderLayer, blockX, blockY, blockZ);
            } else if (noDiffuse && destroyStage < 0) {
                renderNoDiffuseDisplayList(minecraft, tessellator, buffer, modelLocation, model, state, tileEntity, lightSamplePos, renderLayer, blockX, blockY, blockZ);
            } else if (destroyStage < 0) {
                renderNormalDisplayList(minecraft, tessellator, buffer, lightAccess, modelLocation, model, state, tileEntity, lightSamplePos, renderLayer, blockX, blockY, blockZ);
            } else {
                renderModelPass(minecraft, tessellator, buffer, lightAccess, model, state, tileEntity, lightSamplePos, renderLayer, blockX, blockY, blockZ);
            }

            IBakedModel damageModel = createDamageModel(model, state, tileEntity, destroyStage);
            if (damageModel != null) {
                renderDamagePass(minecraft, tessellator, buffer, lightAccess, damageModel, state, tileEntity, lightSamplePos, renderLayer, blockX, blockY, blockZ);
            }

            GlStateManager.popMatrix();
        }

        private IBlockAccess resolveLightAccess(boolean fullBright, @NotNull BlockPos lightSamplePos) {
            if (fullBright) {
                if (lightSamplePos.equals(renderPos)) {
                    return fullBrightAccess;
                }
                if (remappedFullBrightAccess == null || remappedFullBrightSamplePos == null || !remappedFullBrightSamplePos.equals(lightSamplePos)) {
                    remappedFullBrightSamplePos = lightSamplePos.toImmutable();
                    remappedFullBrightAccess = new LightSamplingBlockAccess(tileEntity.getWorld(), renderPos, remappedFullBrightSamplePos, true);
                }
                return remappedFullBrightAccess;
            }
            if (lightSamplePos.equals(renderPos)) {
                return tileEntity.getWorld();
            }
            if (remappedLightAccess == null || remappedLightSamplePos == null || !remappedLightSamplePos.equals(lightSamplePos)) {
                remappedLightSamplePos = lightSamplePos.toImmutable();
                remappedLightAccess = new LightSamplingBlockAccess(tileEntity.getWorld(), renderPos, remappedLightSamplePos, false);
            }
            return remappedLightAccess;
        }

        public void end() {
            RenderHelper.enableStandardItemLighting();
            GlStateManager.enableCull();
            GlStateManager.disableBlend();
            GlStateManager.disableRescaleNormal();
        }
    }

    @Desugar
    @ParametersAreNonnullByDefault
    private record LightSamplingBlockAccess(IBlockAccess delegate, BlockPos renderPos, BlockPos samplePos,
                                            boolean fullBright) implements IBlockAccess {

        private BlockPos remap(BlockPos pos) {
            return pos.add(
                samplePos.getX() - renderPos.getX(),
                samplePos.getY() - renderPos.getY(),
                samplePos.getZ() - renderPos.getZ()
            );
        }

        @Override
        public @Nullable TileEntity getTileEntity(BlockPos pos) {
            return delegate.getTileEntity(remap(pos));
        }

        @Override
        public int getCombinedLight(BlockPos pos, int lightValue) {
            return fullBright ? FULL_BRIGHT_LIGHTMAP : delegate.getCombinedLight(remap(pos), lightValue);
        }

        @Override
        public IBlockState getBlockState(BlockPos pos) {
            return delegate.getBlockState(remap(pos));
        }

        @Override
        public boolean isAirBlock(BlockPos pos) {
            return delegate.isAirBlock(remap(pos));
        }

        @Override
        public Biome getBiome(BlockPos pos) {
            return delegate.getBiome(remap(pos));
        }

        @Override
        public int getStrongPower(BlockPos pos, EnumFacing direction) {
            return delegate.getStrongPower(remap(pos), direction);
        }

        @Override
        public WorldType getWorldType() {
            return delegate.getWorldType();
        }

        @Override
        public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean _default) {
            return delegate.isSideSolid(remap(pos), side, _default);
        }
    }

    private static final class FullBrightBakedModel extends BakedModelWrapper<IBakedModel> {

        private FullBrightBakedModel(@NotNull IBakedModel originalModel) {
            super(originalModel);
        }

        private static List<BakedQuad> wrapQuads(@NotNull List<BakedQuad> quads) {
            List<BakedQuad> wrapped = new ArrayList<>(quads.size());
            for (BakedQuad quad : quads) {
                wrapped.add(FULL_BRIGHT_QUADS.computeIfAbsent(quad, FullBrightBakedModel::wrapQuad));
            }
            return wrapped;
        }

        private static BakedQuad wrapQuad(@NotNull BakedQuad quad) {
            return new BakedQuad(quad.getVertexData(), quad.getTintIndex(), quad.getFace(), quad.getSprite(), false, quad.getFormat());
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
            List<BakedQuad> quads = originalModel.getQuads(state, side, rand);
            if (quads.isEmpty()) {
                return quads;
            }
            return FULL_BRIGHT_QUAD_LISTS.computeIfAbsent(quads, FullBrightBakedModel::wrapQuads);
        }
    }

    private static final class NoDiffuseBakedModel extends BakedModelWrapper<IBakedModel> {

        private NoDiffuseBakedModel(@NotNull IBakedModel originalModel) {
            super(originalModel);
        }

        private static List<BakedQuad> wrapQuads(@NotNull List<BakedQuad> quads) {
            List<BakedQuad> wrapped = new ArrayList<>(quads.size());
            for (BakedQuad quad : quads) {
                wrapped.add(NO_DIFFUSE_QUADS.computeIfAbsent(quad, NoDiffuseBakedModel::wrapQuad));
            }
            return wrapped;
        }

        private static BakedQuad wrapQuad(@NotNull BakedQuad quad) {
            return new BakedQuad(quad.getVertexData(), quad.getTintIndex(), quad.getFace(), quad.getSprite(), false, quad.getFormat());
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
            List<BakedQuad> quads = originalModel.getQuads(state, side, rand);
            if (quads.isEmpty()) {
                return quads;
            }
            return NO_DIFFUSE_QUAD_LISTS.computeIfAbsent(quads, NoDiffuseBakedModel::wrapQuads);
        }
    }

    @Desugar
    private record CachedDisplayList(int id, int lightSignature) {
    }

    @Desugar
    private record CachedLightSignature(long clientTicks, int stateHash, int signature) {
    }

    @Desugar
    private record NoDiffuseDisplayListKey(int worldId, BlockPos pos,
                                           BlockPos lightSamplePos, ResourceLocation modelLocation) {
    }

    @Desugar
    private record LightSignatureKey(int worldId, BlockPos pos, BlockPos lightSamplePos) {
    }
}
