package com.circulation.circulation_networks.utils;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public final class ObjectPool<T> {

    private final Supplier<T> factory;
    private final Consumer<T> resetter;
    private final int maxSize;
    private T[] stack;
    private int top;

    public ObjectPool(Supplier<T> factory, Consumer<T> resetter, int maxSize, IntFunction<T[]> generator) {
        if (maxSize < 0) {
            throw new IllegalArgumentException("ObjectPool maxSize cannot be negative");
        }
        this.factory = Objects.requireNonNull(factory, "ObjectPool factory cannot be null");
        this.resetter = Objects.requireNonNull(resetter, "ObjectPool resetter cannot be null");
        this.maxSize = maxSize;
        int initial = Math.min(maxSize == 0 ? 1 : maxSize, 64);
        this.stack = generator.apply(initial);
    }

    public T obtain() {
        int t = top;
        if (t == 0) {
            return factory.get();
        }
        t--;
        T value = stack[t];
        stack[t] = null; // release the reference so a transiently large pool does not pin objects
        top = t;
        return value;
    }

    public void recycle(T value) {
        if (value == null) {
            throw new IllegalArgumentException("ObjectPool cannot recycle null");
        }
        resetter.accept(value);
        int t = top;
        if (t >= maxSize) {
            return;
        }
        if (t >= stack.length) {
            int newLen = Math.min(maxSize, Math.max(stack.length << 1, t + 1));
            stack = Arrays.copyOf(stack, newLen);
        }
        stack[t] = value;
        top = t + 1;
    }

    public void clear() {
        Arrays.fill(stack, 0, top, null);
        top = 0;
    }

    public int size() {
        return top;
    }
}
