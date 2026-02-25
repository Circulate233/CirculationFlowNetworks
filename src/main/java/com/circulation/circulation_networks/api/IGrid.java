package com.circulation.circulation_networks.api;

import com.circulation.circulation_networks.api.node.INode;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import net.minecraft.nbt.NBTTagCompound;

public interface IGrid {

    int getId();

    ReferenceSet<INode> getNodes();

    NBTTagCompound serialize();

    //TODO:等待实现中枢
    default void setCenter() {

    }
}
