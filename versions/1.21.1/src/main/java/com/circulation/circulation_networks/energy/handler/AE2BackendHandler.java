package com.circulation.circulation_networks.energy.handler;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergyService;
import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.EnergyAmounts;
import com.circulation.circulation_networks.api.HandlerTickResult;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.manager.HandlerBindingPolicy;
import com.circulation.circulation_networks.manager.HandlerInvalidationSink;
import com.circulation.circulation_networks.manager.DeferredReceiveCommit;
import com.circulation.circulation_networks.network.nodes.HubNode;
import com.circulation.circulation_networks.utils.EnergyAmountConversionUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Physical AE2 network backend shared by every endpoint of one energy service. */
public final class AE2BackendHandler implements IEnergyHandler, DeferredReceiveCommit {

    private static final double FE_PER_AE = 2.0D;
    private static final HandlerBindingPolicy BINDING_POLICY = HandlerBindingPolicy.of(
        HandlerBindingPolicy.TickLifecycle.BEGIN_END_TICK,
        HandlerBindingPolicy.RoleScope.FIXED,
        HandlerBindingPolicy.MappingScope.NONE,
        HandlerBindingPolicy.PairMatching.NONE
    );

    private final IEnergyService energyService;
    private final EnergyAmount initialDemand = EnergyAmount.obtain(0L);
    private final EnergyAmount pendingReceive = EnergyAmount.obtain(0L);
    private final EnergyAmount rejectedReceive = EnergyAmount.obtain(0L);
    private long activeEpoch = Long.MIN_VALUE;
    private boolean demandInitialized;
    private RuntimeException demandInitializationFailure;
    private boolean closed;

    public AE2BackendHandler(IEnergyService energyService) {
        this.energyService = Objects.requireNonNull(energyService, "energyService");
    }

    @Override
    public HandlerBindingPolicy bindingPolicy() {
        return BINDING_POLICY;
    }

    @Override
    public void bindBlockEntity(BlockEntity blockEntity, HandlerInvalidationSink invalidationSink) {
        throw new UnsupportedOperationException("AE2 shared backends are acquired by energy-service identity");
    }

    @Override
    public HandlerTickResult beginServerTick(long epoch) {
        requireOpen();
        if (activeEpoch != Long.MIN_VALUE) {
            throw new IllegalStateException("AE2 backend tick already active for epoch " + activeEpoch);
        }
        initialDemand.setZero();
        pendingReceive.setZero();
        rejectedReceive.setZero();
        demandInitialized = false;
        demandInitializationFailure = null;
        activeEpoch = epoch;
        return HandlerTickResult.UNCHANGED;
    }

    @Override
    public void endServerTick(long epoch) {
        requireEpoch(epoch);
        commitPendingReceive();
    }

    @Override
    public void unbindBlockEntity() {
        throw new UnsupportedOperationException("AE2 shared backends are not block-entity bindings");
    }

    @Override
    public void bindItem(ItemStack itemStack, @Nullable HubNode.HubMetadata hubMetadata) {
        throw new UnsupportedOperationException("AE2 shared backends do not support item bindings");
    }

    @Override
    public void unbindItem() {
        throw new UnsupportedOperationException("AE2 shared backends do not support item bindings");
    }

    @Override
    public EnergyAmount receiveEnergy(EnergyAmount maxReceive, @Nullable HubNode.HubMetadata hubMetadata) {
        Objects.requireNonNull(maxReceive, "maxReceive");
        ensureDemandInitialized();
        validatePendingReceive(maxReceive);
        return EnergyAmount.obtain(maxReceive);
    }

    @Override
    public EnergyAmount extractEnergy(EnergyAmount maxExtract, @Nullable HubNode.HubMetadata hubMetadata) {
        return EnergyAmounts.ZERO;
    }

    @Override
    public EnergyAmount canExtractValue(@Nullable HubNode.HubMetadata hubMetadata) {
        return EnergyAmounts.ZERO;
    }

    @Override
    public EnergyAmount canReceiveValue(@Nullable HubNode.HubMetadata hubMetadata) {
        ensureDemandInitialized();
        return EnergyAmount.obtain(initialDemand);
    }

    @Override
    public boolean canExtract(IEnergyHandler receiveHandler, @Nullable HubNode.HubMetadata hubMetadata) {
        return false;
    }

