package com.worldscape.terrain;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.worldscape.config.ConfigManager;
import com.worldscape.WorldScape;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * TerrainTypeReloadListener - Auto-loading system for terrain type JSON definitions.
 * <p>
 * TerrainTypeReloadListener - 地形类型 JSON 定义的自动加载系统。
 * <p>
 * Handles loading terrain type function definitions from three sources with priority:
 * 从三个来源加载地形类型函数定义，按优先级排列：
 * <ol>
 *   <li>Built-in defaults: {@code data/worldscape/worldscape/terrain_type/defaults.json} (lowest priority / 最低优先级)</li>
 *   <li>Data packs: {@code data/<namespace>/worldscape/terrain_type/*.json} (medium priority / 中等优先级)</li>
 *   <li>Config directory: defined in ConfigManager (highest priority / 最高优先级)</li>
 * </ol>
 * <p>
 * Each terrain type entry is parsed into a {@link FunctionDef}
 * and associated with the corresponding {@link TerrainType} via {@link TerrainType#setFunctionDef}.
 * 每个地形类型条目被解析为 {@link FunctionDef}，
 * 并通过 {@link TerrainType#setFunctionDef} 关联到对应的 {@link TerrainType}。
 */
public final class TerrainTypeReloadListener {

    /**
     * Classpath resource path for the built-in defaults JSON file.
     * 内置默认 JSON 文件的类路径资源路径。
     */
    private static final String DEFAULTS_RESOURCE_PATH = "/data/worldscape/worldscape/terrain_type/defaults.json";

    /**
     * JSON key for the terrain types array in defaults.json.
     * defaults.json 中 terrain_types 数组的 JSON 键。
     */
    private static final String TERRAIN_TYPES_KEY = "terrain_types";

    /**
     * JSON key for the terrain type id field.
     * 地形类型 id 字段的 JSON 键。
     */
    private static final String ID_KEY = "id";

    /**
     * JSON file extension filter.
     * JSON 文件扩展名过滤器。
     */
    private static final String JSON_EXTENSION = ".json";

    private TerrainTypeReloadListener() {
        throw new UnsupportedOperationException("Utility class - do not instantiate / 工具类 - 不可实例化");
    }

    // ========================================================================
    // Public API / 公共 API
    // ========================================================================

    /**
     * Load built-in defaults.json from classpath.
     * 从类路径加载内置的 defaults.json。
     * <p>
     * Reads the {@code defaults.json} file bundled in the mod jar,
     * parses the {@code terrain_types} JSON array, and sets the function
     * definition on each corresponding TerrainType.
     * 读取打包在模组 jar 中的 {@code defaults.json} 文件，
     * 解析 {@code terrain_types} JSON 数组，并在每个对应的 TerrainType 上设置函数定义。
     *
     * @return the number of terrain types loaded / 加载的地形类型数量
     */
    public static int loadDefaults() {
        WorldScape.LOGGER.info("[World Scape] Loading built-in terrain type defaults...");
        // [World Scape] 加载内置地形类型默认值...

        JsonObject defaultsRoot;
        try (InputStream is = TerrainTypeReloadListener.class.getResourceAsStream(DEFAULTS_RESOURCE_PATH)) {
            if (is == null) {
                WorldScape.LOGGER.error("[World Scape] Built-in defaults.json not found at: {}",
                        DEFAULTS_RESOURCE_PATH);
                return 0;
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
                WorldScape.LOGGER.error("[World Scape] Built-in defaults.json is empty");
                return 0;
            }

            defaultsRoot = JsonParser.parseString(json).getAsJsonObject();
        } catch (IOException e) {
            WorldScape.LOGGER.error("[World Scape] Failed to read built-in defaults.json", e);
            return 0;
        } catch (Exception e) {
            WorldScape.LOGGER.error("[World Scape] Failed to parse built-in defaults.json", e);
            return 0;
        }

        if (!defaultsRoot.has(TERRAIN_TYPES_KEY) || !defaultsRoot.get(TERRAIN_TYPES_KEY).isJsonArray()) {
            WorldScape.LOGGER.error("[World Scape] Built-in defaults.json missing '{}' array", TERRAIN_TYPES_KEY);
            return 0;
        }

        JsonArray terrainTypesArray = defaultsRoot.getAsJsonArray(TERRAIN_TYPES_KEY);
        int loadedCount = 0;

        for (JsonElement element : terrainTypesArray) {
            if (!element.isJsonObject()) {
                WorldScape.LOGGER.warn("[World Scape] Skipping non-object entry in defaults.json terrain_types array");
                continue;
            }

            JsonObject typeObj = element.getAsJsonObject();
            if (!typeObj.has(ID_KEY)) {
                WorldScape.LOGGER.warn("[World Scape] Skipping defaults entry without 'id' field");
                continue;
            }

            String typeId = typeObj.get(ID_KEY).getAsString();
            if (loadAndApplyFunctionDef(typeObj, typeId, "defaults.json", false)) {
                loadedCount++;
            }
        }

        WorldScape.LOGGER.info("[World Scape] Loaded {} terrain type(s) from built-in defaults", loadedCount);
        return loadedCount;
    }

    /**
     * Load terrain type JSON definitions from a config directory.
     * 从配置目录加载地形类型 JSON 定义。
     * <p>
     * Iterates over all {@code *.json} files in the given directory,
     * parses each as a {@link FunctionDef},
     * and overrides the corresponding TerrainType's function definition.
     * 遍历给定目录中的所有 {@code *.json} 文件，
     * 将每个文件解析为 {@link FunctionDef}，
     * 并覆盖对应 TerrainType 的函数定义。
     * <p>
     * Files in this directory have the highest priority and will
     * override definitions from defaults and data packs.
     * 此目录中的文件具有最高优先级，将覆盖默认值和数据包中的定义。
     *
     * @param configDir the path to the config directory containing JSON files / 包含 JSON 文件的配置目录路径
     * @return the number of terrain types loaded / 加载的地形类型数量
     */
    public static int loadFromConfigDir(Path configDir) {
        if (configDir == null) {
            WorldScape.LOGGER.debug("[World Scape] No config directory specified for terrain types");
            return 0;
        }

        if (!Files.exists(configDir)) {
            WorldScape.LOGGER.debug("[World Scape] Terrain type config directory does not exist: {}", configDir);
            return 0;
        }

        if (!Files.isDirectory(configDir)) {
            WorldScape.LOGGER.warn("[World Scape] Terrain type config path is not a directory: {}", configDir);
            return 0;
        }

        WorldScape.LOGGER.info("[World Scape] Loading terrain type definitions from config: {}", configDir);
        int loadedCount = 0;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(configDir,
                path -> path.toString().endsWith(JSON_EXTENSION))) {

            for (Path jsonFile : stream) {
                loadedCount += loadSingleJsonFile(jsonFile, configDir);
            }
        } catch (IOException e) {
            WorldScape.LOGGER.error("[World Scape] Failed to read config directory: {}", configDir, e);
        }

        WorldScape.LOGGER.info("[World Scape] Loaded {} terrain type(s) from config directory", loadedCount);
        return loadedCount;
    }

    /**
     * Load all terrain type definitions: defaults first, then config overrides.
     * 加载所有地形类型定义：先加载默认值，再加载配置覆盖。
     * <p>
     * This method loads built-in defaults and then applies any config directory
     * overrides if a config directory is configured in ConfigManager.
     * 此方法加载内置默认值，然后应用配置管理器中的任何配置目录覆盖。
     * <p>
     * This should be called during mod construction and also on server starting.
     * 应在模组构造时以及服务器启动时调用此方法。
     */
    public static void loadAll() {
        int defaultsCount = loadDefaults();

        String configDirPath = ConfigManager.getTerrainTypeConfigDirStatic();
        if (configDirPath != null && !configDirPath.isEmpty()) {
            Path gameDir = getGameDirectory();
            Path configDir = gameDir.resolve(configDirPath);
            loadFromConfigDir(configDir);
        }

        WorldScape.LOGGER.info("[World Scape] Terrain type auto-loading complete. "
                + "Defaults: {}, Config overrides: {}", defaultsCount,
                configDirPath != null && !configDirPath.isEmpty() ? "enabled" : "disabled");
    }

    // ========================================================================
    // Internal Helpers / 内部辅助方法
    // ========================================================================

    /**
     * Load a single JSON file from the config directory and apply it.
     * 从配置目录加载单个 JSON 文件并应用。
     *
     * @param jsonFile  the path to the JSON file / JSON 文件的路径
     * @param configDir the parent config directory (for relative path display) / 父配置目录（用于显示相对路径）
     * @return 1 if loaded successfully, 0 otherwise / 成功加载返回 1，否则返回 0
     */
    private static int loadSingleJsonFile(Path jsonFile, Path configDir) {
        try {
            String fileContent = Files.readString(jsonFile, StandardCharsets.UTF_8);

            if (fileContent.trim().isEmpty()) {
                WorldScape.LOGGER.warn("[World Scape] Skipping empty config file: {}", jsonFile.getFileName());
                return 0;
            }

            JsonObject root;
            try {
                root = JsonParser.parseString(fileContent).getAsJsonObject();
            } catch (Exception e) {
                WorldScape.LOGGER.error("[World Scape] Failed to parse config JSON: {}", jsonFile.getFileName(), e);
                return 0;
            }

            if (!root.has(ID_KEY)) {
                WorldScape.LOGGER.warn("[World Scape] Skipping config file without 'id' field: {}",
                        jsonFile.getFileName());
                return 0;
            }

            String typeId = root.get(ID_KEY).getAsString();
            if (loadAndApplyFunctionDef(root, typeId, jsonFile.getFileName().toString(), true)) {
                return 1;
            }

        } catch (IOException e) {
            WorldScape.LOGGER.error("[World Scape] Failed to read config file: {}", jsonFile.getFileName(), e);
        }

        return 0;
    }

    /**
     * Parse a JSON object into a FunctionDef and apply it to the matching TerrainType.
     * 将 JSON 对象解析为 FunctionDef 并应用到匹配的 TerrainType。
     *
     * @param typeObj      the JSON object representing a terrain type definition / 表示地形类型定义的 JSON 对象
     * @param typeId       the terrain type id (e.g. "worldscape:high_mountains") / 地形类型 id
     * @param sourceName   human-readable source name for logging / 用于日志的人类可读来源名称
     * @param isOverride   true if loading from config (logs WARN for overrides), false for defaults / 从配置加载时为 true（覆盖时记录 WARN），默认值为 false
     * @return true if loaded and applied successfully / 加载并应用成功返回 true
     */
    private static boolean loadAndApplyFunctionDef(JsonObject typeObj, String typeId,
                                                    String sourceName, boolean isOverride) {
        FunctionDef functionDef =
                TerrainFunctionSchema.loadFromJsonObject(typeObj, typeId);

        if (functionDef == null) {
            WorldScape.LOGGER.error("[World Scape] Failed to parse terrain type definition for '{}' from {}",
                    typeId, sourceName);
            return false;
        }

        ResourceLocation key = ResourceLocation.parse(typeId);
        TerrainType terrainType = TerrainTypeRegistry.get(key);

        if (terrainType == null) {
            WorldScape.LOGGER.warn("[World Scape] Unknown terrain type '{}' in {}, skipping", typeId, sourceName);
            return false;
        }

        FunctionDef existingDef = terrainType.getFunctionDef();

        if (isOverride && existingDef != null) {
            WorldScape.LOGGER.warn("[World Scape] Overriding terrain type: {} from config", typeId);
        }

        terrainType.setFunctionDef(functionDef);

        WorldScape.LOGGER.info("[World Scape] Loaded terrain type: {}", typeId);

        return true;
    }

    /**
     * Attempt to get the game directory from the Minecraft instance.
     * 尝试从 Minecraft 实例获取游戏目录。
     * <p>
     * On the client, uses {@code Minecraft.getInstance().gameDirectory}.
     * On the server, falls back to the current working directory.
     * 在客户端使用 {@code Minecraft.getInstance().gameDirectory}。
     * 在服务端回退到当前工作目录。
     *
     * @return the game directory path / 游戏目录路径
     */
    private static Path getGameDirectory() {
        // Use NeoForge's FMLPaths to get the game directory, which works on both client and server.
        // 使用 NeoForge 的 FMLPaths 获取游戏目录，在客户端和服务端均可正常工作。
        return FMLPaths.GAMEDIR.get();
    }
}