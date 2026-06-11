/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.level.chunk.ChunkGenerator
 *  net.minecraft.world.level.levelgen.NoiseGeneratorSettings
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.worldscape.generator;

import com.worldscape.generator.FallbackSurfaceAdapter;
import com.worldscape.generator.ReflectionSurfaceAdapter;
import com.worldscape.generator.SurfaceAdapter;
import com.worldscape.terrain.TerrainType;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SurfaceAdapterFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(SurfaceAdapterFactory.class);
    private static final String MOD_ID = "[World Scape] [SurfaceAdapterFactory]";

    private SurfaceAdapterFactory() {
    }

    public static SurfaceAdapter create(AdapterType type, Object generator, NoiseGeneratorSettings settings, long worldSeed) {
        switch (type.ordinal()) {
            case 0: {
                return SurfaceAdapterFactory.createReflectionAdapter(settings, worldSeed, generator);
            }
            case 1: {
                return SurfaceAdapterFactory.createFallbackAdapter(generator, worldSeed, settings.seaLevel());
            }
        }
        return SurfaceAdapterFactory.createAutoAdapter(generator, settings, worldSeed);
    }

    private static SurfaceAdapter createReflectionAdapter(NoiseGeneratorSettings settings, long worldSeed, Object generator) {
        LOGGER.info("{} Creating ReflectionSurfaceAdapter", (Object)MOD_ID);
        if (generator instanceof ChunkGenerator) {
            return new ReflectionSurfaceAdapter(settings, worldSeed, (ChunkGenerator)generator);
        }
        LOGGER.warn("{} Generator is not ChunkGenerator type, creating adapter without generator reference", (Object)MOD_ID);
        return new ReflectionSurfaceAdapter(settings, worldSeed, null);
    }

    private static SurfaceAdapter createFallbackAdapter(Object generator, long worldSeed, int seaLevel) {
        LOGGER.info("{} Creating FallbackSurfaceAdapter", (Object)MOD_ID);
        if (generator instanceof ChunkGenerator) {
            return new FallbackSurfaceAdapter((ChunkGenerator)generator, worldSeed, seaLevel);
        }
        LOGGER.warn("{} Generator is not ChunkGenerator type, using simplified fallback", (Object)MOD_ID);
        return new SimplifiedFallbackAdapter(worldSeed);
    }

    private static SurfaceAdapter createAutoAdapter(Object generator, NoiseGeneratorSettings settings, long worldSeed) {
        SurfaceAdapter reflectionAdapter = SurfaceAdapterFactory.createReflectionAdapter(settings, worldSeed, generator);
        if (reflectionAdapter.isAvailable()) {
            LOGGER.info("{} Using ReflectionSurfaceAdapter (primary)", (Object)MOD_ID);
            return reflectionAdapter;
        }
        LOGGER.warn("{} ReflectionSurfaceAdapter not available, falling back to FallbackSurfaceAdapter", (Object)MOD_ID);
        return SurfaceAdapterFactory.createFallbackAdapter(generator, worldSeed, settings.seaLevel());
    }

    public static enum AdapterType {
        REFLECTION,
        FALLBACK,
        AUTO;

    }

    private static class SimplifiedFallbackAdapter
    implements SurfaceAdapter {
        private static final Logger LOGGER = LoggerFactory.getLogger(SimplifiedFallbackAdapter.class);
        private static final ThreadLocal<Random> THREAD_LOCAL_RANDOM = ThreadLocal.withInitial(Random::new);
        private final long worldSeed;

        private SimplifiedFallbackAdapter(long worldSeed) {
            this.worldSeed = worldSeed;
        }

        @Override
        public String getName() {
            return "SimplifiedFallbackAdapter";
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public boolean buildSurface(SurfaceAdapter.SurfaceBuildContext context) {
            try {
                this.doBuildSurface(context);
                LOGGER.debug("[World Scape] [SimplifiedFallbackAdapter] Surface built successfully");
                return true;
            } catch (Exception e) {
                LOGGER.warn("[World Scape] [SimplifiedFallbackAdapter] Surface build failed: {}", e.getMessage());
                return false;
            }
        }

        private void doBuildSurface(SurfaceAdapter.SurfaceBuildContext context) {
            ChunkAccess chunk = (ChunkAccess) context.getChunk();
            int[][] heightMap = context.getHeightMap();
            TerrainType[][] terrainTypeMap = context.getTerrainTypeMap();
            int minY = context.getMinY();
            int maxY = context.getMaxY();
            int minX = context.getMinBlockX();
            int minZ = context.getMinBlockZ();
            BlockState stone = Blocks.STONE.defaultBlockState();
            BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
            BlockState dirt = Blocks.DIRT.defaultBlockState();
            BlockState sand = Blocks.SAND.defaultBlockState();
            BlockState gravel = Blocks.GRAVEL.defaultBlockState();
            BlockState water = Blocks.WATER.defaultBlockState();
            BlockState snowBlock = Blocks.SNOW_BLOCK.defaultBlockState();
            int surfaceLevel = context.getSeaLevel();

            for (int x = 0; x < 16; ++x) {
                for (int z = 0; z < 16; ++z) {
                    int terrainHeight = heightMap[x][z];
                    int worldX = minX + x;
                    int worldZ = minZ + z;

                    TerrainType terrainType = (terrainTypeMap != null) ? terrainTypeMap[x][z] : null;

                    long stoneVariantSeed = (long) worldX * 31341L + (long) worldZ * 45231L + this.worldSeed;
                    Random stoneRand = THREAD_LOCAL_RANDOM.get();
                    stoneRand.setSeed(stoneVariantSeed);

                    for (int y = minY; y < maxY; ++y) {
                        BlockPos pos = new BlockPos(worldX, y, worldZ);
                        if (y > terrainHeight) {
                            if (y > surfaceLevel) continue;
                            if (terrainType != null && FallbackSurfaceAdapter.isUnderwaterTerrainType(terrainType)) {
                                chunk.setBlockState(pos, water, false);
                            }
                            continue;
                        }
                        if (y == terrainHeight) {
                            BlockState surfaceBlock;
                            if (terrainType != null) {
                                surfaceBlock = FallbackSurfaceAdapter.determineSurfaceBlockByTerrainType(terrainType, terrainHeight, surfaceLevel);
                            } else {
                                // Simplified fallback: snow at high altitude, grass otherwise
                                surfaceBlock = terrainHeight > surfaceLevel + 50 ? snowBlock : grass;
                            }
                            chunk.setBlockState(pos, surfaceBlock, false);
                            continue;
                        }
                        // Sub-surface layering (top 4 blocks below surface)
                        if (y > terrainHeight - 4) {
                            if (terrainHeight <= surfaceLevel) {
                                // Underwater sub-surface
                                if (terrainType != null) {
                                    chunk.setBlockState(pos, FallbackSurfaceAdapter.determineSubSurfaceBlockByTerrainType(terrainType, true), false);
                                } else {
                                    chunk.setBlockState(pos, terrainHeight < surfaceLevel - 5 ? gravel : sand, false);
                                }
                                continue;
                            }
                            // Land sub-surface
                            if (terrainType != null) {
                                chunk.setBlockState(pos, FallbackSurfaceAdapter.determineSubSurfaceBlockByTerrainType(terrainType, false), false);
                            } else {
                                chunk.setBlockState(pos, dirt, false);
                            }
                            continue;
                        }
                        // Stone layer with variant mixing
                        if (y > terrainHeight - 32) {
                            chunk.setBlockState(pos, stoneRand.nextInt(4) == 0
                                ? FallbackSurfaceAdapter.getRandomStoneVariant(stoneRand) : stone, false);
                            continue;
                        }
                        if (y > 0) {
                            chunk.setBlockState(pos, stoneRand.nextInt(3) == 0
                                ? FallbackSurfaceAdapter.getRandomStoneVariant(stoneRand) : stone, false);
                            continue;
                        }
                        chunk.setBlockState(pos, Blocks.DEEPSLATE.defaultBlockState(), false);
                    }
                }
            }
        }
    }
}

