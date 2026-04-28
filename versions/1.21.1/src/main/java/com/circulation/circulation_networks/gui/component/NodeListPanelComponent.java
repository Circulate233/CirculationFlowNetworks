package com.circulation.circulation_networks.gui.component;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.api.hub.NodeSnapshotEntry;
import com.circulation.circulation_networks.api.hub.NodeSnapshotList;
import com.circulation.circulation_networks.client.render.ClientAnimationTicker;
import com.circulation.circulation_networks.container.ContainerHub;
import com.circulation.circulation_networks.gui.CFNBaseGui;
import com.circulation.circulation_networks.gui.component.base.Component;
import com.circulation.circulation_networks.gui.component.base.ComponentAtlas;
import com.circulation.circulation_networks.gui.component.base.DraggableComponent;
import com.circulation.circulation_networks.handlers.NodeHighlightRenderingHandler;
import com.circulation.circulation_networks.packets.UpdateNodeCustomName;
import com.circulation.circulation_networks.tooltip.LocalizedComponent;
import com.circulation.circulation_networks.utils.CI18n;
import com.circulation.circulation_networks.utils.FormatNumberUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class NodeListPanelComponent extends DraggableComponent implements SliderParent {

    private static final String PANEL_SPRITE = "node_list_ui";
    private static final String HEADER_SPRITE = "node_list_link";
    private static final String ENTRY_SPRITE = "node_list_entry";

    private static final int HEADER_X = 9;
    private static final int HEADER_Y = 9;
    private static final int HEADER_WIDTH = 103;
    private static final int HEADER_HEIGHT = 18;
    private static final int HEADER_COUNT_X = 19;
    private static final int HEADER_COUNT_Y = 5;
    private static final int HEADER_COUNT_WIDTH = 80;

    private static final int VIEWPORT_X = 10;
    private static final int VIEWPORT_Y = 31;
    private static final int VIEWPORT_WIDTH = 92;
    private static final int VIEWPORT_HEIGHT = 190;

    private static final int SLIDER_X = 104;
    private static final int SLIDER_Y = 31;
    private static final int SLIDER_HEIGHT = 189;

    private static final int ENTRY_WIDTH = 84;
    private static final int ENTRY_HEIGHT = 33;
    private static final int ENTRY_SPACING = 1;
    private static final int ENTRY_PITCH = ENTRY_HEIGHT + ENTRY_SPACING;
    private static final int MAX_VISIBLE_ENTRIES_WITHOUT_SCROLL = VIEWPORT_HEIGHT / ENTRY_PITCH;

    private static final int ITEM_X = 2;
    private static final int ITEM_Y = 2;
    private static final float ITEM_RENDER_SIZE = 13.5F;
    private static final float ITEM_NATIVE_SIZE = 16.0F;
    private static final float ITEM_RENDER_SCALE = ITEM_RENDER_SIZE / ITEM_NATIVE_SIZE;
    private static final float ITEM_RENDER_SHIFT_X = -0.5F;
    private static final float ITEM_RENDER_SHIFT_Y = 0.5F;
    private static final int NAME_X = 19;
    private static final int NAME_Y = 5;
    private static final int NAME_WIDTH = 61;
    private static final int NAME_FIELD_HEIGHT = 10;
    private static final int NAME_MAX_LENGTH = 32;
    private static final int COORD_X = 4;
    private static final int COORD_Y = 19;
    private static final int COORD_WIDTH = 76;
    private static final int TEXT_COLOR = 0xE8F6FF;
    private static final int FALLBACK_PANEL_COLOR = 0xCC243949;
    private static final int FALLBACK_ENTRY_COLOR = 0xCC36586D;

    private static final int ICON_HIT_SIZE = 14;
    private static final long DOUBLE_CLICK_TICKS = 8L;

    private final ContainerHub container;
    private final SliderComponent slider;
    private final EditableNameField[] nameFields = new EditableNameField[MAX_VISIBLE_ENTRIES_WITHOUT_SCROLL];
    private final Object2ObjectMap<String, ItemStack> stackCache = new Object2ObjectOpenHashMap<>();

    private int firstVisibleEntryIndex;
    private double lastSliderValue;
    private boolean syncingSlider;
    private boolean hasLastSliderValue;

    private int lastIconClickSlot = -1;
    private long lastIconClickTick;

    public NodeListPanelComponent(int x, int y, CFNBaseGui<?> gui, ContainerHub container) {
        super(x, y, 141, 233, gui);
        this.container = container;
        setSpriteLayers(PANEL_SPRITE);
        this.slider = SliderComponent.normalized(this, SLIDER_X, SLIDER_Y, SLIDER_HEIGHT, 0.0d, gui);
        addChild(slider);
        addChild(new Component(HEADER_X, HEADER_Y, HEADER_WIDTH, HEADER_HEIGHT, gui) {
            {
                setSpriteLayers(HEADER_SPRITE);
                setEnabled(false);
            }

            @Override
            public boolean contains(int mouseX, int mouseY) {
                return false;
            }

            @Override
            protected void render(int mouseX, int mouseY, float partialTicks) {
                renderHeaderFallback(getAbsoluteX(), getAbsoluteY());
                renderHeaderCountText(getAbsoluteX() + HEADER_COUNT_X, getAbsoluteY() + HEADER_COUNT_Y);
            }
        });
        addChild(new Component(VIEWPORT_X, VIEWPORT_Y, VIEWPORT_WIDTH, VIEWPORT_HEIGHT, gui) {
            {
                setZIndex(1000);
            }

            @Override
            protected boolean onMouseScrolled(int mouseX, int mouseY, int delta) {
                return slider.isEnabled() && slider.scroll(delta);
            }

            @Override
            protected @NotNull List<LocalizedComponent> getTooltip(int mouseX, int mouseY) {
                int hitSlot = getIconVisibleSlot(mouseX, mouseY);
                if (hitSlot >= 0) {
                    return Collections.singletonList(
                        () -> I18n.get("tooltip.circulation_networks.double_click_locate")
                    );
                }
                return super.getTooltip(mouseX, mouseY);
            }

            @Override
            protected boolean onMouseClicked(int mouseX, int mouseY, int button) {
                if (button != 0) {
                    return false;
                }
                int hitSlot = getIconVisibleSlot(mouseX, mouseY);
                if (hitSlot < 0) {
                    return false;
                }
                long now = ClientAnimationTicker.ticks();
                if (lastIconClickSlot == hitSlot && now - lastIconClickTick < DOUBLE_CLICK_TICKS) {
                    lastIconClickSlot = -1;
                    lastIconClickTick = 0L;
                    handleIconDoubleClick(hitSlot);
                    return true;
                }
                lastIconClickSlot = hitSlot;
                lastIconClickTick = now;
                return true;
            }
        });
        for (int i = 0; i < nameFields.length; i++) {
            EditableNameField field = new EditableNameField(
                i,
                VIEWPORT_X + NAME_X,
                VIEWPORT_Y + NAME_Y + i * ENTRY_PITCH,
                NAME_WIDTH,
                NAME_FIELD_HEIGHT,
                gui
            );
            field.setBackgroundDrawing(false);
            field.setVisible(false);
            nameFields[i] = field;
            addChild(field);
        }
        syncVisibleNameFields();
    }

    private static int clamp(int value, int max) {
        return Math.clamp(value, 0, max);
    }

    private static String normalizeEditableName(String name) {
        if (name == null) {
            return "";
        }
        String trimmed = name.trim();
        return trimmed.isEmpty() ? "" : trimmed;
    }

    @Override
    public void update() {
        syncScrollState();
        syncVisibleNameFields();
        super.update();
    }

    @Override
    protected void render(int mouseX, int mouseY, float partialTicks) {
        renderPanelFallback();

        List<NodeSnapshotEntry> entries = getEntries();
        syncVisibleNameFields();
        if (entries.isEmpty()) {
            return;
        }

        GuiGraphics guiGraphics = getCurrentGuiGraphics();
        if (guiGraphics != null) {
            guiGraphics.enableScissor(
                getAbsoluteX() + VIEWPORT_X,
                getAbsoluteY() + VIEWPORT_Y,
                getAbsoluteX() + VIEWPORT_X + VIEWPORT_WIDTH,
                getAbsoluteY() + VIEWPORT_Y + VIEWPORT_HEIGHT
            );
        }
        try {
            int viewportAbsoluteX = getAbsoluteX() + VIEWPORT_X;
            int viewportAbsoluteY = getAbsoluteY() + VIEWPORT_Y;
            int startIndex = firstVisibleEntryIndex;
            int endIndex = Math.min(entries.size(), startIndex + MAX_VISIBLE_ENTRIES_WITHOUT_SCROLL);
            for (int index = startIndex; index < endIndex; index++) {
                int entryY = viewportAbsoluteY + (index - startIndex) * ENTRY_PITCH;
                renderEntry(entries.get(index), viewportAbsoluteX, entryY);
            }
        } finally {
            if (guiGraphics != null) {
                guiGraphics.disableScissor();
            }
        }
    }

    @Override
    protected boolean onMouseScrolled(int mouseX, int mouseY, int delta) {
        if (!slider.isEnabled()) {
            return false;
        }
        return slider.scroll(delta);
    }

    @Override
    public void onSliderChanged(SliderComponent slider) {
        if (syncingSlider) {
            return;
        }
        syncScrollOffsetFromSlider();
    }

    private void renderPanelFallback() {
        if (ComponentAtlas.INSTANCE.getRegion(PANEL_SPRITE) == null) {
            int panelAbsoluteX = getAbsoluteX();
            int panelAbsoluteY = getAbsoluteY();
            GuiGraphics guiGraphics = getCurrentGuiGraphics();
            if (guiGraphics != null) {
                guiGraphics.fill(panelAbsoluteX, panelAbsoluteY, panelAbsoluteX + width, panelAbsoluteY + height, FALLBACK_PANEL_COLOR);
            }
        }
    }

    private void renderHeaderFallback(int x, int y) {
        if (ComponentAtlas.INSTANCE.getRegion(HEADER_SPRITE) == null) {
            GuiGraphics guiGraphics = getCurrentGuiGraphics();
            if (guiGraphics != null) {
                guiGraphics.fill(x, y, x + HEADER_WIDTH, y + HEADER_HEIGHT, FALLBACK_ENTRY_COLOR);
            }
        }
    }

    private void renderHeaderCountText(int x, int y) {
        GuiGraphics guiGraphics = getCurrentGuiGraphics();
        if (guiGraphics == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        String text = CI18n.format("gui.hub.node_count", FormatNumberUtils.formatNumber(getEntries().size()));
        int textWidth = mc.font.width(text);
        int drawX = x + Math.max(0, (HEADER_COUNT_WIDTH - textWidth) / 2);
        guiGraphics.drawString(mc.font, text, drawX, y, TEXT_COLOR, false);
    }

    private void renderEntry(NodeSnapshotEntry entry, int entryAbsoluteX, int entryAbsoluteY) {
        if (!renderSpriteIfPresent(entryAbsoluteX, entryAbsoluteY)) {
            GuiGraphics guiGraphics = getCurrentGuiGraphics();
            if (guiGraphics != null) {
                guiGraphics.fill(entryAbsoluteX, entryAbsoluteY, entryAbsoluteX + ENTRY_WIDTH, entryAbsoluteY + ENTRY_HEIGHT, FALLBACK_ENTRY_COLOR);
            }
        }

        renderEntryItem(entry, entryAbsoluteX + ITEM_X, entryAbsoluteY + ITEM_Y);

        Minecraft mc = Minecraft.getInstance();
        String coords = mc.font.plainSubstrByWidth(formatCoordinates(entry), COORD_WIDTH);

        drawCenteredText(coords, entryAbsoluteX + COORD_X, entryAbsoluteY + COORD_Y, mc);
    }

    private void renderEntryItem(NodeSnapshotEntry entry, int x, int y) {
        ItemStack stack = resolveStack(entry.itemId());
        if (stack.isEmpty()) {
            return;
        }

        GuiGraphics guiGraphics = getCurrentGuiGraphics();
        if (guiGraphics == null) {
            return;
        }

        restoreGuiRenderState();
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x + ITEM_RENDER_SHIFT_X, y + ITEM_RENDER_SHIFT_Y, 150.0F);
        guiGraphics.pose().scale(ITEM_RENDER_SCALE, ITEM_RENDER_SCALE, 1.0F);
        guiGraphics.renderItem(stack, 0, 0);
        guiGraphics.renderItemDecorations(Minecraft.getInstance().font, stack, 0, 0, getItemCountOverlayText(stack));
        guiGraphics.pose().popPose();
        restoreGuiRenderState();
    }

    private void drawCenteredText(String text, int x, int y, Minecraft mc) {
        if (text.isEmpty()) {
            return;
        }
        GuiGraphics guiGraphics = getCurrentGuiGraphics();
        if (guiGraphics == null) {
            return;
        }
        int textWidth = mc.font.width(text);
        int drawX = x + Math.max(0, (NodeListPanelComponent.COORD_WIDTH - textWidth) / 2);
        guiGraphics.drawString(mc.font, text, drawX, y, TEXT_COLOR, false);
    }

    private void syncScrollState() {
        int maxScrollIndex = getMaxScrollIndex();
        boolean enableSlider = getEntries().size() > MAX_VISIBLE_ENTRIES_WITHOUT_SCROLL && maxScrollIndex > 0;
        slider.setNormalizedScrollStep(maxScrollIndex <= 0 ? 1.0d : 1.0d / maxScrollIndex);
        slider.setEnabled(enableSlider);
        if (!enableSlider) {
            syncingSlider = true;
            try {
                slider.setNormalizedValue(0.0d);
            } finally {
                syncingSlider = false;
            }
            hasLastSliderValue = false;
            setFirstVisibleEntryIndex(0);
            return;
        }
        syncScrollOffsetFromSlider();
    }

    private void syncScrollOffsetFromSlider() {
        double normalizedValue = slider.getNormalizedValue();
        if (hasLastSliderValue && Math.abs(lastSliderValue - normalizedValue) < 0.000001d) {
            return;
        }
        hasLastSliderValue = true;
        lastSliderValue = normalizedValue;
        setFirstVisibleEntryIndex((int) Math.round(normalizedValue * getMaxScrollIndex()));
    }

    private void setFirstVisibleEntryIndex(int newIndex) {
        int clamped = clamp(newIndex, getMaxScrollIndex());
        if (firstVisibleEntryIndex != clamped) {
            commitVisibleNameFields();
        }
        firstVisibleEntryIndex = clamped;
        syncVisibleNameFields();
    }

    private int getMaxScrollIndex() {
        int entryCount = getEntries().size();
        return Math.max(0, entryCount - MAX_VISIBLE_ENTRIES_WITHOUT_SCROLL);
    }

    private List<NodeSnapshotEntry> getEntries() {
        NodeSnapshotList list = container.nodes;
        return list == null ? Collections.emptyList() : list.getEntries();
    }

    private void syncVisibleNameFields() {
        List<NodeSnapshotEntry> entries = getEntries();
        for (int visibleIndex = 0; visibleIndex < nameFields.length; visibleIndex++) {
            EditableNameField field = nameFields[visibleIndex];
            int entryIndex = firstVisibleEntryIndex + visibleIndex;
            if (entryIndex >= entries.size()) {
                field.clearEntry();
                continue;
            }
            NodeSnapshotEntry entry = entries.get(entryIndex);
            field.bindEntry(entryIndex, entry, resolveEntryDisplayName(entry));
        }
    }

    private String resolveEntryDisplayName(NodeSnapshotEntry entry) {
        String name = normalizeEditableName(entry.customName());
        if (!name.isEmpty()) {
            return name;
        }
        ItemStack stack = resolveStack(entry.itemId());
        if (stack.isEmpty()) {
            return "";
        }
        return stack.getHoverName().getString();
    }

    private void commitVisibleNameFields() {
        for (EditableNameField field : nameFields) {
            field.commitIfDirty();
        }
    }

    private ItemStack resolveStack(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack cached = stackCache.get(itemId);
        if (cached != null) {
            return cached;
        }
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
        ItemStack stack = item == null ? ItemStack.EMPTY : new ItemStack(item);
        stackCache.put(itemId, stack);
        return stack;
    }

    private String formatCoordinates(NodeSnapshotEntry entry) {
        return entry.x() + "," + entry.y() + "," + entry.z();
    }

    private boolean renderSpriteIfPresent(int x, int y) {
        if (ComponentAtlas.INSTANCE.getRegion(NodeListPanelComponent.ENTRY_SPRITE) == null) {
            return false;
        }
        renderAtlasSprite(NodeListPanelComponent.ENTRY_SPRITE, x, y, NodeListPanelComponent.ENTRY_WIDTH, NodeListPanelComponent.ENTRY_HEIGHT);
        return true;
    }

    private int getIconVisibleSlot(int mouseX, int mouseY) {
        int absIconX = getAbsoluteX() + VIEWPORT_X + ITEM_X;
        int absIconBaseY = getAbsoluteY() + VIEWPORT_Y + ITEM_Y;
        if (mouseX < absIconX || mouseX >= absIconX + ICON_HIT_SIZE) {
            return -1;
        }
        List<NodeSnapshotEntry> entries = getEntries();
        for (int i = 0; i < MAX_VISIBLE_ENTRIES_WITHOUT_SCROLL; i++) {
            int entryIndex = firstVisibleEntryIndex + i;
            if (entryIndex >= entries.size()) {
                break;
            }
            int iconY = absIconBaseY + i * ENTRY_PITCH;
            if (mouseY >= iconY && mouseY < iconY + ICON_HIT_SIZE) {
                return i;
            }
        }
        return -1;
    }

    private void handleIconDoubleClick(int visibleSlot) {
        List<NodeSnapshotEntry> entries = getEntries();
        int entryIndex = firstVisibleEntryIndex + visibleSlot;
        if (entryIndex < 0 || entryIndex >= entries.size()) {
            return;
        }
        NodeSnapshotEntry entry = entries.get(entryIndex);
        BlockPos pos = new BlockPos(entry.x(), entry.y(), entry.z());

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            NodeHighlightRenderingHandler.INSTANCE.highlight(pos, mc.player.level().dimension().location().hashCode());
            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§e[CFN] §r" + I18n.get("message.circulation_networks.node_location",
                    entry.x(), entry.y(), entry.z())
            ));
        }
        if (mc.screen != null) {
            mc.screen.onClose();
        } else if (mc.player != null) {
            mc.player.closeContainer();
        }
    }

    private static final class EditableNameField extends TextFieldComponent {

        private int boundEntryIndex = -1;
        private BlockPos boundPos;
        private String committedText = "";

        private EditableNameField(int visibleIndex, int x, int y, int width, int height, CFNBaseGui<?> gui) {
            super(x, y, width, height, gui, NAME_MAX_LENGTH, false);
            setZIndex(visibleIndex + 1);
        }

        private void bindEntry(int entryIndex, NodeSnapshotEntry entry, String resolvedName) {
            BlockPos entryPos = new BlockPos(entry.x(), entry.y(), entry.z());
            boolean changedBinding = boundEntryIndex != entryIndex || !Objects.equals(boundPos, entryPos);
            if (changedBinding) {
                commitIfDirty();
                boundEntryIndex = entryIndex;
                boundPos = entryPos;
                committedText = resolvedName;
                setText(resolvedName);
                setFocused(false);
            } else if (!isFocused() && !Objects.equals(getText(), resolvedName)) {
                committedText = resolvedName;
                setText(resolvedName);
            }
            setVisible(true);
        }

        private void clearEntry() {
            commitIfDirty();
            boundEntryIndex = -1;
            boundPos = null;
            committedText = "";
            if (isFocused()) {
                setFocused(false);
            }
            if (!getText().isEmpty()) {
                setText("");
            }
            setVisible(false);
        }

        private void commitIfDirty() {
            if (boundPos == null) {
                return;
            }
            String normalized = normalizeEditableName(getText());
            if (!Objects.equals(getText(), normalized)) {
                setText(normalized);
            }
            if (Objects.equals(committedText, normalized)) {
                return;
            }
            CirculationFlowNetworks.sendToServer(new UpdateNodeCustomName(boundPos, normalized));
            committedText = normalized;
        }

        @Override
        protected boolean onKeyTyped(char typedChar, int keyCode) {
            boolean handled = super.onKeyTyped(typedChar, keyCode);
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                commitIfDirty();
                setFocused(false);
                return true;
            }
            return handled;
        }

        @Override
        protected void onGlobalMouseClicked(int mouseX, int mouseY, int button) {
            boolean wasFocused = isFocused();
            super.onGlobalMouseClicked(mouseX, mouseY, button);
            if (wasFocused && !isFocused()) {
                commitIfDirty();
            }
        }
    }
}
