package com.worldscape.terrain;

import com.worldscape.terrain.ControlPointManager;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class TerrainVoronoiCache {
    // Plain ConcurrentHashMap with thread-safe Iterator-based clearing —
    // avoids the redundant AtomicReference indirection and eliminates
    // the race window of .get() + .set() in clearAllCache.
    // 直接使用 ConcurrentHashMap + 基于 Iterator 的线程安全清理 —
    // 消除了 AtomicReference 的冗余包装，也消除了 clearAllCache 中
    // .get() + .set() 之间的竞态窗口。
    private static final ConcurrentHashMap<ResourceKey<Level>, ControlPointManager> DIMENSION_CACHE = new ConcurrentHashMap<>();

    private TerrainVoronoiCache() {
        // 纯静态工具类，禁止实例化 / Pure static utility class, instantiation prohibited
    }

    public static void clearDimensionCache(ResourceKey<Level> dimensionKey) {
        ControlPointManager manager = DIMENSION_CACHE.remove(dimensionKey);
        if (manager != null) {
            manager.clearCache();
        }
    }

    public static void clearAllCache() {
        Iterator<Map.Entry<ResourceKey<Level>, ControlPointManager>> it = DIMENSION_CACHE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ResourceKey<Level>, ControlPointManager> entry = it.next();
            entry.getValue().clearCache();
            it.remove();
        }
    }
}

