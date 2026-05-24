package com.worldscape.debug;

import com.worldscape.config.WelcomeScreenConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TerrainDebugSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(TerrainDebugSystem.class);
    private static volatile boolean debugLoggingEnabled = false;
    private static volatile boolean debugPillarsEnabled = false;
    private static volatile boolean detailedIssueDetection = false;
    private static volatile boolean enhancedHeightmapEnabled = true;
    private static volatile int chunkSampleRate = 50;
    private static volatile int lastLoggedChunkX = Integer.MIN_VALUE;
    private static volatile int lastLoggedChunkZ = Integer.MIN_VALUE;
    public static final int MAX_PILLAR_HEIGHT = 256;
    public static final int PILLAR_BASE_Y = -64;
    private static volatile boolean initialized = false;

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static void init() {
        if (initialized) {
            LOGGER.debug("[World Scape] TerrainDebugSystem already initialized");
            return;
        }
        Class<TerrainDebugSystem> clazz = TerrainDebugSystem.class;
        synchronized (TerrainDebugSystem.class) {
            if (initialized) {
                // ** MonitorExit[var0] (shouldn't be in output)
                return;
            }
            initialized = true;
            boolean debugMode = WelcomeScreenConfig.isDebugMode();
            if (debugMode) {
                debugLoggingEnabled = true;
                debugPillarsEnabled = true;
                detailedIssueDetection = true;
                chunkSampleRate = 10;
                LOGGER.info("[World Scape] Debug Mode ACTIVE - Advanced features enabled");
            } else {
                debugLoggingEnabled = false;
                debugPillarsEnabled = false;
                detailedIssueDetection = false;
                chunkSampleRate = 50;
                LOGGER.info("[World Scape] Debug Mode INACTIVE - Standard mode active");
            }
            LOGGER.info("[World Scape] TerrainDebugSystem initialized (debugMode={}, logging={}, pillars={}, sampleRate={})", new Object[]{debugMode, debugLoggingEnabled, debugPillarsEnabled, chunkSampleRate});
            // ** MonitorExit[var0] (shouldn't be in output)
            return;
        }
    }

    public static boolean isDebugMode() {
        return WelcomeScreenConfig.isDebugMode();
    }

    public static boolean isDetailedIssueDetectionEnabled() {
        return detailedIssueDetection && TerrainDebugSystem.isDebugMode();
    }

    public static boolean shouldLogChunk(int chunkX, int chunkZ) {
        if (!debugLoggingEnabled || !TerrainDebugSystem.isDebugMode() || chunkSampleRate <= 0) {
            return false;
        }
        int hash = Math.abs(chunkX * 31 + chunkZ * 17);
        return hash % chunkSampleRate == 0;
    }

    public static boolean isLoggingEnabled() {
        return debugLoggingEnabled && TerrainDebugSystem.isDebugMode();
    }

    public static void setLoggingEnabled(boolean enabled) {
        if (TerrainDebugSystem.isDebugMode()) {
            debugLoggingEnabled = enabled;
            LOGGER.info("[World Scape] Debug logging {}", (Object)(enabled ? "enabled" : "disabled"));
        } else {
            LOGGER.warn("[World Scape] Cannot enable logging: Debug Mode is OFF");
        }
    }

    public static boolean isPillarsEnabled() {
        return debugPillarsEnabled && TerrainDebugSystem.isDebugMode();
    }

    public static void setPillarsEnabled(boolean enabled) {
        if (TerrainDebugSystem.isDebugMode()) {
            debugPillarsEnabled = enabled;
            LOGGER.info("[World Scape] Debug pillars {}", (Object)(enabled ? "enabled" : "disabled"));
        } else {
            LOGGER.warn("[World Scape] Cannot enable pillars: Debug Mode is OFF");
        }
    }

    public static boolean isEnhancedHeightmapEnabled() {
        return enhancedHeightmapEnabled;
    }

    public static void setEnhancedHeightmapEnabled(boolean enabled) {
        enhancedHeightmapEnabled = enabled;
    }

    public static int getChunkSampleRate() {
        return chunkSampleRate;
    }

    public static void setChunkSampleRate(int rate) {
        chunkSampleRate = Math.max(0, rate);
        LOGGER.info("[World Scape] Chunk sample rate set to {}", rate == 0 ? "disabled" : "1/" + rate);
    }

    public static String getStatusReport() {
        return String.format("=== Terrain Debug System Status ===\nLogging: %s\nDebug Pillars: %s\nEnhanced Heightmap: %s\nChunk Sample Rate: %s\n==================================", debugLoggingEnabled ? "ON" : "OFF", debugPillarsEnabled ? "ON" : "OFF", enhancedHeightmapEnabled ? "ON" : "OFF", chunkSampleRate == 0 ? "DISABLED" : "1/" + chunkSampleRate);
    }

    public static void resetToDefaults() {
        debugLoggingEnabled = false;
        debugPillarsEnabled = false;
        enhancedHeightmapEnabled = true;
        chunkSampleRate = 50;
        lastLoggedChunkX = Integer.MIN_VALUE;
        lastLoggedChunkZ = Integer.MIN_VALUE;
        LOGGER.info("[World Scape] TerrainDebugSystem reset to defaults");
    }
}

