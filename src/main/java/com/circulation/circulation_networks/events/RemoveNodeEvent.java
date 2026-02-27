package com.circulation.circulation_networks.events;

import com.circulation.circulation_networks.api.node.INode;

public class RemoveNodeEvent extends NodeEvent {

    public RemoveNodeEvent(INode node) {
        super(node);
    }

    public static class Pre extends RemoveNodeEvent {
        public Pre(INode node) {
            super(node);
        }
    }

    public static class Post extends RemoveNodeEvent {
        public Post(INode node) {
            super(node);
        }
    }
}
