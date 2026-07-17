package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.network.nodes.HubNode;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Default physical machine account. Budget snapshots are lazy because some
 * handlers need grid metadata, while consumption is shared by every slot.
 */
final class MachineTransferAccount {

    private final MachineBindingIndex owner;
    private final IEnergyHandler handler;
    private final EnergyHandlerRuntime.FailureContext failureContext;
    private final Runnable quarantine;
    private final MachineTransferBudget extractBudget = new MachineTransferBudget();
    private final MachineTransferBudget receiveBudget = new MachineTransferBudget();
    private final EnergyAmount reservedExtract = EnergyAmount.obtain(0L);
    private final EnergyAmount deferredExtract = EnergyAmount.obtain(0L);
    private final EnergyAmount activeReservation = EnergyAmount.obtain(0L);
    private final EnergyAmount deferredCreditTotal = EnergyAmount.obtain(0L);
    private final EnergyAmount settlementAccepted = EnergyAmount.obtain(0L);
    private final EnergyAmount settlementPiece = EnergyAmount.obtain(0L);
    private final ObjectArrayList<DeferredCredit> deferredCredits = new ObjectArrayList<>();
    @Nullable
    private HubNode.HubMetadata reservedMetadata;
    private long activeEpoch = Long.MIN_VALUE;
    private long settledEpoch = Long.MIN_VALUE;
    private long receiveCandidatePassId = Long.MIN_VALUE;
    private long exhaustedExtractPassId = Long.MIN_VALUE;
    private boolean reservationOpen;
    private int activeDeferredCredits;
    private boolean closed;

