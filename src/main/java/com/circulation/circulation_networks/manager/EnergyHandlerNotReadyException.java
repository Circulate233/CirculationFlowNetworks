package com.circulation.circulation_networks.manager;

/**
 * Signals that an energy handler has a supported endpoint type but cannot be
 * bound because its runtime energy network has not finished initializing.
 * Callers must defer the binding and retry once the endpoint is ready.
 */
public final class EnergyHandlerNotReadyException extends IllegalStateException {

    /**
     * Creates an exception describing the unavailable runtime dependency.
     *
     * @param message reason the endpoint cannot yet be bound
     */
    public EnergyHandlerNotReadyException(String message) {
        super(message);
    }
}
