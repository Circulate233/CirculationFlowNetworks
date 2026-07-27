package com.circulation.circulation_networks.items;

import com.circulation.circulation_networks.items.CirculationConfiguratorModeModel.ToolFunction;
import com.circulation.circulation_networks.utils.Functions;
import com.circulation.circulation_networks.utils.NbtCompat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;

public final class CirculationConfiguratorState {

    private static final String FUNCTION_KEY = "function";
    private static final String MODE_KEY = "mode";

    private CirculationConfiguratorState() {
    }

    public static ToolFunction getFunction(ItemStack stack) {
        var nbt = getTag(stack);
        if (nbt == null) {
            return ToolFunction.INSPECTION;
        }
        return ToolFunction.fromID(getInt(nbt, FUNCTION_KEY));
    }

    public static int getSubMode(ItemStack stack) {
        var nbt = getTag(stack);
        if (nbt == null) {
            return 0;
        }
        return getInt(nbt, MODE_KEY);
    }

    public static void setSubMode(ItemStack stack, int subMode) {
        int normalizedSubMode = CirculationConfiguratorModeModel.wrapSubMode(subMode, getFunction(stack));
        CompoundTag tag = Functions.getOrCreateTagCompound(stack);
        putInt(tag, MODE_KEY, normalizedSubMode);
        Functions.saveTagCompound(stack, tag);
    }

    public static ToggleResult toggleFunction(ItemStack stack) {
        var nbt = Functions.getOrCreateTagCompound(stack);
        ToolFunction previousFunction = ToolFunction.fromID(getInt(nbt, FUNCTION_KEY));
        ToolFunction currentFunction = ToolFunction.fromID(CirculationConfiguratorModeModel.nextFunctionId(previousFunction.ordinal()));
        putInt(nbt, FUNCTION_KEY, currentFunction.ordinal());
        putInt(nbt, MODE_KEY, 0);
        Functions.saveTagCompound(stack, nbt);
        return new ToggleResult(previousFunction, currentFunction);
    }

    private static CompoundTag getTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null ? data.copyTag() : null;
    }

    private static int getInt(CompoundTag nbt, String key) {
        return NbtCompat.getIntOr(nbt, key, 0);
    }

    private static void putInt(CompoundTag nbt, String key, int value) {
        nbt.putInt(key, value);
    }

    public record ToggleResult(ToolFunction previousFunction, ToolFunction currentFunction) {

    }
}
