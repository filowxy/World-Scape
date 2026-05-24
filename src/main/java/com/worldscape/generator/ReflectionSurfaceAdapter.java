package com.worldscape.generator;

import com.worldscape.generator.SurfaceAdapter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeManager;
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
        Method fluidPickerMethod = cache.fluidPickerMethod;
        fluidPickerMethod.setAccessible(true);
        Object fluidPicker = fluidPickerMethod.invoke((Object)randomState, new Object[0]);
        Method beardifierMethod = cache.beardifierMethod;
        beardifierMethod.setAccessible(true);
        Object beardifier = beardifierMethod.invoke((Object)randomState, new Object[0]);
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
        LOGGER.debug("{} Surface built successfully", (Object)MOD_ID);
        return true;
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
            }
        }
        LOGGER.warn("{} Could not inject noiseChunk into chunk", (Object)MOD_ID);
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
                this.fluidPickerMethod = RandomState.class.getDeclaredMethod("fluidPicker", new Class[0]);
                this.beardifierMethod = RandomState.class.getDeclaredMethod("beardifier", new Class[0]);
                this.noiseRouterMethod = RandomState.class.getDeclaredMethod("noiseRouter", new Class[0]);
                Class<?> noiseRouterClass = this.noiseRouterMethod.getReturnType();
                this.noiseRandomMethod = noiseRouterClass.getDeclaredMethod("noiseRandom", new Class[0]);
                Class<SurfaceSystem> surfaceSystemClass = SurfaceSystem.class;
                this.buildSurfaceMethod = surfaceSystemClass.getDeclaredMethod("buildSurface", RandomState.class, BiomeManager.class, Registry.class, Boolean.TYPE, WorldGenerationContext.class, ChunkAccess.class, NoiseChunk.class, SurfaceRules.RuleSource.class);
                for (String fieldName : new String[]{"preliminarySurfaceLevel", "surfaceHeightEstimateCache", "f_224353_"}) {
                    try {
                        this.preliminarySurfaceLevelField = NoiseChunk.class.getDeclaredField(fieldName);
                        break;
                    }
                    catch (NoSuchFieldException noSuchFieldException) {
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

