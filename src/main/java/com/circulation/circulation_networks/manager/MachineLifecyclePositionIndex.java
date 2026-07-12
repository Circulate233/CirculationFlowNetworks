package com.circulation.circulation_networks.manager;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.Objects;

/** Dense lifecycle state for machine positions. */
final class MachineLifecyclePositionIndex<T> {

    private final Int2ObjectOpenHashMap<Long2ObjectOpenHashMap<State<T>>> dimensions =
        new Int2ObjectOpenHashMap<>();
    private long generation;

    public long submit(int dimensionId, long packedPosition, T identity, Action action) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(action, "action");
        Long2ObjectOpenHashMap<State<T>> positions = dimensions.get(dimensionId);
        State<T> state = positions == null ? null : positions.get(packedPosition);
        if (action == Action.INVALIDATE
            && (state == null || state.identity != identity || state.phase == Phase.INVALIDATING)) {
            return -1L;
        }
        if (action == Action.DISCOVERED && state != null && state.identity == identity
            && state.phase == Phase.APPLIED) {
            return -1L;
        }
        if (state != null && state.identity == identity && state.phase == Phase.PENDING
            && state.action == action) {
            return -1L;
        }
        if (generation == Long.MAX_VALUE) {
            throw new IllegalStateException("Block entity lifecycle generation exhausted");
        }
        if (positions == null) {
            positions = new Long2ObjectOpenHashMap<>();
            dimensions.put(dimensionId, positions);
        }
        if (state == null) {
            state = new State<>();
            positions.put(packedPosition, state);
        }
        state.identity = identity;
        state.generation = ++generation;
        state.action = action;
        state.phase = action == Action.INVALIDATE ? Phase.INVALIDATING : Phase.PENDING;
        return state.generation;
    }

    public boolean isCurrent(int dimensionId,
                             long packedPosition,
                             T identity,
                             long generation,
                             Action action) {
        State<T> state = state(dimensionId, packedPosition);
        return state != null
            && state.identity == identity
            && state.generation == generation
            && state.action == action
            && state.phase == (action == Action.INVALIDATE ? Phase.INVALIDATING : Phase.PENDING);
    }

    public void markApplied(int dimensionId, long packedPosition, T identity, long generation, Action action) {
        if (action == Action.INVALIDATE || !isCurrent(dimensionId, packedPosition, identity, generation, action)) {
            throw new IllegalStateException("Cannot apply a lifecycle state that is not current");
        }
        State<T> current = Objects.requireNonNull(
            state(dimensionId, packedPosition), "Current lifecycle state disappeared before apply"
        );
        current.phase = Phase.APPLIED;
    }

    public void markDeferred(int dimensionId, long packedPosition, T identity, long generation, Action action) {
        if (!isCurrent(dimensionId, packedPosition, identity, generation, action)) {
            return;
        }
        State<T> current = Objects.requireNonNull(
            state(dimensionId, packedPosition), "Current lifecycle state disappeared before defer"
        );
        current.phase = Phase.DEFERRED;
    }

    public void removeInvalidated(int dimensionId, long packedPosition, T identity, long generation) {
        if (!isCurrent(dimensionId, packedPosition, identity, generation, Action.INVALIDATE)) {
            throw new IllegalStateException("Cannot remove a lifecycle state that is not the current invalidation");
        }
        Long2ObjectOpenHashMap<State<T>> positions = Objects.requireNonNull(
            dimensions.get(dimensionId), "Invalidation dimension disappeared before removal"
        );
        positions.remove(packedPosition);
        if (positions.isEmpty()) {
            dimensions.remove(dimensionId);
        }
    }

    public void releasePosition(int dimensionId, long packedPosition) {
        removeState(dimensionId, packedPosition);
    }

    public void releaseCurrent(int dimensionId,
                               long packedPosition,
                               T identity,
                               long generation,
                               Action action) {
        if (!isCurrent(dimensionId, packedPosition, identity, generation, action)) {
            return;
        }
        removeState(dimensionId, packedPosition);
    }

    public int size() {
        int size = 0;
        for (Long2ObjectOpenHashMap<State<T>> positions : dimensions.values()) {
            size += positions.size();
        }
        return size;
    }

    public void clear() {
        dimensions.clear();
        generation = 0L;
    }

    private State<T> state(int dimensionId, long packedPosition) {
        Long2ObjectOpenHashMap<State<T>> positions = dimensions.get(dimensionId);
        return positions == null ? null : positions.get(packedPosition);
    }

    private void removeState(int dimensionId, long packedPosition) {
        Long2ObjectOpenHashMap<State<T>> positions = dimensions.get(dimensionId);
        if (positions == null) {
            return;
        }
        positions.remove(packedPosition);
        if (positions.isEmpty()) {
            dimensions.remove(dimensionId);
        }
    }

    enum Action {
        DISCOVERED,
        READY,
        MANAGER_UNAVAILABLE,
        INVALIDATE
    }

    private enum Phase {
        PENDING,
        APPLIED,
        DEFERRED,
        INVALIDATING
    }

    private static final class State<T> {
        private T identity;
        private long generation;
        private Action action;
        private Phase phase;
    }
}
