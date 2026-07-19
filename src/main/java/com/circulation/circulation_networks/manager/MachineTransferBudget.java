package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.api.EnergyAmount;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;

/**
 * Stores one machine transfer budget without narrowing values outside the long range.
 */
final class MachineTransferBudget {

    private long epoch = Long.MIN_VALUE;
    private long longRemaining;
    private boolean initiallyPositive;
    @Nullable
    private BigInteger bigRemaining;

    void initialize(long epoch, EnergyAmount amount) {
        this.epoch = epoch;
        initiallyPositive = amount.isPositive();
        if (!amount.isPositive()) {
            longRemaining = 0L;
            bigRemaining = null;
            return;
        }
        if (amount.fitsLong()) {
            longRemaining = amount.asLongExact();
            bigRemaining = null;
            return;
        }
        longRemaining = 0L;
        bigRemaining = amount.asBigInteger();
    }

    boolean isInitialized(long epoch) {
        return this.epoch == epoch;
    }

    boolean isPositive(long epoch) {
        requireEpoch(epoch);
        return bigRemaining != null ? bigRemaining.signum() > 0 : longRemaining > 0L;
    }

    boolean wasInitiallyPositive(long epoch) {
        requireEpoch(epoch);
        return initiallyPositive;
    }

    EnergyAmount snapshot(long epoch) {
        requireEpoch(epoch);
        return bigRemaining != null ? EnergyAmount.obtain(bigRemaining) : EnergyAmount.obtain(longRemaining);
    }

    void consume(long epoch, EnergyAmount amount) {
        requireEpoch(epoch);
        if (amount.isNegative()) {
            throw new IllegalStateException("Energy handler returned a negative transfer amount");
        }
        if (amount.isZero()) {
            return;
        }
        if (bigRemaining != null) {
            BigInteger next = bigRemaining.subtract(amount.asBigInteger());
            if (next.signum() < 0) {
                throw new IllegalStateException("Energy handler exceeded the cached machine transfer budget");
            }
            setRemaining(next);
            return;
        }
        if (!amount.fitsLong()) {
            throw new IllegalStateException("Energy handler exceeded the cached long machine transfer budget");
        }
        long consumed = amount.asLongExact();
        if (consumed > longRemaining) {
            throw new IllegalStateException("Energy handler exceeded the cached machine transfer budget");
        }
        longRemaining -= consumed;
    }

    void restore(long epoch, EnergyAmount amount) {
        requireEpoch(epoch);
        if (amount.isNegative()) {
            throw new IllegalArgumentException("Restored transfer budget cannot be negative");
        }
        if (!amount.isPositive()) {
            return;
        }
        if (bigRemaining != null || !amount.fitsLong()) {
            setRemaining((bigRemaining != null ? bigRemaining : BigInteger.valueOf(longRemaining))
                .add(amount.asBigInteger()));
            return;
        }
        long restored = amount.asLongExact();
        if (Long.MAX_VALUE - longRemaining < restored) {
            setRemaining(BigInteger.valueOf(longRemaining).add(BigInteger.valueOf(restored)));
            return;
        }
        longRemaining += restored;
    }

    void exhaust(long epoch) {
        requireEpoch(epoch);
        longRemaining = 0L;
        bigRemaining = null;
    }

    void reset() {
        epoch = Long.MIN_VALUE;
        longRemaining = 0L;
        bigRemaining = null;
        initiallyPositive = false;
    }

    private void setRemaining(BigInteger value) {
        if (value.bitLength() <= 63) {
            longRemaining = value.longValueExact();
            bigRemaining = null;
            return;
        }
        longRemaining = 0L;
        bigRemaining = value;
    }

    private void requireEpoch(long epoch) {
        if (!isInitialized(epoch)) {
            throw new IllegalStateException("Machine transfer budget was not initialized for epoch " + epoch);
        }
    }
}
