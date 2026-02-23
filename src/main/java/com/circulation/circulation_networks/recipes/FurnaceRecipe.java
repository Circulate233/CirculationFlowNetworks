package com.circulation.circulation_networks.recipes;

import com.circulation.circulation_networks.tiles.machines.TileEntityCirculationFurnace;
import com.circulation.circulation_networks.utils.ItemStackKey;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;

import java.util.HashMap;
import java.util.Map;

public class FurnaceRecipe {

    public static FurnaceRecipe INSTANCE = new FurnaceRecipe();

    public final Map<ItemStackKey, ItemStackKey> recipes = new HashMap<>();

    public boolean checkInput(TileEntityCirculationFurnace furnace) {
        final var key = ItemStackKey.get(furnace.getInput());
        if (key.isEmpty()) return false;
        final var b = recipes.containsKey(key);
        key.recycle();
        return b;
    }

    public boolean canWork(TileEntityCirculationFurnace furnace) {
        final var key = ItemStackKey.get(furnace.getInput());
        if (key.isEmpty()) return false;
        var o = recipes.getOrDefault(key, ItemStackKey.EMPTY);
        key.recycle();
        if (o.isEmpty()) return false;
        var out = furnace.getOutput();
        if (out.isEmpty()) return true;
        return o.equals(out) && out.getCount() + o.getCount() <= out.getMaxStackSize();
    }

    public ItemStack getOutput(ItemStackKey key) {
        return recipes.getOrDefault(key, ItemStackKey.EMPTY).getItemStack();
    }

    public ItemStackKey getOutputKey(ItemStackKey key) {
        return recipes.getOrDefault(key, ItemStackKey.EMPTY);
    }

    public void init() {
        FurnaceRecipes.instance().getSmeltingList()
                      .forEach((key, stack) ->
                          FurnaceRecipe.INSTANCE.recipes.put(ItemStackKey.get(key), ItemStackKey.get(stack)));
    }

    public void clear() {
        recipes.clear();
    }
}