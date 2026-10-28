package com.circulation.circulation_networks.gui.component;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.api.hub.ChannelSnapshotEntry;
import com.circulation.circulation_networks.api.hub.ChannelSnapshotList;
import com.circulation.circulation_networks.api.hub.PermissionMode;
import com.circulation.circulation_networks.client.compat.GuiGraphicsCompat;
import com.circulation.circulation_networks.container.ContainerHub;
import com.circulation.circulation_networks.gui.CFNBaseGui;
import com.circulation.circulation_networks.gui.component.base.Component;
import com.circulation.circulation_networks.gui.component.base.ComponentAtlas;
import com.circulation.circulation_networks.gui.component.base.DraggableComponent;
import com.circulation.circulation_networks.packets.BindHubChannel;
import com.circulation.circulation_networks.tooltip.LocalizedComponent;
import com.circulation.circulation_networks.utils.CI18n;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

public final class ChannelListPanelComponent extends DraggableComponent implements SliderParent {

    private static final int VIEWPORT_X = 30;
    private static final int VIEWPORT_Y = 31;
    private static final int VIEWPORT_WIDTH = 93;
    private static final int VIEWPORT_HEIGHT = 169;
    private static final int ENTRY_WIDTH = 93;
    private static final int ENTRY_HEIGHT = 18;
    private static final int ENTRY_PITCH = ENTRY_HEIGHT + 1;
    private static final int MAX_VISIBLE_ENTRIES = 9;
    private static final int ENTRY_TEXT_WIDTH = 70;
    private static final int FILTER_WIDTH = 33;

    private final ContainerHub container;
    private final SliderComponent slider;
    private final ModeFilterToggle publicFilter;
    private final ModeFilterToggle teamFilter;
    private final ModeFilterToggle privateFilter;

    private int firstVisibleEntryIndex;
    private double lastSliderValue;
    private boolean syncingSlider;
    private boolean hasLastSliderValue;

    public ChannelListPanelComponent(int x, int y, CFNBaseGui<?> gui, ContainerHub container, Runnable openCreateDialog) {
        super(x, y, 141, 233, gui);
        this.container = container;
        Runnable createAction = openCreateDialog != null ? openCreateDialog : ButtonComponent.EMPTY;
        setSpriteLayers("channel_panel");

        addChild(publicFilter = new ModeFilterToggle(29, 9, "switch_public", PermissionMode.PUBLIC, gui));
        addChild(teamFilter = new ModeFilterToggle(29 + FILTER_WIDTH + 2, 9, "switch_team", PermissionMode.TEAM, gui));
        addChild(privateFilter = new ModeFilterToggle(29 + (FILTER_WIDTH + 2) * 2, 9, "switch_private", PermissionMode.PRIVATE, gui));

        slider = SliderComponent.normalized(this, 124, 31, 168, 0.0d, gui);
        addChild(slider);

        addChild(new ButtonComponent(29, 203, 103, 18, gui, "channel_create", createAction));
        addChild(new Component(29, 203, 103, 18, gui) {
            @Override
            public boolean contains(int mouseX, int mouseY) {
                return false;
            }
        }.setEnabled(false));

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
                ChannelSnapshotEntry entry = getEntryAt(mouseX, mouseY);
                if (entry == null || entry.connected()) {
                    return false;
                }
                CirculationFlowNetworks.sendToServer(new BindHubChannel(entry.id()));
                return true;
            }

