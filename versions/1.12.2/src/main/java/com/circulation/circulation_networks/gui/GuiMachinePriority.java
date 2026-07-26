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
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@SideOnly(Side.CLIENT)
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

    public GuiMachinePriority(ContainerMachinePriority container) {
        super(container);
        this.xSize = GUI_WIDTH;
        this.ySize = GUI_HEIGHT;
    }

    @Override
    protected void buildComponents(Map<RenderPhase, List<Component>> components) {
        List<Component> background = components.computeIfAbsent(RenderPhase.BACKGROUND,
            ignored -> new ObjectArrayList<>());
        background.add(new BackgroundComponent("configurator_priority_panel", this));
        inputBackground = new Component(INPUT_X, INPUT_Y, INPUT_WIDTH, INPUT_HEIGHT, this)
            .setSpriteLayers("configurator_priority_input_box")
            .setVisible(false);
        background.add(inputBackground);

        List<Component> normal = components.computeIfAbsent(RenderPhase.NORMAL,
            ignored -> new ObjectArrayList<>());
        input = new TextFieldComponent(INPUT_X, INPUT_Y, INPUT_WIDTH, INPUT_HEIGHT, this, 11, false)
            .setTextInsets(4, 5, 4, 3)
            .setTextFilter(this::acceptInputEdit);
        input.setVisible(false);
        normal.add(input);

        List<Component> foreground = components.computeIfAbsent(RenderPhase.FOREGROUND,
            ignored -> new ObjectArrayList<>());
        String title = CI18n.format(TITLE_KEY);
        foreground.add(new TextComponent(centeredTextX(title), 9, this, () -> title, 0xFFFFFF));
        inputLabel = new TextComponent(10, 34, this, () -> CI18n.format(INPUT_KEY), 0xD0D0D0)
            .setVisible(false);
        foreground.add(inputLabel);
    }

    private int centeredTextX(String text) {
        return (GUI_WIDTH - fontRenderer.getStringWidth(text)) / 2;
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
    public void initGui() {
        super.initGui();
        synchronizePriorityInput();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
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
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mc.gameSettings.keyBindInventory.getKeyCode() == mouseButton - 100) {
            commitAndClose();
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        int inventoryKey = mc.gameSettings.keyBindInventory.getKeyCode();
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER
            || keyCode == Keyboard.KEY_ESCAPE
            || inventoryKey != Keyboard.KEY_NONE && keyCode == inventoryKey) {
            commitAndClose();
            return;
        }
        super.keyTyped(typedChar, keyCode);
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
        if (mc.player != null) {
            mc.player.closeScreen();
        }
    }

    private void sendInvalidInputMessage() {
        if (mc.player != null) {
            mc.player.sendMessage(new TextComponentTranslation(INVALID_INPUT_KEY));
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
    }
}
