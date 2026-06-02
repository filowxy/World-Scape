/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.datafixers.kinds.App
 *  com.mojang.datafixers.kinds.Applicative
 *  com.mojang.serialization.Codec
 *  com.mojang.serialization.MapCodec
 *  com.mojang.serialization.codecs.RecordCodecBuilder
 *  javax.annotation.ParametersAreNonnullByDefault
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.BlockPos$MutableBlockPos
 *  net.minecraft.core.Holder
 *  net.minecraft.server.level.WorldGenRegion
 *  net.minecraft.util.RandomSource
 *  net.minecraft.world.level.ChunkPos
 *  net.minecraft.world.level.LevelHeightAccessor
 *  net.minecraft.world.level.NoiseColumn
 *  net.minecraft.world.level.StructureManager
 *  net.minecraft.world.level.biome.Biome
 *  net.minecraft.world.level.biome.BiomeManager
 *  net.minecraft.world.level.biome.BiomeSource
 *  net.minecraft.world.level.block.Blocks
 *  net.minecraft.world.level.block.state.BlockState
 *  net.minecraft.world.level.chunk.ChunkAccess
 *  net.minecraft.world.level.chunk.ChunkGenerator
 *  net.minecraft.world.level.chunk.LevelChunkSection
 *  net.minecraft.world.level.chunk.ProtoChunk
 *  net.minecraft.world.level.levelgen.GenerationStep$Carving
 *  net.minecraft.world.level.levelgen.Heightmap$Types
 *  net.minecraft.world.level.levelgen.NoiseGeneratorSettings
 *  net.minecraft.world.level.levelgen.RandomState
 *  net.minecraft.world.level.levelgen.blending.Blender
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.worldscape.generator;

