/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.worldscape.terrain;

import com.worldscape.terrain.ControlPointRegion;
import com.worldscape.terrain.MacroRegionInfo;
import com.worldscape.terrain.MacroVoronoiSystem;
import com.worldscape.terrain.NoiseSet;
import com.worldscape.terrain.TerrainControlPoint;
import com.worldscape.terrain.TerrainType;
import com.worldscape.util.ClimateUtils;
import com.worldscape.util.WorldScapeUtils;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RegionController {
    private static final Logger LOGGER = LoggerFactory.getLogger(RegionController.class);
    private final long worldSeed;
    private final int seaLevel;
    private final MacroVoronoiSystem macroSystem;
    private final NoiseSet noiseSet;
    private final ConcurrentHashMap<Long, ControlPointRegion> terrainRegionCache;
    private static final int CACHE_MAX_SIZE_DEFAULT = 1024;
    private static volatile int cacheMaxSize = CACHE_MAX_SIZE_DEFAULT;
    private final AtomicBoolean evictionInProgress = new AtomicBoolean(false);

    public static void setCacheMaxSize(int newSize) {
        if (newSize >= CACHE_MAX_SIZE_DEFAULT) {
            RegionController.cacheMaxSize = newSize;
        }
    }

    public static int getCacheMaxSize() {
        return RegionController.cacheMaxSize;
    }

    public RegionController(long worldSeed, int seaLevel) {
        this.worldSeed = worldSeed;
        this.seaLevel = seaLevel;
        this.macroSystem = new MacroVoronoiSystem(worldSeed, seaLevel);
        this.noiseSet = NoiseSet.getOrCreate(worldSeed);
        this.terrainRegionCache = new ConcurrentHashMap();
    }

    public TerrainBlendResult getTerrainBlend(int x, int z) {
        return this.getTerrainBlend(x, z, null);
    }

    public TerrainBlendResult getTerrainBlend(int x, int z, BlendCache cache) {
        ArrayList<TerrainControlPoint> points;
        MacroRegionInfo macroInfo = this.macroSystem.getRegionInfo(x, z);
        if (cache != null) {
            points = new ArrayList<TerrainControlPoint>();
            for (TerrainControlPoint p : cache.allPoints) {
                double distSq = p.squaredDistanceTo(x, z);
                if (!(distSq <= p.influenceRadius * p.influenceRadius)) continue;
                points.add(p);
            }
            if (points.isEmpty()) {
                double macroBaseHeight = macroInfo.getBaseHeight();
                TerrainType defaultType = switch (macroInfo.getElevationTier()) {
                    case 0, 1 -> TerrainType.SEA_PLATEAU;
                    case 2 -> TerrainType.BEACH;
                    case 3 -> TerrainType.PLAINS;
                    case 4 -> TerrainType.HILLS;
                    case 5 -> TerrainType.HIGH_MOUNTAINS;
                    default -> TerrainType.PLAINS;
                };
                return new TerrainBlendResult(macroBaseHeight, macroInfo, List.of(), 0.0, defaultType, 0.0);
            }
        } else {
            int regionX = Math.floorDiv(x, 512);
            int regionZ = Math.floorDiv(z, 512);
            points = new ArrayList();
            double searchRadius = 1200.0;
            ControlPointRegion region = this.getOrCreateRegion(regionX, regionZ);
            points.addAll(region.getPointsInRange(x, z, searchRadius));
            for (int dx = -1; dx <= 1; ++dx) {
                for (int dz = -1; dz <= 1; ++dz) {
                    if (dx == 0 && dz == 0) continue;
                    ControlPointRegion neighborRegion = this.getOrCreateRegion(regionX + dx, regionZ + dz);
                    points.addAll(neighborRegion.getPointsInRange(x, z, searchRadius));
                }
            }
        }
        return this.calculateBlend(x, z, points, macroInfo);
    }

    private TerrainBlendResult calculateBlend(int x, int z, List<TerrainControlPoint> points, MacroRegionInfo macroInfo) {
        double finalHeight;
        double blendWeight = macroInfo.getBlendWeight();
        double macroBaseHeight = macroInfo.getBaseHeight();
        if (points.isEmpty()) {
            LOGGER.debug("[World Scape] calculateBlend({}, {}) no points, using macro base: {}", new Object[]{x, z, macroBaseHeight});
            TerrainType defaultType = switch (macroInfo.getElevationTier()) {
                case 0, 1 -> TerrainType.SEA_PLATEAU;
                case 2 -> TerrainType.BEACH;
                case 3 -> TerrainType.PLAINS;
                case 4 -> TerrainType.HILLS;
                case 5 -> TerrainType.HIGH_MOUNTAINS;
                default -> TerrainType.PLAINS;
            };
            return new TerrainBlendResult(macroBaseHeight, macroInfo, List.of(), 0.0, defaultType, 0.0);
        }
        double totalWeight = 0.0;
        double weightedHeightSum = 0.0;
        ArrayList<PointWeight> weightedPoints = new ArrayList<PointWeight>();
        for (TerrainControlPoint point : points) {
            double weight = point.calculateInfluence(x, z);
            if (!(weight > 0.0)) continue;
            double pointHeight = point.getElevationOffset() + this.getBaseHeightForTerrainType(point.getTerrainType());
            weightedPoints.add(new PointWeight(point, weight, pointHeight));
            weightedHeightSum += weight * pointHeight;
            totalWeight += weight;
        }
        if (totalWeight == 0.0) {
            TerrainType defaultType = switch (macroInfo.getElevationTier()) {
                case 0, 1 -> TerrainType.SEA_PLATEAU;
                case 2 -> TerrainType.BEACH;
                case 3 -> TerrainType.PLAINS;
                case 4 -> TerrainType.HILLS;
                case 5 -> TerrainType.HIGH_MOUNTAINS;
                default -> TerrainType.PLAINS;
            };
            return new TerrainBlendResult(macroBaseHeight, macroInfo, List.of(), 0.0, defaultType, 0.0);
        }
        double microHeight = weightedHeightSum / totalWeight;
        int primaryTier = macroInfo.getElevationTier();
        int secondTier = macroInfo.getSecondElevationTier();
        double primaryMin = this.getTierMinimumHeight(primaryTier);
        double secondMin = this.getTierMinimumHeight(secondTier);
        double tierMinHeight = this.lerp(secondMin, primaryMin, blendWeight);
        microHeight = Math.max(microHeight, tierMinHeight);
        double tierAdjustment = (double)(primaryTier - 4) * WorldScapeConstants.TIER_BASE_HEIGHT * WorldScapeConstants.TIER_ADJUSTMENT_FACTOR;
        if (blendWeight > WorldScapeConstants.BLEND_WEIGHT_THRESHOLD) {
            finalHeight = macroBaseHeight + microHeight + tierAdjustment;
        } else {
            double boundaryProximityRaw = 1.0 - Math.abs(blendWeight - 0.5) * 2.0;
            boundaryProximityRaw = Math.max(0.0, Math.min(1.0, boundaryProximityRaw));
            double boundaryProximity = WorldScapeUtils.smoothstep(0.0, 1.0, boundaryProximityRaw);
            double macroInfluence = boundaryProximity * WorldScapeConstants.MAX_MACRO_INFLUENCE;
            if (primaryTier == 0) {
                macroInfluence *= WorldScapeConstants.OCEAN_TIER0_MACRO_DAMPING;
            } else if (primaryTier == 1) {
                macroInfluence *= WorldScapeConstants.OCEAN_TIER1_MACRO_DAMPING;
            }
            finalHeight = this.lerp(macroBaseHeight + microHeight + tierAdjustment, macroBaseHeight, macroInfluence);
        }
        TerrainBlendResult typeResult = this.determineDominantTerrainType(weightedPoints, totalWeight);
        ClimateUtils.ClimateProfile blendedClimate = this.calculateBlendedClimate(weightedPoints, totalWeight, macroInfo.getElevationTier());
        return new TerrainBlendResult(finalHeight, macroInfo, weightedPoints, macroBaseHeight - microHeight, typeResult.dominantType, typeResult.dominantWeight, blendedClimate);
    }

    private double lerp(double a, double b, double t) {
        return a + (b - a) * Math.max(0.0, Math.min(1.0, t));
    }

    private ClimateUtils.ClimateProfile calculateBlendedClimate(List<PointWeight> weightedPoints, double totalWeight, int elevationTier) {
        if (weightedPoints == null || weightedPoints.isEmpty() || totalWeight <= 0.0) {
            return this.getDefaultClimateForTier(elevationTier);
        }
        ArrayList<PointWeight> sorted = new ArrayList<PointWeight>(weightedPoints);
        sorted.sort((a, b) -> Double.compare(b.weight, a.weight));
        TerrainType primaryType = ((PointWeight)sorted.get((int)0)).point.getTerrainType();
        double primaryNormWeight = ((PointWeight)sorted.get((int)0)).weight / totalWeight;
        ClimateUtils.ClimateProfile primaryClimate = ClimateUtils.getTerrainClimateProfile(primaryType.name());
        if (sorted.size() < 2 || primaryNormWeight >= 0.9) {
            return this.applyElevationCorrection(primaryClimate, elevationTier);
        }
        TerrainType secondaryType = ((PointWeight)sorted.get((int)1)).point.getTerrainType();
        double secondaryNormWeight = ((PointWeight)sorted.get((int)1)).weight / totalWeight;
        ClimateUtils.ClimateProfile secondaryClimate = ClimateUtils.getTerrainClimateProfile(secondaryType.name());
        double blendT = secondaryNormWeight / (primaryNormWeight + secondaryNormWeight);
        ClimateUtils.ClimateProfile blended = ClimateUtils.blendClimate(primaryClimate, secondaryClimate, blendT);
        return this.applyElevationCorrection(blended, elevationTier);
    }

    private ClimateUtils.ClimateProfile applyElevationCorrection(ClimateUtils.ClimateProfile profile, int tier) {
        double adjustedTemp = ClimateUtils.adjustTemperatureForElevation(profile.getTemperature(), tier, 0.0);
        return new ClimateUtils.ClimateProfile(adjustedTemp, profile.getHumidity(), profile.getSeasonality(), profile.getContinentality());
    }

    private ClimateUtils.ClimateProfile getDefaultClimateForTier(int tier) {
        return switch (tier) {
            case 0, 1 -> new ClimateUtils.ClimateProfile(0.08, 0.95, 0.05, 0.05);
            case 2 -> new ClimateUtils.ClimateProfile(0.55, 0.75, 0.35, 0.15);
            default -> new ClimateUtils.ClimateProfile(0.5, 0.5, 0.6, 0.5);
        };
    }

    private double getBaseHeightForTerrainType(TerrainType type) {
        return switch (type) {
            default -> throw new MatchException(null, null);
            case TerrainType.HIGH_MOUNTAINS -> 110.0;
            case TerrainType.HILLS -> 28.0;
            case TerrainType.CLIFF -> 44.0;
            case TerrainType.PLATEAU -> 83.0;
            case TerrainType.VALLEY -> 17.0;
            case TerrainType.RIDGE -> 83.0;
            case TerrainType.PEAK -> 110.0;
            case TerrainType.CANYON -> -11.0;
            case TerrainType.ALLUVIAL_FAN -> 28.0;
            case TerrainType.FLOODPLAIN -> 17.0;
            case TerrainType.DUNE -> 14.0;
            case TerrainType.GOBI -> 22.0;
            case TerrainType.YARDANG -> 28.0;
            case TerrainType.SALT_FLAT -> 11.0;
            case TerrainType.ICE_SHEET -> 55.0;
            case TerrainType.GLACIAL_VALLEY -> -6.0;
            case TerrainType.CIRQUE -> 55.0;
            case TerrainType.HORN -> 110.0;
            case TerrainType.BEACH -> 11.0;
            case TerrainType.SEA_CLIFF -> 28.0;
            case TerrainType.FJORD -> -6.0;
            case TerrainType.DELTA -> 8.0;
            case TerrainType.PEAK_FOREST -> 55.0;
            case TerrainType.SINKHOLE -> -6.0;
            case TerrainType.PLAINS -> 17.0;
            case TerrainType.BASIN -> 0.0;
            case TerrainType.DOME -> 83.0;
            case TerrainType.TRENCH -> -44.0;
            case TerrainType.SEA_PLATEAU -> -11.0;
        };
    }

    private double getTierMinimumHeight(int tier) {
        return MacroVoronoiSystem.getTierMinimumHeight(tier);
    }

    private TerrainBlendResult determineDominantTerrainType(List<PointWeight> weightedPoints, double totalWeight) {
        double[] typeWeights = new double[TerrainType.values().length];
        for (PointWeight pw : weightedPoints) {
            double normalizedWeight = pw.weight / totalWeight;
            TerrainType type = pw.point.getTerrainType();
            int idx = type.ordinal();
            if (idx >= typeWeights.length) continue;
            int n = idx;
            typeWeights[n] = typeWeights[n] + normalizedWeight;
        }
        int maxIdx = 0;
        double maxWeight = 0.0;
        for (int i = 0; i < typeWeights.length; ++i) {
            if (!(typeWeights[i] > maxWeight)) continue;
            maxWeight = typeWeights[i];
            maxIdx = i;
        }
        return new TerrainBlendResult(0.0, null, List.of(), 0.0, TerrainType.values()[maxIdx], maxWeight);
    }

    /*
     * C2ME 环境下 fillFromNoise 被并行化，多个线程可能同时创建新区块。
     * 使用 ConcurrentHashMap.computeIfAbsent（桶级锁，不同 key 不互斥）替代全局 synchronized。
     * 淘汰由 AtomicBoolean 守护，确保同一时刻只有一个线程执行淘汰，其他线程直接跳过。
     * evictCache 使用 ConcurrentHashMap.keySet() 的弱一致性迭代器，不阻塞读操作。
     * 
     * Under C2ME, fillFromNoise is parallelized — multiple threads may create new regions concurrently.
     * Uses ConcurrentHashMap.computeIfAbsent (bin-level locking, different keys do NOT contend)
     * instead of a global synchronized block.
     * Eviction is guarded by an AtomicBoolean so only one thread runs it at a time;
     * evictCache uses a weakly-consistent iterator over keySet() that does not block readers.
     */
    private ControlPointRegion getOrCreateRegion(int regionX, int regionZ) {
        long key = (long)regionX << 32 | (long)regionZ & 0xFFFFFFFFL;
        ControlPointRegion region = this.terrainRegionCache.get(key);
        if (region != null) {
            return region;
        }
        // computeIfAbsent uses per-bin locking — threads for different (<regionX>,<regionZ>)
        // pairs do NOT contend on the same lock, eliminating the C2ME serial bottleneck.
        long regionGenStart = System.nanoTime();
        region = this.terrainRegionCache.computeIfAbsent(key, k -> {
            int centerBlockX = regionX * 512 + 256;
            int centerBlockZ = regionZ * 512 + 256;
            int macroTier = this.macroSystem.getRegionInfo(centerBlockX, centerBlockZ).getElevationTier();
            return new ControlPointRegion(regionX, regionZ, this.worldSeed, macroTier);
        });
        long regionGenMs = (System.nanoTime() - regionGenStart) / 1_000_000L;
        if (regionGenMs > 200L) {
            LOGGER.warn("[World Scape] [BLOCK-CHK] SLOW region generation ({},{}): {}ms", new Object[]{regionX, regionZ, regionGenMs});
        }
        // Non-blocking eviction: only one thread runs it; others skip.
        // evictCache() removes entries via ConcurrentHashMap.keySet().remove()
        // which is lock-free for readers, preventing the old synchronized bottleneck.
        if (this.terrainRegionCache.size() > cacheMaxSize
            && evictionInProgress.compareAndSet(false, true)) {
            try {
                this.evictCache();
            } finally {
                evictionInProgress.set(false);
            }
        }
        return region;
    }

    private void evictCache() {
        int removed;
        int toRemove = 512;
        Iterator it = ((ConcurrentHashMap.KeySetView)this.terrainRegionCache.keySet()).iterator();
        for (removed = 0; it.hasNext() && removed < toRemove; ++removed) {
            it.next();
            it.remove();
        }
        LOGGER.debug("[World Scape] Cache evicted {} entries (max={})", (Object)removed, (Object)1024);
    }

    public void clearCache() {
        this.terrainRegionCache.clear();
    }

    public ControlPointRegion getControlPointRegion(int regionX, int regionZ) {
        long key = (long)regionX << 32 | (long)regionZ & 0xFFFFFFFFL;
        ControlPointRegion region = this.terrainRegionCache.get(key);
        if (region == null) {
            region = this.getOrCreateRegion(regionX, regionZ);
        }
        return region;
    }

    public MacroVoronoiSystem getMacroSystem() {
        return this.macroSystem;
    }

    public NoiseSet getNoiseSet() {
        return this.noiseSet;
    }

    public static class BlendCache {
        public final int regionX;
        public final int regionZ;
        public final List<TerrainControlPoint> allPoints;

        public BlendCache(int regionX, int regionZ, List<TerrainControlPoint> allPoints) {
            this.regionX = regionX;
            this.regionZ = regionZ;
            this.allPoints = allPoints;
        }
    }

    public static class TerrainBlendResult {
        public final double blendedHeight;
        public final MacroRegionInfo macroInfo;
        public final List<PointWeight> contributingPoints;
        public final double offsetBlend;
        public final TerrainType dominantType;
        public final double dominantWeight;
        public final ClimateUtils.ClimateProfile blendedClimate;

        TerrainBlendResult(double blendedHeight, MacroRegionInfo macroInfo, List<PointWeight> contributingPoints, double offsetBlend, TerrainType dominantType, double dominantWeight) {
            this(blendedHeight, macroInfo, contributingPoints, offsetBlend, dominantType, dominantWeight, null);
        }

        TerrainBlendResult(double blendedHeight, MacroRegionInfo macroInfo, List<PointWeight> contributingPoints, double offsetBlend, TerrainType dominantType, double dominantWeight, ClimateUtils.ClimateProfile blendedClimate) {
            this.blendedHeight = blendedHeight;
            this.macroInfo = macroInfo;
            this.contributingPoints = contributingPoints;
            this.offsetBlend = offsetBlend;
            this.dominantType = dominantType;
            this.dominantWeight = dominantWeight;
            this.blendedClimate = blendedClimate != null ? blendedClimate : TerrainBlendResult.getDefaultClimateForTier(macroInfo != null ? macroInfo.getElevationTier() : 3);
        }

        private static ClimateUtils.ClimateProfile getDefaultClimateForTier(int tier) {
            return switch (tier) {
                case 0, 1 -> new ClimateUtils.ClimateProfile(0.08, 0.95, 0.05, 0.05);
                case 2 -> new ClimateUtils.ClimateProfile(0.55, 0.75, 0.35, 0.15);
                default -> new ClimateUtils.ClimateProfile(0.5, 0.5, 0.6, 0.5);
            };
        }
    }

    public static class PointWeight {
        public final TerrainControlPoint point;
        public final double weight;
        public final double pointHeight;

        PointWeight(TerrainControlPoint point, double weight, double pointHeight) {
            this.point = point;
            this.weight = weight;
            this.pointHeight = pointHeight;
        }
    }
}

