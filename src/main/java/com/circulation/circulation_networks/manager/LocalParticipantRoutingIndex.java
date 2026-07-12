package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.api.IGrid;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;

import java.util.Objects;

/**
 * Dense, event-maintained registry of locally routable grids.
 * Removal swaps the final grid into the vacated slot, keeping indexed traversal
 * allocation-free while membership mutations remain cold-path operations.
 */
public final class LocalParticipantRoutingIndex {

    /** Shared server-local routing registry. */
    public static final LocalParticipantRoutingIndex INSTANCE = new LocalParticipantRoutingIndex();

    private final ObjectArrayList<IGrid> routingGrids = new ObjectArrayList<>();
    private final Reference2IntOpenHashMap<IGrid> positions = new Reference2IntOpenHashMap<>();

    private LocalParticipantRoutingIndex() {
        positions.defaultReturnValue(-1);
    }

    public void refresh(IGrid grid) {
        Objects.requireNonNull(grid, "grid");
        requireMutationAllowed(grid);
        boolean shouldRouteLocally = grid.getParticipantIndex().size() > 0
            && grid.getParticipantIndex().channelId() == null;
        int position = positions.getInt(grid);
        if (shouldRouteLocally) {
            if (position < 0) {
                positions.put(grid, routingGrids.size());
                routingGrids.add(grid);
            }
            return;
        }
        if (position >= 0) {
            removeAt(position);
        }
    }

    public void remove(IGrid grid) {
        Objects.requireNonNull(grid, "grid");
        requireMutationAllowed(grid);
        int position = positions.getInt(grid);
        if (position >= 0) {
            removeAt(position);
        }
    }

    public int routingGridCount() {
        return routingGrids.size();
    }

    public IGrid routingGridAt(int index) {
        return routingGrids.get(index);
    }

    public void onServerStop() {
        routingGrids.clear();
        positions.clear();
    }

    private void removeAt(int position) {
        int lastIndex = routingGrids.size() - 1;
        IGrid removed = routingGrids.get(position);
        IGrid last = routingGrids.remove(lastIndex);
        positions.removeInt(removed);
        if (position < lastIndex) {
            routingGrids.set(position, last);
            positions.put(last, position);
        }
    }

    private void requireMutationAllowed(IGrid grid) {
        if (grid.getParticipantIndex().isRoutingActive()) {
            throw new IllegalStateException("Cannot mutate local routing membership while the grid routing window is active");
        }
    }
}
