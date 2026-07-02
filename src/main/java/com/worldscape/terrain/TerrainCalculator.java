package com.worldscape.terrain;

import com.worldscape.terrain.NoiseSet;
import com.worldscape.terrain.RegionController;
import com.worldscape.terrain.TerrainFieldSampler;
import com.worldscape.terrain.TerrainFunctionInterpreter;
import com.worldscape.terrain.TerrainType;
import com.worldscape.terrain.WorldScapeConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TerrainCalculator {
    private static final Logger LOGGER = LoggerFactory.getLogger(TerrainCalculator.class);

    private TerrainCalculator() {
    }

    public static double calculateFinalHeight(int x, int z, RegionController.TerrainBlendResult blend, TerrainType type, NoiseSet noiseSet, TerrainFieldSampler fs) {
        double finalHeight;
        double baseHeight = blend.blendedHeight;
        double dominantWeight = blend.dominantWeight;
        TerrainType dominantType = blend.dominantType;
        if (dominantWeight >= WorldScapeConstants.DOMINANT_WEIGHT_THRESHOLD) {
            finalHeight = TerrainCalculator.calcHeightForType(x, z, baseHeight, dominantType, fs, blend);
        } else if (dominantType == type) {
            // dominantType and type are the same — only one calcHeightForType call needed
            double singleHeight = TerrainCalculator.calcHeightForType(x, z, baseHeight, type, fs, blend);
            finalHeight = singleHeight;
        } else {
            // Two different types: dominantType controls blend shape, type is the current cell's assigned type
            double dominantTypeHeight = TerrainCalculator.calcHeightForType(x, z, baseHeight, dominantType, fs, blend);
            double currentTypeHeight = TerrainCalculator.calcHeightForType(x, z, baseHeight, type, fs, blend);
            // @AESTHETIC: Smoothstep blendFactor replaces linear interpolation to eliminate
            // vertical cliff faces at Voronoi cell boundaries. smoothstep (Hermite: t²(3-2t))
            // produces C¹-continuous transitions with zero derivative at endpoints.
            double blendFactor = dominantWeight / WorldScapeConstants.DOMINANT_WEIGHT_THRESHOLD;
            // smoothstep: t²(3 - 2t), clamps 0..1, C¹-continuous at boundaries
            double t = Math.max(0.0, Math.min(1.0, blendFactor));
            double smoothFactor = t * t * (3.0 - 2.0 * t);
            finalHeight = dominantTypeHeight * smoothFactor + currentTypeHeight * (1.0 - smoothFactor);
        }
        return finalHeight;
    }

    public static TerrainType determineTerrainType(RegionController.TerrainBlendResult blend) {
        TerrainType dominantType = blend.dominantType;
        double dominantWeight = blend.dominantWeight;
        if (dominantType != null && dominantWeight >= WorldScapeConstants.DOMINANT_WEIGHT_THRESHOLD) {
            return dominantType;
        }
        int tier = blend.macroInfo.elevationTier;
        if (tier <= 0) {
            return TerrainType.TRENCH;
        }
        if (tier == 1) {
            return TerrainType.SEA_PLATEAU;
        }
        if (tier == 2) {
            // FLOODPLAIN is the safe inland default for tier 2.
            // BEACH requires ocean proximity validation (validateCoastalType in ControlPointRegion),
            // so it must not be used as a blind fallback here.
            // FLOODPLAIN 是 tier 2 的安全内陆默认值。
            // BEACH 需要海洋邻近性验证（ControlPointRegion 中的 validateCoastalType），
            // 因此不能在此处作为盲目回退使用。
            return TerrainType.FLOODPLAIN;
        }
        if (tier == 3) {
            return TerrainType.PLAINS;
        }
        if (tier == 4) {
            return TerrainType.HILLS;
        }
        return TerrainType.HIGH_MOUNTAINS;
    }

    /**
     * Blend-aware variant that uses TerrainFieldSampler for fallback type selection.
     * When dominantWeight is below threshold, samples moisture-based type instead of
     * hardcoded tier→type mapping. Falls back to hardcoded mapping if fieldSampler is null.
     */
    public static TerrainType determineTerrainType(RegionController.TerrainBlendResult blend,
                                                    TerrainFieldSampler fieldSampler,
                                                    int worldX, int worldZ) {
        TerrainType dominantType = blend.dominantType;
        double dominantWeight = blend.dominantWeight;
        if (dominantType != null && dominantWeight >= WorldScapeConstants.DOMINANT_WEIGHT_THRESHOLD) {
            return dominantType;
        }
        int tier = blend.macroInfo.elevationTier;
        if (fieldSampler != null) {
            double moisture = fieldSampler.sampleMoisture(worldX, worldZ);
            return fieldSampler.selectTypeByMoisture(tier, moisture, worldX, worldZ);
        }
        // Null-safe fallback to original hardcoded mapping
        if (tier <= 0) return TerrainType.TRENCH;
        if (tier == 1) return TerrainType.SEA_PLATEAU;
        if (tier == 2) return TerrainType.FLOODPLAIN;
        if (tier == 3) return TerrainType.PLAINS;
        if (tier == 4) return TerrainType.HILLS;
        return TerrainType.HIGH_MOUNTAINS;
    }

    // @AESTHETIC: Original method kept for backward compatibility.
    // Calls blend-aware variant with null blend (control point info not available).
    public static double calcHeightForType(int worldX, int worldZ, double baseHeight, TerrainType type, TerrainFieldSampler fs) {
        return calcHeightForType(worldX, worldZ, baseHeight, type, fs, null);
    }

    // @AESTHETIC: Blend-aware variant — terrain height is now determined by the
    // JSON-based function definition stored in each TerrainType, interpreted at
    // runtime by TerrainFunctionInterpreter. The old 29-branch if-else chain has
    // been replaced with this data-driven registry approach.
    public static double calcHeightForType(int worldX, int worldZ, double baseHeight, TerrainType type, TerrainFieldSampler fs, RegionController.TerrainBlendResult blend) {
        FunctionDef functionDef = type.getFunctionDef();
        if (functionDef == null || blend == null) {
            LOGGER.warn("[WorldScape] FunctionDef is null or blend is null for terrain type: {}, using FBM fallback height. functionDef={}, blend={}",
                // Fallback path: degraded but expected to function — log at WARN, not ERROR.
                // 回退路径：降级但预期可运行 — 使用 WARN 级别而非 ERROR。
                type != null ? type.getId() : "null", functionDef, blend);
            double fallback = fs.sampleFbm(worldX, worldZ, WorldScapeConstants.FBM_FALLBACK_OCTAVES, WorldScapeConstants.FBM_FALLBACK_GAIN) * WorldScapeConstants.FBM_FALLBACK_AMPLITUDE;
            return baseHeight + fallback;
        }

        double result;
        try {
            result = TerrainFunctionInterpreter.evaluate(functionDef, worldX, worldZ, fs, blend);
        } catch (IllegalArgumentException e) {
            LOGGER.error("[WorldScape] Function evaluation failed for terrain type {}: {}", type.getId(), e.getMessage());
            return baseHeight;
        }

        // 唯一钳制点 1/2 — calcHeightForType main path
        return Math.max(WorldScapeConstants.MIN_TERRAIN_HEIGHT,
               Math.min(WorldScapeConstants.TERRAIN_HARD_CLAMP, result));
    }

    public static double getRiverErosionIntensity(int worldX, int worldZ, NoiseSet noiseSet, double baseHeight, int seaLevel, RegionController.TerrainBlendResult blend) {
        if (blend.macroInfo.elevationTier < WorldScapeConstants.HILLS_TIER_THRESHOLD) {
            return 0.0;
        }
        double erosionNoise = noiseSet.sample(NoiseSet.NoiseProfile.DRAINAGE, worldX, worldZ);
        if (erosionNoise < WorldScapeConstants.EROSION_NOISE_THRESHOLD) {
            return 0.0;
        }
        double intensity = (erosionNoise - WorldScapeConstants.EROSION_NOISE_THRESHOLD) / WorldScapeConstants.EROSION_NOISE_RANGE;
        double elevationFactor = Math.max(0.0, (baseHeight - (double)seaLevel) / WorldScapeConstants.ELEVATION_NORMALIZATION_FACTOR);
        return intensity * elevationFactor * WorldScapeConstants.EROSION_INTENSITY_FACTOR;
    }

    // @DEPRECATED: Use the blend-aware overload getAlluvialFactor(worldX, worldZ, noiseSet, baseHeight, seaLevel, blend)
    // which includes a tier check (tier < HILLS_TIER_THRESHOLD → return 0) for consistency with getRiverErosionIntensity().
    // Without the tier check, low-elevation areas may incorrectly produce alluvial deposits.
    @Deprecated
    public static double getAlluvialFactor(int worldX, int worldZ, NoiseSet noiseSet, double baseHeight, int seaLevel) {
        if (baseHeight > (double)(seaLevel + WorldScapeConstants.ALLUVIAL_HEIGHT_RANGE)) {
            return 0.0;
        }
        double alluvialNoise = noiseSet.sample(NoiseSet.NoiseProfile.SEABED, worldX, worldZ);
        if (alluvialNoise < WorldScapeConstants.ALLUVIAL_THRESHOLD) {
            return 0.0;
        }
        double factor = (alluvialNoise - WorldScapeConstants.ALLUVIAL_THRESHOLD) / WorldScapeConstants.EROSION_NOISE_RANGE;
        double distanceFactor = Math.max(0.0, 1.0 - (baseHeight - (double)seaLevel) / WorldScapeConstants.ALLUVIAL_DISTANCE_RANGE);
        return factor * distanceFactor * WorldScapeConstants.ALLUVIAL_FACTOR;
    }

    // @CONSISTENCY: Blend-aware overload — low-altitude areas (tier < HILLS_TIER_THRESHOLD) produce
    // no alluvial deposition, consistent with getRiverErosionIntensity() which returns 0 for the same tier.
    // Without this check, low-altitude areas could have alluvial deposition without erosion transport,
    // which is logically inconsistent (alluvial deposition should accompany erosion transport).
    public static double getAlluvialFactor(int worldX, int worldZ, NoiseSet noiseSet, double baseHeight, int seaLevel, RegionController.TerrainBlendResult blend) {
        if (blend != null && blend.macroInfo != null
            && blend.macroInfo.elevationTier < WorldScapeConstants.HILLS_TIER_THRESHOLD) {
            return 0.0;
        }
        return getAlluvialFactor(worldX, worldZ, noiseSet, baseHeight, seaLevel);
    }

    // @AESTHETIC: Compute erosion multiplier based on elevation tier.
    // Higher tier = more dramatic terrain = deeper erosion gullies.
    //
    // @DESIGN_NOTE: Threshold set at tier >= 5 (HIGH_MOUNTAINS and above) rather than >= 4.
    // Strong surface erosion (rill/gully formation) requires both high elevation AND
    // sufficient orographic precipitation — conditions typically met only at true mountain
    // tiers (≥5). Hills (tier 4: CLIFF, PLATEAU, VALLEY, CANYON, etc.) may be high but
    // lack the sustained precipitation and steep slopes needed for intense surface erosion.
    // Contrast with getRiverDepthMultiplierForTier() which uses tier ≥ 4 — rivers are
    // hydrologically dominant and can incise deeply into hills without strong surface erosion.
    public static double getErosionMultiplierForTier(int elevationTier) {
        if (elevationTier >= WorldScapeConstants.MOUNTAIN_TIER_THRESHOLD) return WorldScapeConstants.EROSION_MULTIPLIER_MOUNTAIN;
        if (elevationTier <= WorldScapeConstants.LOWLAND_TIER_THRESHOLD) return WorldScapeConstants.EROSION_MULTIPLIER_PLAIN;
        return 1.0;
    }

    // @AESTHETIC: Compute river depth multiplier based on elevation tier.
    // Mountain rivers cut deeper; plain rivers spread wider and shallower.
    //
    // @DESIGN_NOTE: Threshold set at tier >= 4 (HILLS and above) rather than >= 5.
    // Rivers are hydrologically dominant linear features — a river flowing through hills
    // (tier 4: CLIFF, PLATEAU, VALLEY, CANYON) carries enough hydraulic energy to incise
    // a deep channel, creating V-shaped valleys and canyons even on moderate slopes.
    // This is geologically realistic: the Grand Canyon was carved by the Colorado River
    // through the Colorado Plateau (a tier-4-class feature), demonstrating that
    // fluvial incision outpaces surface erosion in intermediate-elevation terrain.
    // @AESTHETIC: Continuous elevation-based river depth multiplier.
    // Uses smoothstep interpolation from sea level to high elevation, replacing hard
    // tier switching. The smoothstep ensures C¹ continuity — river depth transitions
    // remain natural even when crossing elevation boundaries. Both gradient and
    // elevation are smoothly varying fields driven by the same noise system, so
    // river continuity is preserved across all terrain transitions.
    public static double getRiverDepthMultiplierForElevation(double baseHeight, int seaLevel) {
        double elevationAboveSea = baseHeight - (double)seaLevel;
        double t = (elevationAboveSea - WorldScapeConstants.RIVER_DEPTH_ELEVATION_MIN)
                 / (WorldScapeConstants.RIVER_DEPTH_ELEVATION_MAX - WorldScapeConstants.RIVER_DEPTH_ELEVATION_MIN);
        t = Math.max(0.0, Math.min(1.0, t));
        // smoothstep: t²(3 - 2t), C¹-continuous at boundaries
        t = t * t * (3.0 - 2.0 * t);
        return WorldScapeConstants.RIVER_DEPTH_MULTIPLIER_LOW
             + t * (WorldScapeConstants.RIVER_DEPTH_MULTIPLIER_HIGH - WorldScapeConstants.RIVER_DEPTH_MULTIPLIER_LOW);
    }

    public static int calculateErodedHeight(int worldX, int worldZ, double continuousHeight, boolean isRiver, double riverDepth, int seaLevel, NoiseSet noiseSet, RegionController.TerrainBlendResult blend) {
        double erosionIntensity = TerrainCalculator.getRiverErosionIntensity(worldX, worldZ, noiseSet, continuousHeight, seaLevel, blend);
        return TerrainCalculator.calculateErodedHeight(worldX, worldZ, continuousHeight, isRiver, riverDepth, seaLevel, noiseSet, blend, erosionIntensity);
    }

    public static int calculateErodedHeight(int worldX, int worldZ, double continuousHeight, boolean isRiver, double riverDepth, int seaLevel, NoiseSet noiseSet, RegionController.TerrainBlendResult blend, double erosionIntensity) {
        double alluvialFactor = TerrainCalculator.getAlluvialFactor(worldX, worldZ, noiseSet, continuousHeight, seaLevel, blend);
        double erosionMultiplier = blend != null && blend.macroInfo != null
            ? getErosionMultiplierForTier(blend.macroInfo.elevationTier) : 1.0;
        return TerrainCalculator.calculateErodedHeight(continuousHeight, isRiver, riverDepth, seaLevel, erosionIntensity, alluvialFactor, erosionMultiplier);
    }

    // @PERF: Pre-computed alluvialFactor overload — avoids redundant noise sampling
    // when the caller has already computed alluvialFactor (e.g., cached in fillFromNoise).
    public static int calculateErodedHeight(int worldX, int worldZ, double continuousHeight, boolean isRiver, double riverDepth, int seaLevel, NoiseSet noiseSet, RegionController.TerrainBlendResult blend, double erosionIntensity, double alluvialFactor) {
        double erosionMultiplier = blend != null && blend.macroInfo != null
            ? getErosionMultiplierForTier(blend.macroInfo.elevationTier) : 1.0;
        return TerrainCalculator.calculateErodedHeight(continuousHeight, isRiver, riverDepth, seaLevel, erosionIntensity, alluvialFactor, erosionMultiplier);
    }

    public static int calculateErodedHeight(double continuousHeight, boolean isRiver, double riverDepth, int seaLevel, double erosionIntensity, double alluvialFactor, double erosionMultiplier) {
        double erodedHeight = continuousHeight;
        if (isRiver && erosionIntensity > WorldScapeConstants.RIVER_DIFF_THRESHOLD) {
            double erosionCut = erosionIntensity * WorldScapeConstants.EROSION_CUT_MULTIPLIER * erosionMultiplier;
            erodedHeight = continuousHeight - erosionCut;
        }
        // @CONSISTENCY: Use ALLUVIAL_THRESHOLD (0.45) to match getAlluvialFactor()'s internal threshold.
        // RIVER_DIFF_THRESHOLD (0.1) is for river noise detection; ALLUVIAL_THRESHOLD is for alluvial deposition.
        if (alluvialFactor > WorldScapeConstants.ALLUVIAL_THRESHOLD) {
            double alluvialRaise = alluvialFactor * WorldScapeConstants.ALLUVIAL_RAISE_MULTIPLIER;
            erodedHeight += alluvialRaise;
        }
        return (int)Math.floor(erodedHeight);
    }

    public static int calculateActualSurfaceHeight(int terrainHeight, boolean isRiver, double riverDepth, int minY) {
        int height;
        if (isRiver && riverDepth > WorldScapeConstants.RIVER_DEPTH_THRESHOLD) {
            height = (int)Math.max((double)minY, (double)terrainHeight - riverDepth);
        } else {
            height = terrainHeight;
        }
        // 唯一钳制点 2/2 — calculateActualSurfaceHeight final output
        // Clamp both lower and upper bounds to prevent negative surface heights
        // 同时钳制上下限，防止负值地表高度
        return Math.max(WorldScapeConstants.MIN_TERRAIN_HEIGHT, Math.min(WorldScapeConstants.TERRAIN_HARD_CLAMP, height));
    }

    public static boolean isRiverAt(int worldX, int worldZ, NoiseSet noiseSet) {
        double riverNoise = noiseSet.sample(NoiseSet.NoiseProfile.RIVER_PATH, worldX, worldZ);
        double riverPath = noiseSet.sample(NoiseSet.NoiseProfile.RIVER_WIDTH, worldX, worldZ);
        return riverNoise > WorldScapeConstants.RIVER_DIFF_THRESHOLD && Math.abs(riverPath) < WorldScapeConstants.RIVER_WIDTH_THRESHOLD;
    }

    public static double getRiverDepthAt(int worldX, int worldZ, NoiseSet noiseSet, int surfaceHeight, int seaLevel) {
        return TerrainCalculator.getRiverDepthAt(worldX, worldZ, noiseSet, surfaceHeight, seaLevel, TerrainCalculator.isRiverAt(worldX, worldZ, noiseSet), 1.0);
    }

    public static double getRiverDepthAt(int worldX, int worldZ, NoiseSet noiseSet, int surfaceHeight, int seaLevel, boolean isRiver) {
        return getRiverDepthAt(worldX, worldZ, noiseSet, surfaceHeight, seaLevel, isRiver, 1.0);
    }

    // @AESTHETIC: River depth with terrain-type-dependent multiplier.
    public static double getRiverDepthAt(int worldX, int worldZ, NoiseSet noiseSet, int surfaceHeight, int seaLevel, boolean isRiver, double depthMultiplier) {
        if (!isRiver) {
            return 0.0;
        }
        double riverNoise = noiseSet.sample(NoiseSet.NoiseProfile.RIVER_PATH, worldX, worldZ);
        double depth = (riverNoise - WorldScapeConstants.RIVER_DIFF_THRESHOLD) * WorldScapeConstants.RIVER_DEPTH_SCALE * WorldScapeConstants.RIVER_DEPTH_AMPLIFIER * depthMultiplier;
        return Math.max(WorldScapeConstants.RIVER_MIN_DEPTH, Math.min(depth, WorldScapeConstants.RIVER_MAX_DEPTH));
    }

    // @AESTHETIC: Gradient-aware river depth calculation.
    // Combines elevation-based continuous interpolation with terrain gradient —
    // steeper slopes (mountains, canyons) produce deeper river incision, while
    // flatter terrain (plains, deltas) produces shallower rivers.
    // Both gradient and elevation are smoothly varying fields driven by the same
    // noise system, so river continuity is preserved across all terrain transitions.
    public static double getRiverDepthAt(int worldX, int worldZ, NoiseSet noiseSet,
            int surfaceHeight, int seaLevel, boolean isRiver,
            TerrainFieldSampler fieldSampler, double baseHeight) {
        if (!isRiver || fieldSampler == null) {
            return 0.0;
        }
        // 1. Base noise-driven depth (preserves river continuity)
        double riverNoise = noiseSet.sample(NoiseSet.NoiseProfile.RIVER_PATH, worldX, worldZ);
        double baseDepth = (riverNoise - WorldScapeConstants.RIVER_DIFF_THRESHOLD)
                         * WorldScapeConstants.RIVER_DEPTH_SCALE
                         * WorldScapeConstants.RIVER_DEPTH_AMPLIFIER;

        // 2. Elevation-based continuous multiplier (replaces hard tier switching)
        double elevationMultiplier = getRiverDepthMultiplierForElevation(baseHeight, seaLevel);

        // 3. Gradient-based factor: steeper terrain → deeper river incision
        // Gradient is a smoothly varying field, preserving continuity
        double gradient = fieldSampler.calculateGradient(worldX, worldZ);
        double normalizedGradient = Math.min(1.0, gradient / WorldScapeConstants.RIVER_GRADIENT_REFERENCE);
        double gradientFactor = 1.0 + normalizedGradient * WorldScapeConstants.RIVER_GRADIENT_DEPTH_FACTOR;

        double depth = baseDepth * elevationMultiplier * gradientFactor;
        return Math.max(WorldScapeConstants.RIVER_MIN_DEPTH,
                       Math.min(depth, WorldScapeConstants.RIVER_MAX_DEPTH));
    }
}