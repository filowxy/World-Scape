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
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.worldscape.compat;

import com.worldscape.terrain.TerrainType;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TerraBlenderCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger(TerraBlenderCompat.class);
    private static final boolean TERRA_BLENDER_EXISTS;
    private static Class<?> regionClass;
    private static Method getBiomesMethod;
    private static boolean collectionSuccessful;
    private final Map<TerrainType, List<ResourceLocation>> terrainToBiomes = new HashMap<TerrainType, List<ResourceLocation>>();
    private final Registry<Biome> biomeRegistry;
    private final List<ResourceLocation> allOverworldBiomes = new ArrayList<ResourceLocation>();

    public TerraBlenderCompat(Registry<Biome> biomeRegistry) {
        this.biomeRegistry = biomeRegistry;
        if (TERRA_BLENDER_EXISTS) {
            this.init();
        }
    }

    public void init() {
        if (!TERRA_BLENDER_EXISTS) {
            LOGGER.info("[TerraBlender Compat] TerraBlender not available, using vanilla biomes only");
            return;
        }
        LOGGER.info("[TerraBlender Compat] Initializing TerraBlender compatibility");
        this.collectTerraBlenderBiomes();
        for (TerrainType type : TerrainType.values()) {
            this.terrainToBiomes.put(type, this.getBiomesForTerrain(type));
        }
    }

    private void collectTerraBlenderBiomes() {
        try {
            LOGGER.debug("[TerraBlender Compat] Starting to collect TerraBlender biomes");
            Method getRegionsMethod = regionClass.getDeclaredMethod("getRegions", new Class[0]);
            Collection regions = (Collection)getRegionsMethod.invoke(null, new Object[0]);
            LOGGER.debug("[TerraBlender Compat] Found {} TerraBlender regions", (Object)regions.size());
            HashSet<ResourceLocation> seenBiomes = new HashSet<ResourceLocation>();
            for (Object region : regions) {
                List<?> biomes = (List<?>)getBiomesMethod.invoke(region, new Object[0]);
                for (Holder<Biome> biome : (List<Holder<Biome>>)biomes) {
                    ResourceLocation biomeId;
                    if (!this.isOverworldBiome((Holder<Biome>)biome) || seenBiomes.contains(biomeId = ((ResourceKey)biome.unwrapKey().orElseThrow()).location())) continue;
                    this.allOverworldBiomes.add(biomeId);
                    seenBiomes.add(biomeId);
                }
            }
            collectionSuccessful = true;
            LOGGER.info("[TerraBlender Compat] Successfully collected {} overworld biomes from TerraBlender", (Object)this.allOverworldBiomes.size());
        }
        catch (NoSuchMethodException e) {
            LOGGER.error("[TerraBlender Compat] Failed to find getRegions() method. TerraBlender API may have changed.", (Throwable)e);
            LOGGER.warn("[TerraBlender Compat] Falling back to vanilla biomes only");
        }
        catch (Exception e) {
            LOGGER.error("[TerraBlender Compat] Failed to collect TerraBlender biomes due to unexpected error", (Throwable)e);
            LOGGER.warn("[TerraBlender Compat] Falling back to vanilla biomes only");
        }
    }

    private List<ResourceLocation> getBiomesForTerrain(TerrainType terrain) {
        ArrayList<ResourceLocation> biomes = new ArrayList<ResourceLocation>();
        biomes.addAll(this.allOverworldBiomes);
        this.addBiomesForTerrainShape(terrain, biomes);
        return biomes;
    }

    private void addBiomesForTerrainShape(TerrainType terrain, List<ResourceLocation> biomes) {
        HashSet<ResourceLocation> existingBiomes = new HashSet<ResourceLocation>(biomes);
        int avgHeight = (terrain.getMinHeight() + terrain.getMaxHeight()) / 2;
        if (avgHeight > 300) {
            this.addBiomeIfNotExists(existingBiomes, biomes, "minecraft:mountain");
            this.addBiomeIfNotExists(existingBiomes, biomes, "minecraft:groves");
            this.addBiomeIfNotExists(existingBiomes, biomes, "minecraft:meadow");
            this.addBiomeIfNotExists(existingBiomes, biomes, "minecraft:taiga");
            this.addBiomeIfNotExists(existingBiomes, biomes, "minecraft:snowy_slopes");
            this.addBiomeIfNotExists(existingBiomes, biomes, "minecraft:jagged_peaks");
            this.addBiomeIfNotExists(existingBiomes, biomes, "minecraft:frozen_peaks");
        } else if (avgHeight > 100) {
            this.addBiomeIfNotExists(existingBiomes, biomes, "minecraft:forest");
            this.addBiomeIfNotExists(existingBiomes, biomes, "minecraft:birch_forest");
            this.addBiomeIfNotExists(existingBiomes, biomes, "minecraft:dark_forest");
            this.addBiomeIfNotExists(existingBiomes, biomes, "minecraft:windswept_hills");
            this.addBiomeIfNotExists(existingBiomes, biomes, "minecraft:windswept_forest");
        } else if (avgHeight >= 0) {
            this.addBiomeIfNotExists(existingBiomes, biomes, "minecraft:plains");
            this.addBiomeIfNotExists(existingBiomes, biomes, "minecraft:sunflower_plains");
            this.addBiomeIfNotExists(existingBiomes, biomes, "minecraft:flower_forest");
            this.addBiomeIfNotExists(existingBiomes, biomes, "minecraft:swamp");
            this.addBiomeIfNotExists(existingBiomes, biomes, "minecraft:mangrove_swamp");
        }
        if (this.isAridTerrain(terrain)) {
            this.addBiomeIfNotExists(existingBiomes, biomes, "minecraft:desert");
            this.addBiomeIfNotExists(existingBiomes, biomes, "minecraft:badlands");
            this.addBiomeIfNotExists(existingBiomes, biomes, "minecraft:windswept_savanna");
        }
        if (this.isCoastalTerrain(terrain)) {
            this.addBiomeIfNotExists(existingBiomes, biomes, "minecraft:beach");
            this.addBiomeIfNotExists(existingBiomes, biomes, "minecraft:stony_shore");
            this.addBiomeIfNotExists(existingBiomes, biomes, "minecraft:snowy_beach");
        }
    }

    private boolean isAridTerrain(TerrainType terrain) {
        return switch (terrain) {
            case TerrainType.DUNE, TerrainType.GOBI, TerrainType.YARDANG, TerrainType.SALT_FLAT -> true;
            default -> false;
        };
    }

    private boolean isCoastalTerrain(TerrainType terrain) {
        return switch (terrain) {
            case TerrainType.BEACH, TerrainType.SEA_CLIFF, TerrainType.FJORD, TerrainType.DELTA, TerrainType.SEA_PLATEAU -> true;
            default -> false;
        };
    }

    private void addBiomeIfNotExists(Set<ResourceLocation> existing, List<ResourceLocation> biomes, String biomeId) {
        ResourceLocation id = ResourceLocation.tryParse((String)biomeId);
        if (id != null && this.biomeRegistry.containsKey(id) && !existing.contains(id)) {
            biomes.add(id);
        }
    }

    public ResourceLocation selectBiome(TerrainType terrain, int x, int z, long seed) {
        if (!TERRA_BLENDER_EXISTS) {
            return null;
        }
        List<ResourceLocation> biomes = this.terrainToBiomes.get((Object)terrain);
        if (biomes == null || biomes.isEmpty()) {
            return null;
        }
        long hash = seed ^ (long)x * 31L + (long)z * 17L;
        int index = Math.abs((int)(hash % (long)biomes.size()));
        return biomes.get(index);
    }

    public boolean isPresent() {
        return TERRA_BLENDER_EXISTS;
    }

    public String generateDiagnosticReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== WorldScape TerraBlender Compatibility Diagnostic ===\n");
        report.append("TerraBlender Present: ").append(TERRA_BLENDER_EXISTS).append("\n");
        report.append("Collection Successful: ").append(collectionSuccessful).append("\n");
        report.append("Total Overworld Biomes: ").append(this.allOverworldBiomes.size()).append("\n");
        if (!this.allOverworldBiomes.isEmpty()) {
            report.append("Biome List: ").append(this.allOverworldBiomes).append("\n");
        }
        report.append("========================================================\n");
        return report.toString();
    }

    private boolean isOverworldBiome(Holder<Biome> biome) {
        try {
            return biome.is(BiomeTags.IS_OVERWORLD);
        }
        catch (Exception e) {
            ResourceLocation biomeId = ((ResourceKey)biome.unwrapKey().orElseThrow()).location();
            String path = biomeId.getPath().toLowerCase();
            return !path.contains("nether") && !path.contains("end") && !path.contains("warped") && !path.contains("crimson") && !path.contains("the_end");
        }
    }

    static {
        collectionSuccessful = false;
        boolean exists = false;
        try {
            regionClass = Class.forName("terrablender.api.Region");
            getBiomesMethod = regionClass.getMethod("getBiomes", new Class[0]);
            exists = true;
            LOGGER.info("[TerraBlender Compat] TerraBlender detected and API loaded successfully");
        }
        catch (ClassNotFoundException e) {
            LOGGER.debug("[TerraBlender Compat] TerraBlender not found in classpath");
        }
        catch (NoSuchMethodException e) {
            LOGGER.warn("[TerraBlender Compat] TerraBlender API method not found: getBiomes(). Version may be incompatible.", (Throwable)e);
        }
        TERRA_BLENDER_EXISTS = exists;
    }
}

