package com.worldscape.voronoi;

public enum VoronoiViewMode {
    MACRO("macro", "Macro Regions", "\u5b8f\u89c2\u533a\u57df"),
    MICRO("micro", "Micro Control Points", "\u5fae\u89c2\u63a7\u5236\u70b9");

    private final String id;
    private final String displayName;
    private final String displayNameCn;

    private VoronoiViewMode(String id, String displayName, String displayNameCn) {
        this.id = id;
        this.displayName = displayName;
        this.displayNameCn = displayNameCn;
    }

    public String getId() {
        return this.id;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public String getDisplayNameCn() {
        return this.displayNameCn;
    }

    public VoronoiViewMode next() {
        return VoronoiViewMode.values()[(this.ordinal() + 1) % VoronoiViewMode.values().length];
    }

    public static VoronoiViewMode fromId(String id) {
        if (id == null) {
            return MACRO;
        }
        for (VoronoiViewMode mode : VoronoiViewMode.values()) {
            if (!mode.id.equals(id.toLowerCase())) continue;
            return mode;
        }
        return MACRO;
    }
}

