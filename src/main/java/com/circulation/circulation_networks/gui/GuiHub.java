package com.circulation.circulation_networks.gui;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.hub.ChargingDefinition;
import com.circulation.circulation_networks.container.ContainerHub;
import com.circulation.circulation_networks.gui.component.BackgroundComponent;
import com.circulation.circulation_networks.gui.component.ButtonComponent;
import com.circulation.circulation_networks.gui.component.ChannelListPanelComponent;
import com.circulation.circulation_networks.gui.component.ChannelSettingsDialogComponent;
import com.circulation.circulation_networks.gui.component.CloseButtonComponent;
import com.circulation.circulation_networks.gui.component.InventoryComponent;
import com.circulation.circulation_networks.gui.component.NodeListPanelComponent;
import com.circulation.circulation_networks.gui.component.PermissionListPanelComponent;
import com.circulation.circulation_networks.gui.component.SlotComponent;
import com.circulation.circulation_networks.gui.component.TextComponent;
import com.circulation.circulation_networks.gui.component.TriStateButtonComponent;
import com.circulation.circulation_networks.gui.component.base.Component;
import com.circulation.circulation_networks.gui.component.base.DraggableComponent;
import com.circulation.circulation_networks.gui.component.base.RenderPhase;
import com.circulation.circulation_networks.packets.UpdatePlayerChargingMode;
import com.circulation.circulation_networks.registry.RegistryEnergyHandler;
import com.circulation.circulation_networks.utils.CI18n;
import com.circulation.circulation_networks.utils.FormatNumberUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;

public class GuiHub extends CFNBaseGui<ContainerHub> {

    private static final int CHANNEL_TEXT_WIDTH = 83;
    private static final int ENERGY_TEXT_WIDTH = 60;
    private static final int LATENCY_TEXT_WIDTH = 83;
    private static int state;
    private String oldInput;
    private String oldOutput;
    private String oldChannelName;
    private String oldInteractionTimeMicros;
    private String oldFC;
    private String oldFI;
    private String oldFO;
    private String oldFT;
    private ButtonComponent node, playerPowerSupply, upgradePlugin, channel, permission, settings;
    private Component nodeUI, playerPowerSupplyUI, upgradePluginUI, channelUI, permissionUI, settingsUI;

    public GuiHub(ContainerHub container, Inventory playerInventory, net.minecraft.network.chat.Component title) {
        super(container, playerInventory, title, 178, 233);
    }

    public static int getEnergyUnitState() {
        return state;
    }

    private Runnable setC(ChargingDefinition c) {
        return () -> {
            container.chargingMode.setPreference(c, !container.chargingMode.getPreference(c));
            CirculationFlowNetworks.sendToServer(new UpdatePlayerChargingMode(c));
        };
    }

    private Runnable setAllCharging(boolean enabled) {
        byte mode = enabled ? (byte) 1 : (byte) 2;
        byte prefs = enabled ? (byte) 0b00111111 : (byte) 0;
        return () -> {
            container.chargingMode.setPrefs(prefs);
            CirculationFlowNetworks.sendToServer(new UpdatePlayerChargingMode(mode));
        };
    }

