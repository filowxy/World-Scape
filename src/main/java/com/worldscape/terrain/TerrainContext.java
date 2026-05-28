/*
 * Decompiled with CFR 0.152.
 */
package com.worldscape.terrain;

public class TerrainContext {
    private final double n1;
    private final double n2;
    private final double n3;
    private final double distance;
    private final long seed;

    public TerrainContext(double n1, double n2, double n3, double distance, long seed) {
        this.n1 = n1;
        this.n2 = n2;
        this.n3 = n3;
        this.distance = distance;
        this.seed = seed;
    }

    public double getN1() {
        return this.n1;
    }

    public double getN2() {
        return this.n2;
    }

    public double getN3() {
        return this.n3;
    }

    public double getDistance() {
        return this.distance;
    }

    public long getSeed() {
        return this.seed;
    }
}

