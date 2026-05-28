/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.worldscape.WorldScape
 *  net.minecraft.client.DeltaTracker
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.Font
 *  net.minecraft.client.gui.GuiGraphics
 *  net.minecraft.client.gui.LayeredDraw$Layer
 *  net.minecraft.network.chat.Component
 *  net.minecraft.resources.ResourceLocation
 *  net.neoforged.api.distmarker.Dist
 *  net.neoforged.bus.api.SubscribeEvent
 *  net.neoforged.fml.common.EventBusSubscriber
 *  net.neoforged.neoforge.client.event.RegisterGuiLayersEvent
 */
package com.worldscape.voronoi;

import com.worldscape.WorldScape;
import com.worldscape.voronoi.TerrainFeatureCalculator;
import com.worldscape.voronoi.VoronoiCamera;
import com.worldscape.voronoi.VoronoiCell;
import com.worldscape.voronoi.VoronoiControlPoint;
import com.worldscape.voronoi.VoronoiControlPointManager;
import com.worldscape.voronoi.VoronoiDiagram;
import com.worldscape.voronoi.VoronoiEdge;
import com.worldscape.voronoi.VoronoiInputHandler;
import com.worldscape.voronoi.VoronoiVertex;
import com.worldscape.voronoi.WorldScapeVoronoiSystem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

