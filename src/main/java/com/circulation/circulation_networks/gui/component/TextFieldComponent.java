package com.circulation.circulation_networks.gui.component;

import com.circulation.circulation_networks.gui.CFNBaseGui;
import com.circulation.circulation_networks.gui.component.base.Component;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

@SuppressWarnings("unused")
public class TextFieldComponent extends Component {

    private static final Predicate<Character> ALLOW_ALL_INPUT = ignored -> true;

    private final TextFieldPlatformServices.NativeTextField textField;
    private Predicate<Character> inputAllowed = ALLOW_ALL_INPUT;
    private int maxLength;
    private int textInsetLeft = 0;
    private int textInsetTop = 0;
    private int textInsetRight = 0;
    private int textInsetBottom = 0;
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
        this.textField = TextFieldPlatformServices.create(getInnerTextX(), getInnerTextY(), getInnerTextWidth(), getInnerTextHeight(), this.maxLength);
        applyNativeState();
    }

    private static boolean shouldFilterCharacter(char typedChar) {
        return typedChar != 0 && !Character.isISOControl(typedChar);
    }

    public String getText() {
        return textField.getText();
    }

    public TextFieldComponent setText(String text) {
        textField.setText(text != null ? text : "");
        return this;
    }

    public TextFieldComponent setTextFilter(Predicate<String> textFilter) {
        textField.setTextFilter(textFilter != null ? textFilter : ignored -> true);
        return this;
    }

    public TextFieldComponent selectAll() {
        textField.selectAll();
        return this;
    }

    public int getMaxLength() {
        return maxLength;
    }

    public TextFieldComponent setMaxLength(int maxLength) {
        this.maxLength = Math.max(0, maxLength);
        textField.setMaxLength(this.maxLength);
        return this;
    }

    public boolean isFocused() {
        return textField.isFocused();
    }

    @SuppressWarnings("UnusedReturnValue")
    public TextFieldComponent setFocused(boolean focused) {
        textField.setFocused(gui, focused);
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
        syncTextFieldBounds();
        applyNativeState();
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

    @SuppressWarnings("UnusedReturnValue")
    public TextFieldComponent setBackgroundDrawing(@Nullable Boolean backgroundDrawing) {
        this.backgroundDrawing = backgroundDrawing;
        applyNativeState();
        return this;
    }

    @Override
    protected void render(int mouseX, int mouseY, float partialTicks) {
        syncTextFieldBounds();
        applyNativeState();
        restoreGuiRenderState();
        textField.render(getCurrentGuiGraphics(), mouseX, mouseY, partialTicks);
        restoreGuiRenderState();
    }

    @Override
    public void update() {
        syncTextFieldBounds();
        applyNativeState();
        textField.update();
    }

    @Override
    protected boolean onMouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0 || !super.contains(mouseX, mouseY) || !isEnabled()) {
            return false;
        }
        syncTextFieldBounds();
        applyNativeState();
        textField.setFocused(gui, true);
        textField.mouseClicked(gui, mouseX, mouseY, button);
        return true;
    }

    @Override
    protected boolean onKeyTyped(char typedChar, int keyCode) {
        if (!isCharacterInputAllowed(typedChar)) {
            return false;
        }
        applyNativeState();
        return textField.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void onGlobalMouseClicked(int mouseX, int mouseY, int button) {
        if (!textField.isFocused() || super.contains(mouseX, mouseY)) {
            return;
        }
        textField.setFocused(gui, false);
    }

    @Override
    public void setSize(int width, int height) {
        super.setSize(width, height);
        syncTextFieldBounds();
    }

    private void syncTextFieldBounds() {
        textField.syncBounds(getInnerTextX(), getInnerTextY(), getInnerTextWidth(), getInnerTextHeight());
    }

    private void applyNativeState() {
        textField.applyState(gui, isEnabled(), isVisible(), backgroundDrawing);
    }
}
