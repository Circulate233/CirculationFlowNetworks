package com.circulation.circulation_networks.container;

//~ mc_imports
import net.minecraft.item.ItemStack;
//? if <1.20 {
import net.minecraft.entity.player.EntityPlayer;
//?} else {
/*import net.minecraft.entity.player.EntityPlayer;
 *///?}
//? if <1.21 {
import net.minecraftforge.items.IItemHandler;
//?} else {
/*import net.neoforged.neoforge.items.IItemHandler;
 *///?}
import org.jetbrains.annotations.NotNull;

import java.util.function.BooleanSupplier;

public class HubPluginSlot extends ComponentSlot {

    private final BooleanSupplier canModifySupplier;

    public HubPluginSlot(BooleanSupplier canModifySupplier, IItemHandler inventory, int index, int relX, int relY) {
        super(inventory, index, relX, relY);
        this.canModifySupplier = canModifySupplier;
    }

    public boolean canModify() {
        return canModifySupplier.getAsBoolean();
    }

    //~ if >=1.20 'canTakeStack(@NotNull EntityPlayer' -> 'mayPickup(@NotNull Player' {
    //~ if >=1.20 'isItemValid(' -> 'mayPlace(' {
    @Override
    public boolean canTakeStack(@NotNull EntityPlayer player) {
        return canModify();
    }

    @Override
    public boolean isItemValid(@NotNull ItemStack stack) {
        return canModify() && super.isItemValid(stack);
    }
    //~}
    //~}
}
