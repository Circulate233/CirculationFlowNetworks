package com.circulation.circulation_networks.gui.component.base;

import it.unimi.dsi.fastutil.objects.ObjectLists;
import com.circulation.circulation_networks.CirculationFlowNetworks;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
//~ mc_imports
import net.minecraft.util.ResourceLocation;
//? if <1.20 {
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
//?} else if <1.21 {
/*import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
*///?} else {
/*import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
*///?}
//? if <1.20 {
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import com.github.bsideup.jabel.Desugar;
//?}
//? if <1.20 {
import net.minecraftforge.common.MinecraftForge;
//?} else if <1.21 {
/*import net.minecraftforge.common.MinecraftForge;
import com.mojang.blaze3d.systems.RenderSystem;
*///?} else {
/*import net.neoforged.neoforge.common.NeoForge;
import com.mojang.blaze3d.systems.RenderSystem;
 *///?}
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.zip.CRC32;

/**
 * Manages the single texture atlas used by all {@link Component} subclass sprites.
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>{@link #startAsync(File)} — called during client init on the main thread.
 *       Discovers sprites via the Minecraft {@code ResourceManager}, then stitches them
 *       into a single {@link BufferedImage} on a background thread. The result is cached
 *       under {@code modConfigDir/atlas_<hash>.png}.</li>
 *   <li>{@link #awaitReady()} — must be called on the GL thread before rendering.
 *       Blocks until stitching is complete, then uploads the image to a GL texture.</li>
 *   <li>{@link #getRegion(String)} — looks up an {@link AtlasRegion} by sprite
 *       base-name (without extension).</li>
 * </ol>
 */
//~ if >=1.20 '@SideOnly(Side.CLIENT)' -> '@OnlyIn(Dist.CLIENT)' {
@SideOnly(Side.CLIENT)
//~}
public final class ComponentAtlas extends ComponentAtlasRegistry {

    public static final ComponentAtlas INSTANCE = new ComponentAtlas();

    private static final String COMPONENT_DIR = "textures/gui/component/";
    private static final String DOMAIN = CirculationFlowNetworks.MOD_ID;
    private static final int MIN_SIZE = 256;
    private static final int MAX_SIZE = 8192;
    private static final int PADDING = 1;

    private CompletableFuture<StitchResult> future;
    private int glTextureId = 0;
    private File cacheDir;
    private boolean init;

    // ── Public API ────────────────────────────────────────────────────────────

