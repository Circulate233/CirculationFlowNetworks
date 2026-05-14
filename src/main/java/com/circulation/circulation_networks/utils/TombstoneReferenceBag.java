package com.circulation.circulation_networks.utils;

import org.jetbrains.annotations.NotNull;

import java.util.AbstractCollection;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Server tick hot path container backed by a sparse array plus occupancy bitset.
 * Elements are compared by reference identity and deletions leave tombstones
 * until a later compaction pass. Compaction only removes interior tombstones;
 * once grown, backing arrays keep their capacity and never shrink.
 *
 * <p>This container is intentionally single-threaded and only supports one
 * active iterator at a time. Hot paths should prefer the index-based alive-slot
 * APIs instead of {@link #iterator()}.</p>
 */
@SuppressWarnings("unchecked")
public final class TombstoneReferenceBag<T> extends AbstractCollection<T> {

    private static final int DEFAULT_CAPACITY = 16;
    private static final int MIN_COMPACT_USED = 64;
    private static final int COMPACT_CHECK_INTERVAL = 16;
    private static final int COMPACT_RATIO_NUMERATOR = 4;

    private Object[] elements;
    private long[] liveWords;
    private int[] freeStack;
    private int liveSize;
    private int usedSize;
    private int tombstoneCount;
    private int freeTop;
    private int removeCountSinceCompact;

    private final ReusableIterator<T> iterator = new ReusableIterator<>(this);

    public TombstoneReferenceBag() {
        this(DEFAULT_CAPACITY);
    }

    public TombstoneReferenceBag(int initialCapacity) {
        int capacity = normalizeInitialCapacity(initialCapacity);
        this.elements = new Object[capacity];
        this.liveWords = new long[wordCount(capacity)];
        this.freeStack = new int[capacity];
    }

    @Override
    public int size() {
        return liveSize;
    }

    @Override
    public boolean isEmpty() {
        return liveSize == 0;
    }

    @Override
    public boolean add(T value) {
        addDirect(value);
        return true;
    }

    public void addDirect(T value) {
        Objects.requireNonNull(value, "value");
        int index;
        if (freeTop > 0) {
            index = freeStack[--freeTop];
            tombstoneCount--;
        } else {
            ensureAppendCapacity();
            index = usedSize++;
        }
        elements[index] = value;
        setLive(index);
        liveSize++;
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends T> c) {
        Objects.requireNonNull(c, "collection");
        if (c.isEmpty()) {
            return false;
        }
        if (c instanceof TombstoneReferenceBag<?> bag) {
            TombstoneReferenceBag<? extends T> cast = (TombstoneReferenceBag<? extends T>) bag;
            addAllFast(cast);
            return true;
        }
        for (T element : c) {
            addDirect(element);
        }
        return true;
    }

    public void addAllFast(TombstoneReferenceBag<? extends T> other) {
        for (int index = other.firstAliveIndex(); index >= 0; index = other.nextAliveIndex(index)) {
            addDirect(other.elementAt(index));
        }
    }

    @Override
    public boolean contains(Object target) {
        if (target == null || liveSize == 0) {
            return false;
        }
        for (int index = firstAliveIndex(); index >= 0; index = nextAliveIndex(index)) {
            if (elements[index] == target) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean remove(Object target) {
        if (target == null || liveSize == 0) {
            return false;
        }
        for (int index = firstAliveIndex(); index >= 0; index = nextAliveIndex(index)) {
            if (elements[index] == target) {
                removeAt(index);
                compactIfNeeded();
                return true;
            }
        }
        return false;
    }

    @Override
    public void clear() {
        clearAndNullUsed();
    }

    public void clearFast() {
        clearWordsForUsedRange();
        liveSize = 0;
        usedSize = 0;
        tombstoneCount = 0;
        freeTop = 0;
        removeCountSinceCompact = 0;
    }

    public void clearAndNullUsed() {
        Arrays.fill(elements, 0, usedSize, null);
        clearWordsForUsedRange();
        liveSize = 0;
        usedSize = 0;
        tombstoneCount = 0;
        freeTop = 0;
        removeCountSinceCompact = 0;
    }

    public int firstAliveIndex() {
        return findAliveIndexAtOrAfter(0);
    }

    public int nextAliveIndex(int currentIndex) {
        return findAliveIndexAtOrAfter(currentIndex + 1);
    }

    public T elementAt(int index) {
        if (!isAlive(index)) {
            throw new IllegalStateException("No live element at index " + index);
        }
        return (T) elements[index];
    }

    public void removeAt(int index) {
        if (!isAlive(index)) {
            return;
        }
        // Hot paths may remove during alive-index traversal. Leave a tombstone
        // here and let compaction happen only after the traversal finishes.
        elements[index] = null;
        clearLive(index);
        freeStack[freeTop++] = index;
        liveSize--;
        tombstoneCount++;
        removeCountSinceCompact++;
    }

    public void compactIfNeeded() {
        if (!shouldCompact()) {
            return;
        }
        compact();
    }

    @Override
    public @NotNull Iterator<T> iterator() {
        iterator.reset();
        return iterator;
    }

    public int findAliveIndexAtOrAfter(int startIndex) {
        if (startIndex < 0) {
            startIndex = 0;
        }
        if (startIndex >= usedSize || liveSize == 0) {
            return -1;
        }
        int wordIndex = startIndex >>> 6;
        long word = liveWords[wordIndex] & (-1L << (startIndex & 63));
        int wordLimit = wordCount(usedSize);
        while (wordIndex < wordLimit) {
            if (word != 0L) {
                int elementIndex = (wordIndex << 6) + Long.numberOfTrailingZeros(word);
                return elementIndex < usedSize ? elementIndex : -1;
            }
            wordIndex++;
            if (wordIndex >= wordLimit) {
                break;
            }
            word = liveWords[wordIndex];
        }
        return -1;
    }

    private boolean isAlive(int index) {
        return index >= 0
            && index < usedSize
            && (liveWords[index >>> 6] & (1L << (index & 63))) != 0L;
    }

    private void ensureAppendCapacity() {
        if (usedSize < elements.length) {
            return;
        }
        grow(usedSize + 1);
    }

    private boolean shouldCompact() {
        return tombstoneCount > 0
            && usedSize >= MIN_COMPACT_USED
            && removeCountSinceCompact >= COMPACT_CHECK_INTERVAL
            && tombstoneCount * COMPACT_RATIO_NUMERATOR >= usedSize;
    }

    private void compact() {
        int oldUsedSize = usedSize;
        if (liveSize == 0) {
            clearAndNullUsed();
            return;
        }
        // Compact only rewrites the used prefix to eliminate tombstones.
        // Capacity stays unchanged so a grown bag never pays shrink/regrow churn.
        int writeIndex = 0;
        for (int readIndex = firstAliveIndex(); readIndex >= 0; readIndex = nextAliveIndex(readIndex)) {
            Object value = elements[readIndex];
            if (writeIndex != readIndex) {
                elements[writeIndex] = value;
                elements[readIndex] = null;
            }
            writeIndex++;
        }
        Arrays.fill(elements, writeIndex, oldUsedSize, null);
        clearWordsForUsedRange();
        usedSize = liveSize;
        for (int i = 0; i < usedSize; i++) {
            setLive(i);
        }
        tombstoneCount = 0;
        freeTop = 0;
        removeCountSinceCompact = 0;
    }

    private void grow(int minRequired) {
        int oldCapacity = elements.length;
        int newCapacity;
        if (oldCapacity < 64) {
            newCapacity = oldCapacity << 1;
        } else if (oldCapacity < 4096) {
            newCapacity = oldCapacity + (oldCapacity >> 1);
        } else {
            newCapacity = oldCapacity + (oldCapacity >> 2);
        }
        if (newCapacity < minRequired) {
            newCapacity = minRequired;
        }
        elements = Arrays.copyOf(elements, newCapacity);
        liveWords = Arrays.copyOf(liveWords, wordCount(newCapacity));
        freeStack = Arrays.copyOf(freeStack, newCapacity);
    }

    private void clearWordsForUsedRange() {
        Arrays.fill(liveWords, 0, wordCount(usedSize), 0L);
    }

    private void setLive(int index) {
        liveWords[index >>> 6] |= 1L << (index & 63);
    }

    private void clearLive(int index) {
        liveWords[index >>> 6] &= ~(1L << (index & 63));
    }

    private static int normalizeInitialCapacity(int initialCapacity) {
        return Math.max(DEFAULT_CAPACITY, initialCapacity);
    }

    private static int wordCount(int capacity) {
        return Math.max(1, (capacity + 63) >>> 6);
    }

    private static final class ReusableIterator<E> implements Iterator<E> {

        private final TombstoneReferenceBag<E> owner;

        private int nextIndex;
        private int lastReturnedIndex;
        private boolean canRemove;
        private boolean pendingCompact;
        private boolean compactedAfterExhaustion;

        private ReusableIterator(TombstoneReferenceBag<E> owner) {
            this.owner = owner;
        }

        private void reset() {
            nextIndex = owner.firstAliveIndex();
            lastReturnedIndex = -1;
            canRemove = false;
            pendingCompact = false;
            compactedAfterExhaustion = false;
        }

        @Override
        public boolean hasNext() {
            if (nextIndex < 0) {
                compactAfterExhaustion();
                return false;
            }
            return nextIndex >= 0;
        }

        @Override
        public E next() {
            if (nextIndex < 0) {
                throw new NoSuchElementException();
            }
            int currentIndex = nextIndex;
            lastReturnedIndex = currentIndex;
            nextIndex = owner.nextAliveIndex(currentIndex);
            canRemove = true;
            return owner.elementAt(currentIndex);
        }

        @Override
        public void remove() {
            if (!canRemove || lastReturnedIndex < 0) {
                throw new IllegalStateException();
            }
            owner.removeAt(lastReturnedIndex);
            nextIndex = owner.findAliveIndexAtOrAfter(lastReturnedIndex);
            lastReturnedIndex = -1;
            canRemove = false;
            pendingCompact = true;
            compactedAfterExhaustion = false;
        }

        /**
         * Delay compaction until the iterator is fully exhausted so live-slot
         * indexes stay stable for the whole traversal.
         */
        private void compactAfterExhaustion() {
            if (!pendingCompact || compactedAfterExhaustion) {
                return;
            }
            owner.compactIfNeeded();
            pendingCompact = false;
            compactedAfterExhaustion = true;
        }
    }
}
