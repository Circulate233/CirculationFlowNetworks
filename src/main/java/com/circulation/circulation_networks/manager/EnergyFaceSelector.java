package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.api.IEnergyHandler.EnergyType;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

/** Selects one usable face without summing aliases of the same physical buffer. */
public final class EnergyFaceSelector<T> {
    private final List<T> candidates = new ReferenceArrayList<>(7);
    private final Predicate<T> canSend;
    private final Predicate<T> canReceive;
    private final ToLongFunction<T> extractBudget;
    private final ToLongFunction<T> receiveBudget;
    private T sender;
    private T receiver;

    public EnergyFaceSelector(Predicate<T> canSend, Predicate<T> canReceive,
                              ToLongFunction<T> extractBudget, ToLongFunction<T> receiveBudget) {
        this.canSend = canSend;
        this.canReceive = canReceive;
        this.extractBudget = extractBudget;
        this.receiveBudget = receiveBudget;
    }

    public void add(T candidate) {
        if (candidates.contains(candidate)) return;
        candidates.add(candidate);
    }

    public EnergyType role() {
        boolean sends = false, receives = false;
        for (var i = 0; i < candidates.size(); i++) {
            T candidate = candidates.get(i);
            sends |= canSend.test(candidate);
            receives |= canReceive.test(candidate);
        }
        return sends ? receives ? EnergyType.STORAGE : EnergyType.SEND
            : receives ? EnergyType.RECEIVE : EnergyType.INVALID;
    }

    public long budget(boolean input) {
        T selected = input ? receiver : sender;
        Predicate<T> permitted = input ? canReceive : canSend;
        ToLongFunction<T> probe = input ? receiveBudget : extractBudget;
        long amount = selected != null && permitted.test(selected) ? Math.max(0L, probe.applyAsLong(selected)) : 0L;
        if (amount > 0L) return amount;
        for (var i = 0; i < candidates.size(); i++) {
            T candidate = candidates.get(i);
            if (candidate == selected || !permitted.test(candidate)) continue;
            amount = Math.max(0L, probe.applyAsLong(candidate));
            if (amount > 0L) {
                if (input) receiver = candidate;
                else sender = candidate;
                return amount;
            }
        }
        return 0L;
    }

    public T sender() {
        return sender;
    }

    public T receiver() {
        return receiver;
    }

    public void clear() {
        candidates.clear();
        sender = null;
        receiver = null;
    }
}
