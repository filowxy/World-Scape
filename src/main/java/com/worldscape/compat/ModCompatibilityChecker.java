/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.neoforged.fml.ModList
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.worldscape.compat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModCompatibilityChecker {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModCompatibilityChecker.class);
    private static final Map<String, String> INCOMPATIBLE_MODS = new LinkedHashMap<String, String>();
    private static final Map<String, String> CONFLICT_MODS;
    private static final List<String> VERIFIED_COMPATIBLE;
    private static List<String> detectedIncompatibleMods;
    private static List<String> detectedConflictMods;
    private static boolean checked;

    private static void validateClassifications() {
        for (String modId : INCOMPATIBLE_MODS.keySet()) {
            if (!CONFLICT_MODS.containsKey(modId)) continue;
            LOGGER.error("[World Scape] CLASSIFICATION ERROR: '{}' is in BOTH INCOMPATIBLE and CONFLICT lists!", (Object)modId);
        }
        for (String modId : VERIFIED_COMPATIBLE) {
            if (INCOMPATIBLE_MODS.containsKey(modId)) {
                LOGGER.error("[World Scape] CLASSIFICATION ERROR: '{}' is in VERIFIED_COMPATIBLE but also INCOMPATIBLE!", (Object)modId);
            }
            if (!CONFLICT_MODS.containsKey(modId)) continue;
            LOGGER.error("[World Scape] CLASSIFICATION ERROR: '{}' is in VERIFIED_COMPATIBLE but also CONFLICT!", (Object)modId);
        }
    }

    public static void checkCompatibility() {
        String modId;
        if (checked) {
            return;
        }
        detectedIncompatibleMods.clear();
        detectedConflictMods.clear();
        ModCompatibilityChecker.validateClassifications();
        ModList modList = ModList.get();
        for (Map.Entry<String, String> entry : INCOMPATIBLE_MODS.entrySet()) {
            modId = entry.getKey();
            if (!modList.isLoaded(modId)) continue;
            detectedIncompatibleMods.add(modId);
            LOGGER.warn("[World Scape] INCOMPATIBLE mod detected: {} \u2014 {}", (Object)modId, (Object)entry.getValue());
        }
        for (Map.Entry<String, String> entry : CONFLICT_MODS.entrySet()) {
            modId = entry.getKey();
            if (!modList.isLoaded(modId)) continue;
            detectedConflictMods.add(modId);
            LOGGER.warn("[World Scape] CONFLICT mod detected: {} \u2014 {}", (Object)modId, (Object)entry.getValue());
        }
        checked = true;
        if (!detectedIncompatibleMods.isEmpty() || !detectedConflictMods.isEmpty()) {
            LOGGER.warn("[World Scape] Compatibility scan complete: {} INCOMPATIBLE, {} CONFLICT mods found", (Object)detectedIncompatibleMods.size(), (Object)detectedConflictMods.size());
        } else {
            LOGGER.info("[World Scape] Compatibility scan passed");
        }
    }

    public static boolean isC2MELoaded() {
        return ModList.get().isLoaded("c2me");
    }

    public static boolean isDistHorizLoaded() {
        return ModList.get().isLoaded("distanthorizons");
    }

    public static String getIncompatibleReason(String modId) {
        return INCOMPATIBLE_MODS.getOrDefault(modId, "Unknown reason");
    }

    public static String getConflictReason(String modId) {
        return CONFLICT_MODS.getOrDefault(modId, "Unknown reason");
    }

    public static boolean hasIncompatibleMods() {
        if (!checked) {
            ModCompatibilityChecker.checkCompatibility();
        }
        return !detectedIncompatibleMods.isEmpty();
    }

    public static boolean hasConflictMods() {
        if (!checked) {
            ModCompatibilityChecker.checkCompatibility();
        }
        return !detectedConflictMods.isEmpty();
    }

    public static boolean hasAnyIssues() {
        return ModCompatibilityChecker.hasIncompatibleMods() || ModCompatibilityChecker.hasConflictMods();
    }

    public static List<String> getIncompatibleMods() {
        if (!checked) {
            ModCompatibilityChecker.checkCompatibility();
        }
        return new ArrayList<String>(detectedIncompatibleMods);
    }

    public static List<String> getConflictMods() {
        if (!checked) {
            ModCompatibilityChecker.checkCompatibility();
        }
        return new ArrayList<String>(detectedConflictMods);
    }

    public static String formatModName(String modId) {
        String[] parts = modId.split("_");
        StringBuilder name = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (name.length() > 0) {
                name.append(" ");
            }
            name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return name.toString();
    }

    public static String getWarningMessage() {
        String mod;
        int i;
        if (!checked) {
            ModCompatibilityChecker.checkCompatibility();
        }
        StringBuilder message = new StringBuilder();
        if (!detectedIncompatibleMods.isEmpty()) {
            message.append("=== INCOMPATIBLE Mods Detected ===\n");
            message.append("The following mods may cause crashes or world generation failure:\n\n");
            for (i = 0; i < detectedIncompatibleMods.size(); ++i) {
                mod = detectedIncompatibleMods.get(i);
                message.append("  ").append(i + 1).append(". ").append(ModCompatibilityChecker.formatModName(mod)).append(" (").append(mod).append(")\n");
                message.append("     Reason: ").append(ModCompatibilityChecker.getIncompatibleReason(mod)).append("\n\n");
            }
        }
        if (!detectedConflictMods.isEmpty()) {
            message.append("=== CONFLICT Mods Detected ===\n");
            message.append("The following mods may cause visual or topological issues:\n\n");
            for (i = 0; i < detectedConflictMods.size(); ++i) {
                mod = detectedConflictMods.get(i);
                message.append("  ").append(i + 1).append(". ").append(ModCompatibilityChecker.formatModName(mod)).append(" (").append(mod).append(")\n");
                message.append("     Reason: ").append(ModCompatibilityChecker.getConflictReason(mod)).append("\n\n");
            }
        }
        if (detectedConflictMods.contains("c2me")) {
            message.append("  [C2ME Special Note]: ").append("WS riverCache uses ThreadLocal for thread isolation. ").append("C2ME parallel fillFromNoise may still cause BlendCache contention.\n\n");
        }
        if (detectedConflictMods.contains("distanthorizons")) {
            message.append("  [Distant Horizons Special Note]: ").append("Set distantGeneratorMode=\"INTERNAL_SERVER\" as workaround. ").append("WS regionController expects ServerLevel environment.\n\n");
        }
        return message.toString();
    }

    public static int getIncompatibleCount() {
        return INCOMPATIBLE_MODS.size();
    }

    public static int getConflictCount() {
        return CONFLICT_MODS.size();
    }

    public static int getVerifiedCompatibleCount() {
        return VERIFIED_COMPATIBLE.size();
    }

    static {
        INCOMPATIBLE_MODS.put("terraincontrol", "Registers custom ChunkGenerator \u2014 mutual exclusion");
        INCOMPATIBLE_MODS.put("amplify", "Replaces OverworldChunkGenerator and NetherChunkGenerator");
        INCOMPATIBLE_MODS.put("terraforged", "Registers custom ChunkGenerator for 1.21");
        INCOMPATIBLE_MODS.put("openworlds", "Provides custom WorldPreset with custom ChunkGenerator");
        INCOMPATIBLE_MODS.put("bigglobe", "Registers custom ChunkGenerator \u2014 mutual exclusion");
        INCOMPATIBLE_MODS.put("amplified_nether", "Replaces NetherChunkGenerator \u2014 mutual exclusion for Nether");
        INCOMPATIBLE_MODS.put("worldpainter", "Directly modifies ChunkAccess BlockState, overrides WS fillColumn");
        INCOMPATIBLE_MODS.put("william_wythers_overhauled_overworld", "Replaces BiomeSource via OverworldBiomeProvider \u2014 WS biome override conflicts");
        INCOMPATIBLE_MODS.put("cubicchunks", "Non-standard vertical chunk structure \u2014 WS assumes 16x384x16");
        INCOMPATIBLE_MODS.put("cubicworldgen", "Same as CubicChunks \u2014 incompatible chunk height model");
        CONFLICT_MODS = new LinkedHashMap<String, String>();
        CONFLICT_MODS.put("c2me", "Parallelizes fillFromNoise \u2014 WS riverCache/BlendCache not thread-safe tested");
        CONFLICT_MODS.put("modernfix", "Surface rules caching may affect WS ReflectionSurfaceAdapter timing");
        CONFLICT_MODS.put("distanthorizons", "Uses DhLitWorldGenRegion instead of ServerLevel \u2014 WS regionController expects ServerLevel");
        CONFLICT_MODS.put("biomesoplenty", "Adds ~90 custom biomes \u2014 WS TerrainBiomeRules may not correctly map BoP biomes to terrain types");
        CONFLICT_MODS.put("regions_unexplored", "Adds ~60 overworld biomes \u2014 WS default biome tags may not cover R-U biomes");
        CONFLICT_MODS.put("byg", "Oh The Biomes You'll Go \u2014 adds many biomes, WS auto-scan may misclassify non-overworld biomes");
        CONFLICT_MODS.put("natures_spirit", "Adds forest/jungle variant biomes \u2014 WS PLAINS blacklist may not exclude NS biomes");
        CONFLICT_MODS.put("terrablender", "MixinNoiseGeneratorSettings injects surface rules \u2014 may affect WS ReflectionSurfaceAdapter");
        CONFLICT_MODS.put("yungsbettercaves", "AquiferMixin modifies cave liquid gen \u2014 WS applyCarvers is no-op, caves won't generate");
        CONFLICT_MODS.put("bettercaves", "Same as YUNG's Better Caves \u2014 carver system conflict");
        CONFLICT_MODS.put("betterterrain", "Modifies surface block placement \u2014 may double-place with WS FallbackSurfaceAdapter");
        CONFLICT_MODS.put("william_wythers_expanded_ecosphere", "Modifies biome decorations \u2014 decorations may appear at wrong heights due to WS terrain override");
        CONFLICT_MODS.put("valhelsia_structures", "Adds structures dependent on vanilla terrain height \u2014 may generate incorrectly on WS steep terrain");
        CONFLICT_MODS.put("quark", "Comprehensive mod with worldgen features \u2014 MixinChunkGenerator injection may fail on WS custom ChunkGenerator");
        CONFLICT_MODS.put("charm", "Comprehensive mod \u2014 some worldgen features may conflict with WS biome override");
        CONFLICT_MODS.put("litematica", "Schematic mod \u2014 does NOT modify world generation; previously misclassified as INCOMPATIBLE");
        CONFLICT_MODS.put("structure_gel", "Structure API library \u2014 does NOT modify world generation; previously misclassified as INCOMPATIBLE");
        VERIFIED_COMPATIBLE = List.of("ferritecore", "starlight", "phosphor", "noisium", "geophilic", "jei", "rei", "emi", "journeymap", "xaerominimap", "xaeroworldmap", "create", "alexsmobs", "naturalist", "supplementaries", "farmersdelight", "apotheosis", "waystones", "crafttweaker", "kubejs");
        detectedIncompatibleMods = new ArrayList<String>();
        detectedConflictMods = new ArrayList<String>();
        checked = false;
    }
}

