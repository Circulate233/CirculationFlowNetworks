package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.api.CFNBlockEntityEx;
import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.energy.handler.MEKHandler;
import com.circulation.circulation_networks.network.Grid;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import mekanism.api.energy.IStrictEnergyAcceptor;
import mekanism.api.energy.IStrictEnergyStorage;
import mekanism.api.energy.IStrictEnergyOutputter;
import mekanism.api.energy.IEnergizedItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MekRecoveryTest {
    private final MachineBindingIndex index = MachineBindingIndex.INSTANCE;
    private long epoch;

    @AfterEach
    void reset() { index.onServerStop(); }

    @Test
    void recoversAutomaticallyAfterRebindingWhileAllInputsAreUnavailable() throws Exception {
        MekTile tile = new MekTile();
        Entity entity = new Entity(tile);
        MEKHandler handler = new MEKHandler();
        MachineBindingIndex.Binding binding = index.bindBlockEntity(entity.proxy, handler, null);
        // A real dimension makes the repeated-failure manager fallback observable.
        entity.world = org.mockito.Mockito.mock(net.minecraft.world.World.class);
        java.lang.reflect.Field provider = net.minecraft.world.World.class.getField("provider");
        provider.setAccessible(true);
        provider.set(entity.world, org.mockito.Mockito.mock(net.minecraft.world.WorldProvider.class));
        assertEquals(IEnergyHandler.EnergyType.RECEIVE, handler.getType(null));
        tick(() -> assertEquals(400L, handler.canReceiveValue(null).asLongExact()));

        tile.enabled = false;
        tick(() -> assertTrue(handler.canReceiveValue(null).isZero()));
        for (int i = 0; i < 1000; i++) tick(() -> {});
        assertFalse(binding.isActive());
        assertEquals(IEnergyHandler.EnergyType.INVALID, handler.getType(null));
        assertTrue(entity.binds <= 16, "failed bindings must back off, not scan every tick");
        assertEquals(0, entity.managerFallbacks, "temporarily unavailable faces must keep their compatible owner");

        tile.enabled = true;
        for (int i = 0; i < 201; i++) tick(() -> {});
        assertTrue(binding.isActive());
        assertEquals(IEnergyHandler.EnergyType.RECEIVE, handler.getType(null));
        tick(() -> assertEquals(400L, handler.canReceiveValue(null).asLongExact()));
        tick(() -> {
            EnergyAmount request = EnergyAmount.obtain(400);
            EnergyAmount received = handler.receiveEnergy(request, null);
            assertEquals(400, received.asLongExact());
            received.recycle(); request.recycle();
        });
        assertEquals(1000.0, tile.stored);
    }

    @Test
    void zeroSimulatedAcceptanceClearsWarningEvenWithEmptyStoredEnergy() {
        MekTile tile = new MekTile();
        Entity entity = new Entity(tile);
        MEKHandler handler = new MEKHandler();
        MachineBindingIndex.Binding binding = index.bindBlockEntity(entity.proxy, handler, null);
        Grid grid = new Grid(UUID.randomUUID());
        EnergyMachineManager.MachineTransferSlot slot = new EnergyMachineManager.MachineTransferSlot(
            entity.proxy, entity.pos.toLong(), new EnergyMachineManager.Interaction());
        slot.attachGridTracking(grid, grid.getInteraction());
        slot.configure(grid, binding.account(), handler.bindingPolicy(), null, grid.getInteraction(),
            IEnergyHandler.EnergyType.RECEIVE, 0, EnergyHandlerRuntime.FailureContext.UNKNOWN,
            "legacy:0", entity.pos.toLong());
        Object2ObjectOpenHashMap<String, LongSet> observers = new Object2ObjectOpenHashMap<>();
        observers.put("legacy:0", new LongOpenHashSet(new long[] {0L}));
        Object2ObjectOpenHashMap<String, LongSet> warnings = new Object2ObjectOpenHashMap<>();

        tick(() -> slot.prepareWarning(epoch, observers));
        slot.collectWarning(warnings, epoch, observers);
        assertTrue(warnings.get("legacy:0").contains(entity.pos.toLong()));

        warnings.clear();
        tile.accepting = false;
        tick(() -> slot.prepareWarning(epoch, observers));
        slot.collectWarning(warnings, epoch, observers);
        assertEquals(0.0, tile.getEnergy());
        assertTrue(tile.canReceiveEnergy(EnumFacing.DOWN));
        assertTrue(warnings.isEmpty());
        assertFalse(binding.account().hasCachedWarning());
        slot.detachGrid();
    }

    private void tick(Runnable action) {
        index.beginServerTick(++epoch);
        try { action.run(); } finally { index.endServerTick(epoch); }
    }

    @Test
    void aFullMekMachineKeepsReceiveRoleAndRecoversAfterItsEnergyIsConsumed() {
        MekTile tile = new MekTile();
        tile.stored = tile.getMaxEnergy();
        Entity entity = new Entity(tile);
        MEKHandler handler = new MEKHandler();
        MachineBindingIndex.Binding binding = index.bindBlockEntity(entity.proxy, handler, null);
        Grid grid = new Grid(UUID.randomUUID());
        EnergyMachineManager.MachineTransferSlot slot = new EnergyMachineManager.MachineTransferSlot(
            entity.proxy, entity.pos.toLong(), new EnergyMachineManager.Interaction());
        slot.attachGridTracking(grid, grid.getInteraction());
        slot.configure(grid, binding.account(), handler.bindingPolicy(), null, grid.getInteraction(),
            IEnergyHandler.EnergyType.RECEIVE, 0, EnergyHandlerRuntime.FailureContext.UNKNOWN,
            "legacy:0", entity.pos.toLong());
        Object2ObjectOpenHashMap<String, LongSet> observers = new Object2ObjectOpenHashMap<>();
        observers.put("legacy:0", new LongOpenHashSet(new long[] {0L}));

        Object2ObjectOpenHashMap<String, LongSet> warnings = new Object2ObjectOpenHashMap<>();
        for (int i = 0; i < 200; i++) {
            tick(() -> slot.prepareWarning(epoch, observers));
            slot.collectWarning(warnings, epoch, observers);
            assertTrue(warnings.isEmpty());
            assertEquals(IEnergyHandler.EnergyType.RECEIVE, handler.getType(null));
            assertTrue(binding.isActive());
        }
        assertEquals(CFNBlockEntityEx.SATURATED_ENERGY_THROTTLE_STAGE, entity.stage);
        tile.stored = 0;
        int waited = 0;
        while (warnings.isEmpty() && waited <= CFNBlockEntityEx.MAX_ENERGY_THROTTLE_TIMER) {
            tick(() -> slot.prepareWarning(epoch, observers));
            slot.collectWarning(warnings, epoch, observers);
            assertEquals(IEnergyHandler.EnergyType.RECEIVE, handler.getType(null));
            waited++;
        }
        assertFalse(warnings.isEmpty());
        assertEquals(0, entity.stage);
        tick(() -> assertEquals(400L, handler.canReceiveValue(null).asLongExact()));
        assertEquals(1, entity.binds);
        slot.detachGrid();
    }

    @Test
    void stableReceiverIsLazyAndRefreshesOnlyOncePerSelectedTick() {
        MekTile tile = new MekTile();
        MEKHandler handler = new MEKHandler();
        index.bindBlockEntity(new Entity(tile).proxy, handler, null);
        tile.faceChecks = 0;
        for (int i = 0; i < 1000; i++) tick(() -> {});
        assertEquals(0, tile.faceChecks, "unselected receivers must not probe faces");
        for (int i = 0; i < 200; i++) tick(() -> {
            for (int read = 0; read < 10; read++) handler.canReceiveValue(null).recycle();
        });
        assertEquals(200, tile.faceChecks, "selected receivers reuse the face for the whole tick");
    }

    @Test
    void discoversNewInputRoleWithoutLosingExistingOutput() {
        BidirectionalTile tile = new BidirectionalTile();
        tile.enabled = false;
        MEKHandler handler = new MEKHandler();
        index.bindBlockEntity(new Entity(tile).proxy, handler, null);
        assertEquals(IEnergyHandler.EnergyType.SEND, handler.getType(null));
        tile.faceChecks = 0;
        for (int i = 0; i < 1000; i++) tick(() -> {});
        assertTrue(tile.faceChecks <= 306, "missing role discovery must be bounded to once per 20 ticks");
        tile.enabled = true;
        for (int i = 0; i < 20; i++) tick(() -> {});
        assertEquals(IEnergyHandler.EnergyType.STORAGE, handler.getType(null));
    }

    @Test
    void itemChargingUsesTheSameJouleConversionAsBlocks() {
        net.minecraft.init.Bootstrap.register();
        Battery battery = new Battery();
        ItemStack stack = new ItemStack(battery);
        MEKHandler handler = new MEKHandler();
        handler.bindItem(stack, null);
        EnergyAmount budget = handler.canReceiveValue(null);
        assertEquals(40, budget.asLongExact());
        budget.recycle();
        EnergyAmount request = EnergyAmount.obtain(30);
        EnergyAmount accepted = handler.receiveEnergy(request, null);
        assertEquals(30, accepted.asLongExact());
        assertEquals(75.0, battery.stored);
        accepted.recycle(); request.recycle();
        request = EnergyAmount.obtain(30);
        accepted = handler.receiveEnergy(request, null);
        assertEquals(10, accepted.asLongExact());
        assertEquals(100.0, battery.stored);
        accepted.recycle(); request.recycle();
        handler.unbindItem();
    }

    @Test
    void roleDiscoverySurvivesTicksSkippedByBudgetBackoff() {
        BidirectionalTile tile = new BidirectionalTile();
        tile.enabled = false;
        MEKHandler handler = new MEKHandler();
        index.bindBlockEntity(new Entity(tile).proxy, handler, null);
        index.beginServerTick(1);
        index.endServerTick(1);
        tile.enabled = true;
        // A throttled binding may never be called on the exact periodic tick.
        index.beginServerTick(41);
        index.endServerTick(41);
        assertEquals(IEnergyHandler.EnergyType.STORAGE, handler.getType(null));
    }

    private static class Battery extends Item implements IEnergizedItem {
        double stored;
        public double getEnergy(ItemStack stack) { return stored; }
        public void setEnergy(ItemStack stack, double value) { stored = value; }
        public double getMaxEnergy(ItemStack stack) { return 1000; }
        public double getMaxTransfer(ItemStack stack) { return 100; }
        public boolean canReceive(ItemStack stack) { return true; }
        public boolean canSend(ItemStack stack) { return true; }
    }

    private static class BidirectionalTile extends MekTile implements IStrictEnergyOutputter {
        public boolean canOutputEnergy(EnumFacing facing) { return true; }
        public double pullEnergy(EnumFacing facing, double amount, boolean simulate) { return 0; }
    }

    private static class MekTile extends TileEntity implements IStrictEnergyStorage, IStrictEnergyAcceptor {
        boolean enabled = true;
        boolean accepting = true;
        double stored;
        int faceChecks;
        public double getEnergy() { return stored; }
        public void setEnergy(double amount) { stored = amount; }
        public double getMaxEnergy() { return 1000.0; }
        public boolean canReceiveEnergy(EnumFacing facing) { faceChecks++; return enabled; }
        public double acceptEnergy(EnumFacing facing, double amount, boolean simulate) {
            double accepted = enabled && accepting ? Math.min(getMaxEnergy() - stored, amount) : 0.0;
            if (!simulate) stored += accepted;
            return accepted;
        }
    }

    private static class Entity {
        int timer, stage, binds, managerFallbacks;
        net.minecraft.world.World world;
        final BlockPos pos = new BlockPos(1, 64, 1);
        final CFNBlockEntityEx proxy;
        Entity(TileEntity tile) {
            proxy = (CFNBlockEntityEx) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {CFNBlockEntityEx.class}, (p, method, args) -> {
                    switch (method.getName()) {
                        case "cfn_getWorld": return world;
                        case "cfn_removeMachineHandlerRuntime": return null;
                        case "cfn_getMachineHandlerRuntime": managerFallbacks++; return null;
                        case "cfn_getDimensionId": case "cfn_getEnergyPriority": return 0;
                        case "cfn_getBlockPos": return pos;
                        case "cfn_getTypeName": return "AuditMekReceiver";
                        case "cfn_bindEnergyHandler":
                            binds++;
                            ((IEnergyHandler) args[0]).bindBlockEntity(tile, (HandlerInvalidationSink) args[1]); return null;
                        case "cfn_getEnergyThrottleTimer": return timer;
                        case "cfn_setEnergyThrottleTimer": timer = (int) args[0]; return null;
                        case "cfn_getEnergyLastThrottleTimer": return stage;
                        case "cfn_setEnergyLastThrottleTimer": stage = (int) args[0]; return null;
                        case "cfn_isRemoved": case "cfn_isEnergyBlacklisted": case "cfn_isSupplyBlacklisted": return false;
                        case "hashCode": return System.identityHashCode(p);
                        case "equals": return p == args[0];
                        case "toString": return "AuditMekReceiver";
                        default: throw new AssertionError(method.getName());
                    }
                });
        }
    }
}
