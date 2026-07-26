package com.circulation.circulation_networks.gui.component;

import com.circulation.circulation_networks.gui.CFNBaseGui;
import com.circulation.circulation_networks.gui.component.base.Component;
import net.minecraft.client.Minecraft;
//? if <1.20 {
import net.minecraft.client.gui.GuiTextField;
//?} else {
/*import net.minecraft.client.gui.components.EditBox;
 *///?}

import org.jetbrains.annotations.Nullable;
import java.util.function.Predicate;

@SuppressWarnings("unused")
public class TextFieldComponent extends Component {

    private static final Predicate<Character> ALLOW_ALL_INPUT = ignored -> true;
    private static int nextId = 1;

    //? if <1.20 {
    private final GuiTextField textField;
    //?} else {
    /*private EditBox textField;
     *///?}
    private Predicate<Character> inputAllowed = ALLOW_ALL_INPUT;
    private Predicate<String> textFilter = ignored -> true;
    private int maxLength;
    private int textInsetLeft = 0;
    private int textInsetTop = 0;
    private int textInsetRight = 0;
    private int textInsetBottom = 0;
    //? if >=1.20 {
    /*private int nativeWidth = -1;
    private int nativeHeight = -1;
    *///?}
    @Nullable
    private Boolean backgroundDrawing;

    public TextFieldComponent(int x, int y, int width, int height, CFNBaseGui<?> gui) {
        this(x, y, width, height, gui, 10, null);
    }

    public TextFieldComponent(int x, int y, int width, int height, CFNBaseGui<?> gui, int maxLength) {
        this(x, y, width, height, gui, maxLength, null);
    }

    public TextFieldComponent(int x, int y, int width, int height, CFNBaseGui<?> gui, int maxLength, @Nullable Boolean backgroundDrawing) {
        super(x, y, width, height, gui);
        this.backgroundDrawing = backgroundDrawing;
        this.maxLength = Math.max(0, maxLength);
        //? if <1.20 {
        this.textField = new GuiTextField(nextId++, Minecraft.getMinecraft().fontRenderer, getAbsoluteX(), getAbsoluteY(), width, height);
        this.textField.setMaxStringLength(this.maxLength);
        applyNativeState();
        //?} else {
        /*this.textField = createEditBox(this.maxLength);
        applyNativeState();
        *///?}
    }

    private static boolean shouldFilterCharacter(char typedChar) {
        return typedChar != 0 && !Character.isISOControl(typedChar);
    }

    public String getText() {
        //? if <1.20 {
        return textField.getText();
        //?} else {
        /*return textField.getValue();
         *///?}
    }

    public TextFieldComponent setText(String text) {
        //? if <1.20 {
        textField.setText(text != null ? text : "");
        //?} else {
        /*textField.setValue(text != null ? text : "");
         *///?}
        return this;
    }

    /**
     * Sets the validator for the complete edited value. This is used when
     * character-level filtering cannot express constraints such as numeric range.
     */
    public TextFieldComponent setTextFilter(Predicate<String> textFilter) {
        this.textFilter = textFilter != null ? textFilter : ignored -> true;
        //? if <1.20 {
        this.textField.setValidator(this.textFilter::test);
        //?} else {
        /*this.textField.setFilter(this.textFilter);
        *///?}
        return this;
    }

    /** Selects the complete current value. */
    public TextFieldComponent selectAll() {
        //? if <1.20 {
        textField.setCursorPositionEnd();
        textField.setSelectionPos(0);
        //?} else {
        /*textField.setCursorPosition(textField.getValue().length());
        textField.setHighlightPos(0);
        *///?}
        return this;
    }

    public int getMaxLength() {
        return maxLength;
    }

    public TextFieldComponent setMaxLength(int maxLength) {
        this.maxLength = Math.max(0, maxLength);
        //? if <1.20 {
        textField.setMaxStringLength(this.maxLength);
        //?} else {
        /*textField.setMaxLength(this.maxLength);
         *///?}
        return this;
    }

    public boolean isFocused() {
        //? if <1.20 {
        return textField.isFocused();
        //?} else {
        /*return textField.isFocused();
         *///?}
    }

    public TextFieldComponent setFocused(boolean focused) {
        //? if <1.20 {
        textField.setFocused(focused);
        //?} else {
        /*textField.setFocused(focused);
        syncScreenFocus();
         *///?}
        return this;
    }

    public Predicate<Character> getInputAllowed() {
        return inputAllowed;
    }

    public TextFieldComponent setInputAllowed(Predicate<Character> inputAllowed) {
        this.inputAllowed = inputAllowed != null ? inputAllowed : ALLOW_ALL_INPUT;
        return this;
    }

    private boolean isCharacterInputAllowed(char typedChar) {
        return !shouldFilterCharacter(typedChar) || inputAllowed.test(typedChar);
    }

    public TextFieldComponent setTextInsets(int left, int top, int right, int bottom) {
        this.textInsetLeft = Math.max(0, left);
        this.textInsetTop = Math.max(0, top);
        this.textInsetRight = Math.max(0, right);
        this.textInsetBottom = Math.max(0, bottom);
        //? if <1.20 {
        syncTextFieldBounds();
        applyNativeState();
        //?} else {
        /*rebuildEditBox();
         *///?}
        return this;
    }

    protected final int getInnerTextX() {
        return getAbsoluteX() + textInsetLeft;
    }

    protected final int getInnerTextY() {
        return getAbsoluteY() + textInsetTop;
    }

    protected final int getInnerTextWidth() {
        return Math.max(1, width - textInsetLeft - textInsetRight);
    }

    protected final int getInnerTextHeight() {
        return Math.max(1, height - textInsetTop - textInsetBottom);
    }

