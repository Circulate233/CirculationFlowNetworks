package com.circulation.circulation_networks.manager;

import appeng.api.config.Actionable;
//? if <1.20 {
import appeng.api.config.PowerUnits;
import appeng.api.networking.energy.IEnergyGrid;
//?} else {
/*import appeng.api.networking.energy.IEnergyService;
*///?}
import com.circulation.circulation_networks.api.CFNBlockEntityEx;
import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.HandlerTickResult;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.api.IEnergyHandler.EnergyType;
import com.circulation.circulation_networks.energy.handler.AE2BackendHandler;
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

class AE2WarningTest {
    private final MachineBindingIndex index = MachineBindingIndex.INSTANCE;
    private final Grid grid = new Grid(UUID.randomUUID());
    private final List<EnergyMachineManager.MachineTransferSlot> slots = new ArrayList<>();
    private final Object2ObjectOpenHashMap<String, LongSet> observers = new Object2ObjectOpenHashMap<>();
    //? if <1.20 {
    private final IEnergyGrid energy = mock(IEnergyGrid.class);
    private final double unitsPerAe = PowerUnits.AE.convertTo(PowerUnits.RF, 1.0D);
    //?} else {
    /*private final IEnergyService energy = mock(IEnergyService.class);
    private final double unitsPerAe = 2.0D;
    *///?}
    private long epoch;
    private long position;
    private double demandUnits = 100.0D;
    private double demandAfterCommit;
    private double rejectedUnits;
    private long supply = 100L;
    private MachineBindingIndex.Binding aeBinding;
    private IEnergyHandler source;

    @AfterEach
    void close() {
        for (var slot : slots) slot.detachGrid();
        index.onServerStop();
    }

    @Test
    void alreadyFullNetworkDoesNotWarnOrInject() {
        demandUnits = 0.0D;
        bindAe();
        tick(() -> {});
        assertTrue(warnings().isEmpty());
        verify(energy, never()).injectPower(anyDouble(), any());
        verify(energy, times(1)).getEnergyDemand(anyDouble());
    }

    @Test
    void roundingRefundDoesNotWarnWhenOnlySubunitDemandRemains() {
        bindAe();
        bindSource();
        rejectedUnits = 0.25D;
        // At the screenshot's storage scale, subtraction can leave floating-point dust after filling.
        demandAfterCommit = Math.ulp(240_450_000.0D) * unitsPerAe;
        tick(this::transfer);
        assertEquals(1L, remainingBudget()); // Conservative energy refund must remain intact.
        assertTrue(warnings().isEmpty());
        verify(energy, times(2)).getEnergyDemand(anyDouble()); // One verdict shared by all controller aliases.
    }

    @Test
    void fullNetworkRejectsCommitWithoutWarningAndSourceEscrowIsReusable() {
        bindAe();
        bindSource();
        rejectedUnits = 100.0D;
        tick(this::transfer);
        assertEquals(100L, remainingBudget());
        assertTrue(warnings().isEmpty());
        for (var slot : slots) if (slot.handler() instanceof AE2BackendHandler) slot.detachGrid();

        IEnergyHandler next = mock(IEnergyHandler.class);
        long[] accepted = {0L};
        when(next.bindingPolicy()).thenReturn(immediatePolicy());
        when(next.beginServerTick(anyLong())).thenReturn(HandlerTickResult.UNCHANGED);
        when(next.canReceiveValue(any())).thenAnswer(call -> EnergyAmount.obtain(100L));
        when(next.receiveEnergy(any(), any())).thenAnswer(call -> {
            EnergyAmount amount = EnergyAmount.obtain((EnergyAmount) call.getArgument(0));
            accepted[0] += amount.asLongExact();
            return amount;
        });
        bindImmediate(next, EnergyType.RECEIVE);
        observers.clear();
        tick(this::transfer);
        verify(source, times(1)).extractEnergy(any(), any());
        assertEquals(100L, accepted[0]);
    }

    @Test
    void anotherPowerSourceFillingTheNetworkAfterSamplingClearsWarning() {
        bindAe();
        tick(() -> demandUnits = 0.0D);
        assertEquals(100L, remainingBudget());
        assertTrue(warnings().isEmpty());
        verify(energy, never()).injectPower(anyDouble(), any());
    }

    @Test
    void realShortfallStillWarnsOncePerSharedBackend() {
        bindAe();
        bindSource();
        supply = 60L;
        demandAfterCommit = 40.0D;
        tick(this::transfer);
        assertEquals(40L, remainingBudget());
        assertEquals(3, warnings().get("test").size());
        // Repeated collection in this epoch must reuse the same physical verdict.
        assertEquals(3, warnings().get("test").size());
        verify(energy, times(2)).getEnergyDemand(anyDouble());
    }

    @Test
    void oneWholeUnitOfRemainingDemandStillWarns() {
        bindAe();
        bindSource();
        supply = 99L;
        demandAfterCommit = 1.0D;
        tick(this::transfer);
        assertEquals(1L, remainingBudget());
        assertEquals(3, warnings().get("test").size());
    }

