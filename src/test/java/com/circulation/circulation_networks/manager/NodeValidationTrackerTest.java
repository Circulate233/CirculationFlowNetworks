package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.utils.Functions;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NodeValidationTrackerTest {
    @Test
    void collidingDimensionsRemainAvailableAfterTheFirstDimensionIsValidated() {
        // These IDs share slot 31 in the default table, so removal shifts the second entry.
        assertAllDimensionsValidated(new String[] {"example:dimension_7", "example:dimension_50"});
    }

    @Test
    void allDimensionsAreValidatedWhenRemovingThemShrinksThePendingTable() {
        String[] dimensions = new String[128];
        for (int i = 0; i < dimensions.length; i++) {
            dimensions[i] = "example:dimension_" + i;
        }
        assertAllDimensionsValidated(dimensions);
    }

    private static void assertAllDimensionsValidated(String[] dimensions) {
        NetworkManager.NodeValidationTracker tracker = new NetworkManager.NodeValidationTracker();
        BlockPos pos = new BlockPos(0, 64, 0);
        long chunk = Functions.mergeChunkCoords(pos);
        for (String dimension : dimensions) {
            tracker.markPending(dimension, pos);
        }

        Set<String> remaining = new HashSet<>(Arrays.asList(dimensions));
        for (String dimension : tracker.pendingDimensionsSnapshot()) {
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
        String dimension = "example:dimension_7";
        BlockPos first = new BlockPos(0, 64, 0);
        BlockPos second = new BlockPos(32, 64, 0);
        tracker.markPending(dimension, first);
        tracker.markPending(dimension, second);
        var chunks = tracker.pendingChunksSnapshot(dimension);

        tracker.removePending(dimension, first);
        tracker.removePending(dimension, second);

        assertEquals(2, chunks.size());
        assertTrue(chunks.contains(Functions.mergeChunkCoords(first)));
        assertTrue(chunks.contains(Functions.mergeChunkCoords(second)));
        assertTrue(tracker.pendingChunksSnapshot(dimension).isEmpty());
        assertTrue(tracker.isPendingEmpty());
    }

    @Test
    void aDimensionRemovedBeforeItsTurnHasNoPendingChunks() {
        NetworkManager.NodeValidationTracker tracker = new NetworkManager.NodeValidationTracker();
        String dimension = "example:dimension_7";
        BlockPos pos = new BlockPos(0, 64, 0);
        tracker.markPending(dimension, pos);
        String[] dimensions = tracker.pendingDimensionsSnapshot();
        tracker.removePending(dimension, pos);

        assertArrayEquals(new String[] {dimension}, dimensions);
        assertTrue(tracker.pendingChunksSnapshot(dimensions[0]).isEmpty());
    }
}
