/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.block.Blocks
 *  net.minecraft.world.level.block.state.BlockState
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.worldscape.debug;

import com.worldscape.debug.TerrainDebugSystem;
import com.worldscape.terrain.MacroRegionInfo;
import com.worldscape.terrain.TerrainControlPoint;
import com.worldscape.terrain.TerrainType;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DebugPillarManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DebugPillarManager.class);
    private static final ConcurrentHashMap<Long, BlockPos> PLACED_BLOCKS = new ConcurrentHashMap();

    private static BlockState getBlockForTerrainType(TerrainType type) {
        return switch (type) {
            case TerrainType.HIGH_MOUNTAINS, TerrainType.PEAK, TerrainType.HORN -> Blocks.RED_STAINED_GLASS.defaultBlockState();
            case TerrainType.RIDGE, TerrainType.CLIFF, TerrainType.SEA_CLIFF -> Blocks.ORANGE_STAINED_GLASS.defaultBlockState();
            case TerrainType.HILLS, TerrainType.ALLUVIAL_FAN, TerrainType.VALLEY -> Blocks.YELLOW_STAINED_GLASS.defaultBlockState();
            case TerrainType.PLATEAU, TerrainType.DOME -> Blocks.PURPLE_STAINED_GLASS.defaultBlockState();
            case TerrainType.PLAINS, TerrainType.FLOODPLAIN -> Blocks.LIME_STAINED_GLASS.defaultBlockState();
            case TerrainType.CANYON, TerrainType.GLACIAL_VALLEY, TerrainType.BASIN -> Blocks.BLUE_STAINED_GLASS.defaultBlockState();
            case TerrainType.BEACH, TerrainType.DELTA, TerrainType.FJORD, TerrainType.SEA_PLATEAU, TerrainType.TRENCH -> Blocks.CYAN_STAINED_GLASS.defaultBlockState();
            case TerrainType.DUNE, TerrainType.GOBI, TerrainType.YARDANG, TerrainType.SALT_FLAT -> Blocks.WHITE_STAINED_GLASS.defaultBlockState();
            case TerrainType.ICE_SHEET, TerrainType.CIRQUE -> Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
            case TerrainType.PEAK_FOREST, TerrainType.SINKHOLE -> Blocks.GRAY_STAINED_GLASS.defaultBlockState();
            default -> Blocks.GLASS.defaultBlockState();
        };
    }

    private static BlockState getBlockForElevationTier(int tier) {
        return switch (tier) {
            case 0 -> Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState();
            case 1 -> Blocks.CYAN_CONCRETE.defaultBlockState();
            case 2 -> Blocks.YELLOW_CONCRETE.defaultBlockState();
            case 3 -> Blocks.LIME_CONCRETE.defaultBlockState();
            case 4 -> Blocks.GREEN_CONCRETE.defaultBlockState();
            case 5 -> Blocks.ORANGE_CONCRETE.defaultBlockState();
            case 6 -> Blocks.RED_CONCRETE.defaultBlockState();
            case 7 -> Blocks.PURPLE_CONCRETE.defaultBlockState();
            default -> Blocks.WHITE_CONCRETE.defaultBlockState();
        };
    }

    public static void placeControlPointPillar(Level level, TerrainControlPoint point, PillarType pillarType) {
        if (!TerrainDebugSystem.isPillarsEnabled()) {
            return;
        }
        BlockPos basePos = new BlockPos(point.x, -64, point.z);
        BlockState pillarBlock = pillarType == PillarType.TYPE ? DebugPillarManager.getBlockForTerrainType(point.terrainType) : DebugPillarManager.getBlockForElevationTier(DebugPillarManager.getTierForTerrainType(point.terrainType));
        int pillarHeight = DebugPillarManager.calculatePillarHeight(point.elevationOffset);
        DebugPillarManager.placePillar(level, basePos, pillarHeight, pillarBlock);
        LOGGER.debug("[World Scape] [DebugPillar] Placed pillar at ({},{}) type={} height={} block={}", new Object[]{point.x, point.z, point.terrainType.getId(), pillarHeight, pillarBlock.getBlock().getName().getString()});
    }

    public static void placeMacroVoronoiMarker(Level level, int cellX, int cellZ, double cpX, double cpZ, int tier, MacroRegionInfo.TectonicType tectonic) {
        if (!TerrainDebugSystem.isPillarsEnabled()) {
            return;
        }
        int pillarX = (int)Math.round(cpX);
        int pillarZ = (int)Math.round(cpZ);
        BlockPos basePos = new BlockPos(pillarX, -64, pillarZ);
        BlockState markerBlock = switch (tectonic) {
            default -> throw new MatchException(null, null);
            case MacroRegionInfo.TectonicType.OROGENIC_BELT -> Blocks.RED_CONCRETE_POWDER.defaultBlockState();
            case MacroRegionInfo.TectonicType.SUBDUCTION_ZONE -> Blocks.MAGMA_BLOCK.defaultBlockState();
            case MacroRegionInfo.TectonicType.RIFT_ZONE -> Blocks.CYAN_CONCRETE_POWDER.defaultBlockState();
            case MacroRegionInfo.TectonicType.FAULT_ZONE -> Blocks.YELLOW_CONCRETE_POWDER.defaultBlockState();
            case MacroRegionInfo.TectonicType.CRATON -> Blocks.GREEN_CONCRETE_POWDER.defaultBlockState();
        };
        int pillarHeight = 10 + tier * 20;
        pillarHeight = Math.min(pillarHeight, 256);
        DebugPillarManager.placePillar(level, basePos, pillarHeight, markerBlock);
        LOGGER.debug("[World Scape] [DebugPillar] Macro marker at ({},{}) cell=({},{}), tier={}, tectonic={}", new Object[]{pillarX, pillarZ, cellX, cellZ, tier, tectonic});
    }

    private static void placePillar(Level level, BlockPos basePos, int height, BlockState blockState) {
        int startY = basePos.getY();
        int endY = Math.min(startY + height, level.getMaxBuildHeight());
        for (int y = startY; y < endY; ++y) {
            BlockPos pos = basePos.atY(y);
            level.setBlock(pos, blockState, 3);
            PLACED_BLOCKS.put(pos.asLong(), pos);
        }
        BlockPos topPos = basePos.atY(Math.min(endY, level.getMaxBuildHeight()));
        level.setBlock(topPos, Blocks.GLOWSTONE.defaultBlockState(), 3);
        PLACED_BLOCKS.put(topPos.asLong(), topPos);
    }

    private static int calculatePillarHeight(double offset) {
        int height = (int)(50.0 + offset * 0.6);
        return Math.max(10, Math.min(height, 256));
    }

    private static int getTierForTerrainType(TerrainType type) {
        return switch (type) {
            case TerrainType.TRENCH -> 0;
            case TerrainType.SEA_PLATEAU -> 1;
            case TerrainType.BEACH, TerrainType.DELTA, TerrainType.FJORD -> 2;
            case TerrainType.PLAINS, TerrainType.FLOODPLAIN, TerrainType.YARDANG, TerrainType.SALT_FLAT -> 3;
            case TerrainType.HILLS, TerrainType.ALLUVIAL_FAN, TerrainType.VALLEY, TerrainType.GLACIAL_VALLEY, TerrainType.DUNE, TerrainType.GOBI -> 4;
            case TerrainType.RIDGE, TerrainType.SEA_CLIFF, TerrainType.PLATEAU, TerrainType.DOME -> 5;
            case TerrainType.HIGH_MOUNTAINS, TerrainType.PEAK, TerrainType.HORN, TerrainType.CLIFF, TerrainType.PEAK_FOREST -> 6;
            default -> 3;
        };
    }

    public static void clearAllPillars(Level level) {
        if (level == null) {
            LOGGER.warn("[World Scape] [DebugPillar] Cannot clear pillars: level is null");
            return;
        }
        int cleared = 0;
        for (BlockPos pos : PLACED_BLOCKS.values()) {
            if (!level.getBlockState(pos).getBlock().defaultBlockState().is(Blocks.GLOWSTONE) && !level.getBlockState(pos).getBlock().defaultBlockState().is(Blocks.RED_STAINED_GLASS) && !level.getBlockState(pos).getBlock().defaultBlockState().is(Blocks.ORANGE_STAINED_GLASS) && !level.getBlockState(pos).getBlock().defaultBlockState().is(Blocks.YELLOW_STAINED_GLASS) && !level.getBlockState(pos).getBlock().defaultBlockState().is(Blocks.LIME_STAINED_GLASS) && !level.getBlockState(pos).getBlock().defaultBlockState().is(Blocks.GREEN_STAINED_GLASS) && !level.getBlockState(pos).getBlock().defaultBlockState().is(Blocks.BLUE_STAINED_GLASS) && !level.getBlockState(pos).getBlock().defaultBlockState().is(Blocks.CYAN_STAINED_GLASS) && !level.getBlockState(pos).getBlock().defaultBlockState().is(Blocks.PURPLE_STAINED_GLASS) && !level.getBlockState(pos).getBlock().defaultBlockState().is(Blocks.WHITE_STAINED_GLASS) && !level.getBlockState(pos).getBlock().defaultBlockState().is(Blocks.LIGHT_BLUE_STAINED_GLASS) && !level.getBlockState(pos).getBlock().defaultBlockState().is(Blocks.GRAY_STAINED_GLASS) && !level.getBlockState(pos).getBlock().defaultBlockState().is(Blocks.RED_CONCRETE) && !level.getBlockState(pos).getBlock().defaultBlockState().is(Blocks.ORANGE_CONCRETE) && !level.getBlockState(pos).getBlock().defaultBlockState().is(Blocks.YELLOW_CONCRETE) && !level.getBlockState(pos).getBlock().defaultBlockState().is(Blocks.LIME_CONCRETE) && !level.getBlockState(pos).getBlock().defaultBlockState().is(Blocks.GREEN_CONCRETE) && !level.getBlockState(pos).getBlock().defaultBlockState().is(Blocks.CYAN_CONCRETE) && !level.getBlockState(pos).getBlock().defaultBlockState().is(Blocks.PURPLE_CONCRETE) && !level.getBlockState(pos).getBlock().defaultBlockState().is(Blocks.LIGHT_BLUE_CONCRETE) && !level.getBlockState(pos).getBlock().defaultBlockState().is(Blocks.WHITE_CONCRETE) && !level.getBlockState(pos).getBlock().defaultBlockState().is(Blocks.RED_CONCRETE_POWDER) && !level.getBlockState(pos).getBlock().defaultBlockState().is(Blocks.CYAN_CONCRETE_POWDER) && !level.getBlockState(pos).getBlock().defaultBlockState().is(Blocks.YELLOW_CONCRETE_POWDER) && !level.getBlockState(pos).getBlock().defaultBlockState().is(Blocks.GREEN_CONCRETE_POWDER) && !level.getBlockState(pos).getBlock().defaultBlockState().is(Blocks.MAGMA_BLOCK)) continue;
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            ++cleared;
        }
        PLACED_BLOCKS.clear();
        LOGGER.info("[World Scape] [DebugPillar] Cleared {} debug blocks", (Object)cleared);
    }

    public static int getPlacedBlockCount() {
        return PLACED_BLOCKS.size();
    }

    public static enum PillarType {
        TYPE,
        TIER;

    }
}

