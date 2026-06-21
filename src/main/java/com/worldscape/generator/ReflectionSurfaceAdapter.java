package com.worldscape.generator;

import com.worldscape.generator.SurfaceAdapter;
import com.worldscape.terrain.TerrainType;
import com.worldscape.terrain.WorldScapeConstants;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReflectionSurfaceAdapter
implements SurfaceAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReflectionSurfaceAdapter.class);
    private static final String MOD_ID = "[World Scape] [ReflectionSurfaceAdapter]";
    private final AtomicReference<ReflectionCache> cacheRef = new AtomicReference();
    private final NoiseGeneratorSettings settings;
    private final long worldSeed;
    private final ChunkGenerator generator;

    public ReflectionSurfaceAdapter(NoiseGeneratorSettings settings, long worldSeed, ChunkGenerator generator) {
        this.settings = settings;
        this.worldSeed = worldSeed;
        this.generator = generator;
    }

    @Override
    public String getName() {
        return "ReflectionSurfaceAdapter";
    }

    @Override
    public boolean isAvailable() {
        ReflectionCache cache = this.getOrCreateCache();
        return cache.isValid();
    }

    @Override
    public boolean buildSurface(SurfaceAdapter.SurfaceBuildContext context) {
        ReflectionCache cache = this.getOrCreateCache();
        if (!cache.isValid()) {
            LOGGER.warn("{} Reflection cache invalid, cannot build surface", (Object)MOD_ID);
            return false;
        }
        try {
            return this.doBuildSurface(context, cache);
        }
        catch (Exception e) {
            LOGGER.warn("{} Surface build failed: {}", (Object)MOD_ID, (Object)e.getMessage());
            return false;
        }
    }

    private boolean doBuildSurface(SurfaceAdapter.SurfaceBuildContext context, ReflectionCache cache) throws Exception {
        RandomState randomState = (RandomState)context.getRandomState();
        ChunkAccess chunk = (ChunkAccess)context.getChunk();
        WorldGenRegion region = (WorldGenRegion)context.getRegion();
        Aquifer.FluidPicker fluidPicker = this.createDefaultFluidPicker();
        DensityFunctions.BeardifierOrMarker beardifier = this.createDefaultBeardifier();
        if (beardifier == null) {
            LOGGER.error("{} Failed to create BeardifierOrMarker, surface build aborted", (Object)MOD_ID);
            return false;
        }
        Object noBlendBlender = this.getNoBlendBlender(cache);
        Method forChunkMethod = cache.forChunkMethod;
        forChunkMethod.setAccessible(true);
        Object noiseChunk = forChunkMethod.invoke(null, chunk, randomState, beardifier, this.settings, fluidPicker, noBlendBlender);
        if (noiseChunk == null) {
            LOGGER.warn("{} NoiseChunk creation returned null", (Object)MOD_ID);
            return false;
        }
        this.setPreliminarySurfaceLevels(noiseChunk, context, cache);
        this.injectNoiseChunk(chunk, noiseChunk, cache);
        Object positionalRandom = this.getPositionalRandom(randomState, cache);
        SurfaceSystem surfaceSystem = new SurfaceSystem(randomState, this.settings.defaultBlock(), this.settings.seaLevel(), (PositionalRandomFactory)positionalRandom);
        Registry biomeRegistry = region.registryAccess().registryOrThrow(Registries.BIOME);
        BiomeManager biomeManager = region.getBiomeManager();
        WorldGenerationContext worldGenContext = new WorldGenerationContext(this.getGeneratorReference(), (LevelHeightAccessor)region);
        SurfaceRules.RuleSource ruleSource = this.settings.surfaceRule();
        Method buildSurfaceMethod = cache.buildSurfaceMethod;
        buildSurfaceMethod.setAccessible(true);
        buildSurfaceMethod.invoke((Object)surfaceSystem, randomState, biomeManager, biomeRegistry, false, worldGenContext, chunk, noiseChunk, ruleSource);
        // @AESTHETIC: Overlay WS-specific surface corrections on top of vanilla SurfaceRules.
        // Vanilla rules don't know about WS terrain types (glaciers, ice sheets, etc.), so we
        // apply a corrective pass to ensure WS-specific surface blocks are correct.
        // 在原版 SurfaceRules 之上叠加 WS 专属地表修正，确保冰川、冰原等 WS 地形类型的地表方块正确。
        this.applyWorldScapeSurfaceOverlay(chunk, context);
        LOGGER.debug("{} Surface built successfully", (Object)MOD_ID);
        return true;
    }

    private Aquifer.FluidPicker createDefaultFluidPicker() {
        final int seaLevel = this.settings.seaLevel();
        return new Aquifer.FluidPicker(){

            public Aquifer.FluidStatus computeFluid(int x, int y, int z) {
                return new Aquifer.FluidStatus(seaLevel, Blocks.WATER.defaultBlockState());
            }
        };
    }

    private DensityFunctions.BeardifierOrMarker createDefaultBeardifier() {
        try {
            Field emptyField = DensityFunctions.BeardifierOrMarker.class.getDeclaredField("EMPTY");
            emptyField.setAccessible(true);
            return (DensityFunctions.BeardifierOrMarker)emptyField.get(null);
        }
        catch (Exception e1) {
            try {
                Field srgField = DensityFunctions.BeardifierOrMarker.class.getDeclaredField("field_37113");
                srgField.setAccessible(true);
                return (DensityFunctions.BeardifierOrMarker)srgField.get(null);
            }
            catch (Exception e2) {
                LOGGER.warn("{} Could not create default BeardifierOrMarker", (Object)MOD_ID);
                return null;
            }
        }
    }

    private ChunkGenerator getGeneratorReference() {
        return this.generator;
    }

    private void setPreliminarySurfaceLevels(Object noiseChunk, SurfaceAdapter.SurfaceBuildContext context, ReflectionCache cache) throws Exception {
        Field surfaceLevelField = cache.preliminarySurfaceLevelField;
        if (surfaceLevelField == null) {
            LOGGER.warn("{} Could not find preliminarySurfaceLevel field", (Object)MOD_ID);
            return;
        }
        surfaceLevelField.setAccessible(true);
        Object preliminarySurfaceLevelMap = surfaceLevelField.get(noiseChunk);
        if (preliminarySurfaceLevelMap == null) {
            LOGGER.warn("{} preliminarySurfaceLevel map is null", (Object)MOD_ID);
            return;
        }
        Method putMethod = preliminarySurfaceLevelMap.getClass().getDeclaredMethod("put", Long.TYPE, Integer.TYPE);
        int[][] heightMap = context.getHeightMap();
        boolean[][] riverMap = context.getRiverMap();
        double[][] riverDepthMap = context.getRiverDepthMap();
        int minY = context.getMinY();
        int minX = context.getMinBlockX();
        int minZ = context.getMinBlockZ();
        for (int x = 0; x < 16; ++x) {
            for (int z = 0; z < 16; ++z) {
                int worldX = minX + x;
                int worldZ = minZ + z;
                int terrainHeight = heightMap[x][z];
                boolean isRiver = riverMap[x][z];
                double riverDepth = riverDepthMap[x][z];
                int actualHeight = this.calculateActualSurfaceHeight(terrainHeight, isRiver, riverDepth, minY);
                long posKey = (long)worldX & 0xFFFFFFFFL | ((long)worldZ & 0xFFFFFFFFL) << 32;
                putMethod.invoke(preliminarySurfaceLevelMap, posKey, actualHeight);
            }
        }
    }

    private int calculateActualSurfaceHeight(int terrainHeight, boolean isRiver, double riverDepth, int minY) {
        if (isRiver && riverDepth > 0.5) {
            return (int)Math.max((double)minY, (double)terrainHeight - riverDepth);
        }
        return terrainHeight;
    }

    // @AESTHETIC: Apply WS-specific surface block corrections as an overlay on vanilla SurfaceRules.
    // Overrides surface and sub-surface blocks for ALL 29 terrain types, consistent with FallbackSurfaceAdapter.
    // 在原版 SurfaceRules 之上叠加 WS 专属地表方块修正，覆盖全部 29 种地形类型，与 FallbackSurfaceAdapter 保持一致。
    private void applyWorldScapeSurfaceOverlay(ChunkAccess chunk, SurfaceAdapter.SurfaceBuildContext context) {
        int[][] heightMap = context.getHeightMap();
        TerrainType[][] terrainTypeMap = context.getTerrainTypeMap();
        int minX = context.getMinBlockX();
        int minZ = context.getMinBlockZ();
        int seaLevel = this.settings.seaLevel();
        net.minecraft.core.BlockPos.MutableBlockPos pos = new net.minecraft.core.BlockPos.MutableBlockPos();

        for (int x = 0; x < 16; ++x) {
            for (int z = 0; z < 16; ++z) {
                int terrainHeight = heightMap[x][z];
                int worldX = minX + x;
                int worldZ = minZ + z;

                TerrainType terrainType = (terrainTypeMap != null) ? terrainTypeMap[x][z] : null;
                if (terrainType == null) continue;

                // Override surface block based on terrain type (all 29 types)
                // 根据地形类型覆盖表面方块（全部 29 种类型）
                net.minecraft.world.level.block.state.BlockState surfaceBlock = FallbackSurfaceAdapter.determineSurfaceBlockByTerrainType(terrainType, terrainHeight, seaLevel);
                pos.set(worldX, terrainHeight, worldZ);
                if (!chunk.getBlockState(pos).isAir()) {
                    chunk.setBlockState(pos, surfaceBlock, false);
                }

                // Override sub-surface blocks based on terrain type (all 29 types)
                // 根据地形类型覆盖次表层方块（全部 29 种类型）
                // Determine underwater status from terrain height vs sea level
                // 根据地形高度与海平面判断是否水下
                boolean isUnderwater = terrainHeight <= seaLevel;
                net.minecraft.world.level.block.state.BlockState subSurfaceBlock = FallbackSurfaceAdapter.determineSubSurfaceBlockByTerrainType(terrainType, isUnderwater);
                for (int dy = 1; dy <= 3; ++dy) {
                    pos.set(worldX, terrainHeight - dy, worldZ);
                    net.minecraft.world.level.block.state.BlockState current = chunk.getBlockState(pos);
                    if (!current.isAir() && current.getBlock() != Blocks.WATER) {
                        chunk.setBlockState(pos, subSurfaceBlock, false);
                    }
                }
            }
        }
    }

    private void injectNoiseChunk(ChunkAccess chunk, Object noiseChunk, ReflectionCache cache) {
        for (String fieldName : cache.noiseChunkFieldNames) {
            try {
                Field field = chunk.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(chunk, noiseChunk);
                LOGGER.debug("{} Injected noiseChunk via field: {}", (Object)MOD_ID, (Object)fieldName);
                return;
            }
            catch (Exception exception) {
                // 字段注入失败，尝试下一个候选字段名 / Field injection failed, trying next candidate field name
                LOGGER.debug("{} Failed to inject via field '{}': {}", (Object)MOD_ID, (Object)fieldName, (Object)exception.getMessage());
            }
        }
        LOGGER.error("{} Could not inject noiseChunk into chunk", (Object)MOD_ID);
    }

    private Object getPositionalRandom(RandomState randomState, ReflectionCache cache) {
        try {
            Method noiseRouterMethod = cache.noiseRouterMethod;
            if (noiseRouterMethod == null) {
                return randomState;
            }
            noiseRouterMethod.setAccessible(true);
            Object noiseRouter = noiseRouterMethod.invoke((Object)randomState, new Object[0]);
            Method noiseRandomMethod = cache.noiseRandomMethod;
            if (noiseRandomMethod == null) {
                return randomState;
            }
            noiseRandomMethod.setAccessible(true);
            return noiseRandomMethod.invoke(noiseRouter, new Object[0]);
        }
        catch (Exception e) {
            LOGGER.debug("{} Using default random: {}", (Object)MOD_ID, (Object)e.getMessage());
            return randomState;
        }
    }

    private Object getNoBlendBlender(ReflectionCache cache) {
        try {
            Field noBlendField = cache.noBlendField;
            if (noBlendField != null) {
                noBlendField.setAccessible(true);
                return noBlendField.get(null);
            }
        }
        catch (Exception e) {
            LOGGER.debug("{} Could not get NO_BLEND Blender: {}", (Object)MOD_ID, (Object)e.getMessage());
        }
        return null;
    }

    private ReflectionCache getOrCreateCache() {
        ReflectionCache cache = this.cacheRef.get();
        if (cache == null || !cache.isValid()) {
            cache = new ReflectionCache();
            this.cacheRef.set(cache);
        }
        return cache;
    }

    private static class ReflectionCache {
        Method forChunkMethod;
        Method fluidPickerMethod;
        Method beardifierMethod;
        Method noiseRouterMethod;
        Method noiseRandomMethod;
        Method buildSurfaceMethod;
        Field preliminarySurfaceLevelField;
        Field noBlendField;
        String[] noiseChunkFieldNames = new String[]{"noiseChunk", "f_62848_", "currentNoiseChunk", "noise"};
        boolean valid = false;

        ReflectionCache() {
            try {
                this.forChunkMethod = NoiseChunk.class.getDeclaredMethod("forChunk", ChunkAccess.class, RandomState.class, DensityFunctions.BeardifierOrMarker.class, NoiseGeneratorSettings.class, Aquifer.FluidPicker.class, Blender.class);
                this.fluidPickerMethod = null;
                this.beardifierMethod = null;
                try {
                    this.noiseRouterMethod = RandomState.class.getDeclaredMethod("router", new Class[0]);
                }
                catch (NoSuchMethodException nsme1) {
                    try {
                        this.noiseRouterMethod = RandomState.class.getDeclaredMethod("noiseRouter", new Class[0]);
                    }
                    catch (NoSuchMethodException nsme2) {
                        this.noiseRouterMethod = null;
                    }
                }
                if (this.noiseRouterMethod != null) {
                    Class<?> noiseRouterClass = this.noiseRouterMethod.getReturnType();
                    this.noiseRandomMethod = noiseRouterClass.getDeclaredMethod("noiseRandom", new Class[0]);
                } else {
                    this.noiseRandomMethod = null;
                }
                Class<SurfaceSystem> surfaceSystemClass = SurfaceSystem.class;
                this.buildSurfaceMethod = surfaceSystemClass.getDeclaredMethod("buildSurface", RandomState.class, BiomeManager.class, Registry.class, Boolean.TYPE, WorldGenerationContext.class, ChunkAccess.class, NoiseChunk.class, SurfaceRules.RuleSource.class);
                for (String fieldName : new String[]{"preliminarySurfaceLevel", "surfaceHeightEstimateCache", "f_224353_"}) {
                    try {
                        this.preliminarySurfaceLevelField = NoiseChunk.class.getDeclaredField(fieldName);
                        break;
                    }
                    catch (NoSuchFieldException e) {
                        // Field name candidate not found — try next candidate.
                        // Fixed: was empty catch block — per AGENTS.md §3.4 all exceptions MUST be logged.
                        // 字段名候选未找到 — 尝试下一个候选。
                        // 修复：原为空 catch 块 — 按 AGENTS.md §3.4 所有异常必须记录日志。
                        LOGGER.debug("[ReflectionSurfaceAdapter] Field '{}' not found on NoiseChunk, trying next candidate", fieldName);
                    }
                }
                if (this.preliminarySurfaceLevelField == null) {
                    for (Field f : NoiseChunk.class.getDeclaredFields()) {
                        if (!f.getType().getName().contains("Long2Int")) continue;
                        this.preliminarySurfaceLevelField = f;
                        break;
                    }
                }
                try {
                    this.noBlendField = Blender.class.getDeclaredField("NO_BLEND");
                }
                catch (NoSuchFieldException e1) {
                    try {
                        this.noBlendField = Blender.class.getDeclaredField("NONE");
                    }
                    catch (NoSuchFieldException e2) {
                        this.noBlendField = null;
                    }
                }
                this.valid = true;
                LOGGER.debug("[World Scape] [ReflectionSurfaceAdapter] Cache initialized successfully");
            }
            catch (Exception e) {
                LOGGER.error("[World Scape] [ReflectionSurfaceAdapter] Failed to initialize reflection cache: {}", (Object)e.getMessage());
                this.valid = false;
            }
        }

        boolean isValid() {
            return this.valid;
        }
    }
}

