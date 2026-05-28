/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.JsonElement
 *  com.google.gson.JsonObject
 *  com.google.gson.JsonParser
 *  com.mojang.logging.LogUtils
 *  net.minecraft.core.Holder
 *  net.minecraft.core.Registry
 *  net.minecraft.core.registries.Registries
 *  net.minecraft.resources.ResourceKey
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.tags.TagKey
 *  net.minecraft.world.level.biome.Biome
 *  org.slf4j.Logger
 */
package com.worldscape.biome;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.worldscape.terrain.TerrainType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import org.slf4j.Logger;

public class TerrainBiomeRules {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String CONFIG_FILE = "config/worldscape/terrain_biome_rules.json";
    private final Map<TerrainType, TerrainBiomeRule> rules = new EnumMap<TerrainType, TerrainBiomeRule>(TerrainType.class);
    private final Map<String, Set<Holder<Biome>>> tagCache = new ConcurrentHashMap<String, Set<Holder<Biome>>>();
    private Registry<Biome> biomeRegistry;
    private final Map<TerrainType, List<Holder<Biome>>> allowedBiomesCache = new EnumMap<TerrainType, List<Holder<Biome>>>(TerrainType.class);
    private final Map<TerrainType, Set<Holder<Biome>>> excludedBiomesCache = new EnumMap<TerrainType, Set<Holder<Biome>>>(TerrainType.class);
    private boolean defaultUseWhitelist = false;
    private boolean autoScan = true;
    private double confidenceThreshold = 0.3;
    private static TerrainBiomeRules INSTANCE;

