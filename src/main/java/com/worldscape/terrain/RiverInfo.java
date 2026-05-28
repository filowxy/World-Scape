/*
 * Decompiled with CFR 0.152.
 */
package com.worldscape.terrain;

public class RiverInfo {
    public static final RiverInfo EMPTY = new RiverInfo(false, 0.0, 0.0, 0.0, 0.0);
    public final boolean isRiver;
    public final double distSq;
    public final double width;
    public final double depth;
    public final double flowDirection;

    public RiverInfo(boolean isRiver, double distSq, double width, double depth, double flowDirection) {
        this.isRiver = isRiver;
        this.distSq = distSq;
        this.width = width;
        this.depth = depth;
        this.flowDirection = flowDirection;
    }

    public double getNormalizedDist() {
        if (this.width <= 0.0) {
            return 1.0;
        }
        return Math.min(Math.sqrt(this.distSq) / (this.width * 0.5), 1.0);
    }

    public boolean isWithinRiver() {
        return this.isRiver && this.width > 0.0 && this.distSq <= this.width * 0.5 * (this.width * 0.5);
    }

    public double getErosionIntensity() {
        if (!this.isRiver) {
            return 0.0;
        }
        double normalizedDist = this.getNormalizedDist();
        return 1.0 - normalizedDist;
    }

    public String toString() {
        return String.format("RiverInfo{isRiver=%b, distSq=%.2f, width=%.2f, depth=%.2f, flow=%.1f\u00b0}", this.isRiver, this.distSq, this.width, this.depth, this.flowDirection);
    }
}

