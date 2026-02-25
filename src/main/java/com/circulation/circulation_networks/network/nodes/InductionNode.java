package com.circulation.circulation_networks.network.nodes;

import com.circulation.circulation_networks.api.INodeTileEntity;
import com.circulation.circulation_networks.api.node.IEnergySupplyNode;
import net.minecraft.nbt.NBTTagCompound;

public final class InductionNode extends Node implements IEnergySupplyNode {

    private final double energyScope;
    private final double linkScope;

    public InductionNode(NBTTagCompound tag) {
        super(tag);
        energyScope = tag.getDouble("energyScope");
        linkScope = tag.getDouble("linkScope");
    }

    public InductionNode(INodeTileEntity tileEntity, double energyScope, double linkScope) {
        super(tileEntity);
        this.energyScope = energyScope;
        this.linkScope = linkScope;
    }

    @Override
    public double getEnergyScope() {
        return energyScope;
    }

    @Override
    public double getLinkScope() {
        return linkScope;
    }

    @Override
    public NBTTagCompound serialize() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setDouble("energyScope", energyScope);
        tag.setDouble("linkScope", linkScope);
        return tag;
    }
}
