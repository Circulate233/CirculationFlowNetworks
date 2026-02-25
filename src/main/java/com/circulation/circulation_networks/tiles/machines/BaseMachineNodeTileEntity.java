package com.circulation.circulation_networks.tiles.machines;

import com.circulation.circulation_networks.api.IMachineNodeTileEntity;
import com.circulation.circulation_networks.api.node.IMachineNode;
import com.circulation.circulation_networks.energy.handler.CEHandler;
import com.circulation.circulation_networks.proxy.CommonProxy;
import com.circulation.circulation_networks.tiles.nodes.BaseNodeTileEntity;
import com.circulation.circulation_networks.utils.CirculationEnergy;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class BaseMachineNodeTileEntity extends BaseNodeTileEntity implements IMachineNodeTileEntity {

    protected CEHandler ceHandler;
    private transient NBTTagCompound initNbt;

    @Override
    public @NotNull IMachineNode getNode() {
        return (IMachineNode) super.getNode();
    }

    @Override
    public @NotNull CEHandler getCEHandler() {
        assert ceHandler != null;
        return ceHandler;
    }

    public final CirculationEnergy getCirculationEnergy() {
        return getCEHandler().getEnergy();
    }

    public long getMaxEnergy() {
        return getNode().getMaxEnergy();
    }

    public void setMaxEnergy(long energy) {
        getNode().setMaxEnergy(energy);
    }

    public long getEnergy() {
        return getCirculationEnergy().getEnergy();
    }

    public long addEnergy(long energy, boolean simulate) {
        return getCirculationEnergy().receiveEnergy(energy, simulate);
    }

    public long removeEnergy(long energy, boolean simulate) {
        return getCirculationEnergy().extractEnergy(energy, simulate);
    }

    @Override
    public @NotNull NBTTagCompound writeToNBT(@NotNull NBTTagCompound compound) {
        var nbt = super.writeToNBT(compound);
        if (ceHandler != null) ceHandler.writeToNBT(nbt);
        return nbt;
    }

    @Override
    public final void readFromNBT(@NotNull NBTTagCompound compound) {
        super.readFromNBT(compound);
        if (ceHandler == null) {
            initNbt = compound;
        } else {
            delayedReadFromNBT(compound);
        }
    }

    public void delayedReadFromNBT(@NotNull NBTTagCompound compound) {
        ceHandler.readNBT(initNbt);
    }

    @Override
    public boolean hasCapability(@NotNull Capability<?> capability, @Nullable EnumFacing facing) {
        return (capability == CommonProxy.ceHandlerCapability && ceHandler != null) || super.hasCapability(capability, facing);
    }

    @Override
    public @Nullable <T> T getCapability(@NotNull Capability<T> capability, @Nullable EnumFacing facing) {
        return capability == CommonProxy.ceHandlerCapability && ceHandler != null ? CommonProxy.ceHandlerCapability.cast(ceHandler) : super.getCapability(capability, facing);
    }

    protected abstract @NotNull IMachineNode createNode();

    protected void onValidate() {
        super.onValidate();
        if (ceHandler == null) ceHandler = new CEHandler(this);
        if (initNbt != null) {
            delayedReadFromNBT(initNbt);
            initNbt = null;
        }
    }
}