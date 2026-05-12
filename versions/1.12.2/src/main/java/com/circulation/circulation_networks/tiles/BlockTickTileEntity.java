package com.circulation.circulation_networks.tiles;

import net.minecraft.util.ITickable;

public class BlockTickTileEntity extends BaseTileEntity implements ITickable {

    @Override
    public void update() {
        if (world == null) return;
        if (world.isRemote) {
            clientUpdate();
        } else {
            serverUpdate();
        }
    }

    public void serverUpdate() {

    }

    public void clientUpdate() {

    }

}
