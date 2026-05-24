package com.worldscape.generator;

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
import com.worldscape.terrain.TerrainControlPoint;
import com.worldscape.terrain.TerrainFieldSampler;
import com.worldscape.terrain.TerrainType;
import com.worldscape.util.SeedDeriver;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.HashMap;
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
    private final ThreadLocal<Map<Long, RiverCacheData>> riverCache =
        ThreadLocal.withInitial(() -> new HashMap<>(4));
    public static final MapCodec<LandscapeChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        BiomeSource.CODEC.fieldOf("biome_source").forGetter(LandscapeChunkGenerator::getBiomeSource),
        NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(LandscapeChunkGenerator::getSettings),
        Codec.LONG.fieldOf("seed").orElse(0L).forGetter(LandscapeChunkGenerator::getWorldSeed)
    ).apply(instance, LandscapeChunkGenerator::new));
    public static final int FALLBACK_SEA_LEVEL = 63;
    private static final int OVERWORLD_MIN_Y = -64;
    private static final int OVERWORLD_HEIGHT = 384;
    private static final double RIVER_DIFF_THRESHOLD = 0.1;
    private static final int HILLS_TIER_THRESHOLD = 3;
    private static final int MOUNTAINS_TIER_THRESHOLD = 5;
    private static final int EROSION_TIER_THRESHOLD = 3;
    private static final double EROSION_NOISE_THRESHOLD = 0.45;
    private static final double EROSION_INTENSITY_FACTOR = 0.8;
    private static final double ALLUVIAL_THRESHOLD = 0.45;
    private static final double ALLUVIAL_FACTOR = 0.4;
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
    private static final double SLOPE_ANOMALY_THRESHOLD = 30.0;
    private static final int HEIGHT_CHANGE_WARNING_THRESHOLD = 100;
    private static final int HEIGHT_CHANGE_CRITICAL_THRESHOLD = 150;
    private static final int VOID_MIN_HEIGHT = -64;
    private static final int MAX_BEDROCK_LAYERS = 3;
    private static final long BEDROCK_SEED = 388350381470L;
    private static final int DEEPSLATE_TOP_Y = 0;
    private static final AtomicInteger warningCount = new AtomicInteger(0);
    private static final AtomicInteger voidWarningCount = new AtomicInteger(0);
    private static final AtomicInteger extremeSlopeCount = new AtomicInteger(0);

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
                    dominantHeight = this.calcHeightForType(worldX, worldZ, baseHeight, dominantType);
                    fallbackHeight = this.calcHeightForType(worldX, worldZ, baseHeight, currentType);
                } else {
                    fallbackHeight = dominantHeight = this.calcHeightForType(worldX, worldZ, baseHeight, currentType);
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
                    LOGGER.error("[World Scape] Chunk slope anomaly at world({}, {}) [chunk({}, {})]: height={}, dominantType={}, fallbackType={}, dominantWeight={}, baseHeight={}, dominantHeight={}, fallbackHeight={}, direction={}, neighborHeight={}, slopeDiff={}", new Object[]{worldX, worldZ, x, z, currentHeight, dominantType, currentType, dominantWeight, baseHeight, dominantHeight, fallbackHeight, dirNames[i], neighborHeight, slopeDiff});
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
        double finalHeight;
        double baseHeight = blend.blendedHeight;
        double dominantWeight = blend.dominantWeight;
        TerrainType dominantType = blend.dominantType;
        double dominantHeight = baseHeight;
        if (dominantWeight >= 0.4) {
            finalHeight = dominantHeight = this.calcHeightForType(x, z, baseHeight, dominantType);
        } else {
            double dominantTypeHeight = this.calcHeightForType(x, z, baseHeight, dominantType);
            double currentTypeHeight = this.calcHeightForType(x, z, baseHeight, type);
            double blendFactor = dominantWeight / 0.4;
            finalHeight = dominantTypeHeight * blendFactor + currentTypeHeight * (1.0 - blendFactor);
        }
        return finalHeight;
    }

    private double getRiverErosionIntensity(int worldX, int worldZ, NoiseSet noiseSet, double baseHeight, int seaLevel, RegionController.TerrainBlendResult blend) {
        if (blend.macroInfo.elevationTier < 3) {
            return 0.0;
        }
        double erosionNoise = noiseSet.sample(NoiseSet.NoiseProfile.DRAINAGE, worldX, worldZ);
        if (erosionNoise < 0.45) {
            return 0.0;
        }
        double intensity = (erosionNoise - 0.45) / 0.55;
        double elevationFactor = Math.max(0.0, (baseHeight - (double)seaLevel) / 100.0);
        return intensity * elevationFactor * 0.8;
    }

    private double getAlluvialFactor(int worldX, int worldZ, NoiseSet noiseSet, double baseHeight, int seaLevel) {
        if (baseHeight > (double)(seaLevel + 20)) {
            return 0.0;
        }
        double alluvialNoise = noiseSet.sample(NoiseSet.NoiseProfile.SEABED, worldX, worldZ);
        if (alluvialNoise < 0.45) {
            return 0.0;
        }
        double factor = (alluvialNoise - 0.45) / 0.55;
        double distanceFactor = Math.max(0.0, 1.0 - (baseHeight - (double)seaLevel) / 20.0);
        return factor * distanceFactor * 0.4;
    }

    private TerrainType determineTerrainType(RegionController.TerrainBlendResult blend) {
        TerrainType dominantType = blend.dominantType;
        double dominantWeight = blend.dominantWeight;
        if (dominantType != null && dominantWeight >= 0.4) {
            return dominantType;
        }
        int tier = blend.macroInfo.elevationTier;
        if (tier <= 0) {
            return TerrainType.TRENCH;
        }
        if (tier == 1) {
            return TerrainType.SEA_PLATEAU;
        }
        if (tier == 2) {
            return TerrainType.BEACH;
        }
        if (tier == 3) {
            return TerrainType.PLAINS;
        }
        if (tier == 4) {
            return TerrainType.HILLS;
        }
        return TerrainType.HIGH_MOUNTAINS;
    }

    private int calculateActualSurfaceHeight(int terrainHeight, boolean isRiver, double riverDepth, int minY) {
        if (isRiver && riverDepth > 0.5) {
            return (int)Math.max((double)minY, (double)terrainHeight - riverDepth);
        }
        return terrainHeight;
    }

    private int calculateErodedHeight(int worldX, int worldZ, double continuousHeight, boolean isRiver, double riverDepth, int seaLevel, NoiseSet noiseSet, RegionController.TerrainBlendResult blend) {
        double erosionIntensity = this.getRiverErosionIntensity(worldX, worldZ, noiseSet, continuousHeight, seaLevel, blend);
        return this.calculateErodedHeight(worldX, worldZ, continuousHeight, isRiver, riverDepth, seaLevel, noiseSet, blend, erosionIntensity);
    }

    private int calculateErodedHeight(int worldX, int worldZ, double continuousHeight, boolean isRiver, double riverDepth, int seaLevel, NoiseSet noiseSet, RegionController.TerrainBlendResult blend, double erosionIntensity) {
        double alluvialFactor = this.getAlluvialFactor(worldX, worldZ, noiseSet, continuousHeight, seaLevel);
        return this.calculateErodedHeight(continuousHeight, isRiver, riverDepth, seaLevel, erosionIntensity, alluvialFactor);
    }

    private int calculateErodedHeight(double continuousHeight, boolean isRiver, double riverDepth, int seaLevel, double erosionIntensity, double alluvialFactor) {
        double erodedHeight = continuousHeight;
        if (isRiver && erosionIntensity > 0.1) {
            double erosionCut = erosionIntensity * 30.0;
            erodedHeight = continuousHeight - erosionCut;
        }
        if (alluvialFactor > 0.1) {
            double alluvialRaise = alluvialFactor * 5.0;
            erodedHeight += alluvialRaise;
        }
        return (int)Math.floor(erodedHeight);
    }

    private boolean isRiverAt(int worldX, int worldZ, NoiseSet noiseSet) {
        double riverNoise = noiseSet.sample(NoiseSet.NoiseProfile.RIVER_PATH, worldX, worldZ);
        double riverPath = noiseSet.sample(NoiseSet.NoiseProfile.RIVER_WIDTH, worldX, worldZ);
        return riverNoise > 0.1 && Math.abs(riverPath) < 0.15;
    }

    private double getRiverDepthAt(int worldX, int worldZ, NoiseSet noiseSet, int surfaceHeight, int seaLevel) {
        if (!this.isRiverAt(worldX, worldZ, noiseSet)) {
            return 0.0;
        }
        double riverNoise = noiseSet.sample(NoiseSet.NoiseProfile.RIVER_PATH, worldX, worldZ);
        double depth = (riverNoise - 0.1) * 2.5 * 10.0;
        return Math.max(3.0, Math.min(depth, 20.0));
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
        double alluvialFactor;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        RegionController.TerrainBlendResult currentBlend = cachedBlend != null ? cachedBlend : controller.getTerrainBlend(worldX, worldZ);
        TerrainType currentType = cachedType != null ? cachedType : this.determineTerrainType(currentBlend);
        double continuousHeight = cachedContinuousHeight;
        if (continuousHeight < -64.0) {
            LOGGER.warn("[World Scape] [WARNING] Possible void at ({}, {}): height={}, terrainType={}, macroTier={}", new Object[]{worldX, worldZ, String.format("%.1f", continuousHeight), currentType.getId(), currentBlend.macroInfo.elevationTier});
            voidWarningCount.incrementAndGet();
        }
        double erosionIntensity = cachedErosionIntensity;
        double erodedHeight = continuousHeight;
        if (isRiver && erosionIntensity > 0.1) {
            double erosionCut = erosionIntensity * 30.0;
            erodedHeight = continuousHeight - erosionCut;
        }
        if ((alluvialFactor = cachedAlluvialFactor) > 0.1) {
            double alluvialRaise = alluvialFactor * 5.0;
            erodedHeight += alluvialRaise;
        }
        int actualSurfaceHeight = this.calculateActualSurfaceHeight((int)Math.floor(erodedHeight), isRiver, riverDepth, minY);
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
            RiverCacheData cachedRiver = this.riverCache.get().remove(ChunkPos.asLong((int)chunk.getPos().x, (int)chunk.getPos().z));
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
                    riverMap[x][z] = this.isRiverAt(worldX, worldZ, noiseSet);
                    riverDepthMap[x][z] = this.getRiverDepthAt(worldX, worldZ, noiseSet, surfaceY, this.seaLevel);
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
            LandscapeChunkGenerator landscapeChunkGenerator = this;
            synchronized (landscapeChunkGenerator) {
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
                riverDepthMap[x][z] = riverDepth = this.getRiverDepthAt(worldX, worldZ, noiseSet, (int)finalHeight, this.seaLevel);
                int erodedHeight = this.calculateErodedHeight((int)finalHeight, isRiver, riverDepth, this.seaLevel, erosionIntensity, alluvialFactor);
                heightMap[x][z] = this.calculateActualSurfaceHeight(erodedHeight, isRiver, riverDepth, this.minY);
            }
        }
        long t4 = System.nanoTime();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[World Scape] [PERF]   Phase4 terrain blend query (256 cells): {}ms", (Object)((t4 - t3) / 1000000L));
        }
        try {
            Class<?> debugSystemClass = Class.forName("com.worldscape.debug.TerrainDebugSystem");
            Object instance = debugSystemClass.getField("INSTANCE").get(null);
            Method isDebugMethod = debugSystemClass.getMethod("isDebugModeEnabled", new Class[0]);
            if (((Boolean)isDebugMethod.invoke(instance, new Object[0])).booleanValue()) {
                this.detectChunkSlopeAnomalies(heightMap, typeMap, riverMap, riverDepthMap, minX, minZ, controller, noiseSet, this.minY, this.minY + this.height);
            }
        }
        catch (Exception debugSystemClass) {
            // empty catch block
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
            LandscapeChunkGenerator landscapeChunkGenerator = this;
            synchronized (landscapeChunkGenerator) {
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
        int regionX = Math.floorDiv(minX, 512);
        int regionZ = Math.floorDiv(minZ, 512);
        ArrayList<TerrainControlPoint> allPoints = new ArrayList<TerrainControlPoint>();
        for (int dx = -1; dx <= 1; ++dx) {
            for (int dz = -1; dz <= 1; ++dz) {
                ControlPointRegion region = controller.getControlPointRegion(regionX + dx, regionZ + dz);
                if (region == null) continue;
                allPoints.addAll(region.getControlPoints());
            }
        }
        MacroRegionInfo macroInfo = controller.getMacroSystem().getRegionInfo(minX + 8, minZ + 8);
        return new RegionController.BlendCache(regionX, regionZ, allPoints, macroInfo);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private NoiseSet getNoiseSet() {
        NoiseSet noiseSet = this.noiseSetRef.get();
        if (noiseSet == null) {
            LandscapeChunkGenerator landscapeChunkGenerator = this;
            synchronized (landscapeChunkGenerator) {
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
            LandscapeChunkGenerator landscapeChunkGenerator = this;
            synchronized (landscapeChunkGenerator) {
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
            LandscapeChunkGenerator landscapeChunkGenerator = this;
            synchronized (landscapeChunkGenerator) {
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
        EnumMap<TerrainType, Integer> overrideCounts = new EnumMap<TerrainType, Integer>(TerrainType.class);
        int totalOverridden = 0;
        boolean reflectionAvailable = true;
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
                if (currentBiome == null || (allowedBiomes = biomeRules.getAllowedBiomes(terrainType = this.determineTerrainType(calc, centerX, centerZ, blendCache))).isEmpty() || allowedBiomes.contains(currentBiome) || (selectedBiome = biomeRules.selectBiomeBySeed(allowedBiomes, this.worldSeed, cellX, cellZ)) == null || !reflectionAvailable) continue;
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
                    reflectionAvailable = false;
                }
                catch (IllegalAccessException e) {
                    LOGGER.error("[World Scape] Cannot access biome field, module access restrictions may apply. Consider adding --add-opens java.base/java.lang=ALL-UNNAMED to JVM args.", (Throwable)e);
                    reflectionAvailable = false;
                }
                catch (InvocationTargetException e) {
                    LOGGER.error("[World Scape] Biome set method threw exception: {}", (Object)e.getCause().getMessage(), (Object)e);
                }
                catch (NoSuchMethodException e) {
                    LOGGER.error("[World Scape] Biome set method not found in PalettedContainer, API may have changed in this Minecraft version.", (Throwable)e);
                    reflectionAvailable = false;
                }
                catch (SecurityException e) {
                    LOGGER.error("[World Scape] Security manager blocked reflection access to biome field", (Throwable)e);
                    reflectionAvailable = false;
                }
                catch (Exception e) {
                    LOGGER.error("[World Scape] Unexpected error setting biome via reflection: {}", (Object)e.getMessage(), (Object)e);
                }
                if (!overrideSucceeded) continue;
                overrideCounts.merge(terrainType, 1, Integer::sum);
                ++totalOverridden;
            }
        }
        if (totalOverridden > 0 && LOGGER.isDebugEnabled()) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("[World Scape] Terrain biome override: %d cells overridden in chunk (%d, %d)", totalOverridden, minX >> 4, minZ >> 4));
            for (Map.Entry entry : overrideCounts.entrySet()) {
                sb.append(String.format(", %s=%d", ((TerrainType)((Object)entry.getKey())).getId(), entry.getValue()));
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
        double height = baseHeight;
        TerrainFieldSampler fs = this.getFieldSampler();
        switch (type) {
            case HIGH_MOUNTAINS: {
                double hmFbm = fs.sampleFbm(worldX, worldZ, 6, 0.5);
                double hmHeight = hmFbm * 200.0;
                double hmDomain = fs.sampleDomainRotated(worldX, worldZ, 0.15) * 15.0;
                double hmTurb = fs.sampleTurbulence(worldX, worldZ, 0.6) * 20.0;
                height = baseHeight + hmHeight + hmDomain + hmTurb;
                height = Math.min(height, baseHeight + 250.0);
                break;
            }
            case RIDGE: {
                double rPrimarySine = Math.sin((double)worldX * 0.007 + (double)worldZ * 0.004);
                double rSecondarySine = Math.sin((double)worldX * 0.025 - (double)worldZ * 0.018);
                double rSineRaw = rPrimarySine * 35.0 + rSecondarySine * 18.0;
                double rGx = Math.sin((double)(worldX + 1) * 0.007 + (double)worldZ * 0.004) - Math.sin((double)(worldX - 1) * 0.007 + (double)worldZ * 0.004);
                double rGz = Math.sin((double)worldX * 0.007 + (double)(worldZ + 1) * 0.004) - Math.sin((double)worldX * 0.007 + (double)(worldZ - 1) * 0.004);
                double rGradMag = Math.sqrt(rGx * rGx + rGz * rGz);
                double rSensitivity = 0.6;
                double rSineWeight = rGradMag > rSensitivity ? 0.3 : (rGradMag < rSensitivity * 0.5 ? 1.0 : 0.3 + 0.7 * (rSensitivity - rGradMag) / (rSensitivity * 0.5));
                double rSine = rSineRaw * rSineWeight;
                double rFbm = fs.sampleFbm(worldX, worldZ, 6, 0.5) * 150.0;
                double rTurb = fs.sampleTurbulence(worldX, worldZ, 0.6) * 15.0;
                double rDomain = fs.sampleDomainRotated(worldX, worldZ, 0.15) * 10.0;
                height = baseHeight + rFbm + rSine + rTurb + rDomain;
                break;
            }
            case PEAK: {
                double pFbm = fs.sampleFbm(worldX, worldZ, 6, 0.4);
                double pHeight = pFbm * 120.0;
                double pTurb = fs.sampleTurbulence(worldX, worldZ, 0.6) * 80.0;
                double pDomain = fs.sampleDomainRotated(worldX, worldZ, 0.15) * 12.0;
                height = baseHeight + pHeight + pTurb + pDomain;
                height = Math.min(height, 500.0);
                break;
            }
            case HORN: {
                double hFbm = fs.sampleFbm(worldX, worldZ, 6, 0.3);
                double hHeight = hFbm * 100.0;
                double hTurb = fs.sampleTurbulence(worldX, worldZ, 0.8) * 120.0;
                height = baseHeight + hHeight + hTurb;
                height = Math.min(height, 500.0);
                break;
            }
            case CLIFF: {
                double cFbm = fs.sampleFbm(worldX, worldZ, 6, 0.5);
                double cRaw = cFbm * 80.0;
                double cTanh = TerrainFieldSampler.tanhScaled(cFbm, 2.0) * 40.0;
                height = baseHeight + cRaw + cTanh;
                break;
            }
            case PLATEAU: {
                double plFbm = fs.sampleFbm(worldX, worldZ, 3, 0.3);
                height = baseHeight + plFbm * 100.0;
                break;
            }
            case DOME: {
                double dOffsetX = fs.sampleFbm(worldX, worldZ, 2, 0.2) * 50.0;
                double dOffsetZ = fs.sampleFbm(worldX + 10000, worldZ + 10000, 2, 0.2) * 50.0;
                double dGauss = TerrainFieldSampler.gaussian((double)worldX - dOffsetX, (double)worldZ - dOffsetZ, 200.0);
                height = baseHeight + dGauss * 150.0;
                break;
            }
            case DUNE: {
                double duPrimary = Math.sin((double)worldX * 0.02 + (double)worldZ * 0.005) * 25.0;
                double duSecondary = Math.sin((double)worldX * 0.005 - (double)worldZ * 0.015) * 8.0;
                double duRidge = Math.abs(duPrimary) + duSecondary;
                double duFbm = fs.sampleFbm(worldX, worldZ, 2, 0.1) * 5.0;
                height = baseHeight + duRidge + duFbm;
                break;
            }
            case YARDANG: {
                double yaPrimary = Math.sin((double)worldX * 0.015 + (double)worldZ * 0.003) * 30.0;
                double yaDomain = fs.sampleDomainRotated(worldX, worldZ, 0.2) * 15.0;
                height = baseHeight + yaPrimary + yaDomain;
                break;
            }
            case GOBI: {
                double goFbm = fs.sampleFbm(worldX, worldZ, 4, 0.7);
                height = baseHeight + goFbm * 15.0;
                break;
            }
            case SALT_FLAT: {
                double sfFbm = fs.sampleFbm(worldX, worldZ, 2, 0.1);
                height = baseHeight + sfFbm * 3.0;
                break;
            }
            case CANYON: {
                double caGradDir = fs.sampleFbm(worldX, worldZ, 3, 0.4);
                double caDepth = Math.abs(caGradDir) * 60.0;
                double caFbm = fs.sampleFbm(worldX, worldZ, 4, 0.5) * 10.0;
                height = baseHeight - caDepth + caFbm;
                break;
            }
            case VALLEY: {
                double vGradMag = fs.calculateGradient(worldX, worldZ);
                double vDepth = TerrainFieldSampler.sigmoid(vGradMag * 5.0) * 40.0;
                double vFbm = fs.sampleFbm(worldX, worldZ, 4, 0.5) * 10.0;
                height = baseHeight - vDepth + vFbm;
                break;
            }
            case FLOODPLAIN: {
                double fpFbm = fs.sampleFbm(worldX, worldZ, 3, 0.15);
                height = baseHeight + fpFbm * 5.0;
                break;
            }
            case DELTA: {
                double dtGrad = fs.calculateGradient(worldX, worldZ);
                double dtHeight = dtGrad * 10.0;
                double dtDomain = fs.sampleDomainRotated(worldX, worldZ, 0.05) * 8.0;
                height = baseHeight + dtHeight + dtDomain;
                break;
            }
            case ALLUVIAL_FAN: {
                double afDist = Math.sqrt((double)worldX * (double)worldX + (double)worldZ * (double)worldZ) % 200.0;
                double afErf = Math.tanh(afDist / 100.0 * 0.886);
                double afSlope = afErf * 25.0;
                double afFbm = fs.sampleFbm(worldX, worldZ, 3, 0.3) * 5.0;
                height = baseHeight + afSlope + afFbm;
                break;
            }
            case BASIN: {
                double bOffsetX = fs.sampleFbm(worldX, worldZ, 2, 0.2) * 80.0;
                double bOffsetZ = fs.sampleFbm(worldX + 20000, worldZ + 20000, 2, 0.2) * 80.0;
                double bGauss = TerrainFieldSampler.gaussian((double)worldX - bOffsetX, (double)worldZ - bOffsetZ, 300.0);
                double bDepth = bGauss * 30.0;
                height = baseHeight - bDepth;
                break;
            }
            case FJORD: {
                double fjTurb = fs.sampleTurbulence(worldX, worldZ, 0.7) * 100.0;
                double fjCliffEdge = fs.sampleFbm(worldX, worldZ, 3, 0.3);
                double fjTanh = TerrainFieldSampler.tanhScaled(fjCliffEdge, 2.0) * 80.0;
                height = baseHeight - fjTurb + fjTanh;
                break;
            }
            case GLACIAL_VALLEY: {
                double gvGradMag = fs.calculateGradient(worldX, worldZ);
                double gvDepth = TerrainFieldSampler.sigmoid(gvGradMag * 5.0) * 60.0;
                double gvFbm = fs.sampleFbm(worldX, worldZ, 4, 0.5) * 8.0;
                height = baseHeight - gvDepth + gvFbm;
                break;
            }
            case CIRQUE: {
                double ciOffsetX = fs.sampleFbm(worldX, worldZ, 2, 0.2) * 40.0;
                double ciOffsetZ = fs.sampleFbm(worldX + 30000, worldZ + 30000, 2, 0.2) * 40.0;
                double ciGauss = TerrainFieldSampler.gaussian((double)worldX - ciOffsetX, (double)worldZ - ciOffsetZ, 150.0);
                double ciDepth = ciGauss * 120.0;
                double ciEdgeTurb = fs.sampleTurbulence(worldX, worldZ, 0.5) * 60.0;
                height = baseHeight - ciDepth + ciEdgeTurb;
                break;
            }
            case ICE_SHEET: {
                double isFbm = fs.sampleFbm(worldX, worldZ, 3, 0.2);
                height = baseHeight + isFbm * 8.0;
                break;
            }
            case SEA_CLIFF: {
                double scEdge = fs.sampleFbm(worldX, worldZ, 4, 0.4);
                double scTanh = TerrainFieldSampler.tanhScaled(scEdge, 3.0) * 100.0;
                height = baseHeight + scTanh;
                break;
            }
            case BEACH: {
                double beDist = fs.sampleFbm(worldX, worldZ, 2, 0.2);
                double beSigmoid = TerrainFieldSampler.sigmoid(beDist * 3.0) * 5.0;
                height = baseHeight + beSigmoid;
                break;
            }
            case SINKHOLE: {
                double skOffsetX = fs.sampleFbm(worldX, worldZ, 2, 0.2) * 30.0;
                double skOffsetZ = fs.sampleFbm(worldX + 40000, worldZ + 40000, 2, 0.2) * 30.0;
                double skGauss = TerrainFieldSampler.gaussian((double)worldX - skOffsetX, (double)worldZ - skOffsetZ, 80.0);
                double skDepth = skGauss * 40.0;
                height = baseHeight - skDepth;
                break;
            }
            case PEAK_FOREST: {
                double pfTurb = fs.sampleTurbulence(worldX, worldZ, 0.7) * 80.0;
                double pfFbm = fs.sampleFbm(worldX, worldZ, 4, 0.5) * 40.0;
                height = baseHeight + pfTurb + pfFbm;
                break;
            }
            case TRENCH: {
                double trAxis = fs.sampleFbm(worldX, worldZ, 3, 0.3);
                double trDepth = TerrainFieldSampler.sigmoid(-trAxis * 3.0) * 30.0;
                height = baseHeight - 20.0 - trDepth;
                break;
            }
            case SEA_PLATEAU: {
                double spFbm = fs.sampleFbm(worldX, worldZ, 3, 0.15);
                height = baseHeight + spFbm * 15.0;
                break;
            }
            case HILLS: {
                double hiFbm = fs.sampleFbm(worldX, worldZ, 6, 0.65);
                height = baseHeight + hiFbm * 40.0;
                break;
            }
            case PLAINS: {
                double plFbm2 = fs.sampleFbm(worldX, worldZ, 4, 0.2);
                height = baseHeight + plFbm2 * 15.0;
                break;
            }
            default: {
                double defFbm = fs.sampleFbm(worldX, worldZ, 6, 0.5);
                height = baseHeight + defFbm * 20.0;
            }
        }
        height = Math.max(-64.0, Math.min(300.0, height));
        return height;
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

