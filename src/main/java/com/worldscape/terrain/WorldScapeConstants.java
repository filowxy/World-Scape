package com.worldscape.terrain;

public final class WorldScapeConstants {
    public static final int CHUNK_SIZE = 16;
    // Removed: REGION_SIZE=256, REGION_HALF_SIZE=128, CHUNK_CENTER_OFFSET=8
    // These were dead code and conflicted with ControlPointRegion.REGION_SIZE=512.
    // The actual region size used by the terrain system is 512 (defined in ControlPointRegion).
    // 已移除：REGION_SIZE=256、REGION_HALF_SIZE=128、CHUNK_CENTER_OFFSET=8
    // 这些是死代码，且与 ControlPointRegion.REGION_SIZE=512 冲突。
    // 地形系统实际使用的区域大小为 512（定义在 ControlPointRegion 中）。
    public static final int SEA_LEVEL_FALLBACK = 63;
    public static final int MIN_TERRAIN_HEIGHT = -64;
    public static final int MAX_TERRAIN_HEIGHT = 400;
    // Hard upper clamp for terrain height calculations: MAX_TERRAIN_HEIGHT - 20
    // 地形高度硬上限：MAX_TERRAIN_HEIGHT - 20
    public static final int TERRAIN_HARD_CLAMP = MAX_TERRAIN_HEIGHT - 20;
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
    // @AESTHETIC: Tier base-height RANGES (replacing single hardcoded point values).
    // Each tier now spans [min, max] instead of a single discrete height. Adjacent
    // tiers overlap (e.g. Tier3 [20,110] ∩ Tier4 [90,240] = [90,110]), so boundary
    // transitions can interpolate continuously within the overlap region instead
    // of jumping between two fixed points — eliminating cliff/fault artifacts.
    // Midpoints approximate the legacy single values to preserve overall terrain shape.
    // See MacroVoronoiSystem#getTierBaseHeightLerped for the noise-driven lerp.
    //
    // 层级基准高度【范围】（替代单一硬编码点值）。每个层级现在跨度为 [min, max] 而非
    // 单一离散高度。相邻层级范围有重叠（如 Tier3 [20,110] ∩ Tier4 [90,240] = [90,110]），
    // 边界过渡可在重叠区内连续插值，而非在两个固定点之间跳变 — 消除悬崖/断层伪影。
    // 中点近似旧的单值，以保留整体地形轮廓。插值驱动见 MacroVoronoiSystem#getTierBaseHeightLerped。
    public static final int TIER_0_MIN_HEIGHT = -120;
    public static final int TIER_0_MAX_HEIGHT = -40;   // deep ocean / 深海
    public static final int TIER_1_MIN_HEIGHT = -70;
    public static final int TIER_1_MAX_HEIGHT = 30;   // ocean / 海洋
    public static final int TIER_2_MIN_HEIGHT = 10;
    public static final int TIER_2_MAX_HEIGHT = 90;   // coast/lowland / 海岸/低地
    public static final int TIER_3_MIN_HEIGHT = 20;
    public static final int TIER_3_MAX_HEIGHT = 110;  // lowland / 低地
    public static final int TIER_4_MIN_HEIGHT = 90;
    public static final int TIER_4_MAX_HEIGHT = 240;  // hills/plateau / 丘陵/高原
    public static final int TIER_5_MIN_HEIGHT = 200;
    public static final int TIER_5_MAX_HEIGHT = 380;  // mountains / 山地（覆盖至高度带上限）
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
    // Range for erosion noise normalization: 1.0 - EROSION_NOISE_THRESHOLD
    // 侵蚀噪声归一化范围：1.0 - EROSION_NOISE_THRESHOLD
    public static final double EROSION_NOISE_RANGE = 0.55;
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
    // Stone vein size: 8x8x8 blocks per vein group for natural-looking mineral deposits.
    // 矿脉大小：每组8x8x8方块，形成自然的矿物脉状分布。
    public static final int STONE_VEIN_SIZE = 8;
    // Export radius in chunks for world save data export (16 chunks ~ 256 blocks).
    // 世界存档数据导出的区块半径（16 区块 ≈ 256 格）。
    public static final int EXPORT_RADIUS_CHUNKS = 16;
    // Export output directory name (relative to game directory).
    // 导出输出目录名（相对于游戏目录）。
    public static final String EXPORT_DIR_NAME = "worldscape_exports";
    // Number of sub-surface layers to export below surfaceY.
    // 地表以下导出的次表层数量。
    public static final int EXPORT_SUBSURFACE_DEPTH = 10;
    // Removed unused SNOW_ALTITUDE_OFFSET -- replaced by ALPINE_SNOW_OFFSET below.
    // 已移除未使用的 SNOW_ALTITUDE_OFFSET -- 由下方的 ALPINE_SNOW_OFFSET 替代。
    // Alpine snow line: altitude above sea level where mountain terrain types show snow.
    // 高山雪线：山地地形类型显示雪块的海拔阈值（高于海平面的高度）。
    public static final int ALPINE_SNOW_OFFSET = 80;
    // Bare rock altitude: altitude above sea level where mountain terrain shows bare stone.
    // 裸岩海拔：山地地形显示石头表面的海拔阈值（高于海平面的高度）。
    public static final int ROCK_ALTITUDE_OFFSET = 40;
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
    // Fallback FBM parameters for terrain height calculation when functionDef is null
    // 当 functionDef 为 null 时用于地形高度计算的 FBM 参数
    public static final int FBM_FALLBACK_OCTAVES = 4;
    public static final double FBM_FALLBACK_GAIN = 0.2;
    public static final double FBM_FALLBACK_AMPLITUDE = 15.0;
    // Tier thresholds for erosion and river depth multipliers
    // 用于侵蚀和河流深度乘数的等级阈值
    public static final int MOUNTAIN_TIER_THRESHOLD = 5;
    public static final int LOWLAND_TIER_THRESHOLD = 2;
    public static final int RIVER_TIER_THRESHOLD = 4;
    // Search radius for control points in 3×3 region search
    // 3×3 区域搜索中控制点的搜索半径
    public static final double CONTROL_POINT_SEARCH_RADIUS = 1200.0;
    // Scale factor for tier gap influence on macro smoothing
    // 等级差距对宏观平滑影响的缩放因子
    public static final double TIER_GAP_FACTOR_SCALE = 0.08;
    // Threshold for warning about slow region generation (milliseconds)
    // 区域生成缓慢警告阈值（毫秒）
    public static final long REGION_GEN_SLOW_THRESHOLD_MS = 200L;
    // Number of entries to evict per cache eviction cycle
    // 每次缓存淘汰周期要删除的条目数
    public static final int CACHE_EVICTION_BATCH_SIZE = 512;
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

    // Noise scale factors for TerrainFieldSampler.
    // Centralized here so the same frequency is reused everywhere it is needed.
    // 1/4096 (energy main) ≈ 0.000244, 1/1024 (energy detail) ≈ 0.000977,
    // 1/256 (energy detail2) ≈ 0.003906, 1/2048 (moisture) ≈ 0.000488.
    // 噪声缩放因子集中在此处，方便统一调参。
    // 主能量 1/4096 ≈ 0.000244，细节 1/1024 ≈ 0.000977，
    // 二级细节 1/256 ≈ 0.003906，湿度 1/2048 ≈ 0.000488。
    public static final double ENERGY_MAIN_SCALE = 2.44140625E-4;
    public static final double ENERGY_DETAIL_SCALE = 9.765625E-4;
    public static final double ENERGY_DETAIL2_SCALE = 0.00390625;
    public static final double MOISTURE_SCALE = 4.8828125E-4;
    public static final double ENERGY_DETAIL_WEIGHT = 0.3;
    public static final double ENERGY_TO_OFFSET_SCALE = 50.0;

    private WorldScapeConstants() {
    }
}