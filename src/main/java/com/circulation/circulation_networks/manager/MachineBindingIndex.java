package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.CFNBlockEntityEx;
import com.circulation.circulation_networks.api.HandlerTickResult;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.api.IGrid;
import com.circulation.circulation_networks.api.node.IHubNode;
import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.network.nodes.HubNode;
import com.circulation.circulation_networks.registry.RegistryEnergyHandler;
//? if <1.20
import com.github.bsideup.jabel.Desugar;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
//~ mc_imports
import net.minecraft.item.ItemStack;
//~ if >=1.20 'net.minecraft.util.math.BlockPos' -> 'net.minecraft.core.BlockPos' {
import net.minecraft.util.math.BlockPos;
//~}
//~ if >=1.20 'net.minecraft.world.World' -> 'net.minecraft.world.level.Level' {
import net.minecraft.world.World;
//~}
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Server-thread owner of durable handler bindings, shared transfer accounts,
 * and topology-driven machine routes. All mutations are server-thread confined.
 */
public final class MachineBindingIndex {

    private static final int MIN_SATURATED_THROTTLE_TIMER =
        CFNBlockEntityEx.SATURATED_ENERGY_THROTTLE_STAGE * 3 / 4;
    private static final int SATURATED_THROTTLE_TIMER_COUNT =
        CFNBlockEntityEx.MAX_ENERGY_THROTTLE_TIMER - MIN_SATURATED_THROTTLE_TIMER + 1;

    /** Shared server lifecycle index. */
    public static final MachineBindingIndex INSTANCE = new MachineBindingIndex();

    private MachineBindingIndex() {
    }

    /**
     * Lifecycle callbacks for topology adapters and machine routes. Two route
     * implementations exist, so this remains a real behavior contract.
     */
    interface Route {
        void close();

        void revoke();

        void refresh();

        void updateMachinePriority(int priority);
    }

    /** Handle used by a topology adapter to remove its own route. */
    interface RouteHandle {
        INode node();

        void unregister();
    }

