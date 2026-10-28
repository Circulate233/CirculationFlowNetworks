package com.circulation.circulation_networks.container;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.function.Consumer;

@SuppressWarnings("unused")
public class ComponentSlotLayout {

    private final ObjectList<SlotFactory> specs = new ObjectArrayList<>();
    private final ObjectList<ComponentSlot> prebuilt = new ObjectArrayList<>();
    private final ObjectList<ComponentSlot> slots = new ObjectArrayList<>();
    private boolean registered = false;

    public static ComponentSlotLayout playerInventory(Inventory inventoryPlayer) {
        ComponentSlotLayout layout = new ComponentSlotLayout();
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) {
                layout.addSlot(inventoryPlayer, j + i * 9 + 9, 2 + j * 18, 2 + i * 18);
            }
        }
        for (int k = 0; k < 9; k++) {
            layout.addSlot(inventoryPlayer, k, 2 + k * 18, 60);
        }
        return layout;
    }

    public ComponentSlotLayout build(CFNBaseContainer container) {
        container.registerPlayerLayout(this);
        return this;
    }

    public ComponentSlotLayout addSlot(Container inventory, int index, int relX, int relY) {
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

    public ComponentSlotLayout addOutput(Container inventory, int index, int relX, int relY) {
        return addPrebuilt(new OutputComponentSlot(inventory, index, relX, relY));
    }

    public ComponentSlotLayout addFilter(Container inventory, int index, int relX, int relY, int maxCount) {
        return addPrebuilt(new FilterComponentSlot(inventory, index, relX, relY, maxCount));
    }

    public void registerInto(Consumer<ComponentSlot> adder) {
        if (registered) {
            return;
        }
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
            slot.x = absX + slot.getRelX();
            slot.y = absY + slot.getRelY();
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
