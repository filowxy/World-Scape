package com.worldscape.biome;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.worldscape.terrain.MacroRegionInfo;
import com.worldscape.terrain.TerrainType;
import com.worldscape.util.ClimateUtils;
import com.worldscape.util.ClimateProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BiomeMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(BiomeMapper.class);
    private final Map<ResourceLocation, TerrainType> biomeToTerrain = new ConcurrentHashMap<>();
    private final Map<TerrainType, List<ResourceLocation>> terrainToBiomes = new ConcurrentHashMap<>();
    private final double autoMatchThreshold;
    private static final Map<String, TerrainType> BIOME_NAME_OVERRIDES = new ConcurrentHashMap<>();
    private static final Set<String> RIVER_BIOME_NAMES;
    private static final Set<String> OCEAN_BIOME_NAMES;
    private boolean crossValidationDone = false;

    public BiomeMapper(double autoMatchThreshold) {
        this.autoMatchThreshold = autoMatchThreshold;
    }

    public void scanAndMap(Registry<Biome> biomeRegistry) {
        this.biomeToTerrain.clear();
        this.terrainToBiomes.clear();
        for (TerrainType type : TerrainType.values()) {
            this.terrainToBiomes.put(type, new CopyOnWriteArrayList<>());
        }
        biomeRegistry.holders().forEach(holder -> {
            Biome biome = (Biome)holder.value();
            ResourceLocation biomeId = ((ResourceKey)holder.unwrapKey().orElseThrow()).location();
            boolean isOverworld = this.isOverworldBiome((Holder<Biome>)holder);
            if (isOverworld) {
                TerrainType terrainType = this.mapBiomeToTerrain(biome, biomeId);
                this.biomeToTerrain.put(biomeId, terrainType);
                this.terrainToBiomes.computeIfAbsent(terrainType, k -> new CopyOnWriteArrayList<>()).add(biomeId);
            }
        });
        this.runCrossValidation(biomeRegistry);
        this.saveToConfig();
    }

    // @VALIDATION: Cross-validate BiomeMapper mappings against TerrainBiomeRules
    // Logs warnings for any biome→terrain mapping that conflicts with TerrainBiomeRules
    // 交叉验证BiomeMapper映射与TerrainBiomeRules规则，记录冲突警告
    private void runCrossValidation(Registry<Biome> biomeRegistry) {
        boolean rulesInit = TerrainBiomeRules.getInstance().isInitialized();
        if (!rulesInit) {
            try {
                TerrainBiomeRules.getInstance().initialize(biomeRegistry);
            } catch (Exception e) {
                LOGGER.warn("[World Scape] Cannot initialize TerrainBiomeRules for cross-validation");
                return;
            }
        }
        int conflictCount = 0;
        for (Map.Entry<ResourceLocation, TerrainType> entry : this.biomeToTerrain.entrySet()) {
            ResourceLocation biomeId = entry.getKey();
            TerrainType terrainType = entry.getValue();
            if (terrainType == TerrainType.SEA_PLATEAU || terrainType == TerrainType.TRENCH) {
                continue;
            }
            // biomeRegistry.getHolder returns Optional, check via holders() filtering
            boolean biomeAllowed = false;
            try {
                for (var holder : TerrainBiomeRules.getInstance().getAllowedBiomes(terrainType)) {
                    if (holder.unwrapKey().isPresent() && holder.unwrapKey().get().location().equals(biomeId)) {
                        biomeAllowed = true;
                        break;
                    }
                }
            } catch (Exception e) {
                continue;
            }
            if (!biomeAllowed) {
                LOGGER.warn("[World Scape] [VALIDATION] Biome {} mapped to {} but not in TerrainBiomeRules allowed list",
                    biomeId, terrainType.getId());
                conflictCount++;
            }
        }
        if (conflictCount > 0) {
            LOGGER.warn("[World Scape] [VALIDATION] Found {} biome-terrain mapping conflicts. Check terrain_biome_rules.json", conflictCount);
        } else {
            LOGGER.info("[World Scape] [VALIDATION] All biome-terrain mappings pass cross-validation");
        }
        crossValidationDone = true;
    }

    private boolean isOverworldBiome(Holder<Biome> holder) {
        try {
            return holder.is(BiomeTags.IS_OVERWORLD);
        }
        catch (Exception e) {
            ResourceLocation biomeId = ((ResourceKey)holder.unwrapKey().orElseThrow()).location();
            return this.isLikelyOverworld(biomeId);
        }
    }

    private boolean isLikelyOverworld(ResourceLocation biomeId) {
        String ns = biomeId.getNamespace();
        String path = biomeId.getPath().toLowerCase();
        if (ns.equals("minecraft")) {
            return !path.contains("nether") && !path.contains("end") && !path.contains("warped") && !path.contains("crimson") && !path.contains("the_end");
        }
        Set<String> dimensionMods = Set.of("aether", "blue_skies", "twilight_forest", "undergarden", "betweenlands", "voidz", "galacticraft", "ad_astra", "deeperdarker", "nowplaying", "dynamictrees", "the_bumblezone", "never_nether", "another_furniture");
        return !dimensionMods.contains(ns);
    }

    private TerrainType mapBiomeToTerrain(Biome biome, ResourceLocation biomeId) {
        TerrainType override;
        String path = biomeId.getPath().toLowerCase();
        if (biomeId.getNamespace().equals("minecraft") && (override = BIOME_NAME_OVERRIDES.get(path)) != null) {
            return override;
        }
        // 使用连续降水量替代二值化的 hasPrecipitation()，保留湿度粒度，
        // 使丛林/沼泽/森林/雪原等原本湿度同为 1.0 的生物群系能够被区分。
        // 注：Biome 未直接暴露 getDownfall()，通过 ClimateSettings.downfall() 获取；
        // TemperatureModifier 仅影响温度不影响降水量，故 downfall 值与基础一致。
        double temperature = biome.getBaseTemperature();
        double humidity = biome.getModifiedClimateSettings().downfall();
        ClimateProfile biomeProfile = new ClimateProfile(temperature, humidity);
        // 统一标签规则与气候匹配：先以 TerrainBiomeRules 的标签规则约束候选地形集，
        // 再在候选集上做气候距离匹配。规则未加载或无候选时回退到全部地形类型。
        List<TerrainType> candidates = this.getCandidateTerrainTypes(biomeId);
        double minDistance = Double.MAX_VALUE;
        TerrainType bestMatch = TerrainType.PLAINS;
        for (TerrainType type : candidates) {
            // 使用 4 维 distanceTo 计算气候距离（温度、湿度、季节性、大陆性）。
            // 优先使用 JSON 定义的气候；缺失时回退到硬编码气候档案。
            // Compute 4-dimensional climate distance (temperature, humidity, seasonality, continentality).
            // Prefer JSON-defined climate; fall back to hardcoded climate profile if absent.
            ClimateProfile terrainProfile = ClimateUtils.fromFunctionDefClimate(
                    type.getFunctionDef() != null ? type.getFunctionDef().climate : null,
                    type.name());
            double distance = terrainProfile.distanceTo(biomeProfile);
            if (!(distance < minDistance)) continue;
            minDistance = distance;
            bestMatch = type;
        }
        if (minDistance >= this.autoMatchThreshold) {
            // 地理上合理的回退，替代原先将寒冷生物群系统一映射为 RIDGE（tier 5 山脊）的不合理逻辑。
            return this.getClimateFallbackTerrain(temperature, humidity);
        }
        return bestMatch;
    }

    /**
     * 获取该生物群系的候选地形类型集合。
     * 当 TerrainBiomeRules 已初始化时，返回所有在标签规则下允许该生物群系的地形类型，
     * 使气候匹配在标签约束的候选集上进行，统一两套并行映射系统。
     * 当规则未初始化或该生物群系未被任何地形规则允许时，回退到全部地形类型。
     */
    private List<TerrainType> getCandidateTerrainTypes(ResourceLocation biomeId) {
        if (!TerrainBiomeRules.getInstance().isInitialized()) {
            return Arrays.asList(TerrainType.values());
        }
        List<TerrainType> allowed = new ArrayList<>();
        for (TerrainType type : TerrainType.values()) {
            if (this.isBiomeAllowedForTerrain(type, biomeId)) {
                allowed.add(type);
            }
        }
        return allowed.isEmpty() ? Arrays.asList(TerrainType.values()) : allowed;
    }

    /**
     * 通过 TerrainBiomeRules 的标签白名单/黑名单判断生物群系是否被该地形类型允许。
     * 按 ResourceLocation 比较，避免 Holder 引用相等的不确定性（与 runCrossValidation 一致）。
     */
    private boolean isBiomeAllowedForTerrain(TerrainType type, ResourceLocation biomeId) {
        try {
            for (Holder<Biome> holder : TerrainBiomeRules.getInstance().getAllowedBiomes(type)) {
                if (holder.unwrapKey().isPresent() && holder.unwrapKey().get().location().equals(biomeId)) {
                    return true;
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    /**
     * 基于气候的合理回退：寒冷→冰川谷，炎热干旱→沙丘，其余→平原。
     * 替代原先将所有寒冷生物群系映射为 RIDGE（tier 5 山脊）的不合理逻辑。
     */
    private TerrainType getClimateFallbackTerrain(double temperature, double humidity) {
        if (temperature < 0.2) {
            return TerrainType.GLACIAL_VALLEY;
        }
        if (temperature > 0.7 && humidity < 0.3) {
            return TerrainType.DUNE;
        }
        return TerrainType.PLAINS;
    }

    public TerrainType getTerrainForBiome(ResourceLocation biomeId) {
        return this.biomeToTerrain.getOrDefault(biomeId, TerrainType.PLAINS);
    }

    public List<ResourceLocation> getBiomesForTerrain(TerrainType terrain) {
        return this.terrainToBiomes.getOrDefault((Object)terrain, Collections.emptyList());
    }

    public ResourceLocation selectBiomeForTerrain(TerrainType terrain, int x, int z, long seed, MacroRegionInfo.ClimateZone climate, MacroRegionInfo.TectonicType tectonic, int elevationTier, boolean isRiver) {
        List<ResourceLocation> highElevationCandidates;
        List<ResourceLocation> candidates = this.getBiomesForTerrain(terrain);
        if (candidates.isEmpty()) {
            return this.getFallbackBiome(climate, elevationTier, isRiver);
        }
        if (this.isHighElevationTerrain(terrain) && !(highElevationCandidates = this.filterHighElevationBiomes(candidates)).isEmpty()) {
            candidates = highElevationCandidates;
        }
        long hash = seed ^ (long)x * 31L + (long)z * 17L;
        if (isRiver) {
            return this.selectRiverBiome(climate, elevationTier, candidates, hash);
        }
        return candidates.get(Math.abs((int)(hash % (long)candidates.size())));
    }

    private boolean isHighElevationTerrain(TerrainType terrain) {
        return terrain == TerrainType.HIGH_MOUNTAINS || terrain == TerrainType.CLIFF;
    }

    private List<ResourceLocation> filterHighElevationBiomes(List<ResourceLocation> biomes) {
        ArrayList<ResourceLocation> filtered = new ArrayList<ResourceLocation>();
        Set<String> highElevationNames = Set.of("snowy_mountains", "stony_peaks", "mountains", "gravelly_mountains", "wooded_mountains");
        for (ResourceLocation biome : biomes) {
            String path = biome.getPath().toLowerCase();
            if (!highElevationNames.contains(path)) continue;
            filtered.add(biome);
        }
        return filtered;
    }

    private ResourceLocation selectRiverBiome(MacroRegionInfo.ClimateZone climate, int elevationTier, List<ResourceLocation> candidates, long hash) {
        for (ResourceLocation biome : candidates) {
            if (!RIVER_BIOME_NAMES.contains(biome.getPath())) continue;
            if (climate == MacroRegionInfo.ClimateZone.GLACIAL || elevationTier <= 1) {
                if (!biome.getPath().contains("frozen")) continue;
            } else if (biome.getPath().contains("frozen")) continue;
            return biome;
        }
        if (climate == MacroRegionInfo.ClimateZone.GLACIAL) {
            return ResourceLocation.fromNamespaceAndPath((String)"minecraft", (String)"frozen_river");
        }
        return ResourceLocation.fromNamespaceAndPath((String)"minecraft", (String)"river");
    }

    private ResourceLocation getFallbackBiome(MacroRegionInfo.ClimateZone climate, int elevationTier, boolean isRiver) {
        if (isRiver) {
            if (climate == MacroRegionInfo.ClimateZone.GLACIAL) {
                return ResourceLocation.fromNamespaceAndPath((String)"minecraft", (String)"frozen_river");
            }
            return ResourceLocation.fromNamespaceAndPath((String)"minecraft", (String)"river");
        }
        if (elevationTier <= 1) {
            if (climate == MacroRegionInfo.ClimateZone.TROPICAL) {
                return ResourceLocation.fromNamespaceAndPath((String)"minecraft", (String)"warm_ocean");
            }
            if (climate == MacroRegionInfo.ClimateZone.GLACIAL) {
                return ResourceLocation.fromNamespaceAndPath((String)"minecraft", (String)"frozen_ocean");
            }
            return ResourceLocation.fromNamespaceAndPath((String)"minecraft", (String)"ocean");
        }
        if (elevationTier >= 5) {
            if (climate == MacroRegionInfo.ClimateZone.GLACIAL) {
                return ResourceLocation.fromNamespaceAndPath((String)"minecraft", (String)"snowy_mountains");
            }
            if (climate == MacroRegionInfo.ClimateZone.ARID) {
                return ResourceLocation.fromNamespaceAndPath((String)"minecraft", (String)"mountains");
            }
            return ResourceLocation.fromNamespaceAndPath((String)"minecraft", (String)"mountains");
        }
        if (climate == MacroRegionInfo.ClimateZone.ARID) {
            return ResourceLocation.fromNamespaceAndPath((String)"minecraft", (String)"desert");
        }
        if (climate == MacroRegionInfo.ClimateZone.TROPICAL) {
            return ResourceLocation.fromNamespaceAndPath((String)"minecraft", (String)"jungle");
        }
        if (climate == MacroRegionInfo.ClimateZone.GLACIAL) {
            return ResourceLocation.fromNamespaceAndPath((String)"minecraft", (String)"snowy_plains");
        }
        return ResourceLocation.fromNamespaceAndPath((String)"minecraft", (String)"plains");
    }

    public boolean isRiverBiome(ResourceLocation biomeId) {
        return RIVER_BIOME_NAMES.contains(biomeId.getPath());
    }

    public boolean isOceanBiome(ResourceLocation biomeId) {
        return OCEAN_BIOME_NAMES.contains(biomeId.getPath());
    }

    public void loadFromConfig() {
        Path configPath = Paths.get("config", "worldscape", "landscape.json");
        if (!Files.exists(configPath, new LinkOption[0])) {
            return;
        }
        try {
            String json = Files.readString(configPath);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            this.biomeToTerrain.clear();
            this.terrainToBiomes.clear();
            for (TerrainType type : TerrainType.values()) {
                this.terrainToBiomes.put(type, new CopyOnWriteArrayList<>());
            }

            if (root.has("biome_to_terrain")) {
                JsonObject btt = root.getAsJsonObject("biome_to_terrain");
                for (Map.Entry<String, JsonElement> entry : btt.entrySet()) {
                    ResourceLocation biomeId = ResourceLocation.parse(entry.getKey());
                    TerrainType terrain = TerrainType.getById(entry.getValue().getAsString());
                    if (terrain != null) {
                        this.biomeToTerrain.put(biomeId, terrain);
                        this.terrainToBiomes.computeIfAbsent(terrain, k -> new CopyOnWriteArrayList<>()).add(biomeId);
                    }
                }
            }

            if (root.has("terrain_to_biomes")) {
                JsonObject ttb = root.getAsJsonObject("terrain_to_biomes");
                for (Map.Entry<String, JsonElement> entry : ttb.entrySet()) {
                    TerrainType terrain = TerrainType.getById(entry.getKey());
                    if (terrain == null) continue;
                    JsonArray biomeArray = entry.getValue().getAsJsonArray();
                    List<ResourceLocation> biomes = new CopyOnWriteArrayList<>();
                    for (JsonElement elem : biomeArray) {
                        biomes.add(ResourceLocation.parse(elem.getAsString()));
                    }
                    this.terrainToBiomes.put(terrain, biomes);
                }
            }
        } catch (IOException | JsonSyntaxException | IllegalStateException e) {
            // Fixed: was e.printStackTrace() — must use LOGGER per AGENTS.md §3.3
            // 修复：原为 e.printStackTrace() — 按 AGENTS.md §3.3 必须使用 LOGGER
            LOGGER.error("[BiomeMapper] Failed to load config from file", e);
        }
    }

    public void saveToConfig() {
        Path configDir = Paths.get("config", "worldscape");
        try {
            Files.createDirectories(configDir);
            Path configPath = configDir.resolve("landscape.json");

            JsonObject root = new JsonObject();

            JsonObject biomeToTerrainJson = new JsonObject();
            for (Map.Entry<ResourceLocation, TerrainType> entry : this.biomeToTerrain.entrySet()) {
                // Save terrain id with namespace so TerrainType.getById() can resolve it correctly.
                // 保存带命名空间的地形 id，以便 TerrainType.getById() 正确解析。
                biomeToTerrainJson.addProperty(entry.getKey().toString(), entry.getValue().getFullId());
            }
            root.add("biome_to_terrain", biomeToTerrainJson);

            JsonObject terrainToBiomesJson = new JsonObject();
            for (Map.Entry<TerrainType, List<ResourceLocation>> entry : this.terrainToBiomes.entrySet()) {
                JsonArray biomeArray = new JsonArray();
                for (ResourceLocation biomeId : entry.getValue()) {
                    biomeArray.add(biomeId.toString());
                }
                // Save terrain id with namespace so TerrainType.getById() can resolve it correctly.
                // 保存带命名空间的地形 id，以便 TerrainType.getById() 正确解析。
                terrainToBiomesJson.add(entry.getKey().getFullId(), biomeArray);
            }
            root.add("terrain_to_biomes", terrainToBiomesJson);

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(configPath, gson.toJson(root), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
        catch (IOException e) {
            // Fixed: was e.printStackTrace() — must use LOGGER per AGENTS.md §3.3
            // 修复：原为 e.printStackTrace() — 按 AGENTS.md §3.3 必须使用 LOGGER
            LOGGER.error("[BiomeMapper] Failed to save config to file", e);
        }
    }

    static {
        BIOME_NAME_OVERRIDES.put("desert", TerrainType.DUNE);
        BIOME_NAME_OVERRIDES.put("badlands", TerrainType.DOME);
        BIOME_NAME_OVERRIDES.put("wooded_badlands", TerrainType.DOME);
        BIOME_NAME_OVERRIDES.put("eroded_badlands", TerrainType.RIDGE);
        BIOME_NAME_OVERRIDES.put("mountains", TerrainType.RIDGE);
        BIOME_NAME_OVERRIDES.put("wooded_mountains", TerrainType.RIDGE);
        BIOME_NAME_OVERRIDES.put("gravelly_mountains", TerrainType.RIDGE);
        BIOME_NAME_OVERRIDES.put("snowy_mountains", TerrainType.HIGH_MOUNTAINS);
        BIOME_NAME_OVERRIDES.put("stony_peaks", TerrainType.HIGH_MOUNTAINS);
        BIOME_NAME_OVERRIDES.put("taiga", TerrainType.HILLS);
        BIOME_NAME_OVERRIDES.put("snowy_taiga", TerrainType.GLACIAL_VALLEY);
        BIOME_NAME_OVERRIDES.put("taiga_mountains", TerrainType.HILLS);
        BIOME_NAME_OVERRIDES.put("plains", TerrainType.PLAINS);
        BIOME_NAME_OVERRIDES.put("sunflower_plains", TerrainType.PLAINS);
        BIOME_NAME_OVERRIDES.put("forest", TerrainType.PLAINS);
        BIOME_NAME_OVERRIDES.put("flower_forest", TerrainType.PLAINS);
        BIOME_NAME_OVERRIDES.put("birch_forest", TerrainType.PLAINS);
        BIOME_NAME_OVERRIDES.put("birch_forest_hills", TerrainType.HILLS);
        BIOME_NAME_OVERRIDES.put("dark_forest", TerrainType.PLAINS);
        BIOME_NAME_OVERRIDES.put("river", TerrainType.DELTA);
        BIOME_NAME_OVERRIDES.put("frozen_river", TerrainType.GLACIAL_VALLEY);
        BIOME_NAME_OVERRIDES.put("beach", TerrainType.BEACH);
        BIOME_NAME_OVERRIDES.put("snowy_beach", TerrainType.BEACH);
        BIOME_NAME_OVERRIDES.put("stone_shore", TerrainType.SEA_CLIFF);
        BIOME_NAME_OVERRIDES.put("swamp", TerrainType.BASIN);
        BIOME_NAME_OVERRIDES.put("mangrove_swamp", TerrainType.BASIN);
        BIOME_NAME_OVERRIDES.put("jungle", TerrainType.PLAINS);
        BIOME_NAME_OVERRIDES.put("jungle_edge", TerrainType.PLAINS);
        BIOME_NAME_OVERRIDES.put("bamboo_jungle", TerrainType.PLAINS);
        BIOME_NAME_OVERRIDES.put("savanna", TerrainType.PLAINS);
        BIOME_NAME_OVERRIDES.put("savanna_plateau", TerrainType.PLATEAU);
        BIOME_NAME_OVERRIDES.put("ice_spikes", TerrainType.GLACIAL_VALLEY);
        BIOME_NAME_OVERRIDES.put("snowy_tundra", TerrainType.ICE_SHEET);
        BIOME_NAME_OVERRIDES.put("ocean", TerrainType.SEA_PLATEAU);
        BIOME_NAME_OVERRIDES.put("deep_ocean", TerrainType.TRENCH);
        BIOME_NAME_OVERRIDES.put("frozen_ocean", TerrainType.TRENCH);
        BIOME_NAME_OVERRIDES.put("warm_ocean", TerrainType.SEA_PLATEAU);
        BIOME_NAME_OVERRIDES.put("lukewarm_ocean", TerrainType.SEA_PLATEAU);
        BIOME_NAME_OVERRIDES.put("cold_ocean", TerrainType.SEA_PLATEAU);
        BIOME_NAME_OVERRIDES.put("deep_warm_ocean", TerrainType.TRENCH);
        BIOME_NAME_OVERRIDES.put("deep_lukewarm_ocean", TerrainType.TRENCH);
        BIOME_NAME_OVERRIDES.put("deep_cold_ocean", TerrainType.TRENCH);
        BIOME_NAME_OVERRIDES.put("deep_frozen_ocean", TerrainType.TRENCH);
        BIOME_NAME_OVERRIDES.put("mushroom_fields", TerrainType.FLOODPLAIN);
        BIOME_NAME_OVERRIDES.put("the_end", TerrainType.PLATEAU);
        BIOME_NAME_OVERRIDES.put("small_end_islands", TerrainType.PLATEAU);
        BIOME_NAME_OVERRIDES.put("end_midlands", TerrainType.PLATEAU);
        BIOME_NAME_OVERRIDES.put("end_highlands", TerrainType.RIDGE);
        BIOME_NAME_OVERRIDES.put("end_barrens", TerrainType.PLATEAU);
        RIVER_BIOME_NAMES = Set.of("river", "frozen_river");
        OCEAN_BIOME_NAMES = Set.of("ocean", "deep_ocean", "warm_ocean", "lukewarm_ocean", "cold_ocean", "frozen_ocean", "deep_warm_ocean", "deep_lukewarm_ocean", "deep_cold_ocean", "deep_frozen_ocean");
    }
}

