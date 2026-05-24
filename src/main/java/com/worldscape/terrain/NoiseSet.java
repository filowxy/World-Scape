package com.worldscape.terrain;

import com.worldscape.util.SeedDeriver;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public class NoiseSet {
    private static final int MAX_CACHE_SIZE = 32;
    private static final Map<Long, NoiseSet> LRU_CACHE = Collections.synchronizedMap(new LinkedHashMap<Long, NoiseSet>(16, 0.75f, true){

        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, NoiseSet> eldest) {
            return this.size() > 32;
        }
    });
    private final NormalNoise continent;
    private final NormalNoise region;
    private final NormalNoise mountain;
    private final NormalNoise valley;
    private final NormalNoise hills;
    private final NormalNoise plainsMacro;
    private final NormalNoise plainsMeso;
    private final NormalNoise plainsMicro;
    private final NormalNoise riverPath;
    private final NormalNoise riverWidth;
    private final NormalNoise drainage;
    private final NormalNoise mountainPeaks;
    private final NormalNoise mountainRidge;
    private final NormalNoise mountainDetail;
    private final NormalNoise seabed;
    private final long worldSeed;

    private NoiseSet(long worldSeed) {
        this.worldSeed = worldSeed;
        long continentSeed = SeedDeriver.deriveSeed(worldSeed, 337254991625L);
        long regionSeed = SeedDeriver.deriveSeed(worldSeed, 410538989594L);
        long mountainSeed = SeedDeriver.deriveSeed(worldSeed, 415120288043L);
        long valleySeed = SeedDeriver.deriveSeed(worldSeed, 488421063228L);
        long hillsSeed = SeedDeriver.deriveSeed(worldSeed, 561721772877L);
        long plainsMacroSeed = SeedDeriver.deriveSeed(worldSeed, 635022548062L);
        long plainsMesoSeed = SeedDeriver.deriveSeed(worldSeed, 708323323247L);
        long plainsMicroSeed = SeedDeriver.deriveSeed(worldSeed, 781624098416L);
        long riverPathSeed = SeedDeriver.deriveSeed(worldSeed, 850629906305L);
        long riverWidthSeed = SeedDeriver.deriveSeed(worldSeed, 923930677394L);
        long drainageSeed = SeedDeriver.deriveSeed(worldSeed, 997230404003L);
        long mountainPeaksSeed = SeedDeriver.deriveSeed(worldSeed, 1070262743732L);
        long mountainRidgeSeed = SeedDeriver.deriveSeed(worldSeed, 44051891159L);
        long mountainDetailSeed = SeedDeriver.deriveSeed(worldSeed, 117352666344L);
        long seabedSeed = SeedDeriver.deriveSeabedNoiseSeed(worldSeed);
        this.continent = NormalNoise.create((RandomSource)RandomSource.create((long)continentSeed), (int)-9, (double[])new double[]{2.0});
        this.region = NormalNoise.create((RandomSource)RandomSource.create((long)regionSeed), (int)-6, (double[])new double[]{1.5});
        this.mountain = NormalNoise.create((RandomSource)RandomSource.create((long)mountainSeed), (int)-5, (double[])new double[]{1.2});
        this.valley = NormalNoise.create((RandomSource)RandomSource.create((long)valleySeed), (int)-4, (double[])new double[]{1.0});
        this.hills = NormalNoise.create((RandomSource)RandomSource.create((long)hillsSeed), (int)-3, (double[])new double[]{0.8});
        this.plainsMacro = NormalNoise.create((RandomSource)RandomSource.create((long)plainsMacroSeed), (int)-9, (double[])new double[]{1.0});
        this.plainsMeso = NormalNoise.create((RandomSource)RandomSource.create((long)plainsMesoSeed), (int)-6, (double[])new double[]{1.0});
        this.plainsMicro = NormalNoise.create((RandomSource)RandomSource.create((long)plainsMicroSeed), (int)-4, (double[])new double[]{0.6});
        this.riverPath = NormalNoise.create((RandomSource)RandomSource.create((long)riverPathSeed), (int)-5, (double[])new double[]{1.0});
        this.riverWidth = NormalNoise.create((RandomSource)RandomSource.create((long)riverWidthSeed), (int)-4, (double[])new double[]{0.6});
        this.drainage = NormalNoise.create((RandomSource)RandomSource.create((long)drainageSeed), (int)-3, (double[])new double[]{0.8});
        this.mountainPeaks = NormalNoise.create((RandomSource)RandomSource.create((long)mountainPeaksSeed), (int)-4, (double[])new double[]{1.5});
        this.mountainRidge = NormalNoise.create((RandomSource)RandomSource.create((long)mountainRidgeSeed), (int)-3, (double[])new double[]{1.2});
        this.mountainDetail = NormalNoise.create((RandomSource)RandomSource.create((long)mountainDetailSeed), (int)-2, (double[])new double[]{0.8});
        this.seabed = NormalNoise.create((RandomSource)RandomSource.create((long)seabedSeed), (int)-2, (double[])new double[]{0.8});
    }

    public static NoiseSet getOrCreate(long worldSeed) {
        return LRU_CACHE.computeIfAbsent(worldSeed, NoiseSet::new);
    }

    public static void clearCache() {
        LRU_CACHE.clear();
    }

    public double sample(NoiseProfile profile, int x, int z) {
        double cx = (double)x + 0.5;
        double cz = (double)z + 0.5;
        return switch (profile.ordinal()) {
            default -> throw new MatchException(null, null);
            case 0 -> this.continent.getValue(cx / 2048.0, cz / 2048.0, 0.0);
            case 1 -> this.region.getValue(cx / 1024.0, cz / 1024.0, 0.0);
            case 2 -> this.mountain.getValue(cx / 512.0, cz / 512.0, 0.0);
            case 3 -> this.valley.getValue(cx / 256.0, cz / 256.0, 0.0);
            case 4 -> this.hills.getValue(cx / 128.0, cz / 128.0, 0.0);
            case 5 -> this.plainsMacro.getValue(cx / 512.0, cz / 512.0, 0.0);
            case 6 -> this.plainsMeso.getValue(cx / 96.0, cz / 96.0, 0.0);
            case 7 -> this.plainsMicro.getValue(cx / 24.0, cz / 24.0, 0.0);
            case 8 -> this.riverPath.getValue(cx / 512.0, cz / 512.0, 0.0);
            case 9 -> this.riverWidth.getValue(cx / 256.0, cz / 256.0, 0.0);
            case 10 -> this.drainage.getValue(cx / 256.0, cz / 256.0, 0.0);
            case 11 -> this.mountainPeaks.getValue(cx / 256.0, cz / 256.0, 0.0);
            case 12 -> this.mountainRidge.getValue(cx / 128.0, cz / 128.0, 0.0);
            case 13 -> this.mountainDetail.getValue(cx / 64.0, cz / 64.0, 0.0);
            case 14 -> this.seabed.getValue(cx / 64.0, cz / 64.0, 0.0);
        };
    }

    public long getWorldSeed() {
        return this.worldSeed;
    }

    public static enum NoiseProfile {
        CONTINENT,
        REGION,
        MOUNTAIN,
        VALLEY,
        HILLS,
        PLAINS_MACRO,
        PLAINS_MESO,
        PLAINS_MICRO,
        RIVER_PATH,
        RIVER_WIDTH,
        DRAINAGE,
        MOUNTAIN_PEAKS,
        MOUNTAIN_RIDGE,
        MOUNTAIN_DETAIL,
        SEABED;

    }
}