    @Override
    protected void buildComponents(Map<RenderPhase, List<Component>> components) {
        List<Component> bg = components.computeIfAbsent(RenderPhase.BACKGROUND, k -> new ObjectArrayList<>());
        bg.add(new BackgroundComponent("hub_base", this));
        bg.add(new InventoryComponent(7, 145, container.playerInvLayout, this));
        bg.add(new Component(-34, 0, 33, 80, this)
            .setSpriteLayers("hub_1_3_button")
            .addChild(
                node = new ButtonComponent(7, 7, 19, 19, this, "node", () -> {
                    nodeUI.setVisible(true);
                    node.setEnabled(false);
                }).addTooltip(boldTooltip("gui.hub.tooltip.node_list")),
                playerPowerSupply = new ButtonComponent(7, 29, 19, 19, this, "player_power_supply", () -> {
                    playerPowerSupplyUI.setVisible(true);
                    playerPowerSupply.setEnabled(false);
                }).addTooltip(boldTooltip("gui.hub.tooltip.charging_config")),
                upgradePlugin = new ButtonComponent(7, 51, 19, 19, this, "upgrade_plugin", () -> {
                    upgradePluginUI.setVisible(true);
                    upgradePlugin.setEnabled(false);
                }).addTooltip(boldTooltip("gui.hub.tooltip.plugin_list"))
            )
        );
        bg.add(new Component(179, 0, 33, 80, this)
            .setSpriteLayers("hub_1_3_button")
            .addChild(
                channel = new ButtonComponent(7, 7, 19, 19, this, "channel", () -> openRightPanel(channelUI, channel, permissionUI, permission, settingsUI, settings)) {
                    @Override
                    public boolean isEnabled() {
                        return super.isEnabled() && container.canOpenChannelList();
                    }
                }.addTooltip(boldTooltip("gui.hub.tooltip.channel_list")),
                permission = new ButtonComponent(7, 29, 19, 19, this, "permission", () -> openRightPanel(permissionUI, permission, channelUI, channel, settingsUI, settings)) {
                    @Override
                    public boolean isEnabled() {
                        return super.isEnabled() && container.canOpenChannelDetails();
                    }
                }.addTooltip(boldTooltip("gui.hub.tooltip.permission_settings")),
                settings = new ButtonComponent(7, 51, 19, 19, this, "settings", () -> {
                    channelUI.setVisible(false);
                    permissionUI.setVisible(false);
                    channel.setEnabled(true);
                    permission.setEnabled(true);
                    if (settingsUI instanceof ChannelSettingsDialogComponent dialog) {
                        dialog.openEdit();
                    } else {
                        settingsUI.setVisible(true);
                    }
                    settings.setEnabled(false);
                }) {
                    @Override
                    public boolean isEnabled() {
                        return super.isEnabled() && container.canOpenChannelDetails();
                    }
                }.addTooltip(boldTooltip("gui.hub.tooltip.channel_settings"))
            )
        );
        bg.add(new Component(-34, 197, 33, 36, this)
            .setSpriteLayers("hub_1_1_button")
            .addChild(
                new ButtonComponent(7, 7, 19, 19, this, "energy_unit", () -> {
                    ++state;
                    oldInput = null;
                    oldOutput = null;
                    oldInteractionTimeMicros = null;
                }).addTooltip(boldTooltip("gui.hub.tooltip.energy_display"))
            )
        );
        bg.add(new TextComponent(12, 13, this, this::formatChannelName, 0x79d7ff));
        bg.add(new TextComponent(106, 13, this, () -> f(container.input, true), 0x79d7ff));
        bg.add(new TextComponent(12, 130, this, this::formatInteractionTime, 0x79d7ff));
        bg.add(new TextComponent(106, 130, this, () -> f(container.output, false), 0x79d7ff));
        List<Component> n = components.computeIfAbsent(RenderPhase.NORMAL, k -> new ObjectArrayList<>());
        n.add(nodeUI = new NodeListPanelComponent(-116, 0, this, container)
            .setVisible(false)
            .addChild(getNewClose(node, 114, 8))
        );
        n.add(upgradePluginUI = new DraggableComponent(-34, 51, 94, 77, this)
            .setSpriteLayers("hub_upgrade_panel")
            .setVisible(false)
            .addChild(
                getNewClose(upgradePlugin, 68, 7),
                new SlotComponent(29, 29, 16, 16, container.slots[0], null, this),
                new SlotComponent(11, 11, 16, 16, container.slots[1], null, this),
                new SlotComponent(47, 11, 16, 16, container.slots[2], null, this),
                new SlotComponent(11, 47, 16, 16, container.slots[3], null, this),
                new SlotComponent(47, 47, 16, 16, container.slots[4], null, this)
            )
        );
        n.add(playerPowerSupplyUI = new DraggableComponent(-34, 29, 142, 116, this)
            .setSpriteLayers("supply_ui")
            .setVisible(false)
            .addChild(
                getNewClose(playerPowerSupply, 115, 9),
                new ButtonComponent(94, 38, 18, 18, this, "supply_all", setAllCharging(true))
                    .addTooltip(boldTooltip("gui.hub.tooltip.all_enable")),
                new ButtonComponent(94, 10, 18, 18, this, "supply_reset", setAllCharging(false))
                    .addTooltip(boldTooltip("gui.hub.tooltip.all_disable")),
                new TriStateButtonComponent(13, 12, 8, 41, this, "supply_armored_fence", setC(ChargingDefinition.ARMOR))
                    .setActiveSupplier(getC(ChargingDefinition.ARMOR))
                    .addTooltip(boldTooltip("gui.hub.tooltip.armor_slot")),
                new TriStateButtonComponent(57, 45, 8, 8, this, "supply_left", setC(ChargingDefinition.MAIN_HAND))
                    .setActiveSupplier(() -> container.chargingMode.getPreference(ChargingDefinition.MAIN_HAND) || container.chargingMode.getPreference(ChargingDefinition.HOTBAR))
                    .addTooltip(boldTooltip("gui.hub.tooltip.main_hand")),
                new TriStateButtonComponent(68, 45, 8, 8, this, "supply_right", setC(ChargingDefinition.OFF_HAND))
                    .setActiveSupplier(getC(ChargingDefinition.OFF_HAND))
                    .addTooltip(boldTooltip("gui.hub.tooltip.off_hand")),
                new TriStateButtonComponent(13, 58, 96, 30, this, "supply_inventory", setC(ChargingDefinition.INVENTORY))
                    .setActiveSupplier(getC(ChargingDefinition.INVENTORY))
                    .addTooltip(boldTooltip("gui.hub.tooltip.inventory")),
                new TriStateButtonComponent(13, 93, 96, 8, this, "supply_quick_access_bar", setC(ChargingDefinition.HOTBAR))
                    .setActiveSupplier(getC(ChargingDefinition.HOTBAR))
                    .addTooltip(boldTooltip("gui.hub.tooltip.hotbar")),
                new TriStateButtonComponent(57, 12, 8, 8, this, "supply_accessory", setC(ChargingDefinition.ACCESSORY))
                    .setActiveSupplier(getC(ChargingDefinition.ACCESSORY))
                    .addTooltip(boldTooltip("gui.hub.tooltip.accessory_slot"))
            )
        );
        n.add(channelUI = new ChannelListPanelComponent(37, 0, this, container, () -> {
                if (settingsUI instanceof ChannelSettingsDialogComponent dialog) {
                    dialog.openCreate();
                    settings.setEnabled(true);
                }
            })
                .setVisible(false)
                .addChild(getNewClose(channel, 8, 8))
        );
        n.add(permissionUI = new PermissionListPanelComponent(37, 0, this, container)
            .setVisible(false)
            .addChild(getNewClose(permission, 8, 8))
        );
        n.add(settingsUI = new ChannelSettingsDialogComponent(18, 78, this, container)
            .setVisible(false)
            .addChild(getNewClose(settings, 8, 8))
        );
    }

