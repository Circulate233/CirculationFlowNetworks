package com.circulation.circulation_networks.gui.component.base;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

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
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.zip.CRC32;

/**
 * Shared atlas implementation that keeps the packing, caching and upload logic readable
 * in one place while version-specific subclasses provide the platform hooks.
 */
public abstract class ComponentAtlasBase extends ComponentAtlasRegistry {

    protected static final String COMPONENT_DIR = "textures/gui/component/";
    protected static final String DOMAIN = CirculationFlowNetworks.MOD_ID;
    protected static final int MIN_SIZE = 256;
    protected static final int MAX_SIZE = 8192;
    protected static final int PADDING = 1;

    protected CompletableFuture<StitchResult> future;
    protected File cacheDir;
    protected boolean init;

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
            MAX_SIZE,
            MAX_SIZE
        );
        return tryPackForceFit(sorted);
    }

    @Nullable
    private static StitchResult tryPack(List<SpriteData> sprites, int width, int height) {
        List<PackedSprite> placements = packSprites(sprites, width, height, false);
        if (placements == null || placements.size() != sprites.size()) {
            return null;
        }

        BufferedImage atlas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = atlas.createGraphics();
        graphics.setBackground(new Color(0, 0, 0, 0));
        graphics.clearRect(0, 0, width, height);

        List<AtlasRegion> regions = new ObjectArrayList<>(placements.size());
        if (placements.isEmpty()) {
            graphics.dispose();
            return new StitchResult(atlas, regions);
        }

        for (PackedSprite placement : placements) {
            graphics.drawImage(placement.sprite.image, placement.x, placement.y, null);
            regions.add(new AtlasRegion(
                placement.sprite.name,
                placement.x,
                placement.y,
                placement.sprite.image.getWidth(),
                placement.sprite.image.getHeight(),
                width,
                height
            ));
        }

        graphics.dispose();
        return new StitchResult(atlas, regions);
    }

    private static StitchResult tryPackForceFit(List<SpriteData> sprites) {
        List<PackedSprite> placements = packSprites(sprites, MAX_SIZE, MAX_SIZE, true);
        BufferedImage atlas = new BufferedImage(MAX_SIZE, MAX_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = atlas.createGraphics();
        graphics.setBackground(new Color(0, 0, 0, 0));
        graphics.clearRect(0, 0, MAX_SIZE, MAX_SIZE);

        List<AtlasRegion> regions = new ObjectArrayList<>(placements.size());
        if (placements.isEmpty()) {
            graphics.dispose();
            return new StitchResult(atlas, regions);
        }

        for (PackedSprite placement : placements) {
            graphics.drawImage(placement.sprite.image, placement.x, placement.y, null);
            regions.add(new AtlasRegion(
                placement.sprite.name,
                placement.x,
                placement.y,
                placement.sprite.image.getWidth(),
                placement.sprite.image.getHeight(),
                MAX_SIZE,
                MAX_SIZE
            ));
        }

        graphics.dispose();
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
                height
            ));
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
            int bestIndex = getBestIndex(freeRects, requiredWidth, requiredHeight);
            if (bestIndex < 0) {
                if (allowSkipping) {
                    CirculationFlowNetworks.LOGGER.warn(
                        "Sprite '{}' ({}×{}) does not fit — skipped.",
                        sprite.name,
                        sprite.image.getWidth(),
                        sprite.image.getHeight()
                    );
                    continue;
                }
                return null;
            }

            FreeRect freeRect = freeRects.remove(bestIndex);
            placements.add(new PackedSprite(sprite, freeRect.x, freeRect.y));
            splitFreeRect(freeRects, freeRect, requiredWidth, requiredHeight);
        }

        return placements;
    }

    private static int getBestIndex(List<FreeRect> freeRects, int requiredWidth, int requiredHeight) {
        int bestIndex = -1;
        int bestAreaWaste = Integer.MAX_VALUE;
        int bestShortSideWaste = Integer.MAX_VALUE;

        for (int i = 0, size = freeRects.size(); i < size; i++) {
            FreeRect freeRect = freeRects.get(i);
            if (freeRect.width < requiredWidth || freeRect.height < requiredHeight) {
                continue;
            }

            int areaWaste = freeRect.width * freeRect.height - requiredWidth * requiredHeight;
            int shortSideWaste = Math.min(freeRect.width - requiredWidth, freeRect.height - requiredHeight);
            if (areaWaste < bestAreaWaste || (areaWaste == bestAreaWaste && shortSideWaste < bestShortSideWaste)) {
                bestIndex = i;
                bestAreaWaste = areaWaste;
                bestShortSideWaste = shortSideWaste;
            }
        }

        return bestIndex;
    }

    private static void splitFreeRect(List<FreeRect> freeRects, FreeRect freeRect, int usedWidth, int usedHeight) {
        int remainingWidth = freeRect.width - usedWidth;
        int remainingHeight = freeRect.height - usedHeight;
        if (remainingWidth <= 0 && remainingHeight <= 0) {
            return;
        }

        if (remainingWidth > remainingHeight) {
            addFreeRect(freeRects, freeRect.x + usedWidth, freeRect.y, remainingWidth, freeRect.height);
            addFreeRect(freeRects, freeRect.x, freeRect.y + usedHeight, usedWidth, remainingHeight);
            return;
        }

        addFreeRect(freeRects, freeRect.x + usedWidth, freeRect.y, remainingWidth, usedHeight);
        addFreeRect(freeRects, freeRect.x, freeRect.y + usedHeight, freeRect.width, remainingHeight);
    }

    private static void addFreeRect(List<FreeRect> freeRects, int x, int y, int width, int height) {
        if (width > 0 && height > 0) {
            freeRects.add(new FreeRect(x, y, width, height));
        }
    }

    private static void sortSpritesForPacking(List<SpriteData> sprites) {
        sprites.sort(Comparator
            .comparingInt((SpriteData sprite) -> Math.max(sprite.image.getWidth(), sprite.image.getHeight()))
            .reversed()
            .thenComparingInt(sprite -> sprite.image.getWidth() * sprite.image.getHeight())
            .thenComparingInt(sprite -> Math.min(sprite.image.getWidth(), sprite.image.getHeight()))
            .thenComparing(sprite -> sprite.name)
        );
    }

    private static List<AtlasDimensions> candidateDimensions() {
        List<AtlasDimensions> dimensions = new ObjectArrayList<>();
        for (int width = MIN_SIZE; width <= MAX_SIZE; width <<= 1) {
            for (int height = MIN_SIZE; height <= MAX_SIZE; height <<= 1) {
                dimensions.add(new AtlasDimensions(width, height));
            }
        }
        dimensions.sort(Comparator
            .comparingInt((AtlasDimensions value) -> value.width * value.height)
            .thenComparingInt(value -> Math.max(value.width, value.height))
            .thenComparingInt(value -> Math.abs(value.width - value.height))
            .thenComparingInt(value -> value.width)
        );
        return dimensions;
    }

    private static BufferedImage createFallback() {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFFFFFFFF);
        return image;
    }

    private static String computeHash(List<SpriteData> sortedSprites) {
        CRC32 crc = new CRC32();
        if (sortedSprites.isEmpty()) {
            return String.format("%016x", crc.getValue());
        }

        for (SpriteData sprite : sortedSprites) {
            crc.update(sprite.name.getBytes(StandardCharsets.UTF_8));
            int width = sprite.image.getWidth();
            int height = sprite.image.getHeight();
            int[] pixels = new int[width * height];
            sprite.image.getRGB(0, 0, width, height, pixels, 0, width);
            ByteBuffer buffer = ByteBuffer.allocate(pixels.length * 4);
            for (int pixel : pixels) {
                buffer.putInt(pixel);
            }
            crc.update(buffer.array());
        }

        return String.format("%016x", crc.getValue());
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void configure(File modConfigDir) {
        cacheDir = modConfigDir;
        if (cacheDir != null && !cacheDir.exists()) {
            cacheDir.mkdirs();
        }
    }

    /**
     * Starts the background stitching task. Call once during client init and again
     * (via {@link #restart()}) after each resource-pack reload.
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void startAsync(File modConfigDir) {
        configure(modConfigDir);
        if (init) {
            return;
        }
        init = true;

        RegisterComponentSpritesEvent event = new RegisterComponentSpritesEvent();
        GeneratedComponentAtlasRegistration.register(event);
        postRegisterComponentSprites(event);

        List<String> sprites = event.getSprites();
        if (!sprites.isEmpty()) {
            for (String sprite : sprites) {
                addSprite(sprite);
            }
        }

        Minecraft mc = minecraft();
        String[] names = registeredSpriteNames();

        List<RawSprite> rawSprites = new ObjectArrayList<>();
        for (String name : names) {
            try (InputStream inputStream = openSpriteStream(mc, name)) {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int read;
                while ((read = inputStream.read(chunk)) != -1) {
                    outputStream.write(chunk, 0, read);
                }
                rawSprites.add(new RawSprite(name, outputStream.toByteArray()));
            } catch (Exception e) {
                CirculationFlowNetworks.LOGGER.warn(
                    "Could not load sprite '{}': {}",
                    name,
                    e.getMessage()
                );
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
                for (RawSprite rawSprite : rawSprites) {
                    BufferedImage image = ImageIO.read(new ByteArrayInputStream(rawSprite.bytes));
                    if (image != null) {
                        stitchedSprites.add(new SpriteData(rawSprite.name, image));
                    }
                }
                if (stitchedSprites.isEmpty()) {
                    return StitchResult.EMPTY;
                }

                stitchedSprites.sort(Comparator.comparing(sprite -> sprite.name));

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
                            cacheFile.getAbsolutePath()
                        );
                    }
                }

                StitchResult result = stitch(stitchedSprites);

                File[] oldCaches = cacheDir.listFiles(file ->
                    file.isFile() && file.getName().startsWith("atlas_") && file.getName().endsWith(".png")
                );
                if (oldCaches != null) {
                    for (File file : oldCaches) {
                        file.delete();
                    }
                }

                try {
                    ImageIO.write(result.image, "PNG", cacheFile);
                } catch (IOException e) {
                    CirculationFlowNetworks.LOGGER.warn(
                        "Could not write atlas cache: {}",
                        e.getMessage()
                    );
                }
                return result;
            } catch (Exception e) {
                CirculationFlowNetworks.LOGGER.error("Stitching failed", e);
                return StitchResult.EMPTY;
            }
        });
    }

    public void restart() {
        if (cacheDir != null) {
            clearRegisteredRegions();
            future = null;
            releaseUploadedTexture();
            init = false;
            startAsync(cacheDir);
        }
    }

    /**
     * Blocks until the background task completes, then uploads the atlas to the GPU.
     * Must be called on the GL thread. Safe to call multiple times.
     */
    public void awaitReady() {
        if (future == null && cacheDir != null) {
            startAsync(cacheDir);
        }
        if (future == null || hasUploadedTexture()) {
            return;
        }

        StitchResult result;
        try {
            result = future.join();
        } catch (Exception e) {
            CirculationFlowNetworks.LOGGER.error("Failed to obtain atlas", e);
            return;
        }

        if (result == StitchResult.EMPTY || result.image == null) {
            uploadAtlasImage(createFallback());
        } else {
            uploadAtlasImage(result.image);
            replaceRegions(result.regions);
        }
    }

    public void bind() {
        if (hasUploadedTexture()) {
            bindUploadedTexture();
        }
    }

    public boolean isReady() {
        return hasUploadedTexture();
    }

    public void dispose() {
        releaseUploadedTexture();
        clearRegisteredRegions();
        future = null;
    }

    protected abstract Minecraft minecraft();

    protected abstract void postRegisterComponentSprites(RegisterComponentSpritesEvent event);

    protected abstract InputStream openSpriteStream(Minecraft minecraft, String spriteName) throws IOException;

    protected abstract void uploadAtlasImage(BufferedImage image);

    protected abstract boolean hasUploadedTexture();

    protected abstract void bindUploadedTexture();

    protected abstract void releaseUploadedTexture();

    protected static final class RawSprite {
        private final String name;
        private final byte[] bytes;

        private RawSprite(String name, byte[] bytes) {
            this.name = name;
            this.bytes = bytes;
        }
    }

    protected static final class SpriteData {
        private final String name;
        private final BufferedImage image;

        private SpriteData(String name, BufferedImage image) {
            this.name = name;
            this.image = image;
        }
    }

    protected static final class PackedSprite {
        private final SpriteData sprite;
        private final int x;
        private final int y;

        private PackedSprite(SpriteData sprite, int x, int y) {
            this.sprite = sprite;
            this.x = x;
            this.y = y;
        }
    }

    protected static final class FreeRect {
        private final int x;
        private final int y;
        private final int width;
        private final int height;

        private FreeRect(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    protected static final class AtlasDimensions {
        private final int width;
        private final int height;

        private AtlasDimensions(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    protected static class StitchResult {
        protected static final StitchResult EMPTY = new StitchResult(null, ObjectLists.emptyList());

        private final BufferedImage image;
        private final List<AtlasRegion> regions;

        private StitchResult(BufferedImage image, List<AtlasRegion> regions) {
            this.image = image;
            this.regions = regions;
        }
    }
}