    private ComponentAtlas() {
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void configure(File modConfigDir) {
        cacheDir = modConfigDir;
        if (cacheDir != null && !cacheDir.exists()) {
            cacheDir.mkdirs();
        }
    }

    private static StitchResult stitch(List<SpriteData> sprites) {
        if (sprites.isEmpty()) {
            return StitchResult.EMPTY;
        }
        List<SpriteData> sorted = new ObjectArrayList<>(sprites);
        sortSpritesForPacking(sorted);

        List<AtlasDimensions> dimensionsList = candidateDimensions();
        for (AtlasDimensions dimensions : dimensionsList) {
            StitchResult result = tryPack(sorted, dimensions.width, dimensions.height);
            if (result != null) {
                return result;
            }
        }

        CirculationFlowNetworks.LOGGER.error(
            "Sprites exceed maximum atlas size {}×{} within configured bounds! Some may be missing.",
            MAX_SIZE, MAX_SIZE);
        return tryPackForceFit(sorted);
    }

    @Nullable
    private static StitchResult tryPack(List<SpriteData> sprites, int width, int height) {
        List<PackedSprite> placements = packSprites(sprites, width, height, false);
        if (placements == null || placements.size() != sprites.size()) {
            return null;
        }

        BufferedImage atlas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = atlas.createGraphics();
        g.setBackground(new Color(0, 0, 0, 0));
        g.clearRect(0, 0, width, height);

        List<AtlasRegion> regions = new ObjectArrayList<>(placements.size());
        if (placements.isEmpty()) {
            g.dispose();
            return new StitchResult(atlas, regions);
        }
        for (PackedSprite placement : placements) {
            g.drawImage(placement.sprite.image, placement.x, placement.y, null);
            regions.add(new AtlasRegion(
                placement.sprite.name,
                placement.x,
                placement.y,
                placement.sprite.image.getWidth(),
                placement.sprite.image.getHeight(),
                width,
                height));
        }

        g.dispose();
        return new StitchResult(atlas, regions);
    }

    private static StitchResult tryPackForceFit(List<SpriteData> sprites) {
        List<PackedSprite> placements = packSprites(sprites, ComponentAtlas.MAX_SIZE, ComponentAtlas.MAX_SIZE, true);
        BufferedImage atlas = new BufferedImage(ComponentAtlas.MAX_SIZE, ComponentAtlas.MAX_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = atlas.createGraphics();
        g.setBackground(new Color(0, 0, 0, 0));
        g.clearRect(0, 0, ComponentAtlas.MAX_SIZE, ComponentAtlas.MAX_SIZE);

        //noinspection DataFlowIssue
        List<AtlasRegion> regions = new ObjectArrayList<>(placements.size());
        if (placements.isEmpty()) {
            g.dispose();
            return new StitchResult(atlas, regions);
        }
        for (PackedSprite placement : placements) {
            g.drawImage(placement.sprite.image, placement.x, placement.y, null);
            regions.add(new AtlasRegion(
                placement.sprite.name,
                placement.x,
                placement.y,
                placement.sprite.image.getWidth(),
                placement.sprite.image.getHeight(),
                ComponentAtlas.MAX_SIZE,
                ComponentAtlas.MAX_SIZE));
        }

        g.dispose();
        return new StitchResult(atlas, regions);
    }

    private static StitchResult buildRegions(List<SpriteData> sprites, BufferedImage cachedImage) {
        int width = cachedImage.getWidth();
        int height = cachedImage.getHeight();
        List<SpriteData> sorted = new ObjectArrayList<>(sprites);
        sortSpritesForPacking(sorted);
        List<PackedSprite> placements = packSprites(sorted, width, height, false);
        if (placements == null) {
            return StitchResult.EMPTY;
        }

        List<AtlasRegion> regions = new ObjectArrayList<>(placements.size());
        if (placements.isEmpty()) {
            return new StitchResult(cachedImage, regions);
        }
        for (PackedSprite placement : placements) {
            regions.add(new AtlasRegion(
                placement.sprite.name,
                placement.x,
                placement.y,
                placement.sprite.image.getWidth(),
                placement.sprite.image.getHeight(),
                width,
                height));
        }

        return new StitchResult(cachedImage, regions);
    }

    @Nullable
    private static List<PackedSprite> packSprites(List<SpriteData> sprites, int atlasWidth, int atlasHeight, boolean allowSkipping) {
        List<FreeRect> freeRects = new ObjectArrayList<>();
        freeRects.add(new FreeRect(PADDING, PADDING, atlasWidth - PADDING, atlasHeight - PADDING));
        List<PackedSprite> placements = new ObjectArrayList<>(sprites.size());
        if (sprites.isEmpty()) {
            return placements;
        }

        for (SpriteData sprite : sprites) {
            int requiredWidth = sprite.image.getWidth() + PADDING;
            int requiredHeight = sprite.image.getHeight() + PADDING;
            var bestIndex = getBestIndex(freeRects, requiredWidth, requiredHeight);

            if (bestIndex < 0) {
                if (allowSkipping) {
                    CirculationFlowNetworks.LOGGER.warn(
                        "Sprite '{}' ({}×{}) does not fit — skipped.",
                        sprite.name, sprite.image.getWidth(), sprite.image.getHeight());
                    continue;
                }
                return null;
            }

            FreeRect free = freeRects.remove(bestIndex);
            placements.add(new PackedSprite(sprite, free.x, free.y));
            splitFreeRect(freeRects, free, requiredWidth, requiredHeight);
        }

        return placements;
    }

    private static int getBestIndex(List<FreeRect> freeRects, int requiredWidth, int requiredHeight) {
        int bestIndex = -1;
        int bestAreaWaste = Integer.MAX_VALUE;
        int bestShortSideWaste = Integer.MAX_VALUE;

        for (int i = 0, size = freeRects.size(); i < size; i++) {
            FreeRect free = freeRects.get(i);
            if (free.width < requiredWidth || free.height < requiredHeight) {
                continue;
            }
            int areaWaste = free.width * free.height - requiredWidth * requiredHeight;
            int shortSideWaste = Math.min(free.width - requiredWidth, free.height - requiredHeight);
            if (areaWaste < bestAreaWaste || (areaWaste == bestAreaWaste && shortSideWaste < bestShortSideWaste)) {
                bestIndex = i;
                bestAreaWaste = areaWaste;
                bestShortSideWaste = shortSideWaste;
            }
        }
        return bestIndex;
    }

    private static void splitFreeRect(List<FreeRect> freeRects, FreeRect free, int usedWidth, int usedHeight) {
        int remainingWidth = free.width - usedWidth;
        int remainingHeight = free.height - usedHeight;
        if (remainingWidth <= 0 && remainingHeight <= 0) {
            return;
        }

        if (remainingWidth > remainingHeight) {
            addFreeRect(freeRects, free.x + usedWidth, free.y, remainingWidth, free.height);
            addFreeRect(freeRects, free.x, free.y + usedHeight, usedWidth, remainingHeight);
            return;
        }

        addFreeRect(freeRects, free.x + usedWidth, free.y, remainingWidth, usedHeight);
        addFreeRect(freeRects, free.x, free.y + usedHeight, free.width, remainingHeight);
    }

    private static void addFreeRect(List<FreeRect> freeRects, int x, int y, int width, int height) {
        if (width > 0 && height > 0) {
            freeRects.add(new FreeRect(x, y, width, height));
        }
    }

    private static void sortSpritesForPacking(List<SpriteData> sprites) {
        sprites.sort(Comparator
            .comparingInt((SpriteData s) -> Math.max(s.image.getWidth(), s.image.getHeight()))
            .reversed()
            .thenComparingInt(s -> s.image.getWidth() * s.image.getHeight())
            .thenComparingInt(s -> Math.min(s.image.getWidth(), s.image.getHeight()))
            .thenComparing(s -> s.name));
    }

    private static List<AtlasDimensions> candidateDimensions() {
        List<AtlasDimensions> dimensions = new ObjectArrayList<>();
        for (int width = MIN_SIZE; width <= MAX_SIZE; width <<= 1) {
            for (int height = MIN_SIZE; height <= MAX_SIZE; height <<= 1) {
                dimensions.add(new AtlasDimensions(width, height));
            }
        }
        dimensions.sort(Comparator
            .comparingInt((AtlasDimensions d) -> d.width * d.height)
            .thenComparingInt(d -> Math.max(d.width, d.height))
            .thenComparingInt(d -> Math.abs(d.width - d.height))
            .thenComparingInt(d -> d.width));
        return dimensions;
    }

    private static int uploadImage(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = new int[width * height];
        image.getRGB(0, 0, width, height, pixels, 0, width);

        ByteBuffer buf = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder());
        for (int pixel : pixels) {
            buf.put((byte) ((pixel >> 16) & 0xFF));
            buf.put((byte) ((pixel >> 8) & 0xFF));
            buf.put((byte) (pixel & 0xFF));
            buf.put((byte) ((pixel >> 24) & 0xFF));
        }
        buf.flip();

        int texId = GL11.glGenTextures();
        //? if <1.20 {
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.bindTexture(texId);
        //?} else {
        /*GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
         *///?}
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height,
            0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
        //? if <1.20 {
        GlStateManager.bindTexture(0);
        //?} else {
        /*GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
         *///?}

        return texId;
    }

    private static BufferedImage createFallback() {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, 0xFFFFFFFF);
        return img;
    }

