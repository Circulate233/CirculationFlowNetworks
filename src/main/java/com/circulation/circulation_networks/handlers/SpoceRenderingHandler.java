package com.circulation.circulation_networks.handlers;

import com.circulation.circulation_networks.items.ItemInspectionTool;
import com.circulation.circulation_networks.registry.RegistryItems;
import com.circulation.circulation_networks.utils.BuckyBallGeometry;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.util.Arrays;
import java.util.List;

@SideOnly(Side.CLIENT)
public final class SpoceRenderingHandler {

    public static final SpoceRenderingHandler INSTANCE = new SpoceRenderingHandler();
    private static final float[] EMPTY_VERTS = new float[0];
    private static final int BUILD_BUF_SIZE = 1 << 17;
    private static final int RESCAN_INTERVAL = 3;
    private final float[] buildBuf = new float[BUILD_BUF_SIZE];
    private final double[] angleScratch = new double[9];
    private TileEntity te;
    private float linkScope;
    private float energyScope;
    private float chargingScope;
    private float lastAnimProgress;
    private float animProgress;
    private float[] rs;
    private int buildCount;
    private float[] linkVerts = EMPTY_VERTS;
    private float[] energyVerts = EMPTY_VERTS;
    private float[] chargingVerts = EMPTY_VERTS;
    private boolean linkDirty = false;
    private boolean energyDirty = false;
    private boolean chargingDirty = false;
    private int tickCounter = 0;

    private static float bright(float v) {
        return Math.min(1.0f, v * 1.3f);
    }

    private static float easeOutCubic(float t) {
        return (float) (1.0 - Math.pow(1.0 - t, 3));
    }

