package com.circulation.circulation_networks.registry;

import com.circulation.circulation_networks.items.DebugItem;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public final class RegistryItems {

    private static final List<Item> ITEMS_TO_REGISTER = new LinkedList<>();
    private static final List<Item> ITEM_MODELS_TO_REGISTER = new LinkedList<>();

    public static DebugItem debugItem;

    public static void registerItems(RegistryEvent.Register<Item> event) {
        debugItem = registryItem(new DebugItem());

        ITEMS_TO_REGISTER.forEach(event.getRegistry()::register);
        ITEMS_TO_REGISTER.clear();
    }

    public static <T extends Item> T registryItem(T item) {
        ITEMS_TO_REGISTER.add(item);
        ITEM_MODELS_TO_REGISTER.add(item);
        return item;
    }

    public static void registerItemModels() {
        if (FMLCommonHandler.instance().getSide().isServer()) {
            ITEM_MODELS_TO_REGISTER.clear();
            return;
        }
        ITEM_MODELS_TO_REGISTER.forEach(RegistryItems::registerItemModel);
        ITEM_MODELS_TO_REGISTER.clear();
    }

    public static void registerItemModel(final Item item) {
        NonNullList<ItemStack> list = NonNullList.create();
        ResourceLocation registryName = Objects.requireNonNull(item.getRegistryName());

        item.getSubItems(Objects.requireNonNull(item.getCreativeTab()), list);
        if (list.isEmpty()) {
            ModelLoader.setCustomModelResourceLocation(
                item, 0, new ModelResourceLocation(registryName, "inventory"));
        } else {
            list.forEach(stack -> ModelLoader.setCustomModelResourceLocation(
                item, stack.getItemDamage(), new ModelResourceLocation(registryName, "inventory")));
        }
    }
}