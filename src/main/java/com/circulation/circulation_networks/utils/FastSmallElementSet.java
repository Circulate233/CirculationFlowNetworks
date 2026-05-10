package com.circulation.circulation_networks.utils;

import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;

/**
 * 对于元素极少的set，使用此特殊实现进行快速操作，从而减少无意义的遍历开销
 */
public class FastSmallElementSet<T> implements ReferenceSet<T> {

    private static final int INLINE_CAPACITY = 32;

    private T element0;
    private T element1;
    private T element2;
    private T element3;
    private T element4;
    private T element5;
    private T element6;
    private T element7;
    private T element8;
    private T element9;
    private T element10;
    private T element11;
    private T element12;
    private T element13;
    private T element14;
    private T element15;
    private T element16;
    private T element17;
    private T element18;
    private T element19;
    private T element20;
    private T element21;
    private T element22;
    private T element23;
    private T element24;
    private T element25;
    private T element26;
    private T element27;
    private T element28;
    private T element29;
    private T element30;
    private T element31;
    private int inlineSize;
    private int size;
    private ReferenceSet<T> overflowSet;

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public boolean contains(Object o) {
        return o != null && (inlineIndexOf(o) >= 0 || (overflowSet != null && overflowSet.contains(o)));
    }

    @Override
    public @NotNull ObjectIterator<T> iterator() {
        return new IteratorImpl();
    }

    @Deprecated
    public ObjectIterator<T> objectIterator() {
        return iterator();
    }

    @Override
    public void forEach(Consumer<? super T> action) {
        Objects.requireNonNull(action, "action");
        forEachInline(action);
        if (overflowSet != null) {
            overflowSet.forEach(action);
        }
    }

