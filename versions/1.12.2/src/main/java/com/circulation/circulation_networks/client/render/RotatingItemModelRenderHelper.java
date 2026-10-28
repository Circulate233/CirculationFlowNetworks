package com.circulation.circulation_networks.client.render;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Map;

@SideOnly(Side.CLIENT)
public final class RotatingItemModelRenderHelper {

    private static final float FULL_BRIGHT_LIGHT = 240.0F;
    private static final float CUTOUT_ALPHA_THRESHOLD = 0.1F;
    private static final Map<IBakedModel, IBakedModel> NO_DIFFUSE_MODELS = new Reference2ReferenceOpenHashMap<>();
    private static final Map<List<BakedQuad>, List<BakedQuad>> NO_DIFFUSE_QUAD_LISTS = new Reference2ReferenceOpenHashMap<>();
    private static final Map<BakedQuad, BakedQuad> NO_DIFFUSE_QUADS = new Reference2ReferenceOpenHashMap<>();

    private RotatingItemModelRenderHelper() {
    }

    public static void renderModel(ItemStack stack, ResourceLocation modelLocation) {
        renderModel(stack, modelLocation, false, false);
    }

    public static void renderModelNoDiffuse(ItemStack stack, ResourceLocation modelLocation) {
        renderModel(stack, modelLocation, false, true);
    }

    public static void renderModelFullBright(ItemStack stack, ResourceLocation modelLocation) {
        renderModel(stack, modelLocation, true, false);
    }

    public static void renderModelCutout(ItemStack stack, ResourceLocation modelLocation) {
        renderModel(stack, modelLocation, false, false, true);
    }

    public static void renderAroundYAxis(ItemStack stack, ResourceLocation modelLocation, float angle, float pivotX, float pivotY, float pivotZ) {
        renderAroundAxis(stack, modelLocation, angle, pivotX, pivotY, pivotZ, 0.0F, 1.0F, 0.0F, false, false);
    }

    public static void renderAroundYAxisNoDiffuse(ItemStack stack, ResourceLocation modelLocation, float angle, float pivotX, float pivotY, float pivotZ) {
        renderAroundAxis(stack, modelLocation, angle, pivotX, pivotY, pivotZ, 0.0F, 1.0F, 0.0F, false, true);
    }

    public static void renderAroundYAxisFullBright(ItemStack stack, ResourceLocation modelLocation, float angle, float pivotX, float pivotY, float pivotZ) {
        renderAroundAxis(stack, modelLocation, angle, pivotX, pivotY, pivotZ, 0.0F, 1.0F, 0.0F, true, false);
    }

    public static void renderAroundAxisCutout(
        ItemStack stack,
        ResourceLocation modelLocation,
        float angle,
        float pivotX,
        float pivotY,
        float pivotZ,
        float axisX,
        float axisY,
        float axisZ
    ) {
        renderAroundAxis(stack, modelLocation, angle, pivotX, pivotY, pivotZ, axisX, axisY, axisZ, false, false, true);
    }

    public static void renderAroundAxis(
        ItemStack stack,
        ResourceLocation modelLocation,
        float angle,
        float pivotX,
        float pivotY,
        float pivotZ,
        float axisX,
        float axisY,
        float axisZ
    ) {
        renderAroundAxis(stack, modelLocation, angle, pivotX, pivotY, pivotZ, axisX, axisY, axisZ, false, false);
    }

    public static void renderAroundAxis(
        ItemStack stack,
        ResourceLocation modelLocation,
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
        renderAroundAxis(stack, modelLocation, angle, pivotX, pivotY, pivotZ, axisX, axisY, axisZ, fullBright, noDiffuse, false);
    }

    private static void renderAroundAxis(
        ItemStack stack,
        ResourceLocation modelLocation,
        float angle,
        float pivotX,
        float pivotY,
        float pivotZ,
        float axisX,
        float axisY,
        float axisZ,
        boolean fullBright,
        boolean noDiffuse,
        boolean cutout
    ) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(pivotX, pivotY, pivotZ);
        GlStateManager.rotate(angle, axisX, axisY, axisZ);
        GlStateManager.translate(-pivotX, -pivotY, -pivotZ);
        renderModel(stack, modelLocation, fullBright, noDiffuse, cutout);
        GlStateManager.popMatrix();
    }

    private static void renderModel(ItemStack stack, ResourceLocation modelLocation, boolean fullBright, boolean noDiffuse) {
        renderModel(stack, modelLocation, fullBright, noDiffuse, false);
    }

    private static void renderModel(ItemStack stack, ResourceLocation modelLocation, boolean fullBright, boolean noDiffuse, boolean cutout) {
        IBakedModel model = RotatingBlockModelCache.get(modelLocation);
        if (noDiffuse) {
            model = toNoDiffuseModel(model);
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        minecraft.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        float lastBrightnessX = OpenGlHelper.lastBrightnessX;
        float lastBrightnessY = OpenGlHelper.lastBrightnessY;
        boolean lightingEnabled = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean alphaEnabled = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);

        if (cutout) {
            GlStateManager.enableAlpha();
            GlStateManager.alphaFunc(GL11.GL_GREATER, CUTOUT_ALPHA_THRESHOLD);
            GlStateManager.disableBlend();
        }

        if (fullBright) {
            GlStateManager.disableLighting();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, FULL_BRIGHT_LIGHT, FULL_BRIGHT_LIGHT);
        }

        try {
            ForgeHooksClient.renderLitItem(minecraft.getRenderItem(), model, -1, stack);
        } finally {
            if (fullBright) {
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastBrightnessX, lastBrightnessY);
                if (lightingEnabled) {
                    GlStateManager.enableLighting();
                } else {
                    GlStateManager.disableLighting();
                }
            }

            if (cutout) {
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
    }

    private static IBakedModel toNoDiffuseModel(IBakedModel model) {
        return NO_DIFFUSE_MODELS.computeIfAbsent(model, NoDiffuseBakedModel::new);
    }

    private static final class NoDiffuseBakedModel extends BakedModelWrapper<IBakedModel> {

        private NoDiffuseBakedModel(IBakedModel originalModel) {
            super(originalModel);
        }

        private static List<BakedQuad> wrapQuads(List<BakedQuad> quads) {
            List<BakedQuad> wrapped = new ObjectArrayList<>(quads.size());
            for (BakedQuad quad : quads) {
                wrapped.add(NO_DIFFUSE_QUADS.computeIfAbsent(quad, NoDiffuseBakedModel::wrapQuad));
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
            return NO_DIFFUSE_QUAD_LISTS.computeIfAbsent(quads, NoDiffuseBakedModel::wrapQuads);
        }
    }
}
