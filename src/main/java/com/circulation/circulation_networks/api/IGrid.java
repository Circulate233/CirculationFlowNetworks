package com.circulation.circulation_networks.api;

import com.circulation.circulation_networks.api.node.INode;
import it.unimi.dsi.fastutil.objects.ReferenceSet;

public interface IGrid {

    int getId();

    ReferenceSet<INode> getNodes();

    //TODO:等待实现中枢
    default void setCenter() {

    }
}