    // ── Discovery ─────────────────────────────────────────────────────────────

    private static String computeHash(List<SpriteData> sortedSprites) {
        CRC32 crc = new CRC32();
        if (sortedSprites.isEmpty()) {
            return String.format("%016x", crc.getValue());
        }
        for (SpriteData s : sortedSprites) {
            crc.update(s.name.getBytes(StandardCharsets.UTF_8));
            int w = s.image.getWidth();
            int h = s.image.getHeight();
            int[] pixels = new int[w * h];
            s.image.getRGB(0, 0, w, h, pixels, 0, w);
            ByteBuffer buf = ByteBuffer.allocate(pixels.length * 4);
            for (int pixel : pixels) buf.putInt(pixel);
            crc.update(buf.array());
        }
        return String.format("%016x", crc.getValue());
    }

    /**
     * Starts the background stitching task. Call once during client init and again
     * (via {@link #restart()}) after each resource-pack reload.
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void startAsync(File modConfigDir) {
        configure(modConfigDir);
        if (init) return;
        init = true;
        RegisterComponentSpritesEvent event = new RegisterComponentSpritesEvent();
        // Always register this mod's built-in component sprites directly.
        // The event bus is only for third-party additions.
        GeneratedComponentAtlasRegistration.register(event);
        //? if <1.21 {
        MinecraftForge.EVENT_BUS.post(event);
        //?} else {
        /*NeoForge.EVENT_BUS.post(event);
         *///?}
        List<String> sprites = event.getSprites();
        if (!sprites.isEmpty()) {
            for (String sprite : sprites) addSprite(sprite);
        }

