package com.worldscape.terrain;

import java.util.Map;

/**
 * Functional interface for custom terrain noise primitive implementations.
 * <p>
 * Third-party mods can implement this interface and register their provider
 * via {@link TerrainFunctionInterpreter#registerPrimitive(String, TerrainPrimitiveProvider)}
 * to add custom noise types that can be referenced in {@code defaults.json}
 * or custom terrain type JSON files.
 *
 * <p><b>Built-in primitive names</b> (registered by default):</p>
 * <table>
 *   <tr><th>Name</th><th>Description</th><th>Return Range</th></tr>
 *   <tr><td>fbm</td><td>Fractional Brownian Motion (octaves, gain)</td><td>[-1, 1]</td></tr>
 *   <tr><td>turbulence</td><td>Absolute turbulence noise (strength)</td><td>[0, 1]</td></tr>
 *   <tr><td>domain_rotated</td><td>Domain-warped noise (warp_strength)</td><td>[-1, 1]</td></tr>
 *   <tr><td>gaussian</td><td>2D Gaussian (sigma, offset_x, offset_z)</td><td>[0, 1]</td></tr>
 *   <tr><td>sigmoid</td><td>Logistic sigmoid (input_scale, input)</td><td>[0, 1]</td></tr>
 *   <tr><td>tanh_scaled</td><td>Hyperbolic tangent (input_source, steepness)</td><td>[-1, 1]</td></tr>
 *   <tr><td>sine</td><td>Sine wave (freq_x, freq_z, phase_offset)</td><td>[-1, 1]</td></tr>
 *   <tr><td>gradient</td><td>fBm-derived terrain gradient</td><td>[0, ~2.0]</td></tr>
 *   <tr><td>math</td><td>Math expression: sin/cos/abs/sqrt/tanh/exp/log/max/min/clamp</td><td>variable</td></tr>
 *   <tr><td>gradient_constrained_sine</td><td>Gradient-constrained sine for ridges</td><td>variable</td></tr>
 *   <tr><td>contributing_point_distance</td><td>Distance to nearest Voronoi control point</td><td>[0, ~200]</td></tr>
 *   <tr><td>fm_sine</td><td>Frequency-modulated sine (freq_mod_amp, freq_mod_input)</td><td>[-1, 1]</td></tr>
 *   <tr><td>abs</td><td>Absolute value of another primitive (input)</td><td>[0, +inf)</td></tr>
 *   <tr><td>negate</td><td>Negation of another primitive (input)</td><td>variable</td></tr>
 *   <tr><td>constant</td><td>Fixed constant value (value)</td><td>constant * amplitude</td></tr>
 * </table>
 *
 * <p><b>Thread Safety:</b> Implementations must be stateless and thread-safe.
 * Multiple threads may call {@code evaluate()} concurrently during chunk generation,
 * especially with C2ME or other parallel chunk generation mods installed.</p>
 *
 * <p><b>Determinism:</b> Implementations must be seed-deterministic — given the same
 * world seed, (x, z) coordinates, and parameters, the output must always be identical.
 * Do not use {@code Math.random()} or any non-seeded random source.</p>
 */
@FunctionalInterface
public interface TerrainPrimitiveProvider {
    /**
     * Evaluate this custom noise primitive at the given world coordinates.
     *
     * @param x      world X coordinate (int, may be transformed)
     * @param z      world Z coordinate (int, may be transformed)
     * @param params the parameters map from the JSON definition (name → value)
     * @param fs     the terrain field sampler for accessing base noise functions
     * @param blend  the current Voronoi blend result (may be null)
     * @return the computed noise value (will be multiplied by the primitive's {@code amplitude} in the JSON)
     */
    double evaluate(int x, int z, Map<String, Object> params,
                    TerrainFieldSampler fs, RegionController.TerrainBlendResult blend);
}