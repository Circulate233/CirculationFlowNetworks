package com.circulation.circulation_networks.network.nodes;

import com.circulation.circulation_networks.api.INodeTileEntity;
import com.circulation.circulation_networks.api.node.IChargingNode;
import net.minecraft.nbt.NBTTagCompound;

public final class ChargingNode extends Node implements IChargingNode {


    private final double chargingScope;
    private final double linkScope;

    public ChargingNode(NBTTagCompound tag) {
        super(tag);
        this.chargingScope = tag.getDouble("chargingScope");
        this.linkScope = tag.getDouble("linkScope");
    }

    public ChargingNode(INodeTileEntity tileEntity, double chargingScope, double linkScope) {
        super(tileEntity);
        this.chargingScope = chargingScope;
        this.linkScope = linkScope;
    }

    @Override
    public double getChargingScope() {
        return chargingScope;
    }

    @Override
    public double getLinkScope() {
        return linkScope;
    }

    @Override
    public NBTTagCompound serialize() {
        var nbt = super.serialize();
        nbt.setDouble("chargingScope", chargingScope);
        nbt.setDouble("linkScope", linkScope);
        return nbt;
    }

}
