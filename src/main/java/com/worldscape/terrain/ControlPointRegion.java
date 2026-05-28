/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.util.RandomSource
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.worldscape.terrain;

import com.worldscape.terrain.TerrainControlPoint;
import com.worldscape.terrain.TerrainFieldSampler;
import com.worldscape.terrain.TerrainType;
import com.worldscape.util.SeedDeriver;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.util.RandomSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ControlPointRegion {
    private static final Logger LOGGER = LoggerFactory.getLogger(ControlPointRegion.class);
    public static final int REGION_SIZE = 512;
    public static final int CELL_SIZE = 16;
    public static final int MAX_ADJACENT_HEIGHT_DIFF = 300;
    private static final int GRID_SPACING = 512;
    private static final int NEIGHBOR_SEARCH_RADIUS = 1024;
    private static final int MAX_ITERATIONS = 12;
    private static final double CONVERGENCE_THRESHOLD = 1.0;
    private static final int OFFSET_DIFF_WARN_THRESHOLD = 200;
    private static final int EXPECTED_MIN_CONTROL_POINTS = 2;
    private static final int EXPECTED_MAX_CONTROL_POINTS = 8;
    private final int regionX;
    private final int regionZ;
    private final long worldSeed;
    private final int macroElevationTier;
    private final List<TerrainControlPoint> controlPoints;
    private final List<TerrainControlPoint>[][] cellIndex;
    private final int cellsPerSide;

    public ControlPointRegion(int regionX, int regionZ, long worldSeed, int macroElevationTier) {
        this.regionX = regionX;
        this.regionZ = regionZ;
        this.worldSeed = worldSeed;
        if (macroElevationTier < 0 || macroElevationTier > 5) {
            LOGGER.warn("[World Scape] Invalid macroElevationTier {} in ControlPointRegion ({},{}), clamping to valid range", new Object[]{macroElevationTier, regionX, regionZ});
            macroElevationTier = Math.max(0, Math.min(5, macroElevationTier));
        }
        this.macroElevationTier = macroElevationTier;
        this.cellsPerSide = 32;
        this.cellIndex = new List[this.cellsPerSide][this.cellsPerSide];
        this.controlPoints = new ArrayList<TerrainControlPoint>();
        this.generateControlPoints();
        this.validateControlPoints();
    }

    private void generateControlPoints() {
        long regionSeed = SeedDeriver.deriveSeed(this.worldSeed, (long)this.regionX * 31L + (long)this.regionZ * 17L + 388350381470L);
        RandomSource random = RandomSource.create((long)regionSeed);
        TerrainFieldSampler fieldSampler = TerrainFieldSampler.getOrCreate(this.worldSeed);
        int pointsPerAxis = Math.max(2, 1);
        ArrayList<PointData> rawPoints = new ArrayList<PointData>();
        for (int gx = 0; gx <= pointsPerAxis; ++gx) {
            for (int gz = 0; gz <= pointsPerAxis; ++gz) {
                int baseX = this.regionX * 512 + gx * 512;
                int baseZ = this.regionZ * 512 + gz * 512;
                int offsetX = (int)((random.nextDouble() - 0.5) * 512.0 * 0.75);
                int offsetZ = (int)((random.nextDouble() - 0.5) * 512.0 * 0.75);
                int px = baseX + offsetX;
                int pz = baseZ + offsetZ;
                double energy = fieldSampler.sampleEnergy(px, pz);
                double moisture = fieldSampler.sampleMoisture(px, pz);
                int tier = fieldSampler.energyToTier(energy, this.macroElevationTier);
                TerrainType type = fieldSampler.selectTypeByMoisture(tier, moisture);
                double rawOffset = fieldSampler.calculateContinuousOffset(energy, type);
                double radius = this.calculateInfluenceRadius(px, pz, type, random);
                rawPoints.add(new PointData(px, pz, type, rawOffset, radius));
            }
        }
        this.applyNeighborConstraintIterative(rawPoints);
        for (PointData pd : rawPoints) {
            TerrainControlPoint point = new TerrainControlPoint(pd.x, pd.z, pd.type, pd.constrainedOffset, pd.radius);
            this.controlPoints.add(point);
            int cellX = Math.floorDiv(pd.x - this.regionX * 512, 16);
            int cellZ = Math.floorDiv(pd.z - this.regionZ * 512, 16);
            if (cellX < 0 || cellX >= this.cellsPerSide || cellZ < 0 || cellZ >= this.cellsPerSide) continue;
            if (this.cellIndex[cellX][cellZ] == null) {
                this.cellIndex[cellX][cellZ] = new ArrayList<TerrainControlPoint>(2);
            }
            this.cellIndex[cellX][cellZ].add(point);
        }
    }

    private void applyNeighborConstraintIterative(List<PointData> allPoints) {
        for (PointData pd : allPoints) {
            pd.constrainedOffset = pd.rawOffset;
        }
        double neighborSearchRadiusSq = 1048576.0;
        int maxIter = Math.min(12, 10);
        for (int iteration = 0; iteration < maxIter; ++iteration) {
            double maxChange = 0.0;
            for (PointData point : allPoints) {
                double oldOffset;
                double newOffset = oldOffset = point.constrainedOffset;
                for (PointData neighbor : allPoints) {
                    int maxAllowedDiff;
                    double neighborOffset;
                    double diff;
                    double dz;
                    double dx;
                    double distSq;
                    if (neighbor == point || (distSq = (dx = (double)(neighbor.x - point.x)) * dx + (dz = (double)(neighbor.z - point.z)) * dz) > neighborSearchRadiusSq || !((diff = Math.abs(newOffset - (neighborOffset = neighbor.constrainedOffset))) > (double)(maxAllowedDiff = this.calculateAdaptiveMaxHeightDiff(point.type, neighbor.type)))) continue;
                    if (newOffset > neighborOffset) {
                        newOffset = neighborOffset + (double)maxAllowedDiff;
                        continue;
                    }
                    newOffset = neighborOffset - (double)maxAllowedDiff;
                }
                point.constrainedOffset = newOffset;
                maxChange = Math.max(maxChange, Math.abs(newOffset - oldOffset));
            }
            if (!(maxChange < 1.0)) continue;
            LOGGER.debug("[World Scape] Region ({},{}) constraint converged at iteration {} (maxChange={})", new Object[]{this.regionX, this.regionZ, iteration + 1, String.format("%.2f", maxChange)});
            break;
        }
        this.applyTerrainTypeConstraints(allPoints);
    }

    private int calculateAdaptiveMaxHeightDiff(TerrainType t1, TerrainType t2) {
        if (this.isNaturalCliffPair(t1, t2)) {
            return 300;
        }
        int level1 = this.getTerrainLevel(t1);
        int level2 = this.getTerrainLevel(t2);
        int levelDiff = Math.abs(level1 - level2);
        int baseDiff = 100 + levelDiff * 100;
        boolean hasExtreme = this.isExtremeTerrain(t1) || this.isExtremeTerrain(t2);
        boolean bl = hasExtreme;
        if (hasExtreme && levelDiff >= 3) {
            baseDiff += 100;
        }
        return Math.max(100, Math.min(500, baseDiff));
    }

    private boolean isNaturalCliffPair(TerrainType t1, TerrainType t2) {
        if (t1 == TerrainType.CLIFF || t2 == TerrainType.CLIFF) {
            TerrainType other = t1 == TerrainType.CLIFF ? t2 : t1;
            TerrainType terrainType = other;
            if (other == TerrainType.CLIFF) {
                return true;
            }
            return switch (other) {
                case TerrainType.HIGH_MOUNTAINS, TerrainType.RIDGE, TerrainType.PEAK, TerrainType.HORN, TerrainType.CIRQUE, TerrainType.PLATEAU -> true;
                case TerrainType.HILLS -> true;
                case TerrainType.CANYON, TerrainType.VALLEY, TerrainType.GLACIAL_VALLEY, TerrainType.FJORD -> true;
                case TerrainType.SEA_CLIFF -> true;
                default -> false;
            };
        }
        boolean isHighMountain = t1 == TerrainType.HIGH_MOUNTAINS || t2 == TerrainType.HIGH_MOUNTAINS;
        boolean isHorn = t1 == TerrainType.HORN || t2 == TerrainType.HORN;
        boolean isLowlandErosion = t1 == TerrainType.CANYON || t2 == TerrainType.CANYON || t1 == TerrainType.TRENCH || t2 == TerrainType.TRENCH || t1 == TerrainType.GLACIAL_VALLEY || t2 == TerrainType.GLACIAL_VALLEY || t1 == TerrainType.SINKHOLE || t2 == TerrainType.SINKHOLE;
        boolean bl = isLowlandErosion;
        if ((isHighMountain || isHorn) && isLowlandErosion) {
            return true;
        }
        boolean isPlateau = t1 == TerrainType.PLATEAU || t2 == TerrainType.PLATEAU || t1 == TerrainType.DOME || t2 == TerrainType.DOME || t1 == TerrainType.SEA_CLIFF || t2 == TerrainType.SEA_CLIFF;
        boolean isDeepCut = t1 == TerrainType.CANYON || t2 == TerrainType.CANYON || t1 == TerrainType.TRENCH || t2 == TerrainType.TRENCH || t1 == TerrainType.FJORD || t2 == TerrainType.FJORD;
        return isPlateau && isDeepCut;
    }

    private boolean isExtremeTerrain(TerrainType type) {
        return switch (type) {
            case TerrainType.HIGH_MOUNTAINS, TerrainType.HORN, TerrainType.CANYON, TerrainType.CLIFF, TerrainType.TRENCH -> true;
            default -> false;
        };
    }

    private void applyTerrainTypeConstraints(List<PointData> allPoints) {
        double neighborSearchRadiusSq = 1048576.0;
        for (int iteration = 0; iteration < 5; ++iteration) {
            boolean converged = true;
            for (PointData point : allPoints) {
                for (PointData neighbor : allPoints) {
                    int typeDifference;
                    double dz;
                    double dx;
                    double distSq;
                    if (neighbor == point || (distSq = (dx = (double)(neighbor.x - point.x)) * dx + (dz = (double)(neighbor.z - point.z)) * dz) > neighborSearchRadiusSq || (typeDifference = this.getTerrainTypeDifference(point.type, neighbor.type)) <= 2) continue;
                    double adjustment = this.calculateTerrainTypeAdjustment(point.type, neighbor.type);
                    if (this.isHigherElevation(point.type, neighbor.type)) {
                        point.constrainedOffset -= adjustment;
                        neighbor.constrainedOffset += adjustment;
                    } else {
                        neighbor.constrainedOffset -= adjustment;
                        point.constrainedOffset += adjustment;
                    }
                    converged = false;
                }
            }
            if (converged) break;
        }
    }

    private int getTerrainTypeDifference(TerrainType t1, TerrainType t2) {
        int level1 = this.getTerrainLevel(t1);
        int level2 = this.getTerrainLevel(t2);
        return Math.abs(level1 - level2);
    }

    private int getTerrainLevel(TerrainType type) {
        return switch (type) {
            case TerrainType.TRENCH -> 0;
            case TerrainType.CANYON, TerrainType.BASIN, TerrainType.SINKHOLE -> 1;
            case TerrainType.BEACH, TerrainType.DELTA, TerrainType.SEA_PLATEAU, TerrainType.FLOODPLAIN, TerrainType.SALT_FLAT -> 2;
            case TerrainType.GLACIAL_VALLEY, TerrainType.FJORD, TerrainType.PLAINS, TerrainType.DUNE, TerrainType.GOBI, TerrainType.YARDANG -> 3;
            case TerrainType.CIRQUE, TerrainType.HILLS, TerrainType.VALLEY, TerrainType.ALLUVIAL_FAN, TerrainType.ICE_SHEET, TerrainType.PEAK_FOREST -> 4;
            case TerrainType.RIDGE, TerrainType.PLATEAU, TerrainType.SEA_CLIFF, TerrainType.DOME -> 5;
            case TerrainType.HIGH_MOUNTAINS, TerrainType.PEAK, TerrainType.HORN, TerrainType.CLIFF -> 6;
            default -> 3;
        };
    }

    private boolean isHigherElevation(TerrainType t1, TerrainType t2) {
        return this.getTerrainLevel(t1) > this.getTerrainLevel(t2);
    }

    private double calculateTerrainTypeAdjustment(TerrainType t1, TerrainType t2) {
        int difference = this.getTerrainTypeDifference(t1, t2);
        return (double)difference * 50.0;
    }

    private void validateControlPoints() {
        if (!LOGGER.isDebugEnabled()) {
            return;
        }
        int totalPoints = this.controlPoints.size();
        LOGGER.debug("[World Scape] Region ({},{}) generated {} control points", new Object[]{this.regionX, this.regionZ, totalPoints});
        if (totalPoints < 2) {
            LOGGER.warn("[World Scape] Region ({},{}) control point density too low: {} (expected {}-{}), may cause insufficient transition zones and steep slopes", new Object[]{this.regionX, this.regionZ, totalPoints, 2, 8});
        }
        double neighborSearchRadiusSq = 1048576.0;
        double offsetDiffWarnThresholdSq = 40000.0;
        int anomalyCount = 0;
        for (int i = 0; i < this.controlPoints.size(); ++i) {
            TerrainControlPoint p1 = this.controlPoints.get(i);
            for (int j = i + 1; j < this.controlPoints.size(); ++j) {
                double totalHeightDiff;
                TerrainControlPoint p2 = this.controlPoints.get(j);
                double dx = p2.x - p1.x;
                double dz = p2.z - p1.z;
                double distSq = dx * dx + dz * dz;
                if (distSq > neighborSearchRadiusSq || !((totalHeightDiff = Math.abs(p1.elevationOffset - p2.elevationOffset)) > 200.0)) continue;
                ++anomalyCount;
                LOGGER.warn("[World Scape] ControlPoint offset anomaly in region ({},{}) (Block Grid): CP1(x={}, z={}, elevationOffset={}, type={}) <-> CP2(x={}, z={}, elevationOffset={}, type={}), distance={}, totalHeightDiff={}", new Object[]{this.regionX, this.regionZ, p1.x, p1.z, p1.elevationOffset, p1.terrainType, p2.x, p2.z, p2.elevationOffset, p2.terrainType, Math.sqrt(distSq), totalHeightDiff});
            }
        }
        if (anomalyCount == 0) {
            LOGGER.debug("[World Scape] Region ({},{}) passed offset validation (no anomalies)", (Object)this.regionX, (Object)this.regionZ);
        } else {
            LOGGER.warn("[World Scape] Region ({},{}) has {} offset anomalies (threshold: {})", new Object[]{this.regionX, this.regionZ, anomalyCount, 200});
        }
    }

    private double calculateInfluenceRadius(int x, int z, TerrainType type, RandomSource random) {
        double baseRadius = 600.0;
        return switch (type) {
            case TerrainType.PLAINS -> baseRadius + random.nextDouble() * 200.0;
            case TerrainType.BEACH, TerrainType.DELTA -> baseRadius + random.nextDouble() * 150.0;
            case TerrainType.FLOODPLAIN, TerrainType.SALT_FLAT -> baseRadius + 50.0 + random.nextDouble() * 150.0;
            case TerrainType.HILLS -> baseRadius + 100.0 + random.nextDouble() * 200.0;
            case TerrainType.VALLEY, TerrainType.ALLUVIAL_FAN -> baseRadius + 50.0 + random.nextDouble() * 150.0;
            case TerrainType.PLATEAU -> baseRadius + 150.0 + random.nextDouble() * 200.0;
            case TerrainType.RIDGE, TerrainType.DOME -> baseRadius + 100.0 + random.nextDouble() * 200.0;
            case TerrainType.HIGH_MOUNTAINS -> baseRadius + 200.0 + random.nextDouble() * 200.0;
            case TerrainType.PEAK, TerrainType.HORN, TerrainType.CLIFF -> baseRadius + 150.0 + random.nextDouble() * 200.0;
            case TerrainType.DUNE -> baseRadius + 50.0 + random.nextDouble() * 150.0;
            case TerrainType.GOBI, TerrainType.YARDANG -> baseRadius + random.nextDouble() * 150.0;
            case TerrainType.ICE_SHEET -> baseRadius + 100.0 + random.nextDouble() * 200.0;
            case TerrainType.CIRQUE, TerrainType.GLACIAL_VALLEY -> baseRadius + 50.0 + random.nextDouble() * 150.0;
            case TerrainType.CANYON -> baseRadius - 100.0 + random.nextDouble() * 100.0;
            case TerrainType.BASIN, TerrainType.SINKHOLE -> baseRadius - 50.0 + random.nextDouble() * 100.0;
            case TerrainType.TRENCH -> baseRadius - 50.0 + random.nextDouble() * 100.0;
            case TerrainType.SEA_CLIFF -> baseRadius + 50.0 + random.nextDouble() * 100.0;
            default -> baseRadius + random.nextDouble() * 150.0;
        };
    }

    public List<TerrainControlPoint> getPointsInRange(int targetX, int targetZ, double radius) {
        ArrayList<TerrainControlPoint> result = new ArrayList<TerrainControlPoint>();
        int cellRadius = (int)Math.ceil(radius / 16.0);
        int centerCellX = Math.floorDiv(targetX - this.regionX * 512, 16);
        int centerCellZ = Math.floorDiv(targetZ - this.regionZ * 512, 16);
        for (int dx = -cellRadius; dx <= cellRadius; ++dx) {
            for (int dz = -cellRadius; dz <= cellRadius; ++dz) {
                List<TerrainControlPoint> cell;
                int cx = centerCellX + dx;
                int cz = centerCellZ + dz;
                if (cx < 0 || cx >= this.cellsPerSide || cz < 0 || cz >= this.cellsPerSide || (cell = this.cellIndex[cx][cz]) == null) continue;
                for (TerrainControlPoint point : cell) {
                    double distSq = point.squaredDistanceTo(targetX, targetZ);
                    if (!(distSq <= point.influenceRadius * point.influenceRadius) || !(point.calculateInfluenceFromSquaredDist(distSq) > 0.0)) continue;
                    result.add(point);
                }
            }
        }
        return result;
    }

    public int getRegionX() {
        return this.regionX;
    }

    public int getRegionZ() {
        return this.regionZ;
    }

    public List<TerrainControlPoint> getControlPoints() {
        return Collections.unmodifiableList(this.controlPoints);
    }

    private static class PointData {
        final int x;
        final int z;
        final TerrainType type;
        final double rawOffset;
        final double radius;
        double constrainedOffset;

        PointData(int x, int z, TerrainType type, double rawOffset, double radius) {
            this.x = x;
            this.z = z;
            this.type = type;
            this.rawOffset = rawOffset;
            this.radius = radius;
            this.constrainedOffset = rawOffset;
        }
    }
}

