package com.circulation.circulation_networks.container;

import net.minecraft.entity.player.EntityPlayer;
import org.jetbrains.annotations.NotNull;

public final class EmptyContainer extends CFNBaseContainer {

    public static final EmptyContainer INSTANCE = new EmptyContainer();

    private EmptyContainer() {
        super(null, null);
    }

    public boolean canInteractWith(@NotNull EntityPlayer entityplayer) {
        return false;
    }

    public void detectAndSendChanges() {

    }
}