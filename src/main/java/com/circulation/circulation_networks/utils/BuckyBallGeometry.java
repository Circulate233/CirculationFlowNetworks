package com.circulation.circulation_networks.utils;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public final class BuckyBallGeometry {
    public static final List<Vec3d> vertices = new ObjectArrayList<>();
    public static final ObjectList<int[]> edges = new ObjectArrayList<>();

    static {
        double p = (1.0 + Math.sqrt(5.0)) / 2.0;

        double[][] base = {
            {0, 1, 3 * p}, {0, 1, -3 * p}, {0, -1, 3 * p}, {0, -1, -3 * p},
            {1, 2 + p, 2 * p}, {1, 2 + p, -2 * p}, {1, -(2 + p), 2 * p}, {1, -(2 + p), -2 * p},
            {-1, 2 + p, 2 * p}, {-1, 2 + p, -2 * p}, {-1, -(2 + p), 2 * p}, {-1, -(2 + p), -2 * p},
            {p, 2, 2 * p + 1}, {p, 2, -(2 * p + 1)}, {p, -2, 2 * p + 1}, {p, -2, -(2 * p + 1)},
            {-p, 2, 2 * p + 1}, {-p, 2, -(2 * p + 1)}, {-p, -2, 2 * p + 1}, {-p, -2, -(2 * p + 1)}
        };

        for (double[] v : base) {
            double length = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
            vertices.add(new Vec3d(v[0] / length, v[1] / length, v[2] / length));
            vertices.add(new Vec3d(v[1] / length, v[2] / length, v[0] / length));
            vertices.add(new Vec3d(v[2] / length, v[0] / length, v[1] / length));
        }

        for (int i = 0; i < vertices.size(); i++) {
            for (int j = i + 1; j < vertices.size(); j++) {
                if (vertices.get(i).distanceTo(vertices.get(j)) < 0.41) {
                    edges.add(new int[]{i, j});
                }
            }
        }
    }
}
