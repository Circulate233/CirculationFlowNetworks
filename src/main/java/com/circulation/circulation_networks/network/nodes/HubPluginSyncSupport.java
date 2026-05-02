package com.circulation.circulation_networks.network.nodes;

//? if <1.20 {
import net.minecraft.item.ItemStack;
//?} else {
/*import net.minecraft.world.item.ItemStack;
*///?}

public final class HubPluginSyncSupport {

    private HubPluginSyncSupport() {
    }

    public static boolean shouldRequestInitialSync(boolean clientSide, boolean alreadyRequested) {
        return clientSide && !alreadyRequested;
    }

    public static ItemStack[] snapshotPlugins(Iterable<ItemStack> plugins, int slotCount) {
        ItemStack[] snapshot = new ItemStack[slotCount];
        int index = 0;
        for (ItemStack stack : plugins) {
            if (index >= slotCount) {
                break;
            }
            snapshot[index++] = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        }
        while (index < slotCount) {
            snapshot[index++] = ItemStack.EMPTY;
        }
        return snapshot;
    }
}
