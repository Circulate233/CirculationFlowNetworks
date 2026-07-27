package com.circulation.circulation_networks.inventory;

import com.circulation.circulation_networks.utils.NbtCompat;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;

@SuppressWarnings("unused")
public class CFNInternalInventory extends ItemStacksResourceHandler implements Iterable<ItemStack>, Container {

    protected final int[] maxStack;
    protected final ItemStack[] lastKnownStacks;
    @Nullable
    protected CFNInternalInventoryInputFilter inFilter;
    @Nullable
    protected CFNInternalInventoryOutputFilter outFilter;
    @Nullable
    protected CFNInternalInventoryHost host;
    protected boolean ignoreItemStackLimit;
    protected boolean dirtyFlag = false;

    public CFNInternalInventory(@Nullable CFNInternalInventoryHost host,
                                int size,
                                int maxStack,
                                boolean ignoreItemStackLimit) {
        super(size);
        this.host = host;
        this.ignoreItemStackLimit = ignoreItemStackLimit;
        this.maxStack = new int[size];
        this.lastKnownStacks = new ItemStack[size];
        Arrays.fill(this.maxStack, maxStack);
        Arrays.fill(this.lastKnownStacks, ItemStack.EMPTY);
        syncKnownStacks();
    }

    public CFNInternalInventory(@Nullable CFNInternalInventoryHost host, int size, int maxStack) {
        this(host, size, maxStack, false);
    }

    public CFNInternalInventory(@Nullable CFNInternalInventoryHost host, int size) {
        this(host, size, 64, false);
    }

    public CFNInternalInventory(@Nullable CFNInternalInventoryHost host, int size, boolean ignoreItemStackLimit) {
        this(host, size, 64, ignoreItemStackLimit);
    }

    private static ItemStack copyStackWithSize(ItemStack stack, int size) {
        return stack.copyWithCount(size);
    }

    private static boolean canItemStacksStack(ItemStack left, ItemStack right) {
        return ItemStack.isSameItemSameComponents(left, right);
    }

    private static boolean sameStackType(ItemStack left, ItemStack right) {
        return ItemStack.isSameItemSameComponents(left, right);
    }

    private void validateSlotIndex(int slot) {
        Objects.checkIndex(slot, getSlots());
    }

    private void syncKnownStacks() {
        for (int i = 0; i < getSlots(); i++) {
            lastKnownStacks[i] = getStackInSlot(i).copy();
        }
    }

    private void syncKnownStack(int slot) {
        lastKnownStacks[slot] = getStackInSlot(slot).copy();
    }

    private void notifyContentsChanged(int slot, ItemStack oldStack) {
        ItemStack newStack = getStackInSlot(slot).copy();
        if (ItemStack.matches(oldStack, newStack)) {
            syncKnownStack(slot);
            return;
        }

        if (host != null && !dirtyFlag) {
            dirtyFlag = true;
            ItemStack previous = oldStack.copy();
            ItemStack current = newStack.copy();
            CFNInventoryChangeOperation operation = CFNInventoryChangeOperation.SET;

            if (previous.isEmpty() && !current.isEmpty()) {
                operation = CFNInventoryChangeOperation.INSERT;
            } else if (!previous.isEmpty() && current.isEmpty()) {
                operation = CFNInventoryChangeOperation.EXTRACT;
            } else if (sameStackType(previous, current) && previous.getCount() != current.getCount()) {
                if (current.getCount() > previous.getCount()) {
                    ItemStack inserted = current.copy();
                    inserted.shrink(previous.getCount());
                    previous = ItemStack.EMPTY;
                    current = inserted;
                    operation = CFNInventoryChangeOperation.INSERT;
                } else {
                    ItemStack extracted = previous.copy();
                    extracted.shrink(current.getCount());
                    previous = extracted;
                    current = ItemStack.EMPTY;
                    operation = CFNInventoryChangeOperation.EXTRACT;
                }
            }

            host.onChangeInventory(this, slot, operation, previous, current);
            dirtyFlag = false;
        }

        syncKnownStack(slot);
    }

    public CFNInternalInventory setInputFilter(@Nullable CFNInternalInventoryInputFilter filter) {
        this.inFilter = filter;
        return this;
    }

    public CFNInternalInventory setOutputFilter(@Nullable CFNInternalInventoryOutputFilter filter) {
        this.outFilter = filter;
        return this;
    }

    public void setHost(@Nullable CFNInternalInventoryHost host) {
        this.host = host;
    }

    public boolean isIgnoringItemStackLimit() {
        return ignoreItemStackLimit;
    }

    public void setIgnoreItemStackLimit(boolean ignoreItemStackLimit) {
        this.ignoreItemStackLimit = ignoreItemStackLimit;
    }

    public int getSlots() {
        return size();
    }

    public @NotNull ItemStack getStackInSlot(int slot) {
        validateSlotIndex(slot);
        return stacks.get(slot);
    }

    public int getSlotLimit(int slot) {
        validateSlotIndex(slot);
        return maxStack[slot];
    }

    public void setMaxStackSize(int slot, int size) {
        validateSlotIndex(slot);
        this.maxStack[slot] = size;
    }

