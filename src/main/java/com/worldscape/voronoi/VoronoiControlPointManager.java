/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.worldscape.WorldScape
 */
package com.worldscape.voronoi;

import com.worldscape.WorldScape;
import com.worldscape.terrain.ControlPointManager;
import com.worldscape.terrain.ControlPointRegion;
import com.worldscape.terrain.MacroRegionInfo;
import com.worldscape.terrain.MacroVoronoiSystem;
import com.worldscape.terrain.TerrainControlPoint;
import com.worldscape.terrain.TerrainType;
import com.worldscape.voronoi.IncrementalVoronoiUpdater;
import com.worldscape.voronoi.VoronoiControlPoint;
import com.worldscape.voronoi.VoronoiDataPersistence;
import com.worldscape.voronoi.VoronoiDiagram;
import com.worldscape.voronoi.VoronoiSpatialIndex;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class VoronoiControlPointManager {
    private static final int AUTO_SAVE_INTERVAL = 600;
    public static final int MAX_POINTS = 5000;
    private static final AtomicInteger ID_COUNTER = new AtomicInteger(0);
    private final Map<String, VoronoiControlPoint> pointById = new ConcurrentHashMap<String, VoronoiControlPoint>();
    private final CopyOnWriteArrayList<VoronoiControlPoint> allPoints = new CopyOnWriteArrayList();
    private final VoronoiSpatialIndex spatialIndex = new VoronoiSpatialIndex();
    private final VoronoiDiagram voronoiDiagram = new VoronoiDiagram();
    private final IncrementalVoronoiUpdater diagramUpdater;
    private final Set<String> selectedIds = ConcurrentHashMap.newKeySet();
    private final AtomicInteger pointCount = new AtomicInteger(0);
    private volatile boolean isDirty = false;
    private volatile int ticksSinceLastSave = 0;
    private volatile boolean diagramNeedsUpdate = true;
    private volatile boolean terrainSystemLinked = false;

    public VoronoiControlPointManager() {
        this.diagramUpdater = new IncrementalVoronoiUpdater(this.voronoiDiagram, this.spatialIndex);
    }

    public VoronoiControlPoint createPoint(int x, int z, int color) {
        if (this.pointCount.get() >= 5000) {
            WorldScape.LOGGER.warn("Maximum control point limit reached ({})", (Object)5000);
            return null;
        }
        String id = "cp_" + ID_COUNTER.incrementAndGet() + "_" + System.currentTimeMillis();
        VoronoiControlPoint point = new VoronoiControlPoint(id, x, z, color);
        this.addPoint(point);
        return point;
    }

    public void addPoint(VoronoiControlPoint point) {
        if (point == null || this.pointById.containsKey(point.getId())) {
            return;
        }
        this.pointById.put(point.getId(), point);
        this.allPoints.add(point);
        this.spatialIndex.insert(point);
        this.pointCount.incrementAndGet();
        this.diagramNeedsUpdate = true;
        this.markDirty();
    }

    public boolean removePoint(String pointId) {
        VoronoiControlPoint point = this.pointById.remove(pointId);
        if (point == null) {
            return false;
        }
        this.allPoints.remove(point);
        this.spatialIndex.remove(point);
        this.selectedIds.remove(pointId);
        this.pointCount.decrementAndGet();
        this.diagramNeedsUpdate = true;
        this.markDirty();
        return true;
    }

    public boolean movePoint(String pointId, int newX, int newZ) {
        VoronoiControlPoint point = this.pointById.get(pointId);
        if (point == null) {
            return false;
        }
        this.spatialIndex.remove(point);
        point.setX(newX);
        point.setZ(newZ);
        this.spatialIndex.insert(point);
        this.diagramNeedsUpdate = true;
        this.markDirty();
        return true;
    }

    public boolean updatePoint(String pointId, Function<VoronoiControlPoint, Boolean> updater) {
        VoronoiControlPoint point = this.pointById.get(pointId);
        if (point == null) {
            return false;
        }
        boolean changed = updater.apply(point);
        if (changed) {
            this.markDirty();
        }
        return true;
    }

    public void selectSingle(String pointId) {
        if (this.pointById.containsKey(pointId)) {
            this.selectedIds.clear();
            this.selectedIds.add(pointId);
            this.updateSelectionStates();
        }
    }

    public void toggleSelection(String pointId) {
        if (this.selectedIds.contains(pointId)) {
            this.selectedIds.remove(pointId);
        } else {
            this.selectedIds.add(pointId);
        }
        this.updateSelectionStates();
    }

    public void selectBox(int minX, int minZ, int maxX, int maxZ) {
        this.selectedIds.clear();
        for (VoronoiControlPoint point : this.allPoints) {
            int px = point.getX();
            int pz = point.getZ();
            if (px < Math.min(minX, maxX) || px > Math.max(minX, maxX) || pz < Math.min(minZ, maxZ) || pz > Math.max(minZ, maxZ)) continue;
            this.selectedIds.add(point.getId());
        }
        this.updateSelectionStates();
    }

    public void selectAll() {
        this.selectedIds.clear();
        for (VoronoiControlPoint point : this.allPoints) {
            this.selectedIds.add(point.getId());
        }
        this.updateSelectionStates();
    }

    public void deselectAll() {
        this.selectedIds.clear();
        this.updateSelectionStates();
    }

    public Set<String> getSelectedIds() {
        return Collections.unmodifiableSet(this.selectedIds);
    }

    public List<VoronoiControlPoint> getSelectedPoints() {
        ArrayList<VoronoiControlPoint> selected = new ArrayList<VoronoiControlPoint>(this.selectedIds.size());
        for (String id : this.selectedIds) {
            VoronoiControlPoint point = this.pointById.get(id);
            if (point == null) continue;
            selected.add(point);
        }
        return selected;
    }

    public VoronoiControlPoint getById(String pointId) {
        return this.pointById.get(pointId);
    }

    public VoronoiControlPoint findNearest(int worldX, int worldZ, int maxRadius) {
        return this.spatialIndex.findNearest(worldX, worldZ, maxRadius);
    }

    public List<VoronoiControlPoint> queryRadius(int worldX, int worldZ, int radius) {
        return this.spatialIndex.queryRadius(worldX, worldZ, radius);
    }

    public List<VoronoiControlPoint> queryViewport(int minX, int minZ, int maxX, int maxZ) {
        return this.spatialIndex.queryViewport(minX, minZ, maxX, maxZ);
    }

    public List<VoronoiControlPoint> getAllPoints() {
        return new ArrayList<VoronoiControlPoint>(this.allPoints);
    }

    public int getPointCount() {
        return this.pointCount.get();
    }

    public void importFromTerrainSystem(int regionCenterX, int regionCenterZ, int radius, MacroVoronoiSystem macroSystem, ControlPointManager controlPointManager) {
        this.clearAll();
        int initialCount = 0;
        try {
            int cellRadius = radius / 2048 + 2;
            int centerCellX = Math.floorDiv(regionCenterX, 2048);
            int centerCellZ = Math.floorDiv(regionCenterZ, 2048);
            for (int dx = -cellRadius; dx <= cellRadius; ++dx) {
                for (int dz = -cellRadius; dz <= cellRadius; ++dz) {
                    int cellX = centerCellX + dx;
                    int cellZ = centerCellZ + dz;
                    int worldX = cellX * 2048;
                    int worldZ = cellZ * 2048;
                    MacroRegionInfo info = macroSystem.getRegionInfo(worldX, worldZ);
                    int color = this.getTierColor(info.elevationTier);
                    String label = "Tier " + info.elevationTier + " (" + info.tectonic.name() + ")";
                    String id = "macro_" + cellX + "_" + cellZ;
                    if (this.pointById.containsKey(id)) continue;
                    VoronoiControlPoint vp = new VoronoiControlPoint(id, worldX, worldZ, color);
                    vp.setLabel(label);
                    vp.setWeight((float)info.elevationTier / 5.0f);
                    vp.setTerrainType("Tectonic:" + info.tectonic.name());
                    this.addPoint(vp);
                }
            }
            initialCount = this.pointCount.get();
            WorldScape.LOGGER.info("Imported {} macro control points from terrain system", (Object)initialCount);
        }
        catch (RuntimeException e) {
            WorldScape.LOGGER.error("Failed to import macro control points", (Throwable)e);
        }
        if (controlPointManager != null) {
            try {
                this.importMicroControlPoints(regionCenterX, regionCenterZ, radius, controlPointManager);
            }
            catch (RuntimeException e) {
                WorldScape.LOGGER.error("Failed to import micro control points", (Throwable)e);
            }
        }
        this.terrainSystemLinked = true;
    }

    private void importMicroControlPoints(int regionCenterX, int regionCenterZ, int radius, ControlPointManager controlPointManager) {
        int regionSize = 512;
        int regionRadius = radius / regionSize + 1;
        int centerRegionX = Math.floorDiv(regionCenterX, regionSize);
        int centerRegionZ = Math.floorDiv(regionCenterZ, regionSize);
        int microPointCount = 0;
        for (int dx = -regionRadius; dx <= regionRadius; ++dx) {
            for (int dz = -regionRadius; dz <= regionRadius; ++dz) {
                int regionX = centerRegionX + dx;
                int regionZ = centerRegionZ + dz;
                ControlPointRegion cpRegion = controlPointManager.getRegion(regionX * 512, regionZ * 512);
                if (cpRegion == null) continue;
                List<TerrainControlPoint> points = cpRegion.getControlPoints();
                int pointIndex = 0;
                for (TerrainControlPoint tp : points) {
                    String id = "micro_" + regionX + "_" + regionZ + "_" + pointIndex;
                    if (this.pointById.containsKey(id)) {
                        ++pointIndex;
                        continue;
                    }
                    int color = this.getTerrainTypeColor(tp.getTerrainType());
                    VoronoiControlPoint vp = new VoronoiControlPoint(id, tp.getX(), tp.getZ(), color);
                    vp.setLabel(tp.getTerrainType().name());
                    vp.setWeight((float)(0.5 + tp.getElevationOffset() / 200.0));
                    vp.setTerrainType(tp.getTerrainType().name());
                    this.addPoint(vp);
                    ++microPointCount;
                    ++pointIndex;
                }
            }
        }
        WorldScape.LOGGER.info("Imported {} micro control points from terrain system", (Object)microPointCount);
    }

    private int getTerrainTypeColor(TerrainType type) {
        if (type == TerrainType.HIGH_MOUNTAINS || type == TerrainType.PEAK || type == TerrainType.HORN) {
            return -2937041;
        } else if (type == TerrainType.CLIFF || type == TerrainType.SEA_CLIFF) {
            return -43230;
        } else if (type == TerrainType.HILLS) {
            return -16121;
        } else if (type == TerrainType.PLATEAU || type == TerrainType.DOME || type == TerrainType.RIDGE) {
            return -6543440;
        } else if (type == TerrainType.PLAINS) {
            return -11751600;
        } else if (type == TerrainType.CANYON || type == TerrainType.VALLEY || type == TerrainType.GLACIAL_VALLEY) {
            return -14575885;
        } else if (type == TerrainType.BEACH || type == TerrainType.DELTA) {
            return -16728876;
        } else if (type == TerrainType.DUNE || type == TerrainType.GOBI || type == TerrainType.YARDANG) {
            return -1249295;
        } else if (type == TerrainType.TRENCH || type == TerrainType.BASIN || type == TerrainType.SINKHOLE) {
            return -15064194;
        } else if (type == TerrainType.ICE_SHEET || type == TerrainType.CIRQUE) {
            return -4987396;
        } else if (type == TerrainType.FLOODPLAIN || type == TerrainType.ALLUVIAL_FAN) {
            return -7617718;
        } else if (type == TerrainType.SEA_PLATEAU) {
            return -15906911;
        } else if (type == TerrainType.SALT_FLAT) {
            return -657931;
        } else if (type == TerrainType.PEAK_FOREST) {
            return -13070788;
        } else if (type == TerrainType.FJORD) {
            return -15108398;
        }
        return -6381922;
    }

    private int getTierColor(int tier) {
        switch (tier) {
            case 0: {
                return -15064194;
            }
            case 1: {
                return -15906911;
            }
            case 2: {
                return -16615491;
            }
            case 3: {
                return -13730510;
            }
            case 4: {
                return -688361;
            }
            case 5: {
                return -2937041;
            }
        }
        return -1;
    }

    public boolean save(File saveDir) {
        boolean success = VoronoiDataPersistence.save(this.allPoints, saveDir);
        if (success) {
            this.isDirty = false;
            this.ticksSinceLastSave = 0;
        }
        return success;
    }

    public void load(File saveDir) {
        this.clearAll();
        List<VoronoiControlPoint> loaded = VoronoiDataPersistence.load(saveDir);
        for (VoronoiControlPoint point : loaded) {
            this.addPoint(point);
        }
        WorldScape.LOGGER.info("Loaded {} Voronoi control points", (Object)this.pointCount.get());
    }

    public void tick() {
        ++this.ticksSinceLastSave;
        if (!this.isDirty || this.ticksSinceLastSave >= 600) {
            // empty if block
        }
    }

    public void clearAll() {
        this.pointById.clear();
        this.allPoints.clear();
        this.spatialIndex.clear();
        this.selectedIds.clear();
        this.pointCount.set(0);
        this.voronoiDiagram.clear();
        this.diagramNeedsUpdate = true;
        this.markDirty();
    }

    public void deleteSelected() {
        for (String id : new ArrayList<String>(this.selectedIds)) {
            this.removePoint(id);
        }
        this.selectedIds.clear();
    }

    public long estimateMemoryUsage() {
        long base = 64L;
        base += (long)this.pointById.size() * 128L;
        base += (long)this.allPoints.size() * 32L;
        base += (long)this.selectedIds.size() * 48L;
        return base += this.spatialIndex.estimateMemoryUsage();
    }

    public boolean isTerrainSystemLinked() {
        return this.terrainSystemLinked;
    }

    public VoronoiSpatialIndex getSpatialIndex() {
        return this.spatialIndex;
    }

    public VoronoiDiagram getVoronoiDiagram() {
        if (this.diagramNeedsUpdate) {
            this.updateVoronoiDiagram();
        }
        return this.voronoiDiagram;
    }

    public boolean isDiagramNeedsUpdate() {
        return this.diagramNeedsUpdate;
    }

    public void updateVoronoiDiagram() {
        if (!this.diagramNeedsUpdate) {
            return;
        }
        try {
            this.diagramUpdater.recomputeLocalDiagram();
            this.diagramNeedsUpdate = false;
        }
        catch (RuntimeException e) {
            WorldScape.LOGGER.error("Failed to update Voronoi diagram", (Throwable)e);
        }
    }

    private void updateSelectionStates() {
        for (VoronoiControlPoint point : this.allPoints) {
            point.setSelected(this.selectedIds.contains(point.getId()));
        }
    }

    private void markDirty() {
        this.isDirty = true;
        this.ticksSinceLastSave = 0;
    }
}

