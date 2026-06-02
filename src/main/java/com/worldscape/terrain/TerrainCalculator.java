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
               Math.min(WorldScapeConstants.MAX_TERRAIN_HEIGHT, result));
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

    // @AESTHETIC: Compute erosion multiplier based on elevation tier.
    // Higher tier = more dramatic terrain = deeper erosion gullies.
    // 基于海拔等级的侵蚀乘数：高等级→更深的侵蚀沟壑。
    public static double getErosionMultiplierForTier(int elevationTier) {
        if (elevationTier >= 5) return WorldScapeConstants.EROSION_MULTIPLIER_MOUNTAIN;
        if (elevationTier <= 2) return WorldScapeConstants.EROSION_MULTIPLIER_PLAIN;
        return 1.0;
    }

    // @AESTHETIC: Compute river depth multiplier based on elevation tier.
    // Mountain rivers cut deeper; plain rivers spread wider and shallower.
    // 基于海拔等级的河流深度乘数：山区河流切割更深，平原河流更浅更宽。
    public static double getRiverDepthMultiplierForTier(int elevationTier) {
        if (elevationTier >= 4) return WorldScapeConstants.RIVER_DEPTH_MULTIPLIER_MOUNTAIN;
        if (elevationTier <= 2) return WorldScapeConstants.RIVER_DEPTH_MULTIPLIER_PLAIN;
        return 1.0;
    }

    public static int calculateErodedHeight(int worldX, int worldZ, double continuousHeight, boolean isRiver, double riverDepth, int seaLevel, NoiseSet noiseSet, RegionController.TerrainBlendResult blend) {
        double erosionIntensity = TerrainCalculator.getRiverErosionIntensity(worldX, worldZ, noiseSet, continuousHeight, seaLevel, blend);
        return TerrainCalculator.calculateErodedHeight(worldX, worldZ, continuousHeight, isRiver, riverDepth, seaLevel, noiseSet, blend, erosionIntensity);
    }

    public static int calculateErodedHeight(int worldX, int worldZ, double continuousHeight, boolean isRiver, double riverDepth, int seaLevel, NoiseSet noiseSet, RegionController.TerrainBlendResult blend, double erosionIntensity) {
        double alluvialFactor = TerrainCalculator.getAlluvialFactor(worldX, worldZ, noiseSet, continuousHeight, seaLevel);
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
}