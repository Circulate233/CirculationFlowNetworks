package com.circulation.circulation_networks.container;

import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
//? if <1.20 {
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
//?} else if <1.21 {
/*import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
*///?} else {
/*import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
*///?}

import java.util.List;
import java.util.function.Consumer;

/**
 * Shared descriptor that binds a set of inventory slots to a GUI component.
 */
@SuppressWarnings("unused")
public class ComponentSlotLayout {

    private final ObjectList<SlotFactory> specs = new ObjectArrayList<>();
    private final ObjectList<ComponentSlot> prebuilt = new ObjectArrayList<>();
    private final ObjectList<ComponentSlot> slots = new ObjectArrayList<>();
    private boolean registered = false;

    //~ if >=1.20 '(InventoryPlayer ' -> '(Inventory ' {
    public static ComponentSlotLayout playerInventory(InventoryPlayer inventoryPlayer) {
        //~}
        ComponentSlotLayout layout = new ComponentSlotLayout();
        IItemHandler inventory = new InvWrapper(inventoryPlayer);
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) {
                layout.addSlot(inventory, j + i * 9 + 9, 2 + j * 18, 2 + i * 18);
            }
        }
        for (int k = 0; k < 9; k++) {
            layout.addSlot(inventory, k, 2 + k * 18, 60);
        }
        return layout;
    }

    public ComponentSlotLayout build(CFNBaseContainer container) {
        container.registerPlayerLayout(this);
        return this;
    }

    //? if <1.20 {
    public ComponentSlotLayout addSlot(IInventory inventory, int index, int relX, int relY) {
        if (registered) {
            throw new IllegalStateException("Cannot add slot specs after registerInto() has been called");
        }
        specs.add(() -> new ComponentSlot(inventory, index, relX, relY));
        return this;
    }
    //?}

    public ComponentSlotLayout addSlot(IItemHandler inventory, int index, int relX, int relY) {
        if (registered) {
            throw new IllegalStateException("Cannot add slot specs after registerInto() has been called");
        }
        specs.add(() -> new ComponentSlot(inventory, index, relX, relY));
        return this;
    }

    public ComponentSlotLayout addPrebuilt(ComponentSlot slot) {
        if (registered) {
            throw new IllegalStateException("Cannot add slots after registerInto() has been called");
        }
        prebuilt.add(slot);
        return this;
    }

    //? if <1.20 {
    public ComponentSlotLayout addOutput(IInventory inventory, int index, int relX, int relY) {
        return addPrebuilt(new OutputComponentSlot(inventory, index, relX, relY));
    }
    //?}

    public ComponentSlotLayout addOutput(IItemHandler inventory, int index, int relX, int relY) {
        return addPrebuilt(new OutputComponentSlot(inventory, index, relX, relY));
    }

    //? if <1.20 {
    public ComponentSlotLayout addFilter(IInventory inventory, int index, int relX, int relY, int maxCount) {
        return addPrebuilt(new FilterComponentSlot(inventory, index, relX, relY, maxCount));
    }
    //?}

    public ComponentSlotLayout addFilter(IItemHandler inventory, int index, int relX, int relY, int maxCount) {
        return addPrebuilt(new FilterComponentSlot(inventory, index, relX, relY, maxCount));
    }

    public void registerInto(Consumer<ComponentSlot> adder) {
        if (registered) return;
        registered = true;
        for (SlotFactory factory : specs) {
            ComponentSlot slot = factory.create();
            adder.accept(slot);
            slots.add(slot);
        }
        for (ComponentSlot slot : prebuilt) {
            adder.accept(slot);
            slots.add(slot);
        }
    }

    public void syncPositions(int absX, int absY, boolean visible) {
        for (ComponentSlot slot : slots) {
            //~ if >=1.20 '.xPos = ' -> '.x = ' {
            //~ if >=1.20 '.yPos = ' -> '.y = ' {
            slot.xPos = absX + slot.getRelX();
            slot.yPos = absY + slot.getRelY();
            //~}
            //~}
            slot.setVisible(visible);
        }
    }

    public List<ComponentSlot> getSlots() {
        return ObjectLists.unmodifiable(slots);
    }

    @FunctionalInterface
    private interface SlotFactory {
        ComponentSlot create();
    }
}
