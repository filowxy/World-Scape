/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.util.RandomSource
 */
package com.worldscape.util;

import net.minecraft.util.RandomSource;

public class SeedDeriver {
    public static final long SALT_MACRO_VORONOI_X = 320162536705L;
    public static final long SALT_MACRO_VORONOI_Z = 393463270930L;
    public static final long SALT_MACRO_ELEVATION = 464079691555L;
    public static final long SALT_MACRO_TECTONIC = 537380462644L;
    public static final long SALT_MACRO_RIFT = 610680189253L;
    public static final long SALT_MACRO_CLIMATE = 683712528982L;
    public static final long SALT_TERRAIN_VORONOI_X = 44051891141L;
    public static final long SALT_TERRAIN_VORONOI_Z = 117352666326L;
    public static final long SALT_TERRAIN_TYPE = 190653441511L;
    public static final long SALT_TERRAIN_RADIUS = 263954216696L;
    public static final long SALT_NOISE_CONTINENT = 337254991625L;
    public static final long SALT_NOISE_REGION = 410538989594L;
    public static final long SALT_NOISE_MOUNTAIN = 415120288043L;
    public static final long SALT_NOISE_VALLEY = 488421063228L;
    public static final long SALT_NOISE_HILLS = 561721772877L;
    public static final long SALT_NOISE_PLAINS_MACRO = 635022548062L;
    public static final long SALT_NOISE_PLAINS_MESO = 708323323247L;
    public static final long SALT_NOISE_PLAINS_MICRO = 781624098416L;
    public static final long SALT_NOISE_RIVER_PATH = 850629906305L;
    public static final long SALT_NOISE_RIVER_WIDTH = 923930677394L;
    public static final long SALT_NOISE_DRAINAGE = 997230404003L;
    public static final long SALT_NOISE_Mountain_PEAKS = 1070262743732L;
    public static final long SALT_NOISE_Mountain_RIDGE = 44051891159L;
    public static final long SALT_NOISE_Mountain_DETAIL = 117352666344L;
    public static final long SALT_NOISE_SEABED = 48633189591L;
    public static final long SALT_TRANSITION_BLEND = 190653441529L;
    public static final long SALT_TERRAIN_ENERGY = 832466842634L;
    public static final long SALT_TERRAIN_MOISTURE = 905767551413L;
    public static final long SALT_TERRAIN_ENERGY_DETAIL = 979051293805L;
    public static final long SALT_FBM_OCTAVE_0 = 263889798480852L;
    public static final long SALT_FBM_OCTAVE_1 = 263894379779301L;
    public static final long SALT_FBM_OCTAVE_2 = 263898961077750L;
    public static final long SALT_FBM_OCTAVE_3 = 263903542375943L;
    public static final long SALT_FBM_OCTAVE_4 = 263908123608856L;
    public static final long SALT_FBM_OCTAVE_5 = 263912688130089L;
    public static final long SALT_DOMAIN_ANGLE = 263913260792395L;
    public static final long SALT_DOMAIN_OFFSET_X = 263917842090844L;
    public static final long SALT_DOMAIN_OFFSET_Z = 263922423389293L;
    public static final double DOMAIN_ANGLE_SCALE = 1.220703125E-4;
    public static final double DOMAIN_OFFSET_SCALE = 2.44140625E-4;
    public static final long SALT_WIND_DIRECTION = 263927004687742L;
    public static final long SALT_WIND_PERPDIR = 263931585986191L;

    public static long deriveSeed(long worldSeed, long salt) {
        RandomSource random = RandomSource.create((long)(worldSeed ^ salt));
        return random.nextLong();
    }

    public static long deriveMacroVoronoiX(long worldSeed) {
        return SeedDeriver.deriveSeed(worldSeed, 320162536705L);
    }

    public static long deriveMacroVoronoiZ(long worldSeed) {
        return SeedDeriver.deriveSeed(worldSeed, 393463270930L);
    }

    public static long deriveMacroElevationSeed(long worldSeed) {
        return SeedDeriver.deriveSeed(worldSeed, 464079691555L);
    }

    public static long deriveMacroTectonicSeed(long worldSeed) {
        return SeedDeriver.deriveSeed(worldSeed, 537380462644L);
    }

    public static long deriveMacroRiftSeed(long worldSeed) {
        return SeedDeriver.deriveSeed(worldSeed, 610680189253L);
    }

    public static long deriveMacroClimateSeed(long worldSeed) {
        return SeedDeriver.deriveSeed(worldSeed, 683712528982L);
    }

    public static long deriveTerrainVoronoiX(long worldSeed) {
        return SeedDeriver.deriveSeed(worldSeed, 44051891141L);
    }

    public static long deriveTerrainVoronoiZ(long worldSeed) {
        return SeedDeriver.deriveSeed(worldSeed, 117352666326L);
    }

    public static long deriveTerrainTypeSeed(long worldSeed) {
        return SeedDeriver.deriveSeed(worldSeed, 190653441511L);
    }

    public static long deriveTerrainRadiusSeed(long worldSeed) {
        return SeedDeriver.deriveSeed(worldSeed, 263954216696L);
    }

    public static long deriveTransitionBlendSeed(long worldSeed) {
        return SeedDeriver.deriveSeed(worldSeed, 190653441529L);
    }

    public static long deriveSeabedNoiseSeed(long worldSeed) {
        return SeedDeriver.deriveSeed(worldSeed, 48633189591L);
    }

    public static double smoothstep(double edge0, double edge1, double x) {
        double t = Math.max(0.0, Math.min(1.0, (x - edge0) / (edge1 - edge0)));
        return t * t * (3.0 - 2.0 * t);
    }

    public static double smootherstep(double edge0, double edge1, double x) {
        double t = Math.max(0.0, Math.min(1.0, (x - edge0) / (edge1 - edge0)));
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }
}

