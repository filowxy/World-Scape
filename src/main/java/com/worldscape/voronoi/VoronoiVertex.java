package com.worldscape.voronoi;

import com.worldscape.voronoi.VoronoiEdge;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class VoronoiVertex {
    private static int idCounter = 0;
    private final String id;
    private final double x;
    private final double y;
    private final List<VoronoiEdge> adjacentEdges = new ArrayList<VoronoiEdge>();

    public VoronoiVertex(double x, double y) {
        this.id = "v_" + idCounter++;
        this.x = x;
        this.y = y;
    }

    public VoronoiVertex(String id, double x, double y) {
        this.id = id;
        this.x = x;
        this.y = y;
    }

    public String getId() {
        return this.id;
    }

    public double getX() {
        return this.x;
    }

    public double getY() {
        return this.y;
    }

    public void addAdjacentEdge(VoronoiEdge edge) {
        if (!this.adjacentEdges.contains(edge)) {
            this.adjacentEdges.add(edge);
        }
    }

    public List<VoronoiEdge> getAdjacentEdges() {
        return new ArrayList<VoronoiEdge>(this.adjacentEdges);
    }

    public double distanceTo(VoronoiVertex other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public double distanceTo(double ox, double oy) {
        double dx = this.x - ox;
        double dy = this.y - oy;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VoronoiVertex)) {
            return false;
        }
        VoronoiVertex vertex = (VoronoiVertex)o;
        return Double.compare(vertex.x, this.x) == 0 && Double.compare(vertex.y, this.y) == 0;
    }

    public int hashCode() {
        return Objects.hash(this.x, this.y);
    }

    public String toString() {
        return "VoronoiVertex{id='" + this.id + "', x=" + this.x + ", y=" + this.y + "}";
    }
}

