package com.circulation.circulation_networks.api.hub;

public enum HubPermissionLevel {
    NONE,
    MEMBER,
    ADMIN,
    OWNER;

    private static final HubPermissionLevel[] VALUES = values();

    public static HubPermissionLevel fromId(int id) {
        return VALUES[Math.floorMod(id, VALUES.length)];
    }

    public int getId() {
        return ordinal();
    }

    public boolean canEditPermissions() {
        return this == OWNER || this == ADMIN;
    }
}