    @Test
    void nextEpochReplacesPreviousShortfallVerdictWhenNetworkBecomesFull() {
        bindAe();
        bindSource();
        supply = 60L;
        demandAfterCommit = 40.0D;
        tick(this::transfer);
        assertEquals(3, warnings().get("test").size());
        tick(() -> demandUnits = 0.0D);
        assertTrue(warnings().isEmpty());
        assertFalse(aeBinding.account().hasCachedWarning());
    }

    @Test
    void unobservedNetworkDoesNotQueryPostCommitDemand() {
        bindAe();
        bindSource();
        supply = 60L;
        demandAfterCommit = 40.0D;
        observers.clear();
        tick(this::transfer);
        assertTrue(warnings().isEmpty());
        verify(energy, times(1)).getEnergyDemand(anyDouble());
    }

    private void bindAe() {
        when(energy.getEnergyDemand(anyDouble())).thenAnswer(call -> demandUnits / unitsPerAe);
        when(energy.injectPower(anyDouble(), eq(Actionable.MODULATE))).thenAnswer(call -> {
            demandUnits = demandAfterCommit;
            return rejectedUnits / unitsPerAe;
        });
        IEnergyHandler endpoint = mock(IEnergyHandler.class);
        when(endpoint.bindingPolicy()).thenReturn(HandlerBindingPolicy.of(
            HandlerBindingPolicy.TickLifecycle.STATIC, HandlerBindingPolicy.RoleScope.FIXED,
            HandlerBindingPolicy.MappingScope.SHARED_BACKEND, HandlerBindingPolicy.PairMatching.NONE));
        MappedEnergyHandlerProvider provider = mock(MappedEnergyHandlerProvider.class);
        when(provider.acquireSharedBackend(endpoint)).thenReturn(new AE2BackendHandler(energy));
        CFNBlockEntityEx entity = mock(CFNBlockEntityEx.class);
        aeBinding = index.bindBlockEntity(entity, endpoint, provider);
        for (int alias = 0; alias < 3; alias++) {
            slot(entity, aeBinding, EnergyType.RECEIVE);
        }
        observers.put("test", new LongOpenHashSet(new long[]{0L}));
    }

    private void bindSource() {
        source = mock(IEnergyHandler.class);
        when(source.bindingPolicy()).thenReturn(immediatePolicy());
        when(source.beginServerTick(anyLong())).thenReturn(HandlerTickResult.UNCHANGED);
        when(source.canExtractValue(any())).thenAnswer(call -> EnergyAmount.obtain(supply));
        when(source.extractEnergy(any(), any())).thenAnswer(call -> {
            long amount = Math.min(supply, ((EnergyAmount) call.getArgument(0)).asLongExact());
            supply -= amount;
            return EnergyAmount.obtain(amount);
        });
        when(source.receiveEnergy(any(), any())).thenAnswer(call -> {
            EnergyAmount amount = EnergyAmount.obtain((EnergyAmount) call.getArgument(0));
            supply += amount.asLongExact();
            return amount;
        });
        bindImmediate(source, EnergyType.SEND);
    }

    private static HandlerBindingPolicy immediatePolicy() {
        return HandlerBindingPolicy.of(HandlerBindingPolicy.TickLifecycle.BEGIN_TICK,
            HandlerBindingPolicy.RoleScope.FIXED, HandlerBindingPolicy.MappingScope.NONE,
            HandlerBindingPolicy.PairMatching.NONE);
    }

    private void bindImmediate(IEnergyHandler handler, EnergyType role) {
        CFNBlockEntityEx entity = mock(CFNBlockEntityEx.class);
        slot(entity, index.bindBlockEntity(entity, handler, null), role);
    }

    private void slot(CFNBlockEntityEx entity, MachineBindingIndex.Binding binding, EnergyType role) {
        long pos = ++position;
        var slot = new EnergyMachineManager.MachineTransferSlot(entity, pos, new EnergyMachineManager.Interaction());
        slot.attachGridTracking(grid, grid.getInteraction());
        slot.configure(grid, binding.account(), binding.account().handler().bindingPolicy(), null,
            grid.getInteraction(), role, 0, EnergyHandlerRuntime.FailureContext.UNKNOWN,
            role == EnergyType.RECEIVE ? "test" : null, pos);
        slots.add(slot);
    }

    private void tick(Runnable action) {
        index.beginServerTick(++epoch);
        try {
            for (var slot : slots) slot.prepareWarning(epoch, observers);
            action.run();
        } finally {
            index.endServerTick(epoch);
        }
    }

    private void transfer() {
        EnergyMachineManager.transferEnergy(grid.getParticipantIndex().send(),
            grid.getParticipantIndex().receive(), EnergyMachineManager.Status.INTERACTION, epoch);
    }

    private long remainingBudget() {
        EnergyAmount amount = aeBinding.account().remainingReceive(epoch, null);
        try { return amount.asLongExact(); } finally { amount.recycle(); }
    }

    private Object2ObjectOpenHashMap<String, LongSet> warnings() {
        var result = new Object2ObjectOpenHashMap<String, LongSet>();
        for (var slot : slots) slot.collectWarning(result, epoch, observers);
        return result;
    }
}
