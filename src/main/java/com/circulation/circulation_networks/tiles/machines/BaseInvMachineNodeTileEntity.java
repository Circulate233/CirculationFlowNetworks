package com.circulation.circulation_networks.tiles.machines;

import net.minecraft.inventory.IInventory;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public abstract class BaseInvMachineNodeTileEntity extends BaseMachineNodeTileEntity {

    public final IInventory inv = createInventory();
    private final IItemHandler inventory = new InvWrapper(inv);

    public abstract IInventory createInventory();

    @Override
    public void delayedReadFromNBT(@NotNull NBTTagCompound compound) {
        super.delayedReadFromNBT(compound);
        CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.readNBT(inventory, null, compound.getTag("inventory"));
    }

    @Override
    public @NotNull NBTTagCompound writeToNBT(@NotNull NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setTag("inventory", Objects.requireNonNull(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.writeNBT(inventory, null)));
        return compound;
    }

    @Override
    public boolean hasCapability(@NotNull Capability<?> capability, @Nullable EnumFacing facing) {
        return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY == capability || super.hasCapability(capability, facing);
    }

    @Override
    public <T> @Nullable T getCapability(@NotNull Capability<T> capability, @Nullable EnumFacing facing) {
        if (CapabilityItemHandler.ITEM_HANDLER_CAPABILITY == capability) {
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(inventory);
        }
        return super.getCapability(capability, facing);
    }

}