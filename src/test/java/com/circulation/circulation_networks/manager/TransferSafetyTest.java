package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.api.CFNBlockEntityEx;
import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.HandlerTickResult;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.api.IEnergyHandler.EnergyType;
import com.circulation.circulation_networks.network.Grid;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TransferSafetyTest {
    private final MachineBindingIndex index = MachineBindingIndex.INSTANCE;
    private final List<EnergyMachineManager.MachineTransferSlot> slots = new ArrayList<>();
    private long epoch;
    private long position;

    @AfterEach
    void close() {
        for (var slot : slots) slot.detachGrid();
        index.onServerStop();
    }

    @Test
    void sharedBackendCannotConsumeItsOwnBudgetAndStarveAnotherReceiver() {
        selfTransfer(false, false);
    }

    @Test
    void sharedBackendCanReceiveFromALaterSourceWithoutAdvancingTheFirstSource() {
        selfTransfer(false, true);
    }

    @Test
    void pairMatchingAlsoSkipsSelfTransfer() {
        selfTransfer(true, false);
    }

    private void selfTransfer(boolean pairs, boolean anotherSource) {
        Grid grid = new Grid(UUID.randomUUID());
        Endpoint shared = new Endpoint(100, 100, pairs);
        slot(grid, shared, EnergyType.SEND, 0);
        slot(grid, shared, EnergyType.RECEIVE, 10);
        Endpoint other = new Endpoint(100, 0, pairs);
        if (anotherSource) slot(grid, other, EnergyType.SEND, 0);
        Endpoint receiver = new Endpoint(0, 100, pairs);
        slot(grid, receiver, EnergyType.RECEIVE, 0);
        tick(() -> EnergyMachineManager.transferEnergy(grid.getParticipantIndex().send(),
            grid.getParticipantIndex().receive(), EnergyMachineManager.Status.INTERACTION, epoch));
        assertEquals(100, receiver.accepted);
        assertEquals(100, shared.sent);
        assertEquals(anotherSource ? 100 : 0, shared.accepted);
        assertEquals(anotherSource ? 100 : 0, other.sent);
        assertEquals(shared.sent + other.sent, shared.accepted + receiver.accepted);
    }

    @Test
    void partialAndRejectedReceivesPreserveEscrowForTheNextConsumer() {
        Grid grid = new Grid(UUID.randomUUID());
        Endpoint sender = new Endpoint(100, 0, false);
        Endpoint partial = new Endpoint(0, 100, false);
        partial.acceptLimit = 30;
        Endpoint rejected = new Endpoint(0, 100, false);
        rejected.acceptLimit = 0;
        Endpoint last = new Endpoint(0, 100, false);
        slot(grid, sender, EnergyType.SEND, 0);
        slot(grid, partial, EnergyType.RECEIVE, 20);
        slot(grid, rejected, EnergyType.RECEIVE, 10);
        slot(grid, last, EnergyType.RECEIVE, 0);
        tick(() -> EnergyMachineManager.transferEnergy(grid.getParticipantIndex().send(),
            grid.getParticipantIndex().receive(), EnergyMachineManager.Status.INTERACTION, epoch));
        assertEquals(30, partial.accepted);
        assertEquals(0, rejected.accepted);
        assertEquals(70, last.accepted);
        assertEquals(partial.accepted + last.accepted, sender.sent);
        verify(sender.handler, times(1)).extractEnergy(any(), any());
    }

    @Test
    void rejectedDeferredCommitKeepsSourceEscrowForTheNextTick() {
        Grid grid = new Grid(UUID.randomUUID());
        Endpoint sender = new Endpoint(100, 0, false);
        Endpoint receiver = new Endpoint(0, 100, false, true);
        receiver.acceptLimit = 40;
        slot(grid, sender, EnergyType.SEND, 0);
        var deferredSlot = slot(grid, receiver, EnergyType.RECEIVE, 0);
        tick(() -> EnergyMachineManager.transferEnergy(grid.getParticipantIndex().send(),
            grid.getParticipantIndex().receive(), EnergyMachineManager.Status.INTERACTION, epoch));
        assertEquals(100, sender.sent);
        assertEquals(40, receiver.accepted);
        deferredSlot.detachGrid();
        Endpoint next = new Endpoint(0, 100, false);
        slot(grid, next, EnergyType.RECEIVE, 0);
        tick(() -> EnergyMachineManager.transferEnergy(grid.getParticipantIndex().send(),
            grid.getParticipantIndex().receive(), EnergyMachineManager.Status.INTERACTION, epoch));
        assertEquals(60, next.accepted);
        assertEquals(receiver.accepted + next.accepted, sender.sent);
        verify(sender.handler, times(1)).extractEnergy(any(), any());
    }

    @Test
    void suspendedReceiverKeepsFaultWarningUntilRecoveryOrDetach() throws Exception {
        Grid grid = new Grid(UUID.randomUUID());
        Endpoint endpoint = new Endpoint(0, 100, false);
        var slot = slot(grid, endpoint, EnergyType.RECEIVE, 0);
        slot.suspendTransfer(true);
        assertTrue(grid.getParticipantIndex().receive().isEmpty());
        assertEquals(1, faultWarnings());
        slot.suspendTransfer(); // Route rebuild during retry backoff must retain the fault.
        assertEquals(1, faultWarnings());
        verify(endpoint.handler, never()).canReceiveValue(any());
        configure(grid, slot, endpoint, EnergyType.RECEIVE, 0);
        assertEquals(0, faultWarnings());
        slot.suspendTransfer(true);
        assertEquals(1, faultWarnings());
        slot.detachGrid();
        assertEquals(0, faultWarnings());
    }

    @Test
    void normalBudgetBackoffAndSendOnlyFailureDoNotCreateFaultWarnings() throws Exception {
        Grid grid = new Grid(UUID.randomUUID());
        var receiver = slot(grid, new Endpoint(0, 0, false), EnergyType.RECEIVE, 0);
        receiver.suspendTransfer();
        var sender = slot(grid, new Endpoint(0, 0, false), EnergyType.SEND, 0);
        sender.suspendTransfer(true);
        assertEquals(0, faultWarnings());
    }

    @Test
    void warningsShareOneBudgetReadAcrossAliasesAndRespectObserverWindow() {
        Grid grid = new Grid(UUID.randomUUID());
        Endpoint endpoint = new Endpoint(0, 100, false);
        for (int i = 0; i < 100; i++) slot(grid, endpoint, EnergyType.RECEIVE, 0);
        var observers = observers();
        tick(() -> { for (var slot : slots) slot.prepareWarning(epoch, observers); });
        verify(endpoint.handler, times(1)).canReceiveValue(any());
        observers.clear();
        tick(() -> { for (var slot : slots) slot.prepareWarning(epoch, observers); });
        verify(endpoint.handler, times(1)).canReceiveValue(any());
    }

    private int faultWarnings() throws Exception {
        var warnings = new Object2ObjectOpenHashMap<String, LongSet>();
        var collect = EnergyMachineManager.class.getDeclaredMethod("collectBindingWarnings",
            it.unimi.dsi.fastutil.objects.Object2ObjectMap.class,
            it.unimi.dsi.fastutil.objects.Object2ObjectMap.class);
        collect.setAccessible(true);
        collect.invoke(EnergyMachineManager.INSTANCE, warnings, observers());
        LongSet positions = warnings.get("test");
        return positions == null ? 0 : positions.size();
    }

    private Object2ObjectOpenHashMap<String, LongSet> observers() {
        var observers = new Object2ObjectOpenHashMap<String, LongSet>();
        observers.put("test", new LongOpenHashSet(new long[]{0L}));
        return observers;
    }

    private EnergyMachineManager.MachineTransferSlot slot(Grid grid, Endpoint endpoint, EnergyType role, int priority) {
        var slot = new EnergyMachineManager.MachineTransferSlot(endpoint.entity, ++position,
            new EnergyMachineManager.Interaction());
        slot.attachGridTracking(grid, grid.getInteraction());
        configure(grid, slot, endpoint, role, priority);
        slots.add(slot);
        return slot;
    }

    private void configure(Grid grid, EnergyMachineManager.MachineTransferSlot slot,
                           Endpoint endpoint, EnergyType role, int priority) {
        slot.configure(grid, endpoint.binding.account(), endpoint.handler.bindingPolicy(), null,
            grid.getInteraction(), role, priority, EnergyHandlerRuntime.FailureContext.UNKNOWN,
            role == EnergyType.RECEIVE ? "test" : null, 0L);
    }

    private void tick(Runnable run) {
        index.beginServerTick(++epoch);
        try { run.run(); } finally { index.endServerTick(epoch); }
    }

    private class Endpoint {
        final CFNBlockEntityEx entity = mock(CFNBlockEntityEx.class);
        final IEnergyHandler handler;
        final MachineBindingIndex.Binding binding;
        long extract, receive, sent, accepted;
        long acceptLimit = Long.MAX_VALUE, pending, rejected;

        Endpoint(long extract, long receive, boolean pairs) {
            this(extract, receive, pairs, false);
        }

        Endpoint(long extract, long receive, boolean pairs, boolean deferred) {
            handler = deferred ? mock(IEnergyHandler.class, withSettings().extraInterfaces(DeferredReceiveCommit.class))
                : mock(IEnergyHandler.class);
            this.extract = extract;
            this.receive = receive;
            var policy = HandlerBindingPolicy.of(deferred ? HandlerBindingPolicy.TickLifecycle.BEGIN_END_TICK
                : HandlerBindingPolicy.TickLifecycle.BEGIN_TICK,
                HandlerBindingPolicy.RoleScope.FIXED, HandlerBindingPolicy.MappingScope.NONE,
                pairs ? HandlerBindingPolicy.PairMatching.REQUIRED : HandlerBindingPolicy.PairMatching.NONE);
            when(handler.bindingPolicy()).thenReturn(policy);
            when(handler.beginServerTick(anyLong())).thenReturn(HandlerTickResult.UNCHANGED);
            when(handler.canReceive(any(), any())).thenReturn(true);
            when(handler.canExtract(any(), any())).thenReturn(true);
            when(handler.canExtractValue(any())).thenAnswer(call -> EnergyAmount.obtain(this.extract));
            when(handler.canReceiveValue(any())).thenAnswer(call -> EnergyAmount.obtain(this.receive));
            when(handler.extractEnergy(any(), any())).thenAnswer(call -> {
                long amount = Math.min(this.extract, ((EnergyAmount) call.getArgument(0)).asLongExact());
                this.extract -= amount; sent += amount;
                return EnergyAmount.obtain(amount);
            });
            when(handler.receiveEnergy(any(), any())).thenAnswer(call -> {
                long amount = Math.min(this.receive, ((EnergyAmount) call.getArgument(0)).asLongExact());
                if (deferred) pending += amount;
                else {
                    amount = Math.min(amount, acceptLimit);
                    acceptLimit -= amount;
                    accepted += amount;
                }
                this.receive -= amount;
                return EnergyAmount.obtain(amount);
            });
            if (deferred) {
                doAnswer(call -> {
                    long committed = Math.min(pending, acceptLimit);
                    accepted += committed;
                    rejected = pending - committed;
                    pending = 0;
                    return null;
                }).when(handler).endServerTick(anyLong());
                when(((DeferredReceiveCommit) handler).drainRejectedReceive()).thenAnswer(call -> {
                    EnergyAmount amount = EnergyAmount.obtain(rejected);
                    rejected = 0;
                    return amount;
                });
            }
            if (deferred) {
                IEnergyHandler endpoint = mock(IEnergyHandler.class);
                when(endpoint.bindingPolicy()).thenReturn(HandlerBindingPolicy.of(
                    HandlerBindingPolicy.TickLifecycle.STATIC, HandlerBindingPolicy.RoleScope.FIXED,
                    HandlerBindingPolicy.MappingScope.SHARED_BACKEND, HandlerBindingPolicy.PairMatching.NONE));
                MappedEnergyHandlerProvider provider = mock(MappedEnergyHandlerProvider.class);
                when(provider.acquireSharedBackend(endpoint)).thenReturn(handler);
                binding = index.bindBlockEntity(entity, endpoint, provider);
            } else {
                binding = index.bindBlockEntity(entity, handler, null);
            }
        }
    }
}
