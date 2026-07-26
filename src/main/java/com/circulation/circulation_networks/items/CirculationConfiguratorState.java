package com.circulation.circulation_networks.items;

import com.circulation.circulation_networks.items.CirculationConfiguratorModeModel.ToolFunction;
import com.circulation.circulation_networks.utils.ItemStackTagUtils;
//~ mc_imports
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
//? if >=1.21 {
/*import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
*///?}

//? if <1.20
import com.github.bsideup.jabel.Desugar;

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
        //? if <1.21 {
        putInt(ItemStackTagUtils.getOrCreateTagCompound(stack), MODE_KEY, normalizedSubMode);
        //?} else {
        /*CompoundTag tag = ItemStackTagUtils.getOrCreateTagCompound(stack);
        putInt(tag, MODE_KEY, normalizedSubMode);
        ItemStackTagUtils.saveTagCompound(stack, tag);
        *///?}
    }

    public static ToggleResult toggleFunction(ItemStack stack) {
        var nbt = ItemStackTagUtils.getOrCreateTagCompound(stack);
        ToolFunction previousFunction = ToolFunction.fromID(getInt(nbt, FUNCTION_KEY));
        ToolFunction currentFunction = ToolFunction.fromID(CirculationConfiguratorModeModel.nextFunctionId(previousFunction.ordinal()));
        putInt(nbt, FUNCTION_KEY, currentFunction.ordinal());
        putInt(nbt, MODE_KEY, 0);
        //? if >=1.21 {
        /*ItemStackTagUtils.saveTagCompound(stack, nbt);
          *///?}
        return new ToggleResult(previousFunction, currentFunction);
    }

    //? if <1.20 {
    private static NBTTagCompound getTag(ItemStack stack) {
        return stack.getTagCompound();
    }

    private static int getInt(NBTTagCompound nbt, String key) {
        return nbt.getInteger(key);
    }

    private static void putInt(NBTTagCompound nbt, String key, int value) {
        nbt.setInteger(key, value);
    }
    //?} else if <1.21 {
    /*private static CompoundTag getTag(ItemStack stack) {
        return stack.getTag();
    }

    private static int getInt(CompoundTag nbt, String key) {
        return nbt.getInt(key);
    }

    private static void putInt(CompoundTag nbt, String key, int value) {
        nbt.putInt(key, value);
    }
    *///?} else {
    /*private static CompoundTag getTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null ? data.copyTag() : null;
    }

    private static int getInt(CompoundTag nbt, String key) {
        return nbt.getInt(key);
    }

    private static void putInt(CompoundTag nbt, String key, int value) {
        nbt.putInt(key, value);
    }
    *///?}


    //? if <1.20
    @Desugar
    public record ToggleResult(ToolFunction previousFunction, ToolFunction currentFunction) {

    }
}
