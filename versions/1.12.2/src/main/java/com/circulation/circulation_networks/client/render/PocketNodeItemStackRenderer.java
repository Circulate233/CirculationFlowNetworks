package com.circulation.circulation_networks.client.render;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import com.circulation.circulation_networks.registry.CFNItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Map;

@SideOnly(Side.CLIENT)
public final class PocketNodeItemStackRenderer extends TileEntityItemStackRenderer {

    private static final float CUTOUT_ALPHA_THRESHOLD = 0.1F;
    private static final Map<IBakedModel, IBakedModel> BRIGHT_ITEM_MODELS = new Reference2ReferenceOpenHashMap<>();

    public static void bindItemRenderers() {
        PocketNodeItemStackRenderer renderer = new PocketNodeItemStackRenderer();
        bind(CFNItems.pocketPortNode, renderer);
        bind(CFNItems.pocketChargingNode, renderer);
        bind(CFNItems.pocketRelayNode, renderer);
    }

    private static void bind(Item item, TileEntityItemStackRenderer renderer) {
        if (item != Items.AIR) {
            item.setTileEntityItemStackRenderer(renderer);
        }
    }

    public static IBakedModel toBrightItemModel(IBakedModel model) {
        return BRIGHT_ITEM_MODELS.computeIfAbsent(model, BrightItemBakedModel::new);
    }

    public static void clearCache() {
        BRIGHT_ITEM_MODELS.clear();
        BrightItemBakedModel.NO_DIFFUSE_QUADS.clear();
        BrightItemBakedModel.NO_DIFFUSE_QUAD_LISTS.clear();
    }

    @Override
    public void renderByItem(ItemStack stack, float partialTicks) {
        if (stack.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        IBakedModel model = toBrightItemModel(PocketNodeModelCache.get(stack));
        minecraft.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        boolean cullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean alphaEnabled = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        boolean lightingEnabled = GL11.glIsEnabled(GL11.GL_LIGHTING);
        float savedBrightnessX = OpenGlHelper.lastBrightnessX;
        float savedBrightnessY = OpenGlHelper.lastBrightnessY;

        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, CUTOUT_ALPHA_THRESHOLD);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
            GL11.GL_SRC_ALPHA,
            GL11.GL_ONE_MINUS_SRC_ALPHA,
            GL11.GL_ONE,
            GL11.GL_ZERO
        );
        GlStateManager.disableLighting();
        GlStateManager.disableCull();

        try {
            GlStateManager.pushMatrix();
            GlStateManager.translate(0.5F, 0.5F, 0.5F);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            minecraft.getRenderItem().renderItem(stack, model);
            GlStateManager.popMatrix();
        } finally {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, savedBrightnessX, savedBrightnessY);
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            if (lightingEnabled) {
                GlStateManager.enableLighting();
            } else {
                GlStateManager.disableLighting();
            }
            if (cullEnabled) {
                GlStateManager.enableCull();
            } else {
                GlStateManager.disableCull();
            }
            if (blendEnabled) {
                GlStateManager.enableBlend();
            } else {
                GlStateManager.disableBlend();
            }
            if (alphaEnabled) {
                GlStateManager.enableAlpha();
            } else {
                GlStateManager.disableAlpha();
            }
            GlStateManager.alphaFunc(GL11.GL_GREATER, CUTOUT_ALPHA_THRESHOLD);
        }
    }

    private static final class BrightItemBakedModel extends BakedModelWrapper<IBakedModel> {

        private static final Map<BakedQuad, BakedQuad> NO_DIFFUSE_QUADS = new Reference2ReferenceOpenHashMap<>();
        private static final Map<List<BakedQuad>, List<BakedQuad>> NO_DIFFUSE_QUAD_LISTS = new Reference2ReferenceOpenHashMap<>();

        private BrightItemBakedModel(IBakedModel originalModel) {
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
            return new BakedQuad(quad.getVertexData(), quad.getTintIndex(), quad.getFace(), quad.getSprite(), false, quad.getFormat());
        }

        @Override
        public @NotNull List<BakedQuad> getQuads(@Nullable net.minecraft.block.state.IBlockState state, @Nullable EnumFacing side, long rand) {
            List<BakedQuad> quads = originalModel.getQuads(state, side, rand);
            if (quads.isEmpty()) {
                return quads;
            }
            return NO_DIFFUSE_QUAD_LISTS.computeIfAbsent(quads, BrightItemBakedModel::wrapQuads);
        }

        @Override
        public boolean isAmbientOcclusion() {
            return false;
        }
    }
}
