package com.circulation.circulation_networks.math;

import org.jetbrains.annotations.NotNull;

public class Vec3i {

    public final int x;
    public final int y;
    public final int z;

    public Vec3i(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double distanceSquared(double x, double y, double z) {
        double dx = this.x - x;
        double dy = this.y - y;
        double dz = this.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public double distanceSquared(Vec3i other) {
        return distanceSquared(other.x, other.y, other.z);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Vec3i other)) {
            return false;
        }
        return x == other.x && y == other.y && z == other.z;
    }

    @Override
    @NotNull
    public String toString() {
        return "Vec3i{" + "x=" + x + ", y=" + y + ", z=" + z + '}';
    }
}