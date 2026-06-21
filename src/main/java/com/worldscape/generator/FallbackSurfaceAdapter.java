package com.worldscape.generator;

import com.worldscape.generator.SurfaceAdapter;
import com.worldscape.WorldScape;
import com.worldscape.terrain.TerrainType;
import com.worldscape.terrain.WorldScapeConstants;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FallbackSurfaceAdapter
implements SurfaceAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(FallbackSurfaceAdapter.class);
    private static final String MOD_ID = "[World Scape] [FallbackSurfaceAdapter]";
    private static final ThreadLocal<Random> THREAD_LOCAL_RANDOM = ThreadLocal.withInitial(Random::new);
    private final ChunkGenerator generator;
    private final long worldSeed;
    private final int seaLevel;
    private final Map<Biome, String> biomeIdCache = new HashMap<>();

    public FallbackSurfaceAdapter(ChunkGenerator generator, long worldSeed, int seaLevel) {
        this.generator = generator;
        this.worldSeed = worldSeed;
        this.seaLevel = seaLevel;
    }

    @Override
    public String getName() {
        return "FallbackSurfaceAdapter";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean buildSurface(SurfaceAdapter.SurfaceBuildContext context) {
        try {
            this.doBuildSurface(context);
            LOGGER.debug("{} Fallback surface built successfully", (Object)MOD_ID);
            return true;
        }
        catch (Exception e) {
            LOGGER.warn("{} Fallback surface build failed: {}", (Object)MOD_ID, (Object)e.getMessage());
            return false;
        }
    }

    private void doBuildSurface(SurfaceAdapter.SurfaceBuildContext context) {
        ChunkAccess chunk = (ChunkAccess)context.getChunk();
        WorldGenRegion region = (WorldGenRegion)context.getRegion();
        int[][] heightMap = context.getHeightMap();
        // 获取地形类型映射表，优先使用 TerrainType 而非 biome 字符串匹配 / Get terrain type map, prefer TerrainType over biome string matching
        TerrainType[][] terrainTypeMap = context.getTerrainTypeMap();
        int minY = context.getMinY();
        int maxY = context.getMaxY();
        int minX = context.getMinBlockX();
        int minZ = context.getMinBlockZ();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState sand = Blocks.SAND.defaultBlockState();
        BlockState gravel = Blocks.GRAVEL.defaultBlockState();
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState snowBlock = Blocks.SNOW_BLOCK.defaultBlockState();
        BlockState andesite = Blocks.ANDESITE.defaultBlockState();
        BlockState granite = Blocks.GRANITE.defaultBlockState();
        BlockState diorite = Blocks.DIORITE.defaultBlockState();
        BlockState cobblestone = Blocks.COBBLESTONE.defaultBlockState();
        BlockState packedIce = Blocks.PACKED_ICE.defaultBlockState();
        int surfaceLevel = context.getSeaLevel();
        for (int x = 0; x < 16; ++x) {
            for (int z = 0; z < 16; ++z) {
                int terrainHeight = heightMap[x][z];
                int worldX = minX + x;
                int worldZ = minZ + z;

                // 优先使用 TerrainType 确定表面方块 / Prefer TerrainType for surface block determination
                TerrainType terrainType = (terrainTypeMap != null) ? terrainTypeMap[x][z] : null;

                // 仅在 terrainTypeMap 不可用时才使用 biome 字符串匹配作为回退 / Only use biome string matching as fallback when terrainTypeMap is unavailable
                Biome biome = null;
                String biomeId = "minecraft:plains";
                boolean useBiomeFallback = (terrainType == null);

                if (useBiomeFallback) {
                    try {
                        biome = (Biome)region.getBiome(new BlockPos(worldX, terrainHeight, worldZ)).value();
                    }
                    catch (Exception e) {
                        LOGGER.debug("{} Failed to get biome at ({}, {}, {}), using default", new Object[]{MOD_ID, worldX, terrainHeight, worldZ});
                    }
                    if (biome != null) {
                        // @PERF: Cache biomeId to avoid repeated reflection per column
                        // 缓存 biomeId 避免每列重复反射
                        biomeId = this.biomeIdCache.computeIfAbsent(biome, b -> {
                            try {
                                Method getKeyMethod = Biome.class.getMethod("getKey", new Class[0]);
                                Object key = getKeyMethod.invoke(b, new Object[0]);
                                if (key != null) {
                                    Method toStringMethod = key.getClass().getMethod("toString", new Class[0]);
                                    return toStringMethod.invoke(key, new Object[0]).toString();
                                }
                            }
                            catch (Exception e) {
                                // 反射获取 biome ID 失败，使用默认值 / Failed to get biome ID via reflection, using default
                                LOGGER.debug("{} Failed to get biome ID via reflection: {}", (Object)MOD_ID, (Object)e.getClass().getSimpleName());
                            }
                            return "minecraft:plains";
                        });
                    }
                }

                boolean isMountain = biomeId.contains("mountain") || biomeId.contains("highland") || biomeId.contains("summit") || biomeId.contains("peak");
                boolean isSnowy = biomeId.contains("snowy") || biomeId.contains("ice") || biomeId.contains("frozen");
                boolean isDesert = biomeId.contains("desert") || biomeId.contains("badlands");
                boolean isBeach = biomeId.contains("beach") || biomeId.contains("shore");
                boolean isStony = biomeId.contains("stone") || biomeId.contains("rocky") || biomeId.contains("gravel") || biomeId.contains("mountains");
                long stoneVariantSeed = (long)worldX * 31341L + (long)worldZ * 45231L + this.worldSeed;
                Random stoneRand = THREAD_LOCAL_RANDOM.get();
                stoneRand.setSeed(stoneVariantSeed);
                BlockState deepStone = getRandomStoneVariant(stoneRand);
                for (int y = minY; y < maxY; ++y) {
                    BlockPos pos = new BlockPos(worldX, y, worldZ);
                    if (y > terrainHeight) {
                        boolean isOceanBiome;
                        if (y > surfaceLevel) continue;
                        // 水下填充：优先根据 TerrainType 判断 / Underwater fill: prefer TerrainType-based check
                        if (terrainType != null) {
                            if (!isUnderwaterTerrainType(terrainType)) continue;
                        } else {
                            boolean bl = isOceanBiome = biomeId.contains("ocean") || biomeId.contains("deep_ocean") || biomeId.contains("sea") || biomeId.contains("cold_ocean") || biomeId.contains("frozen_ocean") || biomeId.contains("lukewarm_ocean") || biomeId.contains("warm_ocean");
                            if (!isOceanBiome) continue;
                        }
                        chunk.setBlockState(pos, water, false);
                        continue;
                    }
                    if (y == terrainHeight) {
                        // 优先使用 TerrainType 确定表面方块 / Prefer TerrainType for surface block
                        BlockState surfaceBlock;
                        if (terrainType != null) {
                            surfaceBlock = determineSurfaceBlockByTerrainType(terrainType, terrainHeight, surfaceLevel);
                        } else {
                            // Fallback: biome-based surface block determination removed, use GRASS_BLOCK as safest default
                            // 回退：基于 biome 的表面方块判断已移除，使用 GRASS_BLOCK 作为最安全的默认值
                            surfaceBlock = Blocks.GRASS_BLOCK.defaultBlockState();
                            LOGGER.warn("{} TerrainType is null at ({}, {}), using GRASS_BLOCK as fallback surface block", MOD_ID, worldX, worldZ);
                        }
                        chunk.setBlockState(pos, surfaceBlock, false);
                        continue;
                    }
                    // @AESTHETIC: Sub-surface layering based on TerrainType / 基于 TerrainType 的次表层分层
                    if (y > terrainHeight - WorldScapeConstants.SUBSURFACE_LAYER_DEPTH) {
                        if (terrainHeight <= surfaceLevel) {
                            // 水下次表层 / Underwater sub-surface
                            if (terrainType != null) {
                                chunk.setBlockState(pos, determineSubSurfaceBlockByTerrainType(terrainType, true), false);
                                continue;
                            }
                            if (isStony || terrainHeight < surfaceLevel - 5) {
                                chunk.setBlockState(pos, gravel, false);
                                continue;
                            }
                            chunk.setBlockState(pos, sand, false);
                            continue;
                        }
                        // 陆上次表层 / Land sub-surface
                        if (terrainType != null) {
                            chunk.setBlockState(pos, determineSubSurfaceBlockByTerrainType(terrainType, false), false);
                            continue;
                        }
                        // @AESTHETIC: Glacier and ice terrain — use packed ice sub-surface for snowy high-altitude areas
                        // 冰川和冰原地形 — 高海拔雪地次表层使用浮冰
                        if (isSnowy && terrainHeight > surfaceLevel + 30) {
                            chunk.setBlockState(pos, packedIce, false);
                            continue;
                        }
                        if (isStony || isMountain) {
                            chunk.setBlockState(pos, gravel, false);
                            continue;
                        }
                        if (isDesert || isBeach) {
                            chunk.setBlockState(pos, sand, false);
                            continue;
                        }
                        chunk.setBlockState(pos, dirt, false);
                        continue;
                    }
                    // @AESTHETIC: Extended stone variant veins — 8x8x8 vein grouping via seed hashing,
                    // producing natural mineral deposits instead of per-block random patches.
                    // 扩展石头变体矿脉 —— 8x8x8 矿脉分组，基于种子哈希生成自然矿脉，而非逐方块随机斑点。
                    if (y > terrainHeight - 32) {
                        if (stoneRand.nextInt(4) == 0) {
                            chunk.setBlockState(pos, getVeinStoneVariant(worldSeed, worldX, y, worldZ), false);
                            continue;
                        }
                        chunk.setBlockState(pos, stone, false);
                        continue;
                    }
                    if (y > 0) {
                        if (stoneRand.nextInt(3) == 0) {
                            chunk.setBlockState(pos, getVeinStoneVariant(worldSeed, worldX, y, worldZ), false);
                            continue;
                        }
                        chunk.setBlockState(pos, stone, false);
                        continue;
                    }
                    chunk.setBlockState(pos, Blocks.DEEPSLATE.defaultBlockState(), false);
                }
            }
        }
    }

    static BlockState getRandomStoneVariant(Random rand) {
        int roll = rand.nextInt(WorldScapeConstants.STONE_VARIANT_ROLL_RANGE);
        if (roll < WorldScapeConstants.GRANITE_THRESHOLD) {
            return Blocks.GRANITE.defaultBlockState();
        }
        if (roll < WorldScapeConstants.DIORITE_THRESHOLD) {
            return Blocks.DIORITE.defaultBlockState();
        }
        if (roll < WorldScapeConstants.ANDESITE_THRESHOLD) {
            return Blocks.ANDESITE.defaultBlockState();
        }
        if (roll < WorldScapeConstants.COBBLESTONE_THRESHOLD) {
            return Blocks.COBBLESTONE.defaultBlockState();
        }
        return Blocks.STONE.defaultBlockState();
    }

    /**
     * Noise-driven stone variant using vein grouping (8x8x8 blocks per vein).
     * Groups blocks into veins via seed-derived hashing — no new noise fields required.
     * Produces natural-looking mineral deposits instead of per-block random patches.
     * 基于种子的矿脉生成（8x8x8 块为一组），无需新噪声场即可产生自然的矿物脉状分布。
     */
    static BlockState getVeinStoneVariant(long worldSeed, int worldX, int worldY, int worldZ) {
        int veinX = Math.floorDiv(worldX, WorldScapeConstants.STONE_VEIN_SIZE);
        int veinY = Math.floorDiv(worldY, WorldScapeConstants.STONE_VEIN_SIZE);
        int veinZ = Math.floorDiv(worldZ, WorldScapeConstants.STONE_VEIN_SIZE);
        long hash = (long)veinX * WorldScapeConstants.STONE_HASH_X
                  + (long)veinY * 17389L
                  + (long)veinZ * WorldScapeConstants.STONE_HASH_Z
                  + worldSeed;
        int roll = Math.abs((int)(hash % WorldScapeConstants.STONE_VARIANT_ROLL_RANGE));
        if (roll < WorldScapeConstants.GRANITE_THRESHOLD) {
            return Blocks.GRANITE.defaultBlockState();
        }
        if (roll < WorldScapeConstants.DIORITE_THRESHOLD) {
            return Blocks.DIORITE.defaultBlockState();
        }
        if (roll < WorldScapeConstants.ANDESITE_THRESHOLD) {
            return Blocks.ANDESITE.defaultBlockState();
        }
        if (roll < WorldScapeConstants.COBBLESTONE_THRESHOLD) {
            return Blocks.COBBLESTONE.defaultBlockState();
        }
        return Blocks.STONE.defaultBlockState();
    }

    /**
     * Check if a terrain type is an alpine (high-elevation mountain) type that
     * should show altitude-dependent surface blocks (snow at peak, bare rock mid-mountain).
     * 检查地形类型是否为高山类型，应显示海拔相关的表面方块（山顶雪、山腰裸岩）。
     */
    static boolean isAlpineTerrainType(TerrainType terrainType) {
        return terrainType == TerrainType.HIGH_MOUNTAINS
            || terrainType == TerrainType.PEAK
            || terrainType == TerrainType.HORN
            || terrainType == TerrainType.RIDGE
            || terrainType == TerrainType.PLATEAU
            || terrainType == TerrainType.CLIFF
            || terrainType == TerrainType.DOME;
    }

    /**
     * 根据 TerrainType 确定表面方块，替代 biome 字符串匹配。
     * Determine surface block based on TerrainType, replacing biome string matching.
     *
     * @param terrainType  地形类型 / terrain type
     * @param terrainHeight 地形高度 / terrain height
     * @param seaLevel     海平面 / sea level
     * @return 表面方块状态 / surface block state
     */
    public static BlockState determineSurfaceBlockByTerrainType(TerrainType terrainType, int terrainHeight, int seaLevel) {

        // Altitude-based surface for all alpine mountain types.
        // 所有高山类型的海拔相关表面。
        if (isAlpineTerrainType(terrainType)) {
            int relativeHeight = terrainHeight - seaLevel;
            if (relativeHeight > WorldScapeConstants.ALPINE_SNOW_OFFSET) {
                return Blocks.SNOW_BLOCK.defaultBlockState();
            }
            if (relativeHeight > WorldScapeConstants.ROCK_ALTITUDE_OFFSET) {
                return Blocks.STONE.defaultBlockState();
            }
            // Moderate altitude: grass_block for vegetated mountains, gravel for rocky
            // 中海拔：植被山地用草方块，岩石山地用砂砾
            if (terrainType == TerrainType.PLATEAU || terrainType == TerrainType.DOME) {
                return Blocks.GRASS_BLOCK.defaultBlockState();
            }
            return Blocks.GRAVEL.defaultBlockState();
        }

        // 沙漠/荒漠类型 → 沙子 / Desert/arid types → sand
        if (terrainType == TerrainType.GOBI || terrainType == TerrainType.SALT_FLAT
            || terrainType == TerrainType.DUNE || terrainType == TerrainType.YARDANG) {
            return Blocks.SAND.defaultBlockState();
        }

        // 海滩 → 沙子 / Beach → sand
        if (terrainType == TerrainType.BEACH) {
            return Blocks.SAND.defaultBlockState();
        }

        // 冰雪类型 → 雪块 / Ice/snow types → snow block
        if (terrainType == TerrainType.ICE_SHEET || terrainType == TerrainType.GLACIAL_VALLEY
            || terrainType == TerrainType.CIRQUE) {
            return Blocks.SNOW_BLOCK.defaultBlockState();
        }

        // 水下类型 → 砂砾 / Underwater types → gravel
        if (terrainType == TerrainType.TRENCH || terrainType == TerrainType.SEA_PLATEAU) {
            return Blocks.GRAVEL.defaultBlockState();
        }

        // 三角洲 → 沙子 / Delta → sand
        if (terrainType == TerrainType.DELTA) {
            return Blocks.SAND.defaultBlockState();
        }

        // 峡湾 → 砂砾 / Fjord → gravel
        if (terrainType == TerrainType.FJORD) {
            return Blocks.GRAVEL.defaultBlockState();
        }

        // Canyon / sea cliff → gravel at low altitude, stone at higher
        // 峡谷/海崖 → 低海拔砂砾，高海拔石头
        if (terrainType == TerrainType.CANYON || terrainType == TerrainType.SEA_CLIFF) {
            return Blocks.GRAVEL.defaultBlockState();
        }

        // 草地/平原/丘陵等类型 → 草方块 / Grass/plains/hills types → grass block
        return Blocks.GRASS_BLOCK.defaultBlockState();
    }

    /**
     * 根据 TerrainType 确定次表层方块。
     * Determine sub-surface block based on TerrainType.
     *
     * @param terrainType 地形类型 / terrain type
     * @param isUnderwater 是否在水下 / whether underwater
     * @return 次表层方块状态 / sub-surface block state
     */
    static BlockState determineSubSurfaceBlockByTerrainType(TerrainType terrainType, boolean isUnderwater) {
        // 冰雪类型 → 浮冰 / Ice/snow types → packed ice
        if (terrainType == TerrainType.ICE_SHEET || terrainType == TerrainType.GLACIAL_VALLEY
            || terrainType == TerrainType.CIRQUE) {
            return Blocks.PACKED_ICE.defaultBlockState();
        }

        // 高山/岩石类型 → 砂砾 / Mountain/rocky types → gravel
        if (terrainType == TerrainType.HIGH_MOUNTAINS || terrainType == TerrainType.RIDGE
            || terrainType == TerrainType.PEAK || terrainType == TerrainType.HORN
            || terrainType == TerrainType.CLIFF || terrainType == TerrainType.CANYON
            || terrainType == TerrainType.PLATEAU || terrainType == TerrainType.SEA_CLIFF
            || terrainType == TerrainType.FJORD) {
            return Blocks.GRAVEL.defaultBlockState();
        }

        // 沙漠/荒漠类型 → 沙子 / Desert/arid types → sand
        if (terrainType == TerrainType.GOBI || terrainType == TerrainType.SALT_FLAT
            || terrainType == TerrainType.DUNE || terrainType == TerrainType.YARDANG) {
            return Blocks.SAND.defaultBlockState();
        }

        // 海滩/三角洲 → 沙子 / Beach/delta → sand
        if (terrainType == TerrainType.BEACH || terrainType == TerrainType.DELTA) {
            return Blocks.SAND.defaultBlockState();
        }

        // 水下类型 → 砂砾 / Underwater types → gravel
        if (terrainType == TerrainType.TRENCH || terrainType == TerrainType.SEA_PLATEAU) {
            return Blocks.GRAVEL.defaultBlockState();
        }

        // 水下次表层默认用沙子 / Default underwater sub-surface → sand
        if (isUnderwater) {
            return Blocks.SAND.defaultBlockState();
        }

        // 草地/平原/丘陵等类型 → 泥土 / Grass/plains/hills types → dirt
        return Blocks.DIRT.defaultBlockState();
    }

    /**
     * 判断地形类型是否为水下类型（需要填充水）。
     * Determine whether the terrain type is an underwater type (requires water fill).
     *
     * @param terrainType 地形类型 / terrain type
     * @return 是否为水下类型 / whether it's an underwater type
     */
    static boolean isUnderwaterTerrainType(TerrainType terrainType) {
        return TerrainType.isUnderwaterTerrainType(terrainType);
    }
}

