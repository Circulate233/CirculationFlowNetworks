package com.circulation.circulation_networks.gui.component.base;

import com.circulation.circulation_networks.client.compat.GuiGraphicsCompat;
import com.circulation.circulation_networks.client.compat.RenderSystemCompat;
import com.circulation.circulation_networks.container.ComponentSlotLayout;
import com.circulation.circulation_networks.gui.CFNBaseGui;
import com.circulation.circulation_networks.tooltip.Composite;
import com.circulation.circulation_networks.tooltip.LocalizedComponent;
import com.circulation.circulation_networks.utils.FormatNumberUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Rectangle;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

@SuppressWarnings({"unused", "unchecked"})
public class Component extends Rectangle {
    protected static final int GUI_TEXT_HEIGHT = 8;
    private static final String[] EMPTY = new String[0];
    @Nullable
    private static GuiGraphicsExtractor currentGuiGraphics;
    @NotNull
    protected final CFNBaseGui<?> gui;
    private final ObjectList<Component> children = new ObjectArrayList<>();
    private final ObjectList<ComponentSlotLayout> boundLayouts = new ObjectArrayList<>();
    protected boolean visible = true;
    protected boolean enabled = true;
    protected int zIndex = 0;
    protected boolean update;
    protected ObjectList<LocalizedComponent> tooltips = new ObjectArrayList<>();
    @Nullable
    private Component parent;
    private boolean hovered = false;
    private String[] spriteLayers = EMPTY;

    public Component(int x, int y, int width, int height, @NotNull CFNBaseGui<?> gui) {
        super(x, y, width, height);
        this.gui = gui;
        update = true;
    }

    protected static void renderAtlasSprite(String spriteName,
                                            int screenX, int screenY,
                                            int renderW, int renderH) {
        ComponentAtlas atlas = ComponentAtlas.INSTANCE;
        AtlasRegion region = atlas.getRegion(spriteName);
        if (region == null) return;
        AtlasRenderHelper.drawRegion(atlas, region, screenX, screenY, renderW, renderH);
    }

    @Nullable
    protected static String getItemCountOverlayText(@NotNull ItemStack stack) {
        if (stack.isEmpty() || stack.getCount() <= 1) {
            return null;
        }
        return FormatNumberUtils.formatItemCount(stack.getCount());
    }

    protected static int alignTextY(int topY, int areaHeight) {
        return topY + Math.max(0, (areaHeight - GUI_TEXT_HEIGHT) / 2);
    }

    @Nullable
    public static GuiGraphicsExtractor peekCurrentGuiGraphics() {
        return currentGuiGraphics;
    }

    public ObjectList<Component> getChildren() {
        return children;
    }

    public boolean isVisible() {
        return parent != null ? parent.isVisible() && visible : visible;
    }

    public Component setVisible(boolean visible) {
        boolean wasVisible = isVisible();
        this.visible = visible;
        invalidateSubtree();
        syncSlotTreePositions();
        if (!wasVisible && isVisible()) {
            promoteDraggableTarget();
        }
        return this;
    }

    public boolean isEnabled() {
        return parent != null ? parent.isEnabled() && enabled : enabled;
    }

    public Component setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public int getZIndex() {
        return zIndex;
    }

    public Component setZIndex(int zIndex) {
        this.zIndex = zIndex;
        if (parent != null) {
            parent.children.sort(Comparator.comparingInt(c -> c.zIndex));
        }
        return this;
    }

    public boolean isHovered() {
        return hovered;
    }

    public Component setX(int x) {
        this.x = x;
        invalidateSubtree();
        syncSlotTreePositions();
        return this;
    }

    public Component setY(int y) {
        this.y = y;
        invalidateSubtree();
        syncSlotTreePositions();
        return this;
    }

    public Component addChild(Component child) {
        children.add(child);
        children.sort(Comparator.comparingInt(c -> c.zIndex));
        child.parent = this;
        child.invalidateSubtree();
        child.syncSlotTreePositions();
        return this;
    }

    public Component addChild(Component... childs) {
        int childCount = childs.length;
        if (childCount == 0) {
            return this;
        }
        for (Component child : childs) {
            child.parent = this;
            children.add(child);
            children.sort(Comparator.comparingInt(c -> c.zIndex));
            child.invalidateSubtree();
            child.syncSlotTreePositions();
        }
        return this;
    }

    public final void bringToFront() {
        if (parent != null) {
            parent.bringChildToFront(this);
            parent.bringToFront();
        } else {
            gui.bringComponentToFront(this);
        }
    }

    @Nullable
    private Component getDraggablePromotionTarget() {
        Component current = this;
        while (current != null) {
            if (current instanceof DraggableComponent) {
                return current;
            }
            current = current.parent;
        }
        return null;
    }

