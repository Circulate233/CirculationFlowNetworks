package com.circulation.circulation_networks.math;

import java.util.Objects;

public class Vec3d {

    public final double x;
    public final double y;
    public final double z;

    public Vec3d(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static Vec3d ofCenter(Vec3i pos) {
        return new Vec3d(pos.x + 0.5D, pos.y + 0.5D, pos.z + 0.5D);
    }

    public double squareDistanceTo(double x, double y, double z) {
        double dx = this.x - x;
        double dy = this.y - y;
        double dz = this.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public double squareDistanceTo(Vec3d other) {
        return squareDistanceTo(other.x, other.y, other.z);
    }

    public double distanceTo(Vec3d other) {
        return Math.sqrt(squareDistanceTo(other));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Vec3d other)) {
            return false;
        }
        return Double.compare(x, other.x) == 0 && Double.compare(y, other.y) == 0 && Double.compare(z, other.z) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }

    @Override
    public String toString() {
        return "Vec3d{" + "x=" + x + ", y=" + y + ", z=" + z + '}';
    }
}