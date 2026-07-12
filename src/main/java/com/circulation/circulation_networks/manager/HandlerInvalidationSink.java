package com.circulation.circulation_networks.manager;

/**
 * Receives a handler-originated notification that its current block-entity
 * binding can no longer be used. Implementations revoke the binding before
 * any later transfer pass observes the stale capability.
 */
public interface HandlerInvalidationSink {

    /**
     * Invalidates the binding associated with this sink. The operation is
     * idempotent so a capability may report repeated invalidation events.
     */
    void invalidate();

    /**
     * Reports that a mapped endpoint may now resolve to a different backend.
     * Repeated notifications are coalesced until the next mapping refresh.
     */
    void backendChanged();

    /**
     * Suspends polling without scheduling an automatic capability rebind. The
     * owning block entity must be explicitly bound again before it is visible.
     */
    void suspendUntilRebind();
}
