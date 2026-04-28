package com.circulation.circulation_networks.gui.component;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.api.hub.ChannelSnapshotEntry;
import com.circulation.circulation_networks.api.hub.HubPermissionLevel;
import com.circulation.circulation_networks.api.hub.PermissionMode;
import com.circulation.circulation_networks.client.render.ClientAnimationTicker;
import com.circulation.circulation_networks.container.ContainerHub;
import com.circulation.circulation_networks.gui.CFNBaseGui;
import com.circulation.circulation_networks.gui.component.base.ComponentAtlas;
import com.circulation.circulation_networks.gui.component.base.DraggableComponent;
import com.circulation.circulation_networks.packets.CreateHubChannel;
import com.circulation.circulation_networks.packets.DeleteHubChannel;
import com.circulation.circulation_networks.packets.UpdateHubChannelSettings;
import com.circulation.circulation_networks.tooltip.LocalizedComponent;
import com.circulation.circulation_networks.utils.CI18n;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.Collections;
import java.util.Objects;

public final class ChannelSettingsDialogComponent extends DraggableComponent {

    private static final String CREATE_BUTTON_SPRITE = "settings_action_create";
    private static final String DELETE_BUTTON_SPRITE = "settings_action_delete";

    private static final int CENTER_X = 18;
    private static final int CENTER_Y = 78;
    private static final int MODE_X = 29;
    private static final int MODE_Y = 28;
    private static final int MODE_WIDTH = 33;
    private static final int MODE_HEIGHT = 18;
    private static final int MODE_GAP = 2;
    private static final long DELETE_SHIFT_COOLDOWN_TICKS = 3L;
    private static final int DELETE_SHIFT_REQUIREMENT = 5;

    private final ContainerHub container;
    private final TextFieldComponent nameField;
    private final ModeButton publicModeButton;
    private final ModeButton teamModeButton;
    private final ModeButton privateModeButton;
    private final ActionButton actionButton;

    private DialogMode mode = DialogMode.SETTINGS;
    private PermissionMode selectedPermissionMode = PermissionMode.PRIVATE;
    private long lastShiftPressTick = -DELETE_SHIFT_COOLDOWN_TICKS;
    private int deleteShiftProgress;

    public ChannelSettingsDialogComponent(int x, int y, CFNBaseGui<?> gui, ContainerHub container) {
        super(x, y, 141, 77, gui);
        this.container = container;
        setSpriteLayers("settings_panel");
        setVisible(false);

        nameField = new SettingsNameField(29, 9, 103, 18, gui);
        addChild(nameField);

        addChild(publicModeButton = new ModeButton(MODE_X, MODE_Y, "switch_public", PermissionMode.PUBLIC, gui));
        addChild(teamModeButton = new ModeButton(MODE_X + MODE_WIDTH + MODE_GAP, MODE_Y, "switch_team", PermissionMode.TEAM, gui));
        addChild(privateModeButton = new ModeButton(MODE_X + (MODE_WIDTH + MODE_GAP) * 2, MODE_Y, "switch_private", PermissionMode.PRIVATE, gui));

        addChild(actionButton = new ActionButton(29, 47, gui));
    }

    private static String normalizeName(String text) {
        return text == null ? "" : text.trim();
    }

    @Override
    public void update() {
        syncUiState();
        super.update();
    }

    @Override
    protected void render(int mouseX, int mouseY, float partialTicks) {
        if (ComponentAtlas.INSTANCE.getRegion("settings_panel") == null) {
            GuiGraphics guiGraphics = getCurrentGuiGraphics();
            if (guiGraphics != null) {
                guiGraphics.fill(getAbsoluteX(), getAbsoluteY(), getAbsoluteX() + width, getAbsoluteY() + height, 0xCC1E3447);
            }
        }
        if (ComponentAtlas.INSTANCE.getRegion("settings_input_box") != null) {
            renderAtlasSprite("settings_input_box", getAbsoluteX() + 29, getAbsoluteY() + 9, 103, 18);
        }
    }

    @Override
    protected boolean onKeyTyped(char typedChar, int keyCode) {
        if (mode == DialogMode.SETTINGS && (keyCode == GLFW.GLFW_KEY_LEFT_SHIFT || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT)) {
            long now = ClientAnimationTicker.ticks();
            if (now - lastShiftPressTick >= DELETE_SHIFT_COOLDOWN_TICKS) {
                lastShiftPressTick = now;
                deleteShiftProgress = Math.min(DELETE_SHIFT_REQUIREMENT, deleteShiftProgress + 1);
                syncUiState();
            }
            return true;
        }
        return false;
    }

