package com.circulation.circulation_networks.tiles.machines;

import com.circulation.circulation_networks.api.node.IMachineNode;
import com.circulation.circulation_networks.container.ContainerCirculationFurnace;
import com.circulation.circulation_networks.gui.GuiCirculationFurnace;
import com.circulation.circulation_networks.network.nodes.machine_node.ConsumerNode;
import com.circulation.circulation_networks.recipes.FurnaceRecipe;
import com.circulation.circulation_networks.utils.ItemStackKey;
import lombok.Getter;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.jetbrains.annotations.NotNull;

public final class TileEntityCirculationFurnace extends BaseInvMachineNodeTileEntity {

    @Getter
    private int cookTime;
    @Getter
    private int totalCookTime;

    private int delayTime;

    @Override
    protected IMachineNode createNode() {
        return new ConsumerNode(this, 5);
    }

    @Override
    public void serverUpdate() {
        super.serverUpdate();
        if (totalCookTime <= 0) {
            if (delayTime > 0) {
                --delayTime;
                return;
            }
            if (FurnaceRecipe.INSTANCE.canWork(this)) {
                totalCookTime = 20;
                cookTime = 0;
                setMaxEnergy(10);
                delayTime = 0;
            } else delayTime = 10;
            return;
        }

        if (FurnaceRecipe.INSTANCE.checkInput(this)) {
            if (getEnergy() == getMaxEnergy()) {
                removeEnergy(getMaxEnergy(), false);
                ++cookTime;
            } else return;
        } else stop();

        if (cookTime >= totalCookTime) {
            if (FurnaceRecipe.INSTANCE.canWork(this)) {
                var o = FurnaceRecipe.INSTANCE.getOutputKey(ItemStackKey.get(getInput()));
                if (getOutput().isEmpty()) {
                    setOutput(o.getItemStack());
                } else {
                    addOutput((int) o.getCount());
                }
                removeInput();
            }
            stop();
        }
    }


    @Override
    public void delayedReadFromNBT(@NotNull NBTTagCompound compound) {
        super.delayedReadFromNBT(compound);
        cookTime = compound.getInteger("cookTime");
        totalCookTime = compound.getInteger("totalCookTime");
        setMaxEnergy(compound.getInteger("maxEnergy"));
    }

    @Override
    public @NotNull NBTTagCompound writeToNBT(@NotNull NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setInteger("cookTime", cookTime);
        compound.setInteger("totalCookTime", totalCookTime);
        compound.setLong("maxEnergy", getMaxEnergy());
        return compound;
    }

    public void stop() {
        totalCookTime = 0;
        cookTime = 0;
        setMaxEnergy(0);
    }

    public long getCurrentFlow() {
        return getCirculationEnergy().getEnergy();
    }

    public long getDemandFlow() {
        return getNode().getMaxEnergy();
    }

    @Override
    public boolean hasGui() {
        return true;
    }

    @Override
    public ContainerCirculationFurnace getContainer(EntityPlayer player) {
        return new ContainerCirculationFurnace(player, this);
    }

    @Override
    public GuiContainer getGui(EntityPlayer player) {
        return new GuiCirculationFurnace(player, this);
    }

    @Override
    public IInventory createInventory() {
        return new InventoryBasic("furnace", false, 2);
    }

    public ItemStack getInput() {
        return inv.getStackInSlot(0);
    }

    public ItemStack getOutput() {
        return inv.getStackInSlot(1);
    }

    public void setOutput(ItemStack output) {
        inv.setInventorySlotContents(1, output);
    }

    public void removeInput() {
        inv.getStackInSlot(0).shrink(1);
    }

    public void addOutput(int output) {
        inv.getStackInSlot(1).grow(output);
    }
}