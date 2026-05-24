package com.worldscape.voronoi;

import com.worldscape.voronoi.VoronoiCell;
import com.worldscape.voronoi.VoronoiControlPoint;
import com.worldscape.voronoi.VoronoiDiagram;
import com.worldscape.voronoi.VoronoiEdge;
import com.worldscape.voronoi.VoronoiVertex;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.PriorityQueue;

public class VoronoiCalculator {
    private static final double EPSILON = 1.0E-10;
    private static final double INFINITE_RADIUS = 1.0E10;

    public void compute(List<VoronoiControlPoint> points, VoronoiDiagram diagram) {
        diagram.clear();
        if (points.isEmpty()) {
            return;
        }
        ArrayList<VoronoiSite> sites = new ArrayList<VoronoiSite>();
        for (VoronoiControlPoint point : points) {
            sites.add(new VoronoiSite(point.getX(), point.getZ(), point.getId()));
        }
        this.computeFortune(sites, diagram);
        for (VoronoiControlPoint point : points) {
            VoronoiCell cell = diagram.getCell(point.getId());
            if (cell == null) continue;
            cell.setControlPoint(point);
        }
    }

    private void computeFortune(List<VoronoiSite> sites, VoronoiDiagram diagram) {
        Collections.sort(sites, Comparator.comparingDouble(s -> s.y));
        PriorityQueue<Event> eventQueue = new PriorityQueue<Event>();
        BeachLine beachLine = new BeachLine();
        for (VoronoiSite site : sites) {
            eventQueue.add(new SiteEvent(site));
        }
        double sweepLine = 0.0;
        while (!eventQueue.isEmpty()) {
            Event event = (Event)eventQueue.poll();
            sweepLine = event.y;
            if (event instanceof SiteEvent) {
                this.handleSiteEvent((SiteEvent)event, beachLine, eventQueue, diagram);
                continue;
            }
            if (!(event instanceof CircleEvent)) continue;
            this.handleCircleEvent((CircleEvent)event, beachLine, eventQueue, diagram);
        }
        this.finishInfiniteEdges(beachLine, diagram, sites);
    }

    private void handleSiteEvent(SiteEvent event, BeachLine beachLine, PriorityQueue<Event> eventQueue, VoronoiDiagram diagram) {
        VoronoiSite site = event.site;
        if (beachLine.isEmpty()) {
            beachLine.insert(site);
            return;
        }
        BeachNode node = beachLine.findNodeAbove(site.x, site.y);
        if (node == null) {
            beachLine.insert(site);
            return;
        }
        VoronoiEdge edge = new VoronoiEdge(null, null);
        edge.setRightCell(diagram.getCell(node.site.id));
        VoronoiEdge newEdge = new VoronoiEdge(null, null);
        beachLine.split(node, site, edge, newEdge);
        edge.setLeftCell(diagram.getCell(site.id));
        newEdge.setRightCell(diagram.getCell(site.id));
        diagram.addEdge(edge);
        diagram.addEdge(newEdge);
        this.checkCircleEvents(node, beachLine, eventQueue);
        this.checkCircleEvents(node.prev, beachLine, eventQueue);
    }

    private void handleCircleEvent(CircleEvent event, BeachLine beachLine, PriorityQueue<Event> eventQueue, VoronoiDiagram diagram) {
        VoronoiEdge newEdge;
        VoronoiEdge rightEdge;
        BeachNode node = event.node;
        if (node == null || node.deleted) {
            return;
        }
        VoronoiVertex vertex = new VoronoiVertex(event.x, event.y);
        diagram.addVertex(vertex);
        VoronoiEdge leftEdge = node.edge;
        VoronoiEdge voronoiEdge = rightEdge = node.next != null ? node.next.edge : null;
        if (leftEdge != null) {
            if (leftEdge.getStart() == null) {
                leftEdge.setStart(vertex);
            } else {
                leftEdge.setEnd(vertex);
            }
        }
        if (rightEdge != null) {
            if (rightEdge.getStart() == null) {
                rightEdge.setStart(vertex);
            } else {
                rightEdge.setEnd(vertex);
            }
        }
        if (leftEdge != null) {
            leftEdge.getStart().addAdjacentEdge(leftEdge);
            if (leftEdge.getEnd() != null) {
                leftEdge.getEnd().addAdjacentEdge(leftEdge);
            }
        }
        if (rightEdge != null) {
            rightEdge.getStart().addAdjacentEdge(rightEdge);
            if (rightEdge.getEnd() != null) {
                rightEdge.getEnd().addAdjacentEdge(rightEdge);
            }
        }
        if ((newEdge = this.mergeEdges(leftEdge, rightEdge, node.prev, node.next, diagram)) != null) {
            diagram.addEdge(newEdge);
        }
        beachLine.remove(node);
        this.checkCircleEvents(node.prev, beachLine, eventQueue);
        this.checkCircleEvents(node.next, beachLine, eventQueue);
    }

