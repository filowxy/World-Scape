package com.worldscape.compat.c2me;

import com.worldscape.WorldScape;
import com.worldscape.generator.LandscapeChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGenerator;

public class C2MECompatibility {
    private static final boolean C2ME_PRESENT;
    private static boolean initialized;
    private static CompatibilityMode mode;

    public static void initialize(CompatibilityMode configMode) {
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
        WorldScape.LOGGER.info("[C2ME Compat] Full compatibility enabled");
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
        initialized = false;
        mode = CompatibilityMode.AUTO;
        boolean present = false;
        try {
            Class.forName("com.ishland.c2me.opts.generation.mixin.MixinThreadedAnvilChunkStorage");
            present = true;
        }
        catch (ClassNotFoundException classNotFoundException) {
            // empty catch block
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

