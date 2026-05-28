/*
 * Decompiled with CFR 0.152.
 */
package com.worldscape.util;

import com.worldscape.util.WorldScapeUtils;

public class NoiseUtils {
    public static double combineNoises(double[] weights, double[] noises) {
        if (weights.length != noises.length) {
            throw new IllegalArgumentException("Weights and noises arrays must have the same length");
        }
        double result = 0.0;
        double totalWeight = 0.0;
        for (int i = 0; i < weights.length; ++i) {
            result += weights[i] * noises[i];
            totalWeight += weights[i];
        }
        return totalWeight > 0.0 ? result / totalWeight : 0.0;
    }

    public static double ridgeNoise(double n1, double n2) {
        double ridge = 2.0 * Math.abs(n1) - 1.0;
        ridge *= ridge;
        return ridge * (1.0 + n2 * 0.5);
    }

    public static double bowlNoise(double distance, double width) {
        return Math.exp(-Math.pow(distance / width, 2.0));
    }

    public static double cliffNoise(double distance, double width, double height) {
        double edge = WorldScapeUtils.smoothstep(0.0, width, distance);
        return height * (1.0 - edge);
    }

    public static double duneNoise(double n1, double n2, double height) {
        double phase = Math.atan2(n1, n2);
        return height * Math.sin(phase * 3.0 + n2 * 5.0);
    }

    public static double plateauNoise(double distance, double width, double topHeight, double edgeHeight, double noise) {
        double flatness = Math.exp(-Math.pow(distance / width, 2.0));
        return edgeHeight + (topHeight - edgeHeight) * flatness + noise;
    }
}

