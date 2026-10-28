package com.circulation.circulation_networks.api.hub;

public enum PermissionMode {
    PUBLIC,
    TEAM,
    PRIVATE;

    private static final PermissionMode[] VALUES = values();

    public static PermissionMode fromId(int id) {
        return VALUES[Math.floorMod(id, VALUES.length)];
    }

    public int getId() {
        return this.ordinal();
    }
}