package com.worldscape.terrain;

import com.worldscape.terrain.ControlPointManager;
import com.worldscape.terrain.MacroRegionInfo;
import com.worldscape.terrain.MacroVoronoiSystem;
import com.worldscape.terrain.RiverInfo;
import com.worldscape.terrain.RiverNoiseSampler;
import com.worldscape.terrain.TerrainControlPoint;
import com.worldscape.terrain.TerrainType;
import com.worldscape.terrain.WorldScapeConstants;
import com.worldscape.util.WorldScapeUtils;
import java.util.List;
import java.util.Objects;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * @deprecated Dead in production. The real generation path is RegionController → TerrainCalculator.
 * This class is retained for reference only; do not use it for new features.
 * 生产中已废弃。实际生成路径为 RegionController → TerrainCalculator。
 * 此类仅保留作参考，新功能请勿使用。
 */
@Deprecated
public class HeightCalculator {
    private final int seaLevel;
    private final ControlPointManager controlPointManager;
    private final MacroVoronoiSystem macroVoronoiSystem;
    private final NormalNoise n1Noise;
    private final NormalNoise n2Noise;
    private final NormalNoise n3Noise;
    private final RiverNoiseSampler riverSampler;
    private static final boolean DEBUG_DISABLE_CACHE = false;

    public HeightCalculator(long seed, int seaLevel, MacroVoronoiSystem macroVoronoiSystem) {
        // 空值校验：防止 NPE
        // Null check: prevent NPE
        Objects.requireNonNull(macroVoronoiSystem, "macroVoronoiSystem must not be null");
        this.seaLevel = seaLevel;
        this.controlPointManager = new ControlPointManager(seed, seaLevel);
        this.macroVoronoiSystem = macroVoronoiSystem;
        this.n1Noise = this.createNoise(seed, -6);
        this.n2Noise = this.createNoise(seed + 1L, -4);
        this.n3Noise = this.createNoise(seed + 2L, -2);
        this.riverSampler = new RiverNoiseSampler(seed);
    }

    public HeightCalculator(long seed, int seaLevel) {
        this(seed, seaLevel, new MacroVoronoiSystem(seed, seaLevel));
    }

    private NormalNoise createNoise(long seed, int octaves) {
        return NormalNoise.create((RandomSource)RandomSource.create((long)seed), (int)octaves, (double[])new double[]{1.0});
    }

    public boolean isRiver(int x, int z) {
        return this.riverSampler.isRiver(x, z);
    }

    public RiverInfo getRiverInfo(int x, int z) {
        return this.riverSampler.sampleRiverInfo(x, z);
    }

    public double calculateHeight(int x, int z, HeightCache cache) {
        MacroRegionInfo macroInfo = this.macroVoronoiSystem.getRegionInfo(x, z);
        double macroBaseHeight = macroInfo.getBaseHeight();
        double blendWeight = macroInfo.getBlendWeight();
        int elevationTier = macroInfo.getElevationTier();
        HeightCache effectiveCache = cache;
        List<TerrainControlPoint> points = effectiveCache != null ? effectiveCache.controlPoints : this.controlPointManager.getNearbyControlPoints(x, z, 600.0);
        double microHeight = points.isEmpty() ? (double)this.seaLevel : this.calculateMicroHeight(x, z, points, effectiveCache);
        double tierMinHeight = this.getTierMinimumHeight(elevationTier);
        microHeight = Math.max(microHeight, tierMinHeight);
        double tierAdjustment = (double)(elevationTier - 4) * WorldScapeConstants.TIER_BASE_HEIGHT * WorldScapeConstants.TIER_ADJUSTMENT_FACTOR;
        double finalHeight;
        // 一致性修复：添加 macroBaseHeight 以与 RegionController.calculateBlend 保持一致
        // Consistency fix: add macroBaseHeight to match RegionController.calculateBlend
        // RegionController 使用 macroBaseHeight + microHeight + tierAdjustment，
        // 其中 microHeight 是相对于 macroBaseHeight 的偏移量（包含 baseHeightForType），
        // 而非绝对高度。缺少 macroBaseHeight 会导致高 Tier 地形过低、低 Tier 地形过高，
        // 在 Tier 边界处产生地形反转。
        // RegionController uses macroBaseHeight + microHeight + tierAdjustment,
        // where microHeight is a delta from macroBaseHeight (includes baseHeightForType),
        // NOT an absolute height. Missing macroBaseHeight causes high-tier terrain to be
        // too low and low-tier terrain too high, producing terrain inversion at tier boundaries.
        if (blendWeight > WorldScapeConstants.BLEND_WEIGHT_THRESHOLD) {
            finalHeight = macroBaseHeight + microHeight + tierAdjustment;
        } else {
            // ponytail: align with RegionController.calculateBlend — sqrt + tierGapFactor,
            // no hard MAX_MACRO_INFLUENCE cap, no oceanic tier damping.
            // RegionController uses this formula; HeightCalculator had stale smoothstep+cap version.
            double boundaryProximityRaw = 1.0 - Math.abs(blendWeight - 0.5) * 2.0;
            boundaryProximityRaw = Math.max(0.0, Math.min(1.0, boundaryProximityRaw));
            double boundaryProximity = Math.sqrt(boundaryProximityRaw);
            double macroInfluence = boundaryProximity;
            finalHeight = WorldScapeUtils.lerp(macroBaseHeight + microHeight + tierAdjustment, macroBaseHeight, macroInfluence);
        }
        double smoothNoise = this.n3Noise.getValue((double)x / 16.0, (double)z / 16.0, 0.0) * 6.0;
        return finalHeight + smoothNoise;
    }

