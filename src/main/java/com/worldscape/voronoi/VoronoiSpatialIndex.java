package com.worldscape.voronoi;

import com.worldscape.voronoi.VoronoiControlPoint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class VoronoiSpatialIndex {
    public static final int CELL_SIZE = 512;
    private static final int MAX_QUERY_CACHE = 256;
    private final Map<Long, List<VoronoiControlPoint>> grid = new ConcurrentHashMap<Long, List<VoronoiControlPoint>>();
    private final AtomicInteger pointCount = new AtomicInteger(0);
    private final Map<Long, List<VoronoiControlPoint>> queryCache = Collections.synchronizedMap(new LinkedHashMap<Long, List<VoronoiControlPoint>>(64, 0.75f, true){

        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, List<VoronoiControlPoint>> eldest) {
            return this.size() > 256;
        }
    });

    public void insert(VoronoiControlPoint point) {
        if (point == null) {
            return;
        }
        long cellKey = this.getCellKey(point.getX(), point.getZ());
        this.grid.computeIfAbsent(cellKey, k -> new ArrayList()).add(point);
        this.pointCount.incrementAndGet();
        this.invalidateQueryCache(point.getX(), point.getZ());
    }

    public boolean remove(VoronoiControlPoint point) {
        if (point == null) {
            return false;
        }
        long cellKey = this.getCellKey(point.getX(), point.getZ());
        List<VoronoiControlPoint> cell = this.grid.get(cellKey);
        if (cell == null) {
            return false;
        }
        boolean removed = cell.remove(point);
        if (removed) {
            this.pointCount.decrementAndGet();
            if (cell.isEmpty()) {
                this.grid.remove(cellKey);
            }
            this.invalidateQueryCache(point.getX(), point.getZ());
        }
        return removed;
    }

    public void clear() {
        this.grid.clear();
        this.queryCache.clear();
        this.pointCount.set(0);
    }

    public List<VoronoiControlPoint> queryRadius(int cx, int cz, int radius) {
        long cacheKey = (long)cx << 32 | (long)cz & 0xFFFFFFFFL ^ (long)radius;
        List<VoronoiControlPoint> cached = this.queryCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        long radiusSq = (long)radius * (long)radius;
        int minCellX = Math.floorDiv(cx - radius, 512);
        int maxCellX = Math.floorDiv(cx + radius, 512);
        int minCellZ = Math.floorDiv(cz - radius, 512);
        int maxCellZ = Math.floorDiv(cz + radius, 512);
        ArrayList<VoronoiControlPoint> result = new ArrayList<VoronoiControlPoint>();
        for (int cellX = minCellX; cellX <= maxCellX; ++cellX) {
            for (int cellZ = minCellZ; cellZ <= maxCellZ; ++cellZ) {
                long cellKey = this.getCellKey(cellX, cellZ);
                List<VoronoiControlPoint> cell = this.grid.get(cellKey);
                if (cell == null) continue;
                for (VoronoiControlPoint p : cell) {
                    if (!p.isWithinRadius(cx, cz, radiusSq)) continue;
                    result.add(p);
                }
            }
        }
        if (result.size() < 500) {
            this.queryCache.put(cacheKey, new ArrayList<VoronoiControlPoint>(result));
        }
        return result;
    }

    public List<VoronoiControlPoint> queryViewport(int viewportMinX, int viewportMinZ, int viewportMaxX, int viewportMaxZ) {
        int minCellX = Math.floorDiv(viewportMinX, 512);
        int maxCellX = Math.floorDiv(viewportMaxX, 512);
        int minCellZ = Math.floorDiv(viewportMinZ, 512);
        int maxCellZ = Math.floorDiv(viewportMaxZ, 512);
        ArrayList<VoronoiControlPoint> result = new ArrayList<VoronoiControlPoint>();
        for (int cellX = minCellX; cellX <= maxCellX; ++cellX) {
            for (int cellZ = minCellZ; cellZ <= maxCellZ; ++cellZ) {
                long cellKey = this.getCellKey(cellX, cellZ);
                List<VoronoiControlPoint> cell = this.grid.get(cellKey);
                if (cell == null) continue;
                for (VoronoiControlPoint p : cell) {
                    if (p.getX() < viewportMinX || p.getX() > viewportMaxX || p.getZ() < viewportMinZ || p.getZ() > viewportMaxZ) continue;
                    result.add(p);
                }
            }
        }
        return result;
    }

    public VoronoiControlPoint findNearest(int cx, int cz, int maxSearchRadius) {
        List<VoronoiControlPoint> nearby = this.queryRadius(cx, cz, maxSearchRadius);
        if (nearby.isEmpty()) {
            return null;
        }
        VoronoiControlPoint nearest = null;
        long minDistSq = Long.MAX_VALUE;
        for (VoronoiControlPoint p : nearby) {
            long distSq = p.squaredDistanceTo(cx, cz);
            if (distSq >= minDistSq) continue;
            minDistSq = distSq;
            nearest = p;
        }
        return nearest;
    }

    public List<VoronoiControlPoint> getCellContents(int worldX, int worldZ) {
        long cellKey = this.getCellKey(worldX, worldZ);
        List<VoronoiControlPoint> cell = this.grid.get(cellKey);
        return cell != null ? cell : List.of();
    }

    public int getPointCount() {
        return this.pointCount.get();
    }

    public boolean isEmpty() {
        return this.pointCount.get() == 0;
    }

    public List<VoronoiControlPoint> getAllPoints() {
        ArrayList<VoronoiControlPoint> all = new ArrayList<VoronoiControlPoint>(this.pointCount.get());
        for (List<VoronoiControlPoint> cell : this.grid.values()) {
            all.addAll(cell);
        }
        return all;
    }

    private long getCellKey(int worldX, int worldZ) {
        int cellX = Math.floorDiv(worldX, 512);
        int cellZ = Math.floorDiv(worldZ, 512);
        return (long)cellX << 32 | (long)cellZ & 0xFFFFFFFFL;
    }

    private void invalidateQueryCache(int worldX, int worldZ) {
        int cellX = Math.floorDiv(worldX, 512);
        int cellZ = Math.floorDiv(worldZ, 512);
        this.queryCache.clear();
    }

    public long estimateMemoryUsage() {
        long base = 64L;
        base += (long)this.grid.size() * 48L;
        base += (long)this.pointCount.get() * 32L;
        return base += (long)this.queryCache.size() * 128L;
    }
}

