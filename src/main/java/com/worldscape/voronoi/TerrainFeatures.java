package com.worldscape.voronoi;

public class TerrainFeatures {
    private final double elevation;
    private final double slope;
    private final double aspect;
    private final double roughness;

    public TerrainFeatures(double elevation, double slope, double aspect, double roughness) {
        this.elevation = elevation;
        this.slope = slope;
        this.aspect = aspect;
        this.roughness = roughness;
    }

    public double getElevation() {
        return this.elevation;
    }

    public double getSlope() {
        return this.slope;
    }

    public double getAspect() {
        return this.aspect;
    }

    public double getRoughness() {
        return this.roughness;
    }

    public String toString() {
        return "TerrainFeatures{elevation=" + this.elevation + ", slope=" + this.slope + ", aspect=" + this.aspect + ", roughness=" + this.roughness + "}";
    }
}

