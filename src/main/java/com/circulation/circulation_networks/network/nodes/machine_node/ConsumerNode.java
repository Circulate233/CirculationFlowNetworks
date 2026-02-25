package com.circulation.circulation_networks.network.nodes.machine_node;

import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.api.IMachineNodeTileEntity;
import net.minecraft.nbt.NBTTagCompound;

public class ConsumerNode extends MachineNode {

    public ConsumerNode(IMachineNodeTileEntity tileEntity, double linkScope) {
        super(tileEntity, 0, linkScope);
    }

    public ConsumerNode(NBTTagCompound compound) {
        super(compound);
    }

    @Override
    public IEnergyHandler.EnergyType getType() {
        return IEnergyHandler.EnergyType.RECEIVE;
    }

}