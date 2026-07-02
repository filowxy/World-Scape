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

            int minHeight = root.has("min_height") && !root.get("min_height").isJsonNull()
                    ? root.get("min_height").getAsInt() : -64;
            int maxHeight = root.has("max_height") && !root.get("max_height").isJsonNull()
                    ? root.get("max_height").getAsInt() : 512;

            int[] tierWhitelist = null;
            if (root.has("tier_whitelist") && root.get("tier_whitelist").isJsonArray()) {
                JsonArray arr = root.getAsJsonArray("tier_whitelist");
                tierWhitelist = new int[arr.size()];
                for (int i = 0; i < arr.size(); i++) {
                    tierWhitelist[i] = arr.get(i).getAsInt();
                }
            }

            double heightCap = root.has("height_cap") && !root.get("height_cap").isJsonNull()
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
    // Data Classes — migrated to Kotlin, see src/main/kotlin/com/worldscape/terrain/
    // FunctionDef.kt, NoisePrimitive.kt, Combinator.kt, CoordinateTransform.kt, Climate.kt
    // ========================================================================
}