    @Nullable
    public Boolean getBackgroundDrawing() {
        return backgroundDrawing;
    }

    public TextFieldComponent setBackgroundDrawing(@Nullable Boolean backgroundDrawing) {
        this.backgroundDrawing = backgroundDrawing;
        //? if <1.20 {
        applyNativeState();
        //?} else {
        /*applyNativeState();
         *///?}
        return this;
    }

    @Override
    protected void render(int mouseX, int mouseY, float partialTicks) {
        //? if <1.20 {
        syncTextFieldBounds();
        applyNativeState();
        restoreGuiRenderState();
        textField.drawTextBox();
        restoreGuiRenderState();
        //?} else {
        /*syncTextFieldBounds();
        applyNativeState();
        var guiGraphics = getCurrentGuiGraphics();
        if (guiGraphics != null) {
            textField.renderWidget(guiGraphics, mouseX, mouseY, partialTicks);
        }
        *///?}
    }

    @Override
    public void update() {
        //? if <1.20 {
        syncTextFieldBounds();
        applyNativeState();
        textField.updateCursorCounter();
        //?} else {
        /*syncTextFieldBounds();
        applyNativeState();
        //? if <1.21 {
        textField.tick();
        //?}
        *///?}
    }

    @Override
    protected boolean onMouseClicked(int mouseX, int mouseY, int button) {
        //? if <1.20 {
        syncTextFieldBounds();
        applyNativeState();
        boolean handled = textField.mouseClicked(mouseX, mouseY, button);
        if (!handled && button == 0 && super.contains(mouseX, mouseY) && isEnabled()) {
            textField.setFocused(true);
            return true;
        }
        return handled;
        //?} else {
        /*syncTextFieldBounds();
        applyNativeState();
        boolean handled = textField.mouseClicked(mouseX, mouseY, button);
        if (handled) {
            textField.setFocused(true);
            syncScreenFocus();
            return true;
        }
        if (button == 0 && super.contains(mouseX, mouseY) && isEnabled()) {
            textField.setFocused(true);
            syncScreenFocus();
            return true;
        }
        syncScreenFocus();
        return false;
        *///?}
    }

    @Override
    protected boolean onKeyTyped(char typedChar, int keyCode) {
        //? if <1.20 {
        if (!isCharacterInputAllowed(typedChar)) {
            return false;
        }
        applyNativeState();
        return textField.textboxKeyTyped(typedChar, keyCode);
        //?} else {
        /*if (!isCharacterInputAllowed(typedChar)) {
            return false;
        }
        applyNativeState();
        if (typedChar != 0 && textField.charTyped(typedChar, 0)) {
            return true;
        }
        return keyCode != 0 && textField.keyPressed(keyCode, 0, 0);
        *///?}
    }

    @Override
    protected void onGlobalMouseClicked(int mouseX, int mouseY, int button) {
        //? if <1.20 {
        if (!textField.isFocused() || super.contains(mouseX, mouseY)) {
            return;
        }
        syncTextFieldBounds();
        textField.mouseClicked(mouseX, mouseY, button);
        //?} else {
        /*if (!textField.isFocused() || super.contains(mouseX, mouseY)) {
            return;
        }
        textField.setFocused(false);
        syncScreenFocus();
        *///?}
    }

    @Override
    public void setSize(int width, int height) {
        super.setSize(width, height);
        //? if <1.20 {
        syncTextFieldBounds();
        //?} else {
        /*rebuildEditBox();
         *///?}
    }

    //? if <1.20 {
    private void syncTextFieldBounds() {
        textField.x = getInnerTextX();
        textField.y = getInnerTextY();
        textField.width = getInnerTextWidth();
        textField.height = getInnerTextHeight();
    }

    private void applyNativeState() {
        textField.setEnabled(isEnabled());
        textField.setVisible(isVisible());
        if (backgroundDrawing != null) {
            textField.setEnableBackgroundDrawing(backgroundDrawing);
        }
    }
    //?} else {
    /*private EditBox createEditBox(int maxLength) {
        nativeWidth = getInnerTextWidth();
        nativeHeight = getInnerTextHeight();
        EditBox field = new EditBox(
            Minecraft.getInstance().font,
            getInnerTextX(),
            getInnerTextY(),
            nativeWidth,
            nativeHeight,
            net.minecraft.network.chat.Component.literal("")
        );
        field.setCanLoseFocus(true);
        field.setMaxLength(Math.max(0, maxLength));
        return field;
    }

    private void rebuildEditBox() {
        String text = textField != null ? textField.getValue() : "";
        boolean focused = textField != null && textField.isFocused();
        syncScreenFocus();
        textField = createEditBox(maxLength);
        textField.setValue(text);
        textField.setFocused(focused);
        applyNativeState();
        syncScreenFocus();
    }

    private void syncTextFieldBounds() {
        int innerWidth = getInnerTextWidth();
        int innerHeight = getInnerTextHeight();
        if (nativeWidth != innerWidth || nativeHeight != innerHeight) {
            rebuildEditBox();
            return;
        }
        textField.setX(getInnerTextX());
        textField.setY(getInnerTextY());
        textField.setWidth(innerWidth);
    }

    private void applyNativeState() {
        textField.setEditable(isEnabled());
        textField.setVisible(isVisible());
        textField.setFilter(textFilter);
        if (!isEnabled() && textField.isFocused()) {
            textField.setFocused(false);
        }
        if (backgroundDrawing != null) {
            textField.setBordered(backgroundDrawing);
        }
        syncScreenFocus();
    }

    private void syncScreenFocus() {
        if (textField.isFocused() && isVisible() && isEnabled()) {
            gui.focusComponentInput(textField);
        } else {
            gui.clearComponentInputFocus(textField);
        }
    }
    *///?}
}