        //? if <1.20 {
        Minecraft mc = Minecraft.getMinecraft();
        //?} else {
        /*Minecraft mc = Minecraft.getInstance();
         *///?}
        String[] names = registeredSpriteNames();

        List<RawSprite> rawSprites = new ObjectArrayList<>();
        for (String name : names) {
            //? if <1.20 {
            ResourceLocation loc = new ResourceLocation(DOMAIN, COMPONENT_DIR + name + ".png");
            try (InputStream is = mc.getResourceManager().getResource(loc).getInputStream()) {
                //?} else if <1.21 {
            /*ResourceLocation loc = new ResourceLocation(DOMAIN, COMPONENT_DIR + name + ".png");
            try (InputStream is = mc.getResourceManager().getResourceOrThrow(loc).open()) {
            *///?} else {
            /*ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(DOMAIN, COMPONENT_DIR + name + ".png");
            try (InputStream is = mc.getResourceManager().getResourceOrThrow(loc).open()) {
            *///?}
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int n;
                while ((n = is.read(chunk)) != -1) baos.write(chunk, 0, n);
                rawSprites.add(new RawSprite(name, baos.toByteArray()));
            } catch (Exception e) {
                CirculationFlowNetworks.LOGGER.warn(
                    "Could not load sprite '{}': {}", name, e.getMessage());
            }
        }

        if (rawSprites.isEmpty()) {
            CirculationFlowNetworks.LOGGER.warn("No sprites found in resource manager!");
            future = CompletableFuture.completedFuture(StitchResult.EMPTY);
            return;
        }

