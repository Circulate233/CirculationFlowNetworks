package com.circulation.circulation_networks.api;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public interface ClientTickMachine {

    @SideOnly(Side.CLIENT)
    void clientUpdate();

}
