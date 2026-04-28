package com.circulation.circulation_networks.client.render;

import com.circulation.circulation_networks.CFNConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.NonNull;

import java.util.function.Consumer;

import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.CHARGING_IN_BASE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.CHARGING_IN_EMISSIVE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.CHARGING_RING_BASE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.CHARGING_RING_EMISSIVE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.CHARGING_STATIC;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.NODE_CRYSTAL;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.PEDESTAL_BASE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.PEDESTAL_BASE_EMISSIVE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.PEDESTAL_FRAME_CLOCKWISE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.PEDESTAL_FRAME_COUNTER_CLOCKWISE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.PEDESTAL_STATIC;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.PORT_IN_BASE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.PORT_IN_EMISSIVE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.PORT_OUT_BASE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.PORT_OUT_EMISSIVE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.PORT_STATIC;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.RELAY_BOTTOM_SPIRAL_BASE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.RELAY_BOTTOM_SPIRAL_EMISSIVE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.RELAY_STATIC;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.RELAY_TOP_SPIRAL_BASE;
import static com.circulation.circulation_networks.client.render.RotatingBlockModelCache.RELAY_TOP_SPIRAL_EMISSIVE;

public final class AnimatedNodeSpecialRenderer implements NoDataSpecialModelRenderer {

    public static final Identifier TYPE_ID = Identifier.fromNamespaceAndPath("circulation_networks", "animated_node");
    private static final float CENTER = 0.5F;
    private static final float FRAME_PIVOT_X = 8.0F / 16.0F;
    private static final float FRAME_PIVOT_Y = 5.0F / 16.0F;
    private static final float FRAME_PIVOT_Z = 8.0F / 16.0F;
    private static final Vector3fc[] UNIT_EXTENTS = {
        new Vector3f(0.0F, 0.0F, 0.0F),
        new Vector3f(1.0F, 0.0F, 0.0F),
        new Vector3f(0.0F, 1.0F, 0.0F),
        new Vector3f(0.0F, 0.0F, 1.0F),
        new Vector3f(1.0F, 1.0F, 0.0F),
        new Vector3f(1.0F, 0.0F, 1.0F),
        new Vector3f(0.0F, 1.0F, 1.0F),
        new Vector3f(1.0F, 1.0F, 1.0F)
    };

    private final Kind kind;

    public AnimatedNodeSpecialRenderer(Kind kind) {
        this.kind = kind;
    }

