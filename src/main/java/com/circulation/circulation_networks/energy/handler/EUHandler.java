package com.circulation.circulation_networks.energy.handler;

import com.circulation.circulation_networks.api.IEnergyHandler;
import ic2.api.energy.EnergyNet;
import ic2.api.energy.tile.IEnergySink;
import ic2.api.energy.tile.IEnergySource;
import ic2.api.energy.tile.IEnergyTile;
import lombok.Getter;
import net.minecraft.tileentity.TileEntity;

import javax.annotation.Nonnull;

@SuppressWarnings("DataFlowIssue")
public final class EUHandler implements IEnergyHandler {

    private static final long max = Long.MAX_VALUE >> 2;
    private static final long maxFE = max << 2;
    @Getter
    private TileEntity tileEntity;
    @Nonnull
    private EnergyType energyType;
    private IEnergySource send;
    private IEnergySink receive;

    public EUHandler(TileEntity tileEntity) {
        this.tileEntity = tileEntity;
        init();
    }

    @Override
    public IEnergyHandler init(TileEntity tileEntity) {
        this.tileEntity = tileEntity;
        init();
        return this;
    }

    private void init() {
        IEnergyTile tile = EnergyNet.instance.getSubTile(tileEntity.getWorld(), tileEntity.getPos());
        boolean o = tile instanceof IEnergySource;
        boolean i = tile instanceof IEnergySink;
        if (o) {
            if (i) {
                energyType = EnergyType.STORAGE;
                receive = (IEnergySink) tile;
            } else energyType = EnergyType.SEND;
            send = (IEnergySource) tile;
        } else {
            energyType = EnergyType.RECEIVE;
            receive = (IEnergySink) tile;
        }
        if (!canExtract() && !canReceive()) energyType = EnergyType.INVALID;
    }

    @Override
    public void clear() {
        this.tileEntity = null;
        this.energyType = EnergyType.INVALID;
        this.send = null;
        this.receive = null;
    }

    @Override
    public long receiveEnergy(long maxReceive) {
        long i = (Math.min(canReceiveValue(), maxReceive)) >> 2;
        receive.injectEnergy(null, i, 0);
        return i << 2;
    }

    @Override
    public long extractEnergy(long maxExtract) {
        long o = (Math.min(canExtractValue(), maxExtract)) >> 2;
        send.drawEnergy(o);
        return o << 2;
    }

    @Override
    public long canExtractValue() {
        if (send.getOfferedEnergy() > max) return maxFE;
        return ((long) send.getOfferedEnergy()) << 2;
    }

    @Override
    public long canReceiveValue() {
        if (receive.getDemandedEnergy() > max) return maxFE;
        return ((long) receive.getDemandedEnergy()) << 2;
    }

    @Override
    public boolean canExtract() {
        return send != null && send.getOfferedEnergy() > 0;
    }

    @Override
    public boolean canReceive() {
        return receive != null && receive.getDemandedEnergy() > 0;
    }

    @Override
    public EnergyType getType() {
        return energyType;
    }
}
