package com.circulation.circulation_networks;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class CFNConfig {

    public static final Node NODE = new Node();
    public static final Shielder SHIELDER = new Shielder();
    static final ModConfigSpec SPEC;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> CLASS_NAMES;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> SUPPLY_CLASS_NAMES;
    private static final ModConfigSpec.ConfigValue<String> DEFAULT_ENERGY_UNIT_DISPLAY;
    private static final ModConfigSpec.DoubleValue PORT_NODE_ENERGY_SCOPE;
    private static final ModConfigSpec.DoubleValue PORT_NODE_LINK_SCOPE;
    private static final ModConfigSpec.DoubleValue CHARGING_NODE_CHARGING_SCOPE;
    private static final ModConfigSpec.DoubleValue CHARGING_NODE_LINK_SCOPE;
    private static final ModConfigSpec.DoubleValue RELAY_NODE_LINK_SCOPE;
    private static final ModConfigSpec.DoubleValue HUB_ENERGY_SCOPE;
    private static final ModConfigSpec.DoubleValue HUB_CHARGING_SCOPE;
    private static final ModConfigSpec.DoubleValue HUB_LINK_SCOPE;
    private static final ModConfigSpec.IntValue SHIELDER_MAX_SCOPE;
    private static final ModConfigSpec.BooleanValue ANIMATED_SPECIAL_MODELS;
    public static String[] classNames = new String[0];
    public static String[] supplyClassNames = new String[0];
    public static String defaultEnergyUnitDisplay = "FE";

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        CLASS_NAMES = builder.comment(
            "Energy entity blacklist (fully qualified name or class name prefix)",
            "BlockEntities in the blacklist will not be recognized as energy containers",
            "Examples:",
            "  - 'com.example.CustomEnergyTile' Exact match",
            "  - 'com.example' Prefix match, includes all classes starting with this"
        ).defineListAllowEmpty(List.of("classNames"), List::of, () -> "", obj -> obj instanceof String);

        SUPPLY_CLASS_NAMES = builder.comment(
            "Energy device blacklist for generic supply nodes (non-specialized).",
            "Matched devices can ONLY be connected by specialized nodes that override isBlacklisted.",
            "Examples:",
            "  - 'com.example.AdvancedEnergyTile' Exact match",
            "  - 'com.example.advanced' Prefix match"
        ).defineListAllowEmpty(List.of("supplyClassNames"), List::of, () -> "", obj -> obj instanceof String);

        DEFAULT_ENERGY_UNIT_DISPLAY = builder.comment(
            "Default energy unit display (exact unit string match)",
            "After registry lock, the matched unit will be moved to the first display slot"
        ).define("defaultEnergyUnitDisplay", "FE");

        builder.push("Node");

        builder.push("PortNode");
        PORT_NODE_ENERGY_SCOPE = builder.comment("Energy range of Circulation Port Node").defineInRange("energyScope", 12.0, 0.1, 48.0);
        PORT_NODE_LINK_SCOPE = builder.comment("Link range of Circulation Port Node").defineInRange("linkScope", 18.0, 0.1, 48.0);
        builder.pop();

        builder.push("ChargingNode");
        CHARGING_NODE_CHARGING_SCOPE = builder.comment("Charging range of Circulation Charging Node").defineInRange("chargingScope", 7.5, 1.0, 48.0);
        CHARGING_NODE_LINK_SCOPE = builder.comment("Link range of Circulation Charging Node").defineInRange("linkScope", 12.0, 1.0, 48.0);
        builder.pop();

        builder.push("RelayNode");
        RELAY_NODE_LINK_SCOPE = builder.comment("Link range of Circulation Relay Node").defineInRange("linkScope", 30.0, 1.0, 96.0);
        builder.pop();

        builder.push("Hub");
        HUB_ENERGY_SCOPE = builder.comment("Energy range of Hub").defineInRange("energyScope", 15.0, 1.0, 48.0);
        HUB_CHARGING_SCOPE = builder.comment("Charging range of Hub").defineInRange("chargingScope", 12.0, 1.0, 48.0);
        HUB_LINK_SCOPE = builder.comment("Link range of Hub").defineInRange("linkScope", 24.0, 1.0, 48.0);
        builder.pop();

        builder.push("Rendering");
        ANIMATED_SPECIAL_MODELS = builder.comment("Enable animated models for relay nodes and node pedestals").define("animatedSpecialModels", true);
        builder.pop();

        builder.pop();

        builder.push("Shielder");
        SHIELDER_MAX_SCOPE = builder.comment("Max range of Circulation Shielder").defineInRange("maxScope", 8, 0, 16);
        builder.pop();

        SPEC = builder.build();
    }

    public static void register(ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, SPEC);
    }

    public static void onConfigLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SPEC) syncFromSpec();
    }

    public static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SPEC) syncFromSpec();
    }

    private static void syncFromSpec() {
        classNames = CLASS_NAMES.get().stream().map(Object::toString).toArray(String[]::new);
        supplyClassNames = SUPPLY_CLASS_NAMES.get().stream().map(Object::toString).toArray(String[]::new);
        defaultEnergyUnitDisplay = DEFAULT_ENERGY_UNIT_DISPLAY.get();
        NODE.portNode.energyScope = PORT_NODE_ENERGY_SCOPE.get();
        NODE.portNode.linkScope = PORT_NODE_LINK_SCOPE.get();
        NODE.chargingNode.chargingScope = CHARGING_NODE_CHARGING_SCOPE.get();
        NODE.chargingNode.linkScope = CHARGING_NODE_LINK_SCOPE.get();
        NODE.relayNode.linkScope = RELAY_NODE_LINK_SCOPE.get();
        NODE.hub.energyScope = HUB_ENERGY_SCOPE.get();
        NODE.hub.chargingScope = HUB_CHARGING_SCOPE.get();
        NODE.hub.linkScope = HUB_LINK_SCOPE.get();
        SHIELDER.maxScope = SHIELDER_MAX_SCOPE.get();
        NODE.rendering.animatedSpecialModels = ANIMATED_SPECIAL_MODELS.get();
    }

    public static class Node {
        public final PortNodeConfig portNode = new PortNodeConfig();
        public final ChargingNodeConfig chargingNode = new ChargingNodeConfig();
        public final RelayNodeConfig relayNode = new RelayNodeConfig();
        public final HubConfig hub = new HubConfig();
        public final RenderingConfig rendering = new RenderingConfig();

        public static class PortNodeConfig {
            public double energyScope = 12;
            public double linkScope = 18;
        }

        public static class ChargingNodeConfig {
            public double chargingScope = 7.5;
            public double linkScope = 12;
        }

        public static class RelayNodeConfig {
            public double linkScope = 30;
        }

        public static class HubConfig {
            public double energyScope = 15;
            public double chargingScope = 12;
            public double linkScope = 24;
        }

        public static class RenderingConfig {
            public boolean animatedSpecialModels = true;
        }
    }

    public static class Shielder {
        public int maxScope = 8;
    }
}
