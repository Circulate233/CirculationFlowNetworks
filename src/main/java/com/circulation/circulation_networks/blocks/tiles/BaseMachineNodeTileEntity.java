package com.circulation.circulation_networks.blocks.tiles;

import com.circulation.circulation_networks.api.IMachineNodeTileEntity;
import com.circulation.circulation_networks.api.node.IMachineNode;
import com.circulation.circulation_networks.energy.handler.CEHandler;
import com.circulation.circulation_networks.proxy.CommonProxy;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class BaseMachineNodeTileEntity extends BaseNodeTileEntity implements IMachineNodeTileEntity {

    protected CEHandler ceHandler;

    @Override
    public @NotNull IMachineNode getNode() {
        return (IMachineNode) super.getNode();
    }

    @Override
    public long getEnergy() {
        return ceHandler.getEnergy().getEnergy();
    }

    @Override
    public long getMaxEnergy() {
        return getNode().getMaxEnergy();
    }

    @Override
    public @NotNull CEHandler getCEHandler() {
        assert ceHandler != null;
        return ceHandler;
    }

    @Override
    public @NotNull NBTTagCompound writeToNBT(@NotNull NBTTagCompound compound) {
        var nbt = super.writeToNBT(compound);
        if (ceHandler != null) ceHandler.writeToNBT(nbt);
        return nbt;
    }

    @Override
    public void readFromNBT(@NotNull NBTTagCompound compound) {
        super.readFromNBT(compound);
        if (ceHandler == null) ceHandler = new CEHandler(this);
        ceHandler.readNBT(compound);
    }

    @Override
    public boolean hasCapability(@NotNull Capability<?> capability, @Nullable EnumFacing facing) {
        return (capability == CommonProxy.ceHandlerCapability && ceHandler != null) || super.hasCapability(capability, facing);
    }

    @Override
    public @Nullable <T> T getCapability(@NotNull Capability<T> capability, @Nullable EnumFacing facing) {
        return capability == CommonProxy.ceHandlerCapability && ceHandler != null ? CommonProxy.ceHandlerCapability.cast(ceHandler) : super.getCapability(capability, facing);
    }

    protected abstract IMachineNode createNode();

    protected void onValidate() {
        super.onValidate();
        if (ceHandler == null) ceHandler = new CEHandler(this);
    }
}