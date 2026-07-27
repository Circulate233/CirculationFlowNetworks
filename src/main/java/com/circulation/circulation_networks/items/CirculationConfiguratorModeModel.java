package com.circulation.circulation_networks.items;

import com.circulation.circulation_networks.api.IEnergyHandler;

public final class CirculationConfiguratorModeModel {

    private CirculationConfiguratorModeModel() {
    }

    public static int normalizeScrollDelta(int rawWheelDelta) {
        int delta = -rawWheelDelta;
        if (delta % 120 == 0) {
            delta /= 120;
        }
        if (delta % 80 == 0) {
            delta /= 80;
        }
        return delta;
    }

    public static int nextFunctionId(int currentFunctionId) {
        return Math.floorMod(currentFunctionId + 1, ToolFunction.values().length);
    }

    public static int wrapSubMode(int rawMode, ToolFunction function) {
        return Math.floorMod(rawMode, function.getSubModeCount());
    }

    public enum ToolFunction {
        INSPECTION(InspectionMode.values().length, true),
        CONFIGURATION(ConfigurationMode.values().length, true),
        PRIORITY(1, false),
        INTERACTION(1, false);

        private final int subModeCount;
        private final boolean hasSubModes;

        ToolFunction(int subModeCount, boolean hasSubModes) {
            this.subModeCount = subModeCount;
            this.hasSubModes = hasSubModes;
        }

        public static ToolFunction fromID(int id) {
            return values()[Math.floorMod(id, values().length)];
        }

        public int getSubModeCount() {
            return subModeCount;
        }

        public boolean hasSubModes() {
            return hasSubModes;
        }

        public String getLangKey() {
            return "item.circulation_networks.circulation_configurator.mode." + name().toLowerCase();
        }

        public String getDescriptionLangKey() {
            return getLangKey() + ".description";
        }

        public String getSubModeLangKey(int subMode) {
            return switch (this) {
                case INSPECTION -> InspectionMode.fromID(subMode).getLangKey();
                case CONFIGURATION -> ConfigurationMode.fromID(subMode).getLangKey();
                case PRIORITY, INTERACTION -> throw new IllegalStateException(name() + " does not define sub-modes");
            };
        }
    }

    public enum InspectionMode {
        ALL,
        SPOCE,
        LINK,
        MACHINE_LINK;

        public static InspectionMode fromID(int id) {
            return values()[Math.floorMod(id, values().length)];
        }

        public boolean isMode(InspectionMode mode) {
            return this == ALL || this == mode;
        }

        public boolean isLinkMode() {
            return this == ALL || this == LINK || this == MACHINE_LINK;
        }

        public boolean showNodeLinks() {
            return this == ALL || this == LINK;
        }

        public boolean showMachineLinks() {
            return this == ALL || this == MACHINE_LINK;
        }

        public String getLangKey() {
            return "item.circulation_networks.circulation_configurator.submode.inspection." + name().toLowerCase();
        }
    }

    public enum ConfigurationMode {
        SEND(IEnergyHandler.EnergyType.SEND),
        RECEIVE(IEnergyHandler.EnergyType.RECEIVE),
        STORAGE(IEnergyHandler.EnergyType.STORAGE),
        INVALID(IEnergyHandler.EnergyType.INVALID),
        CLEAR(null);

        private final IEnergyHandler.EnergyType energyType;

        ConfigurationMode(IEnergyHandler.EnergyType energyType) {
            this.energyType = energyType;
        }

        public static ConfigurationMode fromID(int id) {
            return values()[Math.floorMod(id, values().length)];
        }

        public IEnergyHandler.EnergyType getEnergyType() {
            return energyType;
        }

        public String getLangKey() {
            return "item.circulation_networks.circulation_configurator.submode.configuration." + name().toLowerCase();
        }
    }
}
