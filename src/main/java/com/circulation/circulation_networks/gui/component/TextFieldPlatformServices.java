package com.circulation.circulation_networks.gui.component;

import com.circulation.circulation_networks.client.compat.GuiGraphicsCompat;
import com.circulation.circulation_networks.gui.CFNBaseGui;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

public final class TextFieldPlatformServices {


    private TextFieldPlatformServices() {
    }

    public static NativeTextField create(int x, int y, int width, int height, int maxLength) {
        return new ModernTextField(x, y, width, height, maxLength);
    }

    public interface NativeTextField {

        String getText();

        void setText(String text);

        void setTextFilter(Predicate<String> textFilter);

        void selectAll();

        void setMaxLength(int maxLength);

        boolean isFocused();

        void setFocused(CFNBaseGui<?> gui, boolean focused);

        void syncBounds(int x, int y, int width, int height);

        void applyState(CFNBaseGui<?> gui, boolean enabled, boolean visible, @Nullable Boolean backgroundDrawing);

        void render(@Nullable Object guiGraphics, int mouseX, int mouseY, float partialTicks);

        void update();

        boolean mouseClicked(CFNBaseGui<?> gui, int mouseX, int mouseY, int button);

        boolean keyTyped(char typedChar, int keyCode);
    }


    private static final class ModernTextField implements NativeTextField {

        private GuiGraphicsCompat.CFNEditBox field;
        private int maxLength;
        private int width;
        private int height;
        private boolean enabled = true;
        private boolean visible = true;
        private Predicate<String> textFilter = ignored -> true;

        private ModernTextField(int x, int y, int width, int height, int maxLength) {
            this.maxLength = Math.max(0, maxLength);
            this.width = Math.max(1, width);
            this.height = Math.max(1, height);
            this.field = createField(x, y, this.width, this.height, this.maxLength);
        }

        private static GuiGraphicsCompat.CFNEditBox createField(int x, int y, int width, int height, int maxLength) {
            GuiGraphicsCompat.CFNEditBox editBox = new GuiGraphicsCompat.CFNEditBox(
                Minecraft.getInstance().font,
                x,
                y,
                width,
                height,
                Component.literal("")
            );
            editBox.setCanLoseFocus(true);
            editBox.setMaxLength(Math.max(0, maxLength));
            return editBox;
        }

        @Override
        public String getText() {
            return field.getValue();
        }

        @Override
        public void setText(String text) {
            field.setValue(text != null ? text : "");
        }

        @Override
        public void setTextFilter(Predicate<String> textFilter) {
            this.textFilter = textFilter;
            field.setFilter(textFilter);
        }

        @Override
        public void selectAll() {
            field.setCursorPosition(field.getValue().length());
            field.setHighlightPos(0);
        }

        @Override
        public void setMaxLength(int maxLength) {
            this.maxLength = Math.max(0, maxLength);
            field.setMaxLength(this.maxLength);
        }

        @Override
        public boolean isFocused() {
            return field.isFocused();
        }

        @Override
        public void setFocused(CFNBaseGui<?> gui, boolean focused) {
            field.setFocused(focused);
            syncFocus(gui);
        }

        @Override
        public void syncBounds(int x, int y, int width, int height) {
            int clampedWidth = Math.max(1, width);
            int clampedHeight = Math.max(1, height);
            if (this.width != clampedWidth || this.height != clampedHeight) {
                rebuildField(x, y, clampedWidth, clampedHeight);
                return;
            }
            field.setX(x);
            field.setY(y);
            field.setWidth(clampedWidth);
        }

        @Override
        public void applyState(CFNBaseGui<?> gui, boolean enabled, boolean visible, @Nullable Boolean backgroundDrawing) {
            this.enabled = enabled;
            this.visible = visible;
            field.setEditable(enabled);
            field.setVisible(visible);
            field.setFilter(textFilter);
            if (!enabled && field.isFocused()) {
                field.setFocused(false);
            }
            if (backgroundDrawing != null) {
                field.setBordered(backgroundDrawing);
            }
            syncFocus(gui);
        }

        @Override
        public void render(@Nullable Object guiGraphics, int mouseX, int mouseY, float partialTicks) {
            if (guiGraphics == null) {
                return;
            }
            GuiGraphicsCompat.renderEditBox(field, guiGraphics, mouseX, mouseY, partialTicks);
        }

        @Override
        public void update() {
            field.tickCompat();
        }

        @Override
        public boolean mouseClicked(CFNBaseGui<?> gui, int mouseX, int mouseY, int button) {
            boolean handled = field.mouseClickedCompat(mouseX, mouseY, button);
            if (handled) {
                syncFocus(gui);
            }
            return handled;
        }

        @Override
        public boolean keyTyped(char typedChar, int keyCode) {
            return field.keyTypedCompat(typedChar, keyCode);
        }

        private void rebuildField(int x, int y, int width, int height) {
            String text = field.getValue();
            boolean focused = field.isFocused();
            this.width = width;
            this.height = height;
            field = createField(x, y, width, height, maxLength);
            field.setFilter(textFilter);
            field.setValue(text);
            field.setFocused(focused);
        }

        private void syncFocus(CFNBaseGui<?> gui) {
            if (field.isFocused() && visible && enabled) {
                gui.focusComponentInput(field);
            } else {
                gui.clearComponentInputFocus(field);
            }
        }
    }
}