    MachineTransferAccount(MachineBindingIndex owner,
                           IEnergyHandler handler,
                           HandlerBindingPolicy policy,
                           EnergyHandlerRuntime.FailureContext failureContext,
                           Runnable quarantine) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.handler = Objects.requireNonNull(handler, "handler");
        Objects.requireNonNull(policy, "policy");
        this.failureContext = Objects.requireNonNull(failureContext, "failureContext");
        this.quarantine = Objects.requireNonNull(quarantine, "quarantine");
    }

    public IEnergyHandler handler() {
        return handler;
    }

    public void beginEpoch(long epoch) {
        requireOpen();
        if (activeEpoch != Long.MIN_VALUE) {
            throw new IllegalStateException("Machine transfer account epoch " + activeEpoch + " is still active");
        }
        if (activeDeferredCredits != 0 || deferredExtract.isPositive()) {
            throw new IllegalStateException("Machine transfer account still owns unsettled deferred credits");
        }
        activeEpoch = epoch;
        settledEpoch = Long.MIN_VALUE;
        extractBudget.reset();
        receiveBudget.reset();
    }

    public void activate(long epoch) {
        if (activeEpoch == epoch) {
            return;
        }
        if (settledEpoch == epoch) {
            throw new IllegalStateException("Machine transfer account epoch " + epoch + " is already settled");
        }
        owner.activateAccount(this, epoch);
    }

    public void endEpoch(long epoch) {
        requireOpen();
        requireEpoch(epoch);
        if (reservationOpen) {
            rollbackExtract(epoch);
        }
        if (activeDeferredCredits != 0 || deferredExtract.isPositive()) {
            throw new IllegalStateException("Machine transfer account cannot settle with deferred credits outstanding");
        }
        activeEpoch = Long.MIN_VALUE;
        settledEpoch = epoch;
    }

    public boolean isActive(long epoch) {
        return !closed && activeEpoch == epoch;
    }

    public boolean isSettled(long epoch) {
        return !closed && settledEpoch == epoch;
    }

    public boolean hasRemainingExtract(long epoch, @Nullable HubNode.HubMetadata metadata) {
        requireEpoch(epoch);
        if (reservedExtract.compareTo(deferredExtract) > 0) {
            return true;
        }
        initializeExtract(epoch, metadata);
        return extractBudget.isPositive(epoch);
    }

    public boolean hasRemainingReceive(long epoch, @Nullable HubNode.HubMetadata metadata) {
        if (activeEpoch == epoch) {
            initializeReceive(epoch, metadata);
        } else if (settledEpoch != epoch || !receiveBudget.isInitialized(epoch)) {
            throw new IllegalStateException("Machine transfer account has no receive state for epoch " + epoch);
        }
        return receiveBudget.isPositive(epoch);
    }

    public boolean claimReceiveCandidate(long passId,
                                         long epoch,
                                         @Nullable HubNode.HubMetadata metadata) {
        activate(epoch);
        if (!isActive(epoch)) {
            return false;
        }
        requireTransferPassId(passId);
        requireEpoch(epoch);
        if (receiveCandidatePassId == passId) {
            return false;
        }
        receiveCandidatePassId = passId;
        initializeReceive(epoch, metadata);
        return receiveBudget.isPositive(epoch);
    }

    public boolean hasExtractCandidate(long passId,
                                       long epoch,
                                       @Nullable HubNode.HubMetadata metadata) {
        activate(epoch);
        if (!isActive(epoch)) {
            return false;
        }
        requireTransferPassId(passId);
        requireEpoch(epoch);
        if (exhaustedExtractPassId == passId) {
            return false;
        }
        if (reservedExtract.compareTo(deferredExtract) > 0) {
            return true;
        }
        initializeExtract(epoch, metadata);
        if (extractBudget.isPositive(epoch)) {
            return true;
        }
        exhaustedExtractPassId = passId;
        return false;
    }

    public EnergyAmount remainingExtract(long epoch, @Nullable HubNode.HubMetadata metadata) {
        requireEpoch(epoch);
        initializeExtract(epoch, metadata);
        return extractBudget.snapshot(epoch).add(reservedExtract).subtract(deferredExtract);
    }
    public EnergyAmount remainingReceive(long epoch, @Nullable HubNode.HubMetadata metadata) {
        if (activeEpoch == epoch) {
            initializeReceive(epoch, metadata);
        } else if (settledEpoch != epoch || !receiveBudget.isInitialized(epoch)) {
            throw new IllegalStateException("Machine transfer account has no receive state for epoch " + epoch);
        }
        return receiveBudget.snapshot(epoch);
    }
    public EnergyAmount reserveExtract(EnergyAmount maximum,
                                       long epoch,
                                       @Nullable HubNode.HubMetadata metadata) {
        Objects.requireNonNull(maximum, "maximum");
        requireEpoch(epoch);
        if (reservationOpen) {
            throw new IllegalStateException("Machine transfer account already has an open extraction reservation");
        }
        if (!activeReservation.isZero()) {
            throw new IllegalStateException("Closed extraction reservation retains an active amount");
        }
        if (!maximum.isPositive()) {
            return EnergyAmount.obtain(0L);
        }
        activeReservation.copyFrom(reservedExtract).subtract(deferredExtract);
        if (activeReservation.isNegative()) {
            activeReservation.setZero();
            throw new IllegalStateException("Deferred extraction exceeds reserved physical escrow");
        }
        activeReservation.min(maximum);
        if (activeReservation.isPositive()) {
            EnergyAmount reserved = EnergyAmount.obtain(activeReservation);
            reservationOpen = true;
            return reserved;
        }
        activeReservation.setZero();
        initializeExtract(epoch, metadata);
        EnergyAmount request = cappedRequest(maximum, extractBudget, epoch);
        try {
            EnergyAmount extracted = EnergyHandlerRuntime.extract(handler, request, metadata, failureContext);
            EnergyAmount validated;
            try {
                validated = validateAndConsume(extracted, request, extractBudget, epoch, "extractEnergy");
            } catch (RuntimeException | Error exception) {
                extracted.recycle();
                throw exception;
            }
            reservedExtract.add(validated);
            if (validated.isPositive()) {
                reservedMetadata = metadata;
                openReservation(validated);
            }
            return validated;
        } finally {
            request.recycle();
        }
    }
    public void commitExtract(EnergyAmount accepted, long epoch) {
        Objects.requireNonNull(accepted, "accepted");
        requireEpoch(epoch);
        requireReservation();
        if (accepted.isNegative() || accepted.compareTo(activeReservation) > 0) {
            throw new IllegalArgumentException("Committed extraction exceeds the reserved amount");
        }
        EnergyAmount available = availableReservedExtract();
        try {
            if (accepted.compareTo(available) > 0) {
                throw new IllegalStateException("Committed extraction exceeds available physical escrow");
            }
        } finally {
            available.recycle();
        }
        reservedExtract.subtract(accepted);
        if (!reservedExtract.isPositive()) {
            reservedExtract.setZero();
            reservedMetadata = null;
        }
        closeReservation();
    }
    public void rollbackExtract(long epoch) {
        requireEpoch(epoch);
        requireReservation();
        closeReservation();
    }
    public boolean hasOpenExtractReservation() {
        return reservationOpen;
    }
    public EnergyAmount receive(EnergyAmount maximum,
                                long epoch,
                                @Nullable HubNode.HubMetadata metadata) {
        if (defersReceiveCommit()) {
            throw new IllegalStateException("Deferred receive requires a physical source escrow credit");
        }
        Objects.requireNonNull(maximum, "maximum");
        requireEpoch(epoch);
        initializeReceive(epoch, metadata);
        EnergyAmount request = cappedRequest(maximum, receiveBudget, epoch);
        try {
            EnergyAmount received = EnergyHandlerRuntime.receive(handler, request, metadata, failureContext);
            try {
                return validateAndConsume(received, request, receiveBudget, epoch, "receiveEnergy");
            } catch (RuntimeException | Error exception) {
                received.recycle();
                throw exception;
            }
        } finally {
            request.recycle();
        }
    }
    public boolean defersReceiveCommit() {
        return handler instanceof DeferredReceiveCommit;
    }
    public EnergyAmount receiveDeferred(EnergyAmount maximum,
                                        MachineTransferAccount source,
                                        long epoch,
                                        @Nullable HubNode.HubMetadata metadata,
                                        @Nullable EnergyMachineManager.Interaction senderInteraction,
                                        @Nullable EnergyMachineManager.Interaction receiverInteraction,
                                        EnergyMachineManager.Status status) {
        Objects.requireNonNull(maximum, "maximum");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(status, "status");
        requireEpoch(epoch);
        source.requireEpoch(epoch);
        if (!(handler instanceof DeferredReceiveCommit feedback)) {
            throw new IllegalStateException("Physical receive backend does not defer commit");
        }
        DeferredCredit credit = reusableCredit(activeDeferredCredits);
        initializeReceive(epoch, metadata);
        EnergyAmount request = cappedRequest(maximum, receiveBudget, epoch);
        EnergyAmount received = null;
        boolean sourceHeld = false;
        boolean receiveBudgetConsumed = false;
        try {
            received = EnergyHandlerRuntime.receive(handler, request, metadata, failureContext);
            validateAndConsume(received, request, receiveBudget, epoch, "receiveEnergy");
            receiveBudgetConsumed = true;
            if (!received.isPositive()) {
                return received;
            }
            source.holdDeferredExtract(received, epoch);
            sourceHeld = true;
            credit.assign(source, received, senderInteraction, receiverInteraction, status);
            activeDeferredCredits++;
            deferredCreditTotal.add(received);
            return received;
        } catch (RuntimeException | Error exception) {
            if (received != null && received.isPositive()) {
                if (sourceHeld) {
                    try {
                        source.releaseDeferredHold(received, epoch);
                    } catch (RuntimeException | Error rollbackException) {
                        exception.addSuppressed(rollbackException);
                    }
                }
                try {
                    feedback.rollbackPendingReceive(received);
                    if (receiveBudgetConsumed) {
                        receiveBudget.restore(epoch, received);
                    }
                } catch (RuntimeException | Error rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
            }
            if (received != null) {
                received.recycle();
            }
            throw exception;
        } finally {
            request.recycle();
        }
    }
    public void restoreReceive(EnergyAmount rejected, long epoch) {
        Objects.requireNonNull(rejected, "rejected");
        requireEpoch(epoch);
        if (rejected.isNegative()) {
            throw new IllegalArgumentException("Rejected receive amount cannot be negative");
        }
        if (!rejected.isPositive()) {
            return;
        }
        if (!receiveBudget.isInitialized(epoch)) {
            throw new IllegalStateException("Cannot restore an uninitialized receive budget for epoch " + epoch);
        }
        receiveBudget.restore(epoch, rejected);
    }
    public void settleDeferredReceive(EnergyAmount rejected, long epoch) {
        Objects.requireNonNull(rejected, "rejected");
        requireEpoch(epoch);
        if (!defersReceiveCommit()) {
            if (rejected.isPositive()) {
                throw new IllegalStateException("Immediate receive backend returned deferred feedback");
            }
            return;
        }
        if (rejected.isNegative() || rejected.compareTo(deferredCreditTotal) > 0) {
            throw new IllegalStateException("Deferred receive rejection exceeds recorded source credits");
        }
        settlementAccepted.copyFrom(deferredCreditTotal).subtract(rejected);
        for (int index = 0; index < activeDeferredCredits; index++) {
            DeferredCredit credit = deferredCredits.get(index);
            settlementPiece.copyFrom(credit.amount).min(settlementAccepted);
            Objects.requireNonNull(credit.source, "Deferred credit source")
                .validateDeferredSettlement(credit.amount, settlementPiece, epoch);
            settlementAccepted.subtract(settlementPiece);
        }
        settlementAccepted.copyFrom(deferredCreditTotal).subtract(rejected);
        for (int index = 0; index < activeDeferredCredits; index++) {
            DeferredCredit credit = deferredCredits.get(index);
            settlementPiece.copyFrom(credit.amount).min(settlementAccepted);
            Objects.requireNonNull(credit.source, "Deferred credit source")
                .settleDeferredExtract(credit.amount, settlementPiece, epoch);
            if (settlementPiece.isPositive()) {
                Objects.requireNonNull(credit.status, "Deferred credit status")
                    .interaction(settlementPiece, credit.senderInteraction, credit.receiverInteraction);
            }
            settlementAccepted.subtract(settlementPiece);
            credit.clear();
        }
        activeDeferredCredits = 0;
        deferredCreditTotal.setZero();
        settlementAccepted.setZero();
        settlementPiece.setZero();
        restoreReceive(rejected, epoch);
    }
    public boolean close() {
        requireOpen();
        if (reservationOpen) {
            closeReservation();
        }
        if (activeDeferredCredits != 0 || deferredExtract.isPositive()) {
            return false;
        }
        if (reservedExtract.isPositive()) {
            if (!restorePendingExtraction()) {
                return false;
            }
        }
        activeEpoch = Long.MIN_VALUE;
        settledEpoch = Long.MIN_VALUE;
        extractBudget.reset();
        receiveBudget.reset();
        receiveCandidatePassId = Long.MIN_VALUE;
        exhaustedExtractPassId = Long.MIN_VALUE;
        reservedExtract.recycle();
        deferredExtract.recycle();
        activeReservation.recycle();
        deferredCreditTotal.recycle();
        settlementAccepted.recycle();
        settlementPiece.recycle();
        for (int index = 0; index < deferredCredits.size(); index++) {
            deferredCredits.get(index).recycle();
        }
        deferredCredits.clear();
        reservedMetadata = null;
        closed = true;
        return true;
    }

    private void openReservation(EnergyAmount amount) {
        activeReservation.copyFrom(amount);
        reservationOpen = true;
    }

    private void closeReservation() {
        activeReservation.setZero();
        reservationOpen = false;
    }

    private void requireReservation() {
        if (!reservationOpen) {
            throw new IllegalStateException("Machine transfer account has no open extraction reservation");
        }
    }

    private boolean restorePendingExtraction() {
        while (reservedExtract.isPositive()) {
            EnergyAmount request = EnergyAmount.obtain(reservedExtract);
            try {
                EnergyAmount restored = EnergyHandlerRuntime.receive(handler, request, reservedMetadata, failureContext);
                try {
                if (restored.isNegative() || restored.compareTo(request) > 0) {
                    IllegalStateException violation = new IllegalStateException("Energy handler returned " + restored
                        + " while restoring reserved extraction " + request);
                    EnergyHandlerRuntime.logContractViolation(handler, "restoreExtract", failureContext, violation);
                    quarantine.run();
                    return false;
                }
                if (!restored.isPositive()) {
                    return false;
                }
                reservedExtract.subtract(restored);
                } finally {
                    restored.recycle();
                }
            } finally {
                request.recycle();
            }
        }
        reservedExtract.setZero();
        reservedMetadata = null;
        return true;
    }

    private EnergyAmount availableReservedExtract() {
        return EnergyAmount.obtain(reservedExtract).subtract(deferredExtract);
    }

    private void holdDeferredExtract(EnergyAmount amount, long epoch) {
        requireEpoch(epoch);
        requireReservation();
        if (amount.isNegative() || amount.compareTo(activeReservation) > 0) {
            throw new IllegalArgumentException("Deferred extraction exceeds the active reservation");
        }
        deferredExtract.add(amount);
        closeReservation();
    }

    private void releaseDeferredHold(EnergyAmount amount, long epoch) {
        requireEpoch(epoch);
        if (amount.isNegative() || amount.compareTo(deferredExtract) > 0) {
            throw new IllegalStateException("Deferred hold rollback exceeds assigned physical escrow");
        }
        deferredExtract.subtract(amount);
    }

    private void validateDeferredSettlement(EnergyAmount credit, EnergyAmount accepted, long epoch) {
        requireEpoch(epoch);
        if (credit.isNegative() || accepted.isNegative() || accepted.compareTo(credit) > 0
            || credit.compareTo(deferredExtract) > 0 || accepted.compareTo(reservedExtract) > 0) {
            throw new IllegalStateException("Deferred source settlement does not match physical escrow");
        }
    }

    private void settleDeferredExtract(EnergyAmount credit, EnergyAmount accepted, long epoch) {
        validateDeferredSettlement(credit, accepted, epoch);
        deferredExtract.subtract(credit);
        reservedExtract.subtract(accepted);
        if (!reservedExtract.isPositive()) {
            reservedExtract.setZero();
            reservedMetadata = null;
        }
    }

    private DeferredCredit reusableCredit(int index) {
        if (index == deferredCredits.size()) {
            deferredCredits.add(new DeferredCredit());
        }
        return deferredCredits.get(index);
    }

    private void initializeExtract(long epoch, @Nullable HubNode.HubMetadata metadata) {
        if (extractBudget.isInitialized(epoch)) {
            return;
        }
        EnergyAmount amount = EnergyHandlerRuntime.canExtract(handler, metadata, failureContext);
        try {
            extractBudget.initialize(epoch, amount);
        } finally {
            amount.recycle();
        }
    }

    private void initializeReceive(long epoch, @Nullable HubNode.HubMetadata metadata) {
        if (receiveBudget.isInitialized(epoch)) {
            return;
        }
        EnergyAmount amount = EnergyHandlerRuntime.canReceive(handler, metadata, failureContext);
        try {
            receiveBudget.initialize(epoch, amount);
        } finally {
            amount.recycle();
        }
    }

    private static EnergyAmount cappedRequest(EnergyAmount maximum, MachineTransferBudget budget, long epoch) {
        return budget.snapshot(epoch).min(maximum);
    }

    /** The caller retains ownership of {@code actual} on both success and failure. */
    private EnergyAmount validateAndConsume(EnergyAmount actual,
                                            EnergyAmount request,
                                            MachineTransferBudget budget,
                                            long epoch,
                                            String operation) {
        if (!actual.isNegative() && actual.compareTo(request) <= 0) {
            budget.consume(epoch, actual);
            return actual;
        }
        IllegalStateException violation = new IllegalStateException(
            "Energy handler returned " + actual + " for " + operation + " request " + request
        );
        EnergyHandlerRuntime.logContractViolation(handler, operation, failureContext, violation);
        if (extractBudget.isInitialized(epoch)) {
            extractBudget.exhaust(epoch);
        }
        if (receiveBudget.isInitialized(epoch)) {
            receiveBudget.exhaust(epoch);
        }
        quarantine.run();
        throw violation;
    }

    private void requireEpoch(long epoch) {
        if (activeEpoch != epoch) {
            throw new IllegalStateException("Machine transfer account epoch mismatch: expected "
                + activeEpoch + ", received " + epoch);
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Machine transfer account is closed");
        }
    }

    private static void requireTransferPassId(long passId) {
        if (passId <= 0L) {
            throw new IllegalArgumentException("Machine transfer pass id must be positive");
        }
    }

    private static final class DeferredCredit {
        private final EnergyAmount amount = EnergyAmount.obtain(0L);
        @Nullable
        private MachineTransferAccount source;
        @Nullable
        private EnergyMachineManager.Interaction senderInteraction;
        @Nullable
        private EnergyMachineManager.Interaction receiverInteraction;
        @Nullable
        private EnergyMachineManager.Status status;

        private void assign(MachineTransferAccount source,
                            EnergyAmount amount,
                            @Nullable EnergyMachineManager.Interaction senderInteraction,
                            @Nullable EnergyMachineManager.Interaction receiverInteraction,
                            EnergyMachineManager.Status status) {
            this.source = source;
            this.amount.copyFrom(amount);
            this.senderInteraction = senderInteraction;
            this.receiverInteraction = receiverInteraction;
            this.status = status;
        }

        private void clear() {
            amount.setZero();
            source = null;
            senderInteraction = null;
            receiverInteraction = null;
            status = null;
        }

        private void recycle() {
            clear();
            amount.recycle();
        }
    }
}
