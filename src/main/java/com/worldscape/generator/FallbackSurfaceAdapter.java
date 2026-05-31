/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.server.level.WorldGenRegion
 *  net.minecraft.world.level.biome.Biome
 *  net.minecraft.world.level.block.Blocks
 *  net.minecraft.world.level.block.state.BlockState
 *  net.minecraft.world.level.chunk.ChunkAccess
 *  net.minecraft.world.level.chunk.ChunkGenerator
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.worldscape.generator;

import com.worldscape.generator.SurfaceAdapter;
import java.lang.reflect.Method;
import java.util.Random;
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
    private final ChunkGenerator generator;
    private final long worldSeed;
    private final int seaLevel;

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
        int surfaceLevel = context.getSeaLevel();
        for (int x = 0; x < 16; ++x) {
            for (int z = 0; z < 16; ++z) {
                int terrainHeight = heightMap[x][z];
                int worldX = minX + x;
                int worldZ = minZ + z;
                Biome biome = null;
                try {
                    biome = (Biome)region.getBiome(new BlockPos(worldX, terrainHeight, worldZ)).value();
                }
                catch (Exception e) {
                    LOGGER.debug("{} Failed to get biome at ({}, {}, {}), using default", new Object[]{MOD_ID, worldX, terrainHeight, worldZ});
                }
                String biomeId = "minecraft:plains";
                if (biome != null) {
                    try {
                        Method getKeyMethod = Biome.class.getMethod("getKey", new Class[0]);
                        Object key = getKeyMethod.invoke((Object)biome, new Object[0]);
                        if (key != null) {
                            Method toStringMethod = key.getClass().getMethod("toString", new Class[0]);
                            biomeId = toStringMethod.invoke(key, new Object[0]).toString();
                        }
                    }
                    catch (Exception getKeyMethod) {
                        // empty catch block
                    }
                }
                boolean isHighAltitude = terrainHeight > surfaceLevel + 30;
                boolean isMountain = biomeId.contains("mountain") || biomeId.contains("highland") || biomeId.contains("summit") || biomeId.contains("peak");
                boolean isSnowy = biomeId.contains("snowy") || biomeId.contains("ice") || biomeId.contains("frozen");
                boolean isDesert = biomeId.contains("desert") || biomeId.contains("badlands");
                boolean isBeach = biomeId.contains("beach") || biomeId.contains("shore");
                boolean isStony = biomeId.contains("stone") || biomeId.contains("rocky") || biomeId.contains("gravel") || biomeId.contains("mountains");
                long stoneVariantSeed = (long)worldX * 31341L + (long)worldZ * 45231L + this.worldSeed;
                Random stoneRand = new Random(stoneVariantSeed);
                BlockState deepStone = this.getRandomStoneVariant(stoneRand);
                for (int y = minY; y < maxY; ++y) {
                    BlockPos pos = new BlockPos(worldX, y, worldZ);
                    if (y > terrainHeight) {
                        boolean isOceanBiome;
                        if (y > surfaceLevel) continue;
                        boolean bl = isOceanBiome = biomeId.contains("ocean") || biomeId.contains("deep_ocean") || biomeId.contains("sea") || biomeId.contains("cold_ocean") || biomeId.contains("frozen_ocean") || biomeId.contains("lukewarm_ocean") || biomeId.contains("warm_ocean");
                        if (!isOceanBiome) continue;
                        chunk.setBlockState(pos, water, false);
                        continue;
                    }
                    if (y == terrainHeight) {
                        BlockState surfaceBlock = this.determineSurfaceBlock(biomeId, isHighAltitude, isMountain, isSnowy, isDesert, isBeach, isStony, terrainHeight, surfaceLevel);
                        chunk.setBlockState(pos, surfaceBlock, false);
                        continue;
                    }
                    // @AESTHETIC: Sub-surface layering — dirt on dry land, sand near sea level/desert, gravel on mountain
                    // 次表层分层：干燥陆地用泥土，近海/沙漠用沙子，山地用砂砾
                    if (y > terrainHeight - 4) {
                        if (terrainHeight <= surfaceLevel) {
                            if (isStony || terrainHeight < surfaceLevel - 5) {
                                chunk.setBlockState(pos, gravel, false);
                                continue;
                            }
                            chunk.setBlockState(pos, sand, false);
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
                    // @AESTHETIC: Extended stone variant mixing — deeper range with higher variant probability
                    // 扩展石头变体混合 —— 更深范围，更高变体概率，深层使用深板岩
                    if (y > terrainHeight - 32) {
                        if (stoneRand.nextInt(4) == 0) {
                            chunk.setBlockState(pos, this.getRandomStoneVariant(stoneRand), false);
                            continue;
                        }
                        chunk.setBlockState(pos, stone, false);
                        continue;
                    }
                    if (y > 0) {
                        if (stoneRand.nextInt(3) == 0) {
                            chunk.setBlockState(pos, this.getRandomStoneVariant(stoneRand), false);
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

    private BlockState getRandomStoneVariant(Random rand) {
        int roll = rand.nextInt(100);
        if (roll < 10) {
            return Blocks.GRANITE.defaultBlockState();
        }
        if (roll < 20) {
            return Blocks.DIORITE.defaultBlockState();
        }
        if (roll < 30) {
            return Blocks.ANDESITE.defaultBlockState();
        }
        if (roll < 33) {
            return Blocks.COBBLESTONE.defaultBlockState();
        }
        return Blocks.STONE.defaultBlockState();
    }

    private BlockState determineSurfaceBlock(String biomeId, boolean isHighAltitude, boolean isMountain, boolean isSnowy, boolean isDesert, boolean isBeach, boolean isStony, int terrainHeight, int seaLevel) {
        if (isSnowy || isHighAltitude && terrainHeight > seaLevel + 50) {
            return Blocks.SNOW_BLOCK.defaultBlockState();
        }
        if (isDesert || biomeId.contains("mesa")) {
            return Blocks.SAND.defaultBlockState();
        }
        if (biomeId.contains("savanna")) {
            return Blocks.GRASS_BLOCK.defaultBlockState();
        }
        if (biomeId.contains("forest") || biomeId.contains("plains") || biomeId.contains("sunflower") || biomeId.contains("birch") || biomeId.contains("dark_oak") || biomeId.contains("flower") || biomeId.contains("meadow") || biomeId.contains("grove") || biomeId.contains("taiga")) {
            return Blocks.GRASS_BLOCK.defaultBlockState();
        }
        if (isBeach) {
            return Blocks.SAND.defaultBlockState();
        }
        if (isStony || isMountain || biomeId.contains("stone") || biomeId.contains("gravel")) {
            return Blocks.GRAVEL.defaultBlockState();
        }
        if (biomeId.contains("swamp") || biomeId.contains("mangrove") || biomeId.contains("jungle") || biomeId.contains("bayou")) {
            return Blocks.GRASS_BLOCK.defaultBlockState();
        }
        if (biomeId.contains("deep_ocean") || biomeId.contains("deep_cold") || biomeId.contains("deep_frozen") || biomeId.contains("deep_lukewarm") || biomeId.contains("deep_warm")) {
            return Blocks.GRAVEL.defaultBlockState();
        }
        if (biomeId.contains("ocean") || biomeId.contains("warm_ocean") || biomeId.contains("cold_ocean") || biomeId.contains("lukewarm") || biomeId.contains("frozen_ocean")) {
            return Blocks.SAND.defaultBlockState();
        }
        return Blocks.GRASS_BLOCK.defaultBlockState();
    }
}

