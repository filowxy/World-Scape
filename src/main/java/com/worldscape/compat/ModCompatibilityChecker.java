package com.worldscape.compat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mod Compatibility Checker for World Scape.
 * 
 * <p>Classification rules (v2.0 - 2026-05-23 revision):
 * <ul>
 *   <li><b>INCOMPATIBLE</b>: Registers its own ChunkGenerator (same-dimension exclusion)
 *       or directly replaces core worldgen pipeline classes.</li>
 *   <li><b>CONFLICT</b>: Modifies biome assignment, surface rules, chunk threading model,
 *       or biome source without replacing ChunkGenerator.</li>
 * </ul>
 * 
 * <p><b>Validation checklist before adding new mods:</b>
 * <ol>
 *   <li>Does this mod register a custom ChunkGenerator via WorldPreset? &rarr; INCOMPATIBLE</li>
 *   <li>Does it completely replace BiomeSource? &rarr; INCOMPATIBLE</li>
 *   <li>Does it only add biomes via TerraBlender/datapack? &rarr; CONFLICT</li>
 *   <li>Does it only modify block/entity storage? &rarr; COMPATIBLE (don't add)</li>
 *   <li>Does it only add structures/features? &rarr; CONFLICT at most</li>
 *   <li>Does it change the chunk generation threading model? &rarr; CONFLICT</li>
 * </ol>
 */
public class ModCompatibilityChecker {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModCompatibilityChecker.class);

    /**
     * INCOMPATIBLE mods — register their own custom ChunkGenerator or replace core worldgen pipeline.
     * 
     * <p>Strict criteria:
     * <ul>
     *   <li>Registers custom ChunkGenerator via WorldPreset/MinecraftServer (same-dimension exclusion)</li>
     *   <li>Completely replaces BiomeSource, ChunkGenerator, or core generation pipeline</li>
     *   <li>Uses non-standard chunk structure (e.g., CubicChunks)</li>
     * </ul>
     * 
     * Updated: 2026-05-23 — reclassified multiple mods from INCOMPATIBLE to CONFLICT after review.
     */
    private static final Map<String, String> INCOMPATIBLE_MODS = new LinkedHashMap<>();
    static {
        INCOMPATIBLE_MODS.put("terraincontrol", "Registers custom ChunkGenerator — mutual exclusion");
        INCOMPATIBLE_MODS.put("amplify", "Replaces OverworldChunkGenerator and NetherChunkGenerator");
        INCOMPATIBLE_MODS.put("terraforged", "Registers custom ChunkGenerator for 1.21");
        INCOMPATIBLE_MODS.put("openworlds", "Provides custom WorldPreset with custom ChunkGenerator");
        INCOMPATIBLE_MODS.put("bigglobe", "Registers custom ChunkGenerator — mutual exclusion");
        INCOMPATIBLE_MODS.put("amplified_nether", "Replaces NetherChunkGenerator — mutual exclusion for Nether");
        INCOMPATIBLE_MODS.put("worldpainter", "Directly modifies ChunkAccess BlockState, overrides WS fillColumn");
        INCOMPATIBLE_MODS.put("william_wythers_overhauled_overworld",
                "Replaces BiomeSource via OverworldBiomeProvider — WS biome override conflicts");
        INCOMPATIBLE_MODS.put("cubicchunks", "Non-standard vertical chunk structure — WS assumes 16x384x16");
        INCOMPATIBLE_MODS.put("cubicworldgen", "Same as CubicChunks — incompatible chunk height model");
    }

    /**
     * CONFLICT mods — modify worldgen pipeline components without replacing ChunkGenerator.
     * 
     * <p>Criteria:
     * <ul>
     *   <li>Modifies biome distribution (e.g., adds many custom biomes)</li>
     *   <li>Modifies surface rules, carvers, or aquifer system</li>
     *   <li>Changes chunk generation threading model</li>
     *   <li>Modifies NoiseGeneratorSettings or density function compilation</li>
     *   <li>Modifies biome features list (decorations, vegetation)</li>
     * </ul>
     */
    private static final Map<String, String> CONFLICT_MODS = new LinkedHashMap<>();
    static {
        CONFLICT_MODS.put("c2me", "Parallelizes fillFromNoise — WS riverCache/BlendCache not thread-safe tested");
        CONFLICT_MODS.put("modernfix", "Surface rules caching may affect WS ReflectionSurfaceAdapter timing");
        CONFLICT_MODS.put("distanthorizons",
                "Uses DhLitWorldGenRegion instead of ServerLevel — WS regionController expects ServerLevel");
        CONFLICT_MODS.put("biomesoplenty",
                "Adds ~90 custom biomes — WS TerrainBiomeRules may not correctly map BoP biomes to terrain types");
        CONFLICT_MODS.put("regions_unexplored",
                "Adds ~60 overworld biomes — WS default biome tags may not cover R-U biomes");
        CONFLICT_MODS.put("byg",
                "Oh The Biomes You'll Go — adds many biomes, WS auto-scan may misclassify non-overworld biomes");
        CONFLICT_MODS.put("natures_spirit",
                "Adds forest/jungle variant biomes — WS PLAINS blacklist may not exclude NS biomes");
        CONFLICT_MODS.put("terrablender",
                "MixinNoiseGeneratorSettings injects surface rules — may affect WS ReflectionSurfaceAdapter");
        CONFLICT_MODS.put("yungsbettercaves",
                "AquiferMixin modifies cave liquid gen — WS applyCarvers is no-op, caves won't generate");
        CONFLICT_MODS.put("bettercaves",
                "Same as YUNG's Better Caves — carver system conflict");
        CONFLICT_MODS.put("betterterrain",
                "Modifies surface block placement — may double-place with WS FallbackSurfaceAdapter");
        CONFLICT_MODS.put("william_wythers_expanded_ecosphere",
                "Modifies biome decorations — decorations may appear at wrong heights due to WS terrain override");
        CONFLICT_MODS.put("valhelsia_structures",
                "Adds structures dependent on vanilla terrain height — may generate incorrectly on WS steep terrain");
        CONFLICT_MODS.put("quark",
                "Comprehensive mod with worldgen features — MixinChunkGenerator injection may fail on WS custom ChunkGenerator");
        CONFLICT_MODS.put("charm",
                "Comprehensive mod — some worldgen features may conflict with WS biome override");
        CONFLICT_MODS.put("litematica",
                "Schematic mod — does NOT modify world generation; previously misclassified as INCOMPATIBLE");
        CONFLICT_MODS.put("structure_gel",
                "Structure API library — does NOT modify world generation; previously misclassified as INCOMPATIBLE");
    }

    /**
     * Mods explicitly verified as COMPATIBLE — used for validation consistency.
     */
    private static final List<String> VERIFIED_COMPATIBLE = List.of(
            "ferritecore", "starlight", "phosphor", "noisium", "geophilic",
            "jei", "rei", "emi",
            "journeymap", "xaerominimap", "xaeroworldmap",
            "create", "alexsmobs", "naturalist",
            "supplementaries", "farmersdelight", "apotheosis", "waystones",
            "crafttweaker", "kubejs"
    );

    private static List<String> detectedIncompatibleMods = new ArrayList<>();
    private static List<String> detectedConflictMods = new ArrayList<>();
    private static boolean checked = false;

    private static void validateClassifications() {
        for (String modId : INCOMPATIBLE_MODS.keySet()) {
            if (CONFLICT_MODS.containsKey(modId)) {
                LOGGER.error("[World Scape] CLASSIFICATION ERROR: '{}' is in BOTH INCOMPATIBLE and CONFLICT lists!", modId);
            }
        }
        for (String modId : VERIFIED_COMPATIBLE) {
            if (INCOMPATIBLE_MODS.containsKey(modId)) {
                LOGGER.error("[World Scape] CLASSIFICATION ERROR: '{}' is in VERIFIED_COMPATIBLE but also INCOMPATIBLE!", modId);
            }
            if (CONFLICT_MODS.containsKey(modId)) {
                LOGGER.error("[World Scape] CLASSIFICATION ERROR: '{}' is in VERIFIED_COMPATIBLE but also CONFLICT!", modId);
            }
        }
    }

    public static void checkCompatibility() {
        if (checked) return;
        detectedIncompatibleMods.clear();
        detectedConflictMods.clear();
        validateClassifications();
        ModList modList = ModList.get();
        for (Map.Entry<String, String> entry : INCOMPATIBLE_MODS.entrySet()) {
            String modId = entry.getKey();
            if (modList.isLoaded(modId)) {
                detectedIncompatibleMods.add(modId);
                LOGGER.warn("[World Scape] INCOMPATIBLE mod detected: {} — {}", modId, entry.getValue());
            }
        }
        for (Map.Entry<String, String> entry : CONFLICT_MODS.entrySet()) {
            String modId = entry.getKey();
            if (modList.isLoaded(modId)) {
                detectedConflictMods.add(modId);
                LOGGER.warn("[World Scape] CONFLICT mod detected: {} — {}", modId, entry.getValue());
            }
        }
        checked = true;
        if (!detectedIncompatibleMods.isEmpty() || !detectedConflictMods.isEmpty()) {
            LOGGER.warn("[World Scape] Compatibility scan complete: {} INCOMPATIBLE, {} CONFLICT mods found",
                    detectedIncompatibleMods.size(), detectedConflictMods.size());
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
        if (!checked) checkCompatibility();
        return !detectedIncompatibleMods.isEmpty();
    }

    public static boolean hasConflictMods() {
        if (!checked) checkCompatibility();
        return !detectedConflictMods.isEmpty();
    }

    public static boolean hasAnyIssues() {
        return hasIncompatibleMods() || hasConflictMods();
    }

    public static List<String> getIncompatibleMods() {
        if (!checked) checkCompatibility();
        return new ArrayList<>(detectedIncompatibleMods);
    }

    public static List<String> getConflictMods() {
        if (!checked) checkCompatibility();
        return new ArrayList<>(detectedConflictMods);
    }

    public static String formatModName(String modId) {
        String[] parts = modId.split("_");
        StringBuilder name = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (name.length() > 0) name.append(" ");
            name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return name.toString();
    }

    public static String getWarningMessage() {
        if (!checked) checkCompatibility();
        StringBuilder message = new StringBuilder();
        if (!detectedIncompatibleMods.isEmpty()) {
            message.append("=== INCOMPATIBLE Mods Detected ===\n");
            message.append("The following mods may cause crashes or world generation failure:\n\n");
            for (int i = 0; i < detectedIncompatibleMods.size(); i++) {
                String mod = detectedIncompatibleMods.get(i);
                message.append("  ").append(i + 1).append(". ")
                        .append(formatModName(mod)).append(" (").append(mod).append(")\n");
                message.append("     Reason: ").append(getIncompatibleReason(mod)).append("\n\n");
            }
        }
        if (!detectedConflictMods.isEmpty()) {
            message.append("=== CONFLICT Mods Detected ===\n");
            message.append("The following mods may cause visual or topological issues:\n\n");
            for (int i = 0; i < detectedConflictMods.size(); i++) {
                String mod = detectedConflictMods.get(i);
                message.append("  ").append(i + 1).append(". ")
                        .append(formatModName(mod)).append(" (").append(mod).append(")\n");
                message.append("     Reason: ").append(getConflictReason(mod)).append("\n\n");
            }
        }
        if (detectedConflictMods.contains("c2me")) {
            message.append("  [C2ME Special Note]: ")
                    .append("WS riverCache uses ThreadLocal for thread isolation. ")
                    .append("C2ME parallel fillFromNoise may still cause BlendCache contention.\n\n");
        }
        if (detectedConflictMods.contains("distanthorizons")) {
            message.append("  [Distant Horizons Special Note]: ")
                    .append("Set distantGeneratorMode=\"INTERNAL_SERVER\" as workaround. ")
                    .append("WS regionController expects ServerLevel environment.\n\n");
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
}