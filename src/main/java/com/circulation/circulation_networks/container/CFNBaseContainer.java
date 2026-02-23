package com.circulation.circulation_networks.container;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.utils.GuiSync;
import com.circulation.circulation_networks.utils.SyncData;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.Slot;
import net.minecraft.tileentity.TileEntity;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;

public abstract class CFNBaseContainer extends Container {

    protected final TileEntity te;
    protected final EntityPlayer player;
    private final Int2ObjectMap<SyncData> syncData = new Int2ObjectOpenHashMap<>();

    {
        for (Field f : this.getClass().getFields()) {
            if (f.isAnnotationPresent(GuiSync.class)) {
                GuiSync annotation = f.getAnnotation(GuiSync.class);
                if (this.syncData.containsKey(annotation.value())) {
                    CirculationFlowNetworks.LOGGER.warn("Channel already in use: {} for {}", annotation.value(), f.getName());
                } else {
                    this.syncData.put(annotation.value(), new SyncData(this, f, annotation));
                }
            }
        }
    }

    public CFNBaseContainer(EntityPlayer player, TileEntity te) {
        this.te = te;
        this.player = player;
    }

    protected void bindPlayerInventory(InventoryPlayer inventoryPlayer, int offsetX, int offsetY) {
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlotToContainer(new Slot(inventoryPlayer, j + i * 9 + 9, 8 + j * 18 + offsetX, offsetY + i * 18));
            }
        }

        for (int k = 0; k < 9; ++k) {
            this.addSlotToContainer(new Slot(inventoryPlayer, k, 8 + k * 18 + offsetX, offsetY + 58));
        }
    }

    @Override
    public boolean canInteractWith(@NotNull EntityPlayer playerIn) {
        return true;
    }

    public void detectAndSendChanges() {
        if (isServer()) {
            for (IContainerListener listener : this.listeners) {
                for (SyncData sd : this.syncData.values()) {
                    sd.tick(listener);
                }
            }
        }

        super.detectAndSendChanges();
    }

    protected final boolean isServer() {
        return !te.getWorld().isRemote;
    }

    public final void updateFullProgressBar(int idx, long value) {
        if (this.syncData.containsKey(idx)) {
            this.syncData.get(idx).update(value);
        } else {
            this.updateProgressBar(idx, (int) value);
        }
    }

    public final void stringSync(int idx, String value) {
        if (this.syncData.containsKey(idx)) {
            this.syncData.get(idx).update(value);
        }

    }

    public final void updateProgressBar(int idx, int value) {
        if (this.syncData.containsKey(idx)) {
            this.syncData.get(idx).update((long) value);
        }
    }


}
