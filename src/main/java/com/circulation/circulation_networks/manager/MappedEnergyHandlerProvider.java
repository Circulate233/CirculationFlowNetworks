package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.api.IEnergyHandler;
import org.jetbrains.annotations.NotNull;

/**
 * Resolves the transfer-ready handler behind a bound machine handler when its
 * {@link HandlerBindingPolicy.MappingScope} is not {@code NONE}. Providers
 * encapsulate dynamic and shared-backend mapping without restoring the former
 * handler-level resolve hook.
 */
public interface MappedEnergyHandlerProvider {

    /**
     * Resolves the active transfer handler after the source handler has
     * entered the supplied server-tick epoch.
     *
     * @param boundHandler handler directly bound to the block entity
     * @param epoch current server-tick epoch, or {@link Long#MIN_VALUE} while binding
     * @return non-null transfer-ready handler
     */
    @NotNull
    IEnergyHandler resolveRuntime(IEnergyHandler boundHandler, long epoch);

    /**
     * Acquires one endpoint's reference to a shared transfer backend. The
     * index invokes lifecycle callbacks on the returned backend once per epoch
     * regardless of how many endpoint bindings acquired it.
     *
     * @param boundHandler handler directly bound to the endpoint
     * @return non-null shared transfer backend
     */
    @NotNull
    IEnergyHandler acquireSharedBackend(IEnergyHandler boundHandler);

    /**
     * Releases a reference previously obtained through
     * {@link #acquireSharedBackend(IEnergyHandler)}.
     *
     * @param boundHandler endpoint handler that acquired the backend
     * @param sharedBackend backend to release
     */
    void releaseSharedBackend(IEnergyHandler boundHandler, IEnergyHandler sharedBackend);
}
