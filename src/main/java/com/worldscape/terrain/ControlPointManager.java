/*
 * Decompiled with CFR 0.152.
 */
package com.worldscape.terrain;

import com.worldscape.terrain.ControlPointRegion;
import com.worldscape.terrain.MacroVoronoiSystem;
import com.worldscape.terrain.TerrainControlPoint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ControlPointManager {
    private static final int MAX_CACHE_SIZE = 1024;
    private final long seed;
    private final int seaLevel;
    private final MacroVoronoiSystem macroVoronoiSystem;
    private final Map<Long, ControlPointRegion> regions;

    public ControlPointManager(long seed, int seaLevel) {
        this.seed = seed;
        this.seaLevel = seaLevel;
        this.macroVoronoiSystem = new MacroVoronoiSystem(seed, seaLevel);
        this.regions = Collections.synchronizedMap(new LinkedHashMap<Long, ControlPointRegion>(256, 0.75f, true){

            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, ControlPointRegion> eldest) {
                return this.size() > 1024;
            }
        });
    }

    public ControlPointRegion getRegion(int x, int z) {
        int regionX = Math.floorDiv(x, ControlPointRegion.REGION_SIZE);
        int regionZ = Math.floorDiv(z, ControlPointRegion.REGION_SIZE);
        long key = (long)regionX << 32 | (long)regionZ & 0xFFFFFFFFL;
        return this.regions.computeIfAbsent(key, k -> {
            int centerBlockX = regionX * ControlPointRegion.REGION_SIZE + ControlPointRegion.REGION_SIZE / 2;
            int centerBlockZ = regionZ * ControlPointRegion.REGION_SIZE + ControlPointRegion.REGION_SIZE / 2;
            int macroTier = this.macroVoronoiSystem.getRegionInfo(centerBlockX, centerBlockZ).getElevationTier();
            return new ControlPointRegion(regionX, regionZ, this.seed, macroTier);
        });
    }

    public List<TerrainControlPoint> getNearbyControlPoints(int x, int z, double radius) {
        ArrayList<TerrainControlPoint> result = new ArrayList<TerrainControlPoint>();
        int regionX = Math.floorDiv(x, ControlPointRegion.REGION_SIZE);
        int regionZ = Math.floorDiv(z, ControlPointRegion.REGION_SIZE);
        for (int dx = -1; dx <= 1; ++dx) {
            for (int dz = -1; dz <= 1; ++dz) {
                ControlPointRegion region = this.getRegion(regionX + dx, regionZ + dz);
                for (TerrainControlPoint point : region.getPointsInRange(x, z, radius)) {
                    result.add(point);
                }
            }
        }
        return result;
    }

    public void clearCache() {
        this.regions.clear();
    }

    public int getCacheSize() {
        return this.regions.size();
    }

    public long getSeed() {
        return this.seed;
    }

    public MacroVoronoiSystem getMacroVoronoiSystem() {
        return this.macroVoronoiSystem;
    }
}

