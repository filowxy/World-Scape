/*
 * Decompiled with CFR 0.152.
 */
package com.worldscape.voronoi;

import com.worldscape.voronoi.VoronoiCalculator;
import com.worldscape.voronoi.VoronoiControlPoint;
import com.worldscape.voronoi.VoronoiDiagram;
import com.worldscape.voronoi.VoronoiSpatialIndex;
import java.util.List;

public class IncrementalVoronoiUpdater {
    private final VoronoiDiagram diagram;
    private final VoronoiSpatialIndex spatialIndex;
    private final VoronoiCalculator calculator;

    public IncrementalVoronoiUpdater(VoronoiDiagram diagram, VoronoiSpatialIndex spatialIndex) {
        this.diagram = diagram;
        this.spatialIndex = spatialIndex;
        this.calculator = new VoronoiCalculator();
    }

    public void addPoint(VoronoiControlPoint newPoint) {
        this.spatialIndex.insert(newPoint);
        this.recomputeLocalDiagram();
    }

    public void removePoint(VoronoiControlPoint point) {
        this.spatialIndex.remove(point);
        this.diagram.markCellRemoved(point.getId());
        this.recomputeLocalDiagram();
    }

    public void movePoint(VoronoiControlPoint point, int newX, int newZ) {
        this.spatialIndex.remove(point);
        point.setX(newX);
        point.setZ(newZ);
        this.spatialIndex.insert(point);
        this.recomputeLocalDiagram();
    }

    public void updatePoint(VoronoiControlPoint point) {
        this.recomputeLocalDiagram();
    }

    public void clearAll() {
        this.diagram.clear();
    }

    public void recomputeLocalDiagram() {
        List<VoronoiControlPoint> allPoints = this.spatialIndex.getAllPoints();
        if (allPoints.isEmpty()) {
            this.diagram.clear();
            return;
        }
        this.calculator.compute(allPoints, this.diagram);
    }

    public void invalidateCache() {
        this.recomputeLocalDiagram();
    }

    public VoronoiDiagram getDiagram() {
        return this.diagram;
    }
}

