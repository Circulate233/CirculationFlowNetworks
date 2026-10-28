package com.circulation.circulation_networks.client.render;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class PocketNodeItemStackRenderer extends BlockEntityWithoutLevelRenderer {

    private static final Map<BakedModel, BakedModel> BRIGHT_ITEM_MODELS = new Reference2ReferenceOpenHashMap<>();
    private static PocketNodeItemStackRenderer instance;

    private PocketNodeItemStackRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet modelSet) {
        super(dispatcher, modelSet);
    }

    public static PocketNodeItemStackRenderer getInstance() {
        if (instance == null) {
            Minecraft minecraft = Minecraft.getInstance();
            instance = new PocketNodeItemStackRenderer(minecraft.getBlockEntityRenderDispatcher(), minecraft.getEntityModels());
        }
        return instance;
    }

    public static BakedModel toBrightItemModel(BakedModel model) {
        return BRIGHT_ITEM_MODELS.computeIfAbsent(model, BrightItemBakedModel::new);
    }

    public static void clearCache() {
        BRIGHT_ITEM_MODELS.clear();
        BrightItemBakedModel.NO_DIFFUSE_QUADS.clear();
        BrightItemBakedModel.NO_DIFFUSE_QUAD_LISTS.clear();
    }

    @Override
    public void renderByItem(ItemStack stack, @NotNull ItemDisplayContext displayContext, @NotNull PoseStack poseStack,
                             @NotNull MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (stack.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = toBrightItemModel(PocketNodeModelCache.get(stack));
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
        minecraft.getBlockRenderer().getModelRenderer().renderModel(
            poseStack.last(), consumer, null, model, 1.0F, 1.0F, 1.0F, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null
        );
    }

    private static final class BrightItemBakedModel extends BakedModelWrapper<BakedModel> {

        private static final Map<BakedQuad, BakedQuad> NO_DIFFUSE_QUADS = new Reference2ReferenceOpenHashMap<>();
        private static final Map<List<BakedQuad>, List<BakedQuad>> NO_DIFFUSE_QUAD_LISTS = new Reference2ReferenceOpenHashMap<>();

        private BrightItemBakedModel(BakedModel originalModel) {
            super(originalModel);
        }

        private static List<BakedQuad> wrapQuads(List<BakedQuad> quads) {
            List<BakedQuad> wrapped = new ObjectArrayList<>(quads.size());
            for (BakedQuad quad : quads) {
                wrapped.add(NO_DIFFUSE_QUADS.computeIfAbsent(quad, BrightItemBakedModel::wrapQuad));
            }
            return wrapped;
        }

        private static BakedQuad wrapQuad(BakedQuad quad) {
            return new BakedQuad(
                quad.getVertices(),
                quad.getTintIndex(),
                quad.getDirection(),
                quad.getSprite(),
                false,
                false
            );
        }

        @Override
        public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull net.minecraft.util.RandomSource rand, @NotNull ModelData data, @Nullable RenderType renderType) {
            List<BakedQuad> quads = originalModel.getQuads(state, side, rand, data, renderType);
            if (quads.isEmpty()) {
                return quads;
            }
            return NO_DIFFUSE_QUAD_LISTS.computeIfAbsent(quads, BrightItemBakedModel::wrapQuads);
        }

        @Override
        public boolean useAmbientOcclusion() {
            return false;
        }

        @Override
        public boolean usesBlockLight() {
            return false;
        }
    }
}