        future = CompletableFuture.supplyAsync(() -> {
            try {
                List<SpriteData> stitchedSprites = new ObjectArrayList<>();
                if (rawSprites.isEmpty()) {
                    return StitchResult.EMPTY;
                }
                for (RawSprite raw : rawSprites) {
                    BufferedImage img = ImageIO.read(new ByteArrayInputStream(raw.bytes));
                    if (img != null) stitchedSprites.add(new SpriteData(raw.name, img));
                }
                if (stitchedSprites.isEmpty()) return StitchResult.EMPTY;

                stitchedSprites.sort(Comparator.comparing(s -> s.name));

                String hash = computeHash(stitchedSprites);
                File cacheFile = new File(cacheDir, "atlas_" + hash + ".png");

                if (cacheFile.exists()) {
                    BufferedImage cached = ImageIO.read(cacheFile);
                    if (cached != null) {
                        StitchResult cachedResult = buildRegions(stitchedSprites, cached);
                        if (cachedResult != StitchResult.EMPTY && !cachedResult.regions.isEmpty()) {
                            return cachedResult;
                        }
                        CirculationFlowNetworks.LOGGER.warn(
                            "Ignoring invalid atlas cache {} and rebuilding.",
                            cacheFile.getAbsolutePath());
                    }
                }

                StitchResult result = stitch(stitchedSprites);

                File[] old = cacheDir.listFiles(f ->
                    f.isFile() && f.getName().startsWith("atlas_") && f.getName().endsWith(".png"));
                if (old != null) {
                    for (File file : old) file.delete();
                }
                try {
                    ImageIO.write(result.image, "PNG", cacheFile);
                } catch (IOException e) {
                    CirculationFlowNetworks.LOGGER.warn(
                        "Could not write atlas cache: {}", e.getMessage());
                }
                return result;

            } catch (Exception e) {
                CirculationFlowNetworks.LOGGER.error("Stitching failed", e);
                return StitchResult.EMPTY;
            }
        });
    }

    /**
     * Rebuilds the atlas after a resource-pack reload.
     */
    public void restart() {
        if (cacheDir != null) {
            clearRegisteredRegions();
            future = null;
            glTextureId = 0;
            init = false;
            startAsync(cacheDir);
        }
    }

    // ── Stitching ─────────────────────────────────────────────────────────────

    /**
     * Blocks until the background task completes, then uploads the atlas to the GPU.
     * Must be called on the GL thread. Safe to call multiple times (no-op after first upload).
     */
    public void awaitReady() {
        if (future == null && cacheDir != null) {
            startAsync(cacheDir);
        }
        if (future == null || glTextureId != 0) return;

        StitchResult result;
        try {
            result = future.join();
        } catch (Exception e) {
            CirculationFlowNetworks.LOGGER.error("Failed to obtain atlas", e);
            return;
        }

        if (result == StitchResult.EMPTY || result.image == null) {
            glTextureId = uploadImage(createFallback());
        } else {
            glTextureId = uploadImage(result.image);
            replaceRegions(result.regions);
        }
    }

    /**
     * Binds the atlas GL texture for subsequent draw calls. No-op if not yet uploaded.
     */
    public void bind() {
        if (glTextureId != 0) {
            //? if <1.20 {
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.bindTexture(glTextureId);
            //?} else {
            /*RenderSystem.setShaderTexture(0, glTextureId);
             *///?}
        }
    }

    /**
     * Returns {@code true} once the atlas has been uploaded to the GPU.
     */
    public boolean isReady() {
        return glTextureId != 0;
    }

    // ── GL helpers ────────────────────────────────────────────────────────────

    /**
     * Releases the GL texture and resets all state.
     */
    public void dispose() {
        if (glTextureId != 0) {
            GL11.glDeleteTextures(glTextureId);
            glTextureId = 0;
        }
        clearRegisteredRegions();
        future = null;
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    //? if =1.12.2 {
    @Desugar
        //?}
    private record RawSprite(String name, byte[] bytes) {
    }

    //? if =1.12.2 {
    @Desugar
        //?}
    private record SpriteData(String name, BufferedImage image) {
    }

    //? if =1.12.2 {
    @Desugar
        //?}
    private record PackedSprite(SpriteData sprite, int x, int y) {
    }

    //? if =1.12.2 {
    @Desugar
        //?}
    private record FreeRect(int x, int y, int width, int height) {
    }

    //? if =1.12.2 {
    @Desugar
        //?}
    private record AtlasDimensions(int width, int height) {
    }

    //? if =1.12.2 {
    @Desugar
        //?}
    record StitchResult(BufferedImage image, List<AtlasRegion> regions) {
        static final StitchResult EMPTY = new StitchResult(null, ObjectLists.emptyList());
    }
}
