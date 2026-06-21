package com.worldscape.terrain;

import com.worldscape.WorldScape;
import net.minecraft.ResourceLocationException;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TerrainTypeRegistry - Thread-safe registry for terrain types with namespace isolation.
 * <p>
 * TerrainTypeRegistry - 具有命名空间隔离的线程安全地形类型注册表。
 * <p>
 * Each terrain type is keyed by a {@link ResourceLocation}, preventing namespace collisions
 * and allowing multiple mods to register their own terrain types independently.
 * 每个地形类型以 {@link ResourceLocation} 为键，防止命名空间冲突，
 * 允许多个模组独立注册自己的地形类型。
 */
public final class TerrainTypeRegistry {

    private static final ConcurrentHashMap<ResourceLocation, TerrainType> registry = new ConcurrentHashMap<>();

    private TerrainTypeRegistry() {
        throw new UnsupportedOperationException("Utility class - do not instantiate / 工具类 - 不可实例化");
    }

    /**
     * Register a terrain type with the given key.
     * <p>
     * 使用给定的键注册一个地形类型。
     * <p>
     * If the key already exists, the registration is rejected and an ERROR is logged.
     * 如果键已存在，则拒绝注册并记录 ERROR 日志。
     *
     * @param key  the unique ResourceLocation key / 唯一的 ResourceLocation 键
     * @param type the TerrainType to register / 要注册的 TerrainType
     */
    public static void register(ResourceLocation key, TerrainType type) {
        // 空值校验：防止 NPE
        // Null check: prevent NPE
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(type, "type must not be null");
        TerrainType existing = registry.putIfAbsent(key, type);
        if (existing != null) {
            WorldScape.LOGGER.error(
                "[TerrainTypeRegistry] Duplicate registration attempt for key '{}': existing={}, rejected={}",
                key, existing.getId(), type.getId()
            );
        }
    }

    /**
     * Get the terrain type registered under the given key.
     * <p>
     * 获取在给定键下注册的地形类型。
     *
     * @param key the ResourceLocation key / ResourceLocation 键
     * @return the registered TerrainType, or null if not found / 已注册的 TerrainType，若未找到则返回 null
     */
    public static TerrainType get(ResourceLocation key) {
        return registry.get(key);
    }

    /**
     * Get the terrain type registered under the given string id in "namespace:id" format.
     * <p>
     * 获取在给定字符串 id（格式为 "namespace:id"）下注册的地形类型。
     *
     * @param id the string id in "namespace:id" format / 字符串 id，格式为 "namespace:id"
     * @return the registered TerrainType, or null if not found / 已注册的 TerrainType，若未找到则返回 null
     */
    public static TerrainType get(String id) {
        // 空值/空白值校验：防止 NPE 和无效解析
        // Null/blank check: prevent NPE and invalid parsing
        if (id == null || id.isBlank()) return null;
        try {
            ResourceLocation key = ResourceLocation.parse(id);
            return get(key);
        } catch (ResourceLocationException e) {
            WorldScape.LOGGER.error("[TerrainTypeRegistry] Invalid resource location id: '{}', error: {}", id, e.getMessage());
            return null;
        }
    }

    /**
     * Get an unmodifiable collection of all registered terrain types.
     * <p>
     * 获取所有已注册地形类型的不可修改集合。
     *
     * @return unmodifiable collection of all TerrainTypes / 所有 TerrainType 的不可修改集合
     */
    public static Collection<TerrainType> getAll() {
        return Collections.unmodifiableCollection(registry.values());
    }

    /**
     * Get all registered terrain types that are valid for the given elevation tier.
     * <p>
     * 获取所有对给定海拔层级有效的地形类型。
     * <p>
     * Filters using {@link TerrainType#isValidForTier(TerrainType, int)}.
     * 使用 {@link TerrainType#isValidForTier(TerrainType, int)} 进行过滤。
     *
     * @param tier the elevation tier to filter by / 用于过滤的海拔层级
     * @return list of TerrainTypes valid for the tier / 该层级有效的地形类型列表
     */
    public static List<TerrainType> getTypesForTier(int tier) {
        List<TerrainType> result = new ArrayList<>();
        for (TerrainType type : registry.values()) {
            if (TerrainType.isValidForTier(type, tier)) {
                result.add(type);
            }
        }
        return result;
    }

    /**
     * Check whether a terrain type is registered under the given key.
     * <p>
     * 检查是否有地形类型在给定键下注册。
     *
     * @param key the ResourceLocation key / ResourceLocation 键
     * @return true if registered, false otherwise / 若已注册返回 true，否则返回 false
     */
    public static boolean contains(ResourceLocation key) {
        return registry.containsKey(key);
    }

    /**
     * Get the number of registered terrain types.
     * <p>
     * 获取已注册地形类型的数量。
     *
     * @return registry size / 注册表中的数量
     */
    public static int size() {
        return registry.size();
    }

    /**
     * Clear all registered terrain types. Intended for testing or reloading.
     * <p>
     * 清除所有已注册的地形类型。用于测试或重载。
     */
    public static void clear() {
        registry.clear();
    }
}