    private static void drawCachedIntersection(float[] verts, float r, float g, float b) {
        if (verts.length == 0) return;
        GlStateManager.color(r, g, b, 1.0f);
        GlStateManager.glLineWidth(4.0f);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION);
        for (int i = 0; i < verts.length; i += 3) {
            buf.pos(verts[i], verts[i + 1], verts[i + 2]).endVertex();
        }
        tess.draw();
    }

    private static void draw(float rotation, float r, float g, float b, float radius, float r1, float g1, float b1) {
        GlStateManager.pushMatrix();
        drawSphere(r, g, b, radius, 0.2f);
        GlStateManager.rotate(rotation, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(rotation * 0.5F, 1.0F, 0.0F, 0.0F);
        drawBuckyBallWireframe(r1, g1, b1, radius + 0.01f, 0.8f);
        GlStateManager.popMatrix();
    }

    private static void drawSphere(float r, float g, float b, float radius, float alpha) {
        GlStateManager.color(r, g, b, alpha);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        final int slices = 32, stacks = 32;
        for (int i = 0; i < slices; i++) {
            double phi1 = Math.PI * i / slices;
            double phi2 = Math.PI * (i + 1) / slices;
            buf.begin(GL11.GL_QUAD_STRIP, DefaultVertexFormats.POSITION_NORMAL);
            for (int j = 0; j <= stacks; j++) {
                double theta = 2.0 * Math.PI * j / stacks;
                float x1 = (float) (radius * Math.sin(phi1) * Math.cos(theta));
                float y1 = (float) (radius * Math.cos(phi1));
                float z1 = (float) (radius * Math.sin(phi1) * Math.sin(theta));
                buf.pos(x1, y1, z1).normal(x1 / radius, y1 / radius, z1 / radius).endVertex();
                float x2 = (float) (radius * Math.sin(phi2) * Math.cos(theta));
                float y2 = (float) (radius * Math.cos(phi2));
                float z2 = (float) (radius * Math.sin(phi2) * Math.sin(theta));
                buf.pos(x2, y2, z2).normal(x2 / radius, y2 / radius, z2 / radius).endVertex();
            }
            tess.draw();
        }
    }

    private static void drawBuckyBallWireframe(float r, float g, float b, float radius, float alpha) {
        GlStateManager.color(r, g, b, alpha);
        GlStateManager.glLineWidth(2.0f);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_NORMAL);
        for (int[] edge : BuckyBallGeometry.edges) {
            Vec3d v1 = BuckyBallGeometry.vertices.get(edge[0]);
            Vec3d v2 = BuckyBallGeometry.vertices.get(edge[1]);
            buf.pos(v1.x * radius, v1.y * radius, v1.z * radius).normal((float) v1.x, (float) v1.y, (float) v1.z).endVertex();
            buf.pos(v2.x * radius, v2.y * radius, v2.z * radius).normal((float) v2.x, (float) v2.y, (float) v2.z).endVertex();
        }
        tess.draw();
    }

    private static int addCos(double[] buf, int count, double val) {
        if (val < -1.0 - 1E-9 || val > 1.0 + 1E-9) return count;
        val = val < -1.0 ? -1.0 : Math.min(val, 1.0);
        double a = Math.acos(val);
        buf[count++] = normalizeAngle(a);
        buf[count++] = normalizeAngle(-a);
        return count;
    }

    private static int addSin(double[] buf, int count, double val) {
        if (val < -1.0 - 1E-9 || val > 1.0 + 1E-9) return count;
        val = val < -1.0 ? -1.0 : Math.min(val, 1.0);
        double a = Math.asin(val);
        buf[count++] = normalizeAngle(a);
        buf[count++] = normalizeAngle(Math.PI - a);
        return count;
    }

    private static double normalizeAngle(double a) {
        a %= Math.PI * 2.0;
        return a < 0 ? a + Math.PI * 2.0 : a;
    }

    private static void sortAngles(double[] buf, int count) {
        for (int i = 1; i < count; i++) {
            double key = buf[i];
            int j = i - 1;
            while (j >= 0 && buf[j] > key) {
                buf[j + 1] = buf[j];
                j--;
            }
            buf[j + 1] = key;
        }
    }

    public void setStaus(TileEntity te, double linkScope, double energyScope, double chargingScope) {
        this.te = te;
        this.linkScope = (float) linkScope;
        this.energyScope = (float) energyScope;
        this.chargingScope = (float) chargingScope;
        this.animProgress = 0;
        this.lastAnimProgress = 0;

        Integer[] indices = {0, 1, 2};
        float[] scopes = {this.linkScope, this.energyScope, this.chargingScope};
        Arrays.sort(indices, (a, b) -> Float.compare(scopes[b], scopes[a]));
        this.rs = new float[3];
        this.rs[indices[0]] = 1.0f;
        this.rs[indices[1]] = -1.0f;
        this.rs[indices[2]] = 1.0f;

        linkDirty = energyDirty = chargingDirty = true;
    }

    private boolean isAnimating() {
        return animProgress < 1.0f;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || te == null) return;

        lastAnimProgress = animProgress;
        if (animProgress < 1.0f) {
            animProgress = Math.min(animProgress + 0.025f, 1.0f);
        }

        if (!isAnimating()) {
            tickCounter++;
            if (tickCounter >= RESCAN_INTERVAL) {
                tickCounter = 0;
                linkDirty = energyDirty = chargingDirty = true;
            }
        }
    }

    private void drawIntersectionImmediate(World world, float radius, float r, float g, float b) {
        if (radius <= 0.1f) return;
        float[] verts = buildIntersectionGeometry(world, radius);
        drawCachedIntersection(verts, r, g, b);
    }

    private float[] buildIntersectionGeometry(World world, double radius) {
        buildCount = 0;
        int radInt = (int) Math.ceil(radius);
        double inner = Math.max(0.0, radius - 1.0);
        double outerSq = (radius + 1.0) * (radius + 1.0);
        double innerSq = inner * inner;
        BlockPos center = te.getPos();

        double cx = center.getX() + 0.5;
        double cy = center.getY() + 0.5;
        double cz = center.getZ() + 0.5;

        for (int dx = -radInt; dx <= radInt; dx++) {
            double dx2 = (double) dx * dx;
            if (dx2 > outerSq) continue;
            for (int dy = -radInt; dy <= radInt; dy++) {
                double dy2 = (double) dy * dy;
                if (dx2 + dy2 > outerSq) continue;
                for (int dz = -radInt; dz <= radInt; dz++) {
                    double distSq = dx2 + dy2 + (double) dz * dz;
                    if (distSq > outerSq || distSq < innerSq) continue;

                    BlockPos worldBP = center.add(dx, dy, dz);
                    IBlockState state = world.getBlockState(worldBP);
                    if (state.getBlock().isAir(state, world, worldBP)) continue;

                    double mathCellMinX = dx - 0.5;
                    double mathCellMaxX = dx + 0.5;
                    double mathCellMinY = dy - 0.5;
                    double mathCellMaxY = dy + 0.5;
                    double mathCellMinZ = dz - 0.5;
                    double mathCellMaxZ = dz + 0.5;

                    List<AxisAlignedBB> boxes = new ObjectArrayList<>();
                    state.addCollisionBoxToList(world, worldBP, new AxisAlignedBB(worldBP), boxes, null, false);

                    if (boxes.isEmpty()) {
                        AxisAlignedBB bb = state.getBoundingBox(world, worldBP);
                        if (bb != null && bb != Block.NULL_AABB) {
                            boxes.add(bb.offset(worldBP));
                        }
                    }

                    for (AxisAlignedBB bb : boxes) {
                        AxisAlignedBB mathBox = new AxisAlignedBB(
                            bb.minX - cx, bb.minY - cy, bb.minZ - cz,
                            bb.maxX - cx, bb.maxY - cy, bb.maxZ - cz
                        );

                        for (EnumFacing face : EnumFacing.VALUES) {
                            boolean isFlush = switch (face) {
                                case WEST -> Math.abs(mathBox.minX - mathCellMinX) < 1E-5;
                                case EAST -> Math.abs(mathBox.maxX - mathCellMaxX) < 1E-5;
                                case DOWN -> Math.abs(mathBox.minY - mathCellMinY) < 1E-5;
                                case UP -> Math.abs(mathBox.maxY - mathCellMaxY) < 1E-5;
                                case NORTH -> Math.abs(mathBox.minZ - mathCellMinZ) < 1E-5;
                                case SOUTH -> Math.abs(mathBox.maxZ - mathCellMaxZ) < 1E-5;
                            };

                            boolean shouldDraw = true;
                            if (isFlush) {
                                if (world.getBlockState(worldBP.offset(face)).isOpaqueCube()) {
                                    shouldDraw = false;
                                }
                            }

                            if (shouldDraw) {
                                appendAABBFace(face, mathBox, radius);
                            }
                        }
                    }
                }
            }
        }

        return Arrays.copyOf(buildBuf, buildCount);
    }

    private void appendAABBFace(EnumFacing face, AxisAlignedBB box, double R) {
        double planeW, minU, maxU, minV, maxV;
        switch (face) {
            case UP:
                planeW = box.maxY;
                minU = box.minX;
                maxU = box.maxX;
                minV = box.minZ;
                maxV = box.maxZ;
                break;
            case DOWN:
                planeW = box.minY;
                minU = box.minX;
                maxU = box.maxX;
                minV = box.minZ;
                maxV = box.maxZ;
                break;
            case SOUTH:
                planeW = box.maxZ;
                minU = box.minX;
                maxU = box.maxX;
                minV = box.minY;
                maxV = box.maxY;
                break;
            case NORTH:
                planeW = box.minZ;
                minU = box.minX;
                maxU = box.maxX;
                minV = box.minY;
                maxV = box.maxY;
                break;
            case EAST:
                planeW = box.maxX;
                minU = box.minZ;
                maxU = box.maxZ;
                minV = box.minY;
                maxV = box.maxY;
                break;
            case WEST:
                planeW = box.minX;
                minU = box.minZ;
                maxU = box.maxZ;
                minV = box.minY;
                maxV = box.maxY;
                break;
            default:
                return;
        }

        double r2 = R * R - planeW * planeW;
        if (r2 <= 1E-6) return;
        double r = Math.sqrt(r2);

        if (r < minU || -r > maxU || r < minV || -r > maxV) return;

        double offset = (face.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE) ? 0.005 : -0.005;
        double rp = planeW + offset;

        int ac = 1;
        angleScratch[0] = 0.0;
        ac = addCos(angleScratch, ac, minU / r);
        ac = addCos(angleScratch, ac, maxU / r);
        ac = addSin(angleScratch, ac, minV / r);
        ac = addSin(angleScratch, ac, maxV / r);
        sortAngles(angleScratch, ac);

        final int SUB = 8;
        final double TWO_PI = Math.PI * 2.0;
        EnumFacing.Axis axis = face.getAxis();

        for (int i = 0; i < ac; i++) {
            double aStart = angleScratch[i];
            double aEnd = (i + 1 < ac) ? angleScratch[i + 1] : TWO_PI;
            if (aEnd - aStart < 1E-9) continue;

            double aMid = (aStart + aEnd) * 0.5;
            double uMid = r * Math.cos(aMid);
            double vMid = r * Math.sin(aMid);
            if (uMid < minU - 1E-6 || uMid > maxU + 1E-6 || vMid < minV - 1E-6 || vMid > maxV + 1E-6) continue;

            double span = aEnd - aStart;
            for (int j = 0; j < SUB; j++) {
                double a1 = aStart + span * j / SUB;
                double a2 = aStart + span * (j + 1) / SUB;
                double u1 = r * Math.cos(a1), v1 = r * Math.sin(a1);
                double u2 = r * Math.cos(a2), v2 = r * Math.sin(a2);
                if (buildCount + 6 > buildBuf.length) return;

                if (axis == EnumFacing.Axis.Y) {
                    buildBuf[buildCount++] = (float) u1;
                    buildBuf[buildCount++] = (float) rp;
                    buildBuf[buildCount++] = (float) v1;
                    buildBuf[buildCount++] = (float) u2;
                    buildBuf[buildCount++] = (float) rp;
                    buildBuf[buildCount++] = (float) v2;
                } else if (axis == EnumFacing.Axis.Z) {
                    buildBuf[buildCount++] = (float) u1;
                    buildBuf[buildCount++] = (float) v1;
                    buildBuf[buildCount++] = (float) rp;
                    buildBuf[buildCount++] = (float) u2;
                    buildBuf[buildCount++] = (float) v2;
                    buildBuf[buildCount++] = (float) rp;
                } else {
                    buildBuf[buildCount++] = (float) rp;
                    buildBuf[buildCount++] = (float) v1;
                    buildBuf[buildCount++] = (float) u1;
                    buildBuf[buildCount++] = (float) rp;
                    buildBuf[buildCount++] = (float) v2;
                    buildBuf[buildCount++] = (float) u2;
                }
            }
        }
    }

    public void clear() {
        te = null;
        linkScope = energyScope = chargingScope = 0;
        animProgress = lastAnimProgress = 0;
        rs = null;
        linkVerts = energyVerts = chargingVerts = EMPTY_VERTS;
        linkDirty = energyDirty = chargingDirty = false;
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (te == null) return;
        if (te.isInvalid() || rs == null) {
            clear();
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.player;
        BlockPos pos = te.getPos();
        if (p.dimension != te.getWorld().provider.getDimension() || pos.distanceSq(p.posX, p.posY, p.posZ) > 2500) {
            clear();
            return;
        }

        var stack = p.getHeldItemMainhand();
        if (!(stack.getItem() == RegistryItems.inspectionTool
            && RegistryItems.inspectionTool.getMode(stack).isMode(ItemInspectionTool.Mode.Spoce))) return;

        float partial = event.getPartialTicks();
        double renderPosX = p.lastTickPosX + (p.posX - p.lastTickPosX) * partial;
        double renderPosY = p.lastTickPosY + (p.posY - p.lastTickPosY) * partial;
        double renderPosZ = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * partial;

        double tx = pos.getX() + 0.5 - renderPosX;
        double ty = pos.getY() + 0.5 - renderPosY;
        double tz = pos.getZ() + 0.5 - renderPosZ;

        float interpFactor = easeOutCubic(lastAnimProgress + (animProgress - lastAnimProgress) * partial);
        boolean animating = isAnimating() || (animProgress == 1.0f && lastAnimProgress < 1.0f);

        World world = mc.world;

        GlStateManager.pushMatrix();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.translate(tx, ty, tz);
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);

        float time = world.getTotalWorldTime() + partial;
        float rotation = time * 0.8f;

        if (linkScope > 0) {
            final float radius = linkScope * interpFactor;
            final float wr = 0.4f, wg = 0.8f, wb = 1.0f;
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            draw(rotation * rs[0], 0, 0.4f, 0.8f, radius, wr, wg, wb);
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            if (animating) {
                drawIntersectionImmediate(world, radius, bright(wr), bright(wg), bright(wb));
            } else {
                if (linkDirty) {
                    linkVerts = buildIntersectionGeometry(world, linkScope);
                    linkDirty = false;
                }
                drawCachedIntersection(linkVerts, bright(wr), bright(wg), bright(wb));
            }
        }

        if (energyScope > 0) {
            final float radius = energyScope * interpFactor;
            final float wr = 0.8f, wg = 0.6f, wb = 1.0f;
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            draw(rotation * rs[1], 0.4f, 0.2f, 0.8f, radius, wr, wg, wb);
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            if (animating) {
                drawIntersectionImmediate(world, radius, bright(wr), bright(wg), bright(wb));
            } else {
                if (energyDirty) {
                    energyVerts = buildIntersectionGeometry(world, energyScope);
                    energyDirty = false;
                }
                drawCachedIntersection(energyVerts, bright(wr), bright(wg), bright(wb));
            }
        }

        if (chargingScope > 0) {
            final float radius = chargingScope * interpFactor;
            final float wr = 0.4f, wg = 1.0f, wb = 0.4f;
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            draw(rotation * rs[2], 0, 0.5f, 0.1f, radius, wr, wg, wb);
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            if (animating) {
                drawIntersectionImmediate(world, radius, bright(wr), bright(wg), bright(wb));
            } else {
                if (chargingDirty) {
                    chargingVerts = buildIntersectionGeometry(world, chargingScope);
                    chargingDirty = false;
                }
                drawCachedIntersection(chargingVerts, bright(wr), bright(wg), bright(wb));
            }
        }

        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }
}