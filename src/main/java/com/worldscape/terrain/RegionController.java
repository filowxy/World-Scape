package com.worldscape.terrain;

import com.worldscape.WorldScape;
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

/**
 * Manages terrain blending across macro-regions by caching ControlPointRegion
 * instances and computing blended terrain heights with associated climate profiles.
 * Uses ConcurrentHashMap for thread-safe access during parallel chunk generation
 * (e.g., under C2ME) and performs non-blocking cache eviction when capacity is exceeded.
 */
public class RegionController {
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

    // Use WorldScape.LOGGER for unified logging

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
        // Cache-less path searches 3×3 regions (searchRadius=1200); cached path filters by influenceRadius.
        // Both are functionally equivalent: the cache contains the same 3×3 region points,
        // and all influence radii are less than searchRadius.
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
                TerrainType defaultType = getDefaultTerrainTypeForTier(macroInfo.getElevationTier());
                return new TerrainBlendResult(macroBaseHeight, macroInfo, List.of(), 0.0, defaultType, 0.0);
            }
        } else {
            int regionX = Math.floorDiv(x, ControlPointRegion.REGION_SIZE);
            int regionZ = Math.floorDiv(z, ControlPointRegion.REGION_SIZE);
            points = new ArrayList();
            double searchRadius = WorldScapeConstants.CONTROL_POINT_SEARCH_RADIUS;
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

    private TerrainType getDefaultTerrainTypeForTier(int tier) {
        return switch (tier) {
            case 0, 1 -> TerrainType.SEA_PLATEAU;
            // Tier 2 default is FLOODPLAIN (not BEACH) because BEACH requires
            // actual ocean proximity, which is validated at control-point level.
            // FLOODPLAIN is the safe inland equivalent at tier 2.
            // Tier 2 默认为 FLOODPLAIN（非 BEACH），因为 BEACH 需要实际海洋邻近性，
            // 该邻近性在控制点级别验证。FLOODPLAIN 是 tier 2 的安全内陆等价物。
            case 2 -> TerrainType.FLOODPLAIN;
            case 3 -> TerrainType.PLAINS;
            case 4 -> TerrainType.HILLS;
            case 5 -> TerrainType.HIGH_MOUNTAINS;
            default -> TerrainType.PLAINS;
        };
    }

    private TerrainBlendResult calculateBlend(int x, int z, List<TerrainControlPoint> points, MacroRegionInfo macroInfo) {
        double finalHeight;
        double blendWeight = macroInfo.getBlendWeight();
        double macroBaseHeight = macroInfo.getBaseHeight();
        if (points.isEmpty()) {
            WorldScape.LOGGER.debug("[World Scape] calculateBlend({}, {}) no points, using macro base: {}", x, z, macroBaseHeight);
            TerrainType defaultType = getDefaultTerrainTypeForTier(macroInfo.getElevationTier());
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
            TerrainType defaultType = getDefaultTerrainTypeForTier(macroInfo.getElevationTier());
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
        // macroInfluence naturally approaches 0 as blendWeight → 1.0,
        // eliminating the need for a hard threshold switch.
        // Use sqrt() instead of smoothstep() for wider boundary influence —
        // sqrt(0.2) ≈ 0.447 vs smoothstep(0.2) ≈ 0.104, so macro smoothing
        // reaches further into the region interior, eliminating cliffs.
        double boundaryProximityRaw = 1.0 - Math.abs(blendWeight - 0.5) * 2.0;
        boundaryProximityRaw = Math.max(0.0, Math.min(1.0, boundaryProximityRaw));
        double boundaryProximity = Math.sqrt(boundaryProximityRaw);
        // Scale blendWeight-based proximity by tier gap: larger gap → stronger macro pull.
        // 按层级差距缩放基于 blendWeight 的邻近性：差距越大 → 宏观拉力越强。
        int tierGap = Math.abs(primaryTier - secondTier);
        double tierGapFactor = 1.0 + tierGap * WorldScapeConstants.TIER_GAP_FACTOR_SCALE;
        // Let macroInfluence be naturally determined by boundaryProximity and tierGapFactor,
        // without artificial MAX_MACRO_INFLUENCE hard cap or oceanic tier damping.
        // 让 macroInfluence 由 boundaryProximity 和 tierGapFactor 自然决定，
        // 不使用人为的 MAX_MACRO_INFLUENCE 硬上限或海洋层级阻尼。
        double macroInfluence = boundaryProximity * tierGapFactor;
        finalHeight = this.lerp(macroBaseHeight + microHeight + tierAdjustment, macroBaseHeight, macroInfluence);
        // Clamp finalHeight to within world bounds to prevent extreme terrain
        // 将 finalHeight 限制在世界边界内以防止极端地形
        finalHeight = Math.max(WorldScapeConstants.MIN_TERRAIN_HEIGHT, Math.min(WorldScapeConstants.TERRAIN_HARD_CLAMP, finalHeight));
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
        if (sorted.size() < 2 || primaryNormWeight >= WorldScapeConstants.CLIMATE_BLEND_THRESHOLD) {
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
            case 0, 1 -> new ClimateUtils.ClimateProfile(WorldScapeConstants.DEEP_OCEAN_TEMP, WorldScapeConstants.DEEP_OCEAN_HUMID, WorldScapeConstants.DEEP_OCEAN_SEASON, WorldScapeConstants.DEEP_OCEAN_CONT);
            case 2 -> new ClimateUtils.ClimateProfile(WorldScapeConstants.COASTAL_TEMPERATE_TEMP, WorldScapeConstants.COASTAL_TEMPERATE_HUMID, WorldScapeConstants.COASTAL_TEMPERATE_SEASON, WorldScapeConstants.COASTAL_TEMPERATE_CONT);
            default -> new ClimateUtils.ClimateProfile(WorldScapeConstants.PLAINS_TEMPERATURE, WorldScapeConstants.PLAINS_HUMIDITY, WorldScapeConstants.PLAINS_SEASONALITY, WorldScapeConstants.PLAINS_CONTINENTALITY);
        };
    }

    private double getBaseHeightForTerrainType(TerrainType type) {
        // Delegates to the single source of truth in TerrainType to avoid duplication.
        // Previously this method contained a 60-line if-else chain identical to
        // HeightCalculator.getBaseHeightForTerrainType — consolidated to prevent drift.
        // 委托给 TerrainType 中的唯一数据源以避免重复。
        // 之前此方法包含与 HeightCalculator.getBaseHeightForTerrainType 相同的 60 行 if-else 链
        // — 已合并以防止不一致。
        return TerrainType.getBaseHeightForType(type);
    }

    private double getTierMinimumHeight(int tier) {
        return MacroVoronoiSystem.getTierMinimumHeight(tier);
    }

    private TerrainBlendResult determineDominantTerrainType(List<PointWeight> weightedPoints, double totalWeight) {
        java.util.Map<TerrainType, Double> typeWeights = new java.util.HashMap<>();
        for (PointWeight pw : weightedPoints) {
            double normalizedWeight = pw.weight / totalWeight;
            TerrainType type = pw.point.getTerrainType();
            typeWeights.merge(type, normalizedWeight, Double::sum);
        }
        TerrainType maxType = null;
        double maxWeight = 0.0;
        for (java.util.Map.Entry<TerrainType, Double> entry : typeWeights.entrySet()) {
            if (entry.getValue() > maxWeight) {
                maxWeight = entry.getValue();
                maxType = entry.getKey();
            }
        }
        return new TerrainBlendResult(0.0, null, List.of(), 0.0, maxType, maxWeight);
    }

    /*
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
            int centerBlockX = regionX * ControlPointRegion.REGION_SIZE + ControlPointRegion.REGION_SIZE / 2;
            int centerBlockZ = regionZ * ControlPointRegion.REGION_SIZE + ControlPointRegion.REGION_SIZE / 2;
            int macroTier = this.macroSystem.getRegionInfo(centerBlockX, centerBlockZ).getElevationTier();
            return new ControlPointRegion(regionX, regionZ, this.worldSeed, macroTier, this.macroSystem);
        });
        long regionGenMs = (System.nanoTime() - regionGenStart) / 1_000_000L;
        if (regionGenMs > WorldScapeConstants.REGION_GEN_SLOW_THRESHOLD_MS) {
            WorldScape.LOGGER.warn("[World Scape] [BLOCK-CHK] SLOW region generation ({},{}): {}ms", regionX, regionZ, regionGenMs);
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
        int toRemove = WorldScapeConstants.CACHE_EVICTION_BATCH_SIZE;
        Iterator it = ((ConcurrentHashMap.KeySetView)this.terrainRegionCache.keySet()).iterator();
        for (removed = 0; it.hasNext() && removed < toRemove; ++removed) {
            it.next();
            it.remove();
        }
        WorldScape.LOGGER.debug("[World Scape] Cache evicted {} entries (max={})", removed, cacheMaxSize);
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
                case 0, 1 -> new ClimateUtils.ClimateProfile(WorldScapeConstants.DEEP_OCEAN_TEMP, WorldScapeConstants.DEEP_OCEAN_HUMID, WorldScapeConstants.DEEP_OCEAN_SEASON, WorldScapeConstants.DEEP_OCEAN_CONT);
                case 2 -> new ClimateUtils.ClimateProfile(WorldScapeConstants.COASTAL_TEMPERATE_TEMP, WorldScapeConstants.COASTAL_TEMPERATE_HUMID, WorldScapeConstants.COASTAL_TEMPERATE_SEASON, WorldScapeConstants.COASTAL_TEMPERATE_CONT);
                default -> new ClimateUtils.ClimateProfile(WorldScapeConstants.PLAINS_TEMPERATURE, WorldScapeConstants.PLAINS_HUMIDITY, WorldScapeConstants.PLAINS_SEASONALITY, WorldScapeConstants.PLAINS_CONTINENTALITY);
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

