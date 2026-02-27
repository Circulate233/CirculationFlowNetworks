package com.circulation.circulation_networks.events;

import com.circulation.circulation_networks.api.node.INode;
import lombok.Getter;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.eventhandler.Cancelable;

public class AddNodeEvent extends NodeEvent {

    @Getter
    private final TileEntity tileEntity;

    public AddNodeEvent(INode node, TileEntity tile) {
        super(node);
        this.tileEntity = tile;
    }

    @Cancelable
    public static class Pre extends AddNodeEvent {

        public Pre(INode node, TileEntity tile) {
            super(node, tile);
        }

        @Override
        public boolean isCancelable() {
            return true;
        }
    }

    public static class Post extends AddNodeEvent {

        public Post(INode node, TileEntity tile) {
            super(node, tile);
        }
    }
}