import com.mojang.datafixers.kinds.App;
import com.mojang.datafixers.kinds.Applicative;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.worldscape.biome.TerrainBiomeRules;
import com.worldscape.generator.SurfaceAdapter;
import com.worldscape.generator.SurfaceAdapterFactory;
import com.worldscape.terrain.ControlPointRegion;
import com.worldscape.terrain.HeightCalculator;
import com.worldscape.terrain.MacroRegionInfo;
import com.worldscape.terrain.MacroVoronoiSystem;
import com.worldscape.terrain.NoiseSet;
import com.worldscape.terrain.RegionController;
import com.worldscape.terrain.TerrainCalculator;
import com.worldscape.terrain.TerrainControlPoint;
import com.worldscape.terrain.TerrainFieldSampler;
import com.worldscape.terrain.TerrainType;
import com.worldscape.util.SeedDeriver;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.ParametersAreNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ParametersAreNonnullByDefault
public class LandscapeChunkGenerator
extends ChunkGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger(LandscapeChunkGenerator.class);
    private final long worldSeed;
    private final BiomeSource biomeSource;
    private final Holder<NoiseGeneratorSettings> settings;
    private final int seaLevel;
    private final int minY;
    private final int height;
    private final ThreadLocal<Map<Long, RiverCacheData>> riverCache = ThreadLocal.withInitial(() -> new HashMap(4));
    public static final MapCodec<LandscapeChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(BiomeSource.CODEC.fieldOf("biome_source").forGetter(LandscapeChunkGenerator::getBiomeSource), NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(LandscapeChunkGenerator::getSettings), Codec.LONG.fieldOf("seed").orElse(0L).forGetter(LandscapeChunkGenerator::getWorldSeed)).apply(instance, LandscapeChunkGenerator::new));
    private final AtomicReference<Method> forChunkMethodRef = new AtomicReference();
    private final AtomicReference<Method> fluidPickerMethodRef = new AtomicReference();
    private final AtomicReference<Method> beardifierMethodRef = new AtomicReference();
    private final AtomicReference<Method> noiseRouterMethodRef = new AtomicReference();
    private final AtomicReference<Method> noiseRandomMethodRef = new AtomicReference();
    private final AtomicReference<Method> buildSurfaceMethodRef = new AtomicReference();
    private final AtomicReference<Field> preliminarySurfaceLevelFieldRef = new AtomicReference();
    private final AtomicReference<RegionController> regionControllerRef = new AtomicReference();
    private final AtomicReference<NoiseSet> noiseSetRef = new AtomicReference();
    private final AtomicReference<HeightCalculator> heightCalculatorRef = new AtomicReference();
    private final AtomicReference<TerrainFieldSampler> fieldSamplerRef = new AtomicReference();
    private final AtomicReference<SurfaceAdapter> surfaceAdapterRef = new AtomicReference();
    private static final AtomicInteger warningCount = new AtomicInteger(0);
    private static final AtomicInteger voidWarningCount = new AtomicInteger(0);
    private static final AtomicInteger extremeSlopeCount = new AtomicInteger(0);
    private static volatile boolean diagnosticEnabled = false;

    // @DIAGNOSTIC: Expose counters for debug commands / 暴露计数器供调试命令使用
    public static int getWarningCount() { return warningCount.get(); }
    public static int getVoidWarningCount() { return voidWarningCount.get(); }
    public static int getExtremeSlopeCount() { return extremeSlopeCount.get(); }
    public static void resetDiagnosticCounters() {
        warningCount.set(0);
        voidWarningCount.set(0);
        extremeSlopeCount.set(0);
    }
    public static void setDiagnosticEnabled(boolean enabled) { diagnosticEnabled = enabled; }
    public static boolean isDiagnosticEnabled() { return diagnosticEnabled; }
    private static final boolean DIAGNOSE_OCEAN = true;
    private static final int DIAGNOSE_MAX_CHUNKS = 10;
    private static final AtomicInteger diagChunkCount = new AtomicInteger(0);
    private volatile boolean biomeReflectionAvailable = true;

    public LandscapeChunkGenerator(BiomeSource biomeSource, long worldSeed) {
        super(biomeSource);
        this.biomeSource = biomeSource;
        this.worldSeed = worldSeed;
        this.settings = null;
        this.seaLevel = 63;
        this.minY = -64;
        this.height = 384;
    }

    public LandscapeChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings, long worldSeed) {
        super(biomeSource);
        this.biomeSource = biomeSource;
        this.settings = settings;
        this.worldSeed = worldSeed;
        this.seaLevel = ((NoiseGeneratorSettings)settings.value()).seaLevel();
        this.minY = ((NoiseGeneratorSettings)settings.value()).noiseSettings().minY();
        this.height = ((NoiseGeneratorSettings)settings.value()).noiseSettings().height();
    }

    public LandscapeChunkGenerator(BiomeSource biomeSource, long worldSeed, Holder<NoiseGeneratorSettings> settings) {
        super(biomeSource);
        this.worldSeed = worldSeed;
        this.biomeSource = biomeSource;
        this.settings = settings;
        this.seaLevel = ((NoiseGeneratorSettings)settings.value()).seaLevel();
        this.minY = ((NoiseGeneratorSettings)settings.value()).noiseSettings().minY();
        this.height = ((NoiseGeneratorSettings)settings.value()).noiseSettings().height();
    }

    protected MapCodec<LandscapeChunkGenerator> codec() {
        return CODEC;
    }

    private void detectChunkSlopeAnomalies(int[][] heightMap, TerrainType[][] typeMap, boolean[][] riverMap, double[][] riverDepthMap, int minX, int minZ, RegionController controller, NoiseSet noiseSet, int minY, int maxY) {
        int chunkVoidWarnings = 0;
        int chunkExtremeSlopes = 0;
        for (int x = 1; x < 15; ++x) {
            for (int z = 1; z < 15; ++z) {
                double baseHeight;
                int currentHeight = heightMap[x][z];
                int worldX = minX + x;
                int worldZ = minZ + z;
                RegionController.TerrainBlendResult currentBlend = controller.getTerrainBlend(worldX, worldZ);
                TerrainType currentType = typeMap[x][z];
                double dominantWeight = currentBlend.dominantWeight;
                if (currentHeight < -64) {
                    LOGGER.error("[World Scape] [VOID WARNING] Possible void detected at world({}, {}): height={}, terrainType={}, macroTier={}, blendWeight={}", new Object[]{worldX, worldZ, currentHeight, currentType.getId(), currentBlend.macroInfo.elevationTier, String.format("%.3f", dominantWeight)});
                    voidWarningCount.incrementAndGet();
                    ++chunkVoidWarnings;
                }
                double dominantHeight = baseHeight = currentBlend.blendedHeight;
                double fallbackHeight = baseHeight;
                TerrainType dominantType = currentBlend.dominantType;
                if (dominantType != currentType) {
                    dominantHeight = this.calcHeightForType(worldX, worldZ, baseHeight, dominantType, currentBlend);
                    fallbackHeight = this.calcHeightForType(worldX, worldZ, baseHeight, currentType, currentBlend);
                } else {
                    fallbackHeight = dominantHeight = this.calcHeightForType(worldX, worldZ, baseHeight, currentType, currentBlend);
                }
                int[][] directions = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                String[] dirNames = new String[]{"+X", "-X", "+Z", "-Z"};
                for (int i = 0; i < 4; ++i) {
                    int nx = x + directions[i][0];
                    int nz = z + directions[i][1];
                    int neighborHeight = heightMap[nx][nz];
                    double slopeDiff = Math.abs(currentHeight - neighborHeight);
                    if (slopeDiff > 150.0) {
                        LOGGER.error("[World Scape] [CRITICAL] Extreme height change at world({}, {}) [chunk({}, {})]: height={}, neighborHeight={}, diff={}, terrainType={}, macroTier={}, blendWeight={}, direction={}", new Object[]{worldX, worldZ, x, z, currentHeight, neighborHeight, (int)slopeDiff, currentType.getId(), currentBlend.macroInfo.elevationTier, String.format("%.3f", dominantWeight), dirNames[i]});
                        extremeSlopeCount.incrementAndGet();
                        ++chunkExtremeSlopes;
                        warningCount.incrementAndGet();
                        continue;
                    }
                    if (slopeDiff > 100.0) {
                        LOGGER.warn("[World Scape] [WARNING] Large height change at world({}, {}) [chunk({}, {})]: height={}, neighborHeight={}, diff={}, terrainType={}, macroTier={}, direction={}", new Object[]{worldX, worldZ, x, z, currentHeight, neighborHeight, (int)slopeDiff, currentType.getId(), currentBlend.macroInfo.elevationTier, dirNames[i]});
                        warningCount.incrementAndGet();
                        continue;
                    }
                    if (!(slopeDiff > 30.0)) continue;
                    LOGGER.warn("[World Scape] Chunk slope anomaly at world({}, {}) [chunk({}, {})]: height={}, dominantType={}, fallbackType={}, dominantWeight={}, baseHeight={}, dominantHeight={}, fallbackHeight={}, direction={}, neighborHeight={}, slopeDiff={}", new Object[]{worldX, worldZ, x, z, currentHeight, dominantType, currentType, dominantWeight, baseHeight, dominantHeight, fallbackHeight, dirNames[i], neighborHeight, slopeDiff});
                }
            }
        }
        if (chunkVoidWarnings > 0) {
            LOGGER.error("[World Scape] Chunk ({}, {}) summary: {} void warnings detected", new Object[]{minX / 16, minZ / 16, chunkVoidWarnings});
        }
        if (chunkExtremeSlopes > 0) {
            LOGGER.error("[World Scape] Chunk ({}, {}) summary: {} extreme slope warnings", new Object[]{minX / 16, minZ / 16, chunkExtremeSlopes});
        }
    }

    private double calculateFinalHeight(int x, int z, RegionController.TerrainBlendResult blend, TerrainType type, NoiseSet noiseSet) {
        return TerrainCalculator.calculateFinalHeight(x, z, blend, type, noiseSet, this.getFieldSampler());
    }

    private double getRiverErosionIntensity(int worldX, int worldZ, NoiseSet noiseSet, double baseHeight, int seaLevel, RegionController.TerrainBlendResult blend) {
        return TerrainCalculator.getRiverErosionIntensity(worldX, worldZ, noiseSet, baseHeight, seaLevel, blend);
    }

    private double getAlluvialFactor(int worldX, int worldZ, NoiseSet noiseSet, double baseHeight, int seaLevel) {
        return TerrainCalculator.getAlluvialFactor(worldX, worldZ, noiseSet, baseHeight, seaLevel);
    }

    private TerrainType determineTerrainType(RegionController.TerrainBlendResult blend) {
        return TerrainCalculator.determineTerrainType(blend);
    }

    private int calculateActualSurfaceHeight(int terrainHeight, boolean isRiver, double riverDepth, int minY) {
        return TerrainCalculator.calculateActualSurfaceHeight(terrainHeight, isRiver, riverDepth, minY);
    }

    private int calculateErodedHeight(int worldX, int worldZ, double continuousHeight, boolean isRiver, double riverDepth, int seaLevel, NoiseSet noiseSet, RegionController.TerrainBlendResult blend) {
        return TerrainCalculator.calculateErodedHeight(worldX, worldZ, continuousHeight, isRiver, riverDepth, seaLevel, noiseSet, blend);
    }

    private int calculateErodedHeight(int worldX, int worldZ, double continuousHeight, boolean isRiver, double riverDepth, int seaLevel, NoiseSet noiseSet, RegionController.TerrainBlendResult blend, double erosionIntensity) {
        return TerrainCalculator.calculateErodedHeight(worldX, worldZ, continuousHeight, isRiver, riverDepth, seaLevel, noiseSet, blend, erosionIntensity);
    }

    private int calculateErodedHeight(double continuousHeight, boolean isRiver, double riverDepth, int seaLevel, double erosionIntensity, double alluvialFactor) {
        return TerrainCalculator.calculateErodedHeight(continuousHeight, isRiver, riverDepth, seaLevel, erosionIntensity, alluvialFactor, 1.0);
    }

    private boolean isRiverAt(int worldX, int worldZ, NoiseSet noiseSet) {
        return TerrainCalculator.isRiverAt(worldX, worldZ, noiseSet);
    }

    private double getRiverDepthAt(int worldX, int worldZ, NoiseSet noiseSet, int surfaceHeight, int seaLevel) {
        return TerrainCalculator.getRiverDepthAt(worldX, worldZ, noiseSet, surfaceHeight, seaLevel);
    }

    private double getRiverDepthAt(int worldX, int worldZ, NoiseSet noiseSet, int surfaceHeight, int seaLevel, boolean isRiver) {
        return TerrainCalculator.getRiverDepthAt(worldX, worldZ, noiseSet, surfaceHeight, seaLevel, isRiver);
    }

    private double getRiverDepthAt(int worldX, int worldZ, NoiseSet noiseSet, int surfaceHeight, int seaLevel, boolean isRiver, double depthMultiplier) {
        return TerrainCalculator.getRiverDepthAt(worldX, worldZ, noiseSet, surfaceHeight, seaLevel, isRiver, depthMultiplier);
    }

    private int calculateRiverWaterLevel(int surfaceHeight, boolean isRiver, double riverDepth, int seaLevel, int minY) {
        if (!isRiver || riverDepth <= 0.5) {
            return -1;
        }
        return seaLevel;
    }

    private void world_scape_fillColumn(ProtoChunk protoChunk, int worldX, int worldZ, int minY, int terrainHeight, int seaLevel, TerrainType terrainType, boolean isRiver, double riverDepth, RandomState randomState, RegionController controller, NoiseSet noiseSet, RegionController.TerrainBlendResult cachedBlend, TerrainType cachedType, double cachedContinuousHeight, double cachedErosionIntensity, double cachedAlluvialFactor) {
        int stoneStartY;
        int y;
        double d;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        RegionController.TerrainBlendResult currentBlend = cachedBlend != null ? cachedBlend : controller.getTerrainBlend(worldX, worldZ);
        TerrainType currentType = cachedType != null ? cachedType : this.determineTerrainType(currentBlend);
        double continuousHeight = cachedContinuousHeight;
        if (continuousHeight < -64.0) {
            LOGGER.warn("[World Scape] [WARNING] Possible void at ({}, {}): height={}, terrainType={}, macroTier={}", new Object[]{worldX, worldZ, String.format("%.1f", continuousHeight), currentType.getId(), currentBlend.macroInfo.elevationTier});
            voidWarningCount.incrementAndGet();
        }
        double erosionIntensity = cachedErosionIntensity;
        double alluvialFactor = cachedAlluvialFactor;
        int erodedHeight = this.calculateErodedHeight(continuousHeight, isRiver, riverDepth, seaLevel, erosionIntensity, alluvialFactor);
        int actualSurfaceHeight = this.calculateActualSurfaceHeight(erodedHeight, isRiver, riverDepth, minY);
        int waterLevel = this.calculateRiverWaterLevel(actualSurfaceHeight, isRiver, riverDepth, seaLevel, minY);
        int fillToHeight = actualSurfaceHeight - 1;
        long bedrockSeed = SeedDeriver.deriveSeed(this.worldSeed, (long)worldX * 31L + (long)worldZ * 17L + 388350381470L);
        int bedrockLayers = 1 + RandomSource.create((long)bedrockSeed).nextInt(3);
        int bedrockStartY = minY;
        int bedrockEndY = Math.min(bedrockStartY + bedrockLayers - 1, fillToHeight);
        for (int y2 = bedrockStartY; y2 <= bedrockEndY; ++y2) {
            pos.set(worldX, y2, worldZ);
            protoChunk.setBlockState((BlockPos)pos, Blocks.BEDROCK.defaultBlockState(), false);
        }
        int deepslateStartY = bedrockEndY + 1;
        int deepslateEndY = Math.min(fillToHeight, 0);
        for (int y3 = deepslateStartY; y3 <= deepslateEndY; ++y3) {
            pos.set(worldX, y3, worldZ);
            protoChunk.setBlockState((BlockPos)pos, Blocks.DEEPSLATE.defaultBlockState(), false);
        }
        for (y = stoneStartY = Math.max(deepslateEndY + 1, deepslateStartY); y <= fillToHeight; ++y) {
            pos.set(worldX, y, worldZ);
            protoChunk.setBlockState((BlockPos)pos, Blocks.STONE.defaultBlockState(), false);
        }
        if (isRiver && riverDepth > 0.5) {
            for (y = fillToHeight + 1; y <= waterLevel; ++y) {
                pos.set(worldX, y, worldZ);
                protoChunk.setBlockState((BlockPos)pos, Blocks.AIR.defaultBlockState(), false);
            }
        }
    }

    private void world_scape_buildSurfaceFallback(WorldGenRegion region, ChunkAccess chunk) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        for (int x = 0; x < 16; ++x) {
            for (int z = 0; z < 16; ++z) {
                boolean isOceanBiome;
                int stoneStartY;
                int y;
                int worldX = minX + x;
                int worldZ = minZ + z;
                int surfaceY = this.minY;
                for (int y2 = this.minY + this.height; y2 >= this.minY; --y2) {
                    if (chunk.getBlockState((BlockPos)pos.set(worldX, y2, worldZ)).isAir()) continue;
                    surfaceY = y2;
                    break;
                }
                long bedrockSeed = SeedDeriver.deriveSeed(this.worldSeed, (long)worldX * 31L + (long)worldZ * 17L + 388350381470L);
                int bedrockLayers = 1 + RandomSource.create((long)bedrockSeed).nextInt(3);
                int bedrockEndY = Math.min(this.minY + bedrockLayers - 1, surfaceY);
                for (int y3 = this.minY; y3 <= bedrockEndY; ++y3) {
                    pos.set(worldX, y3, worldZ);
                    chunk.setBlockState((BlockPos)pos, Blocks.BEDROCK.defaultBlockState(), false);
                }
                int deepslateEndY = Math.min(surfaceY, 0);
                for (int y4 = bedrockEndY + 1; y4 <= deepslateEndY; ++y4) {
                    pos.set(worldX, y4, worldZ);
                    chunk.setBlockState((BlockPos)pos, Blocks.DEEPSLATE.defaultBlockState(), false);
                }
                for (y = stoneStartY = Math.max(deepslateEndY + 1, bedrockEndY + 1); y <= surfaceY; ++y) {
                    pos.set(worldX, y, worldZ);
                    chunk.setBlockState((BlockPos)pos, Blocks.STONE.defaultBlockState(), false);
                }
                if (surfaceY >= this.seaLevel) continue;
                int biomeSampleY = Math.max(this.minY, Math.min(surfaceY, this.minY + this.height - 1));
                String biomeId = region.getBiome((BlockPos)pos.set(worldX, biomeSampleY, worldZ)).unwrapKey().map(k -> k.location().toString()).orElse("");
                boolean bl = isOceanBiome = biomeId.contains("ocean") || biomeId.contains("deep_ocean") || biomeId.contains("sea") || biomeId.contains("cold_ocean") || biomeId.contains("frozen_ocean") || biomeId.contains("lukewarm_ocean") || biomeId.contains("warm_ocean");
                if (!isOceanBiome) continue;
                for (y = surfaceY + 1; y <= this.seaLevel; ++y) {
                    pos.set(worldX, y, worldZ);
                    chunk.setBlockState((BlockPos)pos, Blocks.WATER.defaultBlockState(), false);
                }
            }
        }
    }

    public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState randomState, ChunkAccess chunk) {
        if (this.settings == null) {
            LOGGER.warn("[World Scape] Settings not injected, using fallback surface build");
            this.world_scape_buildSurfaceFallback(region, chunk);
            return;
        }
        try {
            SurfaceAdapter adapter = this.getOrCreateSurfaceAdapter();
            if (!adapter.isAvailable()) {
                LOGGER.warn("[World Scape] Surface adapter not available, using fallback");
                this.world_scape_buildSurfaceFallback(region, chunk);
                return;
            }
            int[][] heightMap = new int[16][16];
            boolean[][] riverMap = new boolean[16][16];
            double[][] riverDepthMap = new double[16][16];
            int minX = chunk.getPos().getMinBlockX();
            int minZ = chunk.getPos().getMinBlockZ();
            NoiseSet noiseSet = this.getNoiseSet();
            RiverCacheData cachedRiver = this.riverCache.get().get(ChunkPos.asLong((int)chunk.getPos().x, (int)chunk.getPos().z));
            for (int x = 0; x < 16; ++x) {
                for (int z = 0; z < 16; ++z) {
                    int worldX = minX + x;
                    int worldZ = minZ + z;
                    int surfaceY = this.minY;
                    for (int y = this.minY + this.height; y >= this.minY; --y) {
                        if (chunk.getBlockState(new BlockPos(worldX, y, worldZ)).isAir()) continue;
                        surfaceY = y;
                        break;
                    }
                    heightMap[x][z] = surfaceY;
                    if (cachedRiver != null) {
                        riverMap[x][z] = cachedRiver.riverMap[x][z];
                        riverDepthMap[x][z] = cachedRiver.riverDepthMap[x][z];
                        continue;
                    }
                    boolean isRiverAtResult = this.isRiverAt(worldX, worldZ, noiseSet);
                    riverMap[x][z] = isRiverAtResult;
                    riverDepthMap[x][z] = this.getRiverDepthAt(worldX, worldZ, noiseSet, surfaceY, this.seaLevel, isRiverAtResult);
                }
            }
            SurfaceAdapter.SurfaceBuildContext context = SurfaceAdapter.SurfaceBuildContext.builder().randomState(randomState).chunk(chunk).region(region).settings(this.settings.value()).seaLevel(this.seaLevel).heightMap(heightMap).riverMap(riverMap).riverDepthMap(riverDepthMap).minY(this.minY).maxY(this.minY + this.height).minBlockX(minX).minBlockZ(minZ).build();
            boolean success = adapter.buildSurface(context);
            if (!success) {
                LOGGER.warn("[World Scape] Surface adapter build failed, using fallback");
                this.world_scape_buildSurfaceFallback(region, chunk);
            } else {
                LOGGER.debug("[World Scape] Surface built successfully using {}", (Object)adapter.getName());
            }
        }
        catch (Exception e) {
            LOGGER.warn("[World Scape] Surface build error: {}, using fallback", (Object)e.getMessage());
            this.world_scape_buildSurfaceFallback(region, chunk);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private SurfaceAdapter getOrCreateSurfaceAdapter() {
        SurfaceAdapter adapter = this.surfaceAdapterRef.get();
        if (adapter == null) {
            LandscapeChunkGenerator landscapeChunkGenerator;
            LandscapeChunkGenerator landscapeChunkGenerator2 = landscapeChunkGenerator = this;
            synchronized (landscapeChunkGenerator2) {
                adapter = this.surfaceAdapterRef.get();
                if (adapter == null) {
                    if (this.settings == null) {
                        LOGGER.warn("[World Scape] Settings not available for surface adapter creation");
                        adapter = SurfaceAdapterFactory.create(SurfaceAdapterFactory.AdapterType.FALLBACK, (Object)this, null, this.worldSeed);
                    } else {
                        adapter = SurfaceAdapterFactory.create(SurfaceAdapterFactory.AdapterType.AUTO, (Object)this, (NoiseGeneratorSettings)this.settings.value(), this.worldSeed);
                    }
                    LOGGER.info("[World Scape] Surface adapter created: {}", (Object)adapter.getName());
                    this.surfaceAdapterRef.set(adapter);
                }
            }
        }
        return adapter;
    }

    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
        long startTime = System.nanoTime();
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[World Scape] [PERF] fillFromNoise ENTER chunk ({}, {})", (Object)chunkX, (Object)chunkZ);
        }
        RegionController controller = this.getRegionController();
        long t1 = System.nanoTime();
        long p1 = (t1 - startTime) / 1000000L;
        if (p1 > 50L) {
            LOGGER.warn("[World Scape] [BLOCK-CHK] SLOW getRegionController: {}ms", (Object)p1);
        }
        NoiseSet noiseSet = this.getNoiseSet();
        long t2 = System.nanoTime();
        long p2 = (t2 - t1) / 1000000L;
        if (p2 > 50L) {
            LOGGER.warn("[World Scape] [BLOCK-CHK] SLOW getNoiseSet: {}ms", (Object)p2);
        }
        int[][] heightMap = new int[16][16];
        TerrainType[][] typeMap = new TerrainType[16][16];
        boolean[][] riverMap = new boolean[16][16];
        double[][] riverDepthMap = new double[16][16];
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        RegionController.BlendCache blendCache = this.buildChunkBlendCache(controller, minX, minZ);
        long t3 = System.nanoTime();
        long p3 = (t3 - t2) / 1000000L;
        if (p3 > 100L) {
            LOGGER.warn("[World Scape] [BLOCK-CHK] SLOW buildChunkBlendCache: {}ms, {} points", (Object)p3, (Object)blendCache.allPoints.size());
        }
        RegionController.TerrainBlendResult[][] cachedBlends = new RegionController.TerrainBlendResult[16][16];
        double[][] cachedContinuousHeights = new double[16][16];
        double[][] cachedErosionIntensities = new double[16][16];
        double[][] cachedAlluvialFactors = new double[16][16];
        for (int x = 0; x < 16; ++x) {
            for (int z = 0; z < 16; ++z) {
                double riverDepth;
                boolean isRiver;
                double alluvialFactor;
                double erosionIntensity;
                double finalHeight;
                TerrainType type;
                RegionController.TerrainBlendResult blend;
                int worldX = minX + x;
                int worldZ = minZ + z;
                cachedBlends[x][z] = blend = controller.getTerrainBlend(worldX, worldZ, blendCache);
                typeMap[x][z] = type = this.determineTerrainType(blend);
                cachedContinuousHeights[x][z] = finalHeight = this.calculateFinalHeight(worldX, worldZ, blend, type, noiseSet);
                cachedErosionIntensities[x][z] = erosionIntensity = this.getRiverErosionIntensity(worldX, worldZ, noiseSet, finalHeight, this.seaLevel, blend);
                cachedAlluvialFactors[x][z] = alluvialFactor = this.getAlluvialFactor(worldX, worldZ, noiseSet, finalHeight, this.seaLevel);
                riverMap[x][z] = isRiver = this.isRiverAt(worldX, worldZ, noiseSet);
                double riverDepthMultiplier = TerrainCalculator.getRiverDepthMultiplierForTier(blend.macroInfo.elevationTier);
                riverDepthMap[x][z] = riverDepth = this.getRiverDepthAt(worldX, worldZ, noiseSet, (int)finalHeight, this.seaLevel, isRiver, riverDepthMultiplier);
                int erodedHeight = this.calculateErodedHeight(worldX, worldZ, finalHeight, isRiver, riverDepth, this.seaLevel, noiseSet, blend, erosionIntensity);
                heightMap[x][z] = this.calculateActualSurfaceHeight(erodedHeight, isRiver, riverDepth, this.minY);
            }
        }
        long t4 = System.nanoTime();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[World Scape] [PERF]   Phase4 terrain blend query (256 cells): {}ms", (Object)((t4 - t3) / 1000000L));
        }
        if (com.worldscape.debug.TerrainDebugSystem.isDetailedIssueDetectionEnabled()) {
            this.detectChunkSlopeAnomalies(heightMap, typeMap, riverMap, riverDepthMap, minX, minZ, controller, noiseSet, this.minY, this.minY + this.height);
        }
        if (chunk instanceof ProtoChunk) {
            ProtoChunk protoChunk = (ProtoChunk)chunk;
            this.overrideTerrainBiomesInChunk(protoChunk, minX, minZ, blendCache);
            for (int x = 0; x < 16; ++x) {
                for (int z = 0; z < 16; ++z) {
                    int worldX = minX + x;
                    int worldZ = minZ + z;
                    int terrainHeight = heightMap[x][z];
                    RegionController.TerrainBlendResult blend = cachedBlends[x][z];
                    TerrainType type = typeMap[x][z];
                    boolean isRiver = riverMap[x][z];
                    double riverDepth = riverDepthMap[x][z];
                    this.world_scape_fillColumn(protoChunk, worldX, worldZ, this.minY, terrainHeight, this.seaLevel, type, isRiver, riverDepth, randomState, controller, noiseSet, blend, type, cachedContinuousHeights[x][z], cachedErosionIntensities[x][z], cachedAlluvialFactors[x][z]);
                }
            }
        }
        this.riverCache.get().put(ChunkPos.asLong((int)chunkX, (int)chunkZ), new RiverCacheData(riverMap, riverDepthMap));
        long totalTime = (System.nanoTime() - startTime) / 1000000L;
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[World Scape] [PERF] fillFromNoise EXIT chunk ({}, {}) total: {}ms", new Object[]{chunkX, chunkZ, totalTime});
        }
        return CompletableFuture.completedFuture(chunk);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private RegionController getRegionController() {
        RegionController controller = this.regionControllerRef.get();
        if (controller == null) {
            LandscapeChunkGenerator landscapeChunkGenerator;
            LandscapeChunkGenerator landscapeChunkGenerator2 = landscapeChunkGenerator = this;
            synchronized (landscapeChunkGenerator2) {
                controller = this.regionControllerRef.get();
                if (controller == null) {
                    int effectiveSeaLevel = this.settings != null ? ((NoiseGeneratorSettings)this.settings.value()).seaLevel() : this.seaLevel;
                    controller = new RegionController(this.worldSeed, effectiveSeaLevel);
                    this.regionControllerRef.set(controller);
                }
            }
        }
        return controller;
    }

    private RegionController.BlendCache buildChunkBlendCache(RegionController controller, int minX, int minZ) {
        int regionX = Math.floorDiv(minX, 256);
        int regionZ = Math.floorDiv(minZ, 256);
        ArrayList<TerrainControlPoint> allPoints = new ArrayList<TerrainControlPoint>();
        for (int dx = -1; dx <= 1; ++dx) {
            for (int dz = -1; dz <= 1; ++dz) {
                ControlPointRegion region = controller.getControlPointRegion(regionX + dx, regionZ + dz);
                if (region == null) continue;
                allPoints.addAll(region.getControlPoints());
            }
        }
        return new RegionController.BlendCache(regionX, regionZ, allPoints);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private NoiseSet getNoiseSet() {
        NoiseSet noiseSet = this.noiseSetRef.get();
        if (noiseSet == null) {
            LandscapeChunkGenerator landscapeChunkGenerator;
            LandscapeChunkGenerator landscapeChunkGenerator2 = landscapeChunkGenerator = this;
            synchronized (landscapeChunkGenerator2) {
                noiseSet = this.noiseSetRef.get();
                if (noiseSet == null) {
                    noiseSet = NoiseSet.getOrCreate(this.worldSeed);
                    this.noiseSetRef.set(noiseSet);
                }
            }
        }
        return noiseSet;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private TerrainFieldSampler getFieldSampler() {
        TerrainFieldSampler sampler = this.fieldSamplerRef.get();
        if (sampler == null) {
            LandscapeChunkGenerator landscapeChunkGenerator;
            LandscapeChunkGenerator landscapeChunkGenerator2 = landscapeChunkGenerator = this;
            synchronized (landscapeChunkGenerator2) {
                sampler = this.fieldSamplerRef.get();
                if (sampler == null) {
                    sampler = TerrainFieldSampler.getOrCreate(this.worldSeed);
                    this.fieldSamplerRef.set(sampler);
                }
            }
        }
        return sampler;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private HeightCalculator getHeightCalculator() {
        HeightCalculator calc = this.heightCalculatorRef.get();
        if (calc == null) {
            LandscapeChunkGenerator landscapeChunkGenerator;
            LandscapeChunkGenerator landscapeChunkGenerator2 = landscapeChunkGenerator = this;
            synchronized (landscapeChunkGenerator2) {
                calc = this.heightCalculatorRef.get();
                if (calc == null) {
                    MacroVoronoiSystem sharedMacro = this.getRegionController().getMacroSystem();
                    calc = new HeightCalculator(this.worldSeed, this.seaLevel, sharedMacro);
                    this.heightCalculatorRef.set(calc);
                }
            }
        }
        return calc;
    }

    private void overrideTerrainBiomesInChunk(ProtoChunk protoChunk, int minX, int minZ, RegionController.BlendCache blendCache) {
        HeightCalculator calc = this.getHeightCalculator();
        TerrainBiomeRules biomeRules = TerrainBiomeRules.getInstance();
        int cellsPerXZ = 4;
        int cellsPerSectionY = 4;
        HashMap<TerrainType, Integer> overrideCounts = new HashMap<TerrainType, Integer>();
        int totalOverridden = 0;
        Field biomesField = null;
        Method setMethod = null;
        int minSectionY = protoChunk.getMinSection();
        int maxSectionY = protoChunk.getMaxSection();
        for (int cellX = 0; cellX < cellsPerXZ; ++cellX) {
            for (int cellZ = 0; cellZ < cellsPerXZ; ++cellZ) {
                Holder<Biome> selectedBiome;
                TerrainType terrainType;
                List<Holder<Biome>> allowedBiomes;
                int centerX = minX + cellX * 4 + 2;
                int centerZ = minZ + cellZ * 4 + 2;
                Holder currentBiome = protoChunk.getNoiseBiome(cellX, 0, cellZ);
                if (currentBiome == null || (allowedBiomes = biomeRules.getAllowedBiomes(terrainType = this.determineTerrainType(calc, centerX, centerZ, blendCache))).isEmpty() || allowedBiomes.contains(currentBiome) || (selectedBiome = biomeRules.selectBiomeBySeed(allowedBiomes, this.worldSeed, cellX, cellZ)) == null || !this.biomeReflectionAvailable) continue;
                boolean overrideSucceeded = false;
                try {
                    if (biomesField == null) {
                        biomesField = LevelChunkSection.class.getDeclaredField("biomes");
                        biomesField.setAccessible(true);
                    }
                    for (int sectionY = minSectionY; sectionY <= maxSectionY; ++sectionY) {
                        Object biomesContainer;
                        int sectionIndex = protoChunk.getSectionIndexFromSectionY(sectionY);
                        LevelChunkSection section = protoChunk.getSection(sectionIndex);
                        if (section == null || (biomesContainer = biomesField.get(section)) == null) continue;
                        if (setMethod == null) {
                            setMethod = biomesContainer.getClass().getMethod("set", Integer.TYPE, Integer.TYPE, Integer.TYPE, Object.class);
                        }
                        for (int cellY = 0; cellY < cellsPerSectionY; ++cellY) {
                            setMethod.invoke(biomesContainer, cellX, cellY, cellZ, selectedBiome);
                        }
                    }
                    overrideSucceeded = true;
                }
                catch (NoSuchFieldException e) {
                    LOGGER.error("[World Scape] Biome field not found in LevelChunkSection, API may have changed in this Minecraft version. Falling back to vanilla biome assignment.", (Throwable)e);
                    this.biomeReflectionAvailable = false;
                }
                catch (IllegalAccessException e) {
                    LOGGER.error("[World Scape] Cannot access biome field, module access restrictions may apply. Consider adding --add-opens java.base/java.lang=ALL-UNNAMED to JVM args.", (Throwable)e);
                    this.biomeReflectionAvailable = false;
                }
                catch (InvocationTargetException e) {
                    LOGGER.error("[World Scape] Biome set method threw exception: {}", (Object)(e.getCause() != null ? e.getCause().getMessage() : "null"), (Object)e);
                }
                catch (NoSuchMethodException e) {
                    LOGGER.error("[World Scape] Biome set method not found in PalettedContainer, API may have changed in this Minecraft version.", (Throwable)e);
                    this.biomeReflectionAvailable = false;
                }
                catch (SecurityException e) {
                    LOGGER.error("[World Scape] Security manager blocked reflection access to biome field", (Throwable)e);
                    this.biomeReflectionAvailable = false;
                }
                catch (Exception e) {
                    LOGGER.error("[World Scape] Unexpected error setting biome via reflection: {}", (Object)e.getMessage(), (Object)e);
                    this.biomeReflectionAvailable = false;
                }
                if (!overrideSucceeded) continue;
                overrideCounts.merge(terrainType, 1, Integer::sum);
                ++totalOverridden;
            }
        }
        if (totalOverridden > 0 && LOGGER.isDebugEnabled()) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("[World Scape] Terrain biome override: %d cells overridden in chunk (%d, %d)", totalOverridden, minX >> 4, minZ >> 4));
            for (Map.Entry<TerrainType, Integer> entry : overrideCounts.entrySet()) {
                sb.append(String.format(", %s=%d", entry.getKey().getId(), entry.getValue()));
            }
            LOGGER.debug(sb.toString());
        }
    }

    private TerrainType determineTerrainType(HeightCalculator calc, int x, int z, RegionController.BlendCache blendCache) {
        RegionController controller = this.getRegionController();
        RegionController.TerrainBlendResult blend = controller.getTerrainBlend(x, z, blendCache);
        return this.determineTerrainType(blend);
    }

    public int getMinY() {
        return this.minY;
    }

    public int getSeaLevel() {
        return this.seaLevel;
    }

    public int getGenDepth() {
        return this.height;
    }

    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor heightAccessor, RandomState randomState) {
        int minY = heightAccessor.getMinBuildHeight();
        int maxY = heightAccessor.getMaxBuildHeight();
        int height = maxY - minY;
        BlockState[] column = new BlockState[height];
        long bedrockSeed = SeedDeriver.deriveSeed(this.worldSeed, (long)x * 31L + (long)z * 17L + 388350381470L);
        int bedrockLayers = 1 + RandomSource.create((long)bedrockSeed).nextInt(3);
        for (int y = 0; y < height; ++y) {
            int worldY = minY + y;
            column[y] = worldY < minY + bedrockLayers ? Blocks.BEDROCK.defaultBlockState() : (worldY <= 0 ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState());
        }
        return new NoiseColumn(minY, column);
    }

    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        return level.getMinBuildHeight();
    }

    public void spawnOriginalMobs(WorldGenRegion region) {
    }

    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving carvingStep) {
    }

    public void addDebugScreenInfo(List<String> infoList, RandomState randomState, BlockPos blockPos) {
        infoList.add("World Scape Generator");
        infoList.add("Height: " + this.height);
        infoList.add("SeaLevel: " + this.seaLevel);
    }

    public double getHeight(int worldX, int worldZ) {
        RegionController controller = this.getRegionController();
        RegionController.TerrainBlendResult blend = controller.getTerrainBlend(worldX, worldZ);
        TerrainType type = this.determineTerrainType(blend);
        NoiseSet noiseSet = this.getNoiseSet();
        return this.calculateFinalHeight(worldX, worldZ, blend, type, noiseSet);
    }

    private double cachedNoiseSample(NoiseSet noiseSet, int worldX, int worldZ, NoiseSet.NoiseProfile profile, Map<NoiseSet.NoiseProfile, Double> cache) {
        Double cached = cache.get((Object)profile);
        if (cached != null) {
            return cached;
        }
        double value = noiseSet.sample(profile, worldX, worldZ);
        cache.put(profile, value);
        return value;
    }

    private double calcHeightForType(int worldX, int worldZ, double baseHeight, TerrainType type) {
        return TerrainCalculator.calcHeightForType(worldX, worldZ, baseHeight, type, this.getFieldSampler());
    }

    private double calcHeightForType(int worldX, int worldZ, double baseHeight, TerrainType type, RegionController.TerrainBlendResult blend) {
        return TerrainCalculator.calcHeightForType(worldX, worldZ, baseHeight, type, this.getFieldSampler(), blend);
    }

    public BiomeSource getBiomeSource() {
        return this.biomeSource;
    }

    public long getWorldSeed() {
        return this.worldSeed;
    }

    public Holder<NoiseGeneratorSettings> getSettings() {
        return this.settings;
    }

    private record RiverCacheData(boolean[][] riverMap, double[][] riverDepthMap) {
    }
}

