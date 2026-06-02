package com.worldscape.terrain;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.worldscape.WorldScape;
import net.minecraft.resources.ResourceLocation;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JSON Schema parser and data classes for terrain type function definitions.
 * <p>
 * 地形类型函数定义的 JSON Schema 解析器与数据类。
 * <p>
 * Each terrain type is defined by a JSON file containing noise primitives,
 * combinators, coordinate transforms, and climate parameters.
 * 每种地形类型由一个 JSON 文件定义，包含噪声原语、组合器、坐标变换和气候参数。
 * <p>
 * Uses Gson for JSON parsing (same library as VoronoiDataPersistence and BiomeMapper).
 * 使用 Gson 进行 JSON 解析（与 VoronoiDataPersistence 和 BiomeMapper 相同的库）。
 *
 * @author World Scape
 */
public final class TerrainFunctionSchema {

    private TerrainFunctionSchema() {
    }

    // ========================================================================
    // Static Factory / 静态工厂方法
    // ========================================================================

    /**
     * Load and parse a terrain type function definition from the given resource location.
     * 从给定的资源位置加载并解析地形类型函数定义。
     * <p>
     * The resource location should point to a JSON file in the data pack,
     * e.g. {@code worldscape:terrain_function/high_mountains}.
     * 资源位置应指向数据包中的 JSON 文件，
     * 例如 {@code worldscape:terrain_function/high_mountains}。
     *
     * @param location the resource location of the JSON definition / JSON 定义的资源位置
     * @return the parsed FunctionDef, or empty if loading/parsing fails / 解析后的 FunctionDef，加载或解析失败时返回 empty
     */
    public static Optional<FunctionDef> load(ResourceLocation location) {
        String resourcePath = "/data/" + location.getNamespace() + "/" + location.getPath() + ".json";

        try (InputStream is = TerrainFunctionSchema.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                WorldScape.LOGGER.error("[World Scape] Terrain function definition not found: {} (path: {})",
                        location, resourcePath);
                return Optional.empty();
            }

            String json;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                json = sb.toString();
            }