    private void promoteDraggableTarget() {
        Component target = getDraggablePromotionTarget();
        if (target != null) {
            target.bringToFront();
        }
    }

    private void bringChildToFront(Component child) {
        if (children.remove(child)) {
            children.add(child);
        }
    }

    public void removeChild(Component child) {
        if (children.remove(child)) {
            child.parent = null;
            child.invalidateSubtree();
            child.syncSlotTreePositions();
        }
    }

    public int getAbsoluteX() {
        return parent != null ? parent.getAbsoluteX() + x : gui.getGuiLeftPos() + x;
    }

    public int getAbsoluteY() {
        return parent != null ? parent.getAbsoluteY() + y : gui.getGuiTopPos() + y;
    }

    public boolean contains(int mouseX, int mouseY) {
        int w = this.width;
        int h = this.height;
        if ((w | h) < 0) {
            return false;
        }
        int x = this.getAbsoluteX();
        int y = this.getAbsoluteY();
        if (mouseX < x || mouseY < y) {
            return false;
        }
        w += x;
        h += y;
        return ((w < x || w > mouseX) &&
            (h < y || h > mouseY));
    }

    public boolean contains(int X, int Y, int W, int H) {
        int w = this.width;
        int h = this.height;
        if ((w | h | W | H) < 0) {
            return false;
        }
        int x = this.getAbsoluteX();
        int y = this.getAbsoluteY();
        if (X < x || Y < y) {
            return false;
        }
        w += x;
        W += X;
        if (W <= X) {
            if (w >= x || W > w) return false;
        } else {
            if (w >= x && W > w) return false;
        }
        h += y;
        H += Y;
        if (H <= Y) {
            return h < y && H <= h;
        } else {
            return h < y || H <= h;
        }
    }

    public final void renderComponent(int mouseX, int mouseY, float partialTicks) {
        syncSlotPositions();
        if (!isVisible()) return;

        boolean nowHovered = contains(mouseX, mouseY) && gui.isTopComponent(this, mouseX, mouseY);
        if (nowHovered && !hovered) onMouseEnter();
        else if (!nowHovered && hovered) onMouseLeave();
        hovered = nowHovered;

        renderSpriteLayers();
        render(mouseX, mouseY, partialTicks);

        if (children.isEmpty()) return;
        for (Component child : children) {
            child.renderComponent(mouseX, mouseY, partialTicks);
        }
    }

    public final void renderComponentItems(int mouseX, int mouseY) {
        if (!isVisible()) return;
        renderBoundLayouts(mouseX, mouseY);
        if (children.isEmpty()) return;
        for (Component child : children) {
            child.renderComponentItems(mouseX, mouseY);
        }
    }

    @Nullable
    protected final GuiGraphicsExtractor getCurrentGuiGraphics() {
        return currentGuiGraphics;
    }

    public static void setCurrentGuiGraphics(@Nullable GuiGraphicsExtractor guiGraphics) {
        currentGuiGraphics = guiGraphics;
    }

    protected void render(int mouseX, int mouseY, float partialTicks) {

    }

    protected void renderBoundLayouts(int mouseX, int mouseY) {
        if (boundLayouts.isEmpty()) return;

        GuiGraphicsExtractor guiGraphics = getCurrentGuiGraphics();
        if (guiGraphics == null) return;

        int localMouseX = mouseX - gui.getGuiLeftPos();
        int localMouseY = mouseY - gui.getGuiTopPos();
        boolean topComponent = gui.isTopComponent(this, mouseX, mouseY);

        restoreGuiRenderState();

        for (ComponentSlotLayout layout : boundLayouts) {
            List<? extends Slot> slots = layout.getSlots();
            if (slots.isEmpty()) continue;
            for (Slot slot : slots) {
                if (!slot.isActive()) continue;

                int screenX = gui.getGuiLeftPos() + slot.x;
                int screenY = gui.getGuiTopPos() + slot.y;
                ItemStack stack = slot.getItem();

                if (!stack.isEmpty()) {
                    GuiGraphicsCompat.renderItem(guiGraphics, stack, screenX, screenY);
                    GuiGraphicsCompat.renderItemDecorations(guiGraphics, Minecraft.getInstance().font, stack, screenX, screenY, getItemCountOverlayText(stack));
                }

                if (topComponent && isMouseOverSlot(localMouseX, localMouseY, slot.x, slot.y)) {
                    gui.setHoveredSlot(slot);
                }
            }
        }

        restoreGuiRenderState();
    }

    protected final boolean isMouseOverSlot(int localMouseX, int localMouseY, int slotX, int slotY) {
        return localMouseX >= slotX && localMouseX < slotX + 16
            && localMouseY >= slotY && localMouseY < slotY + 16;
    }

