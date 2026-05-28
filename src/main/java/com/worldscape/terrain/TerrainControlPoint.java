/*
 * Decompiled with CFR 0.152.
 */
package com.worldscape.terrain;

import com.worldscape.terrain.TerrainType;

public class TerrainControlPoint {
    public final int x;
    public final int z;
    public final TerrainType terrainType;
    public final double elevationOffset;
    public final double influenceRadius;

    public TerrainControlPoint(int x, int z, TerrainType terrainType, double elevationOffset, double influenceRadius) {
        this.x = x;
        this.z = z;
        this.terrainType = terrainType;
        this.elevationOffset = elevationOffset;
        this.influenceRadius = influenceRadius;
    }

    private double clampOffset(TerrainType type, double rawOffset) {
        double minOffset;
        double d = minOffset = 0.0;
        return Math.max(d, Math.min(switch (type) {
            case TerrainType.PLAINS -> {
                minOffset = -5.0;
                yield 5.0;
            }
            case TerrainType.HILLS -> {
                minOffset = 10.0;
                yield 30.0;
            }
            case TerrainType.RIDGE -> {
                minOffset = 40.0;
                yield 80.0;
            }
            case TerrainType.HIGH_MOUNTAINS -> {
                minOffset = 80.0;
                yield 150.0;
            }
            case TerrainType.CANYON -> {
                minOffset = -80.0;
                yield -30.0;
            }
            case TerrainType.DUNE -> {
                minOffset = 0.0;
                yield 15.0;
            }
            case TerrainType.PLATEAU -> {
                minOffset = 30.0;
                yield 60.0;
            }
            case TerrainType.DOME -> {
                minOffset = 15.0;
                yield 35.0;
            }
            case TerrainType.CLIFF -> {
                minOffset = 0.0;
                yield 0.0;
            }
            case TerrainType.BASIN -> {
                minOffset = -30.0;
                yield -10.0;
            }
            case TerrainType.GLACIAL_VALLEY -> {
                minOffset = -40.0;
                yield -10.0;
            }
            case TerrainType.BEACH -> {
                minOffset = -2.0;
                yield 6.0;
            }
            case TerrainType.TRENCH -> {
                minOffset = -100.0;
                yield -60.0;
            }
            case TerrainType.SEA_PLATEAU -> {
                minOffset = -30.0;
                yield -10.0;
            }
            case TerrainType.DELTA -> {
                minOffset = -5.0;
                yield 5.0;
            }
            default -> {
                minOffset = -10.0;
                yield 10.0;
            }
        }, rawOffset));
    }

    public double calculateInfluence(int targetX, int targetZ) {
        double dx = targetX - this.x;
        double dz = targetZ - this.z;
        double distanceSq = dx * dx + dz * dz;
        double distance = Math.sqrt(distanceSq);
        if (distance > this.influenceRadius) {
            return 0.0;
        }
        double normalizedDist = distance / this.influenceRadius;
        return (1.0 - normalizedDist) * (1.0 - normalizedDist);
    }

    public double calculateInfluenceFromSquaredDist(double distanceSq) {
        double radiusSq = this.influenceRadius * this.influenceRadius;
        if (distanceSq > radiusSq) {
            return 0.0;
        }
        double normalizedDist = Math.sqrt(distanceSq) / this.influenceRadius;
        return (1.0 - normalizedDist) * (1.0 - normalizedDist);
    }

    public double squaredDistanceTo(int targetX, int targetZ) {
        double dx = targetX - this.x;
        double dz = targetZ - this.z;
        return dx * dx + dz * dz;
    }

    public int getX() {
        return this.x;
    }

    public int getZ() {
        return this.z;
    }

    public TerrainType getTerrainType() {
        return this.terrainType;
    }

    public double getElevationOffset() {
        return this.elevationOffset;
    }

    public double getRadius() {
        return this.influenceRadius;
    }
}

