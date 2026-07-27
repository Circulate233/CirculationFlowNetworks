package com.circulation.circulation_networks.gui.component;

import com.circulation.circulation_networks.gui.CFNBaseGui;
import com.circulation.circulation_networks.gui.component.base.AtlasRegion;
import com.circulation.circulation_networks.gui.component.base.AtlasRenderHelper;
import com.circulation.circulation_networks.gui.component.base.Component;
import com.circulation.circulation_networks.gui.component.base.ComponentAtlas;

public class SliderComponent extends Component {

    public static final int TRACK_WIDTH = 8;
    public static final int BUTTON_HEIGHT = 13;
    public static final int TOP_EXCLUSION = 2;
    public static final int BOTTOM_EXCLUSION = 2;
    public static final int MIN_HEIGHT = TOP_EXCLUSION + BUTTON_HEIGHT + BOTTOM_EXCLUSION;

    private static final int TRACK_TOP_HEIGHT = 6;
    private static final int TRACK_MIDDLE_REPEAT_SRC_Y = 7;
    private static final int TRACK_MIDDLE_REPEAT_HEIGHT = 2;
    private static final int TRACK_BOTTOM_SRC_Y = 10;
    private static final int TRACK_BOTTOM_HEIGHT = 6;
    private static final int MIN = 0;
    private static final int NORMALIZED_SCALE = 1000;
    private static final double NORMALIZED_MIN = 0.0d;
    private static final double NORMALIZED_MAX = 1.0d;

    private static final String TRACK_SPRITE = "slider";

    private final SliderParent sliderParent;
    private final SliderButtonComponent button;

    private final boolean normalizedMode;
    private final int max;
    private int value;
    private int step = 1;

    public SliderComponent(SliderParent parent,
                           int x,
                           int y,
                           int trackHeight,
                           int max,
                           int initialValue,
                           CFNBaseGui<?> gui) {
        this(parent, x, y, trackHeight, max, initialValue, gui, false);
    }

    private SliderComponent(SliderParent parent,
                            int x,
                            int y,
                            int trackHeight,
                            int max,
                            int initialValue,
                            CFNBaseGui<?> gui,
                            boolean normalizedMode) {
        super(x, y, TRACK_WIDTH, normalizeHeight(trackHeight), gui);
        if (max < 0) {
            throw new IllegalArgumentException("max must be greater than or equal to 0");
        }

        this.sliderParent = parent;
        this.normalizedMode = normalizedMode;
        this.max = max;
        this.button = new SliderButtonComponent(0, TOP_EXCLUSION, gui);

        addChild(button);

        updateButtonBounds();
        button.setEnabled(this.max > MIN && isEnabled());
        setValueInternal(initialValue, false);
    }

    public static SliderComponent normalized(SliderParent parent,
                                             int x,
                                             int y,
                                             int trackHeight,
                                             double initialValue,
                                             CFNBaseGui<?> gui) {
        return new SliderComponent(
            parent,
            x,
            y,
            trackHeight,
            NORMALIZED_SCALE,
            normalizedToRaw(initialValue),
            gui,
            true
        );
    }

    private static int normalizedToRaw(double value) {
        double clamped = Math.clamp(value, NORMALIZED_MIN, NORMALIZED_MAX);
        return (int) Math.round(clamped * NORMALIZED_SCALE);
    }

    private static double rawToNormalized(int value) {
        int clamped = clamp(value, MIN, NORMALIZED_SCALE);
        return clamped / (double) NORMALIZED_SCALE;
    }

    private static int normalizeHeight(int height) {
        return Math.max(MIN_HEIGHT, height);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        setValueInternal(value, false);
    }

    public int getMax() {
        return max;
    }

    public int getStep() {
        return step;
    }

    public SliderComponent setStep(int step) {
        return setScrollStep(step);
    }

    public int getScrollStep() {
        return step;
    }

    public SliderComponent setScrollStep(int step) {
        this.step = Math.max(1, step);
        return this;
    }

    public boolean isNormalizedMode() {
        return normalizedMode;
    }

    public SliderComponent setNormalizedStep(double step) {
        return setNormalizedScrollStep(step);
    }

    public SliderComponent setNormalizedScrollStep(double step) {
        ensureNormalizedMode();
        this.step = Math.max(1, normalizedToRaw(step));
        return this;
    }

    public double getNormalizedValue() {
        ensureNormalizedMode();
        return rawToNormalized(value);
    }

    public void setNormalizedValue(double value) {
        ensureNormalizedMode();
        setValueInternal(normalizedToRaw(value), false);
    }

    public boolean scroll(int delta) {
        if (!isEnabled() || delta == 0 || max == MIN) {
            return false;
        }

        int direction = delta > 0 ? -1 : 1;
        int target = value + direction * step;
        return setValueInternal(target, true);
    }

