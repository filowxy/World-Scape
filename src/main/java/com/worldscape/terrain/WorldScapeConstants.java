/*
 * Decompiled with CFR 0.152.
 */
package com.worldscape.terrain;

public final class WorldScapeConstants {
    public static final int CHUNK_SIZE = 16;
    public static final int REGION_SIZE = 256;
    public static final int REGION_HALF_SIZE = 128;
    public static final int CHUNK_CENTER_OFFSET = 8;
    public static final int SEA_LEVEL_FALLBACK = 63;
    public static final int MIN_TERRAIN_HEIGHT = -64;
    public static final int MAX_TERRAIN_HEIGHT = 400;
    public static final int OVERWORLD_MIN_Y = -64;
    public static final int OVERWORLD_HEIGHT = 384;
    public static final int DEEPSLATE_TOP_Y = 0;
    public static final long HASH_MULTIPLIER_X = 31L;
    public static final long HASH_MULTIPLIER_Z = 17L;
    public static final long SEED_XOR_MASK = 3735928559L;
    public static final long BEDROCK_SEED = 388350381470L;
    public static final long POS_KEY_MASK = 0xFFFFFFFFL;
    public static final int POS_KEY_Z_SHIFT = 32;
    public static final int MAX_BEDROCK_LAYERS = 3;
    public static final double DOMINANT_WEIGHT_THRESHOLD = 0.4;
    public static final double BLEND_WEIGHT_THRESHOLD = 0.8;
    // 宏观影响最大值：控制 Voronoi 边界过渡的平滑度
    // Maximum macro influence: controls smoothness of Voronoi boundary transitions
    public static final double MAX_MACRO_INFLUENCE = 0.25;
    public static final double OCEAN_TIER0_MACRO_DAMPING = 0.33;
    public static final double OCEAN_TIER1_MACRO_DAMPING = 0.5;
    public static final double TIER_BASE_HEIGHT = 8.0;
    public static final double TIER_ADJUSTMENT_FACTOR = 0.15;
    public static final int FBM_OCTAVES = 6;
    public static final double FBM_LACUNARITY = 2.0;
    public static final double FBM_GAIN = 0.5;
    public static final double DOMAIN_ROTATION_STRENGTH = 0.15;
    public static final double RIDGE_TURBULENCE_STRENGTH = 0.6;
    public static final double RIVER_GRADIENT_FOLLOW_STRENGTH = 0.8;
    public static final double RIVER_MOUNTAIN_WIDTH = 10.0;
    public static final double RIVER_PLAIN_WIDTH = 20.0;
    public static final double RIVER_DEPTH_THRESHOLD = 0.5;
    public static final double RIVER_DIFF_THRESHOLD = 0.1;
    public static final double RIVER_WIDTH_THRESHOLD = 0.15;
    public static final double RIVER_DEPTH_SCALE = 2.5;
    public static final double RIVER_DEPTH_AMPLIFIER = 10.0;
    public static final double RIVER_MIN_DEPTH = 3.0;
    public static final double RIVER_MAX_DEPTH = 20.0;
    public static final int HILLS_TIER_THRESHOLD = 3;
    public static final double EROSION_NOISE_THRESHOLD = 0.45;
    public static final double EROSION_INTENSITY_FACTOR = 0.8;
    public static final double EROSION_CUT_MULTIPLIER = 30.0;
    // @AESTHETIC: Terrain-type-dependent erosion intensity multipliers
    // High mountains/ridges get deeper gully erosion; plains/ice get gentler erosion
    // 山地类型侵蚀更强（更深的沟壑），平原/冰盖类型侵蚀更弱
    public static final double EROSION_MULTIPLIER_MOUNTAIN = 1.5;
    public static final double EROSION_MULTIPLIER_PLAIN = 0.5;
    // @AESTHETIC: Terrain-type-dependent river depth multipliers (legacy, kept for erosion)
    // Mountain rivers are deeper/narrower; plain rivers are shallower/wider
    // 山区河流更深（×1.5），平原河流更浅（×0.8）
    public static final double RIVER_DEPTH_MULTIPLIER_MOUNTAIN = 1.5;
    public static final double RIVER_DEPTH_MULTIPLIER_PLAIN = 0.8;
    // @AESTHETIC: Continuous elevation-based river depth interpolation parameters.
    // Replaces hard tier switching — depth multiplier lerps smoothly from sea level
    // to high elevation, preserving river continuity across terrain transitions.
    // 基于海拔的连续河流深度插值参数：替代硬切换，深度乘数从海平面到高海拔平滑过渡，
    // 保持河流跨越地形变化时的连续性。
    public static final double RIVER_DEPTH_ELEVATION_MIN = 0.0;
    public static final double RIVER_DEPTH_ELEVATION_MAX = 200.0;
    public static final double RIVER_DEPTH_MULTIPLIER_LOW = 0.8;
    public static final double RIVER_DEPTH_MULTIPLIER_HIGH = 1.5;
    // @AESTHETIC: Gradient-driven river depth factor.
    // Steeper terrain → deeper river incision. Gradient is a smoothly varying field,
    // so depth transitions remain continuous even when crossing terrain boundaries.
    // 基于梯度的河流深度因子：陡坡→更深河流切割。梯度是平滑变化的场，
    // 因此深度过渡即使在跨越地形边界时也保持连续。
    public static final double RIVER_GRADIENT_REFERENCE = 0.2;
    public static final double RIVER_GRADIENT_DEPTH_FACTOR = 0.5;
    public static final double ELEVATION_NORMALIZATION_FACTOR = 100.0;
    public static final double ALLUVIAL_FAN_AMPLITUDE = 25.0;
    public static final double ALLUVIAL_FAN_FBM_AMP = 5.0;
    public static final double ALLUVIAL_THRESHOLD = 0.45;
    public static final double ALLUVIAL_FACTOR = 0.4;
    public static final int ALLUVIAL_HEIGHT_RANGE = 20;
    public static final double ALLUVIAL_DISTANCE_RANGE = 20.0;
    public static final double ALLUVIAL_RAISE_MULTIPLIER = 5.0;
    public static final double ALLUVIAL_FAN_DISTANCE_PERIOD = 200.0;
    public static final double ALLUVIAL_FAN_DISTANCE_NORM = 100.0;
    public static final double ERF_APPROX_FACTOR = 0.886;
    public static final double SIGMOID_STEEPNESS_DEFAULT = 1.0;
    public static final double SLOPE_ANOMALY_THRESHOLD = 30.0;
    public static final int HEIGHT_CHANGE_WARNING_THRESHOLD = 100;
    public static final int HEIGHT_CHANGE_CRITICAL_THRESHOLD = 150;
    public static final int VOID_MIN_HEIGHT = -64;
    public static final double ICE_TEMPERATURE = 0.0;
    public static final double ICE_HUMIDITY = 0.6;
    public static final double MOUNTAIN_TEMPERATURE = 0.2;
    public static final double MOUNTAIN_HUMIDITY = 0.3;
    public static final double PLAINS_TEMPERATURE = 0.5;
    public static final double PLAINS_HUMIDITY = 0.5;
    public static final double DESERT_TEMPERATURE = 0.8;
    public static final double DESERT_HUMIDITY = 0.0;
    public static final double FOREST_TEMPERATURE = 1.0;
    public static final double FOREST_HUMIDITY = 0.8;
    public static final double CANYON_CLIMATE_TEMPERATURE = 0.5;
    public static final double CANYON_CLIMATE_HUMIDITY = 0.8;
    public static final double BEACH_TEMPERATURE = 0.5;
    public static final double BEACH_HUMIDITY = 0.7;
    public static final double DEFAULT_TEMPERATURE = 0.5;
    public static final double DEFAULT_HUMIDITY = 0.5;
    public static final double DEFAULT_SEASONALITY = 0.5;
    public static final double DEFAULT_CONTINENTALITY = 0.5;
    public static final double ICE_SEASONALITY = 0.1;
    public static final double ICE_CONTINENTALITY = 0.8;
    public static final double MOUNTAIN_SEASONALITY = 0.6;
    public static final double MOUNTAIN_CONTINENTALITY = 0.7;
    public static final double PLAINS_SEASONALITY = 0.6;
    public static final double PLAINS_CONTINENTALITY = 0.5;
    public static final double DESERT_SEASONALITY = 0.8;
    public static final double DESERT_CONTINENTALITY = 0.95;
    public static final double FOREST_SEASONALITY = 0.7;
    public static final double FOREST_CONTINENTALITY = 0.3;
    public static final double CANYON_SEASONALITY = 0.55;
    public static final double CANYON_CONTINENTALITY = 0.75;
    public static final double BEACH_SEASONALITY = 0.3;
    public static final double BEACH_CONTINENTALITY = 0.2;
    public static final double COASTAL_TEMPERATE_TEMP = 0.55;
    public static final double COASTAL_TEMPERATE_HUMID = 0.75;
    public static final double COASTAL_TEMPERATE_SEASON = 0.35;
    public static final double COASTAL_TEMPERATE_CONT = 0.15;
    public static final double DEEP_OCEAN_TEMP = 0.08;
    public static final double DEEP_OCEAN_HUMID = 0.95;
    public static final double DEEP_OCEAN_SEASON = 0.05;
    public static final double DEEP_OCEAN_CONT = 0.05;
    public static final double ELEVATION_COOLING_PER_TIER = 0.05;
    public static final double LATITUDE_COOLING_FACTOR = 0.3;
    public static final double GLACIAL_LATITUDE_THRESHOLD = 0.44;
    public static final double CLIMATE_BLEND_THRESHOLD = 0.9;
    public static final double TEMPERATE_THRESHOLD = 0.5;
    public static final int OCEAN_ELEVATION_TIER_MAX = 1;
    public static final int MOUNTAIN_ELEVATION_TIER_MIN = 5;
    public static final int MOUNTAIN_BIOME_HEIGHT_THRESHOLD = 300;
    public static final double DEFAULT_CONFIDENCE_THRESHOLD = 0.3;
    public static final int HIGH_ALTITUDE_OFFSET = 30;
    public static final long STONE_HASH_X = 31341L;
    public static final long STONE_HASH_Z = 45231L;
    public static final int SUBSURFACE_LAYER_DEPTH = 4;
    public static final int UNDERWATER_STONE_DEPTH = 5;
    public static final int DEEP_STONE_LAYER_DEPTH = 16;
    public static final int STONE_VARIANT_CHANCE = 10;
    public static final int STONE_VARIANT_ROLL_RANGE = 100;
    public static final int GRANITE_THRESHOLD = 10;
    public static final int DIORITE_THRESHOLD = 20;
    public static final int ANDESITE_THRESHOLD = 30;
    public static final int COBBLESTONE_THRESHOLD = 33;
    public static final int SNOW_ALTITUDE_OFFSET = 50;
    public static final int LOCATE_MAX_SEARCH_RADIUS = 20000;
    public static final int LOCATE_SEARCH_STEP = 64;
    public static final int COORDINATE_LINK_COLOR = 65280;
    public static final int BLOCK_UPDATE_FLAG = 3;
    public static final int SPATIAL_INDEX_CELL_SIZE = 512;
    public static final int SPATIAL_INDEX_MAX_QUERY_CACHE = 256;
    public static final int SPATIAL_INDEX_MAX_CACHEABLE_RESULTS = 500;
    public static final int MACRO_CELL_SIZE = 2048;
    public static final int MICRO_REGION_SIZE = 512;
    public static final double ELEVATION_TIER_NORMALIZATION = 5.0;
    public static final double MICRO_WEIGHT_BASE = 0.5;
    public static final double ELEVATION_OFFSET_NORMALIZATION = 200.0;
    public static final int NEAREST_POINT_SEARCH_RADIUS = 50;
    public static final int VORONOI_MAX_POINTS = 5000;
    public static final int VORONOI_AUTO_SAVE_INTERVAL = 600;
    public static final int FILE_READ_BUFFER_SIZE = 4096;
    public static final double VORONOI_EPSILON = 1.0E-10;
    public static final double VORONOI_INFINITE_RADIUS = 1.0E10;
    public static final double VORONOI_CLIP_MARGIN_FACTOR = 0.5;
    public static final float MACRO_ZOOM = 0.25f;
    public static final float MICRO_ZOOM = 2.0f;
    public static final float MIN_ZOOM = 0.05f;
    public static final float MAX_ZOOM = 8.0f;
    public static final float ZOOM_IN_FACTOR = 1.15f;
    public static final float ZOOM_OUT_FACTOR = 0.85f;
    public static final float CAMERA_TRANSITION_SPEED = 4.0f;
    public static final float CAMERA_MOVE_SPEED = 800.0f;
    public static final double CAMERA_ARRIVAL_THRESHOLD = 0.5;
    public static final float NS_TO_MS = 1000000.0f;
    public static final float FPS_SMOOTH_OLD_WEIGHT = 0.9f;
    public static final float FPS_SMOOTH_NEW_WEIGHT = 0.1f;
    public static final int SCREEN_CLIP_MARGIN_POLYGON = 50;
    public static final int SCREEN_CLIP_MARGIN_POINT = 20;
    public static final int ID_DISPLAY_MAX_LENGTH = 20;
    public static final int VIEWPORT_LINE_MARGIN = 10;
    public static final long DOUBLE_CLICK_THRESHOLD = 300L;
    public static final float MOVE_SPEED_PER_TICK = 20.0f;
    public static final int BIOME_CELLS_PER_AXIS = 4;
    public static final int BIOME_CELLS_PER_SECTION_Y = 4;
    public static final int BIOME_CELL_SIZE = 4;
    public static final int BIOME_CELL_CENTER = 2;

    private WorldScapeConstants() {
    }
}