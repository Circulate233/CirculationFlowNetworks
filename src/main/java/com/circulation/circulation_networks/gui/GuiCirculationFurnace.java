package com.circulation.circulation_networks.gui;

import com.circulation.circulation_networks.container.ContainerCirculationFurnace;
import com.circulation.circulation_networks.tiles.machines.TileEntityCirculationFurnace;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

@SideOnly(Side.CLIENT)
public class GuiCirculationFurnace extends GuiContainer {

    private static final ResourceLocation TEXTURE = new ResourceLocation("circulation_networks:textures/gui/circulation_furnace");
    private final ContainerCirculationFurnace container;

    public GuiCirculationFurnace(EntityPlayer playerInv, TileEntityCirculationFurnace te) {
        super(new ContainerCirculationFurnace(playerInv, te));
        this.container = (ContainerCirculationFurnace) inventorySlots;
        this.xSize = 176;
        this.ySize = 166;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        this.renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        this.mc.getTextureManager().bindTexture(TEXTURE);
        int i = (this.width - this.xSize) / 2;
        int j = (this.height - this.ySize) / 2;

        this.drawTexturedModalRect(i, j, 0, 0, this.xSize, this.ySize);

        int cookTime = container.cookTime;
        int totalCookTime = container.totalCookTime;
        long currentFlow = container.currentFlow;
        long demandFlow = container.demandFlow;

        if (demandFlow > 0) {
            double ratio = Math.min(1.0F, (double) currentFlow / demandFlow);
            int barHeight = (int) (50.0d * ratio);
            int uOffset = (ratio < 0.95d) ? 192 : 176;
            this.drawTexturedModalRect(i + 8, j + 65 - barHeight, uOffset, 50 - barHeight, 16, barHeight);
        }

        if (totalCookTime > 0 && cookTime > 0) {
            int progressHeight = (int) (54.0F * ((float) cookTime / totalCookTime));
            this.drawTexturedModalRect(i + 62, j + 20, 176, 50, 12, progressHeight);
            this.drawTexturedModalRect(i + 102, j + 20, 188, 50, 12, progressHeight);
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String s = "Circulation Furnace";
        this.fontRenderer.drawString(s, this.xSize / 2 - this.fontRenderer.getStringWidth(s) / 2, 6, 4210752);

        int relX = mouseX - (this.width - this.xSize) / 2;
        int relY = mouseY - (this.height - this.ySize) / 2;

        if (relX >= 8 && relX <= 24 && relY >= 15 && relY <= 65) {
            drawEnergyTooltip(mouseX, mouseY);
        }
    }

    private void drawEnergyTooltip(int mouseX, int mouseY) {
        long current = container.currentFlow;
        long demand = container.demandFlow;
        List<String> list = new ObjectArrayList<>();

        if (demand <= 0) {
            list.add(TextFormatting.GRAY + "Status: " + TextFormatting.DARK_GRAY + "IDLE (待机)");
        } else {
            float ratio = Math.min(1.0F, (float) current / demand);
            TextFormatting color = (ratio < 0.95F) ? TextFormatting.GOLD : TextFormatting.AQUA;

            list.add(TextFormatting.GRAY + "Efficiency: " + color + (int) (ratio * 100) + "%");
            list.add(TextFormatting.BLUE + "Flow: " + current + " CE/t");
            list.add(TextFormatting.RED + "Demand: " + demand + " CE/t");
            if (ratio < 0.95F) {
                list.add(TextFormatting.YELLOW + "(Underpowered!)");
            }
        }

        this.drawHoveringText(list, mouseX - (this.width - this.xSize) / 2, mouseY - (this.height - this.ySize) / 2);
    }
}