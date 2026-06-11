package com.worldscape.terrain;

/**
 * Test utilities for World Scape terrain generation tests.
 * Provides seeded TerrainFieldSampler instances and common test helpers.
 */
public final class TestUtils {

    private TestUtils() {
    }

    /** Test seed #1: small positive */
    public static final long SEED_A = 54321L;
    /** Test seed #2: large positive */
    public static final long SEED_B = 92748229837L;
    /** Test seed #3: negative */
    public static final long SEED_C = -28374534565L;

    /**
     * Create a TerrainFieldSampler with the given world seed.
     * Uses the production getOrCreate factory to ensure consistency
     * between test and production behavior.
     *
     * @param seed world seed
     * @return a seeded TerrainFieldSampler
     */
    public static TerrainFieldSampler createSampler(long seed) {
        return TerrainFieldSampler.getOrCreate(seed);
    }

    /**
     * Assert that two double values are equal within floating-point tolerance.
     * Throws AssertionError if not.
     *
     * @param expected  expected value
     * @param actual    actual value
     * @param tolerance allowed absolute difference
     * @param message   error message
     */
    public static void assertDoubleEquals(double expected, double actual, double tolerance, String message) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(
                String.format("%s: expected %f but got %f (diff=%f, tolerance=%f)",
                    message, expected, actual, Math.abs(expected - actual), tolerance));
        }
    }

    /**
     * Standard tolerance for floating-point noise comparisons.
     * Noise is deterministic but may have tiny differences due to FPU rounding modes.
     */
    public static final double DEFAULT_TOLERANCE = 1e-12;
}