    private static AnimationTick resolveAnimationTick() {
        Minecraft minecraft = Minecraft.getInstance();
        return new AnimationTick(
            ClientAnimationTicker.ticks(),
            minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false)
        );
    }

    @Override
    public void submit(@NotNull PoseStack poseStack, @NotNull SubmitNodeCollector submitNodeCollector, int lightCoords, int overlayCoords, boolean hasFoil, int outlineColor) {
        AnimationTick tick = resolveAnimationTick();
        try (RotatingModelVBORenderer.RenderSession ignored = RotatingModelVBORenderer.beginRenderSession(submitNodeCollector)) {
            kind.render(poseStack, lightCoords, tick.clientTicks(), tick.partialTicks());
        }
    }

    @Override
    public void getExtents(@NotNull Consumer<Vector3fc> output) {
        for (Vector3fc extent : UNIT_EXTENTS) {
            output.accept(extent);
        }
    }

    public enum Kind {
        RELAY("relay") {
            @Override
            void render(PoseStack poseStack, int lightCoords, long clientTicks, float partialTicks) {
                if (!CFNConfig.NODE.rendering.animatedSpecialModels) {
                    RotatingModelVBORenderer.renderLitYAxis(poseStack, lightCoords, RELAY_STATIC, 0.0F, CENTER, CENTER, CENTER);
                    return;
                }

                float topAngle = NodeRotationAnimation.relayTopSpiralAngle(clientTicks, partialTicks);
                float crystalAngle = NodeRotationAnimation.relayCrystalAngle(clientTicks, partialTicks);
                float bottomAngle = NodeRotationAnimation.relayBottomSpiralAngle(clientTicks, partialTicks);

                RotatingModelVBORenderer.renderLitYAxis(poseStack, lightCoords, RELAY_TOP_SPIRAL_BASE, topAngle, CENTER, CENTER, CENTER);
                RotatingModelVBORenderer.renderFullBrightYAxis(poseStack, RELAY_TOP_SPIRAL_EMISSIVE, topAngle, CENTER, CENTER, CENTER);
                poseStack.pushPose();
                poseStack.translate(0.0F, NodeRotationAnimation.bobOffset(clientTicks, partialTicks), 0.0F);
                RotatingModelVBORenderer.renderFullBrightYAxis(poseStack, NODE_CRYSTAL, crystalAngle, CENTER, CENTER, CENTER);
                poseStack.popPose();
                RotatingModelVBORenderer.renderLitYAxis(poseStack, lightCoords, RELAY_BOTTOM_SPIRAL_BASE, bottomAngle, CENTER, CENTER, CENTER);
                RotatingModelVBORenderer.renderFullBrightYAxis(poseStack, RELAY_BOTTOM_SPIRAL_EMISSIVE, bottomAngle, CENTER, CENTER, CENTER);
            }
        },
        CHARGING("charging") {
            @Override
            void render(PoseStack poseStack, int lightCoords, long clientTicks, float partialTicks) {
                if (!CFNConfig.NODE.rendering.animatedSpecialModels) {
                    RotatingModelVBORenderer.renderLitYAxis(poseStack, lightCoords, CHARGING_STATIC, 0.0F, CENTER, CENTER, CENTER);
                    return;
                }

                float topAngle = NodeRotationAnimation.relayBottomSpiralAngle(clientTicks, partialTicks);
                float crystalAngle = NodeRotationAnimation.relayCrystalAngle(clientTicks, partialTicks);
                float bottomAngle = NodeRotationAnimation.relayBottomSpiralAngle(clientTicks, partialTicks);

                RotatingModelVBORenderer.renderLitYAxis(poseStack, lightCoords, CHARGING_IN_BASE, topAngle, CENTER, CENTER, CENTER);
                RotatingModelVBORenderer.renderFullBrightYAxis(poseStack, CHARGING_IN_EMISSIVE, topAngle, CENTER, CENTER, CENTER);
                RotatingModelVBORenderer.renderFullBrightYAxis(poseStack, NODE_CRYSTAL, crystalAngle, CENTER, CENTER, CENTER);
                RotatingModelVBORenderer.renderLitYAxis(poseStack, lightCoords, CHARGING_RING_BASE, bottomAngle, CENTER, CENTER, CENTER);
                RotatingModelVBORenderer.renderFullBrightYAxis(poseStack, CHARGING_RING_EMISSIVE, bottomAngle, CENTER, CENTER, CENTER);
            }
        },
        PORT("port") {
            @Override
            void render(PoseStack poseStack, int lightCoords, long clientTicks, float partialTicks) {
                if (!CFNConfig.NODE.rendering.animatedSpecialModels) {
                    RotatingModelVBORenderer.renderLitYAxis(poseStack, lightCoords, PORT_STATIC, 0.0F, CENTER, CENTER, CENTER);
                    return;
                }

                float topAngle = NodeRotationAnimation.relayBottomSpiralAngle(clientTicks, partialTicks);
                float crystalAngle = NodeRotationAnimation.relayCrystalAngle(clientTicks, partialTicks);
                float bottomAngle = NodeRotationAnimation.relayTopSpiralAngle(clientTicks, partialTicks);

                RotatingModelVBORenderer.renderLitYAxis(poseStack, lightCoords, PORT_IN_BASE, topAngle, CENTER, CENTER, CENTER);
                RotatingModelVBORenderer.renderFullBrightYAxis(poseStack, PORT_IN_EMISSIVE, topAngle, CENTER, CENTER, CENTER);
                RotatingModelVBORenderer.renderFullBrightYAxis(poseStack, NODE_CRYSTAL, crystalAngle, CENTER, CENTER, CENTER);
                RotatingModelVBORenderer.renderLitYAxis(poseStack, lightCoords, PORT_OUT_BASE, bottomAngle, CENTER, CENTER, CENTER);
                RotatingModelVBORenderer.renderFullBrightYAxis(poseStack, PORT_OUT_EMISSIVE, bottomAngle, CENTER, CENTER, CENTER);
            }
        },
        PEDESTAL("pedestal") {
            @Override
            void render(PoseStack poseStack, int lightCoords, long clientTicks, float partialTicks) {
                if (!CFNConfig.NODE.rendering.animatedSpecialModels) {
                    RotatingModelVBORenderer.renderLitYAxis(poseStack, lightCoords, PEDESTAL_STATIC, 0.0F, CENTER, CENTER, CENTER);
                    return;
                }

                RotatingModelVBORenderer.renderLitYAxis(poseStack, lightCoords, PEDESTAL_BASE, 0.0F, CENTER, CENTER, CENTER);
                RotatingModelVBORenderer.renderFullBrightYAxis(poseStack, PEDESTAL_BASE_EMISSIVE, 0.0F, CENTER, CENTER, CENTER);
                RotatingModelVBORenderer.renderFullBright(
                    poseStack,
                    PEDESTAL_FRAME_CLOCKWISE,
                    NodeRotationAnimation.pedestalClockwiseFrameAngle(clientTicks, partialTicks),
                    FRAME_PIVOT_X,
                    FRAME_PIVOT_Y,
                    FRAME_PIVOT_Z,
                    NodeRotationAnimation.tiltedAxisXForZRotation(-22.5F),
                    NodeRotationAnimation.tiltedAxisY(),
                    NodeRotationAnimation.tiltedAxisZ()
                );
                RotatingModelVBORenderer.renderFullBright(
                    poseStack,
                    PEDESTAL_FRAME_COUNTER_CLOCKWISE,
                    NodeRotationAnimation.pedestalCounterClockwiseFrameAngle(clientTicks, partialTicks),
                    FRAME_PIVOT_X,
                    FRAME_PIVOT_Y,
                    FRAME_PIVOT_Z,
                    NodeRotationAnimation.tiltedAxisXForZRotation(22.5F),
                    NodeRotationAnimation.tiltedAxisY(),
                    NodeRotationAnimation.tiltedAxisZ()
                );
            }
        };

        static final Codec<Kind> CODEC = Codec.STRING.xmap(Kind::bySerializedName, Kind::serializedName);

        private final String serializedName;

        Kind(String serializedName) {
            this.serializedName = serializedName;
        }

        private static Kind bySerializedName(String value) {
            for (Kind kind : values()) {
                if (kind.serializedName.equals(value)) {
                    return kind;
                }
            }
            throw new IllegalArgumentException("Unknown animated node renderer kind: " + value);
        }

        public String serializedName() {
            return serializedName;
        }

        abstract void render(PoseStack poseStack, int lightCoords, long clientTicks, float partialTicks);
    }

    private record AnimationTick(long clientTicks, float partialTicks) {
    }

    public record Unbaked(Kind kind) implements NoDataSpecialModelRenderer.Unbaked {

        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Kind.CODEC.fieldOf("kind").forGetter(Unbaked::kind)
        ).apply(instance, Unbaked::new));

        @Override
        public @NotNull MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public @NonNull SpecialModelRenderer<Void> bake(@NotNull SpecialModelRenderer.BakingContext context) {
            return new AnimatedNodeSpecialRenderer(kind);
        }
    }
}
