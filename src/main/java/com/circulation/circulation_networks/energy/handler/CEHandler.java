package com.circulation.circulation_networks.energy.handler;

import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.api.IMachineNodeTileEntity;
import com.circulation.circulation_networks.utils.CirculationEnergy;
import lombok.Getter;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

public final class CEHandler implements IEnergyHandler {

    @Getter
    private final TileEntity tileEntity;
    private final EnergyType type;
    @Getter
    private final CirculationEnergy energy;

    public CEHandler(TileEntity tileEntity) {
        this.tileEntity = tileEntity;
        this.type = ((IMachineNodeTileEntity) tileEntity).getNode().getType();
        this.energy = new CirculationEnergy(((IMachineNodeTileEntity) tileEntity).getMaxEnergy());
    }

    @Override
    public IEnergyHandler init(TileEntity tileEntity) {
        return this;
    }

    @Override
    public void clear() {

    }

    @Override
    public long receiveEnergy(long maxReceive) {
        return energy.receiveEnergy(maxReceive, false);
    }

    @Override
    public long extractEnergy(long maxExtract) {
        return energy.extractEnergy(maxExtract, false);
    }

    @Override
    public long canExtractValue() {
        if (type == EnergyType.RECEIVE) return 0;
        return energy.canExtractValue();
    }

    @Override
    public long canReceiveValue() {
        if (type == EnergyType.SEND) return 0;
        return energy.canReceiveValue();
    }

    @Override
    public boolean canExtract() {
        return type != EnergyType.RECEIVE && canExtractValue() > 0;
    }

    @Override
    public boolean canReceive() {
        return type != EnergyType.SEND && canReceiveValue() > 0;
    }

    @Override
    public EnergyType getType() {
        return type;
    }

    @Override
    public void recycle() {
    }

    public void writeToNBT(NBTTagCompound nbt) {
        nbt.setLong("energy", energy.getEnergy());
    }

    public void readNBT(NBTTagCompound nbt) {
        energy.setEnergy(nbt.getLong("energy"));
    }
}
