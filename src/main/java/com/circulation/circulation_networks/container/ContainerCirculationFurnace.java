package com.circulation.circulation_networks.container;

import com.circulation.circulation_networks.tiles.machines.TileEntityCirculationFurnace;
import com.circulation.circulation_networks.utils.GuiSync;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class ContainerCirculationFurnace extends CFNBaseContainer {

    private final TileEntityCirculationFurnace te;

    @GuiSync(0)
    public int cookTime;
    @GuiSync(1)
    public int totalCookTime;
    @GuiSync(2)
    public long currentFlow;
    @GuiSync(3)
    public long demandFlow;

    public ContainerCirculationFurnace(EntityPlayer player, TileEntityCirculationFurnace te) {
        super(player, te);
        this.te = te;
        this.addSlotToContainer(new Slot(te.inv, 0, 56, 35));
        this.addSlotToContainer(new Slot(te.inv, 1, 116, 35) {
            @Override
            public boolean isItemValid(@NotNull ItemStack stack) {
                return false;
            }
        });

        bindPlayerInventory(player.inventory, 8, 84);
    }

    @Override
    public boolean canInteractWith(@NotNull EntityPlayer playerIn) {
        return playerIn.getDistanceSq(te.getPos().getX() + 0.5D, te.getPos().getY() + 0.5D, te.getPos().getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (isServer()) {
            cookTime = te.getCookTime();
            totalCookTime = te.getTotalCookTime();
            currentFlow = te.getCurrentFlow();
            demandFlow = te.getDemandFlow();
        }
    }

    @Override
    public @NotNull ItemStack transferStackInSlot(@NotNull EntityPlayer playerIn, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.inventorySlots.get(index);

        if (slot != null && slot.getHasStack()) {
            ItemStack itemstack1 = slot.getStack();
            itemstack = itemstack1.copy();

            if (index == 1) {
                if (!this.mergeItemStack(itemstack1, 2, 38, true)) {
                    return ItemStack.EMPTY;
                }
                slot.onSlotChange(itemstack1, itemstack);
            } else if (index == 0) {
                if (!this.mergeItemStack(itemstack1, 2, 38, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!this.mergeItemStack(itemstack1, 0, 1, false)) {
                    if (index >= 2 && index < 29) {
                        if (!this.mergeItemStack(itemstack1, 29, 38, false)) {
                            return ItemStack.EMPTY;
                        }
                    } else if (index >= 29 && index < 38) {
                        if (!this.mergeItemStack(itemstack1, 2, 29, false)) {
                            return ItemStack.EMPTY;
                        }
                    }
                }
            }

            if (itemstack1.isEmpty()) {
                slot.putStack(ItemStack.EMPTY);
            } else {
                slot.onSlotChanged();
            }

            if (itemstack1.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(playerIn, itemstack1);
        }

        return itemstack;
    }
}