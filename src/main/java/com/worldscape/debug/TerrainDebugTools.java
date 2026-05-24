package com.worldscape.debug;

import com.worldscape.terrain.MacroRegionInfo;
import com.worldscape.terrain.MacroVoronoiSystem;
import com.worldscape.terrain.RegionController;
import com.worldscape.terrain.TerrainType;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TerrainDebugTools {
    private static final Logger LOGGER = LoggerFactory.getLogger(TerrainDebugTools.class);

    public static String queryTerrainAt(RegionController controller, MacroVoronoiSystem macroSystem, int x, int z) {
        StringBuilder sb = new StringBuilder();
        sb.append("\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\n");
        sb.append("           \u5730\u5f62\u4fe1\u606f\u67e5\u8be2 / Terrain Query\n");
        sb.append("\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\n");
        sb.append(String.format("  \u5750\u6807 / Coordinates: X=%d, Z=%d\n", x, z));
        sb.append("\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\n");
        MacroRegionInfo macroInfo = macroSystem.getRegionInfo(x, z);
        sb.append("\u3010\u5b8f\u89c2\u533a\u57df / Macro Region\u3011\n");
        sb.append(String.format("  \u63a7\u5236\u70b9\u4f4d\u7f6e: (%d, %d)\n", macroInfo.primaryCellX, macroInfo.primaryCellZ));
        sb.append(String.format("  \u6d77\u62d4\u7b49\u7ea7 / Elevation Tier: %d (%s)\n", macroInfo.elevationTier, TerrainDebugTools.getElevationTierName(macroInfo.elevationTier)));
        sb.append(String.format("  \u6df7\u5408\u57fa\u51c6\u9ad8\u5ea6 / Blended Base Height: %.1f\n", macroInfo.blendedBaseHeight));
        sb.append(String.format("  \u6c14\u5019\u5e26 / Climate Zone: %s\n", macroInfo.climate.name()));
        sb.append(String.format("  \u6784\u9020\u7c7b\u578b / Tectonic: %s\n", macroInfo.tectonic.name()));
        sb.append(String.format("  \u8fc7\u6e21\u5e26\u5bbd / Transition Width: %d \u683c\n", macroInfo.transitionWidth));
        sb.append(String.format("  \u6df7\u5408\u6743\u91cd / Blend Weight: %.2f%%\n", macroInfo.blendWeight * 100.0));
        sb.append("\n");
        RegionController.TerrainBlendResult blend = controller.getTerrainBlend(x, z);
        sb.append("\u3010\u5730\u5f62\u7c7b\u578b / Terrain Type\u3011\n");
        sb.append(String.format("  \u4e3b\u5bfc\u7c7b\u578b / Dominant: %s\n", blend.dominantType.getId()));
        sb.append(String.format("  \u4e3b\u5bfc\u6743\u91cd / Dominant Weight: %.2f%%\n", blend.dominantWeight * 100.0));
        sb.append(String.format("  \u6df7\u5408\u9ad8\u5ea6 / Blended Height: %.2f\n", blend.blendedHeight));
        sb.append(String.format("  \u504f\u79fb\u6df7\u5408 / Offset Blend: %.2f\n", blend.offsetBlend));
        sb.append("\n");
        TerrainType microType = TerrainDebugTools.determineTerrainTypeSimple(blend);
        sb.append("\u3010\u5fae\u89c2\u5730\u5f62 / Micro Terrain\u3011\n");
        sb.append(String.format("  \u786e\u5b9a\u7c7b\u578b / Determined: %s\n", microType.getId()));
        sb.append("\n");
        sb.append("\u3010\u5730\u5f62\u63cf\u8ff0 / Terrain Description\u3011\n");
        sb.append(String.format("  %s\n", TerrainDebugTools.getTerrainDescription(microType, macroInfo)));
        sb.append("\n");
        sb.append("\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550");
        return sb.toString();
    }

    private static String getElevationTierName(int tier) {
        return switch (tier) {
            case 0 -> "\u6df1\u6d77 (Deep Ocean)";
            case 1 -> "\u6d45\u6d77 (Shallow Ocean)";
            case 2 -> "\u6cbf\u6d77 (Coastal)";
            case 3 -> "\u4f4e\u5e73\u539f (Low Plains)";
            case 4 -> "\u5e73\u539f/\u4e18\u9675 (Plains/Hills)";
            case 5 -> "\u4f4e\u5c71 (Low Mountains)";
            case 6 -> "\u9ad8\u5c71 (High Mountains)";
            case 7 -> "\u6781\u9ad8\u5c71 (Extreme Mountains)";
            default -> "\u672a\u77e5 (Unknown)";
        };
    }

    private static TerrainType determineTerrainTypeSimple(RegionController.TerrainBlendResult blend) {
        return blend.dominantType;
    }

    private static String getTerrainDescription(TerrainType type, MacroRegionInfo macroInfo) {
        String baseDesc = switch (type) {
            case TerrainType.HIGH_MOUNTAINS, TerrainType.PEAK, TerrainType.HORN -> "\u9ad8\u8038\u7684\u5c71\u5cf0\u5730\u5f62\uff0c\u5e38\u5e74\u79ef\u96ea";
            case TerrainType.RIDGE, TerrainType.CLIFF -> "\u9661\u5ced\u7684\u5c71\u810a\u6216\u60ac\u5d16";
            case TerrainType.HILLS, TerrainType.ALLUVIAL_FAN, TerrainType.VALLEY -> "\u5e73\u7f13\u7684\u4e18\u9675\u8d77\u4f0f";
            case TerrainType.PLATEAU, TerrainType.DOME -> "\u5e73\u5766\u5f00\u9614\u7684\u9ad8\u539f\u5730\u5f62";
            case TerrainType.PLAINS -> "\u5e73\u5766\u5f00\u9614\u7684\u5e73\u539f";
            case TerrainType.CANYON, TerrainType.BASIN -> "\u6df1\u9083\u7684\u5ce1\u8c37\u6216\u76c6\u5730";
            case TerrainType.GLACIAL_VALLEY -> "\u51b0\u5ddd\u4fb5\u8680\u5f62\u6210\u7684U\u5f62\u8c37";
            case TerrainType.FLOODPLAIN -> "\u6cb3\u6d41\u51b2\u79ef\u5f62\u6210\u7684\u5e73\u539f";
            case TerrainType.DELTA -> "\u6cb3\u6d41\u5165\u6d77\u53e3\u5f62\u6210\u7684\u4e09\u89d2\u6d32";
            case TerrainType.DUNE, TerrainType.GOBI, TerrainType.YARDANG -> "\u98ce\u529b\u4f5c\u7528\u5f62\u6210\u7684\u6c99\u4e18\u6216\u6208\u58c1";
            case TerrainType.SALT_FLAT -> "\u76d0\u6cbc\u5730\u8c8c";
            case TerrainType.ICE_SHEET, TerrainType.CIRQUE -> "\u51b0\u5ddd\u6216\u51bb\u571f\u5f62\u6210\u7684\u5730\u8c8c";
            case TerrainType.PEAK_FOREST -> "\u5580\u65af\u7279\u77f3\u6797\u5730\u8c8c";
            case TerrainType.SINKHOLE -> "\u6eb6\u8680\u584c\u9677\u5f62\u6210\u7684\u5580\u65af\u7279\u5730\u8c8c";
            case TerrainType.BEACH -> "\u6d77\u5cb8\u5806\u79ef\u5f62\u6210\u7684\u6c99\u6ee9";
            case TerrainType.FJORD -> "\u51b0\u5ddd\u4fb5\u8680\u5f62\u6210\u7684\u5ce1\u6e7e";
            case TerrainType.SEA_PLATEAU -> "\u6d45\u6d77\u6d77\u5e95\u5e73\u53f0";
            case TerrainType.TRENCH -> "\u6df1\u6d77\u6d77\u6c9f";
            case TerrainType.SEA_CLIFF -> "\u6d77\u5cb8\u60ac\u5d16";
            default -> "\u4e00\u822c\u5730\u5f62";
        };
        String climateDesc = switch (macroInfo.climate) {
            default -> throw new MatchException(null, null);
            case MacroRegionInfo.ClimateZone.GLACIAL -> "\uff08\u5bd2\u5e26\u6c14\u5019\uff0c\u51ac\u5b63\u6f2b\u957f\uff09";
            case MacroRegionInfo.ClimateZone.TEMPERATE -> "\uff08\u6e29\u5e26\u6c14\u5019\uff0c\u56db\u5b63\u5206\u660e\uff09";
            case MacroRegionInfo.ClimateZone.ARID -> "\uff08\u5e72\u65f1\u6c14\u5019\uff0c\u964d\u6c34\u7a00\u5c11\uff09";
            case MacroRegionInfo.ClimateZone.TROPICAL -> "\uff08\u70ed\u5e26\u6c14\u5019\uff0c\u9ad8\u6e29\u591a\u96e8\uff09";
        };
        String tectonicDesc = switch (macroInfo.tectonic) {
            default -> throw new MatchException(null, null);
            case MacroRegionInfo.TectonicType.OROGENIC_BELT -> " | \u9020\u5c71\u5e26\uff1a\u5730\u58f3\u6d3b\u8dc3\uff0c\u8936\u76b1\u65ad\u5757\u53d1\u80b2";
            case MacroRegionInfo.TectonicType.SUBDUCTION_ZONE -> " | \u4fef\u51b2\u5e26\uff1a\u677f\u5757\u4fef\u51b2\u4f5c\u7528\u663e\u8457";
            case MacroRegionInfo.TectonicType.RIFT_ZONE -> " | \u88c2\u8c37\u5e26\uff1a\u5730\u58f3\u62c9\u5f20\u88c2\u9677";
            case MacroRegionInfo.TectonicType.FAULT_ZONE -> " | \u65ad\u5c42\u5e26\uff1a\u5730\u58f3\u9519\u52a8\u6d3b\u8dc3";
            case MacroRegionInfo.TectonicType.CRATON -> " | \u514b\u62c9\u901a\uff1a\u53e4\u8001\u7a33\u5b9a\u7684\u5730\u5757";
        };
        return baseDesc + climateDesc + tectonicDesc;
    }

    public static String generateTerrainSummary(RegionController controller, MacroVoronoiSystem macroSystem, int centerX, int centerZ, int radiusBlocks) {
        StringBuilder sb = new StringBuilder();
        sb.append("\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\n");
        sb.append("              \u533a\u57df\u5730\u5f62\u6458\u8981 / Regional Terrain Summary\n");
        sb.append("\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\n");
        sb.append(String.format("  \u533a\u57df\u8303\u56f4 / Region: Center=(%d, %d), Radius=%d\n", centerX, centerZ, radiusBlocks));
        sb.append(String.format("  \u603b\u9762\u79ef / Total Area: %.2f km\u00b2\n", Math.PI * (double)radiusBlocks * (double)radiusBlocks / 1000000.0));
        sb.append("\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\n");
        LinkedHashMap<TerrainType, Integer> typeCount = new LinkedHashMap<TerrainType, Integer>();
        LinkedHashMap<Integer, Integer> tierCount = new LinkedHashMap<Integer, Integer>();
        LinkedHashMap<MacroRegionInfo.ClimateZone, Integer> climateCount = new LinkedHashMap<MacroRegionInfo.ClimateZone, Integer>();
        LinkedHashMap<MacroRegionInfo.TectonicType, Integer> tectonicCount = new LinkedHashMap<MacroRegionInfo.TectonicType, Integer>();
        LinkedHashMap<String, Integer> combinedCount = new LinkedHashMap<String, Integer>();
        int sampleStep = Math.max(1, radiusBlocks / 50);
        int totalSamples = 0;
        double avgHeight = 0.0;
        double minHeight = Double.MAX_VALUE;
        double maxHeight = Double.MIN_VALUE;
        for (int x = centerX - radiusBlocks; x <= centerX + radiusBlocks; x += sampleStep) {
            for (int z = centerZ - radiusBlocks; z <= centerZ + radiusBlocks; z += sampleStep) {
                MacroRegionInfo macroInfo = macroSystem.getRegionInfo(x, z);
                RegionController.TerrainBlendResult blend = controller.getTerrainBlend(x, z);
                typeCount.merge(blend.dominantType, 1, Integer::sum);
                tierCount.merge(macroInfo.elevationTier, 1, Integer::sum);
                climateCount.merge(macroInfo.climate, 1, Integer::sum);
                tectonicCount.merge(macroInfo.tectonic, 1, Integer::sum);
                String key = String.format("%s + %s", macroInfo.climate.name(), blend.dominantType.getId());
                combinedCount.merge(key, 1, Integer::sum);
                avgHeight += blend.blendedHeight;
                minHeight = Math.min(minHeight, blend.blendedHeight);
                maxHeight = Math.max(maxHeight, blend.blendedHeight);
                ++totalSamples;
            }
        }
        int finalTotalSamples = totalSamples;
        sb.append("\u3010\u9ad8\u5ea6\u7edf\u8ba1 / Height Statistics\u3011\n");
        sb.append(String.format("  \u5e73\u5747\u9ad8\u5ea6 / Average: %.1f\n", avgHeight /= (double)totalSamples));
        sb.append(String.format("  \u6700\u4f4e\u9ad8\u5ea6 / Minimum: %.1f\n", minHeight));
        sb.append(String.format("  \u6700\u9ad8\u9ad8\u5ea6 / Maximum: %.1f\n", maxHeight));
        sb.append(String.format("  \u9ad8\u5ea6\u8de8\u5ea6 / Range: %.1f\n", maxHeight - minHeight));
        sb.append("\n");
        sb.append("\u3010\u4e3b\u5bfc\u5730\u5f62\u7c7b\u578b\u5206\u5e03 / Dominant Terrain Type Distribution\u3011\n");
        typeCount.entrySet().stream().sorted(Map.Entry.<TerrainType, Integer>comparingByValue().reversed()).limit(10L).forEach(e -> {
            double percent = 100.0 * (double)((Integer)e.getValue()).intValue() / (double)finalTotalSamples;
            sb.append(String.format("  %-20s: %5d (%5.1f%%)\n", ((TerrainType)e.getKey()).getId(), e.getValue(), percent));
        });
        sb.append("\n");
        sb.append("\u3010\u6d77\u62d4\u7b49\u7ea7\u5206\u5e03 / Elevation Tier Distribution\u3011\n");
        for (int i = 0; i <= 7; ++i) {
            int count = tierCount.getOrDefault(i, 0);
            double percent = 100.0 * (double)count / (double)finalTotalSamples;
            sb.append(String.format("  Tier %d (%-15s): %5d (%5.1f%%)\n", i, TerrainDebugTools.getElevationTierName(i).split(" ")[0], count, percent));
        }
        sb.append("\n");
        sb.append("\u3010\u6c14\u5019\u5e26\u5206\u5e03 / Climate Zone Distribution\u3011\n");
        climateCount.entrySet().stream().sorted(Map.Entry.<MacroRegionInfo.ClimateZone, Integer>comparingByValue().reversed()).forEach(e -> {
            double percent = 100.0 * (double)((Integer)e.getValue()).intValue() / (double)finalTotalSamples;
            sb.append(String.format("  %-10s: %5d (%5.1f%%)\n", ((MacroRegionInfo.ClimateZone)e.getKey()).name(), e.getValue(), percent));
        });
        sb.append("\n");
        sb.append("\u3010\u6784\u9020\u7c7b\u578b\u5206\u5e03 / Tectonic Type Distribution\u3011\n");
        tectonicCount.entrySet().stream().sorted(Map.Entry.<MacroRegionInfo.TectonicType, Integer>comparingByValue().reversed()).forEach(e -> {
            double percent = 100.0 * (double)((Integer)e.getValue()).intValue() / (double)finalTotalSamples;
            sb.append(String.format("  %-15s: %5d (%5.1f%%)\n", ((MacroRegionInfo.TectonicType)e.getKey()).name(), e.getValue(), percent));
        });
        sb.append("\n");
        sb.append("\u3010\u4e3b\u8981\u7ec4\u5408\u7c7b\u578b / Major Combinations\u3011\n");
        combinedCount.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(8L).forEach(e -> {
            double percent = 100.0 * (double)((Integer)e.getValue()).intValue() / (double)finalTotalSamples;
            sb.append(String.format("  %-25s: %5d (%5.1f%%)\n", e.getKey(), e.getValue(), percent));
        });
        sb.append("\n");
        sb.append("\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550");
        return sb.toString();
    }

    public static void exportMacroVoronoiImage(MacroVoronoiSystem macroSystem, int centerX, int centerZ, int radiusBlocks, int pixelsPerBlock, Path outputPath) {
        int imageSize = radiusBlocks * 2 * pixelsPerBlock;
        BufferedImage image = new BufferedImage(imageSize, imageSize, 1);
        int progressStep = Math.max(1, imageSize / 10);
        for (int px = 0; px < imageSize; ++px) {
            int worldX = centerX - radiusBlocks + (int)(((double)px + 0.5) / (double)pixelsPerBlock);
            for (int pz = 0; pz < imageSize; ++pz) {
                int worldZ = centerZ - radiusBlocks + (int)(((double)pz + 0.5) / (double)pixelsPerBlock);
                MacroRegionInfo info = macroSystem.getRegionInfo(worldX, worldZ);
                int color = TerrainDebugTools.getColorForRegionInfo(info);
                image.setRGB(px, pz, color);
            }
            if (px % progressStep != 0 || px <= 0) continue;
            LOGGER.info("[World Scape] Macro Voronoi export progress: {}%", (Object)(px * 100 / imageSize));
        }
        try {
            ImageIO.write((RenderedImage)image, "PNG", outputPath.toFile());
            LOGGER.info("[World Scape] Macro Voronoi image exported: {}", (Object)outputPath.getFileName());
        }
        catch (IOException e) {
            throw new RuntimeException("Failed to export macro voronoi image", e);
        }
    }

    public static void exportHeightMapImage(RegionController controller, int centerX, int centerZ, int radiusBlocks, int pixelsPerBlock, Path outputPath) {
        int imageSize = radiusBlocks * 2 * pixelsPerBlock;
        BufferedImage image = new BufferedImage(imageSize, imageSize, 1);
        int totalPixels = imageSize * imageSize;
        float[] heights = new float[totalPixels];
        float minHeight = Float.MAX_VALUE;
        float maxHeight = Float.MIN_VALUE;
        int progressStep = Math.max(1, imageSize / 10);
        for (int px = 0; px < imageSize; ++px) {
            int worldX = centerX - radiusBlocks + (int)(((double)px + 0.5) / (double)pixelsPerBlock);
            int worldZBase = centerZ - radiusBlocks;
            for (int pz = 0; pz < imageSize; ++pz) {
                float height;
                int worldZ = worldZBase + (int)(((double)pz + 0.5) / (double)pixelsPerBlock);
                RegionController.TerrainBlendResult blend = controller.getTerrainBlend(worldX, worldZ);
                heights[px * imageSize + pz] = height = (float)blend.blendedHeight;
                if (height < minHeight) {
                    minHeight = height;
                }
                if (!(height > maxHeight)) continue;
                maxHeight = height;
            }
            if (px % progressStep != 0 || px <= 0) continue;
            LOGGER.info("[World Scape] Heightmap export progress: {}% (pass 1/1)", (Object)(px * 100 / imageSize));
        }
        float range = maxHeight - minHeight;
        if (range == 0.0f) {
            range = 1.0f;
        }
        for (int idx = 0; idx < totalPixels; ++idx) {
            int normalized = (int)((heights[idx] - minHeight) / range * 255.0f);
            int gray = Math.max(0, Math.min(255, normalized));
            int rgb = gray << 16 | gray << 8 | gray;
            image.setRGB(idx % imageSize, idx / imageSize, rgb);
        }
        try {
            ImageIO.write((RenderedImage)image, "PNG", outputPath.toFile());
            LOGGER.info("[World Scape] Heightmap exported: {} ({}x{}, min={}, max={})", new Object[]{outputPath.getFileName(), imageSize, imageSize, String.format("%.1f", Float.valueOf(minHeight)), String.format("%.1f", Float.valueOf(maxHeight))});
        }
        catch (IOException e) {
            throw new RuntimeException("Failed to export height map image", e);
        }
    }

    public static void exportEnhancedTerrainMap(RegionController controller, int centerX, int centerZ, int radiusBlocks, int pixelsPerBlock, Path outputPath) {
        int imageSize = radiusBlocks * 2 * pixelsPerBlock;
        BufferedImage image = new BufferedImage(imageSize, imageSize, 1);
        int totalPixels = imageSize * imageSize;
        float[] heights = new float[totalPixels];
        byte[] terrainTypeIds = new byte[totalPixels];
        byte[] elevationTierIds = new byte[totalPixels];
        float[] dominantWeights = new float[totalPixels];
        float minHeight = Float.MAX_VALUE;
        float maxHeight = Float.MIN_VALUE;
        int progressStep = Math.max(1, imageSize / 10);
        for (int px = 0; px < imageSize; ++px) {
            int worldX = centerX - radiusBlocks + (int)(((double)px + 0.5) / (double)pixelsPerBlock);
            int worldZBase = centerZ - radiusBlocks;
            for (int pz = 0; pz < imageSize; ++pz) {
                float h;
                int worldZ = worldZBase + (int)(((double)pz + 0.5) / (double)pixelsPerBlock);
                int idx = px * imageSize + pz;
                RegionController.TerrainBlendResult blend = controller.getTerrainBlend(worldX, worldZ);
                heights[idx] = h = (float)blend.blendedHeight;
                terrainTypeIds[idx] = (byte)blend.dominantType.ordinal();
                elevationTierIds[idx] = (byte)blend.macroInfo.elevationTier;
                dominantWeights[idx] = (float)blend.dominantWeight;
                if (h < minHeight) {
                    minHeight = h;
                }
                if (!(h > maxHeight)) continue;
                maxHeight = h;
            }
            if (px % progressStep != 0 || px <= 0) continue;
            LOGGER.info("[World Scape] Enhanced terrain map progress: {}% (pass 1/1)", (Object)(px * 100 / imageSize));
        }
        float heightRange = maxHeight - minHeight;
        if (heightRange == 0.0f) {
            heightRange = 1.0f;
        }
        TerrainType[] typeValues = TerrainType.values();
        int[] typeColors = new int[typeValues.length];
        for (int i = 0; i < typeValues.length; ++i) {
            typeColors[i] = TerrainDebugTools.getTerrainTypeColor(typeValues[i]);
        }
        for (int idx = 0; idx < totalPixels; ++idx) {
            float height = heights[idx];
            int baseColor = typeColors[terrainTypeIds[idx] & 0xFF];
            float normalizedHeight = (height - minHeight) / heightRange;
            int brightness = (int)(Math.max(0.1f, Math.min(1.0f, normalizedHeight * 1.2f)) * 255.0f);
            brightness = Math.max(0, Math.min(255, brightness));
            int r = TerrainDebugTools.applyBrightnessToChannel(baseColor >> 16 & 0xFF, brightness);
            int g = TerrainDebugTools.applyBrightnessToChannel(baseColor >> 8 & 0xFF, brightness);
            int b = TerrainDebugTools.applyBrightnessToChannel(baseColor & 0xFF, brightness);
            int color = r << 16 | g << 8 | b;
            if ((double)dominantWeights[idx] < 0.3 && (double)dominantWeights[idx] > 0.05) {
                color = TerrainDebugTools.blendColors(color, 0xFFFFFF, 0.25);
            }
            image.setRGB(idx % imageSize, idx / imageSize, color);
        }
        try {
            ImageIO.write((RenderedImage)image, "PNG", outputPath.toFile());
            LOGGER.info("[World Scape] Enhanced terrain map exported: {} ({}x{})", new Object[]{outputPath.getFileName(), imageSize, imageSize});
        }
        catch (IOException e) {
            throw new RuntimeException("Failed to export enhanced terrain map", e);
        }
    }

    public static void exportContourTerrainMap(RegionController controller, int centerX, int centerZ, int radiusBlocks, int pixelsPerBlock, int contourInterval, Path outputPath) {
        int pz;
        int imageSize = radiusBlocks * 2 * pixelsPerBlock;
        BufferedImage image = new BufferedImage(imageSize, imageSize, 1);
        int totalPixels = imageSize * imageSize;
        float[] heights = new float[totalPixels];
        float minHeight = Float.MAX_VALUE;
        float maxHeight = Float.MIN_VALUE;
        int progressStep = Math.max(1, imageSize / 10);
        for (int px = 0; px < imageSize; ++px) {
            int worldX = centerX - radiusBlocks + (int)(((double)px + 0.5) / (double)pixelsPerBlock);
            int worldZBase = centerZ - radiusBlocks;
            for (pz = 0; pz < imageSize; ++pz) {
                float h;
                int worldZ = worldZBase + (int)(((double)pz + 0.5) / (double)pixelsPerBlock);
                int idx = px * imageSize + pz;
                RegionController.TerrainBlendResult blend = controller.getTerrainBlend(worldX, worldZ);
                heights[idx] = h = (float)blend.blendedHeight;
                if (h < minHeight) {
                    minHeight = h;
                }
                if (!(h > maxHeight)) continue;
                maxHeight = h;
            }
            if (px % progressStep != 0 || px <= 0) continue;
            LOGGER.info("[World Scape] Contour map progress: {}% (pass 1/1)", (Object)(px * 100 / imageSize));
        }
        float heightRange = maxHeight - minHeight;
        if (heightRange == 0.0f) {
            heightRange = 1.0f;
        }
        float pixelHeightTolerance = 0.5f;
        for (int px = 0; px < imageSize; ++px) {
            for (pz = 0; pz < imageSize; ++pz) {
                float prevHeight;
                float height = heights[px * imageSize + pz];
                int normalized = (int)((height - minHeight) / heightRange * 255.0f);
                int gray = Math.max(0, Math.min(255, normalized));
                int rgb = gray << 16 | gray << 8 | gray;
                boolean isContour = false;
                float contourFraction = 0.0f;
                if (px > 0) {
                    prevHeight = heights[(px - 1) * imageSize + pz];
                    contourFraction = Math.max(contourFraction, TerrainDebugTools.detectContourCrossing(prevHeight, height, contourInterval));
                }
                if (pz > 0) {
                    prevHeight = heights[px * imageSize + (pz - 1)];
                    contourFraction = Math.max(contourFraction, TerrainDebugTools.detectContourCrossing(prevHeight, height, contourInterval));
                }
                if (contourFraction > 0.5f) {
                    isContour = true;
                }
                if (isContour) {
                    boolean isMajorContour;
                    int heightInt = Math.round(height);
                    boolean bl = isMajorContour = heightInt % (contourInterval * 5) == 0;
                    rgb = isMajorContour ? (gray > 127 ? 0 : 0xFFFFFF) : TerrainDebugTools.blendColors(rgb, gray > 127 ? 0 : 0xFFFFFF, 0.3);
                }
                image.setRGB(px, pz, rgb);
            }
        }
        try {
            ImageIO.write((RenderedImage)image, "PNG", outputPath.toFile());
            LOGGER.info("[World Scape] Contour map exported: {} ({}x{}, interval={})", new Object[]{outputPath.getFileName(), imageSize, imageSize, contourInterval});
        }
        catch (IOException e) {
            throw new RuntimeException("Failed to export contour terrain map", e);
        }
    }

    private static float detectContourCrossing(float h1, float h2, int contourInterval) {
        if (contourInterval <= 0) {
            return 0.0f;
        }
        float minH = Math.min(h1, h2);
        float maxH = Math.max(h1, h2);
        float range = maxH - minH;
        if (range < 0.1f) {
            return 0.0f;
        }
        int nearestBelow = (int)Math.floor(minH / (float)contourInterval) * contourInterval;
        int nearestAbove = nearestBelow + contourInterval;
        if ((float)nearestAbove > minH && (float)nearestAbove < maxH) {
            return ((float)nearestAbove - minH) / range;
        }
        return 0.0f;
    }

    private static int getTerrainTypeColor(TerrainType type) {
        return switch (type) {
            case TerrainType.HIGH_MOUNTAINS, TerrainType.PEAK, TerrainType.HORN -> 0xCC3333;
            case TerrainType.RIDGE, TerrainType.CLIFF, TerrainType.SEA_CLIFF -> 0xFF8833;
            case TerrainType.HILLS, TerrainType.ALLUVIAL_FAN, TerrainType.VALLEY -> 0xFFCC33;
            case TerrainType.PLATEAU, TerrainType.DOME -> 0x9933CC;
            case TerrainType.PLAINS, TerrainType.FLOODPLAIN -> 0x33CC33;
            case TerrainType.CANYON, TerrainType.BASIN, TerrainType.GLACIAL_VALLEY, TerrainType.SINKHOLE -> 0x2244AA;
            case TerrainType.DELTA, TerrainType.BEACH, TerrainType.FJORD, TerrainType.SEA_PLATEAU, TerrainType.TRENCH -> 0x3366CC;
            case TerrainType.DUNE, TerrainType.GOBI, TerrainType.YARDANG, TerrainType.SALT_FLAT -> 0xCCAA66;
            case TerrainType.ICE_SHEET, TerrainType.CIRQUE -> 0x99CCFF;
            case TerrainType.PEAK_FOREST -> 0x888888;
            default -> 0xAAAAAA;
        };
    }

    private static int applyBrightnessToChannel(int channelValue, int brightness) {
        return (int)((double)channelValue * ((double)brightness / 255.0));
    }

    private static int blendColors(int color1, int color2, double factor) {
        int r1 = color1 >> 16 & 0xFF;
        int g1 = color1 >> 8 & 0xFF;
        int b1 = color1 & 0xFF;
        int r2 = color2 >> 16 & 0xFF;
        int g2 = color2 >> 8 & 0xFF;
        int b2 = color2 & 0xFF;
        int r = (int)((double)r1 * (1.0 - factor) + (double)r2 * factor);
        int g = (int)((double)g1 * (1.0 - factor) + (double)g2 * factor);
        int b = (int)((double)b1 * (1.0 - factor) + (double)b2 * factor);
        return r << 16 | g << 8 | b;
    }

    public static void exportTerrainStatsChart(RegionController controller, int centerX, int centerZ, int radiusBlocks, int pixelsPerBlock, Path outputPath) {
        int imageSize = radiusBlocks * 2 * pixelsPerBlock;
        HashMap<TerrainType, Integer> typeCounts = new HashMap<TerrainType, Integer>();
        HashMap<Integer, Integer> tierCounts = new HashMap<Integer, Integer>();
        int totalPixels = 0;
        for (int px = 0; px < imageSize; px += pixelsPerBlock) {
            for (int pz = 0; pz < imageSize; pz += pixelsPerBlock) {
                int worldX = centerX - radiusBlocks + px / pixelsPerBlock;
                int worldZ = centerZ - radiusBlocks + pz / pixelsPerBlock;
                RegionController.TerrainBlendResult blend = controller.getTerrainBlend(worldX, worldZ);
                typeCounts.merge(blend.dominantType, 1, Integer::sum);
                tierCounts.merge(blend.macroInfo.elevationTier, 1, Integer::sum);
                ++totalPixels;
            }
        }
        int chartWidth = 400;
        int chartHeight = 300;
        BufferedImage chart = new BufferedImage(chartWidth, chartHeight, 1);
        Graphics2D g = chart.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, chartWidth, chartHeight);
        g.setColor(Color.BLACK);
        g.setFont(new Font("Monospaced", 1, 14));
        g.drawString("Terrain Type Distribution", 10, 20);
        g.drawString(String.format("Center: (%d, %d) Radius: %d", centerX, centerZ, radiusBlocks), 10, 40);
        int barY = 60;
        int barMaxWidth = chartWidth - 100;
        g.setFont(new Font("Monospaced", 0, 11));
        LinkedHashMap<TerrainType, Integer> sortedTypes = new LinkedHashMap<>();
        typeCounts.entrySet().stream().sorted(Map.Entry.<TerrainType, Integer>comparingByValue().reversed()).forEach(e -> sortedTypes.put(e.getKey(), e.getValue()));
        int barIndex = 0;
        for (Map.Entry<TerrainType, Integer> entry : sortedTypes.entrySet()) {
            TerrainType type = entry.getKey();
            int count = entry.getValue();
            double percent = totalPixels > 0 ? (double)count / (double)totalPixels * 100.0 : 0.0;
            int color = TerrainDebugTools.getTerrainTypeColor(type);
            g.setColor(new Color(color));
            int barWidth = (int)((double)barMaxWidth * percent / 100.0);
            g.fillRect(100, barY, Math.max(barWidth, 2), 14);
            g.setColor(Color.BLACK);
            g.drawString(String.format("%s: %.1f%%", type.getId(), percent), 10, barY + 11);
            if (++barIndex < 12 && (barY += 18) <= chartHeight - 20) continue;
            break;
        }
        g.dispose();
        try {
            ImageIO.write((RenderedImage)chart, "PNG", outputPath.toFile());
        }
        catch (IOException e2) {
            throw new RuntimeException("Failed to export terrain stats chart", e2);
        }
    }

    public static boolean verifyHeightConsistency(RegionController controller, int startX, int startZ, int size, int step) {
        String singleThreadMD5 = TerrainDebugTools.generateHeightMD5(controller, startX, startZ, size, step, false);
        String multiThreadMD5 = TerrainDebugTools.generateHeightMD5(controller, startX, startZ, size, step, true);
        return singleThreadMD5.equals(multiThreadMD5);
    }

    public static String generateHeightMD5(RegionController controller, int startX, int startZ, int size, int step, boolean multiThread) {
        StringBuilder heightData = new StringBuilder();
        if (multiThread) {
            double[][] heights = new double[size][size];
            Thread[] threads = new Thread[size];
            for (int i = 0; i < size; ++i) {
                int x = i;
                threads[i] = new Thread(() -> {
                    for (int z = 0; z < size; ++z) {
                        int worldX = startX + x * step;
                        int worldZ = startZ + z * step;
                        RegionController.TerrainBlendResult blend = controller.getTerrainBlend(worldX, worldZ);
                        heights[x][z] = blend.blendedHeight;
                    }
                });
                threads[i].start();
            }
            try {
                for (Thread t : threads) {
                    t.join();
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "ERROR";
            }
            for (int i = 0; i < size; ++i) {
                for (int j = 0; j < size; ++j) {
                    heightData.append(String.format("%.2f,", heights[i][j]));
                }
            }
        } else {
            for (int i = 0; i < size; ++i) {
                for (int j = 0; j < size; ++j) {
                    int worldX = startX + i * step;
                    int worldZ = startZ + j * step;
                    RegionController.TerrainBlendResult blend = controller.getTerrainBlend(worldX, worldZ);
                    heightData.append(String.format("%.2f,", blend.blendedHeight));
                }
            }
        }
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(heightData.toString().getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        }
        catch (NoSuchAlgorithmException e) {
            return "ERROR";
        }
    }

    private static int getColorForRegionInfo(MacroRegionInfo info) {
        return switch (info.tectonic) {
            default -> throw new MatchException(null, null);
            case MacroRegionInfo.TectonicType.OROGENIC_BELT -> 16729344;
            case MacroRegionInfo.TectonicType.SUBDUCTION_ZONE -> 0x8B0000;
            case MacroRegionInfo.TectonicType.RIFT_ZONE -> 52945;
            case MacroRegionInfo.TectonicType.FAULT_ZONE -> 16766720;
            case MacroRegionInfo.TectonicType.CRATON -> {
                int tier = info.elevationTier;
                int green = Math.max(0, Math.min(255, 50 + tier * 30));
                yield green << 16 | green << 8 | green;
            }
        };
    }
}