    public final Component setSpriteLayers(String... layers) {
        this.spriteLayers = layers != null ? layers : EMPTY;
        return this;
    }

    protected String[] getActiveLayers() {
        return spriteLayers;
    }

    protected void renderSpriteLayers() {
        String[] layers = getActiveLayers();
        if (layers == null || layers.length == 0) return;

        ComponentAtlas atlas = ComponentAtlas.INSTANCE;
        if (!atlas.isReady()) return;

        restoreGuiRenderState();

        int ax = getAbsoluteX();
        int ay = getAbsoluteY();
        AtlasRenderHelper.beginBatch(atlas);
        for (String name : layers) {
            if (name == null) continue;
            AtlasRegion region = atlas.getRegion(name);
            if (region == null) continue;
            AtlasRenderHelper.drawRegion(atlas, region, ax, ay, width, height);
        }
        AtlasRenderHelper.endBatch();
    }

    @NotNull
    protected List<LocalizedComponent> getTooltip(int mouseX, int mouseY) {
        List<LocalizedComponent> slotTooltip = collectSlotTooltip(mouseX, mouseY);
        if (!slotTooltip.isEmpty()) {
            return slotTooltip;
        }
        return tooltips;
    }

    protected List<LocalizedComponent> collectSlotTooltip(int mouseX, int mouseY) {
        if (boundLayouts.isEmpty()) return ObjectLists.emptyList();

        int localMouseX = mouseX - gui.getGuiLeftPos();
        int localMouseY = mouseY - gui.getGuiTopPos();

        for (ComponentSlotLayout layout : boundLayouts) {
            List<? extends Slot> slots = layout.getSlots();
            if (slots.isEmpty()) continue;
            for (Slot slot : slots) {
                if (!slot.isActive()) continue;
                if (!isMouseOverSlot(localMouseX, localMouseY, slot.x, slot.y)) continue;

                ItemStack stack = slot.getItem();
                if (stack.isEmpty()) {
                    return ObjectLists.emptyList();
                }

                List<String> lines = gui.getContainerItemTooltipLines(stack);
                if (lines.isEmpty()) {
                    return ObjectLists.emptyList();
                }
                List<LocalizedComponent> tips = new ObjectArrayList<>(lines.size());
                for (String line : lines) {
                    tips.add(() -> line);
                }
                return tips;
            }
        }

        return ObjectLists.emptyList();
    }

    protected final boolean hasBoundSlotAt(int mouseX, int mouseY) {
        if (boundLayouts.isEmpty()) return false;

        int localMouseX = mouseX - gui.getGuiLeftPos();
        int localMouseY = mouseY - gui.getGuiTopPos();

        for (ComponentSlotLayout layout : boundLayouts) {
            List<? extends Slot> slots = layout.getSlots();
            if (slots.isEmpty()) continue;
            for (Slot slot : slots) {
                if (!slot.isActive()) continue;
                if (isMouseOverSlot(localMouseX, localMouseY, slot.x, slot.y)) {
                    return true;
                }
            }
        }

        return false;
    }

    protected final boolean hasAnyBoundSlotAt(int mouseX, int mouseY) {
        if (hasBoundSlotAt(mouseX, mouseY)) {
            return true;
        }
        if (children.isEmpty()) {
            return false;
        }

        for (int i = children.size(); i-- > 0; ) {
            Component child = children.get(i);
            if (!child.isVisible()) continue;
            if (child.hasAnyBoundSlotAt(mouseX, mouseY)) {
                return true;
            }
        }

        return false;
    }

    public <T extends Component> T addTooltip(String s) {
        tooltips.add(() -> s);
        return (T) this;
    }

    public <T extends Component> T addTooltip(LocalizedComponent s) {
        tooltips.add(s);
        return (T) this;
    }

    public <T extends Component> T addTooltip(String key, Supplier<Object[]> supplier) {
        tooltips.add(new Composite(key, supplier));
        return (T) this;
    }

    @NotNull
    public final List<LocalizedComponent> collectTooltip(int mouseX, int mouseY) {
        if (!isVisible() || !contains(mouseX, mouseY)) return ObjectLists.emptyList();
        if (children.isEmpty()) {
            return getTooltip(mouseX, mouseY);
        }

        for (int i = children.size(); i-- > 0; ) {
            Component child = children.get(i);
            if (!child.isVisible() || !child.contains(mouseX, mouseY)) continue;
            return child.collectTooltip(mouseX, mouseY);
        }

        return getTooltip(mouseX, mouseY);
    }

