package com.worldscape.terrain;

import com.worldscape.terrain.MacroRegionInfo;
import com.worldscape.util.SeedDeriver;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.util.RandomSource;

public class MacroVoronoiSystem {
    public static final int REGION_CELL_SIZE = 2048;
    private static final int[] ELEVATION_BASE_HEIGHTS = new int[]{-80, -20, 10, 60, 160, 300};
    private static final double HEIGHT_DIFF_TO_BANDWIDTH_FACTOR = 10.0;
    private static final int MIN_TRANSITION_WIDTH = 800;
    private static final int MAX_TRANSITION_WIDTH = 2400;
    private static final double WATER_TRANSITION_MULTIPLIER = 6.0;
    private static final int OCEAN_TIER_THRESHOLD = 2;
    private final long worldSeed;
    private final int seaLevel;
    private final long voronoiXSeed;
    private final long voronoiZSeed;
    private final long elevationSeed;
    private final long tectonicSeed;
    private final long riftSeed;
    private final long climateSeed;
    private static final int MAX_CACHE_SIZE = 10000;
    private final Map<Long, ControlPoint> controlPointCache = Collections.synchronizedMap(new LinkedHashMap<Long, ControlPoint>(1024, 0.75f, true){

        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, ControlPoint> eldest) {
            return this.size() > 10000;
        }
    });
    private final Map<Long, Integer> adjustedTierCache = Collections.synchronizedMap(new LinkedHashMap<Long, Integer>(1024, 0.75f, true){

        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Integer> eldest) {
            return this.size() > 10000;
        }
    });
    private final Map<Long, ControlPoint[]> cellGridCache = Collections.synchronizedMap(new LinkedHashMap<Long, ControlPoint[]>(1024, 0.75f, true){

        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, ControlPoint[]> eldest) {
            return this.size() > 10000;
        }
    });

    public MacroVoronoiSystem(long worldSeed, int seaLevel) {
        this.worldSeed = worldSeed;
        this.seaLevel = seaLevel;
        this.voronoiXSeed = SeedDeriver.deriveMacroVoronoiX(worldSeed);
        this.voronoiZSeed = SeedDeriver.deriveMacroVoronoiZ(worldSeed);
        this.elevationSeed = SeedDeriver.deriveMacroElevationSeed(worldSeed);
        this.tectonicSeed = SeedDeriver.deriveMacroTectonicSeed(worldSeed);
        this.riftSeed = SeedDeriver.deriveMacroRiftSeed(worldSeed);
        this.climateSeed = SeedDeriver.deriveMacroClimateSeed(worldSeed);
    }

    public MacroRegionInfo getRegionInfo(int x, int z) {
        int cellZ;
        int cellX = Math.floorDiv(x, 2048);
        long cellKey = (long)cellX << 32 | (long)(cellZ = Math.floorDiv(z, 2048)) & 0xFFFFFFFFL;
        ControlPoint[] gridPoints = this.cellGridCache.get(cellKey);
        if (gridPoints == null) {
            gridPoints = new ControlPoint[9];
            for (int dx = -1; dx <= 1; ++dx) {
                for (int dz = -1; dz <= 1; ++dz) {
                    int nx = cellX + dx;
                    int nz = cellZ + dz;
                    gridPoints[(dx + 1) * 3 + (dz + 1)] = this.getControlPoint(nx, nz);
                }
            }
            this.cellGridCache.put(cellKey, gridPoints);
        }
        double minDistSq = Double.MAX_VALUE;
        int nearestCellX = cellX;
        int nearestCellZ = cellZ;
        for (int dx = -1; dx <= 1; ++dx) {
            for (int dz = -1; dz <= 1; ++dz) {
                ControlPoint cp = gridPoints[(dx + 1) * 3 + (dz + 1)];
                double dx2 = (double)x - cp.x;
                double dz2 = (double)z - cp.z;
                double distSq = dx2 * dx2 + dz2 * dz2;
                if (!(distSq < minDistSq)) continue;
                minDistSq = distSq;
                nearestCellX = cellX + dx;
                nearestCellZ = cellZ + dz;
            }
        }
        int primaryTier = this.getAdjustedElevationTier(nearestCellX, nearestCellZ);
        double secondMinDistSq = Double.MAX_VALUE;
        int secondCellX = -1;
        int secondCellZ = -1;
        for (int dx = -1; dx <= 1; ++dx) {
            for (int dz = -1; dz <= 1; ++dz) {
                int nx = cellX + dx;
                int nz = cellZ + dz;
                if (nx == nearestCellX && nz == nearestCellZ) continue;
                ControlPoint cp = gridPoints[(dx + 1) * 3 + (dz + 1)];
                double dx2 = (double)x - cp.x;
                double dz2 = (double)z - cp.z;
                double distSq = dx2 * dx2 + dz2 * dz2;
                if (!(distSq < secondMinDistSq)) continue;
                secondMinDistSq = distSq;
                secondCellX = nx;
                secondCellZ = nz;
            }
        }
        double blendWeight = 1.0;
        int transitionWidth = 800;
        int secondTier = primaryTier;
        if (secondCellX != -1) {
            double minHalfBand;
            boolean secondIsOcean;
            secondTier = this.getAdjustedElevationTier(secondCellX, secondCellZ);
            int tierDiff = Math.abs(primaryTier - secondTier);
            int primaryBase = MacroVoronoiSystem.getBaseHeightForTier(primaryTier);
            int secondBase = MacroVoronoiSystem.getBaseHeightForTier(secondTier);
            int actualHeightDiff = Math.abs(primaryBase - secondBase);
            int calculatedWidth = (int)((double)actualHeightDiff * 10.0);
            transitionWidth = Math.max(800, Math.min(2400, calculatedWidth));
            boolean bothUnderwater = primaryBase < this.seaLevel && secondBase < this.seaLevel;
            boolean primaryIsOcean = primaryTier < 2;
            boolean bl = secondIsOcean = secondTier < 2;
            if (bothUnderwater && primaryIsOcean && secondIsOcean) {
                transitionWidth = (int)((double)transitionWidth * 6.0);
                transitionWidth = Math.min(2400, transitionWidth);
            }
            double primaryDist = Math.sqrt(minDistSq);
            double secondDist = Math.sqrt(secondMinDistSq);
            double distRatio = primaryDist / (primaryDist + secondDist + 0.001);
            double halfBand = Math.min(0.45, (double)(transitionWidth / 2048) * 2.0);
            if (halfBand < (minHalfBand = 0.08)) {
                halfBand = minHalfBand;
            }
            double edge0 = 0.5 - halfBand;
            double edge1 = 0.5 + halfBand;
            blendWeight = 1.0 - SeedDeriver.smoothstep(edge0, edge1, distRatio);
        }
        double primaryBaseHeight = MacroVoronoiSystem.getBaseHeightForTier(primaryTier);
        double secondBaseHeight = MacroVoronoiSystem.getBaseHeightForTier(secondTier);
        double blendedBaseHeight = primaryBaseHeight * blendWeight + secondBaseHeight * (1.0 - blendWeight);
        MacroRegionInfo.TectonicType tectonic = this.determineTectonicType(nearestCellX, nearestCellZ, primaryTier);
        MacroRegionInfo.ClimateZone climate = this.determineClimateZone(nearestCellX, nearestCellZ);
        return new MacroRegionInfo(primaryTier, secondTier, blendedBaseHeight, tectonic, climate, blendWeight, transitionWidth, nearestCellX, nearestCellZ);
    }

    private ControlPoint getControlPoint(int cellX, int cellZ) {
        long key = (long)cellX << 32 | (long)cellZ & 0xFFFFFFFFL;
        return this.controlPointCache.computeIfAbsent(key, k -> {
            long xSeed = SeedDeriver.deriveSeed(this.voronoiXSeed, (long)cellX * 31L + (long)cellZ * 17L);
            RandomSource xRandom = RandomSource.create((long)xSeed);
            double xOffset = (xRandom.nextDouble() - 0.5) * 2048.0 * 0.6;
            double px = (double)(cellX * 2048) + 1024.0 + xOffset;
            long zSeed = SeedDeriver.deriveSeed(this.voronoiZSeed, (long)cellX * 17L + (long)cellZ * 31L);
            RandomSource zRandom = RandomSource.create((long)zSeed);
            double zOffset = (zRandom.nextDouble() - 0.5) * 2048.0 * 0.6;
            double pz = (double)(cellZ * 2048) + 1024.0 + zOffset;
            return new ControlPoint(px, pz);
        });
    }

    private int getRawElevationTier(int cellX, int cellZ) {
        long seed = SeedDeriver.deriveSeed(this.elevationSeed, (long)cellX * 31L + (long)cellZ * 17L);
        RandomSource random = RandomSource.create((long)seed);
        int tier = random.nextInt(6);
        double r = random.nextDouble();
        tier = r < 0.25 ? Math.min(tier, 2) : (r < 0.55 ? Math.min(tier, 3) : (r < 0.8 ? Math.min(tier, 4) : Math.min(tier, 5)));
        return tier;
    }

    public int getAdjustedElevationTier(int cellX, int cellZ) {
        long key = (long)cellX << 32 | (long)cellZ & 0xFFFFFFFFL;
        return this.adjustedTierCache.computeIfAbsent(key, k -> {
            int tier = this.getRawElevationTier(cellX, cellZ);
            for (int dx = -1; dx <= 1; ++dx) {
                for (int dz = -1; dz <= 1; ++dz) {
                    int neighborTier;
                    int diff;
                    if (dx == 0 && dz == 0 || (diff = tier - (neighborTier = this.getRawElevationTier(cellX + dx, cellZ + dz))) <= 2) continue;
                    tier = neighborTier + 2;
                }
            }
            return tier;
        });
    }

    private MacroRegionInfo.TectonicType determineTectonicType(int cellX, int cellZ, int elevationTier) {
        long seed = SeedDeriver.deriveSeed(this.tectonicSeed, (long)cellX * 31L + (long)cellZ * 17L);
        RandomSource random = RandomSource.create((long)seed);
        if (elevationTier >= 6) {
            double r = random.nextDouble();
            if (r < 0.6) {
                return MacroRegionInfo.TectonicType.OROGENIC_BELT;
            }
            if (r < 0.85) {
                return MacroRegionInfo.TectonicType.SUBDUCTION_ZONE;
            }
            return MacroRegionInfo.TectonicType.FAULT_ZONE;
        }
        if (elevationTier >= 3) {
            double r = random.nextDouble();
            if (r < 0.2) {
                return MacroRegionInfo.TectonicType.RIFT_ZONE;
            }
            return MacroRegionInfo.TectonicType.CRATON;
        }
        return MacroRegionInfo.TectonicType.CRATON;
    }

    private MacroRegionInfo.ClimateZone determineClimateZone(int cellX, int cellZ) {
        long seed = SeedDeriver.deriveSeed(this.climateSeed, (long)cellX * 31L + (long)cellZ * 17L);
        RandomSource random = RandomSource.create((long)seed);
        double r = random.nextDouble();
        if (r < 0.15) {
            return MacroRegionInfo.ClimateZone.ARID;
        }
        if (r < 0.25) {
            return MacroRegionInfo.ClimateZone.GLACIAL;
        }
        if (r < 0.6) {
            return MacroRegionInfo.ClimateZone.TEMPERATE;
        }
        return MacroRegionInfo.ClimateZone.TROPICAL;
    }

    public static int getBaseHeightForTier(int tier) {
        if (tier < 0) {
            return ELEVATION_BASE_HEIGHTS[0];
        }
        if (tier > 5) {
            return ELEVATION_BASE_HEIGHTS[5];
        }
        return ELEVATION_BASE_HEIGHTS[tier];
    }

    public static double getTierMinimumHeight(int tier) {
        return switch (tier) {
            case 0 -> -55.0;
            case 1 -> -28.0;
            case 2 -> -5.0;
            case 3 -> 28.0;
            case 4 -> 28.0;
            case 5 -> 44.0;
            default -> 0.0;
        };
    }

    private static class ControlPoint {
        final double x;
        final double z;

        ControlPoint(double x, double z) {
            this.x = x;
            this.z = z;
        }
    }
}

