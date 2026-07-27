package com.circulation.circulation_networks.registry;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.items.ItemCirculationConfigurator;
import com.circulation.circulation_networks.items.ItemDimensionalChargingPlugin;
import com.circulation.circulation_networks.items.ItemHubChannelPlugin;
import com.circulation.circulation_networks.items.ItemMaterial;
import com.circulation.circulation_networks.items.ItemPocketNode;
import com.circulation.circulation_networks.items.ItemWideAreaChargingPlugin;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.function.Function;

public final class RegistryItems {

    private RegistryItems() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(RegistryItems::onRegisterItems);
    }

    private static void onRegisterItems(RegisterEvent event) {
        event.register(Registries.ITEM, helper -> {
            CFNItems.circulationConfigurator = register(helper, "circulation_configurator", ItemCirculationConfigurator::new);
            CFNItems.pocketPortNode = register(helper, "pocket_port_node", properties -> new ItemPocketNode(NodeTypes.PORT_NODE, properties));
            CFNItems.pocketChargingNode = register(helper, "pocket_charging_node", properties -> new ItemPocketNode(NodeTypes.CHARGING_NODE, properties));
            CFNItems.pocketRelayNode = register(helper, "pocket_relay_node", properties -> new ItemPocketNode(NodeTypes.RELAY_NODE, properties));
            PocketNodeItems.register(NodeTypes.PORT_NODE, CFNItems.pocketPortNode);
            PocketNodeItems.register(NodeTypes.CHARGING_NODE, CFNItems.pocketChargingNode);
            PocketNodeItems.register(NodeTypes.RELAY_NODE, CFNItems.pocketRelayNode);
            CFNItems.hubChannelPlugin = register(helper, "hub_channel_plugin", ItemHubChannelPlugin::new);
            CFNItems.wideAreaChargingPlugin = register(helper, "wide_area_charging_plugin", ItemWideAreaChargingPlugin::new);
            CFNItems.dimensionalChargingPlugin = register(helper, "dimensional_charging_plugin", ItemDimensionalChargingPlugin::new);
            CFNItems.sourceflowCrystal = register(helper, "sourceflow_crystal", ItemMaterial::new);
            CFNItems.netherforgedCrystal = register(helper, "netherforged_crystal", ItemMaterial::new);
            CFNItems.endercoreCrystal = register(helper, "endercore_crystal", ItemMaterial::new);
        });
    }

    private static <T extends Item> T register(RegisterEvent.RegisterHelper<Item> helper, String name, Function<Item.Properties, T> factory) {
        Identifier id = Identifier.parse(CirculationFlowNetworks.MOD_ID + ":" + name);
        T item = factory.apply(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id)));
        helper.register(id, item);
        return item;
    }
}
