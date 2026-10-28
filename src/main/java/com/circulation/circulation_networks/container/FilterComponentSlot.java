package com.circulation.circulation_networks.container;

//~ mc_imports

import net.minecraft.item.ItemStack;
//? if <1.20 {
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
//?} else {
/*import net.minecraft.entity.player.EntityPlayer;
 *///?}
//? if <1.21 {
import net.minecraftforge.items.IItemHandler;
//?} else {
/*import net.neoforged.neoforge.items.IItemHandler;
 *///?}
import org.jetbrains.annotations.NotNull;

public class FilterComponentSlot extends ComponentSlot {

    private final int maxCount;

    //? if <1.20 {
    public FilterComponentSlot(IInventory inventory, int index, int relX, int relY, int maxCount) {
        super(inventory, index, relX, relY);
        this.maxCount = maxCount;
    }
    //?}

    public FilterComponentSlot(IItemHandler inventory, int index, int relX, int relY, int maxCount) {
        super(inventory, index, relX, relY);
        this.maxCount = maxCount;
    }

    //~ if >=1.20 'putStack(' -> 'set(' {
    //~ if >=1.20 'getSlotStackLimit()' -> 'getMaxStackSize()' {
    //~ if >=1.20 'decrStackSize(' -> 'remove(' {
    //~ if >=1.20 'isItemValid(' -> 'mayPlace(' {
    //~ if >=1.20 'canTakeStack(@NotNull EntityPlayer' -> 'mayPickup(@NotNull Player' {
    public void ghostClickWith(ItemStack held, int dragType) {
        if (held.isEmpty()) {
            putStack(ItemStack.EMPTY);
        } else {
            ItemStack filter = held.copy();
            filter.setCount(dragType == 1 ? 1 : Math.min(held.getCount(), getSlotStackLimit()));
            putStack(filter);
        }
    }

    @Override
    public void putStack(ItemStack is) {
        super.putStack(is.isEmpty() ? is : is.copy());
    }

    @Override
    public @NotNull ItemStack decrStackSize(int amount) {
        return ItemStack.EMPTY;
    }

    //? if <1.20 {
    @Override
    public @NotNull ItemStack onTake(@NotNull EntityPlayer player, @NotNull ItemStack stack) {
        return stack;
    }
    //?} else {
    /*@Override
    public void onTake(@NotNull Player player, @NotNull ItemStack stack) {
    }
    *///?}

    @Override
    public boolean isItemValid(@NotNull ItemStack stack) {
        return false;
    }

    @Override
    public boolean canTakeStack(@NotNull EntityPlayer player) {
        return false;
    }

    @Override
    public int getSlotStackLimit() {
        return maxCount;
    }
    //~}
    //~}
    //~}
    //~}
    //~}
}