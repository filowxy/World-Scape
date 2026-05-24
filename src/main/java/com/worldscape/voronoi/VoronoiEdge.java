package com.worldscape.voronoi;

import com.worldscape.voronoi.VoronoiCell;
import com.worldscape.voronoi.VoronoiVertex;
import java.util.Objects;

public class VoronoiEdge {
    private static int idCounter = 0;
    private final String id;
    private VoronoiVertex start;
    private VoronoiVertex end;
    private VoronoiCell leftCell;
    private VoronoiCell rightCell;
    private boolean visible = true;

    public VoronoiEdge(VoronoiVertex start, VoronoiVertex end) {
        this.id = "e_" + idCounter++;
        this.start = start;
        this.end = end;
    }

    public VoronoiEdge(String id, VoronoiVertex start, VoronoiVertex end) {
        this.id = id;
        this.start = start;
        this.end = end;
    }

    public String getId() {
        return this.id;
    }

    public VoronoiVertex getStart() {
        return this.start;
    }

    public void setStart(VoronoiVertex start) {
        this.start = start;
    }

    public VoronoiVertex getEnd() {
        return this.end;
    }

    public void setEnd(VoronoiVertex end) {
        this.end = end;
    }

    public VoronoiCell getLeftCell() {
        return this.leftCell;
    }

    public void setLeftCell(VoronoiCell leftCell) {
        this.leftCell = leftCell;
    }

    public VoronoiCell getRightCell() {
        return this.rightCell;
    }

    public void setRightCell(VoronoiCell rightCell) {
        this.rightCell = rightCell;
    }

    public boolean isVisible() {
        return this.visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public double getLength() {
        if (this.start == null || this.end == null) {
            return Double.MAX_VALUE;
        }
        return this.start.distanceTo(this.end);
    }

    public double getMidX() {
        if (this.start == null || this.end == null) {
            return 0.0;
        }
        return (this.start.getX() + this.end.getX()) / 2.0;
    }

    public double getMidY() {
        if (this.start == null || this.end == null) {
            return 0.0;
        }
        return (this.start.getY() + this.end.getY()) / 2.0;
    }

    public boolean isInfinite() {
        return this.start == null || this.end == null;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VoronoiEdge)) {
            return false;
        }
        VoronoiEdge edge = (VoronoiEdge)o;
        return Objects.equals(this.start, edge.start) && Objects.equals(this.end, edge.end);
    }

    public int hashCode() {
        return Objects.hash(this.start, this.end);
    }

    public String toString() {
        return "VoronoiEdge{id='" + this.id + "', start=" + (this.start != null ? this.start.getId() : "null") + ", end=" + (this.end != null ? this.end.getId() : "null") + "}";
    }
}

