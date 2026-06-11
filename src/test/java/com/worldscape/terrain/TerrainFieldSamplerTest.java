package com.worldscape.terrain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TerrainFieldSampler noise determinism, caching, and cross-seed isolation.
 */
class TerrainFieldSamplerTest {

    private static final double TOL = TestUtils.DEFAULT_TOLERANCE;

    // ─── Determinism Tests ────────────────────────────────────────────────

    @Test
    void testSampleFbmDeterministic() {
        TerrainFieldSampler fs = TestUtils.createSampler(TestUtils.SEED_A);
        double v1 = fs.sampleFbm(100, 200);
        double v2 = fs.sampleFbm(100, 200);
        assertEquals(v1, v2, TOL, "sampleFbm should be deterministic");
    }

    @Test
    void testSampleFbmWithParamsDeterministic() {
        TerrainFieldSampler fs = TestUtils.createSampler(TestUtils.SEED_A);
        double v1 = fs.sampleFbm(100, 200, 6, 0.5);
        double v2 = fs.sampleFbm(100, 200, 6, 0.5);
        assertEquals(v1, v2, TOL, "sampleFbm with params should be deterministic");
    }

    @Test
    void testSampleTurbulenceDeterministic() {
        TerrainFieldSampler fs = TestUtils.createSampler(TestUtils.SEED_A);
        double v1 = fs.sampleTurbulence(100, 200, 0.6);
        double v2 = fs.sampleTurbulence(100, 200, 0.6);
        assertEquals(v1, v2, TOL, "sampleTurbulence should be deterministic");
    }

    @Test
    void testSampleDomainRotatedDeterministic() {
        TerrainFieldSampler fs = TestUtils.createSampler(TestUtils.SEED_A);
        double v1 = fs.sampleDomainRotated(100, 200, 0.15);
        double v2 = fs.sampleDomainRotated(100, 200, 0.15);
        assertEquals(v1, v2, TOL, "sampleDomainRotated should be deterministic");
    }

    // ─── Cross-Seed Isolation Test ────────────────────────────────────────

    @Test
    void testCrossSeedDifferent() {
        TerrainFieldSampler fsA = TestUtils.createSampler(TestUtils.SEED_A);
        TerrainFieldSampler fsB = TestUtils.createSampler(TestUtils.SEED_B);
        double vA = fsA.sampleFbm(100, 200);
        double vB = fsB.sampleFbm(100, 200);
        assertNotEquals(vA, vB, "Different seeds should produce different noise values");
    }

    // ─── Cache Equivalence Tests ──────────────────────────────────────────

    @Test
    void testFbmCachedEqualsUncached() {
        TerrainFieldSampler fs = TestUtils.createSampler(TestUtils.SEED_A);
        double cached = fs.sampleFbmCached(100, 200, 6, 0.5);
        double uncached = fs.sampleFbm(100, 200, 6, 0.5);
        assertEquals(uncached, cached, TOL, "sampleFbmCached should match sampleFbm");
    }

    @Test
    void testTurbulenceCachedEqualsUncached() {
        TerrainFieldSampler fs = TestUtils.createSampler(TestUtils.SEED_A);
        double cached = fs.sampleTurbulenceCached(100, 200, 0.6);
        double uncached = fs.sampleTurbulence(100, 200, 0.6);
        assertEquals(uncached, cached, TOL, "sampleTurbulenceCached should match sampleTurbulence");
    }

    @Test
    void testDomainRotatedCachedEqualsUncached() {
        TerrainFieldSampler fs = TestUtils.createSampler(TestUtils.SEED_A);
        double cached = fs.sampleDomainRotatedCached(100, 200, 0.15);
        double uncached = fs.sampleDomainRotated(100, 200, 0.15);
        assertEquals(uncached, cached, TOL, "sampleDomainRotatedCached should match sampleDomainRotated");
    }

    // ─── Cache Behavior Tests ─────────────────────────────────────────────

    @Test
    void testCacheHitRepeatedCall() {
        // Calling cached method twice returns the same (cached) result.
        // Since noise is deterministic, the first call populates the cache
        // and the second call retrieves from cache via computeIfAbsent.
        TerrainFieldSampler fs = TestUtils.createSampler(TestUtils.SEED_A);
        double v1 = fs.sampleFbmCached(100, 200, 6, 0.5);
        double v2 = fs.sampleFbmCached(100, 200, 6, 0.5);
        assertEquals(v1, v2, TOL, "Repeated cached calls should return the same value");
    }

    @Test
    void testCacheClearAfterGetOrCreate() {
        // getOrCreate clears caches of existing instances.
        // Verify: create sampler, fill cache, getOrCreate again, cache repopulates correctly.
        TerrainFieldSampler fs = TestUtils.createSampler(TestUtils.SEED_A);

        // Populate the fbm cache
        fs.sampleFbmCached(100, 200, 6, 0.5);

        // getOrCreate returns the same instance but clears caches
        TerrainFieldSampler fsAgain = TerrainFieldSampler.getOrCreate(TestUtils.SEED_A);
        assertSame(fs, fsAgain, "getOrCreate should return the same instance");

        // After cache clear, cached methods should still work (repopulate cache)
        double v = fsAgain.sampleFbmCached(100, 200, 6, 0.5);
        double expected = fsAgain.sampleFbm(100, 200, 6, 0.5);
        assertEquals(expected, v, TOL, "Cached method should work after cache clear");
    }
}