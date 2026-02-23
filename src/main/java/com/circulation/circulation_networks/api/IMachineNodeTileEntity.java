package com.circulation.circulation_networks.api;

import com.circulation.circulation_networks.api.node.IMachineNode;
import com.circulation.circulation_networks.energy.handler.CEHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;

public interface IMachineNodeTileEntity extends INodeTileEntity {

    default void serverUpdate() {

    }

    @SideOnly(Side.CLIENT)
    default void clientUpdate() {

    }

    @Nonnull
    IMachineNode getNode();

    @Nonnull
    CEHandler getCEHandler();
}
