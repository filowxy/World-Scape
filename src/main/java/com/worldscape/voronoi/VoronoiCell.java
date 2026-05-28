/*
 * Decompiled with CFR 0.152.
 */
package com.worldscape.voronoi;

import com.worldscape.voronoi.VoronoiControlPoint;
import com.worldscape.voronoi.VoronoiEdge;
import com.worldscape.voronoi.VoronoiVertex;
import java.util.ArrayList;
import java.util.List;

public class VoronoiCell {
    private final String controlPointId;
    private VoronoiControlPoint controlPoint;
    private final List<VoronoiVertex> vertices = new ArrayList<VoronoiVertex>();
    private final List<VoronoiEdge> edges = new ArrayList<VoronoiEdge>();

    public VoronoiCell(String controlPointId) {
        this.controlPointId = controlPointId;
    }

    public VoronoiCell(String controlPointId, VoronoiControlPoint controlPoint) {
        this.controlPointId = controlPointId;
        this.controlPoint = controlPoint;
    }

    public String getControlPointId() {
        return this.controlPointId;
    }

    public VoronoiControlPoint getControlPoint() {
        return this.controlPoint;
    }

    public void setControlPoint(VoronoiControlPoint controlPoint) {
        this.controlPoint = controlPoint;
    }

    public void addVertex(VoronoiVertex vertex) {
        if (!this.vertices.contains(vertex)) {
            this.vertices.add(vertex);
        }
    }

    public void addEdge(VoronoiEdge edge) {
        if (!this.edges.contains(edge)) {
            this.edges.add(edge);
        }
    }

    public List<VoronoiVertex> getVertices() {
        return new ArrayList<VoronoiVertex>(this.vertices);
    }

    public List<VoronoiEdge> getEdges() {
        return new ArrayList<VoronoiEdge>(this.edges);
    }

    public int getVertexCount() {
        return this.vertices.size();
    }

    public int getEdgeCount() {
        return this.edges.size();
    }

    public double getArea() {
        if (this.vertices.size() < 3) {
            return 0.0;
        }
        double area = 0.0;
        int n = this.vertices.size();
        for (int i = 0; i < n; ++i) {
            int j = (i + 1) % n;
            area += this.vertices.get(i).getX() * this.vertices.get(j).getY();
            area -= this.vertices.get(j).getX() * this.vertices.get(i).getY();
        }
        return Math.abs(area) / 2.0;
    }

    public double getPerimeter() {
        double perimeter = 0.0;
        int n = this.vertices.size();
        for (int i = 0; i < n; ++i) {
            int j = (i + 1) % n;
            perimeter += this.vertices.get(i).distanceTo(this.vertices.get(j));
        }
        return perimeter;
    }

    public double getAverageElevation() {
        if (this.controlPoint == null) {
            return 0.0;
        }
        return this.controlPoint.getWeight() * 100.0f;
    }

    public double getAverageSlope() {
        if (this.vertices.size() < 3) {
            return 0.0;
        }
        return Math.min(1.0, this.getPerimeter() / Math.sqrt(this.getArea()) * 0.1);
    }

    public void sortVerticesCounterclockwise() {
        if (this.vertices.size() < 3) {
            return;
        }
        double centerX = 0.0;
        double centerY = 0.0;
        for (VoronoiVertex v : this.vertices) {
            centerX += v.getX();
            centerY += v.getY();
        }
        double cx = centerX /= (double)this.vertices.size();
        double cy = centerY /= (double)this.vertices.size();
        this.vertices.sort((v1, v2) -> {
            double angle1 = Math.atan2(v1.getY() - cy, v1.getX() - cx);
            double angle2 = Math.atan2(v2.getY() - cy, v2.getX() - cx);
            return Double.compare(angle1, angle2);
        });
    }

    public boolean containsPoint(double x, double y) {
        if (this.vertices.size() < 3) {
            return false;
        }
        int n = this.vertices.size();
        boolean inside = false;
        int i = 0;
        int j = n - 1;
        while (i < n) {
            double d;
            double xi = this.vertices.get(i).getX();
            double yi = this.vertices.get(i).getY();
            double xj = this.vertices.get(j).getX();
            double yj = this.vertices.get(j).getY();
            if (yi > y != d > y && x < (xj - xi) * (y - yi) / (yj - yi) + xi) {
                inside = !inside;
            }
            j = i++;
        }
        return inside;
    }

    public String toString() {
        return "VoronoiCell{controlPointId='" + this.controlPointId + "', vertices=" + this.vertices.size() + ", area=" + this.getArea() + "}";
    }
}

