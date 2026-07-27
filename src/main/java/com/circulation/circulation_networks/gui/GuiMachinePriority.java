package com.circulation.circulation_networks.gui;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.container.ContainerMachinePriority;
import com.circulation.circulation_networks.gui.component.BackgroundComponent;
import com.circulation.circulation_networks.gui.component.TextComponent;
import com.circulation.circulation_networks.gui.component.TextFieldComponent;
import com.circulation.circulation_networks.gui.component.base.Component;
import com.circulation.circulation_networks.gui.component.base.RenderPhase;
import com.circulation.circulation_networks.packets.MachinePriorityPackets;
import com.circulation.circulation_networks.utils.CI18n;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Map;

import static net.minecraft.network.chat.Component.translatable;

public final class GuiMachinePriority extends CFNBaseGui<ContainerMachinePriority> {

    private static final String TITLE_KEY = "gui.circulation_configurator.priority.title";
    private static final String INPUT_KEY = "gui.circulation_configurator.priority.input";
    private static final String INVALID_INPUT_KEY = "gui.circulation_configurator.priority.invalid_input";
    private static final int GUI_WIDTH = 138;
    private static final int GUI_HEIGHT = 56;
    private static final int INPUT_X = 47;
    private static final int INPUT_Y = 29;
    private static final int INPUT_WIDTH = 83;
    private static final int INPUT_HEIGHT = 18;

    private TextFieldComponent input;
    private Component inputBackground;
    private Component inputLabel;
    private boolean inputInitialized;
    private boolean committed;
    private int lastSyncedPriority;

    public GuiMachinePriority(ContainerMachinePriority container, Inventory inventory,
                              net.minecraft.network.chat.Component title) {
        super(container, inventory, title, GUI_WIDTH, GUI_HEIGHT);
    }

    @Override
    protected void buildComponents(Map<RenderPhase, List<Component>> components) {
        List<Component> background =
            components.computeIfAbsent(RenderPhase.BACKGROUND, ignored -> new ObjectArrayList<>());
        background.add(new BackgroundComponent("configurator_priority_panel", this));
        inputBackground = new Component(INPUT_X, INPUT_Y, INPUT_WIDTH, INPUT_HEIGHT, this)
            .setSpriteLayers("configurator_priority_input_box")
            .setVisible(false);
        background.add(inputBackground);

        List<Component> normal =
            components.computeIfAbsent(RenderPhase.NORMAL, ignored -> new ObjectArrayList<>());
        input = new TextFieldComponent(INPUT_X, INPUT_Y, INPUT_WIDTH, INPUT_HEIGHT, this, 11, false)
            .setTextInsets(4, 5, 4, 3)
            .setTextFilter(this::acceptInputEdit);
        input.setVisible(false);
        normal.add(input);

        List<Component> foreground =
            components.computeIfAbsent(RenderPhase.FOREGROUND, ignored -> new ObjectArrayList<>());
        String titleText = CI18n.format(TITLE_KEY);
        foreground.add(new TextComponent(centeredTextX(titleText), 9, this, () -> titleText, 0xFFFFFF));
        inputLabel = new TextComponent(10, 34, this, () -> CI18n.format(INPUT_KEY), 0xD0D0D0)
            .setVisible(false);
        foreground.add(inputLabel);
    }

    private int centeredTextX(String text) {
        return (GUI_WIDTH - font.width(text)) / 2;
    }

    private boolean acceptInputEdit(String text) {
        if (text == null || text.isEmpty() || "-".equals(text)) {
            return true;
        }
        int start = text.charAt(0) == '-' ? 1 : 0;
        for (int index = start; index < text.length(); index++) {
            if (!Character.isDigit(text.charAt(index))) {
                return false;
            }
        }
        return start == 0 || text.length() > 1;
    }

    @Override
    protected void init() {
        super.init();
        synchronizePriorityInput();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        synchronizePriorityInput();
    }

    private void synchronizePriorityInput() {
        if (!container.hasPrioritySnapshot()) {
            if (inputInitialized) {
                commitAndClose();
            }
            return;
        }
        int priority = container.getPriority();
        if (!inputInitialized) {
            inputInitialized = true;
            lastSyncedPriority = priority;
            input.setText(Integer.toString(priority));
            input.selectAll();
            input.setFocused(true);
            inputBackground.setVisible(true);
            inputLabel.setVisible(true);
            input.setVisible(true);
            return;
        }
        if (priority != lastSyncedPriority) {
            String previous = Integer.toString(lastSyncedPriority);
            if (previous.equals(input.getText())) {
                input.setText(Integer.toString(priority));
                input.selectAll();
            }
            lastSyncedPriority = priority;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (minecraft.options.keyInventory.matchesMouse(event)) {
            commitAndClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER
            || event.key() == GLFW.GLFW_KEY_ESCAPE
            || minecraft.options.keyInventory.matches(event)) {
            commitAndClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        commitAndClose();
    }

    private void commitAndClose() {
        if (committed) {
            return;
        }
        committed = true;
        if (container.hasPrioritySnapshot()) {
            try {
                int priority = Integer.parseInt(input.getText());
                CirculationFlowNetworks.sendToServer(
                    new MachinePriorityPackets.Submit(container.getSessionId(), priority)
                );
            } catch (NumberFormatException exception) {
                sendInvalidInputMessage();
            }
        }
        super.onClose();
    }

    private void sendInvalidInputMessage() {
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(translatable(INVALID_INPUT_KEY));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
    }
}
