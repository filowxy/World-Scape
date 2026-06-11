package com.worldscape.terrain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TerrainFunctionInterpreter — function evaluation, custom primitive
 * registration/unregistration, and built-in primitive resilience.
 */
class TerrainFunctionInterpreterTest {

    private static final double TOL = TestUtils.DEFAULT_TOLERANCE;

    /**
     * Clean up custom primitives between tests.
     */
    @AfterEach
    void tearDown() {
        // Unregister any test primitives left behind
        for (String name : Set.copyOf(TerrainFunctionInterpreter.getRegisteredPrimitiveNames())) {
            if (name.startsWith("test_")) {
                TerrainFunctionInterpreter.unregisterPrimitive(name);
            }
        }
    }

    // ─── Helper: Construct a TerrainBlendResult for evaluate() ────────────

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

    // ─── Helper: Construct the HIGH_MOUNTAINS FunctionDef ─────────────────

    private static TerrainFunctionSchema.FunctionDef createHighMountainsDef() {
        Map<String, Object> fbmParams = new HashMap<>();
        fbmParams.put("octaves", 6);
        fbmParams.put("gain", 0.5);
        TerrainFunctionSchema.NoisePrimitive hm_fbm = new TerrainFunctionSchema.NoisePrimitive(
            "hm_fbm", "fbm", fbmParams, 200.0);

        Map<String, Object> domainParams = new HashMap<>();
        domainParams.put("warp_strength", 0.15);
        TerrainFunctionSchema.NoisePrimitive hm_domain = new TerrainFunctionSchema.NoisePrimitive(
            "hm_domain", "domain_rotated", domainParams, 15.0);

        Map<String, Object> turbParams = new HashMap<>();
        turbParams.put("strength", 0.6);
        TerrainFunctionSchema.NoisePrimitive hm_turb = new TerrainFunctionSchema.NoisePrimitive(
            "hm_turb", "turbulence", turbParams, 20.0);

        List<TerrainFunctionSchema.NoisePrimitive> functions = List.of(hm_fbm, hm_domain, hm_turb);
        TerrainFunctionSchema.Combinator combinator = new TerrainFunctionSchema.Combinator(
            "add", List.of("hm_fbm", "hm_domain", "hm_turb"), null, null, 0.0, null, 0.0);

        return new TerrainFunctionSchema.FunctionDef(
            "worldscape:high_mountains", 260, 512, new int[]{5}, 250.0,
            null, functions, combinator, "base + combined", null
        );
    }

    // ─── Helper: Construct the RIDGE FunctionDef ──────────────────────────

    private static TerrainFunctionSchema.FunctionDef createRidgeDef() {
        Map<String, Object> sineParams = new HashMap<>();
        sineParams.put("freq_x", 0.007);
        sineParams.put("freq_z", 0.004);
        sineParams.put("primary_amp", 35.0);
        sineParams.put("secondary_amp", 18.0);
        sineParams.put("secondary_freq_x", 0.025);
        sineParams.put("secondary_freq_z", 0.018);
        sineParams.put("gradient_sensitivity", 0.6);
        sineParams.put("gradient_half_factor", 0.5);
        TerrainFunctionSchema.NoisePrimitive r_sine = new TerrainFunctionSchema.NoisePrimitive(
            "r_sine", "gradient_constrained_sine", sineParams, 1.0);

        Map<String, Object> fbmParams = new HashMap<>();
        fbmParams.put("octaves", 6);
        fbmParams.put("gain", 0.5);
        TerrainFunctionSchema.NoisePrimitive r_fbm = new TerrainFunctionSchema.NoisePrimitive(
            "r_fbm", "fbm", fbmParams, 150.0);

        Map<String, Object> turbParams = new HashMap<>();
        turbParams.put("strength", 0.6);
        TerrainFunctionSchema.NoisePrimitive r_turb = new TerrainFunctionSchema.NoisePrimitive(
            "r_turb", "turbulence", turbParams, 15.0);

        Map<String, Object> domainParams = new HashMap<>();
        domainParams.put("warp_strength", 0.15);
        TerrainFunctionSchema.NoisePrimitive r_domain = new TerrainFunctionSchema.NoisePrimitive(
            "r_domain", "domain_rotated", domainParams, 10.0);

        List<TerrainFunctionSchema.NoisePrimitive> functions = List.of(r_sine, r_fbm, r_turb, r_domain);
        TerrainFunctionSchema.Combinator combinator = new TerrainFunctionSchema.Combinator(
            "add", List.of("r_sine", "r_fbm", "r_turb", "r_domain"), null, null, 0.0, null, 0.0);

        Map<String, Object> ctParams = new HashMap<>();
        ctParams.put("along", 1.5);
        ctParams.put("across", 0.7);
        TerrainFunctionSchema.CoordinateTransform ct = new TerrainFunctionSchema.CoordinateTransform(
            "energy_stretched", ctParams);

        return new TerrainFunctionSchema.FunctionDef(
            "worldscape:ridge", 140, 275, new int[]{5}, Double.MAX_VALUE,
            ct, functions, combinator, "base + combined", null
        );
    }

