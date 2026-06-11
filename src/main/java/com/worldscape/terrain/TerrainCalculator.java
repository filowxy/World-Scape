/*
 * Decompiled with CFR 0.152.
 */
package com.worldscape.terrain;

import com.worldscape.terrain.NoiseSet;
import com.worldscape.terrain.RegionController;
import com.worldscape.terrain.TerrainFieldSampler;
import com.worldscape.terrain.TerrainFunctionInterpreter;
import com.worldscape.terrain.TerrainFunctionSchema;
import com.worldscape.terrain.TerrainType;
import com.worldscape.terrain.WorldScapeConstants;

public final class TerrainCalculator {
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
        // 用 smoothstep 替代线性插值消除 Voronoi 单元边界的垂直悬崖。
            double blendFactor = dominantWeight / WorldScapeConstants.DOMINANT_WEIGHT_THRESHOLD;
            // smoothstep: t²(3 - 2t), clamps 0..1, C¹-continuous at boundaries
            // Hermite 平滑：t²(3-2t)，在边界处导数为零，过渡自然
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
            return TerrainType.BEACH;
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
     *
     * 带地形场采样器的变体，在回退路径使用湿度采样替代硬编码 Tier→类型映射。
     * 当 fieldSampler 为 null 时回退到硬编码映射作为安全兜底。
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
            // Sample moisture from the field sampler for type selection
            // 从场地采样器获取湿度值用于类型选择
            double moisture = fieldSampler.sampleMoisture(worldX, worldZ);
            return fieldSampler.selectTypeByMoisture(tier, moisture, worldX, worldZ);
        }
        // Null-safe fallback to original hardcoded mapping
        // 当 fieldSampler 为 null 时回退到原硬编码映射
        if (tier <= 0) return TerrainType.TRENCH;
        if (tier == 1) return TerrainType.SEA_PLATEAU;
        if (tier == 2) return TerrainType.BEACH;
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
    // 地形高度现在由每个 TerrainType 中存储的 JSON 函数定义决定，
    // 在运行时由 TerrainFunctionInterpreter 解释执行。
    // 旧的 29 分支 if-else 链已被替换为数据驱动的注册表方式。
    public static double calcHeightForType(int worldX, int worldZ, double baseHeight, TerrainType type, TerrainFieldSampler fs, RegionController.TerrainBlendResult blend) {
        TerrainFunctionSchema.FunctionDef functionDef = type.getFunctionDef();
        if (functionDef == null || blend == null) {
            double fallback = fs.sampleFbm(worldX, worldZ, 4, 0.2) * 15.0;
            return Math.max(WorldScapeConstants.MIN_TERRAIN_HEIGHT,
                   Math.min(WorldScapeConstants.MAX_TERRAIN_HEIGHT,
                   baseHeight + fallback));
        }

        double result = TerrainFunctionInterpreter.evaluate(functionDef, worldX, worldZ, fs, blend);

        return Math.max(WorldScapeConstants.MIN_TERRAIN_HEIGHT,
               Math.min(380.0, result));
    }

    public static double getRiverErosionIntensity(int worldX, int worldZ, NoiseSet noiseSet, double baseHeight, int seaLevel, RegionController.TerrainBlendResult blend) {
        if (blend.macroInfo.elevationTier < WorldScapeConstants.HILLS_TIER_THRESHOLD) {
            return 0.0;
        }
        double erosionNoise = noiseSet.sample(NoiseSet.NoiseProfile.DRAINAGE, worldX, worldZ);
        if (erosionNoise < WorldScapeConstants.EROSION_NOISE_THRESHOLD) {
            return 0.0;
        }
        double intensity = (erosionNoise - WorldScapeConstants.EROSION_NOISE_THRESHOLD) / 0.55;
        double elevationFactor = Math.max(0.0, (baseHeight - (double)seaLevel) / WorldScapeConstants.ELEVATION_NORMALIZATION_FACTOR);
        return intensity * elevationFactor * WorldScapeConstants.EROSION_INTENSITY_FACTOR;
    }

    // @DEPRECATED: Use the blend-aware overload getAlluvialFactor(worldX, worldZ, noiseSet, baseHeight, seaLevel, blend)
    // which includes a tier check (tier < HILLS_TIER_THRESHOLD → return 0) for consistency with getRiverErosionIntensity().
    // Without the tier check, low-elevation areas may incorrectly produce alluvial deposits.
    // 请使用带 blend 参数的重载版本，它包含海拔等级检查以保证与侵蚀计算逻辑一致。
    @Deprecated
    public static double getAlluvialFactor(int worldX, int worldZ, NoiseSet noiseSet, double baseHeight, int seaLevel) {
        if (baseHeight > (double)(seaLevel + WorldScapeConstants.ALLUVIAL_HEIGHT_RANGE)) {
            return 0.0;
        }
        double alluvialNoise = noiseSet.sample(NoiseSet.NoiseProfile.SEABED, worldX, worldZ);
        if (alluvialNoise < WorldScapeConstants.ALLUVIAL_THRESHOLD) {
            return 0.0;
        }
        double factor = (alluvialNoise - WorldScapeConstants.ALLUVIAL_THRESHOLD) / 0.55;
        double distanceFactor = Math.max(0.0, 1.0 - (baseHeight - (double)seaLevel) / WorldScapeConstants.ALLUVIAL_DISTANCE_RANGE);
        return factor * distanceFactor * WorldScapeConstants.ALLUVIAL_FACTOR;
    }

    // @CONSISTENCY: Blend-aware overload — low-altitude areas (tier < HILLS_TIER_THRESHOLD) produce
    // no alluvial deposition, consistent with getRiverErosionIntensity() which returns 0 for the same tier.
    // Without this check, low-altitude areas could have alluvial deposition without erosion transport,
    // which is logically inconsistent (alluvial deposition should accompany erosion transport).
    // 带地形混合信息的冲积因子计算——低海拔区域（tier < HILLS_TIER_THRESHOLD）不产生冲积沉积，
    // 与 getRiverErosionIntensity() 在相同等级返回 0 保持一致。
    // 没有此检查，低海拔区域可能出现无侵蚀搬运的冲积沉积，逻辑上不一致
    // （冲积沉积应伴随侵蚀搬运过程）。
    public static double getAlluvialFactor(int worldX, int worldZ, NoiseSet noiseSet, double baseHeight, int seaLevel, RegionController.TerrainBlendResult blend) {
        if (blend != null && blend.macroInfo != null
            && blend.macroInfo.elevationTier < WorldScapeConstants.HILLS_TIER_THRESHOLD) {
            return 0.0;
        }
        return getAlluvialFactor(worldX, worldZ, noiseSet, baseHeight, seaLevel);
    }

    // @AESTHETIC: Compute erosion multiplier based on elevation tier.
    // Higher tier = more dramatic terrain = deeper erosion gullies.
    // 基于海拔等级的侵蚀乘数：高等级→更深的侵蚀沟壑。
    //
    // @DESIGN_NOTE: Threshold set at tier >= 5 (HIGH_MOUNTAINS and above) rather than >= 4.
    // Strong surface erosion (rill/gully formation) requires both high elevation AND
    // sufficient orographic precipitation — conditions typically met only at true mountain
    // tiers (≥5). Hills (tier 4: CLIFF, PLATEAU, VALLEY, CANYON, etc.) may be high but
    // lack the sustained precipitation and steep slopes needed for intense surface erosion.
    // Contrast with getRiverDepthMultiplierForTier() which uses tier ≥ 4 — rivers are
    // hydrologically dominant and can incise deeply into hills without strong surface erosion.
    // 设计说明：阈值设为 tier >= 5（HIGH_MOUNTAINS 及以上）而非 >= 4。
    // 强地表侵蚀（细沟/冲沟形成）需要高海拔和足够的地形降水——这些条件通常只在真正的
    // 山脉等级（≥5）才能满足。丘陵（tier 4: CLIFF, PLATEAU, VALLEY, CANYON 等）可能很高，
    // 但缺乏持续降水和陡坡条件来形成强地表侵蚀。
    // 对比 getRiverDepthMultiplierForTier() 使用 tier ≥ 4——河流作为水文主导特征，
    // 可在无强地表侵蚀的情况下深深切入丘陵地形。
    public static double getErosionMultiplierForTier(int elevationTier) {
        if (elevationTier >= 5) return WorldScapeConstants.EROSION_MULTIPLIER_MOUNTAIN;
        if (elevationTier <= 2) return WorldScapeConstants.EROSION_MULTIPLIER_PLAIN;
        return 1.0;
    }

    // @AESTHETIC: Compute river depth multiplier based on elevation tier.
    // Mountain rivers cut deeper; plain rivers spread wider and shallower.
    // 基于海拔等级的河流深度乘数：山区河流切割更深，平原河流更浅更宽。
    //
    // @DESIGN_NOTE: Threshold set at tier >= 4 (HILLS and above) rather than >= 5.
    // Rivers are hydrologically dominant linear features — a river flowing through hills
    // (tier 4: CLIFF, PLATEAU, VALLEY, CANYON) carries enough hydraulic energy to incise
    // a deep channel, creating V-shaped valleys and canyons even on moderate slopes.
    // This is geologically realistic: the Grand Canyon was carved by the Colorado River
    // through the Colorado Plateau (a tier-4-class feature), demonstrating that
    // fluvial incision outpaces surface erosion in intermediate-elevation terrain.
    // Contrast with getErosionMultiplierForTier() which requires tier ≥ 5 — broad
    // surface erosion demands sustained precipitation, steep slopes, and relief that
    // only true mountain tiers provide.
    // 设计说明：阈值设为 tier >= 4（HILLS 及以上）而非 >= 5。
    // 河流是水文主导的线性特征——流经丘陵（tier 4: CLIFF, PLATEAU, VALLEY, CANYON）的
    // 河流携带足够的水力能量来切割深河道，即使在中等坡度上也能形成 V 形谷和峡谷。
    // 这在地质上是现实的：科罗拉多大峡谷正是由科罗拉多河穿过科罗拉多高原
    // （典型的 tier-4 级地貌）冲刷而成，证明了河流下切在中海拔地形中超过地表侵蚀。
    // 对比 getErosionMultiplierForTier() 要求 tier ≥ 5——广泛的地表侵蚀需要
    // 持续降水、陡坡和地形起伏，这些只有真正的山脉等级才能提供。
    @Deprecated
    public static double getRiverDepthMultiplierForTier(int elevationTier) {
        if (elevationTier >= 4) return WorldScapeConstants.RIVER_DEPTH_MULTIPLIER_MOUNTAIN;
        if (elevationTier <= 2) return WorldScapeConstants.RIVER_DEPTH_MULTIPLIER_PLAIN;
        return 1.0;
    }

    // @AESTHETIC: Continuous elevation-based river depth multiplier.
    // Uses smoothstep interpolation from sea level to high elevation, replacing hard
    // tier switching. The smoothstep ensures C¹ continuity — river depth transitions
    // remain natural even when crossing elevation boundaries. Both gradient and
    // elevation are smoothly varying fields driven by the same noise system, so
    // river continuity is preserved across all terrain transitions.
    // 基于海拔的连续河流深度乘数：使用 smoothstep 从海平面到高海拔平滑插值，
    // 替代硬切换。smoothstep 保证 C¹ 连续性——河流深度过渡在跨越海拔边界时也保持自然。
    // 梯度和海拔都由同一噪声系统驱动的平滑场，因此河流连续性在所有地形过渡中得以保持。
    public static double getRiverDepthMultiplierForElevation(double baseHeight, int seaLevel) {
        double elevationAboveSea = baseHeight - (double)seaLevel;
        double t = (elevationAboveSea - WorldScapeConstants.RIVER_DEPTH_ELEVATION_MIN)
                 / (WorldScapeConstants.RIVER_DEPTH_ELEVATION_MAX - WorldScapeConstants.RIVER_DEPTH_ELEVATION_MIN);
        t = Math.max(0.0, Math.min(1.0, t));
        // smoothstep: t²(3 - 2t), C¹-continuous at boundaries
        // smoothstep 在边界处导数为零，过渡自然
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
    // 预计算 alluvialFactor 的重载——当调用方已计算 alluvialFactor 时避免重复噪声采样。
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
        // 使用 ALLUVIAL_THRESHOLD (0.45) 与 getAlluvialFactor() 内部阈值保持一致。
        // RIVER_DIFF_THRESHOLD (0.1) 用于河流噪声检测；ALLUVIAL_THRESHOLD 用于冲积沉积判断。
        if (alluvialFactor > WorldScapeConstants.ALLUVIAL_THRESHOLD) {
            double alluvialRaise = alluvialFactor * WorldScapeConstants.ALLUVIAL_RAISE_MULTIPLIER;
            erodedHeight += alluvialRaise;
        }
        return (int)Math.floor(erodedHeight);
    }

    public static int calculateActualSurfaceHeight(int terrainHeight, boolean isRiver, double riverDepth, int minY) {
        if (isRiver && riverDepth > WorldScapeConstants.RIVER_DEPTH_THRESHOLD) {
            return (int)Math.max((double)minY, (double)terrainHeight - riverDepth);
        }
        return terrainHeight;
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
    // 带地形乘数的河流深度计算。
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
    // 基于梯度的河流深度计算：结合海拔连续插值和地形梯度——
    // 陡坡（山脉、峡谷）产生更深河流切割，平坦地形（平原、三角洲）产生更浅河流。
    // 梯度和海拔都由同一噪声系统驱动的平滑场，因此河流连续性在所有地形过渡中保持。
    public static double getRiverDepthAt(int worldX, int worldZ, NoiseSet noiseSet,
            int surfaceHeight, int seaLevel, boolean isRiver,
            TerrainFieldSampler fieldSampler, double baseHeight) {
        if (!isRiver || fieldSampler == null) {
            return 0.0;
        }
        // 1. Base noise-driven depth (preserves river continuity / 保持河流连续性)
        double riverNoise = noiseSet.sample(NoiseSet.NoiseProfile.RIVER_PATH, worldX, worldZ);
        double baseDepth = (riverNoise - WorldScapeConstants.RIVER_DIFF_THRESHOLD)
                         * WorldScapeConstants.RIVER_DEPTH_SCALE
                         * WorldScapeConstants.RIVER_DEPTH_AMPLIFIER;

        // 2. Elevation-based continuous multiplier (replaces hard tier switching)
        // 基于海拔的连续乘数（替代硬切换）
        double elevationMultiplier = getRiverDepthMultiplierForElevation(baseHeight, seaLevel);

        // 3. Gradient-based factor: steeper terrain → deeper river incision
        // Gradient is a smoothly varying field, preserving continuity
        // 基于梯度的因子：陡坡→更深河流切割。梯度是平滑变化的场，保持连续性
        double gradient = fieldSampler.calculateGradient(worldX, worldZ);
        double normalizedGradient = Math.min(1.0, gradient / WorldScapeConstants.RIVER_GRADIENT_REFERENCE);
        double gradientFactor = 1.0 + normalizedGradient * WorldScapeConstants.RIVER_GRADIENT_DEPTH_FACTOR;

        double depth = baseDepth * elevationMultiplier * gradientFactor;
        return Math.max(WorldScapeConstants.RIVER_MIN_DEPTH,
                       Math.min(depth, WorldScapeConstants.RIVER_MAX_DEPTH));
    }
}