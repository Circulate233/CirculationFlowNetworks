package com.circulation.circulation_networks.manager;

import com.circulation.circulation_networks.api.EnergyAmount;

/**
 * Reports the physical result of a backend that commits accepted receive
 * transfers at the end of a server tick. The binding index applies rejected
 * energy to both the receiver budget and the physical source escrow before
 * settling the epoch.
 */
public interface DeferredReceiveCommit {

    /**
     * Drains the amount accepted logically during the tick but rejected by the
     * physical commit. The result is owned by the caller and must be recycled.
     * Repeated drains without another completed tick return zero.
     *
     * @return physical receive amount to restore to account remaining
     */
    EnergyAmount drainRejectedReceive();

    /**
     * Removes a logical receive that could not be assigned a source credit.
     * Implementations must perform this synchronously without touching the
     * physical backend, because the corresponding source reservation remains
     * uncommitted.
     *
     * @param amount logical pending receive to remove
     */
    void rollbackPendingReceive(EnergyAmount amount);
}