    private CloseButtonComponent getNewClose(Component c, int x, int y) {
        return new CloseButtonComponent(x, y, 19, 19, this).setMutuallyComponent(c).addTooltip(boldTooltip("gui.hub.tooltip.close_ui"));
    }

    private BooleanSupplier getC(ChargingDefinition c) {
        return () -> container.chargingMode.getPreference(c);
    }

    private void openRightPanel(Component primary, ButtonComponent primaryButton,
                                Component secondary, ButtonComponent secondaryButton,
                                Component tertiary, ButtonComponent tertiaryButton) {
        primary.setVisible(true);
        primaryButton.setEnabled(false);

        if (secondary != null) {
            secondary.setVisible(false);
        }
        if (secondaryButton != null) {
            secondaryButton.setEnabled(true);
        }
        if (tertiary != null) {
            tertiary.setVisible(false);
        }
        if (tertiaryButton != null) {
            tertiaryButton.setEnabled(true);
        }
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
    }

    private String f(String string, boolean IO) {
        if (IO) {
            if (Objects.equals(string, oldInput)) {
                return oldFI;
            } else {
                oldInput = string;
            }
        } else {
            if (Objects.equals(string, oldOutput)) {
                return oldFO;
            } else {
                oldOutput = string;
            }
        }
        var e = EnergyAmount.obtain(string);
        var p = RegistryEnergyHandler.getPair(state);
        if (p.multiplying() != 0) {
            e.divide(p.multiplying());
        }
        String value = FormatNumberUtils.formatNumber(e) + " " + p.unit() + "/t";
        e.recycle();
        String output = trimToWidth(value, ENERGY_TEXT_WIDTH);
        return IO ? (oldFI = output) : (oldFO = output);
    }

    private String formatChannelName() {
        if (Objects.equals(container.channelName, oldChannelName)) {
            return oldFC;
        }
        oldChannelName = container.channelName;
        String value = container.channelName == null || container.channelName.trim().isEmpty()
            ? CI18n.format("gui.hub.no_channel")
            : container.channelName;
        return oldFC = trimToWidth(value, CHANNEL_TEXT_WIDTH);
    }

    private String formatInteractionTime() {
        if (Objects.equals(container.interactionTimeMicros, oldInteractionTimeMicros)) {
            return oldFT;
        }
        oldInteractionTimeMicros = container.interactionTimeMicros;
        long micros;
        try {
            micros = Long.parseLong(container.interactionTimeMicros);
        } catch (NumberFormatException ignored) {
            micros = 0L;
        }
        String value;
        if (micros >= 100L) {
            value = FormatNumberUtils.formatDouble(micros / 1000D, 1) + " ms";
        } else {
            value = FormatNumberUtils.formatNumber(micros) + " μs";
        }
        return oldFT = trimToWidth(CI18n.format("gui.hub.energy_latency", value), LATENCY_TEXT_WIDTH);
    }

    private String trimToWidth(String text, int width) {
        return Minecraft.getInstance().font.plainSubstrByWidth(text == null ? "" : text, width);
    }

    private String boldTooltip(String key) {
        return ChatFormatting.BOLD + CI18n.format(key);
    }
}