    // ─── HIGH_MOUNTAINS Tests ─────────────────────────────────────────────

    @Test
    void testHighMountainsDeterministic() {
        TerrainFunctionSchema.FunctionDef def = createHighMountainsDef();
        TerrainFieldSampler fs = TestUtils.createSampler(TestUtils.SEED_A);
        RegionController.TerrainBlendResult blend = createBlend(300.0, 4,
            TerrainType.HIGH_MOUNTAINS, 0.9);

        double v1 = TerrainFunctionInterpreter.evaluate(def, 100, 200, fs, blend);
        double v2 = TerrainFunctionInterpreter.evaluate(def, 100, 200, fs, blend);
        assertEquals(v1, v2, TOL, "HIGH_MOUNTAINS evaluation should be deterministic");
    }

    @Test
    void testHighMountainsCrossSeed() {
        // Use a low blendedHeight to avoid the heightCap (250.0) masking seed differences.
        // If blendedHeight is too high, both seeded results will be capped at 250.0.
        TerrainFunctionSchema.FunctionDef def = createHighMountainsDef();
        TerrainFieldSampler fsA = TestUtils.createSampler(TestUtils.SEED_A);
        TerrainFieldSampler fsB = TestUtils.createSampler(TestUtils.SEED_B);
        RegionController.TerrainBlendResult blendA = createBlend(0.0, 4,
            TerrainType.HIGH_MOUNTAINS, 0.9);
        RegionController.TerrainBlendResult blendB = createBlend(0.0, 4,
            TerrainType.HIGH_MOUNTAINS, 0.9);

        double vA = TerrainFunctionInterpreter.evaluate(def, 100, 200, fsA, blendA);
        double vB = TerrainFunctionInterpreter.evaluate(def, 100, 200, fsB, blendB);
        assertNotEquals(vA, vB, "HIGH_MOUNTAINS with different seeds should differ");
    }

    // ─── RIDGE Tests ──────────────────────────────────────────────────────

    @Test
    void testRidgeDeterministic() {
        TerrainFunctionSchema.FunctionDef def = createRidgeDef();
        TerrainFieldSampler fs = TestUtils.createSampler(TestUtils.SEED_A);
        RegionController.TerrainBlendResult blend = createBlend(200.0, 4,
            TerrainType.RIDGE, 0.9);

        double v1 = TerrainFunctionInterpreter.evaluate(def, 100, 200, fs, blend);
        double v2 = TerrainFunctionInterpreter.evaluate(def, 100, 200, fs, blend);
        assertEquals(v1, v2, TOL, "RIDGE evaluation should be deterministic");
    }

    @Test
    void testRidgeCrossSeed() {
        TerrainFunctionSchema.FunctionDef def = createRidgeDef();
        TerrainFieldSampler fsA = TestUtils.createSampler(TestUtils.SEED_A);
        TerrainFieldSampler fsB = TestUtils.createSampler(TestUtils.SEED_B);
        RegionController.TerrainBlendResult blendA = createBlend(200.0, 4,
            TerrainType.RIDGE, 0.9);
        RegionController.TerrainBlendResult blendB = createBlend(200.0, 4,
            TerrainType.RIDGE, 0.9);

        double vA = TerrainFunctionInterpreter.evaluate(def, 100, 200, fsA, blendA);
        double vB = TerrainFunctionInterpreter.evaluate(def, 100, 200, fsB, blendB);
        assertNotEquals(vA, vB, "RIDGE with different seeds should differ");
    }

