/*
 * Decompiled with CFR 0.152.
 */
package com.worldscape.terrain;

public class MacroRegionInfo {
    public final int elevationTier;
    public final int secondElevationTier;
    public final double blendedBaseHeight;
    public final TectonicType tectonic;
    public final ClimateZone climate;
    public final double blendWeight;
    public final int transitionWidth;
    public final int primaryCellX;
    public final int primaryCellZ;

    public MacroRegionInfo(int elevationTier, int secondElevationTier, double blendedBaseHeight, TectonicType tectonic, ClimateZone climate, double blendWeight, int transitionWidth, int primaryCellX, int primaryCellZ) {
        this.elevationTier = elevationTier;
        this.secondElevationTier = secondElevationTier;
        this.blendedBaseHeight = blendedBaseHeight;
        this.tectonic = tectonic;
        this.climate = climate;
        this.blendWeight = blendWeight;
        this.transitionWidth = transitionWidth;
        this.primaryCellX = primaryCellX;
        this.primaryCellZ = primaryCellZ;
    }

    public double getBaseHeight() {
        return this.blendedBaseHeight;
    }

    public int getElevationTier() {
        return this.elevationTier;
    }

    public int getSecondElevationTier() {
        return this.secondElevationTier;
    }

    public double getBlendWeight() {
        return this.blendWeight;
    }

    public static enum TectonicType {
        OROGENIC_BELT,
        SUBDUCTION_ZONE,
        RIFT_ZONE,
        CRATON,
        FAULT_ZONE;

    }

    public static enum ClimateZone {
        ARID,
        GLACIAL,
        TEMPERATE,
        TROPICAL;

    }
}