    private final Reference2ObjectOpenHashMap<CFNBlockEntityEx, Binding> handlerBindings = new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectOpenHashMap<INode, NodeRecord> nodes = new Reference2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<Long2ObjectOpenHashMap<ReferenceOpenHashSet<NodeRecord>>> nodesByPosition = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<Long2ObjectOpenHashMap<ReferenceOpenHashSet<NodeRecord>>> nodesByChunk = new Int2ObjectOpenHashMap<>();
    private final Reference2ObjectOpenHashMap<IGrid, ReferenceOpenHashSet<NodeRecord>> nodesByGrid = new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectOpenHashMap<Route, RouteEntry> routes = new Reference2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<Long2ObjectOpenHashMap<MachineRouteEntry>> machineRoutes = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<Long2ObjectOpenHashMap<ObjectArrayList<MachineRouteEntry>>> machineRoutesByChunk =
        new Int2ObjectOpenHashMap<>();
    private final Reference2ObjectOpenHashMap<IGrid, PendingChannelBinding> channelBindings = new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectOpenHashMap<IEnergyHandler, BackendLease> sharedBackendLeases = new Reference2ObjectOpenHashMap<>();
    private final ObjectArrayList<Binding> allBindings = new ObjectArrayList<>();
    private final ObjectArrayList<RouteEntry> allRoutes = new ObjectArrayList<>();
    private final ObjectArrayList<MachineRouteEntry> allMachineRoutes = new ObjectArrayList<>();
    private final ObjectArrayList<RouteEntry> pendingRoutes = new ObjectArrayList<>();
    private final ObjectArrayList<RouteEntry> deferredRouteRetries = new ObjectArrayList<>();
    private final ObjectArrayList<MachineRouteEntry> pendingMachineRoutes = new ObjectArrayList<>();
    private final ObjectArrayList<MachineRouteEntry> deferredMachineRouteRetries = new ObjectArrayList<>();
    private final ObjectArrayList<PendingChannelBinding> pendingChannelBindings = new ObjectArrayList<>();
    private final ObjectArrayList<Binding> pendingRebinds = new ObjectArrayList<>();
    private final ObjectArrayList<Binding> pendingMappings = new ObjectArrayList<>();
    private final ObjectArrayList<Binding> beginBindings = new ObjectArrayList<>();
    private final ObjectArrayList<Binding> endBindings = new ObjectArrayList<>();
    private final ObjectArrayList<BackendLease> transferAccounts = new ObjectArrayList<>();
    private final ObjectArrayList<MachineTransferAccount> activeAccounts = new ObjectArrayList<>();
    private final ObjectArrayList<BackendLease> beginBackends = new ObjectArrayList<>();
    private final ObjectArrayList<BackendLease> endBackends = new ObjectArrayList<>();
    private final ObjectArrayList<BackendLease> pendingQuarantines = new ObjectArrayList<>();
    private final ObjectArrayList<BackendLease> pendingBackendClosures = new ObjectArrayList<>();
    private final ObjectArrayList<CFNBlockEntityEx> throttledBlockEntities = new ObjectArrayList<>();
    private final ObjectArrayList<ProviderCleanupDebt> terminalProviderCleanupDebts = new ObjectArrayList<>();
    private final LongOpenHashSet shielderChunkScratch = new LongOpenHashSet();
    private int topologyTransactionDepth;
    private boolean commitRequested;
    private boolean committing;
    private boolean tickActive;
    private boolean backendsBegun;
    private boolean stopping;
    private boolean providerCallbackActive;
    private boolean hasBegunTick;
    private long activeTickEpoch;
    private long lastBeginEpoch;
    private long structuralMutationCount;
    private long quarantineEnqueueCount;
    private long quarantineProcessedCount;
    private int throttleEntriesAtTickStart;
    private int saturatedThrottleCursor;
    private boolean throttleCountdownPending;
    Binding bindBlockEntity(CFNBlockEntityEx blockEntity,
                                   IEnergyHandler handler,
                                   @Nullable MappedEnergyHandlerProvider mappedProvider) {
        Objects.requireNonNull(blockEntity, "blockEntity");
        Objects.requireNonNull(handler, "handler");
        requireNoProviderCallback("bind a block entity");
        requireRunning();
        unbindBlockEntity(blockEntity);
        Binding binding = new Binding(this, blockEntity, handler, mappedProvider);
        handlerBindings.put(blockEntity, binding);
        binding.allIndex = allBindings.size();
        allBindings.add(binding);
        try {
            binding.bindInitial();
            return binding;
        } catch (RuntimeException | Error exception) {
            handlerBindings.remove(blockEntity);
            removeBinding(binding);
            try {
                binding.unbind();
            } catch (RuntimeException | Error cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
    }
    @Nullable Binding binding(CFNBlockEntityEx blockEntity) {
        return handlerBindings.get(Objects.requireNonNull(blockEntity, "blockEntity"));
    }
    public boolean unbindBlockEntity(CFNBlockEntityEx blockEntity) {
        Objects.requireNonNull(blockEntity, "blockEntity");
        requireNoProviderCallback("unbind a block entity");
        Binding binding = handlerBindings.remove(blockEntity);
        if (binding == null) {
            return false;
        }
        removeBinding(binding);
        binding.unbind();
        return true;
    }
    public void beginServerTick(long epoch) {
        if (tickActive) {
            throw new IllegalStateException("A server-tick binding window is already active for epoch " + activeTickEpoch);
        }
        if (hasBegunTick && epoch <= lastBeginEpoch) {
            throw new IllegalArgumentException(
                "Server-tick epoch must increase monotonically: previous " + lastBeginEpoch + ", received " + epoch
            );
        }
        if (topologyTransactionDepth != 0) {
            throw new IllegalStateException("Cannot begin a server tick while a topology transaction is open");
        }
        tickActive = true;
        backendsBegun = false;
        activeTickEpoch = epoch;
        hasBegunTick = true;
        lastBeginEpoch = epoch;
        try {
            rebindPendingBindings();
            throttleEntriesAtTickStart = throttledBlockEntities.size();
            throttleCountdownPending = true;
            for (int index = 0; index < beginBindings.size();) {
                Binding binding = beginBindings.get(index);
                binding.beginServerTick(epoch);
                if (binding.beginIndex == index) {
                    index++;
                }
            }
            drainPendingMappings(epoch);
            for (int index = 0; index < beginBackends.size();) {
                BackendLease lease = beginBackends.get(index);
                lease.beginServerTick(epoch);
                if (lease.beginIndex == index) {
                    index++;
                }
            }
            backendsBegun = true;
            retryPendingBackendClosures();
            commitPendingUpdates();
        } catch (RuntimeException exception) {
            throw rollbackBegunTick(epoch, exception);
        }
    }
    public void endServerTick(long epoch) {
        if (!tickActive || activeTickEpoch != epoch) {
            throw new IllegalStateException(
                "Server-tick binding epoch mismatch: expected " + (tickActive ? activeTickEpoch : "no active epoch")
                    + ", received " + epoch
            );
        }
        RuntimeException failure = null;
        try {
            for (int index = 0; index < endBindings.size();) {
                Binding binding = endBindings.get(index);
                try {
                    binding.endServerTick(epoch);
                } catch (RuntimeException exception) {
                    failure = aggregate(failure, exception);
                }
                if (binding.endIndex == index) {
                    index++;
                }
            }
            for (int index = 0; index < endBackends.size();) {
                BackendLease lease = endBackends.get(index);
                if (lease.activeEndEpoch != epoch) {
                    index++;
                    continue;
                }
                try {
                    lease.endServerTick(epoch);
                } catch (RuntimeException exception) {
                    failure = aggregate(failure, exception);
                } finally {
                    try {
                        applyReceiveCommitFeedback(lease, epoch);
                    } catch (RuntimeException exception) {
                        failure = aggregate(failure, exception);
                    }
                }
                if (lease.endIndex == index) {
                    index++;
                }
            }
            for (int index = 0; index < activeAccounts.size(); index++) {
                MachineTransferAccount account = activeAccounts.get(index);
                try {
                    if (account.isActive(epoch)) {
                        account.endEpoch(epoch);
                    }
                } catch (RuntimeException exception) {
                    failure = aggregate(failure, exception);
                }
            }
            failure = quarantineRequestedBackends(failure);
            retryPendingBackendClosures();
        } finally {
            advanceEnergyThrottleTimers();
            backendsBegun = false;
            activeAccounts.clear();
            tickActive = false;
            releaseDeferredRetries();
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static void applyReceiveCommitFeedback(BackendLease lease, long epoch) {
        if (!(lease.backend instanceof DeferredReceiveCommit feedback)) {
            return;
        }
        EnergyAmount rejected = feedback.drainRejectedReceive();
        if (rejected == null) {
            throw new IllegalStateException("Deferred receive backend returned null commit feedback");
        }
        try {
            if (lease.account.isActive(epoch)) {
                lease.account.settleDeferredReceive(rejected, epoch);
            } else if (rejected.isPositive()) {
                throw new IllegalStateException("Deferred receive backend rejected energy without an active account");
            }
        } finally {
            rejected.recycle();
        }
    }

    private RuntimeException rollbackBegunTick(long epoch, RuntimeException failure) {
        for (int index = activeAccounts.size() - 1; index >= 0; index--) {
            MachineTransferAccount account = activeAccounts.get(index);
            if (account.isActive(epoch)) {
                try {
                    account.endEpoch(epoch);
                } catch (RuntimeException exception) {
                    failure = aggregate(failure, exception);
                }
            }
        }
        for (int index = endBackends.size() - 1; index >= 0; index--) {
            BackendLease lease = endBackends.get(index);
            if (lease.activeEndEpoch == epoch) {
                try {
                    lease.endServerTick(epoch);
                } catch (RuntimeException exception) {
                    failure = aggregate(failure, exception);
                }
            }
        }
        for (int index = endBindings.size() - 1; index >= 0; index--) {
            Binding binding = endBindings.get(index);
            if (binding.activeEndEpoch == epoch) {
                try {
                    binding.endServerTick(epoch);
                } catch (RuntimeException exception) {
                    failure = aggregate(failure, exception);
                }
            }
        }
        backendsBegun = false;
        activeAccounts.clear();
        tickActive = false;
        releaseDeferredRetries();
        advanceEnergyThrottleTimers();
        return failure;
    }

    boolean isEnergyThrottled(CFNBlockEntityEx blockEntity) {
        return blockEntity.cfn_getEnergyThrottleTimer() != 0;
    }

    void markEnergyBudgetFailure(CFNBlockEntityEx blockEntity) {
        scheduleEnergyThrottleFailure(blockEntity, blockEntity.cfn_getEnergyLastThrottleTimer());
    }

    void beginEnergyRoleEvaluation(CFNBlockEntityEx blockEntity) {
        Binding binding = handlerBindings.get(blockEntity);
        if (binding != null) {
            binding.routeRetryPending = false;
        }
    }

    void markEnergyRoleFailure(CFNBlockEntityEx blockEntity) {
        Binding binding = handlerBindings.get(blockEntity);
        if (binding == null) {
            throw new IllegalStateException("Cannot throttle an invalid role without an indexed handler binding");
        }
        binding.routeRetryPending = true;
        markEnergyBudgetFailure(blockEntity);
    }

    void markEnergyRouteRetry(CFNBlockEntityEx blockEntity) {
        Binding binding = handlerBindings.get(blockEntity);
        if (binding != null) {
            binding.routeRetryPending = true;
        }
    }

    void markEnergyBudgetSuccess(CFNBlockEntityEx blockEntity) {
        if (blockEntity.cfn_getEnergyThrottleTimer() != 0) {
            throw new IllegalStateException("A throttled block entity reported a successful energy budget read");
        }
        blockEntity.cfn_setEnergyLastThrottleTimer(0);
    }

    private void markSharedEnergyBudgetFailure(BackendLease lease) {
        int previousStage = 0;
        for (int index = 0; index < lease.bindings.size(); index++) {
            previousStage = Math.max(
                previousStage, lease.bindings.get(index).blockEntity.cfn_getEnergyLastThrottleTimer()
            );
        }
        int nextStage = nextThrottleStage(previousStage);
        int nextTimer = previousStage == CFNBlockEntityEx.SATURATED_ENERGY_THROTTLE_STAGE
            ? nextSaturatedThrottleTimer()
            : nextStage;
        for (int index = 0; index < lease.bindings.size(); index++) {
            markEnergyThrottleFailure(lease.bindings.get(index).blockEntity, nextTimer, nextStage);
        }
    }

    private void scheduleEnergyThrottleFailure(CFNBlockEntityEx blockEntity, int previousStage) {
        int nextStage = nextThrottleStage(previousStage);
        int nextTimer = previousStage == CFNBlockEntityEx.SATURATED_ENERGY_THROTTLE_STAGE
            ? nextSaturatedThrottleTimer()
            : nextStage;
        markEnergyThrottleFailure(blockEntity, nextTimer, nextStage);
    }

    private void markEnergyThrottleFailure(CFNBlockEntityEx blockEntity, int nextTimer, int nextStage) {
        int currentTimer = blockEntity.cfn_getEnergyThrottleTimer();
        if (currentTimer == 0) {
            throttledBlockEntities.add(blockEntity);
        }
        blockEntity.cfn_setEnergyLastThrottleTimer(nextStage);
        blockEntity.cfn_setEnergyThrottleTimer(nextTimer);
    }

    private void clearEnergyThrottle(CFNBlockEntityEx blockEntity) {
        int foundIndex = -1;
        for (int index = 0; index < throttledBlockEntities.size(); index++) {
            if (throttledBlockEntities.get(index) != blockEntity) {
                continue;
            }
            if (foundIndex >= 0) {
                throw new IllegalStateException("Energy throttle list contains a duplicate block entity");
            }
            foundIndex = index;
        }
        if (foundIndex >= 0) {
            swapRemove(throttledBlockEntities, foundIndex);
        }
        blockEntity.cfn_setEnergyThrottleTimer(0);
        blockEntity.cfn_setEnergyLastThrottleTimer(0);
    }

    private void advanceEnergyThrottleTimers() {
        if (!throttleCountdownPending) {
            return;
        }
        throttleCountdownPending = false;
        int lastOriginalIndex = Math.min(throttleEntriesAtTickStart, throttledBlockEntities.size()) - 1;
        for (int index = lastOriginalIndex; index >= 0; index--) {
            CFNBlockEntityEx blockEntity = throttledBlockEntities.get(index);
            int timer = blockEntity.cfn_getEnergyThrottleTimer();
            if (timer <= 0) {
                throw new IllegalStateException("Energy throttle list contains a block entity without a timer");
            }
            timer--;
            blockEntity.cfn_setEnergyThrottleTimer(timer);
            if (timer == 0) {
                swapRemove(throttledBlockEntities, index);
                Binding binding = handlerBindings.get(blockEntity);
                if (binding != null && binding.routeRetryPending) {
                    binding.enqueueBlockEntityRouteRefresh();
                }
            }
        }
        throttleEntriesAtTickStart = 0;
    }

    private static int nextThrottleStage(int previousStage) {
        if (previousStage == 0) {
            return 1;
        }
        if (previousStage != 1 && previousStage != 2 && previousStage != 4 && previousStage != 8
            && previousStage != 16
            && previousStage != CFNBlockEntityEx.SATURATED_ENERGY_THROTTLE_STAGE) {
            throw new IllegalArgumentException("Invalid previous energy throttle stage: " + previousStage);
        }
        return Math.min(previousStage << 1, CFNBlockEntityEx.SATURATED_ENERGY_THROTTLE_STAGE);
    }

    private int nextSaturatedThrottleTimer() {
        int timer = MIN_SATURATED_THROTTLE_TIMER + saturatedThrottleCursor;
        saturatedThrottleCursor++;
        if (saturatedThrottleCursor == SATURATED_THROTTLE_TIMER_COUNT) {
            saturatedThrottleCursor = 0;
        }
        return timer;
    }

    /**
     * Registers a physical account on first use in the active tick. The
     * durable transfer-account index remains available for ownership and
     * shutdown only; stable ticks no longer open every account eagerly.
     */
    void activateAccount(MachineTransferAccount account, long epoch) {
        if (!tickActive || activeTickEpoch != epoch) {
            throw new IllegalStateException("Cannot activate a machine transfer account outside epoch " + epoch);
        }
        if (account.isActive(epoch)) {
            return;
        }
        account.beginEpoch(epoch);
        activeAccounts.add(account);
    }

    private RuntimeException quarantineRequestedBackends(@Nullable RuntimeException failure) {
        while (!pendingQuarantines.isEmpty()) {
            BackendLease lease = pendingQuarantines.remove(pendingQuarantines.size() - 1);
            lease.quarantineIndex = -1;
            if (!lease.quarantineRequested) {
                continue;
            }
            quarantineProcessedCount++;
            try {
                lease.suspendBindings(true);
            } catch (RuntimeException exception) {
                failure = aggregate(failure, exception);
            }
        }
        return failure;
    }

    private void enqueueQuarantine(BackendLease lease) {
        if (lease.quarantineIndex >= 0) {
            return;
        }
        lease.quarantineIndex = pendingQuarantines.size();
        pendingQuarantines.add(lease);
        quarantineEnqueueCount++;
    }

    private void removeQuarantine(BackendLease lease) {
        if (lease.quarantineIndex < 0) {
            lease.quarantineRequested = false;
            return;
        }
        if (lease.quarantineIndex >= pendingQuarantines.size()
            || pendingQuarantines.get(lease.quarantineIndex) != lease) {
            throw new IllegalStateException("Backend quarantine queue is inconsistent");
        }
        BackendLease moved = swapRemove(pendingQuarantines, lease.quarantineIndex);
        if (moved != null) {
            moved.quarantineIndex = lease.quarantineIndex;
        }
        lease.quarantineIndex = -1;
        lease.quarantineRequested = false;
    }

    private void enqueueBackendClosure(BackendLease lease) {
        lease.closePending = true;
        if (lease.closeIndex >= 0) {
            return;
        }
        lease.closeIndex = pendingBackendClosures.size();
        pendingBackendClosures.add(lease);
    }

    private void removeBackendClosure(BackendLease lease) {
        if (lease.closeIndex < 0) {
            lease.closePending = false;
            return;
        }
        if (lease.closeIndex >= pendingBackendClosures.size()
            || pendingBackendClosures.get(lease.closeIndex) != lease) {
            throw new IllegalStateException("Backend close queue is inconsistent");
        }
        BackendLease moved = swapRemove(pendingBackendClosures, lease.closeIndex);
        if (moved != null) {
            moved.closeIndex = lease.closeIndex;
        }
        lease.closeIndex = -1;
        lease.closePending = false;
    }

    private void retryPendingBackendClosures() {
        for (int index = pendingBackendClosures.size() - 1; index >= 0; index--) {
            BackendLease lease = pendingBackendClosures.get(index);
            if (!lease.isReadyForClosure()) {
                continue;
            }
            try {
                finishLastBackendRelease(lease);
            } catch (RuntimeException exception) {
                CirculationFlowNetworks.LOGGER.error(
                    "Deferred backend escrow close retry failed for {}", lease.backend.getClass().getName(), exception
                );
            }
        }
    }

    /**
     * Captures counters used by stable-tick regression tests. Production tick
     * processing never calls this method, so snapshot allocation occurs only
     * when a test explicitly samples the index.
     */
    @SuppressWarnings("unused")
    TickMetrics tickMetrics() {
        return new TickMetrics(
            pendingQuarantines.size(),
            quarantineEnqueueCount,
            quarantineProcessedCount,
            structuralMutationCount
        );
    }

    public @Nullable IEnergyHandler bindItem(ItemStack stack, @Nullable HubNode.HubMetadata hubMetadata) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        var manager = RegistryEnergyHandler.getEnergyManager(stack);
        if (manager == null) {
            return null;
        }
        IEnergyHandler handler = manager.newItemInstance();
        handler.bindItem(stack, hubMetadata);
        return handler;
    }
    RouteHandle registerRoute(INode node, Route route) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(route, "route");
        requireRunning();
        if (routes.containsKey(route)) {
            throw new IllegalStateException("Route is already registered");
        }
        NodeRecord nodeRecord = nodeRecord(node);
        RouteEntry routeEntry = new RouteEntry(this, nodeRecord, route);
        nodeRecord.routes.add(routeEntry);
        routes.put(route, routeEntry);
        routeEntry.allIndex = allRoutes.size();
        allRoutes.add(routeEntry);
        if (nodeRecord.active) {
            enqueue(routeEntry);
        }
        return routeEntry;
    }
    void registerMachineRoute(int dimensionId, long machinePosition, Route route) {
        Objects.requireNonNull(route, "route");
        requireRunning();
        Long2ObjectOpenHashMap<MachineRouteEntry> dimensionRoutes = machineRoutes.computeIfAbsent(
            dimensionId,
            ignored -> new Long2ObjectOpenHashMap<>()
        );
        if (dimensionRoutes.containsKey(machinePosition)) {
            throw new IllegalStateException("A machine route is already registered at this position");
        }
        MachineRouteEntry entry = new MachineRouteEntry(this, dimensionId, machinePosition, route);
        dimensionRoutes.put(machinePosition, entry);
        entry.allIndex = allMachineRoutes.size();
        allMachineRoutes.add(entry);
        try {
            indexMachineRouteChunk(entry);
            structuralMutationCount++;
        } catch (RuntimeException | Error exception) {
            removeMachineRoute(entry);
            dimensionRoutes.remove(machinePosition);
            if (dimensionRoutes.isEmpty()) {
                machineRoutes.remove(dimensionId);
            }
            throw exception;
        }
        enqueue(entry);
    }
    void unregisterMachineRoute(int dimensionId, long machinePosition, Route route) {
        Objects.requireNonNull(route, "route");
        Long2ObjectOpenHashMap<MachineRouteEntry> dimensionRoutes = machineRoutes.get(dimensionId);
        if (dimensionRoutes == null) {
            return;
        }
        MachineRouteEntry entry = dimensionRoutes.get(machinePosition);
        if (entry == null) {
            return;
        }
        if (entry.route != route) {
            throw new IllegalStateException("A different machine route is registered at this position");
        }
        entry.unregister();
        dimensionRoutes.remove(machinePosition);
        if (dimensionRoutes.isEmpty()) {
            machineRoutes.remove(dimensionId);
        }
        removeMachineRoute(entry);
        unindexMachineRouteChunk(entry);
        structuralMutationCount++;
    }
    public void beginTopologyTransaction() {
        if (topologyTransactionDepth == Integer.MAX_VALUE) {
            throw new IllegalStateException("Topology transaction nesting overflow");
        }
        topologyTransactionDepth++;
    }
    public void endTopologyTransaction() {
        if (topologyTransactionDepth == 0) {
            throw new IllegalStateException("No topology transaction is open");
        }
        topologyTransactionDepth--;
        if (topologyTransactionDepth == 0 && commitRequested) {
            commitPendingUpdates();
        }
    }
    public void onNodeGridChanged(INode node, @Nullable IGrid oldGrid, @Nullable IGrid newGrid) {
        Objects.requireNonNull(node, "node");
        NodeRecord nodeRecord = nodes.get(node);
        boolean wasTracked = nodeRecord != null;
        if (nodeRecord == null) {
            nodeRecord = nodeRecord(node);
        }
        if (wasTracked && nodeRecord.grid != oldGrid) {
            throw new IllegalStateException("Node grid change does not match the registered old grid");
        }
        updateGrid(nodeRecord, newGrid);
        if (node instanceof IHubNode hub) {
            if (oldGrid != null) {
                queueChannelBinding(oldGrid, HubNode.EMPTY);
            }
            if (newGrid != null) {
                queueChannelBinding(newGrid, nodeRecord.active ? hub.getChannelId() : HubNode.EMPTY);
            }
        }
        enqueueNode(nodeRecord);
        enqueueMachineRoute(nodeRecord.dimensionId, nodeRecord.packedPosition);
    }
    public void updateMachinePriority(int dimensionId, long packedPosition, int priority) {
        MachineRouteEntry machineRoute = machineRoute(dimensionId, packedPosition);
        if (machineRoute != null) {
            machineRoute.queuePriority(priority);
        }
    }
    public void onNodeActiveChanged(INode node, boolean oldActive, boolean newActive, @Nullable IGrid grid) {
        Objects.requireNonNull(node, "node");
        NodeRecord nodeRecord = nodes.get(node);
        boolean wasTracked = nodeRecord != null;
        if (nodeRecord == null) {
            nodeRecord = nodeRecord(node);
        }
        updateGrid(nodeRecord, grid);
        if (wasTracked && nodeRecord.active != oldActive) {
            throw new IllegalStateException("Node activity change does not match the registered old state");
        }
        nodeRecord.active = newActive;
        if (node instanceof IHubNode hub && grid != null) {
            queueChannelBinding(grid, newActive ? hub.getChannelId() : HubNode.EMPTY);
        }
        enqueueNode(nodeRecord);
        enqueueMachineRoute(nodeRecord.dimensionId, nodeRecord.packedPosition);
    }
    public void onHubChannelBindingChanged(IHubNode hub, UUID oldChannelId, UUID newChannelId) {
        Objects.requireNonNull(hub, "hub");
        Objects.requireNonNull(oldChannelId, "oldChannelId");
        Objects.requireNonNull(newChannelId, "newChannelId");
        if (oldChannelId.equals(newChannelId)) {
            return;
        }
        IGrid grid = hub.getGrid();
        if (grid != null) {
            NodeRecord record = nodeRecord(hub);
            queueChannelBinding(grid, record.active ? newChannelId : HubNode.EMPTY);
        }
        enqueueNode(nodeRecord(hub));
    }
    public void onEnergyTypeOverrideChanged(int dimensionId,
                                            long packedPosition,
                                            @Nullable IEnergyHandler.EnergyType oldType,
                                            @Nullable IEnergyHandler.EnergyType newType) {
        if (oldType == newType) {
            return;
        }
        enqueuePosition(dimensionId, packedPosition);
    }
    public void onEnergyTypeOverridesLoaded(EnergyTypeOverrideManager overrideManager) {
        Objects.requireNonNull(overrideManager, "overrideManager");
        for (int index = 0; index < allRoutes.size(); index++) {
            enqueue(allRoutes.get(index));
        }
        for (int index = 0; index < allMachineRoutes.size(); index++) {
            enqueue(allMachineRoutes.get(index));
        }
    }
    public void onShielderCoverageChanged(int dimensionId, LongSet added, LongSet removed) {
        Objects.requireNonNull(added, "added");
        Objects.requireNonNull(removed, "removed");
        enqueuePositions(dimensionId, added);
        enqueuePositions(dimensionId, removed);
        shielderChunkScratch.clear();
        try {
            collectPositionChunks(added, shielderChunkScratch);
            collectPositionChunks(removed, shielderChunkScratch);
            for (long chunk : shielderChunkScratch) {
                enqueueMachineRoutesForChunk(dimensionId, chunk);
            }
        } finally {
            shielderChunkScratch.clear();
        }
    }
    public void commitPendingUpdates() {
        if (topologyTransactionDepth != 0) {
            commitRequested = true;
            return;
        }
        if (committing) {
            commitRequested = true;
            return;
        }
        if (!commitRequested && pendingChannelBindings.isEmpty()
            && pendingRoutes.isEmpty() && pendingMachineRoutes.isEmpty()) {
            return;
        }
        committing = true;
        try {
            do {
                commitRequested = false;
                commitPendingChannelBindings();
                drainRoutes();
                drainMachineRoutes();
            } while (commitRequested || !pendingChannelBindings.isEmpty()
                || !pendingRoutes.isEmpty() || !pendingMachineRoutes.isEmpty());
        } finally {
            committing = false;
        }
    }
    public void onServerStop() {
        requireNoProviderCallback("stop the machine binding index");
        if (stopping) {
            throw new IllegalStateException("Machine binding index is already stopping");
        }
        stopping = true;
        retryPendingBackendClosures();
        ObjectArrayList<RouteEntry> routeSnapshot = new ObjectArrayList<>(allRoutes);
        ObjectArrayList<MachineRouteEntry> machineRouteSnapshot = new ObjectArrayList<>(allMachineRoutes);
        ObjectArrayList<Binding> bindingSnapshot = new ObjectArrayList<>(allBindings);

        routes.clear();
        machineRoutes.clear();
        machineRoutesByChunk.clear();
        handlerBindings.clear();
        allRoutes.clear();
        allMachineRoutes.clear();
        allBindings.clear();
        pendingRoutes.clear();
        deferredRouteRetries.clear();
        pendingMachineRoutes.clear();
        deferredMachineRouteRetries.clear();
        pendingChannelBindings.clear();
        pendingRebinds.clear();
        throttledBlockEntities.clear();
        for (int index = 0; index < pendingQuarantines.size(); index++) {
            pendingQuarantines.get(index).quarantineIndex = -1;
        }
        pendingQuarantines.clear();
        shielderChunkScratch.clear();
        channelBindings.clear();
        nodes.clear();
        nodesByPosition.clear();
        nodesByChunk.clear();
        nodesByGrid.clear();

        RuntimeException failure = null;
        Error fatal = null;
        try {
        for (int index = 0; index < routeSnapshot.size(); index++) {
            try {
                routeSnapshot.get(index).shutdown();
            } catch (RuntimeException exception) {
                failure = aggregate(failure, exception);
            } catch (Error error) {
                fatal = aggregate(fatal, error);
            }
        }
        for (int index = 0; index < machineRouteSnapshot.size(); index++) {
            try {
                machineRouteSnapshot.get(index).shutdown();
            } catch (RuntimeException exception) {
                failure = aggregate(failure, exception);
            } catch (Error error) {
                fatal = aggregate(fatal, error);
            }
        }
        for (int index = 0; index < bindingSnapshot.size(); index++) {
            try {
                bindingSnapshot.get(index).unbind();
            } catch (RuntimeException exception) {
                failure = aggregate(failure, exception);
            } catch (Error error) {
                fatal = aggregate(fatal, error);
            }
        }
        retryPendingBackendClosures();
        if (!sharedBackendLeases.isEmpty()) {
            failure = aggregate(failure, new IllegalStateException(
                "Shared backend leases retain physical escrow after handler unbind"
            ));
        }
        if (!pendingBackendClosures.isEmpty()) {
            failure = aggregate(failure, new IllegalStateException(
                "Physical extraction escrow remains owned by " + pendingBackendClosures.size()
                    + " backend lease(s) after server stop"
            ));
        }
        try {
            LocalParticipantRoutingIndex.INSTANCE.onServerStop();
        } catch (RuntimeException exception) {
            failure = aggregate(failure, exception);
        } catch (Error error) {
            fatal = aggregate(fatal, error);
        }
        try {
            ChannelParticipantIndex.INSTANCE.onServerStop();
        } catch (RuntimeException exception) {
            failure = aggregate(failure, exception);
        } catch (Error error) {
            fatal = aggregate(fatal, error);
        }
        } finally {
            for (int index = 0; index < terminalProviderCleanupDebts.size(); index++) {
                ProviderCleanupDebt debt = terminalProviderCleanupDebts.get(index);
                CirculationFlowNetworks.LOGGER.error(
                    "Terminating ambiguous at-most-once provider cleanup debt for provider {}, endpoint {}, backend {}: {}",
                    debt.reference.provider.getClass().getName(), debt.reference.boundHandler.getClass().getName(),
                    debt.reference.sharedBackend.getClass().getName(), debt.operation, debt.failure
                );
            }
            terminalProviderCleanupDebts.clear();
            beginBindings.clear();
            endBindings.clear();
            pendingMappings.clear();
            if (pendingBackendClosures.isEmpty()) {
                sharedBackendLeases.clear();
                transferAccounts.clear();
                beginBackends.clear();
                endBackends.clear();
            }
            routeSnapshot.clear();
            machineRouteSnapshot.clear();
            bindingSnapshot.clear();
            topologyTransactionDepth = 0;
            commitRequested = false;
            committing = false;
            tickActive = false;
            backendsBegun = false;
            activeAccounts.clear();
            hasBegunTick = false;
            activeTickEpoch = 0L;
            lastBeginEpoch = 0L;
            structuralMutationCount = 0L;
            quarantineEnqueueCount = 0L;
            quarantineProcessedCount = 0L;
            throttleEntriesAtTickStart = 0;
            saturatedThrottleCursor = 0;
            throttleCountdownPending = false;
            providerCallbackActive = false;
            stopping = false;
        }
        if (fatal != null) {
            if (failure != null) {
                fatal.addSuppressed(failure);
            }
            throw fatal;
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static RuntimeException aggregate(@Nullable RuntimeException aggregate, RuntimeException exception) {
        if (aggregate == null) {
            return exception;
        }
        aggregate.addSuppressed(exception);
        return aggregate;
    }

    private static Error aggregate(@Nullable Error aggregate, Error error) {
        if (aggregate == null) {
            return error;
        }
        aggregate.addSuppressed(error);
        return aggregate;
    }

    //? if <1.20
    @Desugar
    private record ProviderReference(MappedEnergyHandlerProvider provider,
                                     IEnergyHandler boundHandler,
                                     IEnergyHandler sharedBackend) {
        private ProviderReference {
            Objects.requireNonNull(provider, "provider");
            Objects.requireNonNull(boundHandler, "boundHandler");
            Objects.requireNonNull(sharedBackend, "sharedBackend");
        }
    }

    //? if <1.20
    @Desugar
    private record ProviderCleanupDebt(ProviderReference reference, String operation, Throwable failure) {
        private ProviderCleanupDebt {
            Objects.requireNonNull(reference, "reference");
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(failure, "failure");
        }
    }

    //? if <1.20
    @Desugar
    private record SharedBackendAcquisition(BackendLease lease,
                                            int referenceIndex,
                                            boolean previousReferenceConsumed,
                                            @Nullable ProviderReference displacedProviderReference) {
        private SharedBackendAcquisition {
            Objects.requireNonNull(lease, "lease");
        }
    }

    private NodeRecord nodeRecord(INode node) {
        NodeRecord nodeRecord = nodes.get(node);
        if (nodeRecord != null) {
            return nodeRecord;
        }
        BlockPos position = node.getPos();
        //~ if >=1.20 '.toLong()' -> '.asLong()' {
        nodeRecord = new NodeRecord(node, node.getDimensionId(), position.toLong(), position.getX() >> 4,
            //~}
            position.getZ() >> 4, node.isActive(), node.getGrid());
        nodes.put(node, nodeRecord);
        addNodePosition(nodeRecord);
        addNodeChunk(nodeRecord);
        addNodeGrid(nodeRecord);
        return nodeRecord;
    }

    private void updateGrid(NodeRecord nodeRecord, @Nullable IGrid newGrid) {
        if (nodeRecord.grid == newGrid) {
            return;
        }
        removeNodeGrid(nodeRecord);
        nodeRecord.grid = newGrid;
        addNodeGrid(nodeRecord);
    }

    private void addNodePosition(NodeRecord nodeRecord) {
        nodesByPosition.computeIfAbsent(nodeRecord.dimensionId, ignored -> new Long2ObjectOpenHashMap<>())
            .computeIfAbsent(nodeRecord.packedPosition, ignored -> new ReferenceOpenHashSet<>())
            .add(nodeRecord);
    }

    private void addNodeChunk(NodeRecord nodeRecord) {
        nodesByChunk.computeIfAbsent(nodeRecord.dimensionId, ignored -> new Long2ObjectOpenHashMap<>())
            .computeIfAbsent(chunkKey(nodeRecord.chunkX, nodeRecord.chunkZ), ignored -> new ReferenceOpenHashSet<>())
            .add(nodeRecord);
    }

    private void addNodeGrid(NodeRecord nodeRecord) {
        if (nodeRecord.grid != null) {
            nodesByGrid.computeIfAbsent(nodeRecord.grid, ignored -> new ReferenceOpenHashSet<>()).add(nodeRecord);
        }
    }

    private void removeNodeGrid(NodeRecord nodeRecord) {
        if (nodeRecord.grid == null) {
            return;
        }
        ReferenceOpenHashSet<NodeRecord> gridNodes = nodesByGrid.get(nodeRecord.grid);
        if (gridNodes == null) {
            throw new IllegalStateException("Node grid index is inconsistent");
        }
        gridNodes.remove(nodeRecord);
        if (gridNodes.isEmpty()) {
            nodesByGrid.remove(nodeRecord.grid);
        }
    }

    private void enqueuePosition(int dimensionId, long packedPosition) {
        Long2ObjectOpenHashMap<ReferenceOpenHashSet<NodeRecord>> dimensionNodes = nodesByPosition.get(dimensionId);
        if (dimensionNodes != null) {
            ReferenceOpenHashSet<NodeRecord> positionNodes = dimensionNodes.get(packedPosition);
            if (positionNodes != null) {
                for (NodeRecord nodeRecord : positionNodes) {
                    enqueueNode(nodeRecord);
                }
            }
        }
        MachineRouteEntry machineRoute = machineRoute(dimensionId, packedPosition);
        if (machineRoute != null) {
            enqueue(machineRoute);
        }
    }

    private void enqueuePositions(int dimensionId, LongSet positions) {
        for (long position : positions) {
            enqueuePosition(dimensionId, position);
        }
    }

    private static void collectPositionChunks(LongSet positions, LongSet chunks) {
        for (long positionLong : positions) {
            //~ if >=1.20 '.fromLong(' -> '.of(' {
            BlockPos position = BlockPos.fromLong(positionLong);
            //~}
            chunks.add(chunkKey(position.getX() >> 4, position.getZ() >> 4));
        }
    }

    private void enqueueNode(NodeRecord nodeRecord) {
        for (RouteEntry entry : nodeRecord.routes) {
            enqueue(entry);
        }
    }

    private void enqueue(RouteEntry entry) {
        if (entry.registered) {
            entry.refreshQueued = true;
            if (!entry.retryDeferred && !entry.pendingQueued) {
                entry.pendingQueued = true;
                pendingRoutes.add(entry);
            }
        }
    }

    private void enqueue(MachineRouteEntry entry) {
        if (entry.registered) {
            entry.refreshQueued = true;
            if (!entry.retryDeferred && !entry.pendingQueued) {
                entry.pendingQueued = true;
                pendingMachineRoutes.add(entry);
            }
        }
    }

    private void enqueueMachineRoute(int dimensionId, long packedPosition) {
        MachineRouteEntry entry = machineRoute(dimensionId, packedPosition);
        if (entry != null) {
            enqueue(entry);
        }
    }

    private void enqueueMachineRoutesForChunk(int dimensionId, long chunk) {
        Long2ObjectOpenHashMap<ObjectArrayList<MachineRouteEntry>> dimensionChunks =
            machineRoutesByChunk.get(dimensionId);
        ObjectArrayList<MachineRouteEntry> entries = dimensionChunks == null ? null : dimensionChunks.get(chunk);
        if (entries == null) {
            return;
        }
        for (int index = 0; index < entries.size(); index++) {
            MachineRouteEntry entry = entries.get(index);
            enqueue(entry);
        }
    }

    @Nullable
    private MachineRouteEntry machineRoute(int dimensionId, long machinePosition) {
        Long2ObjectOpenHashMap<MachineRouteEntry> dimensionRoutes = machineRoutes.get(dimensionId);
        return dimensionRoutes == null ? null : dimensionRoutes.get(machinePosition);
    }

    private void indexMachineRouteChunk(MachineRouteEntry entry) {
        if (entry.chunkIndex >= 0) {
            throw new IllegalStateException("Machine route already has chunk membership");
        }
        Long2ObjectOpenHashMap<ObjectArrayList<MachineRouteEntry>> dimensionChunks =
            machineRoutesByChunk.computeIfAbsent(entry.dimensionId, ignored -> new Long2ObjectOpenHashMap<>());
        ObjectArrayList<MachineRouteEntry> chunkEntries =
            dimensionChunks.computeIfAbsent(entry.chunk, ignored -> new ObjectArrayList<>());
        entry.chunkIndex = chunkEntries.size();
        chunkEntries.add(entry);
    }

    private void unindexMachineRouteChunk(MachineRouteEntry entry) {
        if (entry.chunkIndex < 0) {
            return;
        }
        Long2ObjectOpenHashMap<ObjectArrayList<MachineRouteEntry>> dimensionChunks =
            machineRoutesByChunk.get(entry.dimensionId);
        ObjectArrayList<MachineRouteEntry> chunkEntries =
            dimensionChunks == null ? null : dimensionChunks.get(entry.chunk);
        if (chunkEntries == null || entry.chunkIndex >= chunkEntries.size()
            || chunkEntries.get(entry.chunkIndex) != entry) {
            throw new IllegalStateException("Machine route chunk index is inconsistent");
        }
        MachineRouteEntry moved = swapRemove(chunkEntries, entry.chunkIndex);
        if (moved != null) {
            moved.chunkIndex = entry.chunkIndex;
        }
        entry.chunkIndex = -1;
        if (chunkEntries.isEmpty()) {
            dimensionChunks.remove(entry.chunk);
            if (dimensionChunks.isEmpty()) {
                machineRoutesByChunk.remove(entry.dimensionId);
            }
        }
    }

    private void rebindPendingBindings() {
        while (!pendingRebinds.isEmpty()) {
            Binding binding = pendingRebinds.remove(pendingRebinds.size() - 1);
            binding.rebindQueued = false;
            binding.rebindBeforeTick();
        }
    }

    static final class TickMetrics {
        final int pendingQuarantines;
        final long quarantineEnqueues;
        final long quarantineProcessed;
        final long structuralMutations;

        private TickMetrics(int pendingQuarantines,
                            long quarantineEnqueues,
                            long quarantineProcessed,
                            long structuralMutations) {
            this.pendingQuarantines = pendingQuarantines;
            this.quarantineEnqueues = quarantineEnqueues;
            this.quarantineProcessed = quarantineProcessed;
            this.structuralMutations = structuralMutations;
        }
    }

    private void drainRoutes() {
        for (int index = 0; index < pendingRoutes.size(); index++) {
            RouteEntry entry = pendingRoutes.get(index);
            entry.pendingQueued = false;
            commitRoute(entry);
        }
        pendingRoutes.clear();
    }

    private void drainMachineRoutes() {
        for (int index = 0; index < pendingMachineRoutes.size(); index++) {
            MachineRouteEntry entry = pendingMachineRoutes.get(index);
            entry.pendingQueued = false;
            commitMachineRoute(entry);
        }
        pendingMachineRoutes.clear();
    }

    private void commitPendingChannelBindings() {
        for (int index = 0; index < pendingChannelBindings.size(); index++) {
            PendingChannelBinding entry = pendingChannelBindings.get(index);
            entry.queued = false;
            IGrid grid = entry.grid;
            UUID desiredChannelId = entry.desiredChannelId;
            UUID currentChannelId = grid.getParticipantIndex().channelId();
            ChannelParticipantIndex.INSTANCE.migrateGrid(
                grid,
                currentChannelId == null ? HubNode.EMPTY : currentChannelId,
                desiredChannelId
            );
        }
        pendingChannelBindings.clear();
    }

    private void commitRoute(RouteEntry entry) {
        if (!entry.registered || entry.retryDeferred) {
            return;
        }
        if (!entry.node.active) {
            try {
                entry.deactivate();
            } catch (RuntimeException exception) {
                handleRouteFailure(entry, "revoke", exception);
            }
            return;
        }
        if (entry.refreshQueued) {
            entry.refreshQueued = false;
            try {
                entry.route.refresh();
                entry.routed = true;
            } catch (RuntimeException exception) {
                entry.refreshQueued = true;
                handleRouteFailure(entry, "refresh", exception);
            }
        }
    }

    private void commitMachineRoute(MachineRouteEntry entry) {
        if (!entry.registered || entry.retryDeferred) {
            return;
        }
        if (entry.priorityQueued) {
            entry.priorityQueued = false;
            try {
                entry.route.updateMachinePriority(entry.pendingPriority);
            } catch (RuntimeException exception) {
                entry.priorityQueued = true;
                handleMachineRouteFailure(entry, "priority update", exception);
                return;
            }
        }
        if (entry.refreshQueued) {
            entry.refreshQueued = false;
            try {
                entry.route.refresh();
                entry.routed = true;
            } catch (RuntimeException exception) {
                entry.refreshQueued = true;
                handleMachineRouteFailure(entry, "refresh", exception);
            }
        }
    }

    private void handleRouteFailure(RouteEntry entry, String operation, RuntimeException exception) {
        CirculationFlowNetworks.LOGGER.error(
            "Node route {} failed during {} at dimension {} position {}",
            entry.route.getClass().getName(), operation, entry.node.dimensionId,
            EnergyHandlerRuntime.formatPosition(entry.node.packedPosition), exception
        );
        deferRetry(entry);
        try {
            entry.deactivate();
        } catch (RuntimeException revokeException) {
            CirculationFlowNetworks.LOGGER.error(
                "Node route {} failed during recovery revoke at dimension {} position {}",
                entry.route.getClass().getName(), entry.node.dimensionId,
                EnergyHandlerRuntime.formatPosition(entry.node.packedPosition), revokeException
            );
        }
    }

    private void handleMachineRouteFailure(MachineRouteEntry entry,
                                           String operation,
                                           RuntimeException exception) {
        CirculationFlowNetworks.LOGGER.error(
            "Machine route {} failed during {} at dimension {} position {}",
            entry.route.getClass().getName(), operation, entry.dimensionId,
            EnergyHandlerRuntime.formatPosition(entry.machinePosition), exception
        );
        deferRetry(entry);
        try {
            entry.deactivate();
        } catch (RuntimeException revokeException) {
            CirculationFlowNetworks.LOGGER.error(
                "Machine route {} failed during recovery revoke at dimension {} position {}",
                entry.route.getClass().getName(), entry.dimensionId,
                EnergyHandlerRuntime.formatPosition(entry.machinePosition), revokeException
            );
        }
    }

    private void deferRetry(RouteEntry entry) {
        if (!entry.retryDeferred) {
            entry.retryDeferred = true;
            deferredRouteRetries.add(entry);
        }
    }

    private void deferRetry(MachineRouteEntry entry) {
        if (!entry.retryDeferred) {
            entry.retryDeferred = true;
            deferredMachineRouteRetries.add(entry);
        }
    }

    private void releaseDeferredRetries() {
        for (int index = 0; index < deferredRouteRetries.size(); index++) {
            RouteEntry entry = deferredRouteRetries.get(index);
            entry.retryDeferred = false;
            if (entry.registered) {
                enqueue(entry);
            }
        }
        deferredRouteRetries.clear();
        for (int index = 0; index < deferredMachineRouteRetries.size(); index++) {
            MachineRouteEntry entry = deferredMachineRouteRetries.get(index);
            entry.retryDeferred = false;
            if (entry.registered) {
                enqueue(entry);
            }
        }
        deferredMachineRouteRetries.clear();
    }

    private void queueChannelBinding(IGrid grid, UUID channelId) {
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(channelId, "channelId");
        PendingChannelBinding binding = channelBindings.computeIfAbsent(grid, PendingChannelBinding::new);
        binding.desiredChannelId = channelId;
        if (!binding.queued) {
            binding.queued = true;
            pendingChannelBindings.add(binding);
        }
    }

    private void classify(Binding binding) {
        HandlerBindingPolicy policy = binding.policy();
        if (policy.tickLifecycle() != HandlerBindingPolicy.TickLifecycle.STATIC) {
            binding.beginIndex = beginBindings.size();
            beginBindings.add(binding);
        }
        if (policy.tickLifecycle() == HandlerBindingPolicy.TickLifecycle.BEGIN_END_TICK) {
            binding.endIndex = endBindings.size();
            endBindings.add(binding);
        }
    }

    private void unclassify(Binding binding) {
        if (binding.beginIndex >= 0) {
            Binding moved = swapRemove(beginBindings, binding.beginIndex);
            if (moved != null) moved.beginIndex = binding.beginIndex;
            binding.beginIndex = -1;
        }
        if (binding.endIndex >= 0) {
            Binding moved = swapRemove(endBindings, binding.endIndex);
            if (moved != null) moved.endIndex = binding.endIndex;
            binding.endIndex = -1;
        }
        removePendingMapping(binding);
    }

    /**
     * Queues a mapped binding exactly once. Mapping changes may be reported by
     * lifecycle callbacks or capability listeners, but resolving every mapped
     * binding on a stable tick is unnecessary.
     */
    private void enqueueMapping(Binding binding) {
        if (binding.pendingMappingIndex >= 0) {
            return;
        }
        binding.pendingMappingIndex = pendingMappings.size();
        pendingMappings.add(binding);
    }

    private void removePendingMapping(Binding binding) {
        int index = binding.pendingMappingIndex;
        if (index < 0) {
            return;
        }
        if (index >= pendingMappings.size() || pendingMappings.get(index) != binding) {
            throw new IllegalStateException("Pending mapping queue is inconsistent");
        }
        Binding moved = swapRemove(pendingMappings, index);
        if (moved != null) {
            moved.pendingMappingIndex = index;
        }
        binding.pendingMappingIndex = -1;
    }

    private void drainPendingMappings(long epoch) {
        while (!pendingMappings.isEmpty()) {
            Binding binding = pendingMappings.remove(pendingMappings.size() - 1);
            binding.pendingMappingIndex = -1;
            binding.refreshMapping(epoch);
        }
    }

    @Nullable
    private static <T> T swapRemove(ObjectArrayList<T> list, int index) {
        int lastIndex = list.size() - 1;
        T moved = index == lastIndex ? null : list.get(lastIndex);
        if (moved != null) list.set(index, moved);
        list.remove(lastIndex);
        return moved;
    }

    private BackendLease acquireExclusiveBackend(Binding binding,
                                                 IEnergyHandler backend,
                                                 HandlerBindingPolicy policy,
                                                 boolean manageLifecycle,
                                                 EnergyHandlerRuntime.FailureContext failureContext) {
        if (policy.mappingScope() != HandlerBindingPolicy.MappingScope.NONE) {
            throw new IllegalArgumentException("Transfer backend must not require another mapped backend");
        }
        BackendLease lease = new BackendLease(this, backend, policy, manageLifecycle, false, failureContext);
        lease.references = 1;
        lease.attach(binding);
        try {
            classifyBackend(lease);
        } catch (RuntimeException | Error exception) {
            lease.detach(binding, binding.backendReferenceIndex);
            binding.backendReferenceIndex = -1;
            lease.references = 0;
            cleanupFailedBackendClassification(lease, exception);
            throw exception;
        }
        return lease;
    }

    private SharedBackendAcquisition acquireSharedBackend(Binding binding,
                                                          IEnergyHandler backend,
                                                          @Nullable BackendLease previousLease,
                                                          int previousReferenceIndex,
                                                          boolean previousProviderReference,
                                                          EnergyHandlerRuntime.FailureContext failureContext) {
        BackendLease lease = sharedBackendLeases.get(backend);
        boolean created = false;
        if (lease == null) {
            HandlerBindingPolicy backendPolicy = Objects.requireNonNull(backend.bindingPolicy(), "backend.bindingPolicy()");
            if (backendPolicy.mappingScope() != HandlerBindingPolicy.MappingScope.NONE) {
                throw new IllegalArgumentException("Shared backend must not require another mapped backend");
            }
            lease = new BackendLease(this, backend, backendPolicy, true, true, failureContext);
            sharedBackendLeases.put(backend, lease);
            created = true;
        } else if (lease.closePending) {
            return revivePendingSharedBackend(
                lease, binding, previousLease, previousReferenceIndex, previousProviderReference
            );
        } else if (lease == previousLease) {
            if (!previousProviderReference) {
                throw new IllegalStateException("Shared backend binding has no provider reference to transfer");
            }
            if (previousReferenceIndex < 0 || previousReferenceIndex >= lease.bindings.size()
                || lease.bindings.get(previousReferenceIndex) != binding) {
                throw new IllegalStateException("Existing shared backend reference is inconsistent");
            }
            return new SharedBackendAcquisition(
                lease,
                previousReferenceIndex,
                true,
                providerReference(binding, lease.backend)
            );
        }
        if (lease.references == Integer.MAX_VALUE) {
            throw new IllegalStateException("Shared backend reference count overflow");
        }
        int referenceIndex = lease.addReference(binding);
        lease.references++;
        try {
            if (created) {
                classifyBackend(lease);
            }
        } catch (RuntimeException | Error exception) {
            lease.detach(binding, referenceIndex);
            lease.references--;
            if (created) {
                sharedBackendLeases.remove(backend);
                cleanupFailedBackendClassification(lease, exception);
            }
            throw exception;
        }
        return new SharedBackendAcquisition(lease, referenceIndex, false, null);
    }

    private SharedBackendAcquisition revivePendingSharedBackend(BackendLease lease,
                                                                Binding binding,
                                                                @Nullable BackendLease previousLease,
                                                                int previousReferenceIndex,
                                                                boolean previousProviderReference) {
        if (!lease.shared || sharedBackendLeases.get(lease.backend) != lease) {
            throw new IllegalStateException("Pending shared backend lease is inconsistent");
        }
        if (lease.references != 1 || lease.bindings.size() != 1) {
            throw new IllegalStateException("Pending shared backend close must retain exactly one reference");
        }
        Binding finalBinding = Objects.requireNonNull(
            lease.finalBinding, "Pending shared backend close has no final binding"
        );
        int finalReferenceIndex = lease.finalReferenceIndex;
        if (finalReferenceIndex < 0 || finalReferenceIndex >= lease.bindings.size()
            || lease.bindings.get(finalReferenceIndex) != finalBinding) {
            throw new IllegalStateException("Pending shared backend final reference is inconsistent");
        }
        if (lease.closeIndex < 0 || lease.closeIndex >= pendingBackendClosures.size()
            || pendingBackendClosures.get(lease.closeIndex) != lease) {
            throw new IllegalStateException("Pending shared backend close queue is inconsistent");
        }
        if (lease.clearFinalBindingReference && finalBinding.backendLease != lease) {
            throw new IllegalStateException("Pending final binding does not reference its shared backend lease");
        }
        boolean consumesPreviousReference = previousLease == lease;
        if (consumesPreviousReference
            && (finalBinding != binding || previousReferenceIndex != finalReferenceIndex
                || !previousProviderReference)) {
            throw new IllegalStateException("Reacquiring binding does not match its pending shared backend reference");
        }
        ProviderReference displacedProviderReference = lease.finalProviderReference
            ? providerReference(finalBinding, lease.backend)
            : null;
        if (!consumesPreviousReference) {
            lease.bindings.set(finalReferenceIndex, binding);
        }
        if (lease.clearFinalBindingReference && !consumesPreviousReference) {
            clearReleasedBinding(finalBinding, lease);
        }
        removeBackendClosure(lease);
        lease.clearFinalRelease();
        return new SharedBackendAcquisition(
            lease,
            finalReferenceIndex,
            consumesPreviousReference,
            displacedProviderReference
        );
    }

    private static ProviderReference providerReference(Binding binding, IEnergyHandler backend) {
        return new ProviderReference(
            Objects.requireNonNull(binding.mappedProvider, "mappedProvider"), binding.handler, backend
        );
    }

    private void cleanupFailedBackendClassification(BackendLease lease, Throwable failure) {
        try {
            if (lease.accountIndex >= 0) {
                removeBackendClassification(lease);
            }
            if (!lease.account.close()) {
                failure.addSuppressed(new IllegalStateException(
                    "Failed backend classification retained physical extraction escrow"
                ));
            }
        } catch (RuntimeException | Error cleanupException) {
            failure.addSuppressed(cleanupException);
        }
    }

    private void classifyBackend(BackendLease lease) {
        lease.accountIndex = transferAccounts.size();
        transferAccounts.add(lease);
        if (lease.manageLifecycle && lease.policy.tickLifecycle() != HandlerBindingPolicy.TickLifecycle.STATIC) {
            lease.beginIndex = beginBackends.size();
            beginBackends.add(lease);
        }
        if (lease.manageLifecycle
            && lease.policy.tickLifecycle() == HandlerBindingPolicy.TickLifecycle.BEGIN_END_TICK) {
            lease.endIndex = endBackends.size();
            endBackends.add(lease);
        }
        if (tickActive && backendsBegun && lease.beginIndex >= 0) {
            lease.beginServerTick(activeTickEpoch);
        }
    }

    private void releaseBackend(Binding binding,
                                BackendLease lease,
                                boolean providerReference,
                                int referenceIndex,
                                boolean clearBindingReference) {
        if (lease.references <= 0) {
            throw new IllegalStateException("Backend lease reference count is inconsistent");
        }
        if (lease.references == 1) {
            lease.prepareFinalRelease(binding, providerReference, referenceIndex, clearBindingReference);
            enqueueBackendClosure(lease);
            if (lease.isReadyForClosure()) {
                finishLastBackendRelease(lease);
            }
            return;
        }
        lease.detach(binding, referenceIndex);
        lease.references--;
        if (clearBindingReference) {
            clearReleasedBinding(binding, lease);
        }
        if (providerReference) {
            releaseSharedProviderReferenceAtMostOnce(
                providerReference(binding, lease.backend), "shared backend reference release"
            );
        }
    }

    private void finishLastBackendRelease(BackendLease lease) {
        if (lease.references != 1 || lease.bindings.size() != 1) {
            throw new IllegalStateException("Pending backend close must retain exactly one provider reference");
        }
        Binding binding = Objects.requireNonNull(lease.finalBinding, "Backend close has no binding reference");
        boolean providerReference = lease.finalProviderReference;
        int referenceIndex = lease.finalReferenceIndex;
        boolean clearBindingReference = lease.clearFinalBindingReference;
        boolean closed = false;
        RuntimeException failure = null;
        try {
            closed = lease.account.close();
        } catch (RuntimeException exception) {
            failure = aggregate(failure, exception);
        }
        if (!closed) {
            enqueueBackendClosure(lease);
            if (failure != null) {
                throw failure;
            }
            return;
        }
        if (lease.activeEndEpoch != Long.MIN_VALUE) {
            long epoch = lease.activeEndEpoch;
            try {
                lease.endServerTick(epoch);
            } catch (RuntimeException exception) {
                failure = aggregate(failure, exception);
            } finally {
                try {
                    applyReceiveCommitFeedback(lease, epoch);
                } catch (RuntimeException exception) {
                    failure = aggregate(failure, exception);
                }
            }
        }
        if (lease.bindings.get(0) != binding) {
            throw new IllegalStateException("Final backend lease reference belongs to a different binding");
        }
        removeBackendClosure(lease);
        removeQuarantine(lease);
        removeBackendClassification(lease);
        if (lease.shared) {
            BackendLease removed = sharedBackendLeases.remove(lease.backend);
            if (removed != lease) {
                failure = aggregate(failure, new IllegalStateException("Shared backend lease is inconsistent"));
            }
        }
        lease.detach(binding, referenceIndex);
        lease.references = 0;
        if (clearBindingReference) {
            clearReleasedBinding(binding, lease);
        }
        lease.clearFinalRelease();
        if (providerReference) {
            releaseSharedProviderReferenceAtMostOnce(
                providerReference(binding, lease.backend), "final shared backend release"
            );
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void removeBackendClassification(BackendLease lease) {
        if (lease.accountIndex >= 0) {
            BackendLease moved = swapRemove(transferAccounts, lease.accountIndex);
            if (moved != null) moved.accountIndex = lease.accountIndex;
            lease.accountIndex = -1;
        }
        if (lease.beginIndex >= 0) {
            BackendLease moved = swapRemove(beginBackends, lease.beginIndex);
            if (moved != null) moved.beginIndex = lease.beginIndex;
            lease.beginIndex = -1;
        }
        if (lease.endIndex >= 0) {
            BackendLease moved = swapRemove(endBackends, lease.endIndex);
            if (moved != null) moved.endIndex = lease.endIndex;
            lease.endIndex = -1;
        }
    }

    private static void clearReleasedBinding(Binding binding, BackendLease lease) {
        if (binding.backendLease != lease) {
            throw new IllegalStateException("Released binding does not reference its backend lease");
        }
        binding.backendLease = null;
        binding.providerReference = false;
        binding.backendReferenceIndex = -1;
    }

    private void removeBinding(Binding binding) {
        if (binding.allIndex < 0) {
            return;
        }
        Binding moved = swapRemove(allBindings, binding.allIndex);
        if (moved != null) moved.allIndex = binding.allIndex;
        binding.allIndex = -1;
    }

    private void removeRoute(RouteEntry entry) {
        if (entry.allIndex < 0) {
            return;
        }
        RouteEntry moved = swapRemove(allRoutes, entry.allIndex);
        if (moved != null) moved.allIndex = entry.allIndex;
        entry.allIndex = -1;
    }

    private void removeMachineRoute(MachineRouteEntry entry) {
        if (entry.allIndex < 0) {
            return;
        }
        MachineRouteEntry moved = swapRemove(allMachineRoutes, entry.allIndex);
        if (moved != null) moved.allIndex = entry.allIndex;
        entry.allIndex = -1;
    }

    private void requireRunning() {
        if (stopping) {
            throw new IllegalStateException("Machine binding index is stopping");
        }
    }

    private void requireNoProviderCallback(String operation) {
        if (providerCallbackActive) {
            throw new IllegalStateException("Cannot " + operation + " during a mapped provider callback");
        }
    }

    private IEnergyHandler acquireSharedProviderReference(Binding binding) {
        MappedEnergyHandlerProvider provider = Objects.requireNonNull(binding.mappedProvider, "mappedProvider");
        requireNoProviderCallback("acquire a shared provider reference");
        providerCallbackActive = true;
        try {
            return Objects.requireNonNull(
                provider.acquireSharedBackend(binding.handler),
                "Mapped energy handler provider returned null shared backend"
            );
        } finally {
            providerCallbackActive = false;
        }
    }

    private IEnergyHandler resolveRuntimeProvider(Binding binding, long epoch) {
        MappedEnergyHandlerProvider provider = Objects.requireNonNull(binding.mappedProvider, "mappedProvider");
        requireNoProviderCallback("resolve a runtime provider backend");
        providerCallbackActive = true;
        try {
            return Objects.requireNonNull(
                provider.resolveRuntime(binding.handler, epoch),
                "Mapped energy handler provider returned null runtime backend"
            );
        } finally {
            providerCallbackActive = false;
        }
    }

    /**
     * Provider release is intentionally at-most-once. A callback may throw
     * after mutating external state, so retrying could release the same
     * reference twice. Internal ownership is committed before this method is
     * called; any failure is retained as terminal ambiguous cleanup debt until
     * server stop and is never used to fabricate an internal rollback.
     */
    private void releaseSharedProviderReferenceAtMostOnce(ProviderReference reference, String operation) {
        requireNoProviderCallback("release a shared provider reference");
        providerCallbackActive = true;
        try {
            reference.provider.releaseSharedBackend(reference.boundHandler, reference.sharedBackend);
        } catch (RuntimeException | Error failure) {
            terminalProviderCleanupDebts.add(new ProviderCleanupDebt(reference, operation, failure));
            CirculationFlowNetworks.LOGGER.error(
                "Mapped provider release failed after committed {} for provider {}, endpoint {}, backend {}; "
                    + "recording terminal ambiguous cleanup debt without retry",
                operation, reference.provider.getClass().getName(), reference.boundHandler.getClass().getName(),
                reference.sharedBackend.getClass().getName(), failure
            );
        } finally {
            providerCallbackActive = false;
        }
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xffffffffL);
    }

    //~ if >=1.20 'World ' -> 'Level ' {
    //~ if >=1.20 '.provider.getDimension()' -> '.dimension().location().hashCode()' {
    private static int getDimensionId(World world) {
        return world.provider.getDimension();
    }
    //~}
    //~}

    //~ if >=1.20 '.toLong()' -> '.asLong()' {
    private static long packBlockPosition(BlockPos position) {
        return position.toLong();
    }
    //~}

    static final class Binding {

        private final MachineBindingIndex owner;
        private final CFNBlockEntityEx blockEntity;
        private final IEnergyHandler handler;
        @Nullable
        private final MappedEnergyHandlerProvider mappedProvider;
        @Nullable
        private HandlerBindingPolicy policy;
        @Nullable
        private BackendLease backendLease;
        private boolean providerReference;
        private long generation;
        private boolean bound;
        private boolean rebindQueued;
        private boolean mappingDirty;
        private boolean routeRetryPending;
        private int beginIndex = -1;
        private int endIndex = -1;
        private int pendingMappingIndex = -1;
        private int backendReferenceIndex = -1;
        private int allIndex = -1;
        private long activeEndEpoch = Long.MIN_VALUE;

        private Binding(MachineBindingIndex owner,
                               CFNBlockEntityEx blockEntity,
                               IEnergyHandler handler,
                               @Nullable MappedEnergyHandlerProvider mappedProvider) {
            this.owner = owner;
            this.blockEntity = blockEntity;
            this.handler = handler;
            this.mappedProvider = mappedProvider;
        }
        public IEnergyHandler handler() {
            return handler;
        }
        public boolean isActive() {
            return bound && backendLease != null;
        }
        public MachineTransferAccount account() {
            if (!isActive()) {
                throw new IllegalStateException("Binding has been invalidated");
            }
            return Objects.requireNonNull(backendLease, "Binding has no backend lease").account;
        }
        public HandlerBindingPolicy policy() {
            return Objects.requireNonNull(policy, "Binding policy is not frozen");
        }

        private void bindInitial() {
            bindDirectHandler();
            if (bound) {
                resolveActiveHandler(Long.MIN_VALUE);
                mappingDirty = false;
            }
        }

        private void rebindBeforeTick() {
            if (bound || owner.handlerBindings.get(blockEntity) != this) {
                return;
            }
            owner.clearEnergyThrottle(blockEntity);
            bindDirectHandler();
            if (bound) {
                resolveActiveHandler(Long.MIN_VALUE);
                mappingDirty = false;
            }
        }

        private void beginServerTick(long epoch) {
            if (!bound || owner.isEnergyThrottled(blockEntity)) {
                return;
            }
            boolean endTickLifecycle = endIndex >= 0;
            HandlerTickResult result;
            try {
                result = handler.beginServerTick(epoch);
            } catch (RuntimeException exception) {
                handleBeginFailure(epoch, exception);
                return;
            }
            if (result != HandlerTickResult.UNCHANGED && !handleBeginResult(result, epoch)) {
                return;
            }
            if (endTickLifecycle) {
                activeEndEpoch = epoch;
            }
        }

        private boolean handleBeginResult(@Nullable HandlerTickResult result, long epoch) {
            if (result == null) {
                handleBeginFailure(epoch, new NullPointerException("handler.beginServerTick()"));
                return false;
            }
            if (result == HandlerTickResult.SUSPEND_UNTIL_REBIND) {
                owner.markEnergyBudgetFailure(blockEntity);
                suspend(false);
                return false;
            }
            if (policy().mappingScope() != HandlerBindingPolicy.MappingScope.NONE) {
                mappingDirty = true;
                owner.enqueueMapping(this);
            }
            enqueueBlockEntityRouteRefresh();
            return true;
        }

        private void handleBeginFailure(long epoch, RuntimeException exception) {
            CirculationFlowNetworks.LOGGER.error("Energy handler {} failed to begin epoch {}",
                handler.getClass().getName(), epoch, exception);
            owner.markEnergyBudgetFailure(blockEntity);
            suspend(false);
        }

        private void refreshMapping(long epoch) {
            if (!bound || !mappingDirty) {
                return;
            }
            resolveActiveHandler(epoch);
            mappingDirty = false;
            enqueueBlockEntityRouteRefresh();
        }

        private void endServerTick(long epoch) {
            if (activeEndEpoch != epoch) {
                return;
            }
            activeEndEpoch = Long.MIN_VALUE;
            try {
                handler.endServerTick(epoch);
            } catch (RuntimeException exception) {
                CirculationFlowNetworks.LOGGER.error("Energy handler {} failed to end epoch {}",
                    handler.getClass().getName(), epoch, exception);
                suspend(false);
            }
        }

        private void resolveActiveHandler(long epoch) {
            BackendLease previousLease = backendLease;
            boolean previousProviderReference = providerReference;
            int previousReferenceIndex = backendReferenceIndex;
            backendReferenceIndex = -1;
            BackendLease acquiredLease;
            boolean acquiredProviderReference = false;
            SharedBackendAcquisition sharedAcquisition = null;
            try {
            switch (policy().mappingScope()) {
                case NONE -> acquiredLease = owner.acquireExclusiveBackend(
                    this, handler, policy(), false, failureContext()
                );
                case RUNTIME_DYNAMIC -> {
                    IEnergyHandler backend = owner.resolveRuntimeProvider(this, epoch);
                    HandlerBindingPolicy backendPolicy = Objects.requireNonNull(
                        backend.bindingPolicy(), "backend.bindingPolicy()"
                    );
                    if (backendPolicy.mappingScope() != HandlerBindingPolicy.MappingScope.NONE) {
                        throw new IllegalArgumentException("Mapped runtime backend must not require another backend");
                    }
                    acquiredLease = owner.acquireExclusiveBackend(
                        this, backend, backendPolicy, true, failureContext()
                    );
                }
                case SHARED_BACKEND -> {
                    IEnergyHandler backend = owner.acquireSharedProviderReference(this);
                    acquiredProviderReference = true;
                    try {
                        sharedAcquisition = owner.acquireSharedBackend(
                            this,
                            backend,
                            previousLease,
                            previousReferenceIndex,
                            previousProviderReference,
                            failureContext()
                        );
                        acquiredLease = sharedAcquisition.lease;
                    } catch (RuntimeException | Error exception) {
                        owner.releaseSharedProviderReferenceAtMostOnce(
                            MachineBindingIndex.providerReference(this, backend), "failed shared backend acquisition"
                        );
                        throw exception;
                    }
                }
                default -> throw new IllegalStateException("Unknown handler mapping scope");
            }
            } catch (RuntimeException | Error exception) {
                backendReferenceIndex = previousReferenceIndex;
                throw exception;
            }
            if (sharedAcquisition != null) {
                backendReferenceIndex = sharedAcquisition.referenceIndex;
            }
            backendLease = acquiredLease;
            providerReference = acquiredProviderReference;
            if (previousLease != null
                && (sharedAcquisition == null || !sharedAcquisition.previousReferenceConsumed)) {
                owner.releaseBackend(this, previousLease, previousProviderReference, previousReferenceIndex, false);
            }
            if (sharedAcquisition != null && sharedAcquisition.displacedProviderReference != null) {
                owner.releaseSharedProviderReferenceAtMostOnce(
                    sharedAcquisition.displacedProviderReference, "shared backend reference transfer"
                );
            }
        }

        private void unbind() {
            if (!bound && !rebindQueued && backendLease == null) {
                return;
            }
            rebindQueued = false;
            advanceGeneration();
            boolean wasBound = bound;
            bound = false;
            if (wasBound) {
                owner.unclassify(this);
            }
            RuntimeException failure = null;
            if (wasBound) {
                try {
                    handler.unbindBlockEntity();
                } catch (RuntimeException exception) {
                    failure = exception;
                }
            }
            try {
                releaseBackendIfPresent();
            } catch (RuntimeException exception) {
                failure = aggregate(failure, exception);
            }
            mappingDirty = false;
            if (failure != null) {
                throw failure;
            }
        }

        private void bindDirectHandler() {
            advanceGeneration();
            long bindingGeneration = generation;
            boolean handlerBindAttempted = false;
            try {
                handlerBindAttempted = true;
                blockEntity.cfn_bindEnergyHandler(handler, new BindingSink(this, bindingGeneration));
                HandlerBindingPolicy boundPolicy = Objects.requireNonNull(
                    handler.bindingPolicy(), "handler.bindingPolicy()"
                );
                if (policy != null && policy != boundPolicy) {
                    throw new IllegalStateException("Energy handler changed its frozen binding policy after rebind");
                }
                if (boundPolicy.mappingScope() != HandlerBindingPolicy.MappingScope.NONE && mappedProvider == null) {
                    throw new IllegalArgumentException("A mapped handler policy requires a mapped energy handler provider");
                }
                if (policy == null) {
                    policy = boundPolicy;
                }
                bound = true;
                mappingDirty = boundPolicy.mappingScope() != HandlerBindingPolicy.MappingScope.NONE;
                owner.classify(this);
            } catch (RuntimeException | Error exception) {
                bound = false;
                if (handlerBindAttempted) {
                    try {
                        handler.unbindBlockEntity();
                    } catch (RuntimeException | Error cleanupException) {
                        exception.addSuppressed(cleanupException);
                    }
                }
                throw exception;
            }
        }

        private void invalidate(long invalidationGeneration) {
            if (invalidationGeneration != generation || !bound || owner.handlerBindings.get(blockEntity) != this) {
                return;
            }
            suspend(true);
        }

        private void backendChanged(long invalidationGeneration) {
            if (invalidationGeneration != generation || !bound || owner.handlerBindings.get(blockEntity) != this) {
                return;
            }
            mappingDirty = true;
            owner.enqueueMapping(this);
        }

        private void suspend(long invalidationGeneration) {
            if (invalidationGeneration != generation || !bound || owner.handlerBindings.get(blockEntity) != this) {
                return;
            }
            suspend(false);
        }

        private void suspend(boolean queueRebind) {
            owner.unclassify(this);
            bound = false;
            rebindQueued = queueRebind;
            if (queueRebind) owner.pendingRebinds.add(this);
            RuntimeException failure = null;
            try {
                handler.unbindBlockEntity();
            } catch (RuntimeException exception) {
                failure = exception;
            }
            try {
                releaseBackendIfPresent();
            } catch (RuntimeException exception) {
                failure = aggregate(failure, exception);
            } finally {
                mappingDirty = false;
                enqueueBlockEntityRouteRefresh();
            }
            if (failure != null) {
                throw failure;
            }
        }

        private void releaseBackendIfPresent() {
            if (backendLease != null) {
                BackendLease lease = backendLease;
                boolean releaseProviderReference = providerReference;
                int referenceIndex = backendReferenceIndex;
                owner.releaseBackend(this, lease, releaseProviderReference, referenceIndex, true);
            }
        }

        private EnergyHandlerRuntime.FailureContext failureContext() {
            var world = blockEntity.cfn_getWorld();
            if (world == null) {
                return EnergyHandlerRuntime.FailureContext.UNKNOWN;
            }
            return EnergyHandlerRuntime.machineContext(
                getDimensionId(world), packBlockPosition(blockEntity.cfn_getBlockPos()), generation
            );
        }

        private void enqueueBlockEntityRouteRefresh() {
            var world = blockEntity.cfn_getWorld();
            if (world == null) {
                return;
            }
            owner.enqueuePosition(getDimensionId(world), packBlockPosition(blockEntity.cfn_getBlockPos()));
        }

        private void advanceGeneration() {
            if (generation == Long.MAX_VALUE) {
                throw new IllegalStateException("Handler binding generation exhausted");
            }
            generation++;
        }
    }

    @SuppressWarnings("ClassCanBeRecord")
    private static final class BindingSink implements HandlerInvalidationSink {
        private final Binding binding;
        private final long generation;

        private BindingSink(Binding binding, long generation) {
            this.binding = binding;
            this.generation = generation;
        }
        public void invalidate() {
            binding.invalidate(generation);
        }
        public void backendChanged() {
            binding.backendChanged(generation);
        }
        public void suspendUntilRebind() {
            binding.suspend(generation);
        }
    }

    static final class BackendLease {

        private final MachineBindingIndex owner;
        private final IEnergyHandler backend;
        private final HandlerBindingPolicy policy;
        private final boolean manageLifecycle;
        private final boolean shared;
        private final MachineTransferAccount account;
        private final ObjectArrayList<Binding> bindings = new ObjectArrayList<>();
        private int references;
        private int accountIndex = -1;
        private int beginIndex = -1;
        private int endIndex = -1;
        private long activeEndEpoch = Long.MIN_VALUE;
        private boolean quarantineRequested;
        private int quarantineIndex = -1;
        private boolean closePending;
        private int closeIndex = -1;
        @Nullable
        private Binding finalBinding;
        private boolean finalProviderReference;
        private int finalReferenceIndex = -1;
        private boolean clearFinalBindingReference;

        private BackendLease(MachineBindingIndex owner,
                             IEnergyHandler backend,
                             HandlerBindingPolicy policy,
                             boolean manageLifecycle,
                             boolean shared,
                             EnergyHandlerRuntime.FailureContext failureContext) {
            this.owner = owner;
            this.backend = backend;
            this.policy = policy;
            this.manageLifecycle = manageLifecycle;
            this.shared = shared;
            this.account = new MachineTransferAccount(owner, this, backend, policy, failureContext, this::requestQuarantine);
        }

        private void attach(Binding binding) {
            if (binding.backendReferenceIndex >= 0) {
                throw new IllegalStateException("Handler binding already owns a backend lease reference");
            }
            binding.backendReferenceIndex = addReference(binding);
        }

        private int addReference(Binding binding) {
            int referenceIndex = bindings.size();
            bindings.add(binding);
            return referenceIndex;
        }

        private void detach(Binding binding, int index) {
            if (index < 0 || index >= bindings.size() || bindings.get(index) != binding) {
                throw new IllegalStateException("Handler binding backend lease reference is inconsistent");
            }
            Binding moved = swapRemove(bindings, index);
            if (moved != null) moved.backendReferenceIndex = index;
        }

        private void prepareFinalRelease(Binding binding,
                                         boolean providerReference,
                                         int referenceIndex,
                                         boolean clearBindingReference) {
            if (finalBinding != null) {
                throw new IllegalStateException("Backend lease already has a pending final release");
            }
            if (referenceIndex < 0 || referenceIndex >= bindings.size() || bindings.get(referenceIndex) != binding) {
                throw new IllegalStateException("Backend final release reference is inconsistent");
            }
            finalBinding = binding;
            finalProviderReference = providerReference;
            finalReferenceIndex = referenceIndex;
            clearFinalBindingReference = clearBindingReference;
        }

        private void clearFinalRelease() {
            finalBinding = null;
            finalProviderReference = false;
            finalReferenceIndex = -1;
            clearFinalBindingReference = false;
        }

        private void beginServerTick(long epoch) {
            if (allBindingsThrottled()) {
                return;
            }
            boolean endTickLifecycle = endIndex >= 0;
            HandlerTickResult result;
            try {
                result = backend.beginServerTick(epoch);
            } catch (RuntimeException exception) {
                CirculationFlowNetworks.LOGGER.error(
                    "Shared energy backend {} failed to begin epoch {}", backend.getClass().getName(), epoch, exception
                );
                owner.markSharedEnergyBudgetFailure(this);
                suspendBindings(false);
                return;
            }
            if (result != HandlerTickResult.UNCHANGED && !handleBeginResult(result)) {
                return;
            }
            if (endTickLifecycle) {
                activeEndEpoch = epoch;
            }
        }

        private boolean handleBeginResult(@Nullable HandlerTickResult result) {
            if (result == null) {
                owner.markSharedEnergyBudgetFailure(this);
                CirculationFlowNetworks.LOGGER.error(
                    "Shared energy backend {} returned null from beginServerTick", backend.getClass().getName()
                );
                suspendBindings(false);
                return false;
            }
            if (result == HandlerTickResult.SUSPEND_UNTIL_REBIND) {
                owner.markSharedEnergyBudgetFailure(this);
                suspendBindings(false);
                return false;
            }
            for (int index = 0; index < bindings.size(); index++) {
                bindings.get(index).enqueueBlockEntityRouteRefresh();
            }
            return true;
        }

        private boolean allBindingsThrottled() {
            if (bindings.isEmpty()) {
                throw new IllegalStateException("Active backend lease has no endpoint bindings");
            }
            for (int index = 0; index < bindings.size(); index++) {
                if (!owner.isEnergyThrottled(bindings.get(index).blockEntity)) {
                    return false;
                }
            }
            return true;
        }

        void reportBudgetFailure(CFNBlockEntityEx source) {
            if (shared) {
                owner.markSharedEnergyBudgetFailure(this);
            } else {
                owner.markEnergyBudgetFailure(source);
            }
        }

        void reportBudgetSuccess(CFNBlockEntityEx source) {
            owner.markEnergyBudgetSuccess(source);
        }

        private void requestQuarantine() {
            quarantineRequested = true;
            owner.enqueueQuarantine(this);
        }

        private void suspendBindings(boolean queueRebind) {
            quarantineRequested = false;
            ObjectArrayList<Binding> snapshot = new ObjectArrayList<>(bindings);
            RuntimeException failure = null;
            for (int index = 0; index < snapshot.size(); index++) {
                Binding binding = snapshot.get(index);
                if (!binding.bound) {
                    continue;
                }
                try {
                    binding.suspend(queueRebind);
                } catch (RuntimeException exception) {
                    failure = aggregate(failure, exception);
                }
            }
            if (failure != null) {
                throw failure;
            }
        }

        private void endServerTick(long epoch) {
            if (policy.tickLifecycle() == HandlerBindingPolicy.TickLifecycle.BEGIN_END_TICK) {
                if (activeEndEpoch != epoch) {
                    throw new IllegalStateException("Backend lifecycle epoch mismatch: expected "
                        + activeEndEpoch + ", received " + epoch);
                }
                activeEndEpoch = Long.MIN_VALUE;
                backend.endServerTick(epoch);
            }
        }

        private boolean isReadyForClosure() {
            return policy.tickLifecycle() != HandlerBindingPolicy.TickLifecycle.BEGIN_END_TICK
                || activeEndEpoch == Long.MIN_VALUE;
        }
    }

    private static final class PendingChannelBinding {

        private final IGrid grid;
        private UUID desiredChannelId;
        private boolean queued;

        private PendingChannelBinding(IGrid grid) {
            this.grid = grid;
            this.desiredChannelId = HubNode.EMPTY;
        }
    }

    private static final class NodeRecord {

        private final INode node;
        private final int dimensionId;
        private final long packedPosition;
        private final int chunkX;
        private final int chunkZ;
        private final ReferenceOpenHashSet<RouteEntry> routes = new ReferenceOpenHashSet<>();
        private boolean active;
        @Nullable
        private IGrid grid;

        private NodeRecord(INode node,
                           int dimensionId,
                           long packedPosition,
                           int chunkX,
                           int chunkZ,
                           boolean active,
                           @Nullable IGrid grid) {
            this.node = node;
            this.dimensionId = dimensionId;
            this.packedPosition = packedPosition;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.active = active;
            this.grid = grid;
        }
    }

    private static final class RouteEntry implements RouteHandle {

        private final MachineBindingIndex owner;
        private final NodeRecord node;
        private final Route route;
        private boolean registered = true;
        private boolean routed;
        private boolean refreshQueued;
        private boolean retryDeferred;
        private boolean pendingQueued;
        private int allIndex = -1;

        private RouteEntry(MachineBindingIndex owner, NodeRecord node, Route route) {
            this.owner = owner;
            this.node = node;
            this.route = route;
        }
        public INode node() {
            return node.node;
        }
        public void unregister() {
            if (!registered) {
                return;
            }
            registered = false;
            pendingQueued = false;
            retryDeferred = false;
            refreshQueued = false;
            node.routes.remove(this);
            owner.routes.remove(route);
            owner.removeRoute(this);
            closeRoute();
        }

        private void shutdown() {
            registered = false;
            pendingQueued = false;
            retryDeferred = false;
            refreshQueued = false;
            allIndex = -1;
            closeRoute();
        }

        private void closeRoute() {
            routed = false;
            route.close();
        }

        private void deactivate() {
            if (!routed) {
                return;
            }
            routed = false;
            route.revoke();
        }
    }

    private static final class MachineRouteEntry {

        private final MachineBindingIndex owner;
        private final int dimensionId;
        private final long machinePosition;
        private final long chunk;
        private final Route route;
        private boolean registered = true;
        private boolean routed;
        private boolean refreshQueued;
        private boolean priorityQueued;
        private boolean retryDeferred;
        private boolean pendingQueued;
        private int pendingPriority;
        private int allIndex = -1;
        private int chunkIndex = -1;

        private MachineRouteEntry(MachineBindingIndex owner,
                                  int dimensionId,
                                  long machinePosition,
                                  Route route) {
            this.owner = owner;
            this.dimensionId = dimensionId;
            this.machinePosition = machinePosition;
            this.route = route;
            //~ if >=1.20 '.fromLong(' -> '.of(' {
            BlockPos position = BlockPos.fromLong(machinePosition);
            //~}
            this.chunk = chunkKey(position.getX() >> 4, position.getZ() >> 4);
        }

        private void queuePriority(int priority) {
            if (!registered) {
                return;
            }
            pendingPriority = priority;
            priorityQueued = true;
            if (!retryDeferred && !pendingQueued) {
                pendingQueued = true;
                owner.pendingMachineRoutes.add(this);
            }
        }

        private void unregister() {
            if (!registered) {
                return;
            }
            closeRoute();
            registered = false;
            pendingQueued = false;
            retryDeferred = false;
            refreshQueued = false;
            priorityQueued = false;
        }

        private void shutdown() {
            registered = false;
            pendingQueued = false;
            retryDeferred = false;
            refreshQueued = false;
            priorityQueued = false;
            allIndex = -1;
            closeRoute();
        }

        private void closeRoute() {
            routed = false;
            route.close();
        }

        private void deactivate() {
            if (!routed) {
                return;
            }
            routed = false;
            route.revoke();
        }
    }
}
