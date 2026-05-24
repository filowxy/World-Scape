package com.worldscape.generator;

public interface SurfaceAdapter {
    public boolean buildSurface(SurfaceBuildContext var1);

    public boolean isAvailable();

    public String getName();

    public static class SurfaceBuildContext {
        private final Object randomState;
        private final Object chunk;
        private final Object region;
        private final Object settings;
        private final int seaLevel;
        private final int[][] heightMap;
        private final boolean[][] riverMap;
        private final double[][] riverDepthMap;
        private final int minY;
        private final int maxY;
        private final int minBlockX;
        private final int minBlockZ;

        private SurfaceBuildContext(Builder builder) {
            this.randomState = builder.randomState;
            this.chunk = builder.chunk;
            this.region = builder.region;
            this.settings = builder.settings;
            this.seaLevel = builder.seaLevel;
            this.heightMap = builder.heightMap;
            this.riverMap = builder.riverMap;
            this.riverDepthMap = builder.riverDepthMap;
            this.minY = builder.minY;
            this.maxY = builder.maxY;
            this.minBlockX = builder.minBlockX;
            this.minBlockZ = builder.minBlockZ;
        }

        public Object getRandomState() {
            return this.randomState;
        }

        public Object getChunk() {
            return this.chunk;
        }

        public Object getRegion() {
            return this.region;
        }

        public Object getSettings() {
            return this.settings;
        }

        public int getSeaLevel() {
            return this.seaLevel;
        }

        public int[][] getHeightMap() {
            return this.heightMap;
        }

        public boolean[][] getRiverMap() {
            return this.riverMap;
        }

        public double[][] getRiverDepthMap() {
            return this.riverDepthMap;
        }

        public int getMinY() {
            return this.minY;
        }

        public int getMaxY() {
            return this.maxY;
        }

        public int getMinBlockX() {
            return this.minBlockX;
        }

        public int getMinBlockZ() {
            return this.minBlockZ;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private Object randomState;
            private Object chunk;
            private Object region;
            private Object settings;
            private int seaLevel;
            private int[][] heightMap;
            private boolean[][] riverMap;
            private double[][] riverDepthMap;
            private int minY;
            private int maxY;
            private int minBlockX;
            private int minBlockZ;

            public Builder randomState(Object val) {
                this.randomState = val;
                return this;
            }

            public Builder chunk(Object val) {
                this.chunk = val;
                return this;
            }

            public Builder region(Object val) {
                this.region = val;
                return this;
            }

            public Builder settings(Object val) {
                this.settings = val;
                return this;
            }

            public Builder seaLevel(int val) {
                this.seaLevel = val;
                return this;
            }

            public Builder heightMap(int[][] val) {
                this.heightMap = val;
                return this;
            }

            public Builder riverMap(boolean[][] val) {
                this.riverMap = val;
                return this;
            }

            public Builder riverDepthMap(double[][] val) {
                this.riverDepthMap = val;
                return this;
            }

            public Builder minY(int val) {
                this.minY = val;
                return this;
            }

            public Builder maxY(int val) {
                this.maxY = val;
                return this;
            }

            public Builder minBlockX(int val) {
                this.minBlockX = val;
                return this;
            }

            public Builder minBlockZ(int val) {
                this.minBlockZ = val;
                return this;
            }

            public SurfaceBuildContext build() {
                return new SurfaceBuildContext(this);
            }
        }
    }
}

