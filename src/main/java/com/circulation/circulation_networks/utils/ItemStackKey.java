package com.circulation.circulation_networks.utils;

import lombok.ToString;
import net.minecraft.item.ItemStack;

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@ToString
public final class ItemStackKey {

    public static final ItemStackKey EMPTY = new ItemStackKey(ItemStack.EMPTY);
    private static final Queue<ItemStackKey> POOL = new ConcurrentLinkedQueue<>();
    private ItemStack key;
    private int hashCode;

    private ItemStackKey(ItemStack key) {
        set(key);

    }

    public static ItemStackKey get(ItemStack key) {
        if (POOL.isEmpty()) {
            return new ItemStackKey(key);
        }
        return POOL.poll().set(key);
    }

    public boolean isEmpty() {
        return key.isEmpty();
    }

    private ItemStackKey set(ItemStack key) {
        this.key = key;
        this.hashCode = Objects.hash(key.getItem(), key.getTagCompound());
        return this;
    }

    public void recycle() {
        this.key = null;
        this.hashCode = 0;
        if (POOL.size() < 100) {
            POOL.add(this);
        }
    }

    public long getCount() {
        return key.getCount();
    }

    public ItemStack getItemStack() {
        if (key.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return key.copy();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj.getClass() != ItemStackKey.class) {
            return false;
        }
        ItemStackKey other = (ItemStackKey) obj;
        return this.equals(other.key);
    }

    public boolean equals(ItemStack key) {
        return this.key.getItem() == key.getItem()
            && (this.key.getMetadata() == key.getMetadata() || this.key.getMetadata() == 32767 || key.getMetadata() == 32767)
            && Objects.equals(this.key.getTagCompound(), key.getTagCompound());
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
}
