package com.circulation.circulation_networks.events;

import com.circulation.circulation_networks.api.node.INode;
import lombok.Getter;
import net.minecraftforge.fml.common.eventhandler.Event;

public abstract class NodeEvent extends Event {

    @Getter
    private final INode node;

    public NodeEvent(INode node) {
        this.node = node;
    }

}
