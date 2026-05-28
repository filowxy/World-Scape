/*
 * Decompiled with CFR 0.152.
 */
package com.worldscape.util;

import java.util.Random;

public class WorldScapeUtils {
    public static long hash(long seed, int x, int z) {
        return seed ^ (long)x * 31L + (long)z * 17L;
    }

    public static double distance(double x1, double z1, double x2, double z2) {
        double dx = x2 - x1;
        double dz = z2 - z1;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    public static double smoothstep(double edge0, double edge1, double x) {
        double t = WorldScapeUtils.clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    public static double gaussian(double x, double mean, double stdDev) {
        double exponent = -Math.pow(x - mean, 2.0) / (2.0 * Math.pow(stdDev, 2.0));
        return Math.exp(exponent);
    }

    public static double randomRange(Random random, double min, double max) {
        return min + random.nextDouble() * (max - min);
    }
}

