/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.util.RandomSource
 */
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
    // @AESTHETIC: Spawn ocean constraint radius (in Voronoi cells, ~2048 blocks each).
    // RADIUS=0 limits ocean enforcement to the spawn cell itself, letting dist≥1 cells
    // develop natural tier distribution including mountains.
    // RADIUS=0 将海洋约束限制在出生点所在单元本身，距离≥1的单元可获得自然 Tier 分布（含山地）。
    private static final int SPAWN_OCEAN_RADIUS_CELLS = 0;
    private static final double SPAWN_MAX_OCEAN_WEIGHT = 0.35;
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
            secondTier = this.getAdjustedElevationTier(secondCellX, secondCellZ);
            int tierDiff = Math.abs(primaryTier - secondTier);
            int primaryBase = MacroVoronoiSystem.getBaseHeightForTier(primaryTier);
            int secondBase = MacroVoronoiSystem.getBaseHeightForTier(secondTier);
            int actualHeightDiff = Math.abs(primaryBase - secondBase);
            int calculatedWidth = (int)((double)actualHeightDiff * 10.0);
            transitionWidth = Math.max(800, Math.min(2400, calculatedWidth));
            boolean bothUnderwater = primaryBase < this.seaLevel && secondBase < this.seaLevel;
            boolean primaryIsOcean = primaryTier < 2;
            boolean secondIsOcean = secondTier < 2;
            boolean bl = secondIsOcean;
            if (bothUnderwater && primaryIsOcean && secondIsOcean) {
                transitionWidth = (int)((double)transitionWidth * 6.0);
                transitionWidth = Math.min(2400, transitionWidth);
            }
            double primaryDist = Math.sqrt(minDistSq);
            double secondDist = Math.sqrt(secondMinDistSq);
            // 使用较大的 epsilon (1.0) 避免近距离时 distRatio 数值不稳定
            // Use larger epsilon (1.0) to avoid numerical instability in distRatio at close distances
            double distRatio = primaryDist / (primaryDist + secondDist + 1.0);
            double halfBand = Math.min(0.45, (double)(transitionWidth / 2048) * 2.0);
            double minHalfBand = 0.08;
            if (halfBand < 0.08) {
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
        double r;
        long seed = SeedDeriver.deriveSeed(this.elevationSeed, (long)cellX * 31L + (long)cellZ * 17L);
        RandomSource random = RandomSource.create((long)seed);
        int tier = random.nextInt(6);
        int distFromSpawn = Math.max(Math.abs(cellX), Math.abs(cellZ));
        if (distFromSpawn <= SPAWN_OCEAN_RADIUS_CELLS) {
            double oceanWeight = SPAWN_MAX_OCEAN_WEIGHT * (1.0 - (double)distFromSpawn / 3.0);
            if (random.nextDouble() < oceanWeight && tier > 1 && tier < 4) {
                tier = random.nextInt(2);
            }
        }
        // @AESTHETIC: Tier cap distribution adjusted from 10/25/35/30 to 10/20/30/40.
        // Old: T4+T5=17.0%, New: T4+T5=22.0% (29% more mountain cells).
        // T5 cap boosted from 30% to 40% to increase high-mountain terrain diversity.
        // 将 Tier 上限概率从 10/25/35/30 调整为 10/20/30/40，T4+T5 从 17.0% 提升至 22.0%。
        // T5 cap 从 30% 提升至 40%，增加高山地形多样性。
        tier = (r = random.nextDouble()) < 0.10 ? Math.min(tier, 2) : (r < 0.30 ? Math.min(tier, 3) : (r < 0.60 ? Math.min(tier, 4) : Math.min(tier, 5)));
        return tier;
    }

    public int getAdjustedElevationTier(int cellX, int cellZ) {
        long key = (long)cellX << 32 | (long)cellZ & 0xFFFFFFFFL;
        return this.adjustedTierCache.computeIfAbsent(key, k -> {
            int tier = this.getRawElevationTier(cellX, cellZ);
            int minNeighborTier = tier;
            int maxNeighborTier = tier;
            for (int dx = -1; dx <= 1; ++dx) {
                for (int dz = -1; dz <= 1; ++dz) {
                    if (dx == 0 && dz == 0) continue;
                    int neighborTier = this.getRawElevationTier(cellX + dx, cellZ + dz);
                    minNeighborTier = Math.min(minNeighborTier, neighborTier);
                    maxNeighborTier = Math.max(maxNeighborTier, neighborTier);
                }
            }
            // @AESTHETIC: Symmetric neighbor correction — pull down isolated peaks AND pull up isolated valleys.
            // Eliminates iteration-order dependency by collecting min/max first, then correcting once.
            // New threshold 3: allows more natural terrain variation instead of oversmoothing.
            // 对称邻居修正：同时拉低孤立高峰和拉高孤立低谷，先收集 min/max 再统一修正，消除迭代顺序依赖。
            // 新阈值 3：允许更自然地形的变化，避免过度平滑。
            if (tier - minNeighborTier > 3) tier = minNeighborTier + 3;
            if (maxNeighborTier - tier > 3) tier = Math.max(tier, maxNeighborTier - 3);
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

