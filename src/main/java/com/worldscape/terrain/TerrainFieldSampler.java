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
import java.util.List;
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
    private static final List<Double> TIER_THRESHOLDS = List.of(Double.valueOf(-1.405), Double.valueOf(-0.674), Double.valueOf(0.0), Double.valueOf(0.842), Double.valueOf(1.645));
    public static final int NO_MACRO_TIER_CONSTRAINT = -1;
    private static final double ENERGY_TO_OFFSET_SCALE = 50.0;
    private static volatile TerrainFieldSampler instance;
    private static volatile long cachedSeed;
    private static final Object LOCK;

    private TerrainFieldSampler(long worldSeed) {
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
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static TerrainFieldSampler getOrCreate(long worldSeed) {
        Object object;
        TerrainFieldSampler current = instance;
        if (current != null && cachedSeed == worldSeed) {
            return current;
        }
        Object object2 = object = LOCK;
        synchronized (object2) {
            if (instance == null || cachedSeed != worldSeed) {
                instance = new TerrainFieldSampler(worldSeed);
                cachedSeed = worldSeed;
            }
            return instance;
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

    public TerrainType selectTypeByMoisture(int tier, double moisture) {
        double normalizedMoisture = (moisture + 1.0) * 0.5;
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

    public double calculateContinuousOffset(double energy, TerrainType type) {
        double baseOffset = energy * 50.0;
        double typeModifier = TerrainFieldSampler.getTypeModifier(type);
        return baseOffset + typeModifier;
    }

    private static double getTypeModifier(TerrainType type) {
        return switch (type) {
            case TerrainType.TRENCH -> -90.0;
            case TerrainType.SEA_PLATEAU -> -70.0;
            case TerrainType.DELTA -> -45.0;
            case TerrainType.BEACH -> -15.0;
            case TerrainType.SALT_FLAT -> -5.0;
            case TerrainType.FLOODPLAIN -> -10.0;
            case TerrainType.DUNE -> 0.0;
            case TerrainType.SEA_CLIFF -> 5.0;
            case TerrainType.FJORD -> -20.0;
            case TerrainType.PLAINS -> 0.0;
            case TerrainType.GOBI -> 5.0;
            case TerrainType.YARDANG -> 10.0;
            case TerrainType.BASIN -> -15.0;
            case TerrainType.SINKHOLE -> -20.0;
            case TerrainType.PEAK_FOREST -> 25.0;
            case TerrainType.HILLS -> 20.0;
            case TerrainType.CLIFF -> 45.0;
            case TerrainType.PLATEAU -> 50.0;
            case TerrainType.DOME -> 40.0;
            case TerrainType.VALLEY -> 25.0;
            case TerrainType.CANYON -> 15.0;
            case TerrainType.ALLUVIAL_FAN -> 30.0;
            case TerrainType.CIRQUE -> 35.0;
            case TerrainType.GLACIAL_VALLEY -> 20.0;
            case TerrainType.HIGH_MOUNTAINS -> 80.0;
            case TerrainType.RIDGE -> 70.0;
            case TerrainType.PEAK -> 75.0;
            case TerrainType.HORN -> 85.0;
            case TerrainType.ICE_SHEET -> 65.0;
            default -> 0.0;
        };
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

    private TerrainType selectTier3Type(double m) {
        if (m < 0.07) {
            return TerrainType.YARDANG;
        }
        if (m < 0.15) {
            return TerrainType.GOBI;
        }
        if (m < 0.25) {
            return TerrainType.DUNE;
        }
        if (m < 0.55) {
            return TerrainType.PLAINS;
        }
        if (m < 0.7) {
            return TerrainType.FLOODPLAIN;
        }
        if (m < 0.9) {
            return TerrainType.HILLS;
        }
        if (m < 0.94) {
            return TerrainType.BASIN;
        }
        if (m < 0.97) {
            return TerrainType.SINKHOLE;
        }
        return TerrainType.PEAK_FOREST;
    }

    private TerrainType selectTier4Type(double m) {
        if (m < 0.08) {
            return TerrainType.GOBI;
        }
        if (m < 0.18) {
            return TerrainType.CANYON;
        }
        if (m < 0.26) {
            return TerrainType.ALLUVIAL_FAN;
        }
        if (m < 0.51) {
            return TerrainType.HILLS;
        }
        if (m < 0.66) {
            return TerrainType.CLIFF;
        }
        if (m < 0.81) {
            return TerrainType.PLATEAU;
        }
        if (m < 0.91) {
            return TerrainType.VALLEY;
        }
        if (m < 0.96) {
            return TerrainType.CIRQUE;
        }
        return TerrainType.GLACIAL_VALLEY;
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
        if (m < 0.9) {
            return TerrainType.HORN;
        }
        if (m < 0.95) {
            return TerrainType.ICE_SHEET;
        }
        return TerrainType.GLACIAL_VALLEY;
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

    static {
        cachedSeed = Long.MIN_VALUE;
        LOCK = new Object();
    }
}