    private VoronoiEdge mergeEdges(VoronoiEdge leftEdge, VoronoiEdge rightEdge, BeachNode prev, BeachNode next, VoronoiDiagram diagram) {
        VoronoiCell rightCell;
        if (prev == null || next == null) {
            return null;
        }
        VoronoiEdge merged = new VoronoiEdge(leftEdge != null ? leftEdge.getStart() : null, rightEdge != null ? rightEdge.getEnd() : null);
        VoronoiCell leftCell = prev.site != null ? diagram.getCell(prev.site.id) : null;
        VoronoiCell voronoiCell = rightCell = next.site != null ? diagram.getCell(next.site.id) : null;
        if (leftCell != null) {
            merged.setLeftCell(leftCell);
            leftCell.addEdge(merged);
        }
        if (rightCell != null) {
            merged.setRightCell(rightCell);
            rightCell.addEdge(merged);
        }
        return merged;
    }

    private void checkCircleEvents(BeachNode node, BeachLine beachLine, PriorityQueue<Event> eventQueue) {
        CircleEvent event;
        if (node == null || node.prev == null || node.next == null) {
            return;
        }
        BeachNode prev = node.prev;
        BeachNode next = node.next;
        double[] center = this.computeCircleCenter(prev.site, node.site, next.site);
        if (center == null) {
            return;
        }
        double x = center[0];
        double y = center[1];
        if (y >= node.site.y - 1.0E-10) {
            return;
        }
        node.circleEvent = event = new CircleEvent(y, x, node);
        eventQueue.add(event);
    }

    private double[] computeCircleCenter(VoronoiSite a, VoronoiSite b, VoronoiSite c) {
        double d = 2.0 * (a.x * (b.y - c.y) + b.x * (c.y - a.y) + c.x * (a.y - b.y));
        if (Math.abs(d) < 1.0E-10) {
            return null;
        }
        double x = ((a.x * a.x + a.y * a.y) * (b.y - c.y) + (b.x * b.x + b.y * b.y) * (c.y - a.y) + (c.x * c.x + c.y * c.y) * (a.y - b.y)) / d;
        double y = ((a.x * a.x + a.y * a.y) * (c.x - b.x) + (b.x * b.x + b.y * b.y) * (a.x - c.x) + (c.x * c.x + c.y * c.y) * (b.x - a.x)) / d;
        return new double[]{x, y};
    }

    private void finishInfiniteEdges(BeachLine beachLine, VoronoiDiagram diagram, List<VoronoiSite> sites) {
        double minX = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = Double.MIN_VALUE;
        for (VoronoiSite site : sites) {
            minX = Math.min(minX, site.x);
            maxX = Math.max(maxX, site.x);
            minY = Math.min(minY, site.y);
            maxY = Math.max(maxY, site.y);
        }
        double margin = (maxX - minX + maxY - minY) * 0.5;
        double clipMinX = minX - margin;
        double clipMaxX = maxX + margin;
        double clipMinY = minY - margin;
        double clipMaxY = maxY + margin;
        for (VoronoiEdge edge : diagram.getEdges()) {
            if (!edge.isInfinite()) continue;
            this.clipInfiniteEdge(edge, clipMinX, clipMaxX, clipMinY, clipMaxY);
        }
        this.buildCellsFromEdges(diagram);
    }

    private void clipInfiniteEdge(VoronoiEdge edge, double minX, double maxX, double minY, double maxY) {
        VoronoiVertex start = edge.getStart();
        VoronoiVertex end = edge.getEnd();
        if (start != null && end != null) {
            return;
        }
        VoronoiVertex finiteVertex = start != null ? start : end;
        VoronoiCell leftCell = edge.getLeftCell();
        VoronoiCell rightCell = edge.getRightCell();
        double dx = 0.0;
        double dy = 0.0;
        if (leftCell != null && rightCell != null && leftCell.getControlPoint() != null && rightCell.getControlPoint() != null) {
            VoronoiControlPoint lcp = leftCell.getControlPoint();
            VoronoiControlPoint rcp = rightCell.getControlPoint();
            double midX = (double)(lcp.getX() + rcp.getX()) / 2.0;
            double midY = (double)(lcp.getZ() + rcp.getZ()) / 2.0;
            dx = rcp.getZ() - lcp.getZ();
            double len = Math.sqrt(dx * dx + (dy = (double)(lcp.getX() - rcp.getX())) * dy);
            if (len > 1.0E-10) {
                dx /= len;
                dy /= len;
            }
        } else {
            dx = 1.0;
            dy = 0.0;
        }
        if (finiteVertex == null) {
            edge.setStart(new VoronoiVertex(minX, minY));
            edge.setEnd(new VoronoiVertex(maxX, maxY));
        } else if (start == null) {
            double sx = finiteVertex.getX() - dx * 1.0E10;
            double sy = finiteVertex.getY() - dy * 1.0E10;
            sx = Math.max(minX, Math.min(maxX, sx));
            sy = Math.max(minY, Math.min(maxY, sy));
            edge.setStart(new VoronoiVertex(sx, sy));
        } else {
            double ex = finiteVertex.getX() + dx * 1.0E10;
            double ey = finiteVertex.getY() + dy * 1.0E10;
            ex = Math.max(minX, Math.min(maxX, ex));
            ey = Math.max(minY, Math.min(maxY, ey));
            edge.setEnd(new VoronoiVertex(ex, ey));
        }
    }

