package com.circulation.circulation_networks.gui;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.container.CFNBaseContainer;
import com.circulation.circulation_networks.gui.component.base.Component;
import com.circulation.circulation_networks.gui.component.base.ComponentAtlas;
import com.circulation.circulation_networks.gui.component.base.ComponentScreenController;
import com.circulation.circulation_networks.gui.component.base.RenderPhase;
import net.minecraft.client.Minecraft;
//? if <1.20 {
import net.minecraftforge.client.event.GuiContainerEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
//?} else if <1.21 {
/*import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import it.unimi.dsi.fastutil.objects.ObjectLists;
*///?} else {
/*import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import it.unimi.dsi.fastutil.objects.ObjectLists;
*///?}
//? if <1.20 {
import com.circulation.circulation_networks.packets.ContainerProgressBar;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
//?} else if <1.21 {
/*import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.entity.player.Inventory;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import java.util.Optional;
*///?} else {
/*import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.entity.player.Inventory;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import java.util.Optional;
*///?}

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.awt.Rectangle;
import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for all CFN GUI screens that need interactive components.
 *
 * <p>Rendering layers (bottom → top):
 * <ol>
 *   <li>Background texture — implement {@link #drawBG}.</li>
 *   <li>Component-owned slot and item rendering.</li>
 *   <li>Foreground labels — implement {@link #drawFG}.</li>
 *   <li>Component layer — all {@link Component} instances registered via
 *       {@link #buildComponents}, sorted ascending by z-index.</li>
 *   <li>Component tooltip — collected from the topmost hovered component.</li>
 * </ol>
 *
 * <p>Event routing: Mouse and key events are delivered to components in descending
 * z-index order (highest z-index first). The first component that returns {@code true}
 * from its handler consumes the event; remaining components and the default
 * behavior are skipped.
 */
@SuppressWarnings("unused")
//~ if >=1.20 '@SideOnly(Side.CLIENT)' -> '@OnlyIn(Dist.CLIENT)' {
@SideOnly(Side.CLIENT)
//~}
//? if <1.20 {
public abstract class CFNBaseGui<T extends CFNBaseContainer> extends GuiContainer {
//?} else {
    /*public abstract class CFNBaseGui<T extends CFNBaseContainer> extends AbstractContainerScreen<T> {
     *///?}

    protected final T container;
    private final ComponentScreenController componentController = new ComponentScreenController();
    @Nullable
    protected Slot hoveredSlot;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    //? if <1.20 {
    protected CFNBaseGui(@NotNull T container) {
        super(container);
        this.container = container;
    }
    //?} else {
    /*protected CFNBaseGui(@NotNull T container, Inventory playerInventory, net.minecraft.network.chat.Component title) {
        super(container, playerInventory, title);
        this.container = container;
    }
    *///?}

    public int getGuiLeft() {
        //? if <1.20 {
        return guiLeft;
        //?} else {
        /*return leftPos;
         *///?}
    }

    public int getGuiTop() {
        //? if <1.20 {
        return guiTop;
        //?} else {
        /*return topPos;
         *///?}
    }

    public void setHoveredSlot(@Nullable Slot hoveredSlot) {
        this.hoveredSlot = hoveredSlot;
    }

    //? if >=1.20 {
    /*public void focusComponentInput(@Nullable GuiEventListener listener) {
        this.setFocused(listener);
    }

    public void clearComponentInputFocus(@Nullable GuiEventListener listener) {
        if (this.getFocused() == listener) {
            this.setFocused(null);
        }
    }
    *///?}

    //? if >=1.20 {
    /*public List<String> getContainerItemTooltipLines(ItemStack stack) {
        List<net.minecraft.network.chat.Component> lines = getTooltipFromContainerItem(stack);
        if (lines.isEmpty()) {
            return ObjectLists.emptyList();
        }

        List<String> tooltip = new ObjectArrayList<>(lines.size());
        for (net.minecraft.network.chat.Component line : lines) {
            tooltip.add(line.getString());
        }
        return tooltip;
    }
    *///?}

    public boolean isTopComponent(Component component, int mouseX, int mouseY) {
        return componentController.getTopComponentAt(mouseX, mouseY) == component;
    }

    public void bringComponentToFront(Component component) {
        componentController.bringToFront(component);
    }

    // -------------------------------------------------------------------------
    // Component registration
    // -------------------------------------------------------------------------

    /**
     * Override this method to register all root-level components for this GUI.
     * Add components into the map under the appropriate {@link RenderPhase}.
     */
    protected void buildComponents(Map<RenderPhase, List<Component>> components) {
    }

    // -------------------------------------------------------------------------
    // GUI lifecycle
    // -------------------------------------------------------------------------

    @Override
        //? if <1.20 {
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        //?} else {
    /*protected void init() {
        super.init();
    *///?}
        //? if <1.20 {
        CirculationFlowNetworks.sendToServer(new ContainerProgressBar());
        //?}
        Map<RenderPhase, List<Component>> phaseMap = new EnumMap<>(RenderPhase.class);
        buildComponents(phaseMap);
        componentController.initializeComponents(phaseMap);
        ComponentAtlas.INSTANCE.awaitReady();
    }

    //? if <1.20 {
    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }
    //?}

    // -------------------------------------------------------------------------
    // Abstract rendering hooks
    // -------------------------------------------------------------------------

    /**
     * Draw the GUI background texture.
     */
    public abstract void drawBG(int offsetX, int offsetY, int mouseX, int mouseY);

    /**
     * Draw GUI foreground elements (labels, overlays, etc.).
     */
    public abstract void drawFG(int offsetX, int offsetY, int mouseX, int mouseY);

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    private void resetColor() {
        //? if <1.20 {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        //?} else {
        /*RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
         *///?}
    }

    private void renderBGPhase(int mouseX, int mouseY, float partialTicks) {
        resetColor();
        componentController.renderPhase(RenderPhase.BACKGROUND, mouseX, mouseY, partialTicks);
    }

    //? if <1.20 {
    private void renderComponentPhase(RenderPhase phase, int mouseX, int mouseY, float partialTicks) {
        resetColor();
        componentController.renderPhase(phase, mouseX, mouseY, partialTicks);
    }
    //?} else {
    /*private void renderComponentPhase(GuiGraphics guiGraphics, RenderPhase phase, int mouseX, int mouseY, float partialTicks) {
        resetGuiRenderState();
        resetColor();
        Component.setCurrentGuiGraphics(guiGraphics);
        try {
            componentController.renderPhase(phase, mouseX, mouseY, partialTicks);
        } finally {
            Component.setCurrentGuiGraphics(null);
        }
    }
    *///?}

    private void renderComponentTooltip(int mouseX, int mouseY) {
        List<String> componentTooltip = componentController.collectTooltip(mouseX, mouseY);
        if (componentTooltip == null || componentTooltip.isEmpty()) return;
        //? if <1.20 {
        drawHoveringText(componentTooltip, mouseX, mouseY);
        //?} else {
        /*List<net.minecraft.network.chat.Component> mcTooltip = new ObjectArrayList<>();
        for (String line : componentTooltip) {
            mcTooltip.add(net.minecraft.network.chat.Component.literal(line));
        }
        // guiGraphics is not available here — tooltip rendered inline below
        *///?}
    }

    //? if <1.20 {
    private void renderCarriedStack(int mouseX, int mouseY) {
        ItemStack carried = Minecraft.getMinecraft().player.inventory.getItemStack();
        if (carried.isEmpty()) return;

        float prevZLevel = this.itemRender.zLevel;
        resetGuiRenderState();
        GlStateManager.enableRescaleNormal();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0F, 0.0F, 300.0F);
        GlStateManager.enableDepth();
        GlStateManager.enableCull();
        GlStateManager.depthMask(true);
        GlStateManager.enableLighting();
        this.itemRender.zLevel = 300.0F;
        this.itemRender.renderItemAndEffectIntoGUI(Minecraft.getMinecraft().player, carried, mouseX - 8, mouseY - 8);
        this.itemRender.renderItemOverlayIntoGUI(this.fontRenderer, carried, mouseX - 8, mouseY - 8, null);
        this.itemRender.zLevel = prevZLevel;
        GlStateManager.popMatrix();
        resetGuiRenderState();
    }

    private void resetGuiRenderState() {
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.disableRescaleNormal();
        GlStateManager.depthMask(true);
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
            GL11.GL_SRC_ALPHA,
            GL11.GL_ONE_MINUS_SRC_ALPHA,
            GL11.GL_ONE,
            GL11.GL_ZERO
        );
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.enableTexture2D();
        GlStateManager.matrixMode(GL11.GL_TEXTURE);
        GlStateManager.loadIdentity();
        GlStateManager.disableTexture2D();

        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.enableTexture2D();
        GlStateManager.matrixMode(GL11.GL_TEXTURE);
        GlStateManager.loadIdentity();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
    }
    //?} else {
    /*private void renderCarriedStack(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        ItemStack carried = this.menu.getCarried();
        if (carried.isEmpty()) return;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 300);
        guiGraphics.renderItem(carried, mouseX - 8, mouseY - 8);
        guiGraphics.renderItemDecorations(this.font, carried, mouseX - 8, mouseY - 8);
        guiGraphics.pose().popPose();
    }

    private void resetGuiRenderState() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
    *///?}

    //? if <1.20 {
    @Override
    protected final void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        renderBGPhase(mouseX, mouseY, partialTicks);
        resetColor();
        this.drawBG(this.guiLeft, this.guiTop, mouseX, mouseY);
    }

    @Override
    protected final void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        resetColor();
        this.drawFG(this.guiLeft, this.guiTop, mouseX, mouseY);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        componentController.handleActiveDrag(mouseX, mouseY);
        this.hoveredSlot = null;
        resetGuiRenderState();

        renderBGPhase(mouseX, mouseY, partialTicks);
        resetColor();
        this.drawBG(this.guiLeft, this.guiTop, mouseX, mouseY);

        resetGuiRenderState();

        componentController.renderPhase(RenderPhase.NORMAL, mouseX, mouseY, partialTicks);

        resetGuiRenderState();
        resetColor();
        this.drawFG(this.guiLeft, this.guiTop, mouseX, mouseY);
        RenderHelper.enableGUIStandardItemLighting();
        MinecraftForge.EVENT_BUS.post(new GuiContainerEvent.DrawForeground(this, mouseX, mouseY));

        resetGuiRenderState();
        resetColor();
        componentController.renderPhase(RenderPhase.FOREGROUND, mouseX, mouseY, partialTicks);
        renderCarriedStack(mouseX, mouseY);
        resetGuiRenderState();
        renderComponentTooltip(mouseX, mouseY);
        GlStateManager.enableLighting();
        GlStateManager.enableDepth();
        RenderHelper.enableStandardItemLighting();
    }

    @Override
    public Slot getSlotUnderMouse() {
        return this.hoveredSlot;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        componentController.updateComponents();
    }
    //?} else {
    /*@Override
    protected final void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        renderComponentPhase(guiGraphics, RenderPhase.BACKGROUND, mouseX, mouseY, partialTick);
        resetColor();
        this.drawBG(this.leftPos, this.topPos, mouseX, mouseY);
    }

    @Override
    protected final void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        resetColor();
        this.drawFG(this.leftPos, this.topPos, mouseX, mouseY);
    }

    //? if <1.21 {
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        componentController.handleActiveDrag(mouseX, mouseY);
        this.hoveredSlot = null;
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderComponentPhase(guiGraphics, RenderPhase.NORMAL, mouseX, mouseY, partialTick);
        renderComponentPhase(guiGraphics, RenderPhase.FOREGROUND, mouseX, mouseY, partialTick);
        renderCarriedStack(guiGraphics, mouseX, mouseY);

        List<String> componentTooltip = componentController.collectTooltip(mouseX, mouseY);
        if (componentTooltip != null && !componentTooltip.isEmpty()) {
            List<net.minecraft.network.chat.Component> mcTooltip = new ObjectArrayList<>();
            for (String line : componentTooltip) {
                mcTooltip.add(net.minecraft.network.chat.Component.literal(line));
            }
            guiGraphics.renderTooltip(this.font, mcTooltip, Optional.empty(), mouseX, mouseY);
        } else {
            this.renderTooltip(guiGraphics, mouseX, mouseY);
        }
    }
    //?} else {
    /^@Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        componentController.handleActiveDrag(mouseX, mouseY);
        this.hoveredSlot = null;
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderComponentPhase(guiGraphics, RenderPhase.NORMAL, mouseX, mouseY, partialTick);
        renderComponentPhase(guiGraphics, RenderPhase.FOREGROUND, mouseX, mouseY, partialTick);
        renderCarriedStack(guiGraphics, mouseX, mouseY);

        List<String> componentTooltip = componentController.collectTooltip(mouseX, mouseY);
        if (componentTooltip != null && !componentTooltip.isEmpty()) {
            List<net.minecraft.network.chat.Component> mcTooltip = new ObjectArrayList<>();
            for (String line : componentTooltip) {
                mcTooltip.add(net.minecraft.network.chat.Component.literal(line));
            }
            guiGraphics.renderTooltip(this.font, mcTooltip, Optional.empty(), mouseX, mouseY);
        } else {
            this.renderTooltip(guiGraphics, mouseX, mouseY);
        }
    }

    ^///?}

    @Override
    protected void containerTick() {
        super.containerTick();
        componentController.updateComponents();
    }
    *///?}

    // -------------------------------------------------------------------------
    // Event handling
    // -------------------------------------------------------------------------

    @Override
    //? if <1.20 {
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (componentController.mouseClicked(mouseX, mouseY, mouseButton)) {
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }
    //?} else {
    /*public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (componentController.mouseClicked((int) mouseX, (int) mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    *///?}

    @Override
    //? if <1.20 {
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (componentController.mouseReleased(mouseX, mouseY, state)) {
            return;
        }
        super.mouseReleased(mouseX, mouseY, state);
    }
    //?} else {
    /*public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (componentController.mouseReleased((int) mouseX, (int) mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
    *///?}

    @Override
    //? if <1.20 {
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (componentController.keyTyped(typedChar, keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }
    //?} else {
    /*public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (componentController.keyTyped((char) 0, keyCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (componentController.keyTyped(codePoint, 0)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }
    *///?}

    //? if <1.20 {
    private boolean handleComponentMouseScroll() {
        int scrollDelta = Mouse.getEventDWheel();
        if (scrollDelta == 0) {
            return false;
        }
        int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
        int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
        int delta = scrollDelta > 0 ? 1 : -1;
        return componentController.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void handleInput() throws IOException {
        if (Mouse.isCreated()) {
            while (Mouse.next()) {
                this.mouseHandled = false;
                if (handleComponentMouseScroll()) {
                    continue;
                }
                if (MinecraftForge.EVENT_BUS.post(new GuiScreenEvent.MouseInputEvent.Pre(this))) {
                    continue;
                }
                super.handleMouseInput();
                if (this.equals(this.mc.currentScreen) && !this.mouseHandled) {
                    MinecraftForge.EVENT_BUS.post(new GuiScreenEvent.MouseInputEvent.Post(this));
                }
            }
        }

        if (Keyboard.isCreated()) {
            while (Keyboard.next()) {
                this.keyHandled = false;
                if (MinecraftForge.EVENT_BUS.post(new GuiScreenEvent.KeyboardInputEvent.Pre(this))) {
                    continue;
                }
                this.handleKeyboardInput();
                if (this.equals(this.mc.currentScreen) && !this.keyHandled) {
                    MinecraftForge.EVENT_BUS.post(new GuiScreenEvent.KeyboardInputEvent.Post(this));
                }
            }
        }
    }
    //?} else {
    /*//? if <1.21 {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta != 0) {
            int d = delta > 0 ? 1 : -1;
            componentController.mouseScrolled((int) mouseX, (int) mouseY, d);
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
    //?} else {
    /^@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            int d = scrollY > 0 ? 1 : -1;
            componentController.mouseScrolled((int) mouseX, (int) mouseY, d);
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
    ^///?}
    *///?}

    //? if <1.20 {
    public List<Rectangle> getGuiExtraAreas() {
        var list = new ObjectArrayList<Rectangle>();
        for (Component component : componentController.getAllComponents()) {
            if (!component.isVisible()) continue;
            var r = component.getBounds();
            r.setLocation(component.getAbsoluteX(), component.getAbsoluteY());
            list.add(r);
        }
        return list;
    }
    //?} else {
    /*public List<Rect2i> getGuiExtraAreas() {
        var list = new ObjectArrayList<Rect2i>();
        for (Component component : componentController.getAllComponents()) {
            if (!component.isVisible()) continue;
            list.add(new Rect2i(component.getAbsoluteX(), component.getAbsoluteY(), component.width, component.height));
        }
        return list;
    }
    *///?}
}
