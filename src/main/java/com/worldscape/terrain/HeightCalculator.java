/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.util.RandomSource
 *  net.minecraft.world.level.levelgen.synth.NormalNoise
 */
package com.worldscape.terrain;

import com.worldscape.terrain.ControlPointManager;
import com.worldscape.terrain.MacroRegionInfo;
import com.worldscape.terrain.MacroVoronoiSystem;
import com.worldscape.terrain.RiverInfo;
import com.worldscape.terrain.RiverNoiseSampler;
import com.worldscape.terrain.TerrainContext;
import com.worldscape.terrain.TerrainControlPoint;
import com.worldscape.terrain.TerrainType;
import com.worldscape.terrain.WorldScapeConstants;
import com.worldscape.util.WorldScapeUtils;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

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
        if (blendWeight > WorldScapeConstants.BLEND_WEIGHT_THRESHOLD) {
            finalHeight = microHeight + tierAdjustment;
        } else {
            double boundaryProximityRaw = 1.0 - Math.abs(blendWeight - 0.5) * 2.0;
            boundaryProximityRaw = Math.max(0.0, Math.min(1.0, boundaryProximityRaw));
            double boundaryProximity = WorldScapeUtils.smoothstep(0.0, 1.0, boundaryProximityRaw);
            double macroInfluence = boundaryProximity * WorldScapeConstants.MAX_MACRO_INFLUENCE;
            if (elevationTier == 0) {
                macroInfluence *= WorldScapeConstants.OCEAN_TIER0_MACRO_DAMPING;
            } else if (elevationTier == 1) {
                macroInfluence *= WorldScapeConstants.OCEAN_TIER1_MACRO_DAMPING;
            }
            finalHeight = WorldScapeUtils.lerp(microHeight + tierAdjustment, macroBaseHeight, macroInfluence);
        }
        double smoothNoise = this.n3Noise.getValue((double)x / 16.0, (double)z / 16.0, 0.0) * 6.0;
        return finalHeight + smoothNoise;
    }

    private double calculateMicroHeight(int x, int z, List<TerrainControlPoint> points, HeightCache cache) {
        double n1 = cache != null ? cache.n1 : this.n1Noise.getValue((double)x / 512.0, (double)z / 512.0, 0.0);
        double n2 = cache != null ? cache.n2 : this.n2Noise.getValue((double)x / 128.0, (double)z / 128.0, 0.0);
        double n3 = cache != null ? cache.n3 : this.n3Noise.getValue((double)x / 32.0, (double)z / 32.0, 0.0);
        double totalWeight = 0.0;
        double totalHeight = 0.0;
        for (TerrainControlPoint point : points) {
            double distance = point.squaredDistanceTo(x, z);
            double effectiveRadius = point.getRadius() * 1.5;
            double sqrtDistance = Math.sqrt(distance);
            double normalizedDistance = Math.min(sqrtDistance / effectiveRadius, 1.0);
            double weight = this.smoothStep(1.0 - normalizedDistance);
            if (!(weight > 0.001)) continue;
            TerrainContext context = new TerrainContext(n1, n2, n3, sqrtDistance, this.controlPointManager.getSeed());
            double pointHeight = point.getElevationOffset() + point.getTerrainType().calculateHeight(context);
            double terrainIntensity = this.smoothStep(1.0 - Math.min(sqrtDistance / point.getRadius(), 1.0));
            pointHeight = this.applyTerrainIntensity(pointHeight, point.getTerrainType(), terrainIntensity);
            totalWeight += weight;
            totalHeight += weight * pointHeight;
        }
        return totalWeight > 0.0 ? totalHeight / totalWeight : (double)this.seaLevel;
    }

    private double getTierMinimumHeight(int tier) {
        return MacroVoronoiSystem.getTierMinimumHeight(tier);
    }

    public double calculateHeight(int x, int z) {
        return this.calculateHeight(x, z, null);
    }

    private double smoothStep(double x) {
        x = WorldScapeUtils.clamp(x, 0.0, 1.0);
        return x * x * (3.0 - 2.0 * x);
    }

    private double applyTerrainIntensity(double height, TerrainType terrainType, double intensity) {
        if (terrainType == TerrainType.HIGH_MOUNTAINS || terrainType == TerrainType.CLIFF) {
            intensity *= 0.7;
        }
        return WorldScapeUtils.lerp(this.seaLevel, height, intensity);
    }

    public static class HeightCache {
        public final List<TerrainControlPoint> controlPoints;
        public final double n1;
        public final double n2;
        public final double n3;

        public HeightCache(List<TerrainControlPoint> controlPoints, double n1, double n2, double n3) {
            this.controlPoints = controlPoints;
            this.n1 = n1;
            this.n2 = n2;
            this.n3 = n3;
        }
    }
}

