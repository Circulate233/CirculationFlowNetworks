package com.circulation.circulation_networks.api;

/**
 * Describes structural state discovered when a bound handler opens a server
 * tick. The binding index uses this value to avoid unconditional route work.
 */
public enum HandlerTickResult {
    /** No role, mapping, or binding state changed. */
    UNCHANGED,
    /** Structural state changed and affected routes must be committed. */
    STATE_CHANGED,
    /** Revoke this binding and retry with bounded backoff at a later tick. */
    RETRY_BINDING,
    /** Stop polling this binding until an external lifecycle event rebinds it. */
    SUSPEND_UNTIL_REBIND
}
