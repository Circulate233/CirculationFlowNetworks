package com.circulation.circulation_networks.proxy;

import com.circulation.circulation_networks.manager.EnergyMachineManager;
import com.circulation.circulation_networks.manager.NetworkManager;
import com.circulation.circulation_networks.registry.RegistryBlocks;
import com.circulation.circulation_networks.registry.RegistryItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public class ClientProxy extends CommonProxy {

    public void preInit() {
        super.preInit();
    }

    public void init() {
        super.init();
    }

    public void postInit() {
        super.postInit();
    }

    @SubscribeEvent
    public void onModelRegister(ModelRegistryEvent event) {
        RegistryBlocks.registerBlockModels();
        RegistryItems.registerItemModels();
    }

    @SubscribeEvent
    public void renderWorldLastEvent(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.player;
        if (p.getHeldItem(EnumHand.MAIN_HAND).getItem() == RegistryItems.debugItem) {
            double doubleX = p.lastTickPosX + (p.posX - p.lastTickPosX) * event.getPartialTicks();
            double doubleY = p.lastTickPosY + (p.posY - p.lastTickPosY) * event.getPartialTicks();
            double doubleZ = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * event.getPartialTicks();

            GlStateManager.pushMatrix();
            GlStateManager.color(1.0f, 0, 0);
            GlStateManager.glLineWidth(3);
            GlStateManager.translate(-doubleX, -doubleY, -doubleZ);

            GlStateManager.disableDepth();
            GlStateManager.disableTexture2D();

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();

            buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);

            for (var activeNode : NetworkManager.INSTANCE.getActiveNodes()) {
                if (activeNode.getWorld().provider.getDimension() != mc.player.dimension) continue;
                for (var neighbor : activeNode.getNeighbors()) {
                    renderEntityConnectionLine(buffer, activeNode.getPos(), neighbor.getPos(), 0, 0, 1);
                }
            }

            for (var entry : EnergyMachineManager.INSTANCE.getMachineGridMap().entrySet()) {
                if (entry.getKey().getWorld().provider.getDimension() != mc.player.dimension) continue;
                for (var node : entry.getValue()) {
                    renderEntityConnectionLine(buffer, entry.getKey().getPos(), node.getPos(), 1, 0, 0);
                }
            }

            tessellator.draw();

            GlStateManager.enableTexture2D();
            GlStateManager.enableDepth();
            GlStateManager.popMatrix();
        }
    }

    @SuppressWarnings("SameParameterValue")
    private void renderEntityConnectionLine(BufferBuilder buffer, BlockPos pos, BlockPos pos1, float r, float g, float b) {
        buffer.pos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
              .color(r, g, b, 0.5f)
              .endVertex();

        buffer.pos(pos1.getX() + 0.5, pos1.getY() + 0.5, pos1.getZ() + 0.5)
              .color(r, g, b, 0.5f)
              .endVertex();
    }
}