    // ─── Custom Primitive Registration Tests ──────────────────────────────

    @Test
    void testRegisterCustomPrimitive() {
        TerrainPrimitiveProvider provider = (x, z, params, fs, blend) -> 0.0;
        TerrainFunctionInterpreter.registerPrimitive("test_dummy", provider);

        Set<String> names = TerrainFunctionInterpreter.getRegisteredPrimitiveNames();
        assertTrue(names.contains("test_dummy"),
            "Registered primitive should appear in getRegisteredPrimitiveNames()");
    }

    @Test
    void testCustomPrimitiveOverrides() {
        // Register a custom primitive that always returns 42.0
        TerrainFunctionInterpreter.registerPrimitive("test_neg",
            (x, z, params, fs, blend) -> 42.0);

        // Create a FunctionDef with a NoisePrimitive using the custom primitive
        TerrainFunctionSchema.NoisePrimitive np = new TerrainFunctionSchema.NoisePrimitive(
            "np1", "test_neg", new HashMap<>(), 2.0);
        TerrainFunctionSchema.FunctionDef def = new TerrainFunctionSchema.FunctionDef(
            "test:custom", -64, 512, null, Double.MAX_VALUE,
            null, List.of(np), null, "combined", null);

        TerrainFieldSampler fs = TestUtils.createSampler(TestUtils.SEED_A);
        RegionController.TerrainBlendResult blend = createBlend(200.0, 3,
            TerrainType.PLAINS, 0.9);

        double result = TerrainFunctionInterpreter.evaluate(def, 100, 200, fs, blend);
        // Custom primitive returns 42.0, multiplied by amplitude 2.0 → 84.0
        assertEquals(84.0, result, TOL,
            "Custom primitive should return providerValue * amplitude");
    }

    @Test
    void testUnregisterCustomPrimitive() {
        TerrainFunctionInterpreter.registerPrimitive("test_remove",
            (x, z, params, fs, blend) -> 0.0);
        assertTrue(TerrainFunctionInterpreter.getRegisteredPrimitiveNames().contains("test_remove"));

        TerrainFunctionInterpreter.unregisterPrimitive("test_remove");
        assertFalse(TerrainFunctionInterpreter.getRegisteredPrimitiveNames().contains("test_remove"),
            "Unregistered primitive should not appear in getRegisteredPrimitiveNames()");
    }

    @Test
    void testBuiltInPrimitivesStillWork() {
        // Register a custom primitive that should not interfere with built-in fbm
        TerrainFunctionInterpreter.registerPrimitive("test_custom",
            (x, z, params, fs, blend) -> 99.0);

        // Create a FunctionDef using ONLY built-in fbm (not the custom primitive)
        Map<String, Object> fbmParams = new HashMap<>();
        fbmParams.put("octaves", 4);
        fbmParams.put("gain", 0.5);
        TerrainFunctionSchema.NoisePrimitive np = new TerrainFunctionSchema.NoisePrimitive(
            "builtin_test", "fbm", fbmParams, 1.0);
        TerrainFunctionSchema.FunctionDef def = new TerrainFunctionSchema.FunctionDef(
            "test:builtin", -64, 512, null, Double.MAX_VALUE,
            null, List.of(np), null, "combined", null);

        TerrainFieldSampler fs = TestUtils.createSampler(TestUtils.SEED_A);
        RegionController.TerrainBlendResult blend = createBlend(200.0, 3,
            TerrainType.PLAINS, 0.9);

        double result = TerrainFunctionInterpreter.evaluate(def, 100, 200, fs, blend);

        // The built-in fbm should have been used, not the custom test_custom (99.0)
        double expectedFbm = fs.sampleFbmCached(100, 200, 4, 0.5) * 1.0;
        assertEquals(expectedFbm, result, TOL * 100,
            "Built-in fbm should still work after custom primitive registration");
    }
}