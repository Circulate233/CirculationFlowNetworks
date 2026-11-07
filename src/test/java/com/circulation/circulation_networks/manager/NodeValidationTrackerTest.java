package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.utils.ChunkCoordUtils;
//~ mc_imports
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NodeValidationTrackerTest {
    @Test
    void validatingTheOverworldDoesNotInvalidateOtherDimensionIds() {
        // Fastutil 7 shrinks the default table when dimension zero finishes validation.
        assertAllDimensionsValidated(new int[] {0, -1, 1, 2});
    }

    @Test
    void collidingDimensionsRemainAvailableAfterTheFirstDimensionIsValidated() {
        // These IDs share slot 31 in the default table, so removal shifts the second entry.
        assertAllDimensionsValidated(new int[] {33, 66});
    }

    @Test
    void allDimensionsAreValidatedWhenRemovingThemShrinksThePendingTable() {
        int[] dimensions = new int[128];
        for (int i = 0; i < dimensions.length; i++) {
            dimensions[i] = i;
        }
        assertAllDimensionsValidated(dimensions);
    }

    private static void assertAllDimensionsValidated(int[] dimensions) {
        NetworkManager.NodeValidationTracker tracker = new NetworkManager.NodeValidationTracker();
        BlockPos pos = new BlockPos(0, 64, 0);
        long chunk = ChunkCoordUtils.mergeChunkCoords(pos);
        Set<Integer> remaining = new HashSet<>();
        for (int dimension : dimensions) {
            tracker.markPending(dimension, pos);
            remaining.add(dimension);
        }

        for (int dimension : tracker.pendingDimensionsSnapshot()) {
            assertTrue(remaining.remove(dimension), "Each original dimension must be validated exactly once");
            assertNotNull(tracker.getChunkPositions(dimension, chunk));
            tracker.removePending(dimension, pos);
        }
        assertTrue(remaining.isEmpty());
        assertTrue(tracker.isPendingEmpty());
    }

    @Test
    void chunkSnapshotSurvivesRemovingPendingPositionsDuringValidation() {
        NetworkManager.NodeValidationTracker tracker = new NetworkManager.NodeValidationTracker();
        int dimension = 0;
        BlockPos first = new BlockPos(0, 64, 0);
        BlockPos second = new BlockPos(32, 64, 0);
        tracker.markPending(dimension, first);
        tracker.markPending(dimension, second);
        var chunks = tracker.pendingChunksSnapshot(dimension);

        tracker.removePending(dimension, first);
        tracker.removePending(dimension, second);

        assertEquals(2, chunks.size());
        assertTrue(chunks.contains(ChunkCoordUtils.mergeChunkCoords(first)));
        assertTrue(chunks.contains(ChunkCoordUtils.mergeChunkCoords(second)));
        assertTrue(tracker.pendingChunksSnapshot(dimension).isEmpty());
        assertTrue(tracker.isPendingEmpty());
    }

    @Test
    void aDimensionRemovedBeforeItsTurnHasNoPendingChunks() {
        NetworkManager.NodeValidationTracker tracker = new NetworkManager.NodeValidationTracker();
        int dimension = 0;
        BlockPos pos = new BlockPos(0, 64, 0);
        tracker.markPending(dimension, pos);
        int[] dimensions = tracker.pendingDimensionsSnapshot();
        tracker.removePending(dimension, pos);

        assertArrayEquals(new int[] {dimension}, dimensions);
        assertTrue(tracker.pendingChunksSnapshot(dimensions[0]).isEmpty());
    }
}
