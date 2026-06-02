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
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DebugPillarManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DebugPillarManager.class);
    private static final ConcurrentHashMap<Long, BlockPos> PLACED_BLOCKS = new ConcurrentHashMap();
    private static final Set<Block> PILLAR_BLOCKS = Set.of(
        Blocks.GLOWSTONE,
        Blocks.RED_STAINED_GLASS, Blocks.ORANGE_STAINED_GLASS, Blocks.YELLOW_STAINED_GLASS,
        Blocks.LIME_STAINED_GLASS, Blocks.GREEN_STAINED_GLASS, Blocks.BLUE_STAINED_GLASS,
        Blocks.CYAN_STAINED_GLASS, Blocks.PURPLE_STAINED_GLASS, Blocks.WHITE_STAINED_GLASS,
        Blocks.LIGHT_BLUE_STAINED_GLASS, Blocks.GRAY_STAINED_GLASS, Blocks.GLASS,
        Blocks.RED_CONCRETE, Blocks.ORANGE_CONCRETE, Blocks.YELLOW_CONCRETE,
        Blocks.LIME_CONCRETE, Blocks.GREEN_CONCRETE, Blocks.CYAN_CONCRETE,
        Blocks.PURPLE_CONCRETE, Blocks.LIGHT_BLUE_CONCRETE, Blocks.WHITE_CONCRETE,
        Blocks.RED_CONCRETE_POWDER, Blocks.CYAN_CONCRETE_POWDER, Blocks.YELLOW_CONCRETE_POWDER,
        Blocks.GREEN_CONCRETE_POWDER, Blocks.MAGMA_BLOCK
    );

    private static BlockState getBlockForTerrainType(TerrainType type) {
        if (type == TerrainType.HIGH_MOUNTAINS || type == TerrainType.PEAK || type == TerrainType.HORN) {
            return Blocks.RED_STAINED_GLASS.defaultBlockState();
        } else if (type == TerrainType.RIDGE || type == TerrainType.CLIFF || type == TerrainType.SEA_CLIFF) {
            return Blocks.ORANGE_STAINED_GLASS.defaultBlockState();
        } else if (type == TerrainType.HILLS || type == TerrainType.ALLUVIAL_FAN || type == TerrainType.VALLEY) {
            return Blocks.YELLOW_STAINED_GLASS.defaultBlockState();
        } else if (type == TerrainType.PLATEAU || type == TerrainType.DOME) {
            return Blocks.PURPLE_STAINED_GLASS.defaultBlockState();
        } else if (type == TerrainType.PLAINS || type == TerrainType.FLOODPLAIN) {
            return Blocks.LIME_STAINED_GLASS.defaultBlockState();
        } else if (type == TerrainType.CANYON || type == TerrainType.GLACIAL_VALLEY || type == TerrainType.BASIN) {
            return Blocks.BLUE_STAINED_GLASS.defaultBlockState();
        } else if (type == TerrainType.BEACH || type == TerrainType.DELTA || type == TerrainType.FJORD || type == TerrainType.SEA_PLATEAU || type == TerrainType.TRENCH) {
            return Blocks.CYAN_STAINED_GLASS.defaultBlockState();
        } else if (type == TerrainType.DUNE || type == TerrainType.GOBI || type == TerrainType.YARDANG || type == TerrainType.SALT_FLAT) {
            return Blocks.WHITE_STAINED_GLASS.defaultBlockState();
        } else if (type == TerrainType.ICE_SHEET || type == TerrainType.CIRQUE) {
            return Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
        } else if (type == TerrainType.PEAK_FOREST || type == TerrainType.SINKHOLE) {
            return Blocks.GRAY_STAINED_GLASS.defaultBlockState();
        }
        return Blocks.GLASS.defaultBlockState();
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
        if (type == TerrainType.TRENCH) {
            return 0;
        } else if (type == TerrainType.SEA_PLATEAU) {
            return 1;
        } else if (type == TerrainType.BEACH || type == TerrainType.DELTA || type == TerrainType.FJORD) {
            return 2;
        } else if (type == TerrainType.PLAINS || type == TerrainType.FLOODPLAIN || type == TerrainType.YARDANG || type == TerrainType.SALT_FLAT) {
            return 3;
        } else if (type == TerrainType.HILLS || type == TerrainType.ALLUVIAL_FAN || type == TerrainType.VALLEY || type == TerrainType.GLACIAL_VALLEY || type == TerrainType.DUNE || type == TerrainType.GOBI) {
            return 4;
        } else if (type == TerrainType.RIDGE || type == TerrainType.SEA_CLIFF || type == TerrainType.PLATEAU || type == TerrainType.DOME) {
            return 5;
        } else if (type == TerrainType.HIGH_MOUNTAINS || type == TerrainType.PEAK || type == TerrainType.HORN || type == TerrainType.CLIFF || type == TerrainType.PEAK_FOREST) {
            return 6;
        }
        return 3;
    }

    public static void clearAllPillars(Level level) {
        if (level == null) {
            LOGGER.warn("[World Scape] [DebugPillar] Cannot clear pillars: level is null");
            return;
        }
        int cleared = 0;
        for (BlockPos pos : PLACED_BLOCKS.values()) {
            if (!PILLAR_BLOCKS.contains(level.getBlockState(pos).getBlock())) continue;
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