            @Override
            protected @NotNull List<LocalizedComponent> getTooltip(int mouseX, int mouseY) {
                ChannelSnapshotEntry entry = getEntryAt(mouseX, mouseY);
                if (entry == null) {
                    return ObjectLists.emptyList();
                }
                return ObjectLists.singleton(() -> entry.connected()
                    ? CI18n.format("gui.channel_list.tooltip.connected")
                    : CI18n.format("gui.channel_list.tooltip.switch"));
            }
        });
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
        renderPanelFallback();

        List<ChannelSnapshotEntry> entries = getFilteredEntries();
        if (entries.isEmpty()) {
            return;
        }

        GuiGraphicsExtractor guiGraphics = getCurrentGuiGraphics();
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
            int endIndex = Math.min(entries.size(), firstVisibleEntryIndex + MAX_VISIBLE_ENTRIES);
            for (int index = firstVisibleEntryIndex; index < endIndex; index++) {
                ChannelSnapshotEntry entry = entries.get(index);
                int entryY = viewportAbsoluteY + (index - firstVisibleEntryIndex) * ENTRY_PITCH;
                renderEntry(entry, viewportAbsoluteX, entryY);
            }
        } finally {
            if (guiGraphics != null) {
                guiGraphics.disableScissor();
            }
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
        if (ComponentAtlas.INSTANCE.getRegion("channel_panel") == null) {
            GuiGraphicsExtractor guiGraphics = getCurrentGuiGraphics();
            if (guiGraphics != null) {
                guiGraphics.fill(getAbsoluteX(), getAbsoluteY(), getAbsoluteX() + width, getAbsoluteY() + height, 0xCC1E3447);
            }
        }
    }

    private void renderEntry(ChannelSnapshotEntry entry, int x, int y) {
        String sprite = getEntrySprite(entry);
        if (ComponentAtlas.INSTANCE.getRegion(sprite) != null) {
            renderAtlasSprite(sprite, x, y, ENTRY_WIDTH, ENTRY_HEIGHT);
        } else {
            GuiGraphicsExtractor guiGraphics = getCurrentGuiGraphics();
            if (guiGraphics != null) {
                guiGraphics.fill(x, y, x + ENTRY_WIDTH, y + ENTRY_HEIGHT, 0xCC3D6176);
            }
        }
        drawCenteredText(trimToWidth(entry.name()), x + 19, alignTextY(y, ENTRY_HEIGHT));
    }

    private String getEntrySprite(ChannelSnapshotEntry entry) {
        return switch (entry.permissionMode()) {
            case PUBLIC -> entry.connected() ? "channel_entry_public_connected" : "channel_entry_public";
            case TEAM -> entry.connected() ? "channel_entry_team_connected" : "channel_entry_team";
            default -> entry.connected() ? "channel_entry_private_connected" : "channel_entry_private";
        };
    }

    private @Nullable ChannelSnapshotEntry getEntryAt(int mouseX, int mouseY) {
        List<ChannelSnapshotEntry> entries = getFilteredEntries();
        if (entries.isEmpty()) {
            return null;
        }
        int relativeY = mouseY - (getAbsoluteY() + VIEWPORT_Y);
        if (relativeY < 0) {
            return null;
        }
        int visibleIndex = relativeY / ENTRY_PITCH;
        if (visibleIndex < 0 || visibleIndex >= MAX_VISIBLE_ENTRIES) {
            return null;
        }
        int entryIndex = firstVisibleEntryIndex + visibleIndex;
        if (entryIndex < 0 || entryIndex >= entries.size()) {
            return null;
        }
        int rowTop = visibleIndex * ENTRY_PITCH;
        if (relativeY - rowTop >= ENTRY_HEIGHT) {
            return null;
        }
        return entries.get(entryIndex);
    }

    private void syncScrollState() {
        List<ChannelSnapshotEntry> entries = getFilteredEntries();
        int maxScrollIndex = Math.max(0, entries.size() - MAX_VISIBLE_ENTRIES);
        boolean enableSlider = maxScrollIndex > 0;
        slider.setEnabled(enableSlider);
        slider.setNormalizedScrollStep(maxScrollIndex == 0 ? 1.0d : 1.0d / maxScrollIndex);
        if (!enableSlider) {
            syncingSlider = true;
            try {
                slider.setNormalizedValue(0.0d);
            } finally {
                syncingSlider = false;
            }
            hasLastSliderValue = false;
            firstVisibleEntryIndex = 0;
            return;
        }
        syncScrollOffsetFromSlider();
    }

    private void syncScrollOffsetFromSlider() {
        int maxScrollIndex = Math.max(0, getFilteredEntries().size() - MAX_VISIBLE_ENTRIES);
        double normalizedValue = slider.getNormalizedValue();
        if (hasLastSliderValue && Math.abs(lastSliderValue - normalizedValue) < 0.000001d) {
            return;
        }
        hasLastSliderValue = true;
        lastSliderValue = normalizedValue;
        firstVisibleEntryIndex = clamp((int) Math.round(normalizedValue * maxScrollIndex), maxScrollIndex);
    }

    private List<ChannelSnapshotEntry> getFilteredEntries() {
        ChannelSnapshotList list = container.availableChannels;
        if (list == null) {
            return ObjectLists.emptyList();
        }

        List<ChannelSnapshotEntry> filtered = new ObjectArrayList<>();
        for (ChannelSnapshotEntry entry : list.getEntries()) {
            if (!isModeVisible(entry.permissionMode())) {
                continue;
            }
            filtered.add(entry);
        }
        filtered.sort(Comparator.comparing(ChannelSnapshotEntry::connected).reversed()
                                .thenComparing(ChannelSnapshotEntry::name, String.CASE_INSENSITIVE_ORDER)
                                .thenComparing(entry -> entry.id().toString()));
        return filtered;
    }

    private boolean isModeVisible(PermissionMode permissionMode) {
        return switch (permissionMode) {
            case PUBLIC -> publicFilter.active;
            case TEAM -> teamFilter.active;
            default -> privateFilter.active;
        };
    }

    private void drawCenteredText(String text, int x, int y) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        GuiGraphicsExtractor guiGraphics = getCurrentGuiGraphics();
        if (guiGraphics == null) {
            return;
        }
        int drawX = x + Math.max(0, (ChannelListPanelComponent.ENTRY_TEXT_WIDTH - mc.font.width(text)) / 2);
        GuiGraphicsCompat.drawString(guiGraphics, mc.font, text, drawX, y, 0xE8F6FF, false);
    }

    private String trimToWidth(String text) {
        return Minecraft.getInstance().font.plainSubstrByWidth(text == null ? "" : text, ChannelListPanelComponent.ENTRY_TEXT_WIDTH);
    }

    private final class ModeFilterToggle extends AbstractButtonComponent {

        private final String baseSprite;
        private final PermissionMode permissionMode;
        private boolean active = true;

        private ModeFilterToggle(int x, int y, String baseSprite, PermissionMode permissionMode, CFNBaseGui<?> gui) {
            super(x, y, FILTER_WIDTH, 18, gui, EMPTY);
            this.baseSprite = baseSprite;
            this.permissionMode = permissionMode;
        }

        @Override
        protected void render(int mouseX, int mouseY, float partialTicks) {
            if (ComponentAtlas.INSTANCE.getRegion(baseSprite) != null) {
                renderAtlasSprite(baseSprite, getAbsoluteX(), getAbsoluteY(), width, height);
            }
            String marker = active ? "switch_marker_on" : "switch_marker_off";
            if (ComponentAtlas.INSTANCE.getRegion(marker) != null) {
                renderAtlasSprite(marker, getAbsoluteX() + 20, getAbsoluteY() + 5, 8, 8);
            }
        }

        @Override
        protected void onClick() {
            active = !active;
            firstVisibleEntryIndex = 0;
            syncingSlider = true;
            try {
                slider.setNormalizedValue(0.0d);
            } finally {
                syncingSlider = false;
            }
            hasLastSliderValue = false;
        }

        @Override
        protected @NotNull List<LocalizedComponent> getTooltip(int mouseX, int mouseY) {
            return ObjectLists.singleton(() -> switch (permissionMode) {
                case PUBLIC ->
                    active ? CI18n.format("gui.channel_list.tooltip.filter.public.hide") : CI18n.format("gui.channel_list.tooltip.filter.public.show");
                case TEAM ->
                    active ? CI18n.format("gui.channel_list.tooltip.filter.team.hide") : CI18n.format("gui.channel_list.tooltip.filter.team.show");
                default ->
                    active ? CI18n.format("gui.channel_list.tooltip.filter.private.hide") : CI18n.format("gui.channel_list.tooltip.filter.private.show");
            });
        }
    }
}
