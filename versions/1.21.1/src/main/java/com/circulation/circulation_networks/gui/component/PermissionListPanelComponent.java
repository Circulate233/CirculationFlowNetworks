package com.circulation.circulation_networks.gui.component;

import it.unimi.dsi.fastutil.objects.ObjectLists;
import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.api.hub.ChannelSnapshotEntry;
import com.circulation.circulation_networks.api.hub.HubPermissionLevel;
import com.circulation.circulation_networks.api.hub.PermissionSnapshotEntry;
import com.circulation.circulation_networks.api.hub.PermissionSnapshotList;
import com.circulation.circulation_networks.container.ContainerHub;
import com.circulation.circulation_networks.gui.CFNBaseGui;
import com.circulation.circulation_networks.gui.component.base.Component;
import com.circulation.circulation_networks.gui.component.base.ComponentAtlas;
import com.circulation.circulation_networks.gui.component.base.DraggableComponent;
import com.circulation.circulation_networks.packets.UpdateHubChannelPermission;
import com.circulation.circulation_networks.tooltip.LocalizedComponent;
import com.circulation.circulation_networks.utils.CI18n;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class PermissionListPanelComponent extends DraggableComponent implements SliderParent {

    private static final int VIEWPORT_X = 30;
    private static final int VIEWPORT_Y = 31;
    private static final int VIEWPORT_WIDTH = 93;
    private static final int VIEWPORT_HEIGHT = 190;
    private static final int ENTRY_WIDTH = 93;
    private static final int ENTRY_COLLAPSED_HEIGHT = 18;
    private static final int ENTRY_EXPANDED_HEIGHT = 33;
    private static final int ENTRY_SPACING = 1;
    private static final int NAME_WIDTH = 70;
    private static final int TOGGLE_X = 78;
    private static final int TOGGLE_Y = 3;
    private static final int TOGGLE_SIZE = 12;
    private static final int BUTTON_OWNER_X = 20;
    private static final int BUTTON_ADMIN_X = 50;
    private static final int BUTTON_MEMBER_X = 80;
    private static final int BUTTON_Y = 20;
    private static final int BUTTON_SIZE = 8;

    private final ContainerHub container;
    private final SliderComponent slider;
    private final TextFieldComponent searchField;
    private final Object2BooleanMap<UUID> expandedEntries = new Object2BooleanOpenHashMap<>();

    private double lastSliderValue;
    private boolean syncingSlider;
    private boolean hasLastSliderValue;
    private int scrollOffset;

    public PermissionListPanelComponent(int x, int y, CFNBaseGui<?> gui, ContainerHub container) {
        super(x, y, 141, 233, gui);
        this.container = container;
        setSpriteLayers("permission_panel");

        searchField = new TextFieldComponent(29, 9, 103, 18, gui, 32, false).setTextInsets(4, 5, 4, 5);
        addChild(searchField);

        slider = SliderComponent.normalized(this, 124, 31, 190, 0.0d, gui);
        addChild(slider);

        addChild(new Component(VIEWPORT_X, VIEWPORT_Y, VIEWPORT_WIDTH, VIEWPORT_HEIGHT, gui) {
            {
                setZIndex(1000);
            }

            @Override
            protected boolean onMouseScrolled(int mouseX, int mouseY, int delta) {
                return slider.isEnabled() && slider.scroll(delta);
            }

            @Override
            protected boolean onMouseClicked(int mouseX, int mouseY, int button) {
                if (button != 0) {
                    return false;
                }
                EntryHit hit = getEntryHit(mouseX, mouseY);
                if (hit == null) {
                    return false;
                }
                if (hit.toggleHit()) {
                    expandedEntries.put(hit.entry().id(), !expandedEntries.getOrDefault(hit.entry().id(), false));
                    syncScrollState();
                    return true;
                }
                if (!hit.expanded() || hit.buttonTarget() == null || !canEditTarget(hit.entry(), hit.buttonTarget())) {
                    return false;
                }
                HubPermissionLevel newPermission = hit.entry().permission() == hit.buttonTarget() ? HubPermissionLevel.NONE : hit.buttonTarget();
                CirculationFlowNetworks.sendToServer(new UpdateHubChannelPermission(hit.entry().id(), newPermission));
                return true;
            }

            @Override
            protected @NotNull List<LocalizedComponent> getTooltip(int mouseX, int mouseY) {
                EntryHit hit = getEntryHit(mouseX, mouseY);
                if (hit == null) {
                    return ObjectLists.emptyList();
                }
                if (hit.toggleHit()) {
                    return ObjectLists.singleton(() -> hit.expanded()
                        ? CI18n.format("gui.permission_list.tooltip.collapse")
                        : CI18n.format("gui.permission_list.tooltip.expand"));
                }
                if (hit.buttonTarget() != null) {
                    return ObjectLists.singleton(() -> getPermissionTooltip(hit.buttonTarget(), hit.entry().permission()));
                }
                return ObjectLists.emptyList();
            }
        });
    }

    private static String normalizeSearch(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    private static int clamp(int value, int max) {
        return Math.clamp(value, 0, max);
    }

    @Override
    public void update() {
        syncScrollState();
        super.update();
    }

    @Override
    protected void render(int mouseX, int mouseY, float partialTicks) {
        restoreGuiRenderState();
        renderPanelFallback();
        renderSearchBox();

        List<PermissionSnapshotEntry> entries = getFilteredEntries();
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
            int contentY = getAbsoluteY() + VIEWPORT_Y - scrollOffset;
            for (PermissionSnapshotEntry entry : entries) {
                boolean expanded = expandedEntries.getOrDefault(entry.id(), false);
                int entryHeight = expanded ? ENTRY_EXPANDED_HEIGHT : ENTRY_COLLAPSED_HEIGHT;
                if (contentY + entryHeight >= getAbsoluteY() + VIEWPORT_Y && contentY <= getAbsoluteY() + VIEWPORT_Y + VIEWPORT_HEIGHT) {
                    renderEntry(entry, expanded, getAbsoluteX() + VIEWPORT_X, contentY, mouseX, mouseY);
                }
                contentY += entryHeight + ENTRY_SPACING;
            }
        } finally {
            if (guiGraphics != null) {
                guiGraphics.disableScissor();
            }
            restoreGuiRenderState();
        }
    }

    @Override
    protected boolean onMouseScrolled(int mouseX, int mouseY, int delta) {
        return slider.isEnabled() && slider.scroll(delta);
    }

    @Override
    public void onSliderChanged(SliderComponent slider) {
        if (syncingSlider) {
            return;
        }
        syncScrollOffsetFromSlider();
    }

    private void renderPanelFallback() {
        if (ComponentAtlas.INSTANCE.getRegion("permission_panel") == null) {
            GuiGraphics guiGraphics = getCurrentGuiGraphics();
            if (guiGraphics != null) {
                guiGraphics.fill(getAbsoluteX(), getAbsoluteY(), getAbsoluteX() + width, getAbsoluteY() + height, 0xCC1E3447);
            }
            restoreGuiRenderState();
        }
    }

    private void renderSearchBox() {
        if (ComponentAtlas.INSTANCE.getRegion("permission_search_box") != null) {
            renderAtlasSprite("permission_search_box", getAbsoluteX() + 29, getAbsoluteY() + 9, 103, 18);
        }
    }

    private void renderEntry(PermissionSnapshotEntry entry, boolean expanded, int x, int y, int mouseX, int mouseY) {
        restoreGuiRenderState();
        String sprite = expanded ? "permission_entry_expanded" : "permission_entry_collapsed";
        if (ComponentAtlas.INSTANCE.getRegion(sprite) != null) {
            renderAtlasSprite(sprite, x, y, ENTRY_WIDTH, expanded ? ENTRY_EXPANDED_HEIGHT : ENTRY_COLLAPSED_HEIGHT);
        } else {
            GuiGraphics guiGraphics = getCurrentGuiGraphics();
            if (guiGraphics != null) {
                guiGraphics.fill(x, y, x + ENTRY_WIDTH, y + (expanded ? ENTRY_EXPANDED_HEIGHT : ENTRY_COLLAPSED_HEIGHT), 0xCC314E64);
            }
            restoreGuiRenderState();
        }

        drawCenteredText(trimToWidth(entry.name()), x + 4, alignTextY(y, ENTRY_COLLAPSED_HEIGHT));
        renderToggleButton(expanded, x + TOGGLE_X, y + TOGGLE_Y, mouseX, mouseY);

        if (!expanded) {
            return;
        }

        renderPermissionButton(x + BUTTON_OWNER_X, y + BUTTON_Y, HubPermissionLevel.OWNER, entry.permission(), canEditTarget(entry, HubPermissionLevel.OWNER));
        renderPermissionButton(x + BUTTON_ADMIN_X, y + BUTTON_Y, HubPermissionLevel.ADMIN, entry.permission(), canEditTarget(entry, HubPermissionLevel.ADMIN));
        renderPermissionButton(x + BUTTON_MEMBER_X, y + BUTTON_Y, HubPermissionLevel.MEMBER, entry.permission(), canEditTarget(entry, HubPermissionLevel.MEMBER));
    }

    private void renderPermissionButton(int x, int y, HubPermissionLevel buttonPermission, HubPermissionLevel currentPermission, boolean enabled) {
        restoreGuiRenderState();
        boolean active = currentPermission == buttonPermission;
        String sprite = active ? "switch_marker_on" : "switch_marker_off";
        if (ComponentAtlas.INSTANCE.getRegion(sprite) != null) {
            renderAtlasSprite(sprite, x, y, BUTTON_SIZE, BUTTON_SIZE);
        }
    }

    private void renderToggleButton(boolean expanded, int x, int y, int mouseX, int mouseY) {
        restoreGuiRenderState();
        boolean hovered = mouseX >= x && mouseX < x + TOGGLE_SIZE && mouseY >= y && mouseY < y + TOGGLE_SIZE;
        String sprite = expanded
            ? (hovered ? "permission_toggle_on_hovered" : "permission_toggle_on")
            : (hovered ? "permission_toggle_off_hovered" : "permission_toggle_off");
        if (ComponentAtlas.INSTANCE.getRegion(sprite) != null) {
            renderAtlasSprite(sprite, x, y, TOGGLE_SIZE, TOGGLE_SIZE);
        }
    }

    private void syncScrollState() {
        int maxScroll = getMaxScrollOffset();
        boolean enableSlider = maxScroll > 0;
        slider.setEnabled(enableSlider);
        slider.setNormalizedScrollStep(maxScroll <= 0 ? 1.0d : 1.0d / maxScroll);
        if (!enableSlider) {
            syncingSlider = true;
            try {
                slider.setNormalizedValue(0.0d);
            } finally {
                syncingSlider = false;
            }
            hasLastSliderValue = false;
            scrollOffset = 0;
            return;
        }
        syncScrollOffsetFromSlider();
    }

    private void syncScrollOffsetFromSlider() {
        int maxScroll = getMaxScrollOffset();
        double normalizedValue = slider.getNormalizedValue();
        if (hasLastSliderValue && Math.abs(lastSliderValue - normalizedValue) < 0.000001d) {
            return;
        }
        hasLastSliderValue = true;
        lastSliderValue = normalizedValue;
        scrollOffset = clamp((int) Math.round(normalizedValue * maxScroll), maxScroll);
    }

    private int getMaxScrollOffset() {
        return Math.max(0, getContentHeight() - VIEWPORT_HEIGHT);
    }

    private int getContentHeight() {
        List<PermissionSnapshotEntry> entries = getFilteredEntries();
        if (entries.isEmpty()) {
            return 0;
        }
        int height = -ENTRY_SPACING;
        for (PermissionSnapshotEntry entry : entries) {
            height += (expandedEntries.getOrDefault(entry.id(), false) ? ENTRY_EXPANDED_HEIGHT : ENTRY_COLLAPSED_HEIGHT) + ENTRY_SPACING;
        }
        return Math.max(0, height);
    }

    private List<PermissionSnapshotEntry> getFilteredEntries() {
        PermissionSnapshotList list = container.onlinePlayers;
        if (list == null) {
            return ObjectLists.emptyList();
        }

        String search = normalizeSearch(searchField.getText());
        if (search.isEmpty()) {
            return list.getEntries();
        }

        List<PermissionSnapshotEntry> filtered = new ObjectArrayList<>();
        for (PermissionSnapshotEntry entry : list.getEntries()) {
            if (entry.name().toLowerCase(Locale.ROOT).startsWith(search)) {
                filtered.add(entry);
            }
        }
        return filtered;
    }

    private @Nullable EntryHit getEntryHit(int mouseX, int mouseY) {
        int viewportAbsoluteX = getAbsoluteX() + VIEWPORT_X;
        int viewportAbsoluteY = getAbsoluteY() + VIEWPORT_Y;
        int contentY = viewportAbsoluteY - scrollOffset;
        for (PermissionSnapshotEntry entry : getFilteredEntries()) {
            boolean expanded = expandedEntries.getOrDefault(entry.id(), false);
            int entryHeight = expanded ? ENTRY_EXPANDED_HEIGHT : ENTRY_COLLAPSED_HEIGHT;
            if (mouseX >= viewportAbsoluteX && mouseX < viewportAbsoluteX + ENTRY_WIDTH && mouseY >= contentY && mouseY < contentY + entryHeight) {
                return EntryHit.create(entry, expanded, viewportAbsoluteX, contentY, mouseX, mouseY);
            }
            contentY += entryHeight + ENTRY_SPACING;
        }
        return null;
    }

    private boolean canEditTarget(PermissionSnapshotEntry entry, HubPermissionLevel targetPermission) {
        if (targetPermission == HubPermissionLevel.OWNER || entry.permission() == HubPermissionLevel.OWNER) {
            return false;
        }
        if (container.hasChannelManagementOverride()) {
            return true;
        }
        HubPermissionLevel self = getCurrentUserPermission();
        return switch (self) {
            case OWNER -> targetPermission != HubPermissionLevel.OWNER;
            case ADMIN ->
                entry.permission() != HubPermissionLevel.ADMIN && targetPermission == HubPermissionLevel.MEMBER;
            default -> false;
        };
    }

    private HubPermissionLevel getCurrentUserPermission() {
        if (container.availableChannels == null) {
            return HubPermissionLevel.NONE;
        }
        for (ChannelSnapshotEntry entry : container.availableChannels.getEntries()) {
            if (entry.id().equals(container.channelId)) {
                return entry.permission();
            }
        }
        return HubPermissionLevel.NONE;
    }

    private String getPermissionTooltip(HubPermissionLevel target, HubPermissionLevel current) {
        return switch (target) {
            case OWNER -> current == HubPermissionLevel.OWNER
                ? CI18n.format("gui.permission_list.tooltip.owner_locked")
                : CI18n.format("gui.permission_list.tooltip.owner_unset");
            case ADMIN -> current == HubPermissionLevel.ADMIN
                ? CI18n.format("gui.permission_list.tooltip.admin_remove")
                : CI18n.format("gui.permission_list.tooltip.admin_set");
            case MEMBER -> current == HubPermissionLevel.MEMBER
                ? CI18n.format("gui.permission_list.tooltip.member_remove")
                : CI18n.format("gui.permission_list.tooltip.member_set");
            default -> CI18n.format("gui.permission_list.tooltip.unset");
        };
    }

    private void drawCenteredText(String text, int x, int y) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        GuiGraphics guiGraphics = getCurrentGuiGraphics();
        if (guiGraphics == null) {
            return;
        }
        int drawX = x + Math.max(0, (PermissionListPanelComponent.NAME_WIDTH - mc.font.width(text)) / 2);
        guiGraphics.drawString(mc.font, text, drawX, y, 0xE8F6FF, false);
    }

    private String trimToWidth(String text) {
        return Minecraft.getInstance().font.plainSubstrByWidth(text == null ? "" : text, PermissionListPanelComponent.NAME_WIDTH);
    }

    private record EntryHit(PermissionSnapshotEntry entry, boolean expanded, boolean toggleHit,
                            HubPermissionLevel buttonTarget) {

        private static EntryHit create(PermissionSnapshotEntry entry, boolean expanded, int x, int y, int mouseX, int mouseY) {
            if (mouseX >= x + TOGGLE_X && mouseX < x + TOGGLE_X + TOGGLE_SIZE && mouseY >= y + TOGGLE_Y && mouseY < y + TOGGLE_Y + TOGGLE_SIZE) {
                return new EntryHit(entry, expanded, true, null);
            }
            if (!expanded) {
                return new EntryHit(entry, false, false, null);
            }
            if (containsButton(mouseX, mouseY, x + BUTTON_OWNER_X, y + BUTTON_Y)) {
                return new EntryHit(entry, true, false, HubPermissionLevel.OWNER);
            }
            if (containsButton(mouseX, mouseY, x + BUTTON_ADMIN_X, y + BUTTON_Y)) {
                return new EntryHit(entry, true, false, HubPermissionLevel.ADMIN);
            }
            if (containsButton(mouseX, mouseY, x + BUTTON_MEMBER_X, y + BUTTON_Y)) {
                return new EntryHit(entry, true, false, HubPermissionLevel.MEMBER);
            }
            return new EntryHit(entry, true, false, null);
        }

        private static boolean containsButton(int mouseX, int mouseY, int x, int y) {
            return mouseX >= x && mouseX < x + BUTTON_SIZE && mouseY >= y && mouseY < y + BUTTON_SIZE;
        }
    }
}
