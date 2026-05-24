package com.worldscape.terrain;

import com.worldscape.terrain.ControlPointManager;
import com.worldscape.terrain.MacroRegionInfo;
import com.worldscape.terrain.MacroVoronoiSystem;
import com.worldscape.terrain.RiverInfo;
import com.worldscape.terrain.RiverNoiseSampler;
import com.worldscape.terrain.TerrainContext;
import com.worldscape.terrain.TerrainControlPoint;
import com.worldscape.terrain.TerrainType;
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
        double tierAdjustment = (double)(elevationTier - 4) * 8.0 * 0.15;
        double finalHeight;
        if (blendWeight > 0.8) {
            finalHeight = microHeight + tierAdjustment;
        } else {
            double boundaryProximity = 1.0 - Math.abs(blendWeight - 0.5) * 2.0;
            boundaryProximity = Math.max(0.0, Math.min(1.0, boundaryProximity));
            double macroInfluence = boundaryProximity * 0.15;
            if (elevationTier == 0) {
                macroInfluence *= 0.33;
            } else if (elevationTier == 1) {
                macroInfluence *= 0.5;
            }
            finalHeight = WorldScapeUtils.lerp(microHeight + tierAdjustment, macroBaseHeight, macroInfluence);
        }
        double smoothNoise = this.n3Noise.getValue((double)x / 16.0, (double)z / 16.0, 0.0) * 6.0;
        return finalHeight += smoothNoise;
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

    public double[] calculateHeightMap(int startX, int startZ, int size) {
        double[] heightMap = new double[size * size];
        RiverInfo[] riverMap = new RiverInfo[size * size];
        int regionSize = 512;
        int lastRegionX = Integer.MIN_VALUE;
        int lastRegionZ = Integer.MIN_VALUE;
        List<TerrainControlPoint> cachedPoints = null;
        double lastN1 = 0.0;
        double lastN2 = 0.0;
        double lastN3 = 0.0;
        HeightCache cache = null;
        for (int z = 0; z < size; ++z) {
            for (int x = 0; x < size; ++x) {
                boolean cacheValid;
                int worldX = startX + x;
                int worldZ = startZ + z;
                int regionX = Math.floorDiv(worldX, regionSize);
                int regionZ = Math.floorDiv(worldZ, regionSize);
                boolean bl = cacheValid = regionX == lastRegionX && regionZ == lastRegionZ;
                if (!cacheValid) {
                    cachedPoints = this.controlPointManager.getNearbyControlPoints(worldX, worldZ, 600.0);
                    lastN1 = this.n1Noise.getValue((double)worldX / 512.0, (double)worldZ / 512.0, 0.0);
                    lastN2 = this.n2Noise.getValue((double)worldX / 128.0, (double)worldZ / 128.0, 0.0);
                    lastN3 = this.n3Noise.getValue((double)worldX / 32.0, (double)worldZ / 32.0, 0.0);
                    lastRegionX = regionX;
                    lastRegionZ = regionZ;
                    cache = new HeightCache(cachedPoints, lastN1, lastN2, lastN3);
                }
                int idx = z * size + x;
                riverMap[idx] = this.riverSampler.sampleRiverInfo(worldX, worldZ);
                heightMap[idx] = this.calculateHeight(worldX, worldZ, cache);
            }
        }
        this.applyRiverCarving(heightMap, riverMap, startX, startZ, size);
        return this.applyTerrainAwareSmoothing(heightMap, startX, startZ, size);
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

    private void applyRiverCarving(double[] heightMap, RiverInfo[] riverMap, int startX, int startZ, int size) {
        for (int z = 0; z < size; ++z) {
            for (int x = 0; x < size; ++x) {
                int idx = z * size + x;
                RiverInfo river = riverMap[idx];
                if (river == null || !river.isRiver) continue;
                double erosionIntensity = river.getErosionIntensity();
                double carveDepth = river.depth * erosionIntensity;
                double maxCarve = Math.max(0.0, heightMap[idx] - (double)this.seaLevel + 5.0) * 0.5;
                carveDepth = Math.min(carveDepth, maxCarve);
                int n = idx;
                heightMap[n] = heightMap[n] - carveDepth;
            }
        }
    }

    private double[] applyTerrainAwareSmoothing(double[] heightMap, int startX, int startZ, int size) {
        double[] gradientMap = this.calculateGradientMagnitude(heightMap, size);
        TerrainType[] terrainTypeMap = this.classifyTerrainTypes(heightMap, gradientMap, startX, startZ, size);
        return this.applyTypeBasedSmoothing(heightMap, gradientMap, terrainTypeMap, size);
    }

    private TerrainType[] classifyTerrainTypes(double[] heightMap, double[] gradientMap, int startX, int startZ, int size) {
        TerrainType[] types = new TerrainType[size * size];
        for (int z = 0; z < size; ++z) {
            for (int x = 0; x < size; ++x) {
                int idx = z * size + x;
                int worldX = startX + x;
                int worldZ = startZ + z;
                MacroRegionInfo macroInfo = this.macroVoronoiSystem.getRegionInfo(worldX, worldZ);
                int tier = macroInfo.getElevationTier();
                double height = heightMap[idx];
                double gradient = gradientMap[idx];
                TerrainType type = gradient > 25.0 ? (height > 120.0 ? TerrainType.HIGH_MOUNTAINS : TerrainType.CLIFF) : (gradient > 15.0 ? (height > 150.0 ? TerrainType.HIGH_MOUNTAINS : (height > 100.0 ? TerrainType.RIDGE : (height < 25.0 ? TerrainType.CANYON : TerrainType.HILLS))) : (gradient > 8.0 ? (height > 120.0 ? TerrainType.RIDGE : (height > 70.0 ? TerrainType.PLATEAU : TerrainType.HILLS)) : (gradient > 3.0 ? (height > 80.0 ? TerrainType.DOME : TerrainType.HILLS) : (height > 80.0 ? TerrainType.DOME : (height > 25.0 ? TerrainType.PLAINS : (height > 15.0 ? TerrainType.BEACH : TerrainType.PLAINS))))));
                types[idx] = type;
            }
        }
        return types;
    }

    private double[] applyTypeBasedSmoothing(double[] heightMap, double[] gradientMap, TerrainType[] terrainTypeMap, int size) {
        double[] result = new double[size * size];
        for (int z = 0; z < size; ++z) {
            for (int x = 0; x < size; ++x) {
                int idx = z * size + x;
                TerrainType type = terrainTypeMap[idx];
                double gradient = gradientMap[idx];
                SmoothingParams params = this.getSmoothingParams(type, gradient);
                result[idx] = params.blurRadius <= 0 ? heightMap[idx] : (params.anisotropic ? this.applyAnisotropicBlur(heightMap, x, z, size, params) : this.applyRadialBlur(heightMap, x, z, size, params));
            }
        }
        return result;
    }

    private SmoothingParams getSmoothingParams(TerrainType type, double gradient) {
        return switch (type) {
            case TerrainType.HIGH_MOUNTAINS -> new SmoothingParams(4, 3.0, 1.6, false, 0.35);
            case TerrainType.CLIFF -> new SmoothingParams(3, 2.5, 1.4, false, 0.4);
            case TerrainType.RIDGE, TerrainType.PEAK, TerrainType.HORN -> new SmoothingParams(5, 3.5, 1.3, false, 0.3);
            case TerrainType.PLATEAU, TerrainType.DOME -> new SmoothingParams(5, 3.0, 1.3, true, 0.3);
            case TerrainType.HILLS -> new SmoothingParams(6, 4.0, 1.2, false, 0.25);
            case TerrainType.PLAINS -> new SmoothingParams(7, 4.5, 1.15, false, 0.2);
            case TerrainType.BEACH -> new SmoothingParams(6, 4.0, 1.2, true, 0.2);
            case TerrainType.CANYON -> new SmoothingParams(4, 3.0, 1.3, true, 0.35);
            case TerrainType.DUNE -> new SmoothingParams(5, 3.5, 1.25, true, 0.25);
            default -> new SmoothingParams(6, 4.0, 1.2, false, 0.25);
        };
    }

    private double applyRadialBlur(double[] heightMap, int x, int z, int size, SmoothingParams params) {
        double centerHeight = heightMap[z * size + x];
        double sum = 0.0;
        double totalWeight = 0.0;
        for (int dz = -params.blurRadius; dz <= params.blurRadius; ++dz) {
            for (int dx = -params.blurRadius; dx <= params.blurRadius; ++dx) {
                int nx = x + dx;
                int nz = z + dz;
                if (nx < 0 || nx >= size || nz < 0 || nz >= size) continue;
                double neighborHeight = heightMap[nz * size + nx];
                double heightDiff = Math.abs(neighborHeight - centerHeight);
                double edgeFactor = Math.exp(-heightDiff / (params.edgeWeight * 100.0));
                double distSq = dx * dx + dz * dz;
                double gaussianWeight = Math.exp(-distSq / (2.0 * params.sigma * params.sigma));
                double weight = gaussianWeight * edgeFactor;
                if (dx == 0 && dz == 0) {
                    weight *= params.centerWeight;
                }
                sum += neighborHeight * weight;
                totalWeight += weight;
            }
        }
        return totalWeight > 0.0 ? sum / totalWeight : heightMap[z * size + x];
    }

    private double applyAnisotropicBlur(double[] heightMap, int x, int z, int size, SmoothingParams params) {
        double centerHeight = heightMap[z * size + x];
        double sum = 0.0;
        double totalWeight = 0.0;
        int xBlur = params.blurRadius;
        int zBlur = params.blurRadius;
        if (params.edgeWeight > 0.6) {
            xBlur = params.blurRadius + 1;
            zBlur = 1;
        }
        for (int dz = -zBlur; dz <= zBlur; ++dz) {
            for (int dx = -xBlur; dx <= xBlur; ++dx) {
                int nx = x + dx;
                int nz = z + dz;
                if (nx < 0 || nx >= size || nz < 0 || nz >= size) continue;
                double neighborHeight = heightMap[nz * size + nx];
                double heightDiff = Math.abs(neighborHeight - centerHeight);
                double edgeFactor = Math.exp(-heightDiff / (params.edgeWeight * 60.0));
                double distSq = dx * dx + dz * dz;
                double gaussianWeight = Math.exp(-distSq / (2.0 * params.sigma * params.sigma));
                double weight = gaussianWeight * edgeFactor;
                if (dx == 0 && dz == 0) {
                    weight *= params.centerWeight;
                }
                sum += neighborHeight * weight;
                totalWeight += weight;
            }
        }
        return totalWeight > 0.0 ? sum / totalWeight : centerHeight;
    }

    private double[] calculateGradientMagnitude(double[] heightMap, int size) {
        double[] gradient = new double[size * size];
        for (int z = 1; z < size - 1; ++z) {
            for (int x = 1; x < size - 1; ++x) {
                double gx = -heightMap[(z - 1) * size + (x - 1)] + heightMap[(z - 1) * size + (x + 1)] - 2.0 * heightMap[z * size + (x - 1)] + 2.0 * heightMap[z * size + (x + 1)] - heightMap[(z + 1) * size + (x - 1)] + heightMap[(z + 1) * size + (x + 1)];
                double gz = -heightMap[(z - 1) * size + (x - 1)] - 2.0 * heightMap[(z - 1) * size + x] - heightMap[(z - 1) * size + (x + 1)] + heightMap[(z + 1) * size + (x - 1)] + 2.0 * heightMap[(z + 1) * size + x] + heightMap[(z + 1) * size + (x + 1)];
                gradient[z * size + x] = Math.sqrt(gx * gx + gz * gz);
            }
        }
        for (int i = 0; i < size; ++i) {
            gradient[i] = gradient[size + Math.max(1, Math.min(i, size - 2))];
            gradient[(size - 1) * size + i] = gradient[(size - 2) * size + Math.max(1, Math.min(i, size - 2))];
            gradient[i * size] = gradient[i * size + Math.max(1, Math.min(1, size - 2))];
            gradient[i * size + size - 1] = gradient[i * size + Math.max(1, Math.min(size - 2, size - 2))];
        }
        return gradient;
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

    private static class SmoothingParams {
        final int blurRadius;
        final double sigma;
        final double centerWeight;
        final boolean anisotropic;
        final double edgeWeight;

        SmoothingParams(int blurRadius, double sigma, double centerWeight, boolean anisotropic, double edgeWeight) {
            this.blurRadius = blurRadius;
            this.sigma = sigma;
            this.centerWeight = centerWeight;
            this.anisotropic = anisotropic;
            this.edgeWeight = edgeWeight;
        }
    }
}