    public void setStackInSlot(int slot, @NotNull ItemStack stack) {
        validateSlotIndex(slot);
        ItemStack oldStack = getStackInSlot(slot).copy();
        ItemStack toSet = stack;
        if (!stack.isEmpty()) {
            int limit = getEffectiveStackLimit(slot, stack);
            if (stack.getCount() > limit) {
                toSet = copyStackWithSize(stack, limit);
            }
        }
        stacks.set(slot, toSet.isEmpty() ? ItemStack.EMPTY : toSet.copy());
        notifyContentsChanged(slot, oldStack);
    }

    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        validateSlotIndex(slot);
        if (maxStack[slot] == 0) {
            return false;
        }
        return inFilter == null || inFilter.allowInsert(this, slot, stack);
    }

    @Override
    public boolean canPlaceItem(int slot, @NotNull ItemStack stack) {
        return isItemValid(slot, stack);
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return !resource.isEmpty() && isItemValid(index, resource.toStack());
    }

    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (inFilter != null && !inFilter.allowInsert(this, slot, stack)) {
            return stack;
        }

        validateSlotIndex(slot);
        ItemStack oldStack = getStackInSlot(slot).copy();
        ItemStack existing = stacks.get(slot);
        int limit = getEffectiveStackLimit(slot, stack);

        if (!existing.isEmpty()) {
            if (!canItemStacksStack(stack, existing)) {
                return stack;
            }
            limit -= existing.getCount();
        }

        if (limit <= 0) {
            return stack;
        }

        boolean reachedLimit = stack.getCount() > limit;
        if (!simulate) {
            if (existing.isEmpty()) {
                stacks.set(slot, reachedLimit ? copyStackWithSize(stack, limit) : stack.copy());
            } else {
                existing.grow(reachedLimit ? limit : stack.getCount());
            }
            notifyContentsChanged(slot, oldStack);
        }

        return reachedLimit ? copyStackWithSize(stack, stack.getCount() - limit) : ItemStack.EMPTY;
    }

    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount == 0) {
            return ItemStack.EMPTY;
        }
        if (outFilter != null && !outFilter.allowExtract(this, slot, amount)) {
            return ItemStack.EMPTY;
        }

        validateSlotIndex(slot);
        ItemStack oldStack = getStackInSlot(slot).copy();
        ItemStack existing = stacks.get(slot);
        if (existing.isEmpty()) {
            return ItemStack.EMPTY;
        }

        int toExtract = Math.min(amount, existing.getCount());
        if (!ignoreItemStackLimit) {
            toExtract = Math.min(toExtract, existing.getMaxStackSize());
        }

        if (existing.getCount() <= toExtract) {
            if (!simulate) {
                stacks.set(slot, ItemStack.EMPTY);
                notifyContentsChanged(slot, oldStack);
            }
            return existing.copy();
        }

        ItemStack extracted = copyStackWithSize(existing, toExtract);
        if (!simulate) {
            stacks.set(slot, copyStackWithSize(existing, existing.getCount() - toExtract));
            notifyContentsChanged(slot, oldStack);
        }

        return extracted;
    }

    @Override
    protected void onContentsChanged(int slot, ItemStack previousContents) {
        notifyContentsChanged(slot, previousContents.copy());
    }

    public void writeToNBT(final CompoundTag data, final String name, HolderLookup.Provider provider) {
        ProblemReporter.Collector reporter = new ProblemReporter.Collector();
        TagValueOutput output = TagValueOutput.createWithContext(reporter, provider);
        serialize(output);
        data.put(name, output.buildResult());
    }

    public void readFromNBT(final CompoundTag data, final String name, HolderLookup.Provider provider) {
        if (data.contains(name)) {
            readFromNBT(NbtCompat.getCompoundOrEmpty(data, name), provider);
        }
    }

    public void readFromNBT(final CompoundTag data, HolderLookup.Provider provider) {
        deserialize(TagValueInput.create(new ProblemReporter.Collector(), provider, data));
        syncKnownStacks();
    }

    @Override
    public @NotNull Iterator<ItemStack> iterator() {
        return stacks.iterator();
    }

    private int getEffectiveStackLimit(int slot, @NotNull ItemStack stack) {
        return ignoreItemStackLimit ? getSlotLimit(slot) : Math.min(getSlotLimit(slot), stack.getMaxStackSize());
    }

    @Override
    public int getContainerSize() {
        return getSlots();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public @NotNull ItemStack getItem(int slot) {
        return getStackInSlot(slot);
    }

    @Override
    public @NotNull ItemStack removeItem(int slot, int amount) {
        return extractItem(slot, amount, false);
    }

    @Override
    public @NotNull ItemStack removeItemNoUpdate(int slot) {
        validateSlotIndex(slot);
        ItemStack existing = getStackInSlot(slot);
        if (existing.isEmpty()) {
            return ItemStack.EMPTY;
        }
        stacks.set(slot, ItemStack.EMPTY);
        lastKnownStacks[slot] = ItemStack.EMPTY;
        return existing;
    }

    @Override
    public void setItem(int slot, @NotNull ItemStack stack) {
        setStackInSlot(slot, stack);
    }

    @Override
    public void setChanged() {
        for (int i = 0; i < getSlots(); i++) {
            ItemStack oldStack = lastKnownStacks[i];
            if (!ItemStack.matches(oldStack, getStackInSlot(i))) {
                notifyContentsChanged(i, oldStack.copy());
            }
        }
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < getSlots(); i++) {
            setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    public interface CFNInternalInventoryInputFilter {
        boolean allowInsert(CFNInternalInventory inventory, int slot, ItemStack stack);
    }

    public interface CFNInternalInventoryOutputFilter {
        boolean allowExtract(CFNInternalInventory inventory, int slot, int amount);
    }
}
