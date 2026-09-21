package com.circulation.circulation_networks.utils;

import com.circulation.circulation_networks.api.EnergyAmount;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

public final class EnergyAmountConversionUtils {

    private static final BigDecimal DOUBLE_MAX = BigDecimal.valueOf(Double.MAX_VALUE);
    private static final BigInteger POSITIVE_UNBOUNDED = DOUBLE_MAX.toBigInteger().shiftLeft(8);
    private static final BigInteger NEGATIVE_UNBOUNDED = POSITIVE_UNBOUNDED.negate();

    private EnergyAmountConversionUtils() {
    }

    public static EnergyAmount obtainFromDoubleFloor(double value) {
        return setFromDoubleFloor(EnergyAmount.obtain(0L), value);
    }

    public static EnergyAmount setFromDoubleFloor(EnergyAmount target, double value) {
        if (Double.isNaN(value)) {
            return target.setZero();
        }
        if (value == Double.POSITIVE_INFINITY) {
            return target.init(POSITIVE_UNBOUNDED);
        }
        if (value == Double.NEGATIVE_INFINITY) {
            return target.init(NEGATIVE_UNBOUNDED);
        }
        if (value >= Long.MIN_VALUE && value <= Long.MAX_VALUE) {
            return target.init((long) value);
        }
        return target.init(BigDecimal.valueOf(value).toBigInteger());
    }

    public static EnergyAmount setFromDoubleCeiling(EnergyAmount target, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Energy amount conversion requires a finite value");
        }
        if (value >= Long.MIN_VALUE && value <= Long.MAX_VALUE) {
            return target.init((long) Math.ceil(value));
        }
        return target.init(BigDecimal.valueOf(value).setScale(0, RoundingMode.CEILING).toBigIntegerExact());
    }

    public static double toDoubleClamped(EnergyAmount amount) {
        if (amount == null || !amount.isInitialized() || amount.isZero()) {
            return 0.0D;
        }
        if (amount.fitsLong()) {
            return amount.asLongExact();
        }
        double d = amount.asBigInteger().doubleValue();
        return Double.isInfinite(d) ? Math.copySign(Double.MAX_VALUE, d) : d;
    }
}
