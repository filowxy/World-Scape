package com.worldscape.compat.c2me;

import com.worldscape.WorldScape;
import com.worldscape.generator.LandscapeChunkGenerator;
import com.worldscape.terrain.RegionController;
import net.minecraft.world.level.chunk.ChunkGenerator;

public class C2MECompatibility {
    private static final boolean C2ME_PRESENT;
    private static volatile boolean initialized = false;
    private static volatile CompatibilityMode mode;
    private static final int C2ME_CACHE_SIZE = 4096;

    // Thread-safe initialization: volatile fields + synchronized method ensure
    // visibility and atomicity across multiple threads during first-time init.
    // 线程安全初始化：volatile 字段 + synchronized 方法确保多线程环境下
    // 首次初始化时的可见性和原子性。
    public static synchronized void initialize(CompatibilityMode configMode) {
        if (initialized) {
            return;
        }
        initialized = true;
        mode = configMode;
        if (!C2ME_PRESENT) {
            WorldScape.LOGGER.info("[C2ME Compat] C2ME not detected, using standard mode");
            return;
        }
        switch (mode.ordinal()) {
            case 3: {
                WorldScape.LOGGER.warn("[C2ME Compat] C2ME compatibility disabled by config");
                break;
            }
            case 0: {
                WorldScape.LOGGER.info("[C2ME Compat] Auto mode - C2ME detected, enabling full compatibility");
                C2MECompatibility.enableFullCompatibility();
                break;
            }
            case 1: {
                WorldScape.LOGGER.info("[C2ME Compat] Full compatibility mode enabled");
                C2MECompatibility.enableFullCompatibility();
                break;
            }
            case 2: {
                WorldScape.LOGGER.info("[C2ME Compat] Minimal compatibility mode enabled");
            }
        }
    }

    private static void enableFullCompatibility() {
        // 当 C2ME 并行化 fillFromNoise 时，terrainRegionCache 的 synchronized 溢出路径成为全局串行瓶颈。
        // 增大缓存 → 无锁 computeIfAbsent 路径命中率更高 → 减少争用。
        // When C2ME parallelizes fillFromNoise, the synchronized overflow path of terrainRegionCache
        // becomes a global serial bottleneck.
        // Larger cache → higher hit rate on lock-free computeIfAbsent path → reduced contention.
        RegionController.setCacheMaxSize(C2ME_CACHE_SIZE);
        WorldScape.LOGGER.info("[C2ME Compat] Full compatibility enabled — RegionController cache size set to {}", RegionController.getCacheMaxSize());
    }

    public static void validateChunkGenerator(ChunkGenerator generator, boolean isNewWorld) {
        if (!C2ME_PRESENT) {
            return;
        }
        if (!(generator instanceof LandscapeChunkGenerator)) {
            String message = "[C2ME Compat] ChunkGenerator is not LandscapeChunkGenerator: " + generator.getClass().getName();
            if (isNewWorld) {
                WorldScape.LOGGER.error(message);
                throw new RuntimeException(message + " in new world");
            }
            WorldScape.LOGGER.warn(message + " - old archive read-only mode");
        }
    }

    public static String generateDiagnosticReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== World Scape C2ME Compatibility Diagnostic ===\n");
        report.append("C2ME Present: ").append(C2ME_PRESENT).append("\n");
        report.append("Compatibility Mode: ").append((Object)mode).append("\n");
        report.append("Initialized: ").append(initialized).append("\n");
        report.append("=================================================\n");
        return report.toString();
    }

    public static boolean isC2MEPresent() {
        return C2ME_PRESENT;
    }

    public static CompatibilityMode getMode() {
        return mode;
    }

    static {
        mode = CompatibilityMode.AUTO;
        boolean present = false;
        try {
            Class.forName("com.ishland.c2me.opts.generation.mixin.MixinThreadedAnvilChunkStorage");
            present = true;
        }
        catch (ClassNotFoundException e) {
            // C2ME not found is an expected scenario — log at DEBUG for diagnostics.
            // Fixed: was empty catch block — per AGENTS.md §3.4 all exceptions MUST be logged.
            // C2ME 未找到是预期场景 — 以 DEBUG 级别记录用于诊断。
            // 修复：原为空 catch 块 — 按 AGENTS.md §3.4 所有异常必须记录日志。
            WorldScape.LOGGER.debug("[C2ME Compat] C2ME not found on classpath, compatibility hooks will be skipped");
        }
        C2ME_PRESENT = present;
    }

    public static enum CompatibilityMode {
        AUTO,
        FULL,
        MINIMAL,
        DISABLED;

    }
}

