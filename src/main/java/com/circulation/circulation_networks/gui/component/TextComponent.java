package com.circulation.circulation_networks.gui.component;

import com.circulation.circulation_networks.client.render.ClientAnimationTicker;
import com.circulation.circulation_networks.gui.CFNBaseGui;
import com.circulation.circulation_networks.gui.component.base.Component;
import com.circulation.circulation_networks.utils.ScrollingTextHelper;
import net.minecraft.client.Minecraft;

import java.util.function.Supplier;

public class TextComponent extends Component {

    private static final int FONT_HEIGHT = 8;

    private final Supplier<String> textSupplier;
    private final int color;
    private final boolean shadow;
    private int maxWidth = -1;

    public TextComponent(int x, int y, CFNBaseGui<?> gui, Supplier<String> textSupplier, int color) {
        this(x, y, gui, textSupplier, color, false);
    }

    public TextComponent(int x, int y, CFNBaseGui<?> gui, Supplier<String> textSupplier, int color, boolean shadow) {
        super(x, y, 0, FONT_HEIGHT, gui);
        this.textSupplier = textSupplier;
        this.color = color;
        this.shadow = shadow;
        setEnabled(false);
    }

    public TextComponent setMaxWidth(int maxWidth) {
        this.maxWidth = maxWidth;
        return this;
    }

    @Override
    public boolean contains(int mouseX, int mouseY) {
        return false;
    }

    @Override
    protected void render(int mouseX, int mouseY, float partialTicks) {
        String text = textSupplier.get();
        if (text == null || text.isEmpty()) {
            setSize(0, FONT_HEIGHT);
            return;
        }

        //? if <1.20 {
        Minecraft mc = Minecraft.getMinecraft();
        int textWidth = mc.fontRenderer.getStringWidth(text);
        setSize(maxWidth > 0 ? Math.min(textWidth, maxWidth) : textWidth, FONT_HEIGHT);
        if (maxWidth > 0 && textWidth > maxWidth) {
            long tick = ClientAnimationTicker.ticks();
            float offset = ScrollingTextHelper.getScrollOffset(textWidth, maxWidth, tick, partialTicks);
            int absX = getAbsoluteX();
            int absY = getAbsoluteY();
            net.minecraft.client.gui.ScaledResolution sr = new net.minecraft.client.gui.ScaledResolution(mc);
            int sf = sr.getScaleFactor();
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
            org.lwjgl.opengl.GL11.glScissor(absX * sf, mc.displayHeight - (absY + FONT_HEIGHT) * sf, maxWidth * sf, FONT_HEIGHT * sf);
            mc.fontRenderer.drawString(text, absX - (int) offset, absY, color, shadow);
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
        } else {
            mc.fontRenderer.drawString(text, getAbsoluteX(), getAbsoluteY(), color, shadow);
        }
        //?} else {
        /*Minecraft mc = Minecraft.getInstance();
        int textWidth = mc.font.width(text);
        setSize(maxWidth > 0 ? Math.min(textWidth, maxWidth) : textWidth, FONT_HEIGHT);
        var guiGraphics = getCurrentGuiGraphics();
        if (guiGraphics == null) return;
        if (maxWidth > 0 && textWidth > maxWidth) {
            long tick = ClientAnimationTicker.ticks();
            float offset = ScrollingTextHelper.getScrollOffset(textWidth, maxWidth, tick, partialTicks);
            int absX = getAbsoluteX();
            int absY = getAbsoluteY();
            guiGraphics.enableScissor(absX, absY, absX + maxWidth, absY + FONT_HEIGHT);
            guiGraphics.drawString(mc.font, text, absX - (int) offset, absY, color, shadow);
            guiGraphics.disableScissor();
        } else {
            guiGraphics.drawString(mc.font, text, getAbsoluteX(), getAbsoluteY(), color, shadow);
        }
        *///?}
    }
}