@EventBusSubscriber(value={Dist.CLIENT}, modid="worldscape")
public class VoronoiOverlayRenderer {
    private static final ResourceLocation VORONOI_HUD_ID = ResourceLocation.fromNamespaceAndPath((String)"worldscape", (String)"voronoi_hud");
    private static final int COLOR_CELL_FILL_DEFAULT = 0x20000000;
    private static final int COLOR_CELL_FILL_SELECTED = 0x30FFFFFF;
    private static final int COLOR_BOUNDARY_DEFAULT = -2130706433;
    private static final int COLOR_BOUNDARY_SELECTED = -1;
    private static final int COLOR_POINT_DEFAULT = -1;
    private static final int COLOR_POINT_SELECTED = -256;
    private static final int COLOR_POINT_HOVER = -16711681;
    private static final int COLOR_TEXT_PRIMARY = -1;
    private static final int COLOR_TEXT_SECONDARY = -5592406;
    private static final int COLOR_BG_PANEL = -872415232;
    private static final int COLOR_BG_TOOLTIP = -535818224;
    private static final int COLOR_GRID_LINE = 0x20FFFFFF;
    private static final float LOD_SHOW_BOUNDARIES = 0.15f;
    private static final float LOD_SHOW_LABELS = 1.0f;
    private static final float LOD_SHOW_DETAIL = 2.0f;
    private static final float LOD_SHOW_GRID = 0.3f;
    private static final float LOD_POINT_SIZE_HIGH = 1.0f;
    private static final float LOD_POINT_SIZE_LOW = 0.5f;
    private static final float BASE_POINT_SIZE = 4.0f;
    private static boolean enabled = false;
    private static boolean showInfoPanel = true;
    private static VoronoiControlPoint hoveredPoint = null;
    private static long lastFrameTime = System.currentTimeMillis();
    private static float currentFPS = 60.0f;

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        LayeredDraw.Layer voronoiLayer = (guiGraphics, deltaTracker) -> {
            if (!enabled) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null || mc.options.hideGui) {
                return;
            }
            VoronoiOverlayRenderer.updateFPS();
            VoronoiOverlayRenderer.renderVoronoiOverlay(guiGraphics, mc, deltaTracker);
        };
        event.registerAboveAll(VORONOI_HUD_ID, voronoiLayer);
        WorldScape.LOGGER.info("Registered Voronoi HUD overlay layer");
    }

    public static void renderVoronoiOverlay(GuiGraphics guiGraphics, Minecraft mc, DeltaTracker deltaTracker) {
        long frameStart = System.nanoTime();
        VoronoiControlPointManager manager = WorldScapeVoronoiSystem.getControlPointManager();
        VoronoiCamera camera = WorldScapeVoronoiSystem.getCamera();
        if (manager == null || camera == null) {
            return;
        }
        int screenWidth = guiGraphics.guiWidth();
        int screenHeight = guiGraphics.guiHeight();
        float deltaSeconds = deltaTracker.getGameTimeDeltaPartialTick(false);
        camera.update(deltaSeconds);
        double[] viewport = camera.getViewportBounds(screenWidth, screenHeight);
        int viewMinX = (int)viewport[0];
        int viewMinZ = (int)viewport[1];
        int viewMaxX = (int)viewport[2];
        int viewMaxZ = (int)viewport[3];
        List<VoronoiControlPoint> visiblePoints = manager.queryViewport(viewMinX, viewMinZ, viewMaxX, viewMaxZ);
        float zoom = camera.getZoomLevel();
        int lodLevel = VoronoiOverlayRenderer.calculateLODLevel(zoom);
        VoronoiOverlayRenderer.renderCellFills(guiGraphics, camera, manager, screenWidth, screenHeight, lodLevel);
        if (lodLevel >= 1) {
            VoronoiOverlayRenderer.renderGridLines(guiGraphics, camera, screenWidth, screenHeight);
        }
        if (lodLevel >= 1) {
            VoronoiOverlayRenderer.renderCellBoundaries(guiGraphics, camera, manager, screenWidth, screenHeight);
        }
        VoronoiOverlayRenderer.renderControlPoints(guiGraphics, camera, visiblePoints, screenWidth, screenHeight, zoom, lodLevel);
        VoronoiOverlayRenderer.renderSelectionBox(guiGraphics);
        if (showInfoPanel) {
            VoronoiOverlayRenderer.renderInfoPanel(guiGraphics, mc, manager, camera, zoom, lodLevel, (float)(System.nanoTime() - frameStart) / 1000000.0f, currentFPS);
        }
        if (hoveredPoint != null && lodLevel >= 1) {
            VoronoiOverlayRenderer.renderTooltip(guiGraphics, mc, hoveredPoint, screenWidth, screenHeight);
        }
        hoveredPoint = null;
    }

    private static void renderCellFills(GuiGraphics guiGraphics, VoronoiCamera camera, VoronoiControlPointManager manager, int screenWidth, int screenHeight, int lodLevel) {
        VoronoiDiagram diagram = manager.getVoronoiDiagram();
        for (VoronoiCell cell : diagram.getCells()) {
            List<VoronoiVertex> vertices;
            VoronoiControlPoint point = cell.getControlPoint();
            if (point == null || !point.isVisible() || (vertices = cell.getVertices()).size() < 3) continue;
            ArrayList<int[]> screenPoints = new ArrayList<int[]>();
            boolean allOffScreen = true;
            for (VoronoiVertex vertex : vertices) {
                double[] screenPos = camera.worldToScreen(vertex.getX(), vertex.getY(), screenWidth, screenHeight);
                int sx = (int)screenPos[0];
                int sy = (int)screenPos[1];
                screenPoints.add(new int[]{sx, sy});
                if (sx < -50 || sx > screenWidth + 50 || sy < -50 || sy > screenHeight + 50) continue;
                allOffScreen = false;
            }
            if (allOffScreen) continue;
            int fillColor = VoronoiOverlayRenderer.computeCellFillColor(point, cell);
            VoronoiOverlayRenderer.renderPolygon(guiGraphics, screenPoints, fillColor);
        }
    }

    private static int computeCellFillColor(VoronoiControlPoint point, VoronoiCell cell) {
        if (point.isSelected()) {
            return 0x30FFFFFF;
        }
        return TerrainFeatureCalculator.computeTerrainColor(point, cell);
    }

    private static void renderPolygon(GuiGraphics guiGraphics, List<int[]> points, int color) {
        if (points.size() < 3) {
            return;
        }
        int n = points.size();
        int[] xs = new int[n];
        int[] ys = new int[n];
        for (int i = 0; i < n; ++i) {
            xs[i] = points.get(i)[0];
            ys[i] = points.get(i)[1];
        }
        VoronoiOverlayRenderer.fillPolygon(guiGraphics, xs, ys, n, color);
    }

    private static void fillPolygon(GuiGraphics guiGraphics, int[] xs, int[] ys, int n, int color) {
        if (n < 3) {
            return;
        }
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int i = 0; i < n; ++i) {
            minY = Math.min(minY, ys[i]);
            maxY = Math.max(maxY, ys[i]);
        }
        for (int y = minY; y <= maxY; ++y) {
            int i;
            ArrayList<Integer> intersections = new ArrayList<Integer>();
            for (i = 0; i < n; ++i) {
                int j = (i + 1) % n;
                int y1 = ys[i];
                int y2 = ys[j];
                if ((y1 > y || y2 < y) && (y2 > y || y1 < y) || y1 == y2) continue;
                double t = (double)(y - y1) / (double)(y2 - y1);
                int x = (int)((double)xs[i] + t * (double)(xs[j] - xs[i]));
                intersections.add(x);
            }
            Collections.sort(intersections);
            for (i = 0; i < intersections.size(); i += 2) {
                if (i + 1 >= intersections.size()) continue;
                int x1 = (Integer)intersections.get(i);
                int x2 = (Integer)intersections.get(i + 1);
                guiGraphics.hLine(x1, x2, y, color);
            }
        }
    }

    private static void renderGridLines(GuiGraphics guiGraphics, VoronoiCamera camera, int screenWidth, int screenHeight) {
        double[] screenEnd;
        double[] screenStart;
        float zoom = camera.getZoomLevel();
        double camX = camera.getCameraX();
        double camZ = camera.getCameraZ();
        int gridSpacingBlocks = zoom > 2.0f ? 64 : (zoom > 0.5f ? 256 : (zoom > 0.15f ? 512 : 1024));
        double[] viewport = camera.getViewportBounds(screenWidth, screenHeight);
        int minBlockX = Math.floorDiv((int)viewport[0], gridSpacingBlocks) * gridSpacingBlocks;
        int maxBlockX = Math.floorDiv((int)viewport[2], gridSpacingBlocks) * gridSpacingBlocks + gridSpacingBlocks;
        int minBlockZ = Math.floorDiv((int)viewport[1], gridSpacingBlocks) * gridSpacingBlocks;
        int maxBlockZ = Math.floorDiv((int)viewport[3], gridSpacingBlocks) * gridSpacingBlocks + gridSpacingBlocks;
        for (int x = minBlockX; x <= maxBlockX; x += gridSpacingBlocks) {
            screenStart = camera.worldToScreen(x, (int)viewport[1], screenWidth, screenHeight);
            screenEnd = camera.worldToScreen(x, (int)viewport[3], screenWidth, screenHeight);
            if (!(screenStart[0] >= 0.0) || !(screenStart[0] <= (double)screenWidth)) continue;
            guiGraphics.vLine((int)screenStart[0], (int)screenStart[1], (int)screenEnd[1], 0x20FFFFFF);
        }
        for (int z = minBlockZ; z <= maxBlockZ; z += gridSpacingBlocks) {
            screenStart = camera.worldToScreen((int)viewport[0], z, screenWidth, screenHeight);
            screenEnd = camera.worldToScreen((int)viewport[2], z, screenWidth, screenHeight);
            if (!(screenStart[1] >= 0.0) || !(screenStart[1] <= (double)screenHeight)) continue;
            guiGraphics.hLine((int)screenStart[0], (int)screenEnd[0], (int)screenStart[1], 0x20FFFFFF);
        }
    }

    private static void renderCellBoundaries(GuiGraphics guiGraphics, VoronoiCamera camera, VoronoiControlPointManager manager, int screenWidth, int screenHeight) {
        VoronoiDiagram diagram = manager.getVoronoiDiagram();
        HashSet<VoronoiEdge> renderedEdges = new HashSet<VoronoiEdge>();
        for (VoronoiEdge edge : diagram.getEdges()) {
            double[] p2Screen;
            double[] p1Screen;
            if (!edge.isVisible() || renderedEdges.contains(edge)) continue;
            VoronoiVertex start = edge.getStart();
            VoronoiVertex end = edge.getEnd();
            if (start == null || end == null || !VoronoiOverlayRenderer.isLineInViewport(p1Screen = camera.worldToScreen(start.getX(), start.getY(), screenWidth, screenHeight), p2Screen = camera.worldToScreen(end.getX(), end.getY(), screenWidth, screenHeight), screenWidth, screenHeight)) continue;
            int lineColor = -2130706433;
            VoronoiCell leftCell = edge.getLeftCell();
            VoronoiCell rightCell = edge.getRightCell();
            if (leftCell != null && leftCell.getControlPoint() != null && leftCell.getControlPoint().isSelected() || rightCell != null && rightCell.getControlPoint() != null && rightCell.getControlPoint().isSelected()) {
                lineColor = -1;
            }
            VoronoiOverlayRenderer.drawLine(guiGraphics, (int)p1Screen[0], (int)p1Screen[1], (int)p2Screen[0], (int)p2Screen[1], lineColor);
            renderedEdges.add(edge);
        }
    }

    private static void renderControlPoints(GuiGraphics guiGraphics, VoronoiCamera camera, List<VoronoiControlPoint> points, int screenWidth, int screenHeight, float zoom, int lodLevel) {
        for (VoronoiControlPoint point : points) {
            if (!point.isVisible()) continue;
            double[] screenPos = camera.worldToScreen(point.getX(), point.getZ(), screenWidth, screenHeight);
            int sx = (int)screenPos[0];
            int sy = (int)screenPos[1];
            if (sx < -20 || sx > screenWidth + 20 || sy < -20 || sy > screenHeight + 20) continue;
            float pointSize = 4.0f * point.getSize();
            if (lodLevel <= 1) {
                pointSize *= 0.5f;
            }
            int pointColor = hoveredPoint != null && hoveredPoint.getId().equals(point.getId()) ? -16711681 : (point.isSelected() ? -256 : point.getColor());
            int halfSize = (int)(pointSize / 2.0f);
            guiGraphics.fill(sx - halfSize, sy - halfSize, sx + halfSize, sy + halfSize, pointColor);
            if (point.isSelected()) {
                guiGraphics.fill(sx - halfSize - 1, sy - halfSize - 1, sx + halfSize + 1, sy - halfSize, -1);
                guiGraphics.fill(sx - halfSize - 1, sy + halfSize, sx + halfSize + 1, sy + halfSize + 1, -1);
                guiGraphics.fill(sx - halfSize - 1, sy - halfSize, sx - halfSize, sy + halfSize, -1);
                guiGraphics.fill(sx + halfSize, sy - halfSize, sx + halfSize + 1, sy + halfSize, -1);
            }
            if (lodLevel < 2 || point.getLabel() == null || point.getLabel().isEmpty()) continue;
            guiGraphics.drawString(Minecraft.getInstance().font, point.getLabel(), sx + halfSize + 3, sy - 4, -1, true);
        }
    }

    private static void renderSelectionBox(GuiGraphics guiGraphics) {
        VoronoiInputHandler.SelectionBox selBox = VoronoiInputHandler.getCurrentSelectionBox();
        if (selBox == null || !selBox.isActive()) {
            return;
        }
        int x = Math.min(selBox.startX, selBox.endX);
        int y = Math.min(selBox.startY, selBox.endY);
        int w = Math.abs(selBox.endX - selBox.startX);
        int h = Math.abs(selBox.endY - selBox.startY);
        guiGraphics.fill(x, y, x + w, y + h, 541761279);
        guiGraphics.hLine(x, x + w, y, -11886849);
        guiGraphics.hLine(x, x + w, y + h, -11886849);
        guiGraphics.vLine(x, y, y + h, -11886849);
        guiGraphics.vLine(x + w, y, y + h, -11886849);
    }

    private static void renderInfoPanel(GuiGraphics guiGraphics, Minecraft mc, VoronoiControlPointManager manager, VoronoiCamera camera, float zoom, int lodLevel, float renderTimeMs, float fps) {
        Font font = mc.font;
        int panelX = 4;
        int panelY = 4;
        Objects.requireNonNull(font);
        int lineHeight = 11;
        ArrayList<String> lines = new ArrayList<String>();
        lines.add("\u00a7b\u00a7lWorld Scape - Voronoi Visualizer");
        lines.add("");
        lines.add(String.format("\u00a77View Mode: \u00a7f%s", camera.getViewMode().getDisplayName()));
        lines.add(String.format("\u00a77Zoom: \u00a7f%.2fx", Float.valueOf(zoom)));
        lines.add(String.format("\u00a77Camera: \u00a7f(%.0f, %.0f)", camera.getCameraX(), camera.getCameraZ()));
        lines.add(String.format("\u00a77Control Points: \u00a7f%d", manager.getPointCount()));
        lines.add(String.format("\u00a77Selected: \u00a7f%d", manager.getSelectedIds().size()));
        lines.add("");
        lines.add(String.format("\u00a77FPS: \u00a7f%.0f", Float.valueOf(fps)));
        lines.add(String.format("\u00a77Render Time: \u00a7f%.1fms", Float.valueOf(renderTimeMs)));
        lines.add(String.format("\u00a77LOD Level: \u00a7f%d", lodLevel));
        int panelWidth = 0;
        for (String line : lines) {
            int w = font.width(Component.literal((String)line).getString());
            panelWidth = Math.max(panelWidth, w);
        }
        int panelHeight = lines.size() * lineHeight + 6;
        guiGraphics.fill(panelX - 2, panelY - 2, panelX + (panelWidth += 8), panelY + panelHeight, -872415232);
        for (int i = 0; i < lines.size(); ++i) {
            guiGraphics.drawString(font, (String)lines.get(i), panelX + 2, panelY + i * lineHeight, -1, false);
        }
        int hintY = panelY + panelHeight + 8;
        List<String> hints = List.of("\u00a77[TAB] \u00a7f\u5207\u6362\u89c6\u56fe", "\u00a77[WASD] \u00a7f\u79fb\u52a8\u76f8\u673a", "\u00a77[\u6eda\u8f6e] \u00a7f\u7f29\u653e", "\u00a77[\u5de6\u952e] \u00a7f\u9009\u62e9/\u6dfb\u52a0\u70b9", "\u00a77[\u53f3\u952e] \u00a7f\u53d6\u6d88\u9009\u62e9", "\u00a77[Del] \u00a7f\u5220\u9664\u9009\u4e2d", "\u00a77[H] \u00a7f\u5207\u6362\u4fe1\u606f\u9762\u677f");
        int hintWidth = 140;
        int n = hints.size();
        Objects.requireNonNull(font);
        int hintHeight = n * 10 + 6;
        guiGraphics.fill(panelX - 2, hintY - 2, panelX + hintWidth, hintY + hintHeight, -872415232);
        for (int i = 0; i < hints.size(); ++i) {
            Objects.requireNonNull(font);
            guiGraphics.drawString(font, hints.get(i), panelX + 2, hintY + i * 10, -5592406, false);
        }
    }

    private static void renderTooltip(GuiGraphics guiGraphics, Minecraft mc, VoronoiControlPoint point, int screenWidth, int screenHeight) {
        double[] dArray;
        Font font = mc.font;
        ArrayList<String> lines = new ArrayList<String>();
        lines.add(String.format("\u00a7e\u00a7lPoint: %s", point.getId().substring(0, Math.min(20, point.getId().length()))));
        lines.add(String.format("\u00a77Position: \u00a7f(%d, %d)", point.getX(), point.getZ()));
        lines.add(String.format("\u00a77Terrain: \u00a7f%s", point.getTerrainType() != null && !point.getTerrainType().isEmpty() ? point.getTerrainType() : "Custom"));
        lines.add(String.format("\u00a77Color: \u00a7f#%06X", point.getColor() & 0xFFFFFF));
        lines.add(String.format("\u00a77Size: \u00a7f%.1f", Float.valueOf(point.getSize())));
        lines.add(String.format("\u00a77Weight: \u00a7f%.2f", Float.valueOf(point.getWeight())));
        if (point.getLabel() != null) {
            lines.add(String.format("\u00a77Label: \u00a7f%s", point.getLabel()));
        }
        if (mc.level != null) {
            dArray = WorldScapeVoronoiSystem.getCamera().worldToScreen(point.getX(), point.getZ(), screenWidth, screenHeight);
        } else {
            double[] dArray2 = new double[2];
            dArray2[0] = (double)screenWidth / 2.0;
            dArray = dArray2;
            dArray2[1] = (double)screenHeight / 2.0;
        }
        double[] screenPos = dArray;
        int tooltipX = (int)screenPos[0] + 15;
        int tooltipY = (int)screenPos[1] - 10;
        int tooltipWidth = 0;
        for (String line : lines) {
            tooltipWidth = Math.max(tooltipWidth, font.width(Component.literal((String)line).getString()));
        }
        int n = lines.size();
        Objects.requireNonNull(font);
        int tooltipHeight = n * 10 + 4;
        if (tooltipX + (tooltipWidth += 6) > screenWidth) {
            tooltipX = (int)screenPos[0] - tooltipWidth - 15;
        }
        if (tooltipY + tooltipHeight > screenHeight) {
            tooltipY = screenHeight - tooltipHeight - 4;
        }
        if (tooltipY < 4) {
            tooltipY = 4;
        }
        guiGraphics.fill(tooltipX, tooltipY, tooltipX + tooltipWidth, tooltipY + tooltipHeight, -535818224);
        guiGraphics.fill(tooltipX, tooltipY, tooltipX + tooltipWidth, tooltipY + 1, -11886849);
        for (int i = 0; i < lines.size(); ++i) {
            String string = (String)lines.get(i);
            Objects.requireNonNull(font);
            guiGraphics.drawString(font, string, tooltipX + 3, tooltipY + 2 + i * 10, -1, false);
        }
    }

    private static void drawLine(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int color) {
        int dy;
        int dx = Math.abs(x2 - x1);
        if (dx > (dy = Math.abs(y2 - y1))) {
            int stepX = x2 > x1 ? 1 : -1;
            double error = 0.0;
            double errorDelta = (double)dy / (double)dx;
            int y = y1;
            for (int x = x1; x != x2; x += stepX) {
                guiGraphics.hLine(x, x, y, color);
                if (!((error += errorDelta) >= 0.5)) continue;
                y += y2 > y1 ? 1 : -1;
                error -= 1.0;
            }
        } else {
            int stepY = y2 > y1 ? 1 : -1;
            double error = 0.0;
            double errorDelta = (double)dx / (double)dy;
            int x = x1;
            for (int y = y1; y != y2; y += stepY) {
                guiGraphics.vLine(x, y, y, color);
                if (!((error += errorDelta) >= 0.5)) continue;
                x += x2 > x1 ? 1 : -1;
                error -= 1.0;
            }
        }
    }

    private static boolean isLineInViewport(double[] p1, double[] p2, int screenWidth, int screenHeight) {
        int margin = 10;
        boolean p1OnScreen = p1[0] >= (double)(-margin) && p1[0] <= (double)(screenWidth + margin) && p1[1] >= (double)(-margin) && p1[1] <= (double)(screenHeight + margin);
        boolean p2OnScreen = p2[0] >= (double)(-margin) && p2[0] <= (double)(screenWidth + margin) && p2[1] >= (double)(-margin) && p2[1] <= (double)(screenHeight + margin);
        return p1OnScreen || p2OnScreen;
    }

    private static int calculateLODLevel(float zoom) {
        if (zoom >= 2.0f) {
            return 2;
        }
        if (zoom >= 0.15f) {
            return 1;
        }
        return 0;
    }

    private static void updateFPS() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastFrameTime;
        lastFrameTime = now;
        if (elapsed > 0L) {
            float instantFPS = 1000.0f / (float)elapsed;
            currentFPS = currentFPS * 0.9f + instantFPS * 0.1f;
        }
    }

    public static void setEnabled(boolean enabled) {
        VoronoiOverlayRenderer.enabled = enabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void toggleInfoPanel() {
        showInfoPanel = !showInfoPanel;
    }

    public static void setHoveredPoint(VoronoiControlPoint point) {
        hoveredPoint = point;
    }

    public static VoronoiControlPoint getHoveredPoint() {
        return hoveredPoint;
    }
}