    public void openCreate() {
        mode = DialogMode.CREATE;
        deleteShiftProgress = 0;
        lastShiftPressTick = -DELETE_SHIFT_COOLDOWN_TICKS;
        selectedPermissionMode = PermissionMode.PRIVATE;
        ((SettingsNameField) nameField).bind(getDefaultChannelName());
        nameField.setFocused(false);
        setX(CENTER_X);
        setY(CENTER_Y);
        setVisible(true);
        bringToFront();
        syncUiState();
    }

    public void openEdit() {
        mode = DialogMode.SETTINGS;
        deleteShiftProgress = 0;
        lastShiftPressTick = -DELETE_SHIFT_COOLDOWN_TICKS;
        selectedPermissionMode = getCurrentPermissionMode();
        ((SettingsNameField) nameField).bind(container.channelName == null ? "" : container.channelName);
        nameField.setFocused(false);
        setX(CENTER_X);
        setY(CENTER_Y);
        setVisible(true);
        bringToFront();
        syncUiState();
    }

    private void syncUiState() {
        HubPermissionLevel permission = getCurrentUserPermission();
        boolean override = container.hasChannelManagementOverride();
        boolean canEditName = mode == DialogMode.CREATE || override || permission == HubPermissionLevel.OWNER || permission == HubPermissionLevel.ADMIN;
        boolean canEditMode = mode == DialogMode.CREATE || override || permission == HubPermissionLevel.OWNER;
        boolean canDelete = mode == DialogMode.SETTINGS && (override || permission == HubPermissionLevel.OWNER) && deleteShiftProgress >= DELETE_SHIFT_REQUIREMENT;

        nameField.setEnabled(canEditName);
        publicModeButton.setEnabled(canEditMode);
        teamModeButton.setEnabled(canEditMode);
        privateModeButton.setEnabled(canEditMode);
        actionButton.setEnabled(switch (mode) {
            case CREATE -> !nameField.getText().trim().isEmpty();
            case SETTINGS -> canDelete;
        });
    }

    private void selectMode(PermissionMode permissionMode) {
        if (permissionMode != null) {
            selectedPermissionMode = permissionMode;
            sendSettingsUpdateIfAllowed();
        }
    }

    private void handleAction() {
        switch (mode) {
            case CREATE:
                CirculationFlowNetworks.sendToServer(new CreateHubChannel(nameField.getText().trim(), selectedPermissionMode));
                setVisible(false);
                break;
            case SETTINGS:
                if (deleteShiftProgress >= DELETE_SHIFT_REQUIREMENT) {
                    CirculationFlowNetworks.sendToServer(new DeleteHubChannel());
                    setVisible(false);
                }
                break;
        }
    }

    private void sendSettingsUpdateIfAllowed() {
        if (mode != DialogMode.SETTINGS) {
            return;
        }

        HubPermissionLevel permission = getCurrentUserPermission();
        if (!container.hasChannelManagementOverride() && permission != HubPermissionLevel.OWNER && permission != HubPermissionLevel.ADMIN) {
            return;
        }

        CirculationFlowNetworks.sendToServer(new UpdateHubChannelSettings(nameField.getText().trim(), selectedPermissionMode));
    }

    private String getDefaultChannelName() {
        String playerName = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getName().getString() : CI18n.format("gui.channel_settings.default_player_name");
        String defaultName = CI18n.format("gui.channel_settings.default_name", playerName);
        return defaultName.length() <= 32 ? defaultName : defaultName.substring(0, 32);
    }

    private PermissionMode getCurrentPermissionMode() {
        for (ChannelSnapshotEntry entry : container.availableChannels.getEntries()) {
            if (entry.id().equals(container.channelId)) {
                return entry.permissionMode();
            }
        }
        return PermissionMode.PRIVATE;
    }

    private HubPermissionLevel getCurrentUserPermission() {
        for (ChannelSnapshotEntry entry : container.availableChannels.getEntries()) {
            if (entry.id().equals(container.channelId)) {
                return entry.permission();
            }
        }
        return HubPermissionLevel.NONE;
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
        int drawX = x + Math.max(0, (103 - mc.font.width(text)) / 2);
        guiGraphics.drawString(mc.font, text, drawX, y, 0xE8F6FF, false);
    }

    private enum DialogMode {
        CREATE,
        SETTINGS
    }

    private final class SettingsNameField extends TextFieldComponent {

