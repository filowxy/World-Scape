package com.worldscape.debug;

import com.worldscape.debug.TerrainDebugSystem;
import com.worldscape.terrain.ControlPointRegion;
import com.worldscape.terrain.MacroRegionInfo;
import com.worldscape.terrain.RegionController;
import com.worldscape.terrain.TerrainControlPoint;
import com.worldscape.terrain.TerrainType;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TerrainFrameLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger(TerrainFrameLogger.class);
    private static final String LOG_PREFIX = "[World Scape] [TerrainFrame]";

    public static void logChunkTerrainFrame(int chunkX, int chunkZ, RegionController controller, int[][] heightMap, TerrainType[][] typeMap, RegionController.TerrainBlendResult[][] blendMap) {
        if (!TerrainDebugSystem.shouldLogChunk(chunkX, chunkZ)) {
            return;
        }
        int minX = chunkX * 16;
        int minZ = chunkZ * 16;
        int[][] samplePoints = new int[][]{{0, 0}, {8, 0}, {15, 0}, {0, 8}, {8, 8}, {15, 8}, {0, 15}, {8, 15}, {15, 15}};
        int heightMin = Integer.MAX_VALUE;
        int heightMax = Integer.MIN_VALUE;
        double heightSum = 0.0;
        int heightCount = 0;
        HashMap<TerrainType, Integer> typeDistribution = new HashMap<TerrainType, Integer>();
        HashMap<Integer, Integer> tierDistribution = new HashMap<Integer, Integer>();
        HashMap<MacroRegionInfo.TectonicType, Integer> tectonicDistribution = new HashMap<MacroRegionInfo.TectonicType, Integer>();
        double maxBlendWeight = 0.0;
        double minBlendWeight = 1.0;
        double blendWeightSum = 0.0;
        int blendWeightCount = 0;
        HashSet<String> uniqueControlPoints = new HashSet<String>();
        for (int[] sample : samplePoints) {
            int x = sample[0];
            int z = sample[1];
            int worldX = minX + x;
            int worldZ = minZ + z;
            RegionController.TerrainBlendResult blend = blendMap[x][z];
            TerrainType type = typeMap[x][z];
            int height = heightMap[x][z];
            heightMin = Math.min(heightMin, height);
            heightMax = Math.max(heightMax, height);
            heightSum += (double)height;
            ++heightCount;
            typeDistribution.merge(type, 1, Integer::sum);
            tierDistribution.merge(blend.macroInfo.elevationTier, 1, Integer::sum);
            tectonicDistribution.merge(blend.macroInfo.tectonic, 1, Integer::sum);
            maxBlendWeight = Math.max(maxBlendWeight, blend.dominantWeight);
            minBlendWeight = Math.min(minBlendWeight, blend.dominantWeight);
            blendWeightSum += blend.dominantWeight;
            ++blendWeightCount;
            for (RegionController.PointWeight pw : blend.contributingPoints) {
                String cpKey = pw.point.x + "," + pw.point.z;
                uniqueControlPoints.add(cpKey);
            }
        }
        for (int x = 0; x < 16; ++x) {
            for (int z = 0; z < 16; ++z) {
                int h = heightMap[x][z];
                heightMin = Math.min(heightMin, h);
                heightMax = Math.max(heightMax, h);
            }
        }
        double avgHeight = heightCount > 0 ? heightSum / (double)heightCount : 0.0;
        double avgBlendWeight = blendWeightCount > 0 ? blendWeightSum / (double)blendWeightCount : 0.0;
        int heightRange = heightMax - heightMin;
        LOGGER.info("{} Chunk ({},{}) worldPos=({},{}): heightRange=[{}..{}] avg={} spread={}", new Object[]{LOG_PREFIX, chunkX, chunkZ, minX, minZ, heightMin, heightMax, String.format("%.1f", avgHeight), heightRange});
        if (!typeDistribution.isEmpty()) {
            StringBuilder typeStr = new StringBuilder("Types={");
            typeDistribution.entrySet().stream().sorted(Map.Entry.<TerrainType, Integer>comparingByValue().reversed()).limit(5L).forEach(e -> typeStr.append(((TerrainType)((Object)((Object)e.getKey()))).getId()).append(":").append(e.getValue()).append(", "));
            if (typeStr.length() > 7) {
                typeStr.setLength(typeStr.length() - 2);
            }
            typeStr.append("}");
            LOGGER.info("{} {}", (Object)LOG_PREFIX, (Object)typeStr);
        }
        if (!tierDistribution.isEmpty()) {
            StringBuilder tierStr = new StringBuilder("Tiers={");
            tierDistribution.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> tierStr.append("T").append(e.getKey()).append(":").append(e.getValue()).append(", "));
            if (tierStr.length() > 7) {
                tierStr.setLength(tierStr.length() - 2);
            }
            tierStr.append("}");
            LOGGER.info("{} {}", (Object)LOG_PREFIX, (Object)tierStr);
        }
        if (!tectonicDistribution.isEmpty()) {
            StringBuilder tectonicStr = new StringBuilder("Tectonics={");
            tectonicDistribution.forEach((k, v) -> tectonicStr.append(k).append(":").append(v).append(", "));
            if (tectonicStr.length() > 12) {
                tectonicStr.setLength(tectonicStr.length() - 2);
            }
            tectonicStr.append("}");
            LOGGER.info("{} {}", (Object)LOG_PREFIX, (Object)tectonicStr);
        }
        LOGGER.info("{} BlendWeights: min={} max={} avg={}", new Object[]{LOG_PREFIX, String.format("%.3f", minBlendWeight), String.format("%.3f", maxBlendWeight), String.format("%.3f", avgBlendWeight)});
        LOGGER.info("{} ControlPoints: {} unique points in sample area", (Object)LOG_PREFIX, (Object)uniqueControlPoints.size());
        String skeletonQuality = TerrainFrameLogger.assessSkeletonQuality(heightRange, typeDistribution, tierDistribution);
        LOGGER.info("{} Skeleton Quality: {}", (Object)LOG_PREFIX, (Object)skeletonQuality);
    }

    private static String assessSkeletonQuality(int heightRange, Map<TerrainType, Integer> typeDistribution, Map<Integer, Integer> tierDistribution) {
        String rangeScore = heightRange >= 200 ? "EXCELLENT(>200)" : (heightRange >= 100 ? "GOOD(100-200)" : (heightRange >= 50 ? "MODERATE(50-100)" : "FLAT(<50)"));
        int typeCount = typeDistribution.size();
        int tierCount = tierDistribution.size();
        String diversityScore = typeCount >= 4 && tierCount >= 3 ? "DIVERSE" : (typeCount >= 2 && tierCount >= 2 ? "MODERATE" : "UNIFORM");
        boolean hasMountain = typeDistribution.containsKey((Object)TerrainType.HIGH_MOUNTAINS) || typeDistribution.containsKey((Object)TerrainType.RIDGE) || typeDistribution.containsKey((Object)TerrainType.PEAK) || typeDistribution.containsKey((Object)TerrainType.HORN);
        boolean hasPlateau = typeDistribution.containsKey((Object)TerrainType.PLATEAU) || typeDistribution.containsKey((Object)TerrainType.DOME);
        boolean hasPlains = typeDistribution.containsKey((Object)TerrainType.PLAINS) || typeDistribution.containsKey((Object)TerrainType.FLOODPLAIN) || typeDistribution.containsKey((Object)TerrainType.ALLUVIAL_FAN);
        Object features = "";
        if (hasMountain) {
            features = (String)features + "MOUNTAIN,";
        }
        if (hasPlateau) {
            features = (String)features + "PLATEAU,";
        }
        if (hasPlains) {
            features = (String)features + "PLAINS,";
        }
        if (!((String)features).isEmpty()) {
            features = ((String)features).substring(0, ((String)features).length() - 1);
        }
        return String.format("%s | %s | Features=[%s]", rangeScore, diversityScore, ((String)features).isEmpty() ? "NONE" : features);
    }

    public static void logControlPointDetail(TerrainControlPoint point, int worldX, int worldZ) {
        if (!TerrainDebugSystem.isLoggingEnabled()) {
            return;
        }
        double distance = Math.sqrt(point.squaredDistanceTo(worldX, worldZ));
        double influence = point.calculateInfluence(worldX, worldZ);
        LOGGER.debug("{} CP(x={},z={},type={},offset={},radius={},distToRef={},influence={})", new Object[]{LOG_PREFIX, point.x, point.z, point.terrainType.getId(), String.format("%.1f", point.elevationOffset), String.format("%.1f", point.influenceRadius), String.format("%.1f", distance), String.format("%.3f", influence)});
    }

    public static void logMacroRegionInfo(int x, int z, MacroRegionInfo macroInfo) {
        if (!TerrainDebugSystem.isLoggingEnabled()) {
            return;
        }
        LOGGER.debug("{} Macro(x={},z={},tier={},baseHeight={},tectonic={},climate={},blendWeight={},transWidth={})", new Object[]{LOG_PREFIX, x, z, macroInfo.elevationTier, String.format("%.1f", macroInfo.blendedBaseHeight), macroInfo.tectonic, macroInfo.climate, String.format("%.3f", macroInfo.blendWeight), macroInfo.transitionWidth});
    }

    public static void logRegionOverview(ControlPointRegion region) {
        if (!TerrainDebugSystem.isLoggingEnabled()) {
            return;
        }
        List<TerrainControlPoint> points = region.getControlPoints();
        HashMap<TerrainType, Integer> typeCounts = new HashMap<TerrainType, Integer>();
        double minOffset = Double.MAX_VALUE;
        double maxOffset = Double.MIN_VALUE;
        double sumOffset = 0.0;
        for (TerrainControlPoint point : points) {
            typeCounts.merge(point.terrainType, 1, Integer::sum);
            minOffset = Math.min(minOffset, point.elevationOffset);
            maxOffset = Math.max(maxOffset, point.elevationOffset);
            sumOffset += point.elevationOffset;
        }
        double avgOffset = points.isEmpty() ? 0.0 : sumOffset / (double)points.size();
        LOGGER.info("{} Region ({},{}): {} points, offsetRange=[{}..{}] avg={}", new Object[]{LOG_PREFIX, region.getRegionX(), region.getRegionZ(), points.size(), String.format("%.1f", minOffset), String.format("%.1f", maxOffset), String.format("%.1f", avgOffset)});
        StringBuilder summary = new StringBuilder("  TypeDist=");
        typeCounts.entrySet().stream().sorted(Map.Entry.<TerrainType, Integer>comparingByValue().reversed()).forEach(e -> summary.append(((TerrainType)((Object)((Object)e.getKey()))).getId()).append(":").append(e.getValue()).append(" "));
        LOGGER.debug("{} {}", (Object)LOG_PREFIX, (Object)summary);
    }
}