    @Override
    public Component setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        button.setEnabled(enabled && max > MIN);
        return this;
    }

    @Override
    public void setSize(int width, int height) {
        super.setSize(TRACK_WIDTH, normalizeHeight(height));
        updateButtonBounds();
        syncButtonToValue();
    }

    @Override
    protected void render(int mouseX, int mouseY, float partialTicks) {
        ComponentAtlas atlas = ComponentAtlas.INSTANCE;
        AtlasRegion region = atlas.getRegion(TRACK_SPRITE);
        if (region == null) {
            return;
        }

        int ax = getAbsoluteX();
        int ay = getAbsoluteY();

        AtlasRenderHelper.drawSubRegion(atlas, region,
            0, 0, TRACK_WIDTH, TRACK_TOP_HEIGHT,
            ax, ay, TRACK_WIDTH, TRACK_TOP_HEIGHT);

        int middleHeight = height - TRACK_TOP_HEIGHT - TRACK_BOTTOM_HEIGHT;
        if (middleHeight > 0) {
            int drawn = 0;
            while (drawn < middleHeight) {
                int sliceHeight = Math.min(TRACK_MIDDLE_REPEAT_HEIGHT, middleHeight - drawn);
                AtlasRenderHelper.drawSubRegion(atlas, region,
                    0, TRACK_MIDDLE_REPEAT_SRC_Y, TRACK_WIDTH, sliceHeight,
                    ax, ay + TRACK_TOP_HEIGHT + drawn, TRACK_WIDTH, sliceHeight);
                drawn += sliceHeight;
            }
        }

        AtlasRenderHelper.drawSubRegion(atlas, region,
            0, TRACK_BOTTOM_SRC_Y, TRACK_WIDTH, TRACK_BOTTOM_HEIGHT,
            ax, ay + height - TRACK_BOTTOM_HEIGHT, TRACK_WIDTH, TRACK_BOTTOM_HEIGHT);
    }

    @Override
    protected boolean onMouseScrolled(int mouseX, int mouseY, int delta) {
        return scroll(delta);
    }

    void onButtonDragged(boolean released) {
        syncValueFromButton();
        if (released) {
            syncButtonToValue();
            sliderParent.onSliderChanged(this);
        }
    }

    private boolean setValueInternal(int newValue, boolean notify) {
        if (max == MIN) {
            int old = value;
            value = MIN;
            syncButtonToValue();
            if (notify && old != value) {
                sliderParent.onSliderChanged(this);
            }
            return false;
        }

        int clamped = clampValue(newValue);
        if (value == clamped) {
            if (notify) {
                sliderParent.onSliderChanged(this);
            }
            return false;
        }

        value = clamped;
        syncButtonToValue();
        if (notify) {
            sliderParent.onSliderChanged(this);
        }
        return true;
    }

    private void updateButtonBounds() {
        int minY = minButtonY();
        int maxY = maxButtonY();
        if (max == MIN) {
            button.setDragBounds(new int[]{0, minY, 0, minY});
        } else {
            button.setDragBounds(new int[]{0, minY, 0, maxY});
        }
    }

    private void syncButtonToValue() {
        button.setX(0);
        button.setY(buttonYForValue(value));
    }

    private void syncValueFromButton() {
        int y = clamp(button.y, minButtonY(), maxButtonY());
        if (button.y != y) {
            button.setY(y);
        }
        value = valueForButtonY(y);
    }

    private int valueForButtonY(int buttonY) {
        if (max == MIN) {
            return MIN;
        }
        int travel = travel();
        if (travel <= 0) {
            return MIN;
        }
        float t = (buttonY - minButtonY()) / (float) travel;
        int mapped = MIN + Math.round(t * (max - MIN));
        return clampValue(mapped);
    }

    private int buttonYForValue(int value) {
        int travel = travel();
        if (travel <= 0 || max == MIN) {
            return minButtonY();
        }
        int clamped = clampValue(value);
        float t = (clamped - MIN) / (float) (max - MIN);
        int mapped = minButtonY() + Math.round(t * travel);
        return clamp(mapped, minButtonY(), maxButtonY());
    }

    private int travel() {
        return Math.max(0, maxButtonY() - minButtonY());
    }

    private int minButtonY() {
        return TOP_EXCLUSION;
    }

    private int maxButtonY() {
        return Math.max(minButtonY(), height - BOTTOM_EXCLUSION - BUTTON_HEIGHT);
    }

    private int clampValue(int value) {
        return clamp(value, MIN, max);
    }

    private void ensureNormalizedMode() {
        if (!normalizedMode) {
            throw new IllegalStateException("Slider is not configured for normalized values");
        }
    }
}
