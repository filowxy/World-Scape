/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.Holder
 *  net.minecraft.core.Registry
 *  net.minecraft.resources.ResourceKey
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.tags.BiomeTags
 *  net.minecraft.world.level.biome.Biome
 */
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;

public class BiomeMapper {
    private final Map<ResourceLocation, TerrainType> biomeToTerrain = new HashMap<ResourceLocation, TerrainType>();
    private final Map<TerrainType, List<ResourceLocation>> terrainToBiomes = new HashMap<TerrainType, List<ResourceLocation>>();
    private final double autoMatchThreshold;
    private static final Map<String, TerrainType> BIOME_NAME_OVERRIDES = new HashMap<String, TerrainType>();
    private static final Set<String> RIVER_BIOME_NAMES;
    private static final Set<String> OCEAN_BIOME_NAMES;

    public BiomeMapper(double autoMatchThreshold) {
        this.autoMatchThreshold = autoMatchThreshold;
    }

    public void scanAndMap(Registry<Biome> biomeRegistry) {
        this.biomeToTerrain.clear();
        this.terrainToBiomes.clear();
        for (TerrainType type : TerrainType.values()) {
            this.terrainToBiomes.put(type, new ArrayList());
        }
        biomeRegistry.holders().forEach(holder -> {
            Biome biome = (Biome)holder.value();
            ResourceLocation biomeId = ((ResourceKey)holder.unwrapKey().orElseThrow()).location();
            boolean isOverworld = this.isOverworldBiome((Holder<Biome>)holder);
            if (isOverworld) {
                TerrainType terrainType = this.mapBiomeToTerrain(biome, biomeId);
                this.biomeToTerrain.put(biomeId, terrainType);
                this.terrainToBiomes.computeIfAbsent(terrainType, k -> new ArrayList()).add(biomeId);
            }
        });
        this.saveToConfig();
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
        double temperature = biome.getBaseTemperature();
        double humidity = biome.hasPrecipitation() ? 1.0 : 0.0;
        double minDistance = Double.MAX_VALUE;
        TerrainType bestMatch = TerrainType.PLAINS;
        for (TerrainType type : TerrainType.values()) {
            double distance = this.calculateClimateDistance(type, temperature, humidity);
            if (!(distance < minDistance)) continue;
            minDistance = distance;
            bestMatch = type;
        }
        if (minDistance >= this.autoMatchThreshold) {
            if (temperature > 0.5) {
                return TerrainType.PLAINS;
            }
            return TerrainType.RIDGE;
        }
        return bestMatch;
    }

    private double calculateClimateDistance(TerrainType terrain, double temperature, double humidity) {
        ClimateUtils.ClimateProfile terrainProfile = ClimateUtils.getTerrainClimateProfile(terrain.name());
        ClimateUtils.ClimateProfile biomeProfile = new ClimateUtils.ClimateProfile(temperature, humidity);
        return terrainProfile.distanceTo(biomeProfile);
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
            return ResourceLocation.fromNamespaceAndPath((String)"minecraft", (String)"snowy_tundra");
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
                this.terrainToBiomes.put(type, new ArrayList());
            }

            if (root.has("biome_to_terrain")) {
                JsonObject btt = root.getAsJsonObject("biome_to_terrain");
                for (Map.Entry<String, JsonElement> entry : btt.entrySet()) {
                    ResourceLocation biomeId = ResourceLocation.parse(entry.getKey());
                    TerrainType terrain = TerrainType.getById(entry.getValue().getAsString());
                    if (terrain != null) {
                        this.biomeToTerrain.put(biomeId, terrain);
                        this.terrainToBiomes.computeIfAbsent(terrain, k -> new ArrayList()).add(biomeId);
                    }
                }
            }

            if (root.has("terrain_to_biomes")) {
                JsonObject ttb = root.getAsJsonObject("terrain_to_biomes");
                for (Map.Entry<String, JsonElement> entry : ttb.entrySet()) {
                    TerrainType terrain = TerrainType.getById(entry.getKey());
                    if (terrain == null) continue;
                    JsonArray biomeArray = entry.getValue().getAsJsonArray();
                    List<ResourceLocation> biomes = new ArrayList<>();
                    for (JsonElement elem : biomeArray) {
                        biomes.add(ResourceLocation.parse(elem.getAsString()));
                    }
                    this.terrainToBiomes.put(terrain, biomes);
                }
            }
        } catch (IOException | JsonSyntaxException | IllegalStateException e) {
            e.printStackTrace();
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
                biomeToTerrainJson.addProperty(entry.getKey().toString(), entry.getValue().getId());
            }
            root.add("biome_to_terrain", biomeToTerrainJson);

            JsonObject terrainToBiomesJson = new JsonObject();
            for (Map.Entry<TerrainType, List<ResourceLocation>> entry : this.terrainToBiomes.entrySet()) {
                JsonArray biomeArray = new JsonArray();
                for (ResourceLocation biomeId : entry.getValue()) {
                    biomeArray.add(biomeId.toString());
                }
                terrainToBiomesJson.add(entry.getKey().getId(), biomeArray);
            }
            root.add("terrain_to_biomes", terrainToBiomesJson);

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(configPath, gson.toJson(root), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
        catch (IOException e) {
            e.printStackTrace();
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
        BIOME_NAME_OVERRIDES.put("snowy_mountains", TerrainType.RIDGE);
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

