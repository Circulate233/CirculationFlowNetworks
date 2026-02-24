package com.circulation.circulation_networks.tiles;

import com.circulation.circulation_networks.container.CFNBaseContainer;
import com.circulation.circulation_networks.container.EmptyContainer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;

public class BaseTileEntity extends TileEntity {

    public boolean hasGui() {
        return false;
    }

    @Nonnull
    public CFNBaseContainer getContainer(EntityPlayer player) {
        return EmptyContainer.INSTANCE;
    }

    @SideOnly(Side.CLIENT)
    public GuiContainer getGui(EntityPlayer player) {
        return null;
    }

}
