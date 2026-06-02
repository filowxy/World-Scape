package com.worldscape.terrain;

import com.worldscape.terrain.TerrainContext;
import com.worldscape.terrain.TerrainFunctionSchema;
import com.worldscape.terrain.TerrainTypeRegistry;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/**
 * Represents a terrain type in the world generation system.
 * <p>
 * 表示世界生成系统中的地形类型。
 * <p>
 * Each terrain type has a unique id, name, height range, tier whitelist,
 * and an optional JSON-based function definition for height calculation.
 * 每种地形类型有唯一的 id、名称、高度范围、层级白名单和可选的高度计算函数定义。
 */
public final class TerrainType {

    public static final TerrainType HIGH_MOUNTAINS = new TerrainType("HIGH_MOUNTAINS", "high_mountains", 260, 512);
    public static final TerrainType HILLS = new TerrainType("HILLS", "hills", 55, 110);
    public static final TerrainType CLIFF = new TerrainType("CLIFF", "cliff", 55, 220);
    public static final TerrainType PLATEAU = new TerrainType("PLATEAU", "plateau", 165, 275);
    public static final TerrainType VALLEY = new TerrainType("VALLEY", "valley", 28, 83);
    public static final TerrainType RIDGE = new TerrainType("RIDGE", "ridge", 140, 275);
    public static final TerrainType PEAK = new TerrainType("PEAK", "peak", 165, 330);
    public static final TerrainType CANYON = new TerrainType("CANYON", "canyon", 11, 83);
    public static final TerrainType ALLUVIAL_FAN = new TerrainType("ALLUVIAL_FAN", "alluvial_fan", 44, 110);
    public static final TerrainType FLOODPLAIN = new TerrainType("FLOODPLAIN", "floodplain", 28, 44);
    public static final TerrainType DUNE = new TerrainType("DUNE", "dune", 28, 55);
    public static final TerrainType GOBI = new TerrainType("GOBI", "gobi", 33, 66);
    public static final TerrainType YARDANG = new TerrainType("YARDANG", "yardang", 44, 99);
    public static final TerrainType SALT_FLAT = new TerrainType("SALT_FLAT", "salt_flat", 22, 33);
    public static final TerrainType ICE_SHEET = new TerrainType("ICE_SHEET", "ice_sheet", 55, 165);
    public static final TerrainType GLACIAL_VALLEY = new TerrainType("GLACIAL_VALLEY", "glacial_valley", 28, 110);
    public static final TerrainType CIRQUE = new TerrainType("CIRQUE", "cirque", 83, 193);
    public static final TerrainType HORN = new TerrainType("HORN", "horn", 165, 330);
    public static final TerrainType BEACH = new TerrainType("BEACH", "beach", 28, 39);
    public static final TerrainType SEA_CLIFF = new TerrainType("SEA_CLIFF", "sea_cliff", 44, 110);
    public static final TerrainType FJORD = new TerrainType("FJORD", "fjord", 17, 110);
    public static final TerrainType DELTA = new TerrainType("DELTA", "delta", 22, 39);
    public static final TerrainType PEAK_FOREST = new TerrainType("PEAK_FOREST", "peak_forest", 83, 193);
    public static final TerrainType SINKHOLE = new TerrainType("SINKHOLE", "sinkhole", 11, 55);
    public static final TerrainType PLAINS = new TerrainType("PLAINS", "plains", 33, 55);
    public static final TerrainType BASIN = new TerrainType("BASIN", "basin", 22, 66);
    public static final TerrainType DOME = new TerrainType("DOME", "dome", 83, 193);
    public static final TerrainType TRENCH = new TerrainType("TRENCH", "trench", -55, 0);
    public static final TerrainType SEA_PLATEAU = new TerrainType("SEA_PLATEAU", "sea_plateau", -28, 17);

    private final String name;
    private final String id;
    private final int minHeight;
    private final int maxHeight;
    private int[] tierWhitelist;
    private TerrainFunctionSchema.FunctionDef functionDef;

    /**
     * Private constructor for terrain type instances.
     * 地形类型实例的私有构造函数。
     *
     * @param name      the enum-style name (e.g. "HIGH_MOUNTAINS") for backward compatibility / 枚举式名称，用于向后兼容
     * @param id        the unique identifier (e.g. "high_mountains") / 唯一标识符
     * @param minHeight minimum height range / 最小高度范围
     * @param maxHeight maximum height range / 最大高度范围
     */
    private TerrainType(String name, String id, int minHeight, int maxHeight) {
        this.name = name;
        this.id = id;
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
    }