    @Override
    public @NotNull Object @NotNull [] toArray() {
        Object[] array = new Object[size];
        fillArray(array);
        return array;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T1> T1 @NotNull [] toArray(T1[] a) {
        if (a.length < size) {
            a = (T1[]) Array.newInstance(a.getClass().getComponentType(), size);
        }
        fillArray(a);
        return a;
    }

    @Override
    public <T1> T1[] toArray(@NonNull IntFunction<T1[]> generator) {
        return toArray(generator.apply(size));
    }

    @Override
    public boolean add(T t) {
        Objects.requireNonNull(t, "t");
        if (contains(t)) {
            return false;
        }
        if (inlineSize < INLINE_CAPACITY) {
            setInline(inlineSize++, t);
        } else {
            overflowSet().add(t);
        }
        size++;
        return true;
    }

    @Override
    public boolean remove(Object k) {
        if (k == null) {
            return false;
        }
        int inlineIndex = inlineIndexOf(k);
        if (inlineIndex >= 0) {
            removeInlineAt(inlineIndex);
            size--;
            return true;
        }
        if (overflowSet != null && overflowSet.remove(k)) {
            size--;
            clearOverflowIfEmpty();
            return true;
        }
        return false;
    }

    @Override
    public boolean containsAll(@NotNull Collection<?> c) {
        for (Object element : c) {
            if (!contains(element)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends T> c) {
        boolean changed = false;
        for (T element : c) {
            if (add(element)) {
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> c) {
        boolean changed = false;
        ObjectIterator<T> iterator = iterator();
        while (iterator.hasNext()) {
            if (containsReference(c, iterator.next())) {
                iterator.remove();
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public boolean removeIf(@NotNull Predicate<? super T> filter) {
        Objects.requireNonNull(filter, "filter");
        boolean changed = false;
        ObjectIterator<T> iterator = iterator();
        while (iterator.hasNext()) {
            if (filter.test(iterator.next())) {
                iterator.remove();
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> c) {
        boolean changed = false;
        ObjectIterator<T> iterator = iterator();
        while (iterator.hasNext()) {
            if (!containsReference(c, iterator.next())) {
                iterator.remove();
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public void clear() {
        clearInline();
        inlineSize = 0;
        size = 0;
        overflowSet = null;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        for (int i = 0; i < inlineSize; i++) {
            hash += System.identityHashCode(getInline(i));
        }
        if (overflowSet != null) {
            for (T element : overflowSet) {
                hash += System.identityHashCode(element);
            }
        }
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Collection<?> other)) {
            return false;
        }
        return other.size() == size && containsAll(other);
    }

    private void removeInlineAt(int index) {
        if (overflowSet != null && !overflowSet.isEmpty()) {
            ObjectIterator<T> iterator = overflowSet.iterator();
            T replacement = iterator.next();
            iterator.remove();
            setInline(index, replacement);
            clearOverflowIfEmpty();
            return;
        }
        int lastIndex = inlineSize - 1;
        if (index != lastIndex) {
            setInline(index, getInline(lastIndex));
        }
        setInline(lastIndex, null);
        inlineSize--;
    }

    private int inlineIndexOf(Object target) {
        for (int i = 0; i < inlineSize; i++) {
            if (getInline(i) == target) {
                return i;
            }
        }
        return -1;
    }

    private static boolean containsReference(Collection<?> collection, Object target) {
        for (Object element : collection) {
            if (element == target) {
                return true;
            }
        }
        return false;
    }

    private void forEachInline(Consumer<? super T> action) {
        if (inlineSize > 0) action.accept(element0);
        if (inlineSize > 1) action.accept(element1);
        if (inlineSize > 2) action.accept(element2);
        if (inlineSize > 3) action.accept(element3);
        if (inlineSize > 4) action.accept(element4);
        if (inlineSize > 5) action.accept(element5);
        if (inlineSize > 6) action.accept(element6);
        if (inlineSize > 7) action.accept(element7);
        if (inlineSize > 8) action.accept(element8);
        if (inlineSize > 9) action.accept(element9);
        if (inlineSize > 10) action.accept(element10);
        if (inlineSize > 11) action.accept(element11);
        if (inlineSize > 12) action.accept(element12);
        if (inlineSize > 13) action.accept(element13);
        if (inlineSize > 14) action.accept(element14);
        if (inlineSize > 15) action.accept(element15);
        if (inlineSize > 16) action.accept(element16);
        if (inlineSize > 17) action.accept(element17);
        if (inlineSize > 18) action.accept(element18);
        if (inlineSize > 19) action.accept(element19);
        if (inlineSize > 20) action.accept(element20);
        if (inlineSize > 21) action.accept(element21);
        if (inlineSize > 22) action.accept(element22);
        if (inlineSize > 23) action.accept(element23);
        if (inlineSize > 24) action.accept(element24);
        if (inlineSize > 25) action.accept(element25);
        if (inlineSize > 26) action.accept(element26);
        if (inlineSize > 27) action.accept(element27);
        if (inlineSize > 28) action.accept(element28);
        if (inlineSize > 29) action.accept(element29);
        if (inlineSize > 30) action.accept(element30);
        if (inlineSize > 31) action.accept(element31);
    }

    private void fillArray(Object[] array) {
        int index = 0;
        for (int i = 0; i < inlineSize; i++) {
            array[index++] = getInline(i);
        }
        if (overflowSet != null) {
            for (T element : overflowSet) {
                array[index++] = element;
            }
        }
    }

    private void clearInline() {
        element0 = null;
        element1 = null;
        element2 = null;
        element3 = null;
        element4 = null;
        element5 = null;
        element6 = null;
        element7 = null;
        element8 = null;
        element9 = null;
        element10 = null;
        element11 = null;
        element12 = null;
        element13 = null;
        element14 = null;
        element15 = null;
        element16 = null;
        element17 = null;
        element18 = null;
        element19 = null;
        element20 = null;
        element21 = null;
        element22 = null;
        element23 = null;
        element24 = null;
        element25 = null;
        element26 = null;
        element27 = null;
        element28 = null;
        element29 = null;
        element30 = null;
        element31 = null;
    }

    private T getInline(int index) {
        return switch (index) {
            case 0 -> element0;
            case 1 -> element1;
            case 2 -> element2;
            case 3 -> element3;
            case 4 -> element4;
            case 5 -> element5;
            case 6 -> element6;
            case 7 -> element7;
            case 8 -> element8;
            case 9 -> element9;
            case 10 -> element10;
            case 11 -> element11;
            case 12 -> element12;
            case 13 -> element13;
            case 14 -> element14;
            case 15 -> element15;
            case 16 -> element16;
            case 17 -> element17;
            case 18 -> element18;
            case 19 -> element19;
            case 20 -> element20;
            case 21 -> element21;
            case 22 -> element22;
            case 23 -> element23;
            case 24 -> element24;
            case 25 -> element25;
            case 26 -> element26;
            case 27 -> element27;
            case 28 -> element28;
            case 29 -> element29;
            case 30 -> element30;
            case 31 -> element31;
            default -> throw new IndexOutOfBoundsException(Integer.toString(index));
        };
    }

    private void setInline(int index, T value) {
        switch (index) {
            case 0 -> element0 = value;
            case 1 -> element1 = value;
            case 2 -> element2 = value;
            case 3 -> element3 = value;
            case 4 -> element4 = value;
            case 5 -> element5 = value;
            case 6 -> element6 = value;
            case 7 -> element7 = value;
            case 8 -> element8 = value;
            case 9 -> element9 = value;
            case 10 -> element10 = value;
            case 11 -> element11 = value;
            case 12 -> element12 = value;
            case 13 -> element13 = value;
            case 14 -> element14 = value;
            case 15 -> element15 = value;
            case 16 -> element16 = value;
            case 17 -> element17 = value;
            case 18 -> element18 = value;
            case 19 -> element19 = value;
            case 20 -> element20 = value;
            case 21 -> element21 = value;
            case 22 -> element22 = value;
            case 23 -> element23 = value;
            case 24 -> element24 = value;
            case 25 -> element25 = value;
            case 26 -> element26 = value;
            case 27 -> element27 = value;
            case 28 -> element28 = value;
            case 29 -> element29 = value;
            case 30 -> element30 = value;
            case 31 -> element31 = value;
            default -> throw new IndexOutOfBoundsException(Integer.toString(index));
        }
    }

    private ReferenceSet<T> overflowSet() {
        if (overflowSet == null) {
            overflowSet = new ReferenceOpenHashSet<>();
        }
        return overflowSet;
    }

    private void clearOverflowIfEmpty() {
        if (overflowSet != null && overflowSet.isEmpty()) {
            overflowSet = null;
        }
    }

    private final class IteratorImpl implements ObjectIterator<T> {
        private int nextInlineIndex;
        private ObjectIterator<T> overflowIterator;
        private T last;
        private boolean canRemove;
        private boolean lastFromOverflow;

        @Override
        public boolean hasNext() {
            if (nextInlineIndex < inlineSize) {
                return true;
            }
            return overflowSet != null && overflow().hasNext();
        }

        @Override
        public T next() {
            if (nextInlineIndex < inlineSize) {
                last = getInline(nextInlineIndex++);
                lastFromOverflow = false;
            } else {
                ObjectIterator<T> iterator = overflow();
                if (!iterator.hasNext()) {
                    throw new NoSuchElementException();
                }
                last = iterator.next();
                lastFromOverflow = true;
            }
            canRemove = true;
            return last;
        }

        @Override
        public void remove() {
            if (!canRemove) {
                throw new IllegalStateException();
            }
            if (lastFromOverflow) {
                overflow().remove();
                clearOverflowIfEmpty();
            } else {
                removeInlineAt(nextInlineIndex - 1);
                nextInlineIndex--;
            }
            size--;
            last = null;
            canRemove = false;
            lastFromOverflow = false;
        }

        @Override
        public int skip(int n) {
            if (n < 0) {
                throw new IllegalArgumentException("Argument must be nonnegative: " + n);
            }
            int skipped = 0;
            while (skipped < n && hasNext()) {
                next();
                skipped++;
            }
            return skipped;
        }

        private ObjectIterator<T> overflow() {
            if (overflowIterator == null) {
                overflowIterator = overflowSet().iterator();
            }
            return overflowIterator;
        }
    }
}
