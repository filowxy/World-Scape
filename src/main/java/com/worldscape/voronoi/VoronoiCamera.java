package com.worldscape.voronoi;

import com.worldscape.voronoi.VoronoiViewMode;
import net.minecraft.util.Mth;

public class VoronoiCamera {
    private static final float MACRO_ZOOM = 0.25f;
    private static final float MICRO_ZOOM = 2.0f;
    private static final float MIN_ZOOM = 0.05f;
    private static final float MAX_ZOOM = 8.0f;
    private static final float TRANSITION_SPEED = 4.0f;
    private static final float MOVE_SPEED = 800.0f;
    private static final float ZOOM_SENSITIVITY = 0.15f;
    private double cameraX = 0.0;
    private double cameraZ = 0.0;
    private float zoomLevel = 0.25f;
    private VoronoiViewMode viewMode = VoronoiViewMode.MACRO;
    private boolean isTransitioning = false;
    private float transitionProgress = 1.0f;
    private float transitionStartZoom;
    private float transitionTargetZoom;
    private VoronoiViewMode transitionTargetMode;
    private boolean isPanning = false;
    private double panTargetX;
    private double panTargetZ;

    public void update(float deltaSeconds) {
        if (this.isTransitioning && this.transitionProgress < 1.0f) {
            this.transitionProgress = Math.min(1.0f, this.transitionProgress + deltaSeconds * 4.0f);
            float t = this.smoothstep(this.transitionProgress);
            this.zoomLevel = Mth.lerp((float)t, (float)this.transitionStartZoom, (float)this.transitionTargetZoom);
            if (this.transitionProgress >= 1.0f) {
                this.isTransitioning = false;
                this.viewMode = this.transitionTargetMode;
                this.zoomLevel = this.transitionTargetZoom;
            }
        }
        if (this.isPanning) {
            double dx = this.panTargetX - this.cameraX;
            double dz = this.panTargetZ - this.cameraZ;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist < 0.5) {
                this.cameraX = this.panTargetX;
                this.cameraZ = this.panTargetZ;
                this.isPanning = false;
            } else {
                float moveAmount = Math.min((float)dist, 800.0f * deltaSeconds);
                this.cameraX += dx / dist * (double)moveAmount;
                this.cameraZ += dz / dist * (double)moveAmount;
            }
        }
    }

    public void move(float deltaX, float deltaZ) {
        if (this.isPanning) {
            this.panTargetX += (double)deltaX;
            this.panTargetZ += (double)deltaZ;
        } else {
            this.cameraX += (double)deltaX;
            this.cameraZ += (double)deltaZ;
        }
    }

    public void setPosition(double x, double z) {
        this.cameraX = x;
        this.cameraZ = z;
        this.isPanning = false;
    }

    public void panTo(double x, double z) {
        this.panTargetX = x;
        this.panTargetZ = z;
        this.isPanning = true;
    }

    public void zoom(float factor) {
        if (!this.isTransitioning) {
            this.zoomLevel = Math.max(0.05f, Math.min(8.0f, this.zoomLevel * factor));
        }
    }

    public void scrollZoom(double scrollDelta) {
        if (scrollDelta > 0.0) {
            this.zoom(1.15f);
        } else {
            this.zoom(0.85f);
        }
    }

    public void switchViewMode(VoronoiViewMode mode) {
        if (mode == this.viewMode && !this.isTransitioning) {
            return;
        }
        if (this.isTransitioning) {
            this.transitionProgress = 1.0f;
            this.zoomLevel = this.transitionTargetZoom;
            this.viewMode = this.transitionTargetMode;
        }
        this.transitionStartZoom = this.zoomLevel;
        this.transitionTargetZoom = mode == VoronoiViewMode.MACRO ? 0.25f : 2.0f;
        this.transitionTargetMode = mode;
        this.transitionProgress = 0.0f;
        this.isTransitioning = true;
    }

    public void toggleViewMode() {
        this.switchViewMode(this.viewMode.next());
    }

    public double[] getViewportBounds(int screenWidth, int screenHeight) {
        double halfWidthBlocks = (double)screenWidth / 2.0 / (double)this.zoomLevel;
        double halfHeightBlocks = (double)screenHeight / 2.0 / (double)this.zoomLevel;
        return new double[]{this.cameraX - halfWidthBlocks, this.cameraZ - halfHeightBlocks, this.cameraX + halfWidthBlocks, this.cameraZ + halfHeightBlocks};
    }

    public double[] worldToScreen(double worldX, double worldZ, int screenWidth, int screenHeight) {
        double screenX = (worldX - this.cameraX) * (double)this.zoomLevel + (double)screenWidth / 2.0;
        double screenY = (worldZ - this.cameraZ) * (double)this.zoomLevel + (double)screenHeight / 2.0;
        return new double[]{screenX, screenY};
    }

    public double[] screenToWorld(double screenX, double screenY, int screenWidth, int screenHeight) {
        double worldX = (screenX - (double)screenWidth / 2.0) / (double)this.zoomLevel + this.cameraX;
        double worldZ = (screenY - (double)screenHeight / 2.0) / (double)this.zoomLevel + this.cameraZ;
        return new double[]{worldX, worldZ};
    }

    public boolean isVisible(double worldX, double worldZ, int screenWidth, int screenHeight, int margin) {
        double[] screen = this.worldToScreen(worldX, worldZ, screenWidth, screenHeight);
        return screen[0] >= (double)(-margin) && screen[0] <= (double)(screenWidth + margin) && screen[1] >= (double)(-margin) && screen[1] <= (double)(screenHeight + margin);
    }

    public double getCameraX() {
        return this.cameraX;
    }

    public double getCameraZ() {
        return this.cameraZ;
    }

    public float getZoomLevel() {
        return this.zoomLevel;
    }

    public VoronoiViewMode getViewMode() {
        return this.isTransitioning ? this.transitionTargetMode : this.viewMode;
    }

    public boolean isTransitioning() {
        return this.isTransitioning;
    }

    public float getTransitionProgress() {
        return this.transitionProgress;
    }

    public float getEffectiveZoom() {
        return this.zoomLevel;
    }

    private float smoothstep(float t) {
        return t * t * (3.0f - 2.0f * t);
    }

    public void reset() {
        this.cameraX = 0.0;
        this.cameraZ = 0.0;
        this.zoomLevel = 0.25f;
        this.viewMode = VoronoiViewMode.MACRO;
        this.isTransitioning = false;
        this.isPanning = false;
        this.transitionProgress = 1.0f;
    }

    public String toString() {
        return String.format("VoronoiCamera[ pos=(%.0f, %.0f) zoom=%.2f mode=%s transitioning=%s ]", this.cameraX, this.cameraZ, Float.valueOf(this.zoomLevel), this.viewMode.getId(), this.isTransitioning);
    }
}

