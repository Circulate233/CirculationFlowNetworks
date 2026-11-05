package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.api.IEnergyHandler.EnergyType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnergyIndexRecoveryTest {
    @Test
    void topologyChangesAreNotDiscardedAsDuplicateDiscovery() {
        MachineLifecyclePositionIndex<Object> index = new MachineLifecyclePositionIndex<>();
        Object machine = new Object();
        long generation = index.submit(0, 42L, machine, MachineLifecyclePositionIndex.Action.DISCOVERED);
        index.markApplied(0, 42L, machine, generation, MachineLifecyclePositionIndex.Action.DISCOVERED);
        assertEquals(-1, index.submit(0, 42L, machine, MachineLifecyclePositionIndex.Action.DISCOVERED));
        assertTrue(index.submit(0, 42L, machine, MachineLifecyclePositionIndex.Action.TOPOLOGY_CHANGED) > 0);
    }

    @Test
    void unloadReleasesDeferredAndAppliedMachineIdentitiesOnlyInItsChunk() throws Exception {
        MachineLifecyclePositionIndex<Object> index = new MachineLifecyclePositionIndex<>();
        Object machine = new Object();
        long first = pack(-17, 64, -1), neighbor = pack(-16, 64, -1);
        long generation = index.submit(0, first, machine, MachineLifecyclePositionIndex.Action.DISCOVERED);
        index.markDeferred(0, first, machine, generation, MachineLifecyclePositionIndex.Action.DISCOVERED);
        index.submit(0, neighbor, new Object(), MachineLifecyclePositionIndex.Action.READY);
        index.submit(1, first, new Object(), MachineLifecyclePositionIndex.Action.READY);
        assertEquals((-2L << 32) | 0xffffffffL, MachineLifecyclePositionIndex.chunkOf(first));
        index.releaseChunk(0, MachineLifecyclePositionIndex.chunkOf(first));
        assertEquals(2, index.size());
        index.releaseDimension(0);
        assertEquals(1, index.size());
        index.releaseDimension(1);
        assertEquals(0, index.size());
    }

    @Test
    void facesKeepStructuralRolesAndUseAnotherUsableFaceWithoutAddingAliases() {
        EnergyFaceSelector<Box> selector = new EnergyFaceSelector<>(box -> box.output, box -> box.input,
            box -> box.extract, box -> box.receive);
        Box first = new Box(), second = new Box();
        first.input = second.input = true;
        second.receive = 100;
        selector.add(first);
        selector.add(second);
        selector.add(second);
        assertEquals(EnergyType.RECEIVE, selector.role());
        assertEquals(100, selector.budget(true));
        assertSame(second, selector.receiver());
        second.receive = 0;
        assertEquals(0, selector.budget(true));
        assertEquals(EnergyType.RECEIVE, selector.role());
        first.output = true;
        first.extract = 50;
        assertEquals(EnergyType.STORAGE, selector.role());
        assertEquals(50, selector.budget(false));
        selector.clear();
        assertEquals(EnergyType.INVALID, selector.role());
    }

    @Test
    void integerJoulesNeverRoundAnOddFeRequestIntoUnaccountedEnergy() {
        assertEquals(0, MekanismLongEnergyConversion.toJoules(1));
        assertEquals(5, MekanismLongEnergyConversion.toJoules(2));
        assertEquals(5, MekanismLongEnergyConversion.toJoules(3));
        assertEquals(0, MekanismLongEnergyConversion.toFe(4));
        assertEquals(2, MekanismLongEnergyConversion.toFe(5));
        long max = MekanismLongEnergyConversion.toJoules(Long.MAX_VALUE);
        assertTrue(max > 0);
        assertEquals(0, max % 5);
        assertEquals(MekanismLongEnergyConversion.MAX_FE, MekanismLongEnergyConversion.toFe(max));
    }

    private static long pack(int x, int y, int z) throws Exception {
        //? if <1.20 {
        return ((x & 0x3ffffffL) << 38) | ((y & 0xfffL) << 26) | (z & 0x3ffffffL);
        //?} else {
        /*return ((x & 0x3ffffffL) << 38) | ((z & 0x3ffffffL) << 12) | (y & 0xfffL);
        *///?}
    }

    private static final class Box {
        boolean input, output;
        long extract, receive;
    }
}
