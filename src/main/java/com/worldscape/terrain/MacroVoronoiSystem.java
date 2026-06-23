package com.worldscape.terrain;

import com.worldscape.terrain.MacroRegionInfo;
import com.worldscape.util.SeedDeriver;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MacroVoronoiSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(MacroVoronoiSystem.class);
    public static final int REGION_CELL_SIZE = 2048;
    private static final int[] ELEVATION_BASE_HEIGHTS = new int[]{-80, -20, 50, 60, 160, 300};
    // Removed unused transition-width constants: HEIGHT_DIFF_TO_BANDWIDTH_FACTOR, MIN_TRANSITION_WIDTH,
    // MAX_TRANSITION_WIDTH, WATER_TRANSITION_MULTIPLIER, OCEAN_TIER_THRESHOLD.
    // They were dead code; actual transition logic used different hardcoded values or was removed.
    // 已移除未使用的过渡宽度常量：HEIGHT_DIFF_TO_BANDWIDTH_FACTOR、MIN_TRANSITION_WIDTH、
    // MAX_TRANSITION_WIDTH、WATER_TRANSITION_MULTIPLIER、OCEAN_TIER_THRESHOLD。
    // 它们是死代码；实际过渡逻辑使用了不同的硬编码值或已被移除。
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
    // Spatial coherent noise for tier assignment — replaces random.nextInt(6)
    // 空间连贯噪声用于层级分配 — 替代 random.nextInt(6)
    private final NormalNoise tierNoise;
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
        // Derive a dedicated seed for tier noise to ensure spatial coherence
        // 推导专用种子用于层级噪声，确保空间连贯性
        long tierNoiseSeed = SeedDeriver.deriveSeed(worldSeed, 756324891011L);
        // Multi-octave noise for spatial tier variation: 4 octaves from -3 to 0
        // 多倍频程噪声实现空间层级变化：从 -3 到 0 共 4 个倍频程
        this.tierNoise = NormalNoise.create(RandomSource.create(tierNoiseSeed), -3, new double[]{1.0, 0.5, 0.25, 0.125});
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
            // Let transitionWidth be naturally determined by height difference,
            // only enforce the lower bound to prevent razor-thin boundaries.
            // 让 transitionWidth 由高度差自然决定，仅强制下限以防止极窄边界。
            transitionWidth = Math.max(800, calculatedWidth);
            boolean bothUnderwater = primaryBase < this.seaLevel && secondBase < this.seaLevel;
            boolean primaryIsOcean = primaryTier < 2;
            boolean secondIsOcean = secondTier < 2;
            if (bothUnderwater && primaryIsOcean && secondIsOcean) {
                transitionWidth = (int)((double)transitionWidth * 6.0);
            }
            double primaryDist = Math.sqrt(minDistSq);
            double secondDist = Math.sqrt(secondMinDistSq);
            // 使用较大的 epsilon (1.0) 避免近距离时 distRatio 数值不稳定
            // Use larger epsilon (1.0) to avoid numerical instability in distRatio at close distances
            double distRatio = primaryDist / (primaryDist + secondDist + 1.0);
            // Keep only lower bound protection to prevent razor-thin blends
            // 仅保留下限保护以防止过度锐利的混合
            double halfBand = Math.max(0.08, (double)(transitionWidth / 2048) * 2.0);
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
        // Use spatial coherent noise for tier assignment — ensures smooth transitions
        // between adjacent cells instead of random jumps.
        // 使用空间连贯噪声进行层级分配 — 确保相邻单元之间平滑过渡，而非随机跳变。
        double noiseValue = this.tierNoise.getValue(cellX * 0.05, 0.0, cellZ * 0.05);
        // Map noise value [-1, 1] to tier [0, 5] and clamp
        // 将噪声值 [-1, 1] 映射到层级 [0, 5] 并夹紧
        int tier = (int)((noiseValue + 1.0) * 3.0);
        tier = Math.max(0, Math.min(5, tier));

        int distFromSpawn = Math.max(Math.abs(cellX), Math.abs(cellZ));
        if (distFromSpawn <= SPAWN_OCEAN_RADIUS_CELLS) {
            long seed = SeedDeriver.deriveSeed(this.elevationSeed, (long)cellX * 31L + (long)cellZ * 17L);
            RandomSource random = RandomSource.create(seed);
            double oceanWeight = SPAWN_MAX_OCEAN_WEIGHT * (1.0 - (double)distFromSpawn / 3.0);
            if (random.nextDouble() < oceanWeight && tier > 1 && tier < 4) {
                tier = random.nextInt(2);
            }
        }
        return tier;
    }

    public int getAdjustedElevationTier(int cellX, int cellZ) {
        long key = (long)cellX << 32 | (long)cellZ & 0xFFFFFFFFL;
        return this.adjustedTierCache.computeIfAbsent(key, k -> {
            int rawTier = this.getRawElevationTier(cellX, cellZ);
            // Diagnostic: log cell tier info for the first 30 unique cells
            // 诊断：记录前 30 个唯一单元的层级信息
            if (adjustedTierCache.size() < 30) {
                LOGGER.info("[MACRO_DIAG] cell({},{}): rawTier={}", cellX, cellZ, rawTier);
            }
            return rawTier;
        });
    }

    private MacroRegionInfo.TectonicType determineTectonicType(int cellX, int cellZ, int elevationTier) {
        long seed = SeedDeriver.deriveSeed(this.tectonicSeed, (long)cellX * 31L + (long)cellZ * 17L);
        RandomSource random = RandomSource.create((long)seed);
        // Removed dead code: elevationTier >= 6 was unreachable (max tier is 5).
        // If tectonic type expansion is needed in the future, re-add here with higher tier support.
        // 已移除死代码：elevationTier >= 6 永远不可达（最大 tier 为 5）。
        // 若未来需要扩展构造类型，在此处重新添加并支持更高 tier。
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
        // Tier minimum heights enforce separation between tiers so that higher
        // tiers consistently produce higher terrain. Previously tier 3 and tier 4
        // both returned 28.0, providing no separation — tier 4 terrain (HILLS,
        // CLIFF, PLATEAU at base height 160) could be pulled down to tier 3
        // levels (base height 60) during blending, blurring the tier boundary.
        // Tier 最小高度强制层级间分离，使更高层级一致地产生更高地形。
        // 之前 tier 3 和 tier 4 都返回 28.0，没有分离 — tier 4 地形
        //（HILLS、CLIFF、PLATEAU 基准高度 160）在混合时可能被拉低到
        // tier 3 水平（基准高度 60），模糊了层级边界。
        return switch (tier) {
            case 0 -> -55.0;
            case 1 -> -28.0;
            case 2 -> -5.0;
            case 3 -> 28.0;
            case 4 -> 80.0;   // Was 28.0 — now enforces tier 3→4 height separation
            case 5 -> 120.0;  // Was 44.0 — raised to enforce tier 4→5 separation
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