    /**
     * Returns the enum-style name for backward compatibility with ClimateUtils.
     * 返回枚举式名称，用于与 ClimateUtils 向后兼容。
     *
     * @return the name (e.g. "HIGH_MOUNTAINS") / 名称
     */
    public String name() {
        return this.name;
    }

    /**
     * Returns the unique identifier string.
     * 返回唯一标识符字符串。
     *
     * @return the id (e.g. "high_mountains") / id
     */
    public String getId() {
        return this.id;
    }

    /**
     * Returns the minimum height for this terrain type.
     * 返回此地形类型的最小高度。
     */
    public int getMinHeight() {
        return this.minHeight;
    }

    /**
     * Returns the maximum height for this terrain type.
     * 返回此地形类型的最大高度。
     */
    public int getMaxHeight() {
        return this.maxHeight;
    }

    /**
     * Calculate the height using the stored function definition.
     * Falls back to 0.0 if no function definition is set.
     * 使用存储的函数定义计算高度。若未设置函数定义则返回 0.0。
     *
     * @param context the terrain context / 地形上下文
     * @return the calculated height / 计算出的高度
     */
    public double calculateHeight(TerrainContext context) {
        return 0.0;
    }

    /**
     * Returns all registered terrain types as an array.
     * Delegates to TerrainTypeRegistry.
     * 以数组形式返回所有已注册的地形类型。委托给 TerrainTypeRegistry。
     *
     * @return array of all TerrainType instances / 所有 TerrainType 实例的数组
     */
    public static TerrainType[] values() {
        return TerrainTypeRegistry.getAll().toArray(new TerrainType[0]);
    }

    /**
     * Looks up a terrain type by its string id (in "namespace:id" format).
     * Delegates to TerrainTypeRegistry.
     * 通过字符串 id（格式为 "namespace:id"）查找地形类型。委托给 TerrainTypeRegistry。
     *
     * @param id the string id / 字符串 id
     * @return the matching TerrainType, or null / 匹配的 TerrainType，或 null
     */
    public static TerrainType getById(String id) {
        return TerrainTypeRegistry.get(id);
    }

    /**
     * Returns all terrain types valid for the given elevation tier.
     * Delegates to TerrainTypeRegistry.
     * 返回给定海拔层级所有有效的地形类型。委托给 TerrainTypeRegistry。
     *
     * @param tier the elevation tier / 海拔层级
     * @return array of valid TerrainTypes / 有效 TerrainType 的数组
     */
    public static TerrainType[] getWhitelistForTier(int tier) {
        return TerrainTypeRegistry.getTypesForTier(tier).toArray(new TerrainType[0]);
    }

