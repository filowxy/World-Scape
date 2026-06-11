/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.util.RandomSource
 *  net.minecraft.world.level.levelgen.synth.NormalNoise
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.worldscape.terrain;

import com.worldscape.terrain.TerrainType;
import com.worldscape.util.SeedDeriver;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TerrainFieldSampler {
    private static final Logger LOGGER = LoggerFactory.getLogger(TerrainFieldSampler.class);
    private final NormalNoise energyMain;
    private final NormalNoise energyDetail;
    private final NormalNoise moisture;
    private final NormalNoise[] fbmOctaves = new NormalNoise[6];
    private final NormalNoise domainAngle;
    private final NormalNoise domainOffsetX;
    private final NormalNoise domainOffsetZ;
    private static final double ENERGY_MAIN_SCALE = 2.44140625E-4;
    private static final double ENERGY_DETAIL_SCALE = 9.765625E-4;
    private static final double MOISTURE_SCALE = 4.8828125E-4;
    private static final double ENERGY_DETAIL_WEIGHT = 0.3;
    private final long worldSeed;
    private static final List<Double> TIER_THRESHOLDS = List.of(Double.valueOf(-1.405), Double.valueOf(-0.674), Double.valueOf(0.0), Double.valueOf(0.842), Double.valueOf(1.645));
    public static final int NO_MACRO_TIER_CONSTRAINT = -1;
    private static final double ENERGY_TO_OFFSET_SCALE = 50.0;
    // Thread-safe noise result caches to avoid redundant fBm/turbulence/domain_rotated recomputation.
    // Uses ConcurrentHashMap with computeIfAbsent for lock-free reads. Entries bounded by
    // periodic clear in getOrCreate() to prevent unbounded growth on long-running servers.
    // Maximum ~50K entries per sampler before eviction (~400 KB memory per sampler).
    private static final int MAX_CACHE_SIZE = 10000;
    private final ConcurrentHashMap<NoiseCacheKey, Double> fbmCache = new ConcurrentHashMap<>(1024);
    private final ConcurrentHashMap<NoiseCacheKey, Double> turbulenceCache = new ConcurrentHashMap<>(256);
    private final ConcurrentHashMap<NoiseCacheKey, Double> domainRotatedCache = new ConcurrentHashMap<>(256);

    // Cache hit/miss counters for runtime monitoring.
    // Reset via clearNoiseCaches(). Access via getCacheHits() / getCacheMisses().
    // 缓存命中/未命中计数器，用于运行时监控。通过 clearNoiseCaches() 重置，
    // 通过 getCacheHits() / getCacheMisses() 访问。
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    // LRU 缓存，最大容量 8，避免多世界长期运行导致内存泄漏
    // LRU cache with max capacity 8, preventing memory leak on long-running multi-world servers
    private static final Map<Long, TerrainFieldSampler> instances = Collections.synchronizedMap(
        new LinkedHashMap<Long, TerrainFieldSampler>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, TerrainFieldSampler> eldest) {
                return this.size() > 8;
            }
        }
    );

    private TerrainFieldSampler(long worldSeed) {
        this.worldSeed = worldSeed;
        long energyMainSeed = SeedDeriver.deriveSeed(worldSeed, 832466842634L);
        long energyDetailSeed = SeedDeriver.deriveSeed(worldSeed, 979051293805L);
        long moistureSeed = SeedDeriver.deriveSeed(worldSeed, 905767551413L);
        this.energyMain = NormalNoise.create((RandomSource)RandomSource.create((long)energyMainSeed), (int)-8, (double[])new double[]{1.5});
        this.energyDetail = NormalNoise.create((RandomSource)RandomSource.create((long)energyDetailSeed), (int)-6, (double[])new double[]{1.0});
        this.moisture = NormalNoise.create((RandomSource)RandomSource.create((long)moistureSeed), (int)-7, (double[])new double[]{1.2});
        long[] fbmSalts = new long[]{263889798480852L, 263894379779301L, 263898961077750L, 263903542375943L, 263908123608856L, 263912688130089L};
        for (int i = 0; i < 6; ++i) {
            long salt = SeedDeriver.deriveSeed(worldSeed, fbmSalts[i]);
            this.fbmOctaves[i] = NormalNoise.create((RandomSource)RandomSource.create((long)salt), (int)(-(8 + i)), (double[])new double[]{1.0});
        }
        long angleSeed = SeedDeriver.deriveSeed(worldSeed, 263913260792395L);
        long offsetXSeed = SeedDeriver.deriveSeed(worldSeed, 263917842090844L);
        long offsetZSeed = SeedDeriver.deriveSeed(worldSeed, 263922423389293L);
        this.domainAngle = NormalNoise.create((RandomSource)RandomSource.create((long)angleSeed), (int)-9, (double[])new double[]{1.0});
        this.domainOffsetX = NormalNoise.create((RandomSource)RandomSource.create((long)offsetXSeed), (int)-8, (double[])new double[]{1.0});
        this.domainOffsetZ = NormalNoise.create((RandomSource)RandomSource.create((long)offsetZSeed), (int)-8, (double[])new double[]{1.0});
    }

    /*
     * 按 worldSeed 分实例缓存，避免旧种子实例被覆盖导致跨世界线程安全问题。
     * Cache instances by worldSeed to prevent cross-world thread safety issues
     * caused by overwriting the previous instance.
     */
    public static TerrainFieldSampler getOrCreate(long worldSeed) {
        synchronized (instances) {
            TerrainFieldSampler existing = instances.get(worldSeed);
            if (existing != null) {
                // Clear caches of existing instance to prevent stale entries
                // from accumulating across multiple terrain generation passes
                existing.clearNoiseCaches();
                return existing;
            }
            TerrainFieldSampler newSampler = new TerrainFieldSampler(worldSeed);
            instances.put(worldSeed, newSampler);
            return newSampler;
        }
    }

    public double sampleEnergy(int x, int z) {
        double cx = (double)x + 0.5;
        double cz = (double)z + 0.5;
        double main = this.energyMain.getValue(cx * 2.44140625E-4, cz * 2.44140625E-4, 0.0);
        double detail = this.energyDetail.getValue(cx * 9.765625E-4, cz * 9.765625E-4, 0.0);
        return main * 0.7 + detail * 0.3;
    }

    public double sampleMoisture(int x, int z) {
        double cx = (double)x + 0.5;
        double cz = (double)z + 0.5;
        return this.moisture.getValue(cx * 4.8828125E-4, cz * 4.8828125E-4, 0.0);
    }

    public int energyToTier(double energy, int macroTierConstraint) {
        if (Double.isNaN(energy) || Double.isInfinite(energy)) {
            energy = 0.0;
        }
        energy = Math.max(-2.0, Math.min(2.0, energy));
        int tier = 0;
        for (int i = 0; i < TIER_THRESHOLDS.size(); ++i) {
            if (!(energy >= TIER_THRESHOLDS.get(i))) continue;
            tier = i + 1;
        }
        if (macroTierConstraint != -1) {
            int clampedConstraint = Math.max(0, Math.min(5, macroTierConstraint));
            int minTier = Math.max(0, clampedConstraint - 1);
            int maxTier = Math.min(5, clampedConstraint + 1);
            tier = Math.max(minTier, Math.min(maxTier, tier));
        }
        return tier;
    }

    /**
     * Select terrain type by tier and moisture, with deterministic position-based jitter.
     * The jitter (±0.05 on normalized [0,1] moisture) allows ~5-10% of points near
     * interval boundaries to cross into adjacent intervals, producing 2-3 terrain types
     * per Voronoi cell instead of 100% single-type dominance.
     */
    public TerrainType selectTypeByMoisture(int tier, double moisture, int worldX, int worldZ) {
        double normalizedMoisture = (moisture + 1.0) * 0.5;
        // Deterministic position-based jitter: small random offset based on world coordinates
        long positionSeed = SeedDeriver.deriveSeed(this.worldSeed, worldX * 31L + worldZ * 17L);
        RandomSource positionRandom = RandomSource.create(positionSeed);
        double jitter = (positionRandom.nextDouble() - 0.5) * 0.10;
        normalizedMoisture += jitter;
        normalizedMoisture = Math.max(0.0, Math.min(1.0, normalizedMoisture));
        if (tier < 0 || tier > 5) {
            LOGGER.warn("[World Scape] Invalid tier {} passed to selectTypeByMoisture, falling back to PLAINS", (Object)tier);
            return TerrainType.PLAINS;
        }
        return switch (tier) {
            case 0 -> this.selectTier0Type(normalizedMoisture);
            case 1 -> this.selectTier1Type(normalizedMoisture);
            case 2 -> this.selectTier2Type(normalizedMoisture);
            case 3 -> this.selectTier3Type(normalizedMoisture);
            case 4 -> this.selectTier4Type(normalizedMoisture);
            case 5 -> this.selectTier5Type(normalizedMoisture);
            default -> TerrainType.PLAINS;
        };
    }

    /**
     * Backward-compatible overload without jitter coordinates.
     * Delegates to the 4-parameter version with (0, 0) as default coordinates.
     */
    public TerrainType selectTypeByMoisture(int tier, double moisture) {
        return selectTypeByMoisture(tier, moisture, 0, 0);
    }

    public double calculateContinuousOffset(double energy, TerrainType type) {
        double baseOffset = energy * 50.0;
        double typeModifier = TerrainFieldSampler.getTypeModifier(type);
        return baseOffset + typeModifier;
    }

    private static double getTypeModifier(TerrainType type) {
        if (type == TerrainType.TRENCH) {
            return -90.0;
        } else if (type == TerrainType.SEA_PLATEAU) {
            return -70.0;
        } else if (type == TerrainType.DELTA) {
            return -45.0;
        } else if (type == TerrainType.BEACH) {
            return -15.0;
        } else if (type == TerrainType.SALT_FLAT) {
            return -5.0;
        } else if (type == TerrainType.FLOODPLAIN) {
            return -10.0;
        } else if (type == TerrainType.DUNE) {
            return 0.0;
        } else if (type == TerrainType.SEA_CLIFF) {
            return 5.0;
        } else if (type == TerrainType.FJORD) {
            return -20.0;
        } else if (type == TerrainType.PLAINS) {
            return 0.0;
        } else if (type == TerrainType.GOBI) {
            return 5.0;
        } else if (type == TerrainType.YARDANG) {
            return 10.0;
        } else if (type == TerrainType.BASIN) {
            return -15.0;
        } else if (type == TerrainType.SINKHOLE) {
            return -20.0;
        } else if (type == TerrainType.PEAK_FOREST) {
            return 25.0;
        } else if (type == TerrainType.HILLS) {
            return 20.0;
        } else if (type == TerrainType.CLIFF) {
            return 45.0;
        } else if (type == TerrainType.PLATEAU) {
            return 50.0;
        } else if (type == TerrainType.DOME) {
            return 40.0;
        } else if (type == TerrainType.VALLEY) {
            return 25.0;
        } else if (type == TerrainType.CANYON) {
            return 15.0;
        } else if (type == TerrainType.ALLUVIAL_FAN) {
            return 30.0;
        } else if (type == TerrainType.CIRQUE) {
            return 35.0;
        } else if (type == TerrainType.GLACIAL_VALLEY) {
            return 20.0;
        } else if (type == TerrainType.HIGH_MOUNTAINS) {
            return 80.0;
        } else if (type == TerrainType.RIDGE) {
            return 70.0;
        } else if (type == TerrainType.PEAK) {
            return 75.0;
        } else if (type == TerrainType.HORN) {
            return 85.0;
        } else if (type == TerrainType.ICE_SHEET) {
            return 65.0;
        }
        return 0.0;
    }

    private TerrainType selectTier0Type(double m) {
        if (m < 0.6) {
            return TerrainType.TRENCH;
        }
        return TerrainType.SEA_PLATEAU;
    }

    private TerrainType selectTier1Type(double m) {
        if (m < 0.6) {
            return TerrainType.SEA_PLATEAU;
        }
        return TerrainType.DELTA;
    }

    private TerrainType selectTier2Type(double m) {
        if (m < 0.12) {
            return TerrainType.SALT_FLAT;
        }
        if (m < 0.25) {
            return TerrainType.DUNE;
        }
        if (m < 0.6) {
            return TerrainType.BEACH;
        }
        if (m < 0.8) {
            return TerrainType.DELTA;
        }
        return TerrainType.FLOODPLAIN;
    }

    // @AESTHETIC: T3 expanded with ALLUVIAL_FAN and VALLEY from T4, plus GOBI.
    // These low-altitude types (typeModifier 5-30) fit better at T3 alongside PLAINS/HILLS.
    // T3 扩展了从 T4 迁移来的 ALLUVIAL_FAN、VALLEY 和 GOBI。
    // 这些低海拔类型（typeModifier 5-30）更适合与 PLAINS/HILLS 一起在 T3。
    private TerrainType selectTier3Type(double m) {
        if (m < 0.06) {
            return TerrainType.YARDANG;
        }
        if (m < 0.12) {
            return TerrainType.GOBI;
        }
        if (m < 0.20) {
            return TerrainType.DUNE;
        }
        if (m < 0.45) {
            return TerrainType.PLAINS;
        }
        if (m < 0.55) {
            return TerrainType.FLOODPLAIN;
        }
        if (m < 0.70) {
            return TerrainType.HILLS;
        }
        if (m < 0.80) {
            return TerrainType.ALLUVIAL_FAN;
        }
        if (m < 0.84) {
            return TerrainType.BASIN;
        }
        if (m < 0.87) {
            return TerrainType.SINKHOLE;
        }
        if (m < 0.92) {
            return TerrainType.VALLEY;
        }
        return TerrainType.PEAK_FOREST;
    }

    // @AESTHETIC: T4 now contains only high-altitude-capable types (typeModifier 15-50).
    // GOBI, ALLUVIAL_FAN, VALLEY moved to T3 where they fit better with PLAINS/HILLS.
    // Fewer types → larger moisture ranges → higher probability of CLIFF/PLATEAU/CIRQUE.
    // T4 现在仅包含高海拔能力类型（typeModifier 15-50），低海拔类型移至 T3。
    // 类型更少 → 湿度区间更大 → CLIFF/PLATEAU/CIRQUE 出现概率更高。
    private TerrainType selectTier4Type(double m) {
        if (m < 0.15) {
            return TerrainType.CANYON;
        }
        if (m < 0.35) {
            return TerrainType.HILLS;
        }
        if (m < 0.50) {
            return TerrainType.GLACIAL_VALLEY;
        }
        if (m < 0.65) {
            return TerrainType.CLIFF;
        }
        if (m < 0.80) {
            return TerrainType.CIRQUE;
        }
        return TerrainType.PLATEAU;
    }

    private TerrainType selectTier5Type(double m) {
        if (m < 0.25) {
            return TerrainType.HIGH_MOUNTAINS;
        }
        if (m < 0.4) {
            return TerrainType.CLIFF;
        }
        if (m < 0.53) {
            return TerrainType.RIDGE;
        }
        if (m < 0.68) {
            return TerrainType.PLATEAU;
        }
        if (m < 0.78) {
            return TerrainType.PEAK;
        }
        if (m < 0.85) {
            return TerrainType.CIRQUE;
        }
        if (m < 0.93) {
            return TerrainType.HORN;
        }
        return TerrainType.ICE_SHEET;
    }

    public double sampleFbm(int x, int z) {
        double cx = (double)x + 0.5;
        double cz = (double)z + 0.5;
        double value = 0.0;
        double amplitude = 1.0;
        double frequency = 1.0;
        double maxValue = 0.0;
        for (int i = 0; i < 6; ++i) {
            value += this.fbmOctaves[i].getValue(cx * frequency, cz * frequency, 0.0) * amplitude;
            maxValue += amplitude;
            frequency *= 2.0;
            amplitude *= 0.5;
        }
        return value / maxValue;
    }

    /**
     * Cached variant of sampleFbm — avoids recomputing the 6-octave fBm loop
     * for coordinates already sampled in the current terrain generation pass.
     * Uses ConcurrentHashMap.computeIfAbsent for thread-safe, lock-free reads.
     *
     * @param x       world X coordinate
     * @param z       world Z coordinate
     * @param octaves number of fBm octaves (1-6)
     * @param gain    amplitude decay factor per octave
     * @return noise value in [-1, 1]
     */
    public double sampleFbmCached(int x, int z, int octaves, double gain) {
        // Encode double gain into int with 3 decimal precision for cache key
        int gainInt = (int) Math.round(gain * 1000.0);
        NoiseCacheKey key = new NoiseCacheKey(x, z, octaves, gainInt);
        Double cached = fbmCache.get(key);
        if (cached != null) {
            cacheHits.incrementAndGet();
            return cached;
        }
        cacheMisses.incrementAndGet();
        return fbmCache.computeIfAbsent(key, k -> sampleFbm(k.x(), k.z(), k.param1(), gain));
    }

    /**
     * Cached variant of sampleTurbulence — avoids recomputing turbulence noise
     * for coordinates already sampled.
     *
     * @param x        world X coordinate
     * @param z        world Z coordinate
     * @param strength turbulence strength multiplier
     * @return turbulence value in [0, 1]
     */
    public double sampleTurbulenceCached(int x, int z, double strength) {
        int strengthInt = (int) Math.round(strength * 1000.0);
        NoiseCacheKey key = new NoiseCacheKey(x, z, strengthInt, 0);
        Double cached = turbulenceCache.get(key);
        if (cached != null) {
            cacheHits.incrementAndGet();
            return cached;
        }
        cacheMisses.incrementAndGet();
        return turbulenceCache.computeIfAbsent(key, k -> sampleTurbulence(k.x(), k.z(), strength));
    }

    /**
     * Cached variant of sampleDomainRotated — avoids recomputing domain rotation noise
     * for coordinates already sampled.
     *
     * @param x            world X coordinate
     * @param z            world Z coordinate
     * @param warpStrength domain warp strength multiplier
     * @return noise value in [-1, 1]
     */
    public double sampleDomainRotatedCached(int x, int z, double warpStrength) {
        int warpInt = (int) Math.round(warpStrength * 1000.0);
        NoiseCacheKey key = new NoiseCacheKey(x, z, warpInt, 0);
        Double cached = domainRotatedCache.get(key);
        if (cached != null) {
            cacheHits.incrementAndGet();
            return cached;
        }
        cacheMisses.incrementAndGet();
        return domainRotatedCache.computeIfAbsent(key, k -> sampleDomainRotated(k.x(), k.z(), warpStrength));
    }

    /**
     * Clear all noise caches. Called at the end of each terrain generation pass
     * to prevent stale entries and unbounded memory growth.
     * Public for testing and for external cache lifecycle management.
     */
    public void clearNoiseCaches() {
        fbmCache.clear();
        turbulenceCache.clear();
        domainRotatedCache.clear();
        cacheHits.set(0);
        cacheMisses.set(0);
    }

    /**
     * Get the total number of cache hits across all three noise caches.
     * Useful for monitoring cache efficiency at runtime.
     * Reset to 0 by clearNoiseCaches().
     *
     * @return total cache hit count since last reset
     */
    public long getCacheHits() {
        return cacheHits.get();
    }

    /**
     * Get the total number of cache misses across all three noise caches.
     * Useful for monitoring cache efficiency at runtime.
     * Reset to 0 by clearNoiseCaches().
     *
     * @return total cache miss count since last reset
     */
    public long getCacheMisses() {
        return cacheMisses.get();
    }

    /**
     * Get the current cache hit rate as a ratio in [0, 1].
     * Returns 1.0 if no requests have been made (avoids division by zero).
     *
     * @return hit rate = hits / (hits + misses), or 1.0 if no requests
     */
    public double getCacheHitRate() {
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long total = hits + misses;
        return total == 0 ? 1.0 : (double) hits / total;
    }

    public double sampleDomainRotated(int x, int z, double warpStrength) {
        double cx = (double)x + 0.5;
        double cz = (double)z + 0.5;
        double angleNoise = this.domainAngle.getValue(cx * 1.220703125E-4, cz * 1.220703125E-4, 0.0);
        double angle = angleNoise * Math.PI * 2.0;
        double cosA = Math.cos(angle);
        double sinA = Math.sin(angle);
        double xr = cx * cosA - cz * sinA;
        double zr = cx * sinA + cz * cosA;
        double offsetX = this.domainOffsetX.getValue(xr * 2.44140625E-4, zr * 2.44140625E-4, 0.0) * warpStrength * 512.0;
        double offsetZ = this.domainOffsetZ.getValue(xr * 2.44140625E-4, zr * 2.44140625E-4, 0.0) * warpStrength * 512.0;
        return this.energyMain.getValue((cx + offsetX) * 2.44140625E-4, (cz + offsetZ) * 2.44140625E-4, 0.0);
    }

    public double sampleTurbulence(int x, int z, double strength) {
        double cx = (double)x + 0.5;
        double cz = (double)z + 0.5;
        double raw = this.energyDetail.getValue(cx * 9.765625E-4, cz * 9.765625E-4, 0.0);
        double turbulence = Math.abs(raw * 2.0 - 1.0);
        return Math.min(1.0, turbulence * strength);
    }

    public double sampleEnergyStretched(int x, int z) {
        double cx = (double)x + 0.5;
        double cz = (double)z + 0.5;
        double primaryGradX = Math.cos((double)x * 0.007 + (double)z * 0.004) * 0.007;
        double primaryGradZ = Math.cos((double)x * 0.007 + (double)z * 0.004) * 0.004;
        double ridgeAngle = Math.atan2(primaryGradZ, primaryGradX);
        double angleNoise = this.domainAngle.getValue(cx * 1.220703125E-4, cz * 1.220703125E-4, 0.0);
        double cosA = Math.cos(ridgeAngle += angleNoise * Math.PI * 0.5);
        double sinA = Math.sin(ridgeAngle);
        double along = cx * cosA + cz * sinA;
        double across = -cx * sinA + cz * cosA;
        double stretchedX = (along *= 1.5) * cosA - (across *= 0.7) * sinA;
        double stretchedZ = along * sinA + across * cosA;
        double main = this.energyMain.getValue(stretchedX * 2.44140625E-4, stretchedZ * 2.44140625E-4, 0.0);
        double detail = this.energyDetail.getValue(stretchedX * 9.765625E-4, stretchedZ * 9.765625E-4, 0.0);
        return main * 0.7 + detail * 0.3;
    }

    public double[] getEnergyStretchedCoords(int x, int z) {
        double cx = (double)x + 0.5;
        double cz = (double)z + 0.5;
        double primaryGradX = Math.cos((double)x * 0.007 + (double)z * 0.004) * 0.007;
        double primaryGradZ = Math.cos((double)x * 0.007 + (double)z * 0.004) * 0.004;
        double ridgeAngle = Math.atan2(primaryGradZ, primaryGradX);
        double angleNoise = this.domainAngle.getValue(cx * 1.220703125E-4, cz * 1.220703125E-4, 0.0);
        ridgeAngle += angleNoise * Math.PI * 0.5;
        double cosA = Math.cos(ridgeAngle);
        double sinA = Math.sin(ridgeAngle);
        double along = cx * cosA + cz * sinA;
        double across = -cx * sinA + cz * cosA;
        along *= 1.5;
        across *= 0.7;
        double tx = along * cosA - across * sinA;
        double tz = along * sinA + across * cosA;
        return new double[]{tx, tz};
    }

    public static double sigmoid(double t) {
        if (t > 10.0) {
            return 1.0;
        }
        if (t < -10.0) {
            return 0.0;
        }
        return 1.0 / (1.0 + Math.exp(-t));
    }

    public static double tanhScaled(double t, double steepness) {
        return Math.tanh(t * steepness);
    }

    public static double gaussian(double x, double z, double sigma) {
        if (sigma <= 0.0) {
            return 1.0;
        }
        double sigmaSq2 = 2.0 * sigma * sigma;
        return Math.exp(-(x * x + z * z) / sigmaSq2);
    }

    public double sampleFbm(int x, int z, int octaves, double gain) {
        double cx = (double)x + 0.5;
        double cz = (double)z + 0.5;
        double value = 0.0;
        double amplitude = 1.0;
        double frequency = 1.0;
        double maxValue = 0.0;
        int clampedOctaves = Math.max(1, Math.min(octaves, this.fbmOctaves.length));
        for (int i = 0; i < clampedOctaves; ++i) {
            value += this.fbmOctaves[i].getValue(cx * frequency, cz * frequency, 0.0) * amplitude;
            maxValue += amplitude;
            frequency *= 2.0;
            amplitude *= gain;
        }
        return maxValue > 0.0 ? value / maxValue : 0.0;
    }

    public double calculateGradient(int x, int z) {
        double step = 4.0;
        double hC = this.sampleFbm(x, z);
        double hX1 = this.sampleFbm(x + 4, z);
        double hX2 = this.sampleFbm(x - 4, z);
        double hZ1 = this.sampleFbm(x, z + 4);
        double hZ2 = this.sampleFbm(x, z - 4);
        double gx = (hX1 - hX2) / (2.0 * step);
        double gz = (hZ1 - hZ2) / (2.0 * step);
        return Math.sqrt(gx * gx + gz * gz);
    }

    /**
     * Immutable cache key for noise value lookups.
     * Encodes (x, z) coordinates plus int-valued parameters to distinguish
     * different noise function call signatures (e.g., octaves, gain, strength, warpStrength).
     */
    private record NoiseCacheKey(int x, int z, int param1, int param2) {
        // Uses Java 16+ record auto-generated equals/hashCode
    }
}

