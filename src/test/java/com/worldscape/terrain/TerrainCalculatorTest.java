package com.worldscape.terrain;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TerrainCalculator — height calculation determinism,
 * cross-seed isolation, null-blend fallback, and terrain type determination.
 */
class TerrainCalculatorTest {

    private static final double TOL = TestUtils.DEFAULT_TOLERANCE;

    // ─── Helper: Construct a TerrainBlendResult ───────────────────────────

    private static RegionController.TerrainBlendResult createBlend(double blendedHeight, int tier,
                                                                    TerrainType dominantType, double dominantWeight) {
        MacroRegionInfo macroInfo = new MacroRegionInfo(
            tier, Math.max(0, tier - 1), blendedHeight,
            MacroRegionInfo.TectonicType.CRATON,
            MacroRegionInfo.ClimateZone.TEMPERATE,
            0.9, 2, 0, 0
        );
        return new RegionController.TerrainBlendResult(
            blendedHeight, macroInfo,
            Collections.emptyList(), 0.0,
            dominantType, dominantWeight
        );
    }

    // ─── calcHeightForType Tests ──────────────────────────────────────────

    @Test
    void testCalcHeightForTypeDeterministic() {
        TerrainFieldSampler fs = TestUtils.createSampler(TestUtils.SEED_A);
        RegionController.TerrainBlendResult blend = createBlend(200.0, 3,
            TerrainType.PLAINS, 0.9);

        double v1 = TerrainCalculator.calcHeightForType(100, 200, 200.0,
            TerrainType.PLAINS, fs, blend);
        double v2 = TerrainCalculator.calcHeightForType(100, 200, 200.0,
            TerrainType.PLAINS, fs, blend);
        assertEquals(v1, v2, TOL, "calcHeightForType should be deterministic");
    }

    @Test
    void testCalcHeightForTypeCrossSeed() {
        TerrainFieldSampler fsA = TestUtils.createSampler(TestUtils.SEED_A);
        TerrainFieldSampler fsB = TestUtils.createSampler(TestUtils.SEED_B);
        RegionController.TerrainBlendResult blendA = createBlend(200.0, 3,
            TerrainType.PLAINS, 0.9);
        RegionController.TerrainBlendResult blendB = createBlend(200.0, 3,
            TerrainType.PLAINS, 0.9);

        double vA = TerrainCalculator.calcHeightForType(100, 200, 200.0,
            TerrainType.PLAINS, fsA, blendA);
        double vB = TerrainCalculator.calcHeightForType(100, 200, 200.0,
            TerrainType.PLAINS, fsB, blendB);
        assertNotEquals(vA, vB,
            "calcHeightForType with different seeds should differ");
    }

    @Test
    void testCalcHeightForTypeWithNullBlend() {
        TerrainFieldSampler fs = TestUtils.createSampler(TestUtils.SEED_A);

        // When blend is null, calcHeightForType falls back to fbm-based calculation:
        // baseHeight + sampleFbm(x, z, 4, 0.2) * 15.0, clamped to [-64, 400]
        double baseHeight = 150.0;
        double result = TerrainCalculator.calcHeightForType(100, 200, baseHeight,
            TerrainType.PLAINS, fs);

        double expectedFallback = fs.sampleFbm(100, 200, 4, 0.2) * 15.0;
        double expected = Math.max(WorldScapeConstants.MIN_TERRAIN_HEIGHT,
            Math.min(WorldScapeConstants.MAX_TERRAIN_HEIGHT,
                baseHeight + expectedFallback));

        assertEquals(expected, result, TOL,
            "Null blend should trigger fbm-based fallback calculation");
    }

    // ─── determineTerrainType Tests ───────────────────────────────────────

    @Test
    void testDetermineTerrainType() {
        // When dominantWeight < 0.4 (DOMINANT_WEIGHT_THRESHOLD),
        // determineTerrainType falls back to tier-based hardcoded mapping.

        // Tier 0 → TRENCH
        RegionController.TerrainBlendResult blendT0 = createBlend(10.0, 0,
            TerrainType.SEA_PLATEAU, 0.1);
        assertEquals(TerrainType.TRENCH,
            TerrainCalculator.determineTerrainType(blendT0),
            "Tier 0 should map to TRENCH");

        // Tier 1 → SEA_PLATEAU
        RegionController.TerrainBlendResult blendT1 = createBlend(20.0, 1,
            TerrainType.TRENCH, 0.1);
        assertEquals(TerrainType.SEA_PLATEAU,
            TerrainCalculator.determineTerrainType(blendT1),
            "Tier 1 should map to SEA_PLATEAU");

        // Tier 2 → FLOODPLAIN (BEACH requires ocean proximity validation, so
        // the blind fallback uses FLOODPLAIN as the safe inland default)
        // Tier 2 → FLOODPLAIN（BEACH 需要海洋邻近性验证，
        // 因此盲目回退使用 FLOODPLAIN 作为安全的内陆默认值）
        RegionController.TerrainBlendResult blendT2 = createBlend(30.0, 2,
            TerrainType.PLAINS, 0.1);
        assertEquals(TerrainType.FLOODPLAIN,
            TerrainCalculator.determineTerrainType(blendT2),
            "Tier 2 should map to FLOODPLAIN (safe inland default, BEACH requires ocean validation)");

        // Tier 3 → PLAINS
        RegionController.TerrainBlendResult blendT3 = createBlend(40.0, 3,
            TerrainType.HILLS, 0.1);
        assertEquals(TerrainType.PLAINS,
            TerrainCalculator.determineTerrainType(blendT3),
            "Tier 3 should map to PLAINS");

        // Tier 4 → HILLS
        RegionController.TerrainBlendResult blendT4 = createBlend(60.0, 4,
            TerrainType.PLAINS, 0.1);
        assertEquals(TerrainType.HILLS,
            TerrainCalculator.determineTerrainType(blendT4),
            "Tier 4 should map to HILLS");

        // Tier 5 → HIGH_MOUNTAINS
        RegionController.TerrainBlendResult blendT5 = createBlend(150.0, 5,
            TerrainType.PLAINS, 0.1);
        assertEquals(TerrainType.HIGH_MOUNTAINS,
            TerrainCalculator.determineTerrainType(blendT5),
            "Tier 5 should map to HIGH_MOUNTAINS");
    }
}