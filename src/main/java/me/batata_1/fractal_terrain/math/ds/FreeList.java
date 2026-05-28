package me.batata_1.fractal_terrain.math.ds;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Slot-stable pool: insert() returns a persistent index, remove() recycles that slot.
 * O(1) amortised insert, O(1) remove, O(n) iteration over occupied slots only.
 */
public class FreeList<T> implements Iterable<T> {

    private static final int NONE = -1;
    private static final int DEFAULT_CAPACITY = 16;

    private Object[] data;
    private int[]    nextFree;   // free-chain; NONE when slot is occupied
    private boolean[] occupied;
    private int freeHead;        // first free slot, or NONE when full
    private int size;
    private int capacity;

    public FreeList() {
        this(DEFAULT_CAPACITY);
    }

    public FreeList(int initialCapacity) {
        capacity = Math.max(initialCapacity, 1);
        data     = new Object[capacity];
        nextFree = new int[capacity];
        occupied = new boolean[capacity];
        buildFreeChain(0, capacity);
        freeHead = 0;
    }

    // -------------------------------------------------------------------------
    // Core operations
    // -------------------------------------------------------------------------

    /** Inserts {@code item} into the next free slot and returns its stable index. */
    public int insert(T item) {
        if (freeHead == NONE) grow();
        int idx  = freeHead;
        freeHead = nextFree[idx];
        data[idx]     = item;
        occupied[idx] = true;
        nextFree[idx] = NONE;
        size++;
        return idx;
    }

    /** Removes the element at {@code index}. The slot is immediately available for reuse. */
    public void remove(int index) {
        requireOccupied(index);
        data[index]     = null;
        occupied[index] = false;
        nextFree[index] = freeHead;
        freeHead        = index;
        size--;
    }

    /** Returns the element at {@code index}. */
    @SuppressWarnings("unchecked")
    public T get(int index) {
        requireOccupied(index);
        return (T) data[index];
    }

    /** Replaces the element at an already-occupied {@code index}. */
    public void set(int index, T item) {
        requireOccupied(index);
        data[index] = item;
    }

    // -------------------------------------------------------------------------
    // Query
    // -------------------------------------------------------------------------

    public boolean isOccupied(int index) {
        return index >= 0 && index < capacity && occupied[index];
    }

    /** Number of occupied slots. */
    public int size() { return size; }

    /** Total allocated capacity including free slots. */
    public int capacity() { return capacity; }

    public boolean isEmpty() { return size == 0; }

    // -------------------------------------------------------------------------
    // Bulk operations
    // -------------------------------------------------------------------------

    /** Removes all elements, resetting to a fully-free state. */
    public void clear() {
        Arrays.fill(data, null);
        Arrays.fill(occupied, false);
        buildFreeChain(0, capacity);
        freeHead = 0;
        size     = 0;
    }

    // -------------------------------------------------------------------------
    // Iteration  (visits only occupied slots, in ascending index order)
    // -------------------------------------------------------------------------

    @Override
    public @NotNull Iterator<T> iterator() {
        return new OccupiedIterator();
    }

    private final class OccupiedIterator implements Iterator<T> {
        private int cursor = firstOccupied(0);

        @Override
        public boolean hasNext() { return cursor < capacity; }

        @Override
        @SuppressWarnings("unchecked")
        public T next() {
            if (!hasNext()) throw new NoSuchElementException();
            T value = (T) data[cursor];
            cursor  = firstOccupied(cursor + 1);
            return value;
        }
    }

    /**
     * Returns an {@link IndexedEntry} iterator that exposes both value and index,
     * useful when callers need to call {@link #remove(int)} while iterating.
     */
    public Iterable<IndexedEntry<T>> entries() {
        return () -> new Iterator<>() {
            private int cursor = firstOccupied(0);

            @Override
            public boolean hasNext() { return cursor < capacity; }

            @Override
            @SuppressWarnings("unchecked")
            public IndexedEntry<T> next() {
                if (!hasNext()) throw new NoSuchElementException();
                IndexedEntry<T> entry = new IndexedEntry<>(cursor, (T) data[cursor]);
                cursor = firstOccupied(cursor + 1);
                return entry;
            }
        };
    }

    public record IndexedEntry<T>(int index, T value) {}

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private int firstOccupied(int from) {
        while (from < capacity && !occupied[from]) from++;
        return from;
    }

    private void grow() {
        int newCap = capacity * 2;
        data     = Arrays.copyOf(data,     newCap);
        nextFree = Arrays.copyOf(nextFree, newCap);
        occupied = Arrays.copyOf(occupied, newCap);
        buildFreeChain(capacity, newCap);
        freeHead = capacity;
        capacity = newCap;
    }

    /** Links slots [from, to) into a free chain ending with NONE. */
    private void buildFreeChain(int from, int to) {
        for (int i = from; i < to - 1; i++) nextFree[i] = i + 1;
        if (to > from) nextFree[to - 1] = NONE;
    }

    private void requireOccupied(int index) {
        if (index < 0 || index >= capacity)
            throw new IndexOutOfBoundsException("index " + index + " out of range [0, " + capacity + ")");
        if (!occupied[index])
            throw new IllegalArgumentException("slot " + index + " is not occupied");
    }
}