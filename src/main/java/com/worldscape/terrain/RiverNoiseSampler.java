package com.worldscape.terrain;

import com.worldscape.terrain.RiverInfo;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public class RiverNoiseSampler {
    private final NormalNoise pathNoise;
    private final NormalNoise widthNoise;
    private final NormalNoise depthNoise;
    private final NormalNoise flowNoiseX;
    private final NormalNoise flowNoiseZ;
    private static final double PATH_THRESHOLD = 0.15;
    private static final double BASE_WIDTH = 8.0;
    private static final double MAX_WIDTH = 24.0;
    private static final double MAX_DEPTH = 12.0;
    private static final double GRADIENT_STEEP_THRESHOLD = 0.5;
    private static final double GRADIENT_FLAT_THRESHOLD = 0.2;
    private static final double GRADIENT_WIDTH_BASE_WEIGHT = 0.2;
    private static final double GRADIENT_WIDTH_TARGET_WEIGHT = 0.8;

    public RiverNoiseSampler(long seed) {
        this.pathNoise = NormalNoise.create((RandomSource)RandomSource.create((long)(seed + 1000L)), (int)-4, (double[])new double[]{1.0});
        this.widthNoise = NormalNoise.create((RandomSource)RandomSource.create((long)(seed + 1001L)), (int)-3, (double[])new double[]{1.0});
        this.depthNoise = NormalNoise.create((RandomSource)RandomSource.create((long)(seed + 1002L)), (int)-2, (double[])new double[]{1.0});
        this.flowNoiseX = NormalNoise.create((RandomSource)RandomSource.create((long)(seed + 1003L)), (int)-3, (double[])new double[]{1.0});
        this.flowNoiseZ = NormalNoise.create((RandomSource)RandomSource.create((long)(seed + 1004L)), (int)-3, (double[])new double[]{1.0});
    }

    public RiverInfo sampleRiverInfo(int worldX, int worldZ) {
        double pathValue = this.pathNoise.getValue((double)worldX / 64.0, (double)worldZ / 64.0, 0.0);
        boolean isRiver = Math.abs(pathValue) < 0.15;
        boolean bl = isRiver;
        if (!isRiver) {
            return RiverInfo.EMPTY;
        }
        double distFromCenter = Math.abs(pathValue) / 0.15;
        double distSq = distFromCenter * distFromCenter;
        double widthValue = this.widthNoise.getValue((double)worldX / 128.0, (double)worldZ / 128.0, 0.0);
        double width = 8.0 + (widthValue + 1.0) * 0.5 * 16.0;
        double pathIntensity = 1.0 - distFromCenter;
        double depthValue = this.depthNoise.getValue((double)worldX / 32.0, (double)worldZ / 32.0, 0.0);
        double depth = pathIntensity * 12.0 * (0.5 + 0.5 * depthValue);
        double dx = this.flowNoiseX.getValue((double)worldX / 64.0, (double)worldZ / 64.0, 0.0);
        double dz = this.flowNoiseZ.getValue((double)worldX / 64.0, (double)worldZ / 64.0, 0.0);
        double flowDirection = Math.toDegrees(Math.atan2(dz, dx));
        if (flowDirection < 0.0) {
            flowDirection += 360.0;
        }
        return new RiverInfo(true, distSq, width, depth, flowDirection);
    }

    public boolean isRiver(int worldX, int worldZ) {
        double pathValue = this.pathNoise.getValue((double)worldX / 64.0, (double)worldZ / 64.0, 0.0);
        return Math.abs(pathValue) < 0.15;
    }

    public double getWidth(int worldX, int worldZ) {
        double widthValue = this.widthNoise.getValue((double)worldX / 128.0, (double)worldZ / 128.0, 0.0);
        return 8.0 + (widthValue + 1.0) * 0.5 * 16.0;
    }

    public double getGradientDrivenWidth(int worldX, int worldZ, double gradient) {
        double widthValue = this.widthNoise.getValue((double)worldX / 128.0, (double)worldZ / 128.0, 0.0);
        double baseWidth = 10.0 + (widthValue + 1.0) * 0.5 * 10.0;
        double gradientWeight = gradient > 0.5 ? 0.0 : (gradient < 0.2 ? 1.0 : (0.5 - gradient) / 0.3);
        double targetWidth = 10.0 + gradientWeight * 10.0;
        return baseWidth * GRADIENT_WIDTH_BASE_WEIGHT + targetWidth * GRADIENT_WIDTH_TARGET_WEIGHT;
    }

    public double getDepth(int worldX, int worldZ) {
        double pathValue = this.pathNoise.getValue((double)worldX / 64.0, (double)worldZ / 64.0, 0.0);
        double pathIntensity = 1.0 - Math.abs(pathValue) / 0.15;
        double depthValue = this.depthNoise.getValue((double)worldX / 32.0, (double)worldZ / 32.0, 0.0);
        return pathIntensity * 12.0 * (0.5 + 0.5 * depthValue);
    }
}

