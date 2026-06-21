package com.worldscape.terrain;

import com.worldscape.terrain.TerrainType;

public class TerrainControlPoint {
    public final int x;
    public final int z;
    public final TerrainType terrainType;
    public final double elevationOffset;
    public final double influenceRadius;

    public TerrainControlPoint(int x, int z, TerrainType terrainType, double elevationOffset, double influenceRadius) {
        // 参数验证：influenceRadius 必须为正数，防止除零和无效影响范围
        // Parameter validation: influenceRadius must be positive to prevent division by zero and invalid influence ranges
        if (influenceRadius <= 0.0) throw new IllegalArgumentException("influenceRadius must be positive, got: " + influenceRadius);
        this.x = x;
        this.z = z;
        this.terrainType = terrainType;
        // 构造时钳制偏移量，确保海拔偏移在合法范围内
        // Clamp offset on construction to ensure elevation offset is within valid range
        this.elevationOffset = this.clampOffset(terrainType, elevationOffset);
        this.influenceRadius = influenceRadius;
    }

    private double clampOffset(TerrainType type, double rawOffset) {
        double minOffset = 0.0;
        double maxOffset;
        if (type == TerrainType.PLAINS) {
            maxOffset = 5.0;
            minOffset = -5.0;
        } else if (type == TerrainType.HILLS) {
            maxOffset = 30.0;
            minOffset = 10.0;
        } else if (type == TerrainType.RIDGE) {
            maxOffset = 80.0;
            minOffset = 40.0;
        } else if (type == TerrainType.HIGH_MOUNTAINS) {
            maxOffset = 150.0;
            minOffset = 80.0;
        } else if (type == TerrainType.CANYON) {
            // Deep erosional cut: modifier -50.0, energy*50-50=[-50,0] → clamps to [-50,-30]
            maxOffset = -30.0;
            minOffset = -80.0;
        } else if (type == TerrainType.DUNE) {
            maxOffset = 15.0;
            minOffset = 0.0;
        } else if (type == TerrainType.PLATEAU) {
            maxOffset = 60.0;
            minOffset = 30.0;
        } else if (type == TerrainType.DOME) {
            maxOffset = 35.0;
            minOffset = 15.0;
        } else if (type == TerrainType.CLIFF) {
            maxOffset = 0.0;
            minOffset = 0.0;
        } else if (type == TerrainType.BASIN) {
            // Broad depression: modifier -25.0, energy*50-25=[-25,25] → clamps to [-25,-10]
            maxOffset = -10.0;
            minOffset = -30.0;
        } else if (type == TerrainType.GLACIAL_VALLEY) {
            // U-shaped glacial depression: modifier -30.0, energy*50-30=[-30,20] → clamps to [-30,-10]
            maxOffset = -10.0;
            minOffset = -40.0;
        } else if (type == TerrainType.VALLEY) {
            // Moderate erosional depression: modifier -15.0, energy*50-15=[-15,35] → clamps to [-15,-5]
            maxOffset = -5.0;
            minOffset = -25.0;
        } else if (type == TerrainType.CIRQUE) {
            // Bowl-shaped glacial depression: modifier -20.0, energy*50-20=[-20,30] → clamps to [-20,-5]
            maxOffset = -5.0;
            minOffset = -30.0;
        } else if (type == TerrainType.FJORD) {
            // Deep coastal inlet: modifier -30.0, energy*50-30=[-30,20] → clamps to [-30,-10]
            maxOffset = -10.0;
            minOffset = -40.0;
        } else if (type == TerrainType.SINKHOLE) {
            // Deep narrow depression: modifier -25.0, energy*50-25=[-25,25] → clamps to [-25,-10]
            maxOffset = -10.0;
            minOffset = -30.0;
        } else if (type == TerrainType.BEACH) {
            maxOffset = 6.0;
            minOffset = -2.0;
        } else if (type == TerrainType.TRENCH) {
            maxOffset = -60.0;
            minOffset = -100.0;
        } else if (type == TerrainType.SEA_PLATEAU) {
            maxOffset = -10.0;
            minOffset = -30.0;
        } else if (type == TerrainType.DELTA) {
            maxOffset = 5.0;
            minOffset = -5.0;
        } else {
            maxOffset = 10.0;
            minOffset = -10.0;
        }
        return Math.max(minOffset, Math.min(maxOffset, rawOffset));
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

