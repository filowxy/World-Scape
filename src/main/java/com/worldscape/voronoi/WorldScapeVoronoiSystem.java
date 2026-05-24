package com.worldscape.voronoi;

import com.worldscape.WorldScape;
import com.worldscape.terrain.ControlPointManager;
import com.worldscape.terrain.MacroVoronoiSystem;
import com.worldscape.voronoi.VoronoiCamera;
import com.worldscape.voronoi.VoronoiControlPointManager;
import com.worldscape.voronoi.VoronoiInputHandler;
import com.worldscape.voronoi.VoronoiOverlayRenderer;
import com.worldscape.voronoi.VoronoiSpatialIndex;
import java.io.File;

public class WorldScapeVoronoiSystem {
    private static final String VORONOI_DATA_DIR = "voronoi";
    private static volatile boolean initialized = false;
    private static final Object INIT_LOCK = new Object();
    private static volatile boolean enabled = false;
    private static volatile VoronoiControlPointManager controlPointManager;
    private static volatile VoronoiCamera camera;
    private static volatile VoronoiSpatialIndex spatialIndex;
    private static volatile File saveDirectory;

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static void init(File gameDir) {
        if (initialized) {
            return;
        }
        Object object = INIT_LOCK;
        synchronized (object) {
            if (initialized) {
                return;
            }
            WorldScape.LOGGER.info("[WorldScapeVoronoi] Initializing Voronoi visualization system");
            controlPointManager = new VoronoiControlPointManager();
            camera = new VoronoiCamera();
            spatialIndex = controlPointManager.getSpatialIndex();
            if (gameDir != null) {
                saveDirectory = new File(gameDir, "config" + File.separator + "worldscape" + File.separator + VORONOI_DATA_DIR);
            }
            initialized = true;
            WorldScape.LOGGER.info("[WorldScapeVoronoi] Voronoi visualization system initialized");
        }
    }

    public static boolean isInitialized() {
        return initialized;
    }

    private static void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException("WorldScapeVoronoiSystem has not been initialized. Call init() first.");
        }
    }

    public static VoronoiControlPointManager getControlPointManager() {
        WorldScapeVoronoiSystem.ensureInitialized();
        return controlPointManager;
    }

    public static VoronoiCamera getCamera() {
        WorldScapeVoronoiSystem.ensureInitialized();
        return camera;
    }

    public static VoronoiSpatialIndex getSpatialIndex() {
        WorldScapeVoronoiSystem.ensureInitialized();
        return spatialIndex;
    }

    public static boolean isEnabled() {
        return enabled && initialized;
    }

    public static File getSaveDirectory() {
        return saveDirectory;
    }

    public static boolean toggle() {
        WorldScapeVoronoiSystem.ensureInitialized();
        boolean bl = enabled = !enabled;
        if (enabled) {
            WorldScape.LOGGER.info("[WorldScapeVoronoi] Voronoi visualization enabled");
            VoronoiOverlayRenderer.setEnabled(true);
        } else {
            WorldScape.LOGGER.info("[WorldScapeVoronoi] Voronoi visualization disabled");
            VoronoiOverlayRenderer.setEnabled(false);
            VoronoiInputHandler.resetState();
        }
        return enabled;
    }

    public static void enable() {
        WorldScapeVoronoiSystem.ensureInitialized();
        if (!enabled) {
            WorldScapeVoronoiSystem.toggle();
        }
    }

    public static void disable() {
        WorldScapeVoronoiSystem.ensureInitialized();
        if (enabled) {
            WorldScapeVoronoiSystem.toggle();
        }
    }

    public static void tick() {
        if (!initialized || !enabled) {
            return;
        }
        if (controlPointManager != null) {
            controlPointManager.tick();
        }
        if (camera != null) {
            // empty if block
        }
    }

    public static void populateFromTerrainSystem(int regionCenterX, int regionCenterZ, int radius, MacroVoronoiSystem macroSystem, ControlPointManager terrainControlPointManager) {
        WorldScapeVoronoiSystem.ensureInitialized();
        if (macroSystem == null || terrainControlPointManager == null) {
            WorldScape.LOGGER.warn("[WorldScapeVoronoi] Cannot populate from terrain system: null references");
            return;
        }
        WorldScape.LOGGER.info("[WorldScapeVoronoi] Populating Voronoi points from terrain system (center: {},{} radius: {})", new Object[]{regionCenterX, regionCenterZ, radius});
        try {
            controlPointManager.importFromTerrainSystem(regionCenterX, regionCenterZ, radius, macroSystem, terrainControlPointManager);
            camera.setPosition(regionCenterX, regionCenterZ);
            WorldScape.LOGGER.info("[WorldScapeVoronoi] Populated {} Voronoi control points from terrain system", (Object)controlPointManager.getPointCount());
        }
        catch (RuntimeException e) {
            WorldScape.LOGGER.error("[WorldScapeVoronoi] Failed to populate from terrain system", (Throwable)e);
        }
    }

    public static boolean save() {
        WorldScapeVoronoiSystem.ensureInitialized();
        if (saveDirectory == null) {
            WorldScape.LOGGER.warn("[WorldScapeVoronoi] Cannot save: no save directory configured");
            return false;
        }
        try {
            boolean success = controlPointManager.save(saveDirectory);
            if (success) {
                WorldScape.LOGGER.info("[WorldScapeVoronoi] Saved {} control points", (Object)controlPointManager.getPointCount());
            }
            return success;
        }
        catch (RuntimeException e) {
            WorldScape.LOGGER.error("[WorldScapeVoronoi] Failed to save Voronoi data", (Throwable)e);
            return false;
        }
    }

    public static boolean load() {
        WorldScapeVoronoiSystem.ensureInitialized();
        if (saveDirectory == null) {
            WorldScape.LOGGER.warn("[WorldScapeVoronoi] Cannot load: no save directory configured");
            return false;
        }
        try {
            if (!saveDirectory.exists() && !saveDirectory.mkdirs()) {
                WorldScape.LOGGER.warn("[WorldScapeVoronoi] Failed to create Voronoi data directory");
                return false;
            }
            controlPointManager.load(saveDirectory);
            WorldScape.LOGGER.info("[WorldScapeVoronoi] Loaded {} control points", (Object)controlPointManager.getPointCount());
            return true;
        }
        catch (RuntimeException e) {
            WorldScape.LOGGER.error("[WorldScapeVoronoi] Failed to load Voronoi data", (Throwable)e);
            return false;
        }
    }

    public static void clear() {
        WorldScapeVoronoiSystem.ensureInitialized();
        controlPointManager.clearAll();
        camera.reset();
        WorldScape.LOGGER.info("[WorldScapeVoronoi] Cleared all Voronoi data");
    }
}