    @Override
    public boolean canReceive(IEnergyHandler sendHandler, @Nullable HubNode.HubMetadata hubMetadata) {
        if (activeEpoch == Long.MIN_VALUE) {
            return false;
        }
        ensureDemandInitialized();
        return initialDemand.isPositive();
    }

    @Override
    public EnergyType getType(@Nullable HubNode.HubMetadata hubMetadata) {
        return closed ? EnergyType.INVALID : EnergyType.RECEIVE;
    }

    public void close() {
        requireOpen();
        if (activeEpoch != Long.MIN_VALUE) {
            commitPendingReceive();
        }
        closed = true;
    }

    @Override
    public EnergyAmount drainRejectedReceive() {
        EnergyAmount result = EnergyAmount.obtain(rejectedReceive);
        rejectedReceive.setZero();
        return result;
    }

    @Override
    public void rollbackPendingReceive(EnergyAmount amount) {
        Objects.requireNonNull(amount, "amount");
        if (amount.isNegative() || amount.compareTo(pendingReceive) > 0) {
            throw new IllegalArgumentException("AE2 pending receive rollback exceeds the queued amount");
        }
        pendingReceive.subtract(amount);
    }

    private void validatePendingReceive(EnergyAmount amount) {
        if (amount.isNegative()) {
            throw new IllegalArgumentException("AE2 receive amount cannot be negative");
        }
        pendingReceive.add(amount);
        if (pendingReceive.compareTo(initialDemand) > 0) {
            pendingReceive.subtract(amount);
            throw new IllegalStateException("AE2 pending receive exceeds initial demand");
        }
    }

    private void ensureDemandInitialized() {
        requireActive();
        if (demandInitialized) {
            return;
        }
        if (demandInitializationFailure != null) {
            throw demandInitializationFailure;
        }
        try {
            double demand = energyService.getEnergyDemand(Double.MAX_VALUE);
            if (!Double.isFinite(demand) || demand < 0.0D) {
                throw new IllegalStateException("AE2 energy service returned invalid demand " + demand);
            }
            double demandFe = demand * FE_PER_AE;
            if (!Double.isFinite(demandFe)) {
                throw new IllegalStateException("AE2 energy demand exceeds the supported conversion range: " + demand);
            }
            EnergyAmountConversionUtils.setFromDoubleFloor(initialDemand, demandFe);
            demandInitialized = true;
        } catch (RuntimeException exception) {
            demandInitializationFailure = exception;
            throw exception;
        }
    }

    private void commitPendingReceive() {
        rejectedReceive.setZero();
        try {
            if (pendingReceive.isPositive()) {
                double receivedFe = pendingReceive.doubleValue();
                if (!Double.isFinite(receivedFe) || receivedFe < 0.0D) {
                    throw new IllegalStateException("AE2 pending receive exceeds the supported commit range");
                }
                double requestedAe = receivedFe / FE_PER_AE;
                double overflowAe = energyService.injectPower(requestedAe, Actionable.MODULATE);
                if (!Double.isFinite(overflowAe) || overflowAe < 0.0D || overflowAe > requestedAe) {
                    throw new IllegalStateException("AE2 energy service returned invalid injection overflow " + overflowAe);
                }
                double rejectedFe = overflowAe * FE_PER_AE;
                if (!Double.isFinite(rejectedFe)) {
                    throw new IllegalStateException("AE2 injection overflow exceeds the supported feedback range");
                }
                EnergyAmountConversionUtils.setFromDoubleCeiling(rejectedReceive, rejectedFe);
                if (rejectedReceive.compareTo(pendingReceive) > 0) {
                    throw new IllegalStateException("AE2 rejected receive exceeds pending receive");
                }
            }
        } catch (RuntimeException exception) {
            rejectedReceive.copyFrom(pendingReceive);
            throw exception;
        } finally {
            initialDemand.setZero();
            pendingReceive.setZero();
            demandInitialized = false;
            demandInitializationFailure = null;
            activeEpoch = Long.MIN_VALUE;
        }
    }

    private void requireActive() {
        requireOpen();
        if (activeEpoch == Long.MIN_VALUE) {
            throw new IllegalStateException("AE2 backend has no active tick");
        }
    }

    private void requireEpoch(long epoch) {
        requireOpen();
        if (activeEpoch != epoch) {
            throw new IllegalStateException("AE2 backend tick epoch mismatch: expected " + activeEpoch + ", got " + epoch);
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("AE2 backend is closed");
        }
    }
}
