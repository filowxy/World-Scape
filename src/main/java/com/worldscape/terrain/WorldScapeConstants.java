package com.worldscape.terrain;

public final class WorldScapeConstants {
    public static final double BLEND_WEIGHT_THRESHOLD = 0.8;
    public static final double DOMINANT_WEIGHT_THRESHOLD = 0.4;
    public static final double MAX_MACRO_INFLUENCE = 0.15;
    public static final double OCEAN_TIER0_MACRO_DAMPING = 0.33;
    public static final double OCEAN_TIER1_MACRO_DAMPING = 0.5;
    public static final double TIER_BASE_HEIGHT = 8.0;
    public static final double TIER_ADJUSTMENT_FACTOR = 0.15;
    public static final int MAX_TERRAIN_HEIGHT = 300;
    public static final int MIN_TERRAIN_HEIGHT = -64;
    public static final double HIGH_MOUNTAIN_PEAK_CEILING = 500.0;
    public static final int FBM_OCTAVES = 6;
    public static final double FBM_LACUNARITY = 2.0;
    public static final double FBM_GAIN = 0.5;
    public static final double RIDGE_SINE_PRIMARY_AMP = 35.0;
    public static final double RIDGE_SINE_SECONDARY_AMP = 18.0;
    public static final double RIDGE_GRADIENT_SENSITIVITY = 0.6;
    public static final double DOMAIN_ROTATION_STRENGTH = 0.15;
    public static final double ENERGY_STRETCH_ALONG_RIDGE = 1.5;
    public static final double ENERGY_STRETCH_ACROSS_RIDGE = 0.7;
    public static final double RIVER_GRADIENT_FOLLOW_STRENGTH = 0.8;
    public static final double RIVER_MOUNTAIN_WIDTH = 10.0;
    public static final double RIVER_PLAIN_WIDTH = 20.0;
    public static final double RIDGE_TURBULENCE_STRENGTH = 0.6;
    public static final double SIGMOID_STEEPNESS_DEFAULT = 1.0;
    public static final double TANH_STEEPNESS_CLIFF = 2.0;
    public static final double TANH_STEEPNESS_SEA_CLIFF = 3.0;
    public static final double GAUSSIAN_SIGMA_DOME = 200.0;
    public static final double GAUSSIAN_SIGMA_CIRQUE = 150.0;
    public static final double GAUSSIAN_SIGMA_SINKHOLE = 80.0;
    public static final double GAUSSIAN_SIGMA_BASIN = 300.0;
    public static final double ALLUVIAL_FAN_AMPLITUDE = 25.0;
    public static final double ALLUVIAL_FAN_FBM_AMP = 5.0;
    public static final double CIRQUE_DEPTH = 120.0;
    public static final double CIRQUE_EDGE_TURBULENCE = 60.0;
    public static final double DOME_AMPLITUDE = 150.0;
    public static final double DOME_OFFSET = 50.0;
    public static final double BASIN_DEPTH = 30.0;
    public static final double BASIN_OFFSET = 80.0;
    public static final double DUNE_PRIMARY_AMP = 25.0;
    public static final double DUNE_SECONDARY_AMP = 8.0;
    public static final double YARDANG_AMP = 30.0;

    private WorldScapeConstants() {
    }
}

