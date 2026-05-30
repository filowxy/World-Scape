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

public final class TerrainVoronoiCache {
    private static final AtomicReference<ConcurrentHashMap<ResourceKey<Level>, ControlPointManager>> DIMENSION_CACHE = new AtomicReference(new ConcurrentHashMap());

    private TerrainVoronoiCache() {
        // 纯静态工具类，禁止实例化 / Pure static utility class, instantiation prohibited
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
}