        private String committedText = "";

        private SettingsNameField(int x, int y, int width, int height, CFNBaseGui<?> gui) {
            super(x, y, width, height, gui, 32, false);
            setTextInsets(4, 5, 4, 5);
        }

        private void bind(String text) {
            committedText = normalizeName(text);
            setText(committedText);
        }

        private void commitIfDirty() {
            String normalized = normalizeName(getText());
            if (!Objects.equals(normalized, getText())) {
                setText(normalized);
            }
            if (Objects.equals(committedText, normalized)) {
                return;
            }
            committedText = normalized;
            sendSettingsUpdateIfAllowed();
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

    private final class ModeButton extends AbstractButtonComponent {

        private final String baseSprite;
        private final PermissionMode permissionMode;

        private ModeButton(int x, int y, String baseSprite, PermissionMode permissionMode, CFNBaseGui<?> gui) {
            super(x, y, MODE_WIDTH, MODE_HEIGHT, gui, EMPTY);
            this.baseSprite = baseSprite;
            this.permissionMode = permissionMode;
        }

        @Override
        protected void render(int mouseX, int mouseY, float partialTicks) {
            if (ComponentAtlas.INSTANCE.getRegion(baseSprite) != null) {
                renderAtlasSprite(baseSprite, getAbsoluteX(), getAbsoluteY(), width, height);
            }
            String marker = selectedPermissionMode == permissionMode ? "switch_marker_on" : "switch_marker_off";
            if (ComponentAtlas.INSTANCE.getRegion(marker) != null) {
                renderAtlasSprite(marker, getAbsoluteX() + 20, getAbsoluteY() + 5, 8, 8);
            }
            if (!isEnabled()) {
                GuiGraphics guiGraphics = getCurrentGuiGraphics();
                if (guiGraphics != null) {
                    guiGraphics.fill(getAbsoluteX(), getAbsoluteY(), getAbsoluteX() + width, getAbsoluteY() + height, 0x55000000);
                }
            }
        }

        @Override
        protected void onClick() {
            selectMode(permissionMode);
        }
    }

    private final class ActionButton extends ButtonComponent {

        private ActionButton(int x, int y, CFNBaseGui<?> gui) {
            super(x, y, 103, 18, gui, CREATE_BUTTON_SPRITE, ChannelSettingsDialogComponent.this::handleAction);
        }

        private String getLabel() {
            return switch (mode) {
                case CREATE -> CI18n.format("gui.channel_settings.action.create");
                case SETTINGS -> CI18n.format("gui.channel_settings.action.delete");
            };
        }

        @Override
        protected String[] getActiveLayers() {
            return switch (mode) {
                case CREATE -> resolveSprites(CREATE_BUTTON_SPRITE);
                case SETTINGS -> resolveSprites(DELETE_BUTTON_SPRITE);
            };
        }

        @Override
        protected void render(int mouseX, int mouseY, float partialTicks) {
            drawCenteredText(getLabel(), getAbsoluteX(), alignTextY(getAbsoluteY(), height));
        }

        @Override
        protected @NotNull java.util.List<LocalizedComponent> getTooltip(int mouseX, int mouseY) {
            if (mode == DialogMode.CREATE) {
                return Collections.singletonList(() -> CI18n.format("gui.channel_settings.tooltip.create"));
            }

            HubPermissionLevel permission = getCurrentUserPermission();
            if (!container.hasChannelManagementOverride() && permission != HubPermissionLevel.OWNER) {
                return Collections.singletonList(() -> CI18n.format("gui.channel_settings.tooltip.delete.owner_only"));
            }
            if (deleteShiftProgress < DELETE_SHIFT_REQUIREMENT) {
                return Collections.singletonList(() -> CI18n.format("gui.channel_settings.tooltip.delete.shift_required", deleteShiftProgress, DELETE_SHIFT_REQUIREMENT));
            }
            return Collections.singletonList(() -> CI18n.format("gui.channel_settings.tooltip.delete.ready"));
        }

        private String[] resolveSprites(String base) {
            cache[0] = !isEnabled() && ComponentAtlas.INSTANCE.getRegion(base + "_disabled") != null
                ? base + "_disabled"
                : pressed && ComponentAtlas.INSTANCE.getRegion(base + "_pressed") != null
                  ? base + "_pressed"
                  : isHovered() && ComponentAtlas.INSTANCE.getRegion(base + "_hovered") != null
                    ? base + "_hovered"
                    : base;
            cache[1] = null;
            return cache;
        }
    }
}
