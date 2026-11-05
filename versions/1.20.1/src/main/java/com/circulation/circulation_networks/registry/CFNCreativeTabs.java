package com.circulation.circulation_networks.registry;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegisterEvent;

import java.util.function.Consumer;

public final class CFNCreativeTabs {

    public static CreativeModeTab MAIN;

    private CFNCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(CFNCreativeTabs::onRegister);
    }

    private static void onRegister(RegisterEvent event) {
        event.register(Registries.CREATIVE_MODE_TAB, helper -> MAIN = registerTab(helper, "main", createMainTab()));
    }

    private static CreativeModeTab createMainTab() {
        return CreativeModeTab.builder()
                              .title(Component.translatable("itemGroup.circulation_networks"))
                              .icon(() -> CFNBlocks.blockHub == null ? new ItemStack(Items.BARRIER) : new ItemStack(CFNBlocks.blockHub))
                              .displayItems((parameters, output) -> collectDisplayItems(output::accept))
                              .build();
    }

    static void collectDisplayItems(Consumer<ItemLike> consumer) {
        consumer.accept(CFNBlocks.blockHub);
        consumer.accept(CFNBlocks.blockChargingNode);
        consumer.accept(CFNBlocks.blockRelayNode);
        consumer.accept(CFNBlocks.blockLongDistanceRelayNode);
        consumer.accept(CFNBlocks.blockPortNode);
        consumer.accept(CFNBlocks.blockCirculationShielder);
        consumer.accept(CFNBlocks.blockNodePedestal);
        consumer.accept(CFNItems.circulationConfigurator);
        consumer.accept(CFNItems.pocketPortNode);
        consumer.accept(CFNItems.pocketChargingNode);
        consumer.accept(CFNItems.pocketRelayNode);
        consumer.accept(CFNItems.hubChannelPlugin);
        consumer.accept(CFNItems.wideAreaChargingPlugin);
        consumer.accept(CFNItems.dimensionalChargingPlugin);
        consumer.accept(CFNItems.sourceflowCrystal);
        consumer.accept(CFNItems.netherforgedCrystal);
        consumer.accept(CFNItems.endercoreCrystal);
    }

    private static CreativeModeTab registerTab(RegisterEvent.RegisterHelper<CreativeModeTab> helper, String name, CreativeModeTab tab) {
        helper.register(new ResourceLocation(CirculationFlowNetworks.MOD_ID, name), tab);
        return tab;
    }
}
