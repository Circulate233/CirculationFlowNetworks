package com.circulation.circulation_networks.manager;

/**
 * Selects which participant-owned bucket membership a persistent role index
 * updates. Grid and channel indexes remain independent so a machine slot can
 * belong to both without a per-tick copy.
 */
enum ParticipantMembershipScope {
    GRID,
    CHANNEL
}
