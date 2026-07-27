package com.circulation.circulation_networks.items;

import com.circulation.circulation_networks.items.CirculationConfiguratorModeModel.ToolFunction;
import com.circulation.circulation_networks.tooltip.LocalizedComponent;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public record CirculationConfiguratorSelection(ToolFunction function, int subMode) {

    public static final String MODE_DISPLAY_KEY = "item.circulation_networks.circulation_configurator.mode_display";
    public static final String CURRENT_MODE_KEY = "item.circulation_networks.circulation_configurator.current_mode";
    public static final String CURRENT_SUBMODE_KEY = "item.circulation_networks.circulation_configurator.current_submode";
    public static final String SWITCH_MODE_USAGE_KEY = "item.circulation_networks.circulation_configurator.usage.switch_mode";
    public static final String SWITCH_SUBMODE_USAGE_KEY = "item.circulation_networks.circulation_configurator.usage.switch_submode";

    public static CirculationConfiguratorSelection fromStack(ItemStack stack) {
        return new CirculationConfiguratorSelection(CirculationConfiguratorState.getFunction(stack), CirculationConfiguratorState.getSubMode(stack));
    }

    public String modeLangKey() {
        return function.getLangKey();
    }

    public String modeDisplayKey() {
        return MODE_DISPLAY_KEY;
    }

    public String currentModeDisplayKey() {
        return CURRENT_MODE_KEY;
    }

    public String currentSubModeDisplayKey() {
        return CURRENT_SUBMODE_KEY;
    }

    public String subModeLangKey() {
        return function.getSubModeLangKey(subMode);
    }

    public String descriptionLangKey() {
        return function.getDescriptionLangKey();
    }

    public String switchModeUsageKey() {
        return SWITCH_MODE_USAGE_KEY;
    }

    public String switchSubModeUsageKey() {
        return SWITCH_SUBMODE_USAGE_KEY;
    }

    public List<LocalizedComponent> tooltipLines() {
        List<LocalizedComponent> lines = new ObjectArrayList<>();
        lines.add(LocalizedComponent.withTranslatedArg(currentModeDisplayKey(), modeLangKey()));
        if (function.hasSubModes()) {
            lines.add(LocalizedComponent.withTranslatedArg(currentSubModeDisplayKey(), subModeLangKey()));
        }
        lines.add(LocalizedComponent.description(descriptionLangKey()));
        lines.add(LocalizedComponent.blank());
        lines.add(LocalizedComponent.of(switchModeUsageKey()));
        if (function.hasSubModes()) {
            lines.add(LocalizedComponent.of(switchSubModeUsageKey()));
        }
        return lines;
    }
}
