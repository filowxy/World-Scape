package com.worldscape.voronoi;

import com.worldscape.voronoi.VoronoiCell;
import com.worldscape.voronoi.VoronoiControlPoint;
import com.worldscape.voronoi.VoronoiEdge;
import com.worldscape.voronoi.VoronoiVertex;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class VoronoiDiagram {
    private final Map<String, VoronoiCell> cells = new HashMap<String, VoronoiCell>();
    private final List<VoronoiEdge> edges = new ArrayList<VoronoiEdge>();
    private final List<VoronoiVertex> vertices = new ArrayList<VoronoiVertex>();
    private final Set<String> removedCellIds = new HashSet<String>();

    public void addCell(VoronoiCell cell) {
        this.cells.put(cell.getControlPointId(), cell);
    }

    public void addEdge(VoronoiEdge edge) {
        this.edges.add(edge);
    }

    public void addVertex(VoronoiVertex vertex) {
        this.vertices.add(vertex);
    }

    public VoronoiCell getCell(String pointId) {
        return this.cells.get(pointId);
    }

    public VoronoiCell getCell(VoronoiControlPoint point) {
        return this.cells.get(point.getId());
    }

    public Collection<VoronoiCell> getCells() {
        return Collections.unmodifiableCollection(this.cells.values());
    }

    public List<VoronoiEdge> getEdges() {
        return Collections.unmodifiableList(this.edges);
    }

    public List<VoronoiVertex> getVertices() {
        return Collections.unmodifiableList(this.vertices);
    }

    public void markCellRemoved(String pointId) {
        this.removedCellIds.add(pointId);
    }

    public boolean isCellRemoved(String pointId) {
        return this.removedCellIds.contains(pointId);
    }

    public void clear() {
        this.cells.clear();
        this.edges.clear();
        this.vertices.clear();
        this.removedCellIds.clear();
    }

    public int getCellCount() {
        return this.cells.size();
    }

    public int getEdgeCount() {
        return this.edges.size();
    }

    public int getVertexCount() {
        return this.vertices.size();
    }

    public void merge(VoronoiDiagram other) {
        this.cells.putAll(other.cells);
        this.edges.addAll(other.edges);
        this.vertices.addAll(other.vertices);
    }
}

