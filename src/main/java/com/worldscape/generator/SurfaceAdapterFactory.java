package com.worldscape.generator;

import com.worldscape.generator.FallbackSurfaceAdapter;
import com.worldscape.generator.ReflectionSurfaceAdapter;
import com.worldscape.generator.SurfaceAdapter;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SurfaceAdapterFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(SurfaceAdapterFactory.class);
    private static final String MOD_ID = "[World Scape] [SurfaceAdapterFactory]";

    private SurfaceAdapterFactory() {
    }

    public static SurfaceAdapter create(AdapterType type, Object generator, NoiseGeneratorSettings settings, long worldSeed) {
        switch (type.ordinal()) {
            case 0: {
                return SurfaceAdapterFactory.createReflectionAdapter(settings, worldSeed, generator);
            }
            case 1: {
                return SurfaceAdapterFactory.createFallbackAdapter(generator, worldSeed, settings.seaLevel());
            }
        }
        return SurfaceAdapterFactory.createAutoAdapter(generator, settings, worldSeed);
    }

    private static SurfaceAdapter createReflectionAdapter(NoiseGeneratorSettings settings, long worldSeed, Object generator) {
        LOGGER.info("{} Creating ReflectionSurfaceAdapter", (Object)MOD_ID);
        if (generator instanceof ChunkGenerator) {
            return new ReflectionSurfaceAdapter(settings, worldSeed, (ChunkGenerator)generator);
        }
        LOGGER.warn("{} Generator is not ChunkGenerator type, creating adapter without generator reference", (Object)MOD_ID);
        return new ReflectionSurfaceAdapter(settings, worldSeed, null);
    }

    private static SurfaceAdapter createFallbackAdapter(Object generator, long worldSeed, int seaLevel) {
        LOGGER.info("{} Creating FallbackSurfaceAdapter", (Object)MOD_ID);
        if (generator instanceof ChunkGenerator) {
            return new FallbackSurfaceAdapter((ChunkGenerator)generator, worldSeed, seaLevel);
        }
        LOGGER.warn("{} Generator is not ChunkGenerator type, using simplified fallback", (Object)MOD_ID);
        return new SimplifiedFallbackAdapter();
    }

    private static SurfaceAdapter createAutoAdapter(Object generator, NoiseGeneratorSettings settings, long worldSeed) {
        SurfaceAdapter reflectionAdapter = SurfaceAdapterFactory.createReflectionAdapter(settings, worldSeed, generator);
        if (reflectionAdapter.isAvailable()) {
            LOGGER.info("{} Using ReflectionSurfaceAdapter (primary)", (Object)MOD_ID);
            return reflectionAdapter;
        }
        LOGGER.warn("{} ReflectionSurfaceAdapter not available, falling back to FallbackSurfaceAdapter", (Object)MOD_ID);
        return SurfaceAdapterFactory.createFallbackAdapter(generator, worldSeed, settings.seaLevel());
    }

    public static enum AdapterType {
        REFLECTION,
        FALLBACK,
        AUTO;

    }

    private static class SimplifiedFallbackAdapter
    implements SurfaceAdapter {
        private static final Logger LOGGER = LoggerFactory.getLogger(SimplifiedFallbackAdapter.class);

        private SimplifiedFallbackAdapter() {
        }

        @Override
        public String getName() {
            return "SimplifiedFallbackAdapter";
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public boolean buildSurface(SurfaceAdapter.SurfaceBuildContext context) {
            LOGGER.warn("[World Scape] [SimplifiedFallbackAdapter] Surface build not implemented, using default");
            return false;
        }
    }
}

