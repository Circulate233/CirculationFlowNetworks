package com.circulation.circulation_networks.client.compat;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class GuiGraphicsCompat {

    private GuiGraphicsCompat() {
    }

    public static void drawString(GuiGraphicsExtractor guiGraphics, Font font, String text, int x, int y, int color, boolean shadow) {
        guiGraphics.text(font, text, x, y, normalizeGuiTextColor(color), shadow);
    }

    public static void pushPose(GuiGraphicsExtractor guiGraphics) {
        guiGraphics.pose().pushMatrix();
    }

    public static void popPose(GuiGraphicsExtractor guiGraphics) {
        guiGraphics.pose().popMatrix();
    }

    public static void renderItem(GuiGraphicsExtractor guiGraphics, ItemStack stack, int x, int y) {
        guiGraphics.item(stack, x, y);
    }

    public static void renderItemDecorations(GuiGraphicsExtractor guiGraphics, Font font, ItemStack stack, int x, int y, @Nullable String text) {
        guiGraphics.itemDecorations(font, stack, x, y, text);
    }

    public static void renderEditBox(CFNEditBox editBox, @Nullable Object guiGraphics, int mouseX, int mouseY, float partialTicks) {
        if (guiGraphics instanceof GuiGraphicsExtractor typedGuiGraphics) {
            editBox.renderCompat(typedGuiGraphics, mouseX, mouseY, partialTicks);
        }
    }

    public static void translate(GuiGraphicsExtractor guiGraphics, float x, float y, float z) {
        guiGraphics.pose().translate(x, y);
    }

    public static void scale(GuiGraphicsExtractor guiGraphics, float x, float y, float z) {
        guiGraphics.pose().scale(x, y);
    }

    private static int normalizeGuiTextColor(int color) {
        return (color & 0xFF000000) == 0 ? color | 0xFF000000 : color;
    }

    public static class CFNEditBox extends EditBox {

        public CFNEditBox(Font font, int x, int y, int width, int height, Component narration) {
            super(font, x, y, width, height, narration);
        }

        public void renderCompat(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
            this.extractWidgetRenderState(guiGraphics, mouseX, mouseY, partialTicks);
        }

        public void tickCompat() {
        }

        public boolean mouseClickedCompat(int mouseX, int mouseY, int button) {
            return this.mouseClicked(new MouseButtonEvent(mouseX, mouseY, new MouseButtonInfo(button, 0)), false);
        }

        public boolean keyTypedCompat(char typedChar, int keyCode) {
            if (typedChar != 0 && this.charTyped(new CharacterEvent(typedChar))) {
                return true;
            }
            return keyCode != 0 && this.keyPressed(new KeyEvent(keyCode, 0, 0));
        }
    }
}