    public static synchronized TerrainBiomeRules getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new TerrainBiomeRules();
        }
        return INSTANCE;
    }

    private TerrainBiomeRules() {
    }

    public synchronized void initialize(Registry<Biome> biomeRegistry) {
        this.biomeRegistry = biomeRegistry;
        LOGGER.info("[World Scape] Initializing TerrainBiomeRules...");
        this.loadDefaultRules();
        this.loadUserConfig();
        this.expandAllTags();
        LOGGER.info("[World Scape] TerrainBiomeRules initialized with {} terrain rules", (Object)this.rules.size());
    }

    private void loadDefaultRules() {
        this.addRule(TerrainType.PLAINS, false, List.of(), List.of("#minecraft:is_ocean", "#minecraft:is_river"));
        this.addRule(TerrainType.BEACH, true, List.of(), List.of("#minecraft:is_beach"));
        this.addRule(TerrainType.DUNE, false, List.of(), List.of("#minecraft:is_ocean", "#minecraft:is_river", "#minecraft:is_forest", "#minecraft:is_swamp", "#minecraft:is_jungle", "#minecraft:is_taiga"));
        this.addRule(TerrainType.SEA_PLATEAU, true, List.of(), List.of("#minecraft:is_ocean"));
        this.addRule(TerrainType.HILLS, false, List.of(), List.of("#minecraft:is_ocean", "#minecraft:is_river", "#minecraft:is_badlands"));
        this.addRule(TerrainType.HIGH_MOUNTAINS, false, List.of(), List.of("#minecraft:is_ocean", "#minecraft:is_river", "#minecraft:is_badlands", "#minecraft:is_swamp"));
        this.addRule(TerrainType.PLATEAU, true, List.of(), List.of("#minecraft:is_mountain", "#minecraft:is_hill"));
        this.addRule(TerrainType.CANYON, false, List.of(), List.of("#minecraft:is_ocean", "#minecraft:is_river", "#minecraft:is_forest"));
        this.addRule(TerrainType.GLACIAL_VALLEY, true, List.of(), List.of("#minecraft:is_taiga", "minecraft:snowy_taiga"));
        this.addRule(TerrainType.ICE_SHEET, true, List.of(), List.of("minecraft:snowy_tundra", "minecraft:snowy_taiga", "minecraft:frozen_ocean"));
        this.addRule(TerrainType.DELTA, true, List.of(), List.of("#minecraft:is_river", "#minecraft:is_ocean"));
        this.addRule(TerrainType.FLOODPLAIN, false, List.of(), List.of("#minecraft:is_ocean", "#minecraft:is_mountain"));
        this.addRule(TerrainType.GOBI, false, List.of(), List.of("#minecraft:is_ocean", "#minecraft:is_river", "#minecraft:is_forest", "#minecraft:is_swamp", "#minecraft:is_jungle"));
        this.addRule(TerrainType.YARDANG, false, List.of(), List.of("#minecraft:is_ocean", "#minecraft:is_river", "#minecraft:is_forest"));
        this.addRule(TerrainType.SALT_FLAT, true, List.of(), List.of("#minecraft:is_badlands", "#minecraft:is_savanna"));
        this.addRule(TerrainType.SEA_CLIFF, true, List.of(), List.of("#minecraft:is_beach", "#minecraft:is_ocean"));
        this.addRule(TerrainType.FJORD, true, List.of(), List.of("#minecraft:is_ocean", "#minecraft:is_river", "minecraft:frozen_ocean"));
        this.addRule(TerrainType.PEAK_FOREST, false, List.of(), List.of("#minecraft:is_ocean", "#minecraft:is_river", "#minecraft:is_badlands"));
        this.addRule(TerrainType.SINKHOLE, false, List.of(), List.of("#minecraft:is_ocean"));
        this.addRule(TerrainType.BASIN, false, List.of(), List.of("#minecraft:is_ocean", "#minecraft:is_mountain"));
        this.addRule(TerrainType.DOME, true, List.of(), List.of("#minecraft:is_mountain", "#minecraft:is_hill"));
        this.addRule(TerrainType.TRENCH, true, List.of(), List.of("#minecraft:is_ocean", "minecraft:deep_ocean"));
        this.addRule(TerrainType.VALLEY, false, List.of(), List.of("#minecraft:is_ocean", "#minecraft:is_badlands"));
        this.addRule(TerrainType.RIDGE, true, List.of(), List.of("#minecraft:is_mountain"));
        this.addRule(TerrainType.PEAK, true, List.of(), List.of("#minecraft:is_mountain"));
        this.addRule(TerrainType.ALLUVIAL_FAN, false, List.of(), List.of("#minecraft:is_ocean"));
        this.addRule(TerrainType.CIRQUE, true, List.of(), List.of("minecraft:snowy_tundra", "minecraft:snowy_taiga"));
        this.addRule(TerrainType.HORN, true, List.of(), List.of("#minecraft:is_mountain", "minecraft:snowy_taiga"));
        this.addRule(TerrainType.CLIFF, true, List.of(), List.of("#minecraft:is_mountain"));
    }

    private void addRule(TerrainType terrain, boolean useWhitelist, List<String> biomeIds, List<String> tagPaths) {
        this.rules.put(terrain, new TerrainBiomeRule(useWhitelist, biomeIds, tagPaths));
    }

    private void loadUserConfig() {
        try {
            Path configPath = Paths.get(CONFIG_FILE, new String[0]);
            if (!Files.exists(configPath, new LinkOption[0])) {
                this.createDefaultConfig(configPath);
                return;
            }
            String json = Files.readString(configPath);
            JsonObject root = JsonParser.parseString((String)json).getAsJsonObject();
            if (root.has("global")) {
                JsonObject global = root.getAsJsonObject("global");
                if (global.has("default_use_whitelist")) {
                    this.defaultUseWhitelist = global.get("default_use_whitelist").getAsBoolean();
                }
                if (global.has("auto_scan")) {
                    this.autoScan = global.get("auto_scan").getAsBoolean();
                }
                if (global.has("confidence_threshold")) {
                    this.confidenceThreshold = global.get("confidence_threshold").getAsDouble();
                }
            }
            if (root.has("terrain_biome_rules")) {
                JsonObject terrainRules = root.getAsJsonObject("terrain_biome_rules");
                for (Map.Entry entry : terrainRules.entrySet()) {
                    try {
                        TerrainType terrain = TerrainType.valueOf(((String)entry.getKey()).toUpperCase().replace("-", "_"));
                        JsonObject ruleJson = ((JsonElement)entry.getValue()).getAsJsonObject();
                        if (ruleJson.has("enabled") && !ruleJson.get("enabled").getAsBoolean()) continue;
                        boolean useWhitelist = ruleJson.has("use_whitelist") && ruleJson.get("use_whitelist").getAsBoolean();
                        ArrayList<String> biomeIds = new ArrayList<String>();
                        if (ruleJson.has("biomes")) {
                            for (JsonElement el : ruleJson.getAsJsonArray("biomes")) {
                                biomeIds.add(el.getAsString());
                            }
                        }
                        ArrayList<String> tagPaths = new ArrayList<String>();
                        if (ruleJson.has("tags")) {
                            for (JsonElement el : ruleJson.getAsJsonArray("tags")) {
                                tagPaths.add(el.getAsString());
                            }
                        }
                        this.rules.put(terrain, new TerrainBiomeRule(useWhitelist, biomeIds, tagPaths));
                    }
                    catch (IllegalArgumentException e) {
                        LOGGER.warn("[World Scape] Unknown terrain type in config: {}", entry.getKey());
                    }
                }
            }
            LOGGER.info("[World Scape] Loaded user terrain biome rules from {}", (Object)CONFIG_FILE);
        }
        catch (IOException | RuntimeException e) {
            LOGGER.warn("[World Scape] Failed to load user terrain biome rules, using defaults", (Throwable)e);
        }
    }

    private void createDefaultConfig(Path configPath) throws IOException {
        Files.createDirectories(configPath.getParent(), new FileAttribute[0]);
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  // \u5730\u5f62-\u7fa4\u7cfb\u89c4\u5219\u914d\u7f6e\n");
        sb.append("  // Terrain-Biome Rules Configuration\n\n");
        sb.append("  terrain_biome_rules: {\n");
        boolean first = true;
        for (Map.Entry<TerrainType, TerrainBiomeRule> entry : this.rules.entrySet()) {
            int i;
            if (!first) {
                sb.append(",\n");
            }
            first = false;
            sb.append("    \"").append(entry.getKey().getId()).append("\": {\n");
            sb.append("      use_whitelist: ").append(entry.getValue().useWhitelist).append(",\n");
            sb.append("      biomes: [");
            for (i = 0; i < entry.getValue().biomeIds.size(); ++i) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append("\"").append(entry.getValue().biomeIds.get(i)).append("\"");
            }
            sb.append("],\n");
            sb.append("      tags: [");
            for (i = 0; i < entry.getValue().tagPaths.size(); ++i) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append("\"").append(entry.getValue().tagPaths.get(i)).append("\"");
            }
            sb.append("]\n");
            sb.append("    }");
        }
        sb.append("\n  },\n\n");
        sb.append("  global: {\n");
        sb.append("    default_use_whitelist: false,\n");
        sb.append("    auto_scan: true,\n");
        sb.append("    confidence_threshold: 0.3\n");
        sb.append("  }\n");
        sb.append("}\n");
        Files.writeString(configPath, (CharSequence)sb.toString(), new OpenOption[0]);
        LOGGER.info("[World Scape] Created default terrain biome rules config at {}", (Object)configPath);
    }

    private void expandAllTags() {
        if (this.biomeRegistry == null) {
            LOGGER.warn("[World Scape] Biome registry not set, cannot expand tags");
            return;
        }
        HashSet<String> allTagPaths = new HashSet<String>();
        for (TerrainBiomeRule terrainBiomeRule : this.rules.values()) {
            allTagPaths.addAll(terrainBiomeRule.tagPaths);
        }
        for (String string : allTagPaths) {
            if (!string.startsWith("#")) continue;
            String tagId = string.substring(1);
            try {
                ResourceLocation rl = ResourceLocation.parse((String)tagId);
                TagKey tagKey = TagKey.create((ResourceKey)Registries.BIOME, (ResourceLocation)rl);
                HashSet biomes = new HashSet();
                this.biomeRegistry.getTag(tagKey).ifPresent(tag -> tag.stream().forEach(biomes::add));
                this.tagCache.put(string, biomes);
                LOGGER.debug("[World Scape] Expanded tag {} \u2192 {} biomes", (Object)string, (Object)biomes.size());
            }
            catch (Exception e) {
                LOGGER.warn("[World Scape] Failed to expand tag: {}", (Object)string, (Object)e);
                this.tagCache.put(string, Set.of());
            }
        }
        for (Map.Entry entry : this.rules.entrySet()) {
            TerrainType terrain = (TerrainType)((Object)entry.getKey());
            TerrainBiomeRule rule = (TerrainBiomeRule)entry.getValue();
            HashSet allowed = new HashSet();
            HashSet excluded = new HashSet();
            for (String tagPath : rule.tagPaths) {
                Set tagBiomes = this.tagCache.getOrDefault(tagPath, Set.of());
                if (rule.useWhitelist) {
                    allowed.addAll(tagBiomes);
                    continue;
                }
                excluded.addAll(tagBiomes);
            }
            for (String biomeId : rule.biomeIds) {
                try {
                    ResourceLocation rl = ResourceLocation.parse((String)biomeId);
                    this.biomeRegistry.getHolder(rl).ifPresent(rule.useWhitelist ? allowed::add : excluded::add);
                }
                catch (Exception e) {
                    LOGGER.warn("[World Scape] Invalid biome ID: {}", (Object)biomeId);
                }
            }
            if (rule.useWhitelist) {
                this.allowedBiomesCache.put(terrain, Collections.unmodifiableList(new ArrayList(allowed)));
                this.excludedBiomesCache.put(terrain, Collections.emptySet());
                continue;
            }
            ArrayList allowedList = new ArrayList();
            this.biomeRegistry.holders().forEach(holder -> {
                if (!excluded.contains(holder)) {
                    allowedList.add(holder);
                }
            });
            this.allowedBiomesCache.put(terrain, Collections.unmodifiableList(allowedList));
            this.excludedBiomesCache.put(terrain, Collections.unmodifiableSet(Set.copyOf(excluded)));
        }
        LOGGER.info("[World Scape] Pre-computed allowed biomes for {} terrain types", (Object)this.allowedBiomesCache.size());
    }

    public List<Holder<Biome>> getAllowedBiomes(TerrainType terrain) {
        if (this.biomeRegistry == null) {
            return Collections.emptyList();
        }
        List<Holder<Biome>> cachedAllowed = this.allowedBiomesCache.get((Object)terrain);
        if (cachedAllowed != null && !cachedAllowed.isEmpty()) {
            return cachedAllowed;
        }
        ArrayList<Holder<Biome>> allBiomes = new ArrayList<Holder<Biome>>();
        this.biomeRegistry.holders().forEach(allBiomes::add);
        return allBiomes;
    }

    public boolean isBiomeAllowed(TerrainType terrain, Holder<Biome> biome) {
        List<Holder<Biome>> allowed = this.getAllowedBiomes(terrain);
        return allowed.contains(biome);
    }

    public Holder<Biome> selectBiomeBySeed(List<Holder<Biome>> allowedBiomes, long worldSeed, int cellX, int cellZ) {
        if (allowedBiomes.isEmpty()) {
            return null;
        }
        long hash = worldSeed ^ (long)cellX * 31L + (long)cellZ * 17L;
        int index = Math.abs((int)(hash % (long)allowedBiomes.size()));
        return allowedBiomes.get(index);
    }

    public static class TerrainBiomeRule {
        public final boolean useWhitelist;
        public final List<String> biomeIds;
        public final List<String> tagPaths;

        public TerrainBiomeRule(boolean useWhitelist, List<String> biomeIds, List<String> tagPaths) {
            this.useWhitelist = useWhitelist;
            this.biomeIds = biomeIds;
            this.tagPaths = tagPaths;
        }
    }
}