    private double calculateMicroHeight(int x, int z, List<TerrainControlPoint> points, HeightCache cache) {
        double totalWeight = 0.0;
        double totalHeight = 0.0;
        for (TerrainControlPoint point : points) {
            double distance = point.squaredDistanceTo(x, z);
            double effectiveRadius = point.influenceRadius;
            double sqrtDistance = Math.sqrt(distance);
            double normalizedDistance = Math.min(sqrtDistance / effectiveRadius, 1.0);
            double weight = (1.0 - normalizedDistance) * (1.0 - normalizedDistance);
            if (!(weight > 0.001)) continue;
            // 一致性修复：使用 getBaseHeightForTerrainType 替代 calculateHeight(context)
            // Consistency fix: use getBaseHeightForTerrainType instead of calculateHeight(context)
            // calculateHeight(context) 始终返回 0.0，导致 microHeight 缺少地形类型基础高度，
            // 与 RegionController.calculateBlend 中的 elevationOffset + baseHeightForType 不一致。
            // calculateHeight(context) always returns 0.0, causing microHeight to miss the
            // terrain-type base height, inconsistent with RegionController.calculateBlend's
            // elevationOffset + baseHeightForType.
            double pointHeight = point.elevationOffset + this.getBaseHeightForTerrainType(point.terrainType);
            totalWeight += weight;
            totalHeight += weight * pointHeight;
        }
        return totalWeight > 0.0 ? totalHeight / totalWeight : (double)this.seaLevel;
    }

    private double getTierMinimumHeight(int tier) {
        return MacroVoronoiSystem.getTierMinimumHeight(tier);
    }

    // 地形类型基础高度查找表，与 RegionController.getBaseHeightForTerrainType 保持一致
    // Terrain type base height lookup table, consistent with RegionController.getBaseHeightForTerrainType
    // 这些值是相对于 macroBaseHeight 的偏移量，代表每种地形类型的典型局部高度变化，
    // 而非绝对高度。最终高度 = macroBaseHeight + microHeight(含 baseHeightForType) + tierAdjustment。
    // These values are deltas from macroBaseHeight, representing typical local height variation
    // for each terrain type, NOT absolute heights. Final height = macroBaseHeight + microHeight
    // (includes baseHeightForType) + tierAdjustment.
    private double getBaseHeightForTerrainType(TerrainType type) {
        // Delegates to the single source of truth in TerrainType to avoid duplication.
        // Previously this method contained a 60-line if-else chain identical to
        // RegionController.getBaseHeightForTerrainType — consolidated to prevent drift.
        // 委托给 TerrainType 中的唯一数据源以避免重复。
        // 之前此方法包含与 RegionController.getBaseHeightForTerrainType 相同的 60 行 if-else 链
        // — 已合并以防止不一致。
        return TerrainType.getBaseHeightForType(type);
    }

    public double calculateHeight(int x, int z) {
        return this.calculateHeight(x, z, null);
    }

    // 已移除 applyTerrainIntensity：该方法将高度向海平面插值，与 macroBaseHeight 叠加后
    // 产生错误结果（macroBaseHeight + seaLevel），且 RegionController.calculateBlend 不使用此逻辑。
    // Removed applyTerrainIntensity: it lerped height toward seaLevel, which conflicts with
    // macroBaseHeight addition (producing macroBaseHeight + seaLevel), and RegionController
    // does not use this logic.

    public static class HeightCache {
        public final List<TerrainControlPoint> controlPoints;

        public HeightCache(List<TerrainControlPoint> controlPoints) {
            this.controlPoints = controlPoints;
        }
    }
}

