package com.circulation.circulation_networks.utils;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nonnull;

public final class Functions {

    @Nonnull
    public static NBTTagCompound getOrCreateTagCompound(ItemStack stack) {
        var nbt = stack.getTagCompound();
        if (nbt == null) {
            stack.setTagCompound(nbt = new NBTTagCompound());
        }
        return nbt;
    }
}
