package com.worldscape.voronoi;

public class VoronoiControlPoint {
    private final String id;
    private int x;
    private int z;
    private int color;
    private float size;
    private float weight;
    private String label;
    private String terrainType;
    private boolean selected;
    private boolean visible;
    public static final int DEFAULT_COLOR = -11886849;
    private static final int SELECTED_COLOR = -43691;
    public static final float DEFAULT_SIZE = 8.0f;
    public static final float DEFAULT_WEIGHT = 1.0f;

    public VoronoiControlPoint(String id, int x, int z, int color, float size, float weight, String label, String terrainType) {
        this.id = id;
        this.x = x;
        this.z = z;
        this.color = color;
        this.size = Math.max(1.0f, size);
        this.weight = Math.max(0.0f, weight);
        this.label = label != null ? label : "";
        this.terrainType = terrainType != null ? terrainType : "";
        this.selected = false;
        this.visible = true;
    }

    public VoronoiControlPoint(String id, int x, int z, int color) {
        this(id, x, z, color, 8.0f, 1.0f, null, "");
    }

    public VoronoiControlPoint(int x, int z, String terrainType) {
        this("auto_" + x + "_" + z, x, z, -11886849, 8.0f, 1.0f, null, terrainType);
    }

    public VoronoiControlPoint(VoronoiControlPoint other) {
        this.id = other.id;
        this.x = other.x;
        this.z = other.z;
        this.color = other.color;
        this.size = other.size;
        this.weight = other.weight;
        this.label = other.label;
        this.terrainType = other.terrainType;
        this.selected = other.selected;
        this.visible = other.visible;
    }

    public String getId() {
        return this.id;
    }

    public int getX() {
        return this.x;
    }

    public int getZ() {
        return this.z;
    }

    public int getColor() {
        return this.selected ? -43691 : this.color;
    }

    public int getOriginalColor() {
        return this.color;
    }

    public float getSize() {
        return this.size;
    }

    public float getWeight() {
        return this.weight;
    }

    public String getLabel() {
        return this.label;
    }

    public String getTerrainType() {
        return this.terrainType;
    }

    public boolean isSelected() {
        return this.selected;
    }

    public void setX(int x) {
        this.x = x;
    }

    public void setZ(int z) {
        this.z = z;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public void setSize(float size) {
        this.size = Math.max(1.0f, size);
    }

    public void setWeight(float weight) {
        this.weight = Math.max(0.0f, weight);
    }

    public void setLabel(String label) {
        this.label = label != null ? label : "";
    }

    public void setTerrainType(String terrainType) {
        this.terrainType = terrainType != null ? terrainType : "";
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean isVisible() {
        return this.visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public long squaredDistanceTo(int px, int pz) {
        long dx = (long)this.x - (long)px;
        long dz = (long)this.z - (long)pz;
        return dx * dx + dz * dz;
    }

    public double distanceTo(int px, int pz) {
        return Math.sqrt(this.squaredDistanceTo(px, pz));
    }

    public boolean isWithinRadius(int px, int pz, long radiusSq) {
        return this.squaredDistanceTo(px, pz) <= radiusSq;
    }

    public void toggleSelection() {
        this.selected = !this.selected;
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof VoronoiControlPoint)) {
            return false;
        }
        VoronoiControlPoint other = (VoronoiControlPoint)obj;
        return this.id.equals(other.id);
    }

    public int hashCode() {
        return this.id.hashCode();
    }

    public String toString() {
        return String.format("VoronoiControlPoint[id=%s, x=%d, z=%d, type=%s, weight=%.2f]", this.id, this.x, this.z, this.terrainType, Float.valueOf(this.weight));
    }
}

