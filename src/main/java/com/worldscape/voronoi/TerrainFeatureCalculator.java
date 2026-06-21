package com.worldscape.voronoi;

import com.worldscape.terrain.TerrainContext;
import com.worldscape.voronoi.TerrainFeatures;
import com.worldscape.voronoi.VoronoiCell;
import com.worldscape.voronoi.VoronoiControlPoint;
import com.worldscape.voronoi.VoronoiVertex;
import java.util.List;

public class TerrainFeatureCalculator {
    public static TerrainFeatures computeFeatures(VoronoiCell cell, TerrainContext context) {
        double averageElevation = TerrainFeatureCalculator.computeAverageElevation(cell, context);
        double slope = TerrainFeatureCalculator.computeSlope(cell, context);
        double aspect = TerrainFeatureCalculator.computeAspect(cell, context);
        double roughness = TerrainFeatureCalculator.computeRoughness(cell, context);
        return new TerrainFeatures(averageElevation, slope, aspect, roughness);
    }

    private static double computeAverageElevation(VoronoiCell cell, TerrainContext context) {
        if (cell.getControlPoint() == null) {
            return 0.0;
        }
        double baseElevation = cell.getControlPoint().getWeight() * 100.0f;
        if (context != null) {
            baseElevation += context.getN1() * 50.0 + context.getN2() * 25.0 + context.getN3() * 10.0;
        }
        return Math.max(0.0, baseElevation);
    }

    private static double computeSlope(VoronoiCell cell, TerrainContext context) {
        if (cell.getVertexCount() < 3) {
            return 0.0;
        }
        double perimeter = cell.getPerimeter();
        double area = cell.getArea();
        if (area < 1.0) {
            return 0.0;
        }
        double shapeFactor = perimeter / (2.0 * Math.sqrt(Math.PI * area));
        double baseSlope = Math.min(1.0, shapeFactor * 0.5);
        if (context != null) {
            baseSlope += Math.abs(context.getN3()) * 0.3;
        }
        return Math.min(1.0, baseSlope);
    }

    private static double computeAspect(VoronoiCell cell, TerrainContext context) {
        double dx;
        if (cell.getVertexCount() < 3) {
            return 0.0;
        }
        VoronoiControlPoint cp = cell.getControlPoint();
        if (cp == null) {
            return 0.0;
        }
        double centerX = cp.getX();
        double centerZ = cp.getZ();
        double avgX = 0.0;
        double avgZ = 0.0;
        List<VoronoiVertex> vertices = cell.getVertices();
        for (VoronoiVertex v : vertices) {
            avgX += v.getX();
            avgZ += v.getY();
        }
        avgX /= (double)vertices.size();
        double dz = (avgZ /= (double)vertices.size()) - centerZ;
        double aspect = Math.atan2(dz, dx = avgX - centerX);
        if (aspect < 0.0) {
            aspect += Math.PI * 2;
        }
        return aspect;
    }

    private static double computeRoughness(VoronoiCell cell, TerrainContext context) {
        if (cell.getVertexCount() < 3) {
            return 0.0;
        }
        double area = cell.getArea();
        double perimeter = cell.getPerimeter();
        double compactness = Math.PI * 4 * area / (perimeter * perimeter);
        double roughness = 1.0 - Math.min(1.0, compactness * 2.0);
        if (context != null) {
            roughness += context.getN2() * 0.2;
        }
        return Math.min(1.0, Math.max(0.0, roughness));
    }

    public static int computeTerrainColor(VoronoiControlPoint point, VoronoiCell cell) {
        return TerrainFeatureCalculator.computeTerrainColor(point, cell, null);
    }

    public static int computeTerrainColor(VoronoiControlPoint point, VoronoiCell cell, TerrainContext context) {
        TerrainFeatures features = TerrainFeatureCalculator.computeFeatures(cell, context);
        return TerrainFeatureCalculator.computeColorFromFeatures(point, features);
    }

    private static int computeColorFromFeatures(VoronoiControlPoint point, TerrainFeatures features) {
        int baseColor = point.getOriginalColor();
        int r = baseColor >> 16 & 0xFF;
        int g = baseColor >> 8 & 0xFF;
        int b = baseColor & 0xFF;
        double elevationFactor = TerrainFeatureCalculator.normalizeElevation(features.getElevation());
        double slopeFactor = features.getSlope();
        r = (int)((double)r * (0.7 + elevationFactor * 0.3));
        g = (int)((double)g * (0.7 + elevationFactor * 0.3));
        b = (int)((double)b * (0.7 + elevationFactor * 0.3));
        double shadow = 1.0 - slopeFactor * 0.3;
        r = (int)((double)r * shadow);
        g = (int)((double)g * shadow);
        b = (int)((double)b * shadow);
        r = Math.max(0, Math.min(255, r));
        g = Math.max(0, Math.min(255, g));
        b = Math.max(0, Math.min(255, b));
        return 0x40000000 | r << 16 | g << 8 | b;
    }

    private static double normalizeElevation(double elevation) {
        return Math.min(1.0, elevation / 200.0);
    }
}

