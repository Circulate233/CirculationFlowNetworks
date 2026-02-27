package com.circulation.circulation_networks.network.nodes.machine_node;


import com.circulation.circulation_networks.api.IMachineNodeTileEntity;
import com.circulation.circulation_networks.api.node.IMachineNode;
import com.circulation.circulation_networks.network.nodes.Node;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.nbt.NBTTagCompound;

public abstract class MachineNode extends Node implements IMachineNode {

    protected final double energyScope;
    protected final double linkScope;
    @Getter
    @Setter
    private long maxEnergy;

    public MachineNode(NBTTagCompound compound) {
        super(compound);
        this.energyScope = compound.getDouble("energyScope");
        this.linkScope = compound.getDouble("linkScope");
    }

    public MachineNode(IMachineNodeTileEntity tileEntity, double energyScope, double linkScope) {
        super(tileEntity);
        this.energyScope = energyScope;
        this.linkScope = linkScope;
    }

    @Override
    public double getLinkScope() {
        return linkScope;
    }

    @Override
    public double getEnergyScope() {
        return energyScope;
    }

    @Override
    public NBTTagCompound serialize() {
        var nbt = super.serialize();
        nbt.setDouble("energyScope", energyScope);
        nbt.setDouble("linkScope", linkScope);
        nbt.setLong("maxEnergy", maxEnergy);
        return nbt;
    }
}