    private void buildCellsFromEdges(VoronoiDiagram diagram) {
        HashMap<String, VoronoiCell> cells = new HashMap<String, VoronoiCell>();
        for (VoronoiEdge edge : diagram.getEdges()) {
            VoronoiCell leftCell = edge.getLeftCell();
            VoronoiCell rightCell = edge.getRightCell();
            if (leftCell != null) {
                if (!cells.containsKey(leftCell.getControlPointId())) {
                    cells.put(leftCell.getControlPointId(), leftCell);
                }
                leftCell.addEdge(edge);
                if (edge.getStart() != null) {
                    leftCell.addVertex(edge.getStart());
                }
                if (edge.getEnd() != null) {
                    leftCell.addVertex(edge.getEnd());
                }
            }
            if (rightCell == null) continue;
            if (!cells.containsKey(rightCell.getControlPointId())) {
                cells.put(rightCell.getControlPointId(), rightCell);
            }
            rightCell.addEdge(edge);
            if (edge.getStart() != null) {
                rightCell.addVertex(edge.getStart());
            }
            if (edge.getEnd() == null) continue;
            rightCell.addVertex(edge.getEnd());
        }
        for (VoronoiCell cell : cells.values()) {
            cell.sortVerticesCounterclockwise();
            diagram.addCell(cell);
        }
    }

    private static class VoronoiSite {
        final double x;
        final double y;
        final String id;

        VoronoiSite(double x, double y, String id) {
            this.x = x;
            this.y = y;
            this.id = id;
        }
    }

    private static class BeachLine {
        private BeachNode head;

        private BeachLine() {
        }

        boolean isEmpty() {
            return this.head == null;
        }

        void insert(VoronoiSite site) {
            BeachNode newNode = new BeachNode(site);
            if (this.head == null) {
                this.head = newNode;
            } else {
                BeachNode current = this.head;
                while (current.next != null) {
                    current = current.next;
                }
                current.next = newNode;
                newNode.prev = current;
            }
        }

        BeachNode findNodeAbove(double x, double y) {
            BeachNode current = this.head;
            while (current != null) {
                if (this.isPointAboveParabola(x, y, current.site, y)) {
                    return current;
                }
                current = current.next;
            }
            return null;
        }

        private boolean isPointAboveParabola(double px, double py, VoronoiSite focus, double sweepLine) {
            if (py >= sweepLine) {
                return false;
            }
            double dx = px - focus.x;
            double dy = py - focus.y;
            double parabolaY = dx * dx / (2.0 * (focus.y - sweepLine)) + (focus.y + sweepLine) / 2.0;
            return py <= parabolaY + 1.0E-10;
        }

        void split(BeachNode node, VoronoiSite newSite, VoronoiEdge leftEdge, VoronoiEdge rightEdge) {
            BeachNode newNode = new BeachNode(newSite);
            newNode.edge = rightEdge;
            BeachNode temp = node.next;
            node.next = newNode;
            newNode.prev = node;
            newNode.next = temp;
            if (temp != null) {
                temp.prev = newNode;
            }
            node.edge = leftEdge;
        }

        void remove(BeachNode node) {
            node.deleted = true;
            if (node.circleEvent != null) {
                node.circleEvent.node = null;
            }
            if (node.prev != null) {
                node.prev.next = node.next;
            } else {
                this.head = node.next;
            }
            if (node.next != null) {
                node.next.prev = node.prev;
            }
        }
    }

    private static class SiteEvent
    extends Event {
        final VoronoiSite site;

        SiteEvent(VoronoiSite site) {
            super(site.y);
            this.site = site;
        }
    }

    private static abstract class Event
    implements Comparable<Event> {
        final double y;

        Event(double y) {
            this.y = y;
        }

        @Override
        public int compareTo(Event other) {
            return Double.compare(this.y, other.y);
        }
    }

    private static class CircleEvent
    extends Event {
        final double x;
        BeachNode node;

        CircleEvent(double y, double x, BeachNode node) {
            super(y);
            this.x = x;
            this.node = node;
        }
    }

    private static class BeachNode {
        VoronoiSite site;
        VoronoiEdge edge;
        BeachNode prev;
        BeachNode next;
        CircleEvent circleEvent;
        boolean deleted;

        BeachNode(VoronoiSite site) {
            this.site = site;
        }
    }
}

