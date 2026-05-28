/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.resources.ResourceKey
 *  net.minecraft.world.level.Level
 */
package com.worldscape.terrain;

import com.worldscape.terrain.ControlPointManager;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public class TerrainVoronoiCache {
    private static final AtomicReference<ConcurrentHashMap<ResourceKey<Level>, ControlPointManager>> DIMENSION_CACHE = new AtomicReference(new ConcurrentHashMap());
    private static final int DEFAULT_CACHE_MAX_SIZE = 1024;
    private final ResourceKey<Level> dimensionKey;
    private final ControlPointManager manager;
    private final int maxCacheSize;

    public TerrainVoronoiCache(ResourceKey<Level> dimensionKey, long worldSeed, int seaLevel, int maxCacheSize) {
        this.dimensionKey = dimensionKey;
        this.manager = new ControlPointManager(worldSeed, seaLevel);
        this.maxCacheSize = maxCacheSize;
    }

    public TerrainVoronoiCache(ResourceKey<Level> dimensionKey, long worldSeed, int seaLevel) {
        this(dimensionKey, worldSeed, seaLevel, 1024);
    }

    public static ControlPointManager getOrCreate(Level world, long worldSeed, int seaLevel) {
        ResourceKey dimKey = world.dimension();
        ConcurrentHashMap<ResourceKey<Level>, ControlPointManager> cache = DIMENSION_CACHE.get();
        return cache.computeIfAbsent((ResourceKey<Level>)dimKey, key -> {
            ControlPointManager manager = new ControlPointManager(worldSeed, seaLevel);
            return manager;
        });
    }

    public static void clearDimensionCache(ResourceKey<Level> dimensionKey) {
        ConcurrentHashMap<ResourceKey<Level>, ControlPointManager> cache = DIMENSION_CACHE.get();
        ControlPointManager manager = cache.remove(dimensionKey);
        if (manager != null) {
            manager.clearCache();
        }
    }

    public static void clearAllCache() {
        ConcurrentHashMap<ResourceKey<Level>, ControlPointManager> oldCache = DIMENSION_CACHE.get();
        oldCache.forEach((key, manager) -> manager.clearCache());
        DIMENSION_CACHE.set(new ConcurrentHashMap());
    }

    public static ControlPointManager getManager(ResourceKey<Level> dimensionKey) {
        return DIMENSION_CACHE.get().get(dimensionKey);
    }

    public ControlPointManager getManager() {
        return this.manager;
    }

    public ResourceKey<Level> getDimensionKey() {
        return this.dimensionKey;
    }

    public int getMaxCacheSize() {
        return this.maxCacheSize;
    }
}