            if (json.isEmpty()) {
                WorldScape.LOGGER.error("[World Scape] Empty terrain function definition: {}", location);
                return Optional.empty();
            }

            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            FunctionDef def = parseFunctionDef(root, location);
            if (def == null) {
                return Optional.empty();
            }
            return Optional.of(def);
        } catch (Exception e) {
            WorldScape.LOGGER.error("[World Scape] Failed to load terrain function: {}", location, e);
            return Optional.empty();
        }
    }

    // ========================================================================
    // JSON Parsing Helpers / JSON 解析辅助方法
    // ========================================================================

    /**
     * Load and parse a FunctionDef directly from a JSON object.
     * 直接从 JSON 对象加载并解析 FunctionDef。
     * <p>
     * This is used by TerrainTypeReloadListener to parse inline entries from defaults.json
     * and config directory JSON files without needing a separate classpath resource.
     * 此方法由 TerrainTypeReloadListener 使用，用于解析 defaults.json 中的内联条目
     * 和配置目录中的 JSON 文件，无需单独的类路径资源。
     *
     * @param root     the JSON object containing the function definition / 包含函数定义的 JSON 对象
     * @param sourceId a human-readable identifier for error logging / 用于错误日志的人类可读标识符
     * @return the parsed FunctionDef, or null if parsing fails / 解析后的 FunctionDef，解析失败时返回 null
     */
    public static FunctionDef loadFromJsonObject(JsonObject root, String sourceId) {
        return parseFunctionDef(root, ResourceLocation.parse(sourceId));
    }

    /**
     * Parse the root JSON object into a FunctionDef.
     * 将根 JSON 对象解析为 FunctionDef。
     *
     * @return the parsed FunctionDef, or null if required fields are missing / 解析后的 FunctionDef，缺少必填字段时返回 null
     */
    private static FunctionDef parseFunctionDef(JsonObject root, ResourceLocation location) {
        try {
            if (!root.has("id")) {
                WorldScape.LOGGER.error("[World Scape] Missing required field 'id' in terrain function: {}", location);
                return null;
            }
            String id = root.get("id").getAsString();

            int minHeight = root.has("min_height") ? root.get("min_height").getAsInt() : -64;
            int maxHeight = root.has("max_height") ? root.get("max_height").getAsInt() : 512;

            int[] tierWhitelist = null;
            if (root.has("tier_whitelist") && root.get("tier_whitelist").isJsonArray()) {
                JsonArray arr = root.getAsJsonArray("tier_whitelist");
                tierWhitelist = new int[arr.size()];
                for (int i = 0; i < arr.size(); i++) {
                    tierWhitelist[i] = arr.get(i).getAsInt();
                }
            }

            double heightCap = root.has("height_cap")
                    ? root.get("height_cap").getAsDouble()
                    : Double.MAX_VALUE;

            CoordinateTransform coordinateTransform = null;
            if (root.has("coordinate_transform") && !root.get("coordinate_transform").isJsonNull()) {
                coordinateTransform = parseCoordinateTransform(root.getAsJsonObject("coordinate_transform"));
            }

            List<NoisePrimitive> functions = new ArrayList<>();
            if (root.has("functions") && root.get("functions").isJsonArray()) {
                JsonArray funcsArr = root.getAsJsonArray("functions");
                for (int i = 0; i < funcsArr.size(); i++) {
                    NoisePrimitive np = parseNoisePrimitive(funcsArr.get(i).getAsJsonObject());
                    if (np != null) {
                        functions.add(np);
                    }
                }
            }
            if (functions.isEmpty()) {
                WorldScape.LOGGER.warn("[World Scape] No noise primitives defined in terrain function: {}", location);
            }

            Combinator combinator = null;
            if (root.has("combinator") && !root.get("combinator").isJsonNull()) {
                combinator = parseCombinator(root.getAsJsonObject("combinator"));
            }

            String finalExpr = root.has("final")
                    ? root.get("final").getAsString()
                    : "combined";

            Climate climate = null;
            if (root.has("climate") && !root.get("climate").isJsonNull()) {
                climate = parseClimate(root.getAsJsonObject("climate"));
            }

            return new FunctionDef(id, minHeight, maxHeight, tierWhitelist, heightCap,
                    coordinateTransform, functions, combinator, finalExpr, climate);
        } catch (Exception e) {
            WorldScape.LOGGER.error("[World Scape] Failed to parse terrain function definition: {}", location, e);
            return null;
        }
    }

    /**
     * Parse a NoisePrimitive from a JSON object.
     * 从 JSON 对象解析 NoisePrimitive。
     */
    private static NoisePrimitive parseNoisePrimitive(JsonObject obj) {
        try {
            if (!obj.has("name")) {
                WorldScape.LOGGER.error("[World Scape] Missing required field 'name' in noise primitive");
                return null;
            }
            String name = obj.get("name").getAsString();

            String id = obj.has("id") ? obj.get("id").getAsString() : null;

            Map<String, Object> params = new HashMap<>();
            if (obj.has("params") && obj.get("params").isJsonObject()) {
                JsonObject paramsObj = obj.getAsJsonObject("params");
                for (Map.Entry<String, JsonElement> entry : paramsObj.entrySet()) {
                    params.put(entry.getKey(), jsonElementToObject(entry.getValue()));
                }
            }

            double amplitude = obj.has("amplitude") ? obj.get("amplitude").getAsDouble() : 1.0;

            return new NoisePrimitive(id, name, params, amplitude);
        } catch (Exception e) {
            WorldScape.LOGGER.error("[World Scape] Failed to parse noise primitive", e);
            return null;
        }
    }

    /**
     * Parse a Combinator from a JSON object.
     * 从 JSON 对象解析 Combinator。
     */
    private static Combinator parseCombinator(JsonObject obj) {
        try {
            if (!obj.has("type")) {
                WorldScape.LOGGER.error("[World Scape] Missing required field 'type' in combinator");
                return null;
            }
            String type = obj.get("type").getAsString();

            List<String> terms = null;
            String a = null;
            String b = null;
            double weightA = 0.0;
            String source = null;
            double factor = 0.0;

            switch (type) {
                case "add":
                    if (obj.has("terms") && obj.get("terms").isJsonArray()) {
                        JsonArray termsArr = obj.getAsJsonArray("terms");
                        terms = new ArrayList<>(termsArr.size());
                        for (int i = 0; i < termsArr.size(); i++) {
                            terms.add(termsArr.get(i).getAsString());
                        }
                    }
                    break;

                case "blend":
                    if (obj.has("a")) {
                        a = obj.get("a").getAsString();
                    }
                    if (obj.has("b")) {
                        b = obj.get("b").getAsString();
                    }
                    if (obj.has("weightA")) {
                        weightA = obj.get("weightA").getAsDouble();
                    }
                    break;

                case "product":
                    if (obj.has("a")) {
                        a = obj.get("a").getAsString();
                    }
                    if (obj.has("b")) {
                        b = obj.get("b").getAsString();
                    }
                    break;

                case "scale":
                    if (obj.has("source")) {
                        source = obj.get("source").getAsString();
                    }
                    if (obj.has("factor")) {
                        factor = obj.get("factor").getAsDouble();
                    }
                    break;

                default:
                    WorldScape.LOGGER.warn("[World Scape] Unknown combinator type: {}", type);
                    break;
            }

            return new Combinator(type, terms, a, b, weightA, source, factor);
        } catch (Exception e) {
            WorldScape.LOGGER.error("[World Scape] Failed to parse combinator", e);
            return null;
        }
    }

    /**
     * Parse a CoordinateTransform from a JSON object.
     * 从 JSON 对象解析 CoordinateTransform。
     */
    private static CoordinateTransform parseCoordinateTransform(JsonObject obj) {
        try {
            if (!obj.has("type")) {
                WorldScape.LOGGER.error("[World Scape] Missing required field 'type' in coordinate_transform");
                return null;
            }
            String type = obj.get("type").getAsString();

            Map<String, Object> params = new HashMap<>();
            if (obj.has("params") && obj.get("params").isJsonObject()) {
                JsonObject paramsObj = obj.getAsJsonObject("params");
                for (Map.Entry<String, JsonElement> entry : paramsObj.entrySet()) {
                    params.put(entry.getKey(), jsonElementToObject(entry.getValue()));
                }
            }

            return new CoordinateTransform(type, params);
        } catch (Exception e) {
            WorldScape.LOGGER.error("[World Scape] Failed to parse coordinate transform", e);
            return null;
        }
    }

    /**
     * Parse a Climate from a JSON object.
     * 从 JSON 对象解析 Climate。
     */
    private static Climate parseClimate(JsonObject obj) {
        try {
            double temperature = obj.has("temperature") ? obj.get("temperature").getAsDouble() : 0.5;
            double humidity = obj.has("humidity") ? obj.get("humidity").getAsDouble() : 0.5;
            double seasonality = obj.has("seasonality") ? obj.get("seasonality").getAsDouble() : 0.5;
            double continentality = obj.has("continentality") ? obj.get("continentality").getAsDouble() : 0.5;

            return new Climate(temperature, humidity, seasonality, continentality);
        } catch (Exception e) {
            WorldScape.LOGGER.error("[World Scape] Failed to parse climate", e);
            return null;
        }
    }

    /**
     * Convert a Gson JsonElement to a plain Java Object.
     * 将 Gson JsonElement 转换为普通 Java 对象。
     * <p>
     * Numbers that are whole numbers are returned as Integer,
     * floating-point numbers as Double, booleans as Boolean, strings as String.
     * 整数返回 Integer，浮点数返回 Double，布尔值返回 Boolean，字符串返回 String。
     */
    private static Object jsonElementToObject(JsonElement element) {
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isNumber()) {
                double d = primitive.getAsDouble();
                if (d == Math.floor(d) && !Double.isInfinite(d) && d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE) {
                    return (int) d;
                }
                return d;
            }
            if (primitive.isBoolean()) {
                return primitive.getAsBoolean();
            }
            if (primitive.isString()) {
                return primitive.getAsString();
            }
        }
        return element.toString();
    }

    // ========================================================================
    // Data Classes / 数据类
    // ========================================================================

    /**
     * Complete terrain type function definition.
     * 完整的地形类型函数定义。
     * <p>
     * Contains all parameters needed to generate terrain for a specific terrain type:
     * noise primitives, their combination rules, coordinate transforms, and climate settings.
     * 包含生成特定地形类型所需的所有参数：噪声原语、组合规则、坐标变换和气候设置。
     */
    public static final class FunctionDef {
        /** Unique identifier for this terrain function, e.g. "worldscape:high_mountains" / 地形函数的唯一标识符 */
        public final String id;
        /** Minimum world height this function applies to / 此函数适用的最小世界高度 */
        public final int minHeight;
        /** Maximum world height this function applies to / 此函数适用的最大世界高度 */
        public final int maxHeight;
        /** Elevation tiers this function is allowed on (null = all tiers) / 此函数允许出现的高程等级（null = 所有等级） */
        public final int[] tierWhitelist;
        /** Maximum height cap after applying this function / 应用此函数后的最大高度上限 */
        public final double heightCap;
        /** Optional coordinate-space transformation before noise evaluation / 噪声评估前的可选坐标空间变换 */
        public final CoordinateTransform coordinateTransform;
        /** List of noise primitives that make up the terrain function / 构成地形函数的噪声原语列表 */
        public final List<NoisePrimitive> functions;
        /** How noise primitives are combined (null = no combination, use raw primitives) / 噪声原语的组合方式 */
        public final Combinator combinator;
        /** Final expression string for post-processing / 后处理的最终表达式字符串 */
        public final String finalExpr;
        /** Climate parameters for this terrain type (null = use defaults) / 此地形类型的气候参数 */
        public final Climate climate;

        FunctionDef(String id, int minHeight, int maxHeight, int[] tierWhitelist, double heightCap,
                    CoordinateTransform coordinateTransform, List<NoisePrimitive> functions,
                    Combinator combinator, String finalExpr, Climate climate) {
            this.id = id;
            this.minHeight = minHeight;
            this.maxHeight = maxHeight;
            this.tierWhitelist = tierWhitelist;
            this.heightCap = heightCap;
            this.coordinateTransform = coordinateTransform;
            this.functions = Collections.unmodifiableList(new ArrayList<>(functions));
            this.combinator = combinator;
            this.finalExpr = finalExpr;
            this.climate = climate;
        }

        /**
         * Check if this function is allowed on the given elevation tier.
         * 检查此函数是否允许在给定的高程等级上使用。
         *
         * @param tier the elevation tier to check / 要检查的高程等级
         * @return true if allowed (null whitelist means all tiers are allowed) / 如果允许返回 true（null whitelist 表示允许所有等级）
         */
        public boolean isAllowedOnTier(int tier) {
            if (tierWhitelist == null || tierWhitelist.length == 0) {
                return true;
            }
            for (int t : tierWhitelist) {
                if (t == tier) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String toString() {
            return "FunctionDef{id='" + id + "', functions=" + functions.size()
                    + ", combinator=" + (combinator != null ? combinator.type : "none") + "}";
        }
    }

    /**
     * A single noise primitive (e.g. fBm, domain rotation, turbulence).
     * 单个噪声原语（例如 fBm、域旋转、湍流）。
     * <p>
     * Each primitive has a type name, optional named reference id, parameters map, and amplitude.
     * 每个原语有类型名称、可选命名引用 ID、参数映射和振幅。
     */
    public static final class NoisePrimitive {
        /** Optional named reference id for use in combinators / 可选的命名引用 ID，用于组合器中 */
        public final String id;
        /** Noise type name, e.g. "fbm", "domain_rotated", "turbulence" / 噪声类型名称 */
        public final String name;
        /** Parameters for the noise function (keys depend on noise type) / 噪声函数的参数（键取决于噪声类型） */
        public final Map<String, Object> params;
        /** Amplitude multiplier for this primitive / 此原语的振幅乘数 */
        public final double amplitude;

        NoisePrimitive(String id, String name, Map<String, Object> params, double amplitude) {
            this.id = id;
            this.name = name;
            this.params = Collections.unmodifiableMap(new HashMap<>(params));
            this.amplitude = amplitude;
        }

        @Override
        public String toString() {
            return "NoisePrimitive{id='" + id + "', name='" + name + "', amplitude=" + amplitude + "}";
        }
    }

    /**
     * How multiple noise primitives are combined into a single output.
     * 多个噪声原语如何组合成单个输出。
     * <p>
     * Supported types and their fields:
     * 支持的类型及其字段：
     * <ul>
     *   <li>{@code "add"} — terms (List&lt;String&gt;): additive blend of named references / 命名引用的加性混合</li>
     *   <li>{@code "blend"} — a (String), b (String), weightA (double): weighted blend of two references / 两个引用的加权混合</li>
     *   <li>{@code "product"} — a (String), b (String): multiplicative combination / 乘性组合</li>
     *   <li>{@code "scale"} — source (String), factor (double): scale a single reference by factor / 按因子缩放单个引用</li>
     * </ul>
     */
    public static final class Combinator {
        /** Combinator type: "add", "blend", "product", or "scale" / 组合器类型 */
        public final String type;
        /** For "add": list of named references to sum / 加性组合：要相加的命名引用列表 */
        public final List<String> terms;
        /** For "blend" / "product": first named reference / 混合/乘积：第一个命名引用 */
        public final String a;
        /** For "blend" / "product": second named reference / 混合/乘积：第二个命名引用 */
        public final String b;
        /** For "blend": weight of the first reference (0.0 = all b, 1.0 = all a) / 混合权重：第一个引用的权重 */
        public final double weightA;
        /** For "scale": the named reference to scale / 缩放：要缩放的命名引用 */
        public final String source;
        /** For "scale": the scaling factor / 缩放：缩放因子 */
        public final double factor;

        Combinator(String type, List<String> terms, String a, String b, double weightA,
                   String source, double factor) {
            this.type = type;
            this.terms = terms != null ? Collections.unmodifiableList(new ArrayList<>(terms)) : null;
            this.a = a;
            this.b = b;
            this.weightA = weightA;
            this.source = source;
            this.factor = factor;
        }

        @Override
        public String toString() {
            return "Combinator{type='" + type + "'}";
        }
    }

    /**
     * Coordinate-space transformation applied before noise evaluation.
     * 噪声评估前应用的坐标空间变换。
     * <p>
     * Supported types:
     * 支持的类型：
     * <ul>
     *   <li>{@code "identity"} — no transformation / 不变换</li>
     *   <li>{@code "energy_stretched"} — anisotropic scale matching terrain tectonic energy / 各向异性缩放匹配地形构造能量</li>
     *   <li>{@code "scale"} — uniform scale / 均匀缩放</li>
     * </ul>
     */
    public static final class CoordinateTransform {
        /** Transform type: "identity", "energy_stretched", or "scale" / 变换类型 */
        public final String type;
        /** Parameters for the transform (keys depend on transform type) / 变换参数（键取决于变换类型） */
        public final Map<String, Object> params;

        CoordinateTransform(String type, Map<String, Object> params) {
            this.type = type;
            this.params = Collections.unmodifiableMap(new HashMap<>(params));
        }

        @Override
        public String toString() {
            return "CoordinateTransform{type='" + type + "', params=" + params + "}";
        }
    }

    /**
     * Climate parameters associated with a terrain type.
     * 与地形类型关联的气候参数。
     * <p>
     * These values influence biome selection and ecosystem placement.
     * 这些值影响生物群落选择和生态系统分布。
     */
    public static final class Climate {
        /** Temperature value (0.0 = frozen, 1.0 = hot) / 温度值（0.0 = 冰冻，1.0 = 炎热） */
        public final double temperature;
        /** Humidity value (0.0 = arid, 1.0 = wet) / 湿度值（0.0 = 干旱，1.0 = 湿润） */
        public final double humidity;
        /** Seasonality value (0.0 = stable, 1.0 = extreme seasonal variation) / 季节性值（0.0 = 稳定，1.0 = 极端季节变化） */
        public final double seasonality;
        /** Continentality value (0.0 = oceanic, 1.0 = deep inland) / 大陆性值（0.0 = 海洋性，1.0 = 深层内陆） */
        public final double continentality;

        Climate(double temperature, double humidity, double seasonality, double continentality) {
            this.temperature = temperature;
            this.humidity = humidity;
            this.seasonality = seasonality;
            this.continentality = continentality;
        }

        @Override
        public String toString() {
            return "Climate{t=" + temperature + ", h=" + humidity
                    + ", s=" + seasonality + ", c=" + continentality + "}";
        }
    }
}