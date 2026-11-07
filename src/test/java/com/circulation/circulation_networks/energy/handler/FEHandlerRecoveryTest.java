package com.circulation.circulation_networks.energy.handler;

import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.HandlerTickResult;
import com.circulation.circulation_networks.api.IEnergyHandler.EnergyType;
import com.circulation.circulation_networks.manager.HandlerInvalidationSink;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FEHandlerRecoveryTest {
    @Test
    void fullReceiverKeepsItsRoleAndRecoversWithoutRebinding() {
        Rig rig = new Rig();
        rig.bind();
        assertEquals(EnergyType.RECEIVE, rig.handler.getType(null));
        rig.stored = 100;
        for (int epoch = 1; epoch <= 200; epoch++) {
            assertEquals(HandlerTickResult.UNCHANGED, rig.handler.beginServerTick(epoch));
            assertEquals(EnergyType.RECEIVE, rig.handler.getType(null));
            assertBudget(rig.handler, 0, true);
        }
        rig.stored = 0;
        rig.handler.beginServerTick(241); // Budget backoff may skip callbacks.
        assertEquals(EnergyType.RECEIVE, rig.handler.getType(null));
        assertBudget(rig.handler, 100, true);
        verify(rig.energy, never()).getAmountAsLong();
        verify(rig.energy, never()).getCapacityAsLong();
        rig.handler.unbindBlockEntity();
    }

    @Test
    void initiallyFullCapabilityRemainsBoundAndFindsDemandLater() {
        Rig rig = new Rig();
        rig.stored = 100;
        rig.bind();
        assertNotEquals(EnergyType.INVALID, rig.handler.getType(null));
        assertBudget(rig.handler, 0, true);
        rig.stored = 0;
        rig.handler.beginServerTick(41);
        assertEquals(EnergyType.RECEIVE, rig.handler.getType(null));
        assertBudget(rig.handler, 100, true);
        rig.handler.unbindBlockEntity();
    }

    @Test
    void depletedSourceKeepsItsRoleAndFindsSupplyLater() {
        Rig rig = new Rig();
        rig.input = false;
        rig.output = true;
        rig.stored = 100;
        rig.bind();
        assertEquals(EnergyType.SEND, rig.handler.getType(null));
        rig.stored = 0;
        rig.handler.beginServerTick(1);
        assertEquals(EnergyType.SEND, rig.handler.getType(null));
        assertBudget(rig.handler, 0, false);
        rig.stored = 100;
        rig.handler.beginServerTick(41);
        assertBudget(rig.handler, 100, false);
        rig.handler.unbindBlockEntity();
    }

    @Test
    void missingCapabilityStillRequestsRebinding() {
        Rig rig = new Rig();
        rig.bind();
        rig.visible = false;
        assertEquals(HandlerTickResult.RETRY_BINDING, rig.handler.beginServerTick(1));
        assertEquals(EnergyType.INVALID, rig.handler.getType(null));
        rig.handler.unbindBlockEntity();
        rig.visible = true;
        rig.bind();
        assertEquals(EnergyType.RECEIVE, rig.handler.getType(null));
        rig.handler.unbindBlockEntity();
    }

    private static void assertBudget(FEHandler handler, long expected, boolean input) {
        EnergyAmount budget = input ? handler.canReceiveValue(null) : handler.canExtractValue(null);
        try {
            assertEquals(expected, budget.asLongExact());
        } finally {
            budget.recycle();
        }
    }

    private static class Rig {
        final FEHandler handler = new FEHandler();
        final EnergyHandler energy = mock(EnergyHandler.class);
        final Level level = mock(Level.class);
        final BlockEntity tile = mock(BlockEntity.class);
        int stored;
        boolean input = true, output, visible = true;

        Rig() {
            when(tile.getLevel()).thenReturn(level);
            when(tile.getBlockPos()).thenReturn(BlockPos.ZERO);
            when(level.getCapability(eq(Capabilities.Energy.BLOCK), eq(BlockPos.ZERO), nullable(Direction.class)))
                .thenAnswer(call -> visible ? energy : null);
            // These tests only perform uncommitted probes; the caller changes storage between ticks.
            when(energy.insert(anyInt(), any())).thenAnswer(call -> input
                ? Math.min((int) call.getArgument(0), 100 - stored) : 0);
            when(energy.extract(anyInt(), any())).thenAnswer(call -> output
                ? Math.min((int) call.getArgument(0), stored) : 0);
        }

        void bind() {
            handler.bindBlockEntity(tile, mock(HandlerInvalidationSink.class));
        }
    }
}