    public final void dispatchGlobalMouseClicked(int mouseX, int mouseY, int button) {
        if (!isVisible()) {
            return;
        }

        onGlobalMouseClicked(mouseX, mouseY, button);
        if (children.isEmpty()) {
            return;
        }

        for (Component child : children) {
            child.dispatchGlobalMouseClicked(mouseX, mouseY, button);
        }
    }

    public final boolean dispatchMouseClicked(int mouseX, int mouseY, int button) {
        if (!isVisible() || !isEnabled() || !contains(mouseX, mouseY)) return false;
        if (children.isEmpty()) {
            boolean handled = onMouseClicked(mouseX, mouseY, button);
            if (handled) {
                promoteDraggableTarget();
            }
            return handled;
        }

        for (int i = children.size(); i-- > 0; ) {
            Component child = children.get(i);
            if (child.dispatchMouseClicked(mouseX, mouseY, button)) {
                bringChildToFront(child);
                promoteDraggableTarget();
                return true;
            }
        }

        boolean handled = onMouseClicked(mouseX, mouseY, button);
        if (handled) {
            promoteDraggableTarget();
        }
        return handled;
    }

    public final boolean dispatchMouseReleased(int mouseX, int mouseY, int button) {
        if (!isVisible() || !isEnabled() || !contains(mouseX, mouseY)) return false;
        if (children.isEmpty()) {
            return onMouseReleased(mouseX, mouseY, button);
        }

        for (int i = children.size(); i-- > 0; ) {
            if (children.get(i).dispatchMouseReleased(mouseX, mouseY, button)) return true;
        }

        return onMouseReleased(mouseX, mouseY, button);
    }

    public final boolean dispatchMouseScrolled(int mouseX, int mouseY, int delta) {
        if (!isVisible() || !isEnabled() || !contains(mouseX, mouseY)) return false;
        if (children.isEmpty()) {
            return onMouseScrolled(mouseX, mouseY, delta);
        }

        for (int i = children.size(); i-- > 0; ) {
            if (children.get(i).dispatchMouseScrolled(mouseX, mouseY, delta)) return true;
        }

        return onMouseScrolled(mouseX, mouseY, delta);
    }

    public final boolean dispatchKeyTyped(char typedChar, int keyCode) {
        if (!isVisible() || !isEnabled()) return false;
        if (children.isEmpty()) {
            return onKeyTyped(typedChar, keyCode);
        }

        for (int i = children.size(); i-- > 0; ) {
            if (children.get(i).dispatchKeyTyped(typedChar, keyCode)) return true;
        }

        return onKeyTyped(typedChar, keyCode);
    }

    protected boolean onMouseClicked(int mouseX, int mouseY, int button) {
        return false;
    }

    protected void onGlobalMouseClicked(int mouseX, int mouseY, int button) {
    }

    protected boolean onMouseReleased(int mouseX, int mouseY, int button) {
        return false;
    }

    protected boolean onMouseScrolled(int mouseX, int mouseY, int delta) {
        return false;
    }

    protected boolean onKeyTyped(char typedChar, int keyCode) {
        return false;
    }

    protected void onMouseEnter() {
    }

    protected void onMouseLeave() {
    }

    public void update() {
        if (children.isEmpty()) {
            return;
        }
        for (Component child : children) {
            child.update();
        }
    }

    public Component bindLayout(ComponentSlotLayout... layout) {
        Collections.addAll(boundLayouts, layout);
        invalidateSubtree();
        syncSlotPositions();
        return this;
    }

    public void syncSlotPositions() {
        if (!update || boundLayouts.isEmpty()) return;
        int ax = getAbsoluteX();
        int ay = getAbsoluteY();
        for (ComponentSlotLayout layout : boundLayouts) {
            layout.syncPositions(ax - gui.getGuiLeftPos(), ay - gui.getGuiTopPos(), isVisible());
        }
    }

    public Component setPosition(int x, int y) {
        this.x = x;
        this.y = y;
        invalidateSubtree();
        syncSlotTreePositions();
        return this;
    }

    private void invalidateSubtree() {
        update = true;
        if (children.isEmpty()) {
            return;
        }
        for (Component child : children) {
            child.invalidateSubtree();
        }
    }

    private void syncSlotTreePositions() {
        syncSlotPositions();
        if (children.isEmpty()) {
            return;
        }
        for (Component child : children) {
            child.syncSlotTreePositions();
        }
    }

    protected final void restoreGuiRenderState() {
        RenderSystemCompat.enableBlend();
        RenderSystemCompat.defaultBlendFunc();
        RenderSystemCompat.disableDepthTest();
        RenderSystemCompat.disableCull();
    }

    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Nullable
    public Component getParent() {
        return parent;
    }

    @Override
    public Rectangle clone() throws AssertionError {
        throw new AssertionError();
    }
}
