package com.circulation.circulation_networks.manager;

import java.util.Objects;

/**
 * Immutable, shared description of an energy handler's binding behavior.
 * Every valid field combination is allocated once during class initialization;
 * {@link #of(TickLifecycle, RoleScope, MappingScope, PairMatching)} always
 * returns that canonical value and therefore performs no allocation.
 */
public final class HandlerBindingPolicy {

    private static final HandlerBindingPolicy[][][][] VALUES = createValues();

    /** Static handler with a fixed role, no mapping, and no pair restriction. */
    public static final HandlerBindingPolicy DEFAULT = of(
        TickLifecycle.STATIC,
        RoleScope.FIXED,
        MappingScope.NONE,
        PairMatching.NONE
    );

    private final TickLifecycle tickLifecycle;
    private final RoleScope roleScope;
    private final MappingScope mappingScope;
    private final PairMatching pairMatching;

    private HandlerBindingPolicy(TickLifecycle tickLifecycle,
                                 RoleScope roleScope,
                                 MappingScope mappingScope,
                                 PairMatching pairMatching) {
        this.tickLifecycle = tickLifecycle;
        this.roleScope = roleScope;
        this.mappingScope = mappingScope;
        this.pairMatching = pairMatching;
    }

    /**
     * Returns the canonical policy for the supplied behavior combination.
     *
     * @param tickLifecycle lifecycle callback requirement
     * @param roleScope role stability requirement
     * @param mappingScope handler mapping requirement
     * @param pairMatching pair compatibility requirement
     * @return preallocated shared policy value
     */
    public static HandlerBindingPolicy of(TickLifecycle tickLifecycle,
                                          RoleScope roleScope,
                                          MappingScope mappingScope,
                                          PairMatching pairMatching) {
        return VALUES[Objects.requireNonNull(tickLifecycle, "tickLifecycle").ordinal()]
            [Objects.requireNonNull(roleScope, "roleScope").ordinal()]
            [Objects.requireNonNull(mappingScope, "mappingScope").ordinal()]
            [Objects.requireNonNull(pairMatching, "pairMatching").ordinal()];
    }

    /**
     * Returns when the handler participates in server-tick lifecycle calls.
     *
     * @return lifecycle requirement
     */
    public TickLifecycle tickLifecycle() {
        return tickLifecycle;
    }

    /**
     * Returns how often the handler's transfer role may change.
     *
     * @return role stability requirement
     */
    public RoleScope roleScope() {
        return roleScope;
    }

    /**
     * Returns whether the active handler is mapped from another binding.
     *
     * @return mapping requirement
     */
    public MappingScope mappingScope() {
        return mappingScope;
    }

    /**
     * Returns whether transfer candidates must pass reciprocal matching.
     *
     * @return pair matching requirement
     */
    public PairMatching pairMatching() {
        return pairMatching;
    }

    private static HandlerBindingPolicy[][][][] createValues() {
        TickLifecycle[] tickLifecycles = TickLifecycle.values();
        RoleScope[] roleScopes = RoleScope.values();
        MappingScope[] mappingScopes = MappingScope.values();
        PairMatching[] pairMatchings = PairMatching.values();
        HandlerBindingPolicy[][][][] values = new HandlerBindingPolicy[tickLifecycles.length][roleScopes.length]
            [mappingScopes.length][pairMatchings.length];
        for (TickLifecycle tickLifecycle : tickLifecycles) {
            for (RoleScope roleScope : roleScopes) {
                for (MappingScope mappingScope : mappingScopes) {
                    for (PairMatching pairMatching : pairMatchings) {
                        values[tickLifecycle.ordinal()][roleScope.ordinal()][mappingScope.ordinal()][pairMatching.ordinal()]
                            = new HandlerBindingPolicy(tickLifecycle, roleScope, mappingScope, pairMatching);
                    }
                }
            }
        }
        return values;
    }

    /**
     * Defines which server-tick callbacks the index invokes for a binding.
     */
    public enum TickLifecycle {

        /** The handler does not need a per-tick lifecycle callback. */
        STATIC,
        /** The handler needs {@code beginServerTick(epoch)} only. */
        BEGIN_TICK,
        /** The handler needs matching begin and end callbacks. */
        BEGIN_END_TICK
    }

    /**
     * Defines the duration for which a handler's send/receive/storage role is
     * valid.
     */
    public enum RoleScope {

        /** The role remains fixed until the block-entity binding is removed. */
        FIXED,
        /** The role may change for a new server tick. */
        RUNTIME_DYNAMIC,
        /** The role may differ for each endpoint using a shared backend. */
        ENDPOINT_DYNAMIC
    }

    /**
     * Defines how a block-entity handler maps to its active transfer backend.
     */
    public enum MappingScope {

        /** The bound handler is itself the active transfer backend. */
        NONE,
        /** The active backend may change between server ticks. */
        RUNTIME_DYNAMIC,
        /** Multiple endpoints may share one stable transfer backend. */
        SHARED_BACKEND
    }

    /**
     * Defines whether two transfer participants require reciprocal matching.
     */
    public enum PairMatching {

        /**
         * Normal role-compatible transfer is sufficient. A zero actual receive
         * rejects the shared physical account for only the current transfer
         * invocation; it neither consumes receive budget nor prevents a later
         * invocation from retrying the account.
         */
        NONE,
        /** Both participants must accept the pair before transferring energy. */
        REQUIRED
    }
}
