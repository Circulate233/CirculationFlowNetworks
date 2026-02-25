package com.circulation.circulation_networks.network.nodes.machine_node;

import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.api.IMachineNodeTileEntity;
import net.minecraft.nbt.NBTTagCompound;

public final class GeneratorNode extends MachineNode {

    public GeneratorNode(IMachineNodeTileEntity tileEntity, double energyScope, double linkScope) {
        super(tileEntity, energyScope, linkScope);
    }

    public GeneratorNode(NBTTagCompound tag) {
        super(tag);
    }

    @Override
    public IEnergyHandler.EnergyType getType() {
        return IEnergyHandler.EnergyType.SEND;
    }

}