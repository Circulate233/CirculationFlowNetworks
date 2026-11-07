package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.api.*;
import com.circulation.circulation_networks.energy.handler.FEHandler;
import com.circulation.circulation_networks.energy.handler.EUHandler;
import com.circulation.circulation_networks.network.Grid;
import com.circulation.circulation_networks.network.nodes.HubNode;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.IEnergyStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class EnergyRoutingRecoveryTest {
    private static long epoch;
    private static int position;
    private final MachineBindingIndex index = MachineBindingIndex.INSTANCE;

    @AfterEach
    void closeBindings() { index.onServerStop(); }

    @Test
    void pureReceiverRecoversAfterTransientCapabilityLoss() {
        Tile tile = new Tile();
        Entity entity = new Entity(tile);
        FEHandler handler = new FEHandler();
        MachineBindingIndex.Binding binding = index.bindBlockEntity(entity.proxy, handler, null);
        tick(() -> assertEquals(100, handler.canReceiveValue(null).asLongExact()));
        tile.visible = false;
        tick(() -> assertTrue(handler.canReceiveValue(null).isZero()));
        tile.visible = true;
        for (int i = 0; i < 200; i++) tick(() -> {});
        assertSame(binding, index.binding(entity.proxy));
        assertTrue(binding.isActive());
        tick(() -> assertEquals(100, handler.canReceiveValue(null).asLongExact()));
    }

    @Test
    void failedRebindDoesNotAbortOtherBindingsAndContinuesRetrying() {
        Entity entity = new Entity(null);
        Handler handler = new Handler(IEnergyHandler.EnergyType.RECEIVE);
        MachineBindingIndex.Binding binding = index.bindBlockEntity(entity.proxy, handler, null);
        handler.failBinds = 3;
        entity.sink.invalidate();
        for (int i = 0; i < 200; i++) tick(() -> {});
        assertTrue(binding.isActive());
        assertEquals(0, handler.failBinds);
        assertEquals(5, handler.binds);
    }

    @Test
    void pureReceiverUsesAnotherFaceAndSupportsUnsidedCapability() {
        Tile tile = new Tile();
        tile.sided = true;
        tile.first.capacity = 0;
        FEHandler handler = new FEHandler();
        index.bindBlockEntity(new Entity(tile).proxy, handler, null);
        tick(() -> {
            assertEquals(IEnergyHandler.EnergyType.RECEIVE, handler.getType(null));
            assertEquals(100, handler.canReceiveValue(null).asLongExact());
            assertEquals(40, handler.receiveEnergy(EnergyAmount.obtain(40), null).asLongExact());
            assertEquals(40, tile.second.accepted);
        });
        Tile unsided = new Tile();
        unsided.unsided = true;
        assertTrue(FEHandler.supports(unsided));
    }

    @Test
    void fixedOrderRemainsUnchanged() {
        Grid grid = new Grid(UUID.randomUUID());
        Handler source = new Handler(IEnergyHandler.EnergyType.SEND);
        Handler first = new Handler(IEnergyHandler.EnergyType.RECEIVE);
        Handler second = new Handler(IEnergyHandler.EnergyType.RECEIVE);
        slot(grid, source); slot(grid, first); slot(grid, second);
        for (int i = 0; i < 200; i++) {
            source.extract = 100; first.receive = 100; second.receive = 100;
            route(grid);
        }
        assertEquals(20000, first.accepted);
        assertEquals(0, second.accepted);
    }

    @Test
    void smallPacketsDoNotBlockLargerSourcesAndEscrowCanAccumulate() {
        Grid grid = new Grid(UUID.randomUUID());
        Handler tiny = new Handler(IEnergyHandler.EnergyType.SEND);
        Handler large = new Handler(IEnergyHandler.EnergyType.SEND);
        Handler receiver = new Handler(IEnergyHandler.EnergyType.RECEIVE);
        receiver.quantum = 4;
        slot(grid, tiny); slot(grid, large); slot(grid, receiver);
        for (int i = 0; i < 200; i++) {
            tiny.extract = 1; large.extract = 1000; receiver.receive = 100;
            route(grid);
        }
        assertEquals(20000, receiver.accepted);
        assertTrue(large.sent > 0);
        assertEquals(tiny.sent + large.sent, receiver.accepted);
        assertEquals(200, tiny.sent);
    }

    @Test
    void clearingOverrideReadsPhysicalRole() {
        Handler handler = new Handler(IEnergyHandler.EnergyType.SEND);
        EnergyMachineManager.MachineTransferSlot slot = slot(new Grid(UUID.randomUUID()), handler);
        handler.role = IEnergyHandler.EnergyType.RECEIVE;
        assertEquals(IEnergyHandler.EnergyType.RECEIVE, slot.resolveRole(handler, handler.bindingPolicy(),
            null, null, EnergyHandlerRuntime.FailureContext.UNKNOWN));
    }

    @Test
    void feRoleDiscoverySurvivesTicksSkippedByBudgetBackoff() {
        Tile tile = new Tile();
        FEHandler handler = new FEHandler();
        handler.bindBlockEntity(tile, org.mockito.Mockito.mock(HandlerInvalidationSink.class));
        handler.beginServerTick(1);
        tile.first.output = true;
        handler.beginServerTick(41);
        assertEquals(IEnergyHandler.EnergyType.STORAGE, handler.getType(null));
        handler.unbindBlockEntity();
    }

    @Test
    void shielderRefreshesMachineOnlyCoverage() {
        final int[] refreshed = {0};
        final boolean[] active = {true};
        final BlockPos pos = new BlockPos(100, 64, 100);
        index.registerMachineRoute(17, pos.toLong(), new MachineBindingIndex.Route() {
            public void close() {} public void revoke() {} public void updateMachinePriority(int priority) {}
            public void refresh() { refreshed[0]++; }
        });
        index.commitPendingUpdates();
        ICirculationShielderBlockEntity shield = (ICirculationShielderBlockEntity) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{ICirculationShielderBlockEntity.class}, (p, m, a) -> {
                switch (m.getName()) {
                    case "getBEPos": return pos;
                    case "getMaxScope": case "getScope": return 8;
                    case "isActive": return active[0];
                    default: throw new AssertionError(m.getName());
                }
            });
        CirculationShielderManager.INSTANCE.register(shield, 17);
        index.commitPendingUpdates();
        active[0] = false;
        CirculationShielderManager.INSTANCE.refreshActiveState(shield, 17);
        index.commitPendingUpdates();
        assertEquals(3, refreshed[0]);
        CirculationShielderManager.INSTANCE.unregister(shield, 17);
    }

    @Test
    @SuppressWarnings("unchecked")
    void unloadedMachinesWithoutResidencyRecordsReleaseLifecycleIdentity() throws Exception {
        EnergyMachineManager manager = EnergyMachineManager.INSTANCE;
        Field field = EnergyMachineManager.class.getDeclaredField("lifecyclePositions");
        field.setAccessible(true);
        MachineLifecyclePositionIndex<CFNBlockEntityEx> states =
            (MachineLifecyclePositionIndex<CFNBlockEntityEx>) field.get(manager);
        Entity entity = new Entity(null);
        int before = states.size();
        long generation = states.submit(91, entity.pos.toLong(), entity.proxy, MachineLifecyclePositionIndex.Action.DISCOVERED);
        states.markDeferred(91, entity.pos.toLong(), entity.proxy, generation, MachineLifecyclePositionIndex.Action.DISCOVERED);
        manager.scheduleDiscoveryThrottle(entity.proxy);
        manager.markChunkLoaded(91, entity.pos.getX() >> 4, 0);
        manager.markChunkUnloaded(91, entity.pos.getX() >> 4, 0);
        assertEquals(before, states.size());
        Method reconcile = EnergyMachineManager.class.getDeclaredMethod("reconcileMachineChunkResidency");
        reconcile.setAccessible(true);
        reconcile.invoke(manager);
        assertEquals(0, entity.timer);
    }

    @Test
    @SuppressWarnings("unchecked")
    void staleLifecycleCancelsDiscoveryRetryWithoutAnUnloadEvent() throws Exception {
        EnergyMachineManager manager = EnergyMachineManager.INSTANCE;
        Field field = EnergyMachineManager.class.getDeclaredField("lifecyclePositions");
        field.setAccessible(true);
        MachineLifecyclePositionIndex<CFNBlockEntityEx> states =
            (MachineLifecyclePositionIndex<CFNBlockEntityEx>) field.get(manager);
        Entity entity = new Entity(null);
        int before = states.size();
        long generation = states.submit(0, entity.pos.toLong(), entity.proxy, MachineLifecyclePositionIndex.Action.READY);
        manager.scheduleDiscoveryThrottle(entity.proxy);
        Method apply = EnergyMachineManager.class.getDeclaredMethod("applyLifecycleCommand", EnergyMachineManager.LifecycleCommand.class);
        apply.setAccessible(true);
        apply.invoke(manager, new EnergyMachineManager.LifecycleCommand(0, entity.pos.toLong(), entity.proxy,
            generation, MachineLifecyclePositionIndex.Action.READY, null));
        assertEquals(before, states.size());
        assertEquals(0, entity.timer);
    }

    @Test
    void failedManagerProbeDoesNotPreventCompatibleFallback() throws Exception {
        IEnergyHandlerManager broken = (IEnergyHandlerManager) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{IEnergyHandlerManager.class}, (p, m, a) -> { throw new IllegalStateException("not ready"); });
        IEnergyHandlerManager fallback = (IEnergyHandlerManager) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{IEnergyHandlerManager.class}, (p, m, a) -> {
                if (m.getName().equals("isAvailable")) return true;
                throw new AssertionError(m.getName());
            });
        Method select = com.circulation.circulation_networks.registry.RegistryEnergyHandler.class.getDeclaredMethod(
            "selectEnergyManagerExcluding", TileEntity.class, IEnergyHandlerManager[].class, IEnergyHandlerManager.class);
        select.setAccessible(true);
        assertSame(fallback, select.invoke(null, new Tile(), new IEnergyHandlerManager[]{broken, fallback}, null));
    }

    @Test
    void shortRegistrationRetryExpiresAndCanBeCancelled() throws Exception {
        EnergyMachineManager manager = EnergyMachineManager.INSTANCE;
        Entity entity = new Entity(null);
        Method schedule = EnergyMachineManager.class.getDeclaredMethod("scheduleDiscoveryThrottle", CFNBlockEntityEx.class, int.class);
        schedule.setAccessible(true);
        schedule.invoke(manager, entity.proxy, 20);
        assertTrue(entity.timer >= 20 && entity.timer < 120);
        for (int tick = 0; tick < 120; tick++) manager.advanceDiscoveryThrottleWheel();
        assertEquals(0, entity.timer);
        schedule.invoke(manager, entity.proxy, 20);
        manager.cancelDiscoveryThrottle(entity.proxy);
        assertEquals(0, entity.timer);
    }

    @Test
    void ic2TransfersFractionalEuWithoutIncorrectFeAccounting() throws Exception {
        EUHandler handler = new EUHandler();
        final double[] drawn = {0};
        Object source = Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{ic2.api.energy.tile.IEnergySource.class}, (p, m, a) -> {
                if (m.getName().equals("drawEnergy")) { drawn[0] += (double) a[0]; return null; }
                throw new AssertionError(m.getName());
            });
        Field send = EUHandler.class.getDeclaredField("send");
        send.setAccessible(true); send.set(handler, source);
        for (int amount = 1; amount <= 5; amount++) {
            assertEquals(amount, handler.extractEnergy(EnergyAmount.obtain(amount), null).asLongExact());
        }
        assertEquals(15 / 4.0, drawn[0]);
        Object sink = Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{ic2.api.energy.tile.IEnergySink.class}, (p, m, a) -> {
                if (m.getName().equals("injectEnergy")) return Math.max(0.0, (double) a[1] - 0.5);
                if (m.getName().equals("getDemandedEnergy")) return 0.5;
                throw new AssertionError(m.getName());
            });
        Field receive = EUHandler.class.getDeclaredField("receive");
        receive.setAccessible(true); receive.set(handler, sink);
        assertEquals(2, handler.canReceiveValue(null).asLongExact());
        assertEquals(2, handler.receiveEnergy(EnergyAmount.obtain(4), null).asLongExact());
        assertEquals(1, handler.receiveEnergy(EnergyAmount.obtain(1), null).asLongExact());
    }

    private void tick(Runnable action) {
        index.beginServerTick(++epoch);
        try { action.run(); } finally { index.endServerTick(epoch); }
    }

    private void route(Grid grid) {
        tick(() -> EnergyMachineManager.transferEnergy(grid.getParticipantIndex().send(),
            grid.getParticipantIndex().receive(), EnergyMachineManager.Status.INTERACTION, epoch));
    }

    private EnergyMachineManager.MachineTransferSlot slot(Grid grid, Handler handler) {
        Entity entity = new Entity(null);
        MachineBindingIndex.Binding binding = index.bindBlockEntity(entity.proxy, handler, null);
        EnergyMachineManager.MachineTransferSlot slot = new EnergyMachineManager.MachineTransferSlot(
            entity.proxy, entity.pos.toLong(), new EnergyMachineManager.Interaction());
        slot.attachGridTracking(grid, grid.getInteraction());
        slot.configure(grid, binding.account(), handler.bindingPolicy(), null, grid.getInteraction(), handler.role, 0,
            EnergyHandlerRuntime.FailureContext.UNKNOWN, null, 0);
        return slot;
    }

    private static class Entity {
        int timer, stage;
        HandlerInvalidationSink sink;
        final BlockPos pos = new BlockPos(position++, 64, 0);
        final CFNBlockEntityEx proxy;
        Entity(TileEntity tile) {
            proxy = (CFNBlockEntityEx) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{CFNBlockEntityEx.class}, (p, m, a) -> {
                    switch (m.getName()) {
                        case "cfn_getWorld": return null;
                        case "cfn_removeMachineHandlerRuntime": return null;
                        case "cfn_getDimensionId": return 0;
                        case "cfn_getBlockPos": return pos;
                        case "cfn_getTypeName": return "TestMachine";
                        case "cfn_bindEnergyHandler": sink = (HandlerInvalidationSink) a[1]; ((IEnergyHandler) a[0]).bindBlockEntity(tile, sink); return null;
                        case "cfn_getEnergyThrottleTimer": return timer;
                        case "cfn_setEnergyThrottleTimer": timer = (int) a[0]; return null;
                        case "cfn_getEnergyLastThrottleTimer": return stage;
                        case "cfn_setEnergyLastThrottleTimer": stage = (int) a[0]; return null;
                        case "cfn_getEnergyPriority": return 0;
                        case "cfn_isRemoved": case "cfn_isEnergyBlacklisted": case "cfn_isSupplyBlacklisted": return false;
                        case "hashCode": return System.identityHashCode(p);
                        case "equals": return p == a[0];
                        case "toString": return "TestMachine";
                        default: throw new AssertionError(m.getName());
                    }
                });
        }
    }

    private static class Input implements IEnergyStorage {
        int capacity = 100, accepted;
        boolean output;
        public boolean canExtract() { return output; }
        public boolean canReceive() { return true; }
        public int getEnergyStored() { return 0; }
        public int getMaxEnergyStored() { return capacity; }
        public int extractEnergy(int amount, boolean simulate) { return 0; }
        public int receiveEnergy(int amount, boolean simulate) {
            int result = Math.min(capacity, amount);
            if (!simulate) accepted += result;
            return result;
        }
    }

    private static class Tile extends TileEntity {
        boolean visible = true, sided, unsided;
        final Input first = new Input(), second = new Input();
        @Override @SuppressWarnings("unchecked")
        public <T> T getCapability(Capability<T> capability, EnumFacing side) {
            if (!visible || unsided && side != null) return null;
            return (T) (sided ? side == EnumFacing.DOWN ? first : side == EnumFacing.UP ? second : null : first);
        }
    }

    private static class Handler implements IEnergyHandler {
        long extract, receive, accepted, sent;
        int quantum = 1, failBinds, binds;
        EnergyType role;
        Handler(EnergyType role) { this.role = role; }
        public HandlerBindingPolicy bindingPolicy() { return HandlerBindingPolicy.DEFAULT; }
        public void bindBlockEntity(TileEntity tile, HandlerInvalidationSink sink) {
            binds++;
            if (failBinds > 0) { failBinds--; throw new IllegalStateException("temporarily unavailable"); }
        }
        public void unbindBlockEntity() {}
        public HandlerTickResult beginServerTick(long epoch) { return HandlerTickResult.UNCHANGED; }
        public void endServerTick(long epoch) {}
        public void bindItem(ItemStack stack, HubNode.HubMetadata metadata) {}
        public void unbindItem() {}
        public EnergyType getType(HubNode.HubMetadata metadata) { return role; }
        public boolean canExtract(IEnergyHandler handler, HubNode.HubMetadata metadata) { return true; }
        public boolean canReceive(IEnergyHandler handler, HubNode.HubMetadata metadata) { return true; }
        public EnergyAmount canExtractValue(HubNode.HubMetadata metadata) { return EnergyAmount.obtain(extract); }
        public EnergyAmount canReceiveValue(HubNode.HubMetadata metadata) { return EnergyAmount.obtain(receive); }
        public EnergyAmount extractEnergy(EnergyAmount amount, HubNode.HubMetadata metadata) {
            long result = Math.min(extract, amount.asLongExact()); extract -= result; sent += result;
            return EnergyAmount.obtain(result);
        }
        public EnergyAmount receiveEnergy(EnergyAmount amount, HubNode.HubMetadata metadata) {
            long result = Math.min(receive, amount.asLongExact()) / quantum * quantum;
            receive -= result; accepted += result;
            return EnergyAmount.obtain(result);
        }
    }
}