    /**
     * Checks whether a terrain type is valid for the given elevation tier
     * based on its internal tier whitelist.
     * 根据内部层级白名单检查地形类型是否对给定海拔层级有效。
     *
     * @param type the terrain type to check / 要检查的地形类型
     * @param tier the elevation tier / 海拔层级
     * @return true if valid for the tier / 如果对该层级有效返回 true
     */
    public static boolean isValidForTier(TerrainType type, int tier) {
        if (type == null || type.tierWhitelist == null) {
            return false;
        }
        for (int t : type.tierWhitelist) {
            if (t == tier) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the JSON-based function definition, if set.
     * 返回 JSON 函数定义（如果已设置）。
     *
     * @return the FunctionDef, or null / FunctionDef 或 null
     */
    public TerrainFunctionSchema.FunctionDef getFunctionDef() {
        return this.functionDef;
    }

    /**
     * Sets the JSON-based function definition for this terrain type.
     * 设置此地形类型的 JSON 函数定义。
     *
     * @param functionDef the FunctionDef to associate / 要关联的 FunctionDef
     */
    public void setFunctionDef(TerrainFunctionSchema.FunctionDef functionDef) {
        this.functionDef = functionDef;
    }

    static {
        // Set tier whitelists for each terrain type
        // 为每种地形类型设置层级白名单
        TRENCH.tierWhitelist = new int[]{0};
        SEA_PLATEAU.tierWhitelist = new int[]{0, 1};
        DELTA.tierWhitelist = new int[]{1, 2};
        BEACH.tierWhitelist = new int[]{2};
        FLOODPLAIN.tierWhitelist = new int[]{2, 3};
        DUNE.tierWhitelist = new int[]{2, 3};
        SALT_FLAT.tierWhitelist = new int[]{2};
        SEA_CLIFF.tierWhitelist = new int[]{2};
        FJORD.tierWhitelist = new int[]{2};
        PLAINS.tierWhitelist = new int[]{3};
        HILLS.tierWhitelist = new int[]{3, 4};
        GOBI.tierWhitelist = new int[]{3, 4};
        YARDANG.tierWhitelist = new int[]{3};
        BASIN.tierWhitelist = new int[]{3};
        SINKHOLE.tierWhitelist = new int[]{3};
        PEAK_FOREST.tierWhitelist = new int[]{3};
        CLIFF.tierWhitelist = new int[]{4, 5};
        PLATEAU.tierWhitelist = new int[]{4, 5};
        VALLEY.tierWhitelist = new int[]{4};
        CANYON.tierWhitelist = new int[]{4};
        ALLUVIAL_FAN.tierWhitelist = new int[]{4};
        CIRQUE.tierWhitelist = new int[]{4, 5};
        GLACIAL_VALLEY.tierWhitelist = new int[]{4, 5};
        DOME.tierWhitelist = new int[]{4};
        HIGH_MOUNTAINS.tierWhitelist = new int[]{5};
        RIDGE.tierWhitelist = new int[]{5};
        PEAK.tierWhitelist = new int[]{5};
        HORN.tierWhitelist = new int[]{5};
        ICE_SHEET.tierWhitelist = new int[]{5};

        // Register all types in TerrainTypeRegistry
        // 将所有类型注册到 TerrainTypeRegistry
        TerrainTypeRegistry.register(ResourceLocation.parse("worldscape:high_mountains"), HIGH_MOUNTAINS);
        TerrainTypeRegistry.register(ResourceLocation.parse("worldscape:hills"), HILLS);
        TerrainTypeRegistry.register(ResourceLocation.parse("worldscape:cliff"), CLIFF);
        TerrainTypeRegistry.register(ResourceLocation.parse("worldscape:plateau"), PLATEAU);
        TerrainTypeRegistry.register(ResourceLocation.parse("worldscape:valley"), VALLEY);
        TerrainTypeRegistry.register(ResourceLocation.parse("worldscape:ridge"), RIDGE);
        TerrainTypeRegistry.register(ResourceLocation.parse("worldscape:peak"), PEAK);
        TerrainTypeRegistry.register(ResourceLocation.parse("worldscape:canyon"), CANYON);
        TerrainTypeRegistry.register(ResourceLocation.parse("worldscape:alluvial_fan"), ALLUVIAL_FAN);
        TerrainTypeRegistry.register(ResourceLocation.parse("worldscape:floodplain"), FLOODPLAIN);
        TerrainTypeRegistry.register(ResourceLocation.parse("worldscape:dune"), DUNE);
        TerrainTypeRegistry.register(ResourceLocation.parse("worldscape:gobi"), GOBI);
        TerrainTypeRegistry.register(ResourceLocation.parse("worldscape:yardang"), YARDANG);
        TerrainTypeRegistry.register(ResourceLocation.parse("worldscape:salt_flat"), SALT_FLAT);
        TerrainTypeRegistry.register(ResourceLocation.parse("worldscape:ice_sheet"), ICE_SHEET);
        TerrainTypeRegistry.register(ResourceLocation.parse("worldscape:glacial_valley"), GLACIAL_VALLEY);
        TerrainTypeRegistry.register(ResourceLocation.parse("worldscape:cirque"), CIRQUE);
        TerrainTypeRegistry.register(ResourceLocation.parse("worldscape:horn"), HORN);
        TerrainTypeRegistry.register(ResourceLocation.parse("worldscape:beach"), BEACH);
        TerrainTypeRegistry.register(ResourceLocation.parse("worldscape:sea_cliff"), SEA_CLIFF);
        TerrainTypeRegistry.register(ResourceLocation.parse("worldscape:fjord"), FJORD);
        TerrainTypeRegistry.register(ResourceLocation.parse("worldscape:delta"), DELTA);
        TerrainTypeRegistry.register(ResourceLocation.parse("worldscape:peak_forest"), PEAK_FOREST);
        TerrainTypeRegistry.register(ResourceLocation.parse("worldscape:sinkhole"), SINKHOLE);
        TerrainTypeRegistry.register(ResourceLocation.parse("worldscape:plains"), PLAINS);
        TerrainTypeRegistry.register(ResourceLocation.parse("worldscape:basin"), BASIN);
        TerrainTypeRegistry.register(ResourceLocation.parse("worldscape:dome"), DOME);
        TerrainTypeRegistry.register(ResourceLocation.parse("worldscape:trench"), TRENCH);
        TerrainTypeRegistry.register(ResourceLocation.parse("worldscape:sea_plateau"), SEA_PLATEAU);
    }
}