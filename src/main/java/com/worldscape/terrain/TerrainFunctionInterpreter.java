package com.worldscape.terrain;

import com.worldscape.WorldScape;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Interpreter engine for terrain type function definitions.
 * <p>
 * Evaluates {@link TerrainFunctionSchema.FunctionDef} against
 * {@link TerrainFieldSampler} noise functions to produce a terrain height value.
 * <p>
 * Supports coordinate transforms, multiple noise primitives, combinators,
 * and final expression processing. Includes a built-in recursive-descent
 * math expression parser for the "math" noise primitive.
 *
 * @author World Scape
 */
public final class TerrainFunctionInterpreter {

    private TerrainFunctionInterpreter() {
    }

    private static final ConcurrentHashMap<String, TerrainPrimitiveProvider> customPrimitives = new ConcurrentHashMap<>();

    // ========================================================================
    // Noise Primitive Name Constants
    // ========================================================================
    private static final String FBM = "fbm";
    private static final String TURBULENCE = "turbulence";
    private static final String DOMAIN_ROTATED = "domain_rotated";
    private static final String GAUSSIAN = "gaussian";
    private static final String SIGMOID = "sigmoid";
    private static final String TANH_SCALED = "tanh_scaled";
    private static final String SINE = "sine";
    private static final String GRADIENT = "gradient";
    private static final String MATH = "math";
    private static final String GRADIENT_CONSTRAINED_SINE = "gradient_constrained_sine";
    private static final String CONTRIBUTING_POINT_DISTANCE = "contributing_point_distance";
    private static final String FM_SINE = "fm_sine";
    private static final String ABS = "abs";
    private static final String NEGATE = "negate";
    private static final String CONSTANT = "constant";

    // ========================================================================
    // Coordinate Transform Type Constants
    // ========================================================================
    private static final String CT_IDENTITY = "identity";
    private static final String CT_ENERGY_STRETCHED = "energy_stretched";
    private static final String CT_SCALE = "scale";

    // ========================================================================
    // Combinator Type Constants
    // ========================================================================
    private static final String COMBINATOR_ADD = "add";
    private static final String COMBINATOR_BLEND = "blend";
    private static final String COMBINATOR_PRODUCT = "product";
    private static final String COMBINATOR_SCALE = "scale";

    /**
     * Register a custom noise primitive provider.
     * <p>
     * Registered primitives take precedence over built-in primitives with the same name.
     * This allows third-party mods to override or extend the noise primitive system.
     *
     * @param name     the primitive name (matched against JSON "name" field)
     * @param provider the provider implementation
     * @throws NullPointerException if name or provider is null
     */
    public static void registerPrimitive(String name, TerrainPrimitiveProvider provider) {
        if (name == null || provider == null) {
            throw new NullPointerException("Primitive name and provider must not be null");
        }
        customPrimitives.put(name, provider);
        WorldScape.LOGGER.info("[World Scape] Registered custom noise primitive: '{}'", name);
    }

    /**
     * Unregister a custom noise primitive provider.
     * <p>
     * After unregistration, evaluations of the named primitive will fall back to
     * the built-in logic (or error if no built-in exists).
     *
     * @param name the primitive name to unregister
     * @return the removed provider, or null if none was registered
     */
    public static TerrainPrimitiveProvider unregisterPrimitive(String name) {
        TerrainPrimitiveProvider removed = customPrimitives.remove(name);
        if (removed != null) {
            WorldScape.LOGGER.info("[World Scape] Unregistered custom noise primitive: '{}'", name);
        }
        return removed;
    }

    /**
     * Get the names of all currently registered custom primitives.
     * <p>
     * Useful for runtime introspection and debugging.
     *
     * @return an unmodifiable set of registered primitive names
     */
    public static Set<String> getRegisteredPrimitiveNames() {
        return java.util.Collections.unmodifiableSet(customPrimitives.keySet());
    }

    /**
     * Evaluate a terrain function definition at the given world coordinates.
     *
     * @param def    the parsed function definition
     * @param worldX world X coordinate
     * @param worldZ world Z coordinate
     * @param fs     the terrain field sampler for noise evaluation
     * @param blend  the terrain blend result from RegionController
     * @return the computed terrain height
     */
    public static double evaluate(TerrainFunctionSchema.FunctionDef def, int worldX, int worldZ,
                                   TerrainFieldSampler fs, RegionController.TerrainBlendResult blend) {
        if (def == null || fs == null || blend == null) {
            WorldScape.LOGGER.warn("[World Scape] evaluate() called with null parameters: def={}, fs={}, blend={}",
                def, fs, blend);
            return 0.0;
        }

        double[] transformed = applyCoordinateTransform(def.coordinateTransform, worldX, worldZ, fs);

        double tx = transformed[0];
        double tz = transformed[1];

        Map<String, Double> idToOutput = new HashMap<>();
        double lastOutput = 0.0;

        for (TerrainFunctionSchema.NoisePrimitive primitive : def.functions) {
            double value = evaluatePrimitive(primitive, (int)Math.round(tx), (int)Math.round(tz),
                    tx, tz, fs, blend, idToOutput);
            if (primitive.id != null) {
                idToOutput.put(primitive.id, value);
            }
            lastOutput = value;
        }

        double combinedValue;
        if (def.combinator != null) {
            combinedValue = applyCombinator(def.combinator, idToOutput, lastOutput);
        } else {
            combinedValue = lastOutput;
        }

        double baseHeight = blend.blendedHeight;

        String finalExpr = def.finalExpr != null ? def.finalExpr : "combined";
        if ("base + combined".equals(finalExpr)) {
            combinedValue = baseHeight + combinedValue;
        }

        if (def.heightCap < Double.MAX_VALUE) {
            combinedValue = Math.min(combinedValue, def.heightCap);
        }

        return combinedValue;
    }

    /**
     * Apply coordinate-space transform to produce (tx, tz).
     *
     * @param ct     the coordinate transform definition (nullable)
     * @param worldX original world X
     * @param worldZ original world Z
     * @param fs     terrain field sampler
     * @return double[2] {tx, tz}
     */
    private static double[] applyCoordinateTransform(TerrainFunctionSchema.CoordinateTransform ct,
                                                      int worldX, int worldZ, TerrainFieldSampler fs) {
        if (ct == null || ct.type == null || CT_IDENTITY.equals(ct.type)) {
            return new double[]{worldX, worldZ};
        }

        switch (ct.type) {
            case CT_ENERGY_STRETCHED:
                return fs.getEnergyStretchedCoords(worldX, worldZ);

            case CT_SCALE: {
                double factor = getDoubleParam(ct.params, "factor", 1.0);
                double scale = getDoubleParam(ct.params, "scale", factor);
                return new double[]{worldX * scale, worldZ * scale};
            }

            default:
                WorldScape.LOGGER.warn("[World Scape] Unknown coordinate transform type: '{}', using identity",
                        ct.type);
                return new double[]{worldX, worldZ};
        }
    }

    /**
     * Evaluate a single noise primitive.
     *
     * @param primitive   the noise primitive definition
     * @param ix         integer world X (transformed)
     * @param iz         integer world Z (transformed)
     * @param tx         double world X (transformed, for sine/math)
     * @param tz         double world Z (transformed, for sine/math)
     * @param fs         terrain field sampler
     * @param blend      terrain blend result
     * @param idToOutput map of already-evaluated primitive ids to their outputs
     * @return the primitive's output value
     */
    private static double evaluatePrimitive(TerrainFunctionSchema.NoisePrimitive primitive,
                                             int ix, int iz, double tx, double tz,
                                             TerrainFieldSampler fs,
                                             RegionController.TerrainBlendResult blend,
                                             Map<String, Double> idToOutput) {
        Map<String, Object> params = primitive.params;
        double amp = primitive.amplitude;

        // Custom primitive takes precedence over built-in
        TerrainPrimitiveProvider custom = customPrimitives.get(primitive.name);
        if (custom != null) {
            return custom.evaluate(ix, iz, params, fs, blend) * amp;
        }

        switch (primitive.name) {
            case FBM: {
                int octaves = getIntParam(params, "octaves", 6);
                double gain = getDoubleParam(params, "gain", 0.5);
                return fs.sampleFbmCached(ix, iz, octaves, gain) * amp;
            }

            case TURBULENCE: {
                double strength = getDoubleParam(params, "strength", 1.0);
                return fs.sampleTurbulenceCached(ix, iz, strength) * amp;
            }

            case DOMAIN_ROTATED: {
                double warpStrength = getDoubleParam(params, "warp_strength", 1.0);
                return fs.sampleDomainRotatedCached(ix, iz, warpStrength) * amp;
            }

            case GAUSSIAN: {
                double sigma = getDoubleParam(params, "sigma", 100.0);
                // Support gaussian center offset: offset_x/offset_z can reference other primitives' output values
                double offsetX = 0.0;
                double offsetZ = 0.0;
                String offsetXRef = getStringParam(params, "offset_x", null);
                String offsetZRef = getStringParam(params, "offset_z", null);
                if (offsetXRef != null && idToOutput.containsKey(offsetXRef)) {
                    offsetX = idToOutput.get(offsetXRef);
                }
                if (offsetZRef != null && idToOutput.containsKey(offsetZRef)) {
                    offsetZ = idToOutput.get(offsetZRef);
                }
                return TerrainFieldSampler.gaussian(tx - offsetX, tz - offsetZ, sigma) * amp;
            }

            case SIGMOID: {
                double inputScale = getDoubleParam(params, "input_scale", 1.0);
                double input = getDoubleParam(params, "input", fs.sampleEnergy(ix, iz));
                return TerrainFieldSampler.sigmoid(input * inputScale) * amp;
            }

            case TANH_SCALED: {
                String inputSource = getStringParam(params, "input_source", null);
                double inputValue;
                if (inputSource != null && idToOutput.containsKey(inputSource)) {
                    inputValue = idToOutput.get(inputSource);
                } else {
                    inputValue = fs.sampleFbm(ix, iz);
                }
                double steepness = getDoubleParam(params, "steepness", 1.0);
                return TerrainFieldSampler.tanhScaled(inputValue, steepness) * amp;
            }

            case SINE: {
                double freqX = getDoubleParam(params, "freq_x", 0.01);
                double freqZ = getDoubleParam(params, "freq_z", 0.01);
                double phaseOffset = getDoubleParam(params, "phase_offset", 0.0);
                return Math.sin(tx * freqX + tz * freqZ + phaseOffset) * amp;
            }

            case GRADIENT: {
                return fs.calculateGradient(ix, iz) * amp;
            }

            case MATH: {
                String expression = getStringParam(params, "expression", "0");
                Map<String, Object> bindingsRaw = getMapParam(params, "bindings");
                Map<String, Double> mathBindings = new HashMap<>(idToOutput);
                if (bindingsRaw != null) {
                    for (Map.Entry<String, Object> entry : bindingsRaw.entrySet()) {
                        Object val = entry.getValue();
                        if (val instanceof Number) {
                            mathBindings.put(entry.getKey(), ((Number) val).doubleValue());
                        } else if (val instanceof String && idToOutput.containsKey((String) val)) {
                            mathBindings.put(entry.getKey(), idToOutput.get((String) val));
                        }
                    }
                }
                return evaluateMathExpression(expression, tx, tz, mathBindings, fs, ix, iz) * amp;
            }

            case GRADIENT_CONSTRAINED_SINE: {
                double freqX = getDoubleParam(params, "freq_x", 0.01);
                double freqZ = getDoubleParam(params, "freq_z", 0.01);
                double primaryAmp = getDoubleParam(params, "primary_amp", 35.0);
                double secondaryAmp = getDoubleParam(params, "secondary_amp", 18.0);
                double secFreqX = getDoubleParam(params, "secondary_freq_x", 0.025);
                double secFreqZ = getDoubleParam(params, "secondary_freq_z", 0.018);
                double sensitivity = getDoubleParam(params, "gradient_sensitivity", 0.6);
                double halfFactor = getDoubleParam(params, "gradient_half_factor", 0.5);

                double rPrimarySine = Math.sin(tx * freqX + tz * freqZ);
                double rSecondarySine = Math.sin(tx * secFreqX - tz * secFreqZ);
                double rSineRaw = rPrimarySine * primaryAmp + rSecondarySine * secondaryAmp;

                double rGx = Math.sin((tx + 1) * freqX + tz * freqZ)
                           - Math.sin((tx - 1) * freqX + tz * freqZ);
                double rGz = Math.sin(tx * freqX + (tz + 1) * freqZ)
                           - Math.sin(tx * freqX + (tz - 1) * freqZ);
                double rGradMag = Math.sqrt(rGx * rGx + rGz * rGz);

                double halfSensitivity = sensitivity * halfFactor;
                double t = Math.max(0.0, Math.min(1.0,
                        (rGradMag - halfSensitivity) / (sensitivity - halfSensitivity)));
                double rSineWeight = 1.0 + t * t * (3.0 - 2.0 * t) * -0.7;
                return rSineRaw * rSineWeight * amp;
            }

            case CONTRIBUTING_POINT_DISTANCE: {
                double centerX = 0.0;
                double centerZ = 0.0;
                if (blend != null && blend.contributingPoints != null) {
                    double bestDistSq = Double.MAX_VALUE;
                    for (RegionController.PointWeight pw : blend.contributingPoints) {
                        double dx = tx - pw.point.getX();
                        double dz = tz - pw.point.getZ();
                        double distSq = dx * dx + dz * dz;
                        if (distSq < bestDistSq) {
                            bestDistSq = distSq;
                            centerX = pw.point.getX();
                            centerZ = pw.point.getZ();
                        }
                    }
                }
                double afDx = tx - centerX;
                double afDz = tz - centerZ;
                double afRawDist = Math.sqrt(afDx * afDx + afDz * afDz);
                double distPeriod = getDoubleParam(params, "distance_period", 200.0);
                double afPhase = afRawDist / distPeriod * Math.PI * 2.0;
                double afDist = (Math.sin(afPhase) * 0.5 + 0.5) * distPeriod;
                return afDist * amp;
            }

            case FM_SINE: {
                // Frequency-modulated sine: frequency is modulated by another noise signal, used for wind-eroded ridge textures
                double freqX = getDoubleParam(params, "freq_x", 0.01);
                double freqZ = getDoubleParam(params, "freq_z", 0.01);
                double freqModAmp = getDoubleParam(params, "freq_mod_amp", 0.0);
                String freqModInput = getStringParam(params, "freq_mod_input", null);
                double freqModValue = 0.0;
                if (freqModInput != null && idToOutput.containsKey(freqModInput)) {
                    freqModValue = idToOutput.get(freqModInput);
                }
                double phase = tx * freqX + tz * freqZ;
                double modulatedPhase = phase * (1.0 + freqModValue * freqModAmp);
                return Math.sin(modulatedPhase) * amp;
            }

            case ABS: {
                // Absolute value: takes the absolute value of another primitive's output, used for canyon depth etc.
                String inputRef = getStringParam(params, "input", null);
                double inputValue = 0.0;
                if (inputRef != null && idToOutput.containsKey(inputRef)) {
                    inputValue = idToOutput.get(inputRef);
                }
                return Math.abs(inputValue) * amp;
            }

            case NEGATE: {
                // Negation: takes the negative of another primitive's output, used for trench axis etc.
                String inputRef = getStringParam(params, "input", null);
                double inputValue = 0.0;
                if (inputRef != null && idToOutput.containsKey(inputRef)) {
                    inputValue = idToOutput.get(inputRef);
                }
                return -inputValue * amp;
            }

            case CONSTANT: {
                // Constant: returns a fixed value multiplied by amplitude, used for base offsets etc.
                double value = getDoubleParam(params, "value", 1.0);
                return value * amp;
            }

            default:
                throw new IllegalArgumentException("Unknown noise primitive: " + primitive.name);
        }
    }

    /**
     * Apply a combinator to combine primitive outputs.
     *
     * @param combinator   the combinator definition
     * @param idToOutput   map of primitive id to output
     * @param lastOutput   the output of the last primitive (fallback)
     * @return the combined value
     */
    private static double applyCombinator(TerrainFunctionSchema.Combinator combinator,
                                           Map<String, Double> idToOutput, double lastOutput) {
        if (combinator == null || combinator.type == null) {
            return lastOutput;
        }

        switch (combinator.type) {
            case COMBINATOR_ADD: {
                if (combinator.terms == null || combinator.terms.isEmpty()) {
                    WorldScape.LOGGER.warn("[World Scape] 'add' combinator has no terms, returning last output");
                    return lastOutput;
                }
                double sum = 0.0;
                for (String term : combinator.terms) {
                    Double val = idToOutput.get(term);
                    if (val != null) {
                        sum += val;
                    } else {
                        WorldScape.LOGGER.warn("[World Scape] 'add' combinator: unresolved term '{}'", term);
                    }
                }
                return sum;
            }

            case COMBINATOR_BLEND: {
                String aRef = combinator.a;
                String bRef = combinator.b;
                Double valA = aRef != null ? idToOutput.get(aRef) : null;
                Double valB = bRef != null ? idToOutput.get(bRef) : null;
                if (valA == null || valB == null) {
                    WorldScape.LOGGER.warn("[World Scape] 'blend' combinator: unresolved ref(s) a='{}' b='{}'",
                            aRef, bRef);
                    return lastOutput;
                }
                double w = Math.max(0.0, Math.min(1.0, combinator.weightA));
                return valA * w + valB * (1.0 - w);
            }

            case COMBINATOR_PRODUCT: {
                String aRef = combinator.a;
                String bRef = combinator.b;
                Double valA = aRef != null ? idToOutput.get(aRef) : null;
                Double valB = bRef != null ? idToOutput.get(bRef) : null;
                if (valA == null || valB == null) {
                    WorldScape.LOGGER.warn("[World Scape] 'product' combinator: unresolved ref(s) a='{}' b='{}'",
                            aRef, bRef);
                    return lastOutput;
                }
                return valA * valB;
            }

            case COMBINATOR_SCALE: {
                String source = combinator.source;
                Double val = source != null ? idToOutput.get(source) : null;
                if (val == null) {
                    WorldScape.LOGGER.warn("[World Scape] 'scale' combinator: unresolved source '{}'", source);
                    return lastOutput;
                }
                return val * combinator.factor;
            }

            default:
                WorldScape.LOGGER.error("[World Scape] Unknown combinator type: '{}'", combinator.type);
                return lastOutput;
        }
    }

    // ========================================================================
    // Math Expression Parser
    // ========================================================================

    /**
     * Recursive-descent math expression evaluator.
     * <p>
     * Supports: sin(x), cos(x), abs(x), sqrt(x), tanh(x), exp(x), log(x),
     * max(a,b), min(a,b), clamp(x,lo,hi), and +, -, *, /, ^ operators, numeric
     * literals, and named variable references from the bindings map.
     *
     * @param expression  the math expression string
     * @param tx          transformed X coordinate
     * @param tz          transformed Z coordinate
     * @param bindings    map of named variable values
     * @param fs          terrain field sampler for fallback
     * @param ix          integer X for fallback
     * @param iz          integer Z for fallback
     * @return the evaluated result
     */
    private static double evaluateMathExpression(String expression, double tx, double tz,
                                                  Map<String, Double> bindings,
                                                  TerrainFieldSampler fs, int ix, int iz) {
        if (expression == null || expression.trim().isEmpty()) {
            return 0.0;
        }
        try {
            MathParser parser = new MathParser(expression, bindings, tx, tz, fs, ix, iz);
            double result = parser.parseExpression();
            return Double.isFinite(result) ? result : 0.0;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse math expression: " + expression, e);
        }
    }

    /**
     * Simple recursive-descent parser for math expressions.
     * <p>
     * Grammar:
     * <pre>
     *   expression  -> term (('+' | '-') term)*
     *   term        -> unary (('*' | '/') unary)*
     *   unary       -> ('-' | '+')? power
     *   power       -> atom ('^' unary)?
     *   atom        -> number | function_call | identifier | '(' expression ')'
     *   function_call -> name '(' arglist ')'
     *   arglist     -> expression (',' expression)*
     * </pre>
     */
    private static class MathParser {
        private final String input;
        private final Map<String, Double> bindings;
        private final double tx;
        private final double tz;
        private final TerrainFieldSampler fs;
        private final int ix;
        private final int iz;
        private int pos;

        MathParser(String input, Map<String, Double> bindings, double tx, double tz,
                   TerrainFieldSampler fs, int ix, int iz) {
            this.input = input;
            this.bindings = bindings;
            this.tx = tx;
            this.tz = tz;
            this.fs = fs;
            this.ix = ix;
            this.iz = iz;
            this.pos = 0;
        }

        double parseExpression() {
            double left = parseTerm();
            while (pos < input.length()) {
                char op = input.charAt(pos);
                if (op == '+') {
                    pos++;
                    left += parseTerm();
                } else if (op == '-') {
                    pos++;
                    left -= parseTerm();
                } else {
                    break;
                }
            }
            return left;
        }

        private double parseTerm() {
            double left = parseUnary();
            while (pos < input.length()) {
                char op = input.charAt(pos);
                if (op == '*') {
                    pos++;
                    left *= parseUnary();
                } else if (op == '/') {
                    pos++;
                    double divisor = parseUnary();
                    left = divisor != 0.0 ? left / divisor : 0.0;
                } else {
                    break;
                }
            }
            return left;
        }

        private double parseUnary() {
            skipWhitespace();
            if (pos < input.length()) {
                char c = input.charAt(pos);
                if (c == '-') {
                    pos++;
                    return -parsePower();
                }
                if (c == '+') {
                    pos++;
                    return parsePower();
                }
            }
            return parsePower();
        }

        private double parsePower() {
            double base = parseAtom();
            skipWhitespace();
            if (pos < input.length() && input.charAt(pos) == '^') {
                pos++;
                double exponent = parseUnary();
                return Math.pow(base, exponent);
            }
            return base;
        }

        private double parseAtom() {
            skipWhitespace();
            if (pos >= input.length()) {
                throw new IllegalArgumentException("Unexpected end of expression");
            }

            char c = input.charAt(pos);

            if (c == '(') {
                pos++;
                double value = parseExpression();
                skipWhitespace();
                if (pos < input.length() && input.charAt(pos) == ')') {
                    pos++;
                } else {
                    throw new IllegalArgumentException("Missing closing parenthesis");
                }
                return value;
            }

            if (Character.isDigit(c) || c == '.') {
                return parseNumber();
            }

            if (Character.isLetter(c) || c == '_') {
                return parseIdentifierOrFunction();
            }

            throw new IllegalArgumentException("Unexpected character: '" + c + "' at position " + pos);
        }

        private double parseNumber() {
            int start = pos;
            while (pos < input.length() &&
                    (Character.isDigit(input.charAt(pos)) || input.charAt(pos) == '.')) {
                pos++;
            }

            String numStr = input.substring(start, pos);

            String intStr = numStr.replaceAll("\\.$", "");

            if (intStr.contains(".")) {
                return Double.parseDouble(intStr);
            }
            return (double)Integer.parseInt(intStr);
        }

        private double parseIdentifierOrFunction() {
            int start = pos;
            while (pos < input.length() &&
                    (Character.isLetterOrDigit(input.charAt(pos)) || input.charAt(pos) == '_')) {
                pos++;
            }
            String name = input.substring(start, pos);

            skipWhitespace();

            if (pos < input.length() && input.charAt(pos) == '(') {
                return parseFunctionCall(name);
            }

            return resolveVariable(name);
        }

        private double parseFunctionCall(String name) {
            pos++;
            skipWhitespace();

            java.util.ArrayList<Double> args = new java.util.ArrayList<>();
            if (pos < input.length() && input.charAt(pos) != ')') {
                args.add(parseExpression());
                skipWhitespace();
                while (pos < input.length() && input.charAt(pos) == ',') {
                    pos++;
                    skipWhitespace();
                    args.add(parseExpression());
                    skipWhitespace();
                }
            }

            if (pos < input.length() && input.charAt(pos) == ')') {
                pos++;
            } else {
                throw new IllegalArgumentException("Missing closing parenthesis for function " + name);
            }

            return evaluateFunction(name, args);
        }

        private double evaluateFunction(String name, java.util.ArrayList<Double> args) {
            switch (name) {
                case "sin":
                    checkArgCount(name, args, 1);
                    return Math.sin(args.get(0));

                case "cos":
                    checkArgCount(name, args, 1);
                    return Math.cos(args.get(0));

                case "abs":
                    checkArgCount(name, args, 1);
                    return Math.abs(args.get(0));

                case "sqrt":
                    checkArgCount(name, args, 1);
                    double val = args.get(0);
                    return val >= 0.0 ? Math.sqrt(val) : 0.0;

                case "tanh":
                    checkArgCount(name, args, 1);
                    return Math.tanh(args.get(0));

                case "exp":
                    checkArgCount(name, args, 1);
                    return Math.exp(args.get(0));

                case "log":
                    checkArgCount(name, args, 1);
                    double logVal = args.get(0);
                    return logVal > 0.0 ? Math.log(logVal) : 0.0;

                case "max":
                    if (args.size() < 2) {
                        throw new IllegalArgumentException("max() requires at least 2 arguments");
                    }
                    double maxVal = args.get(0);
                    for (int i = 1; i < args.size(); i++) {
                        maxVal = Math.max(maxVal, args.get(i));
                    }
                    return maxVal;

                case "min":
                    if (args.size() < 2) {
                        throw new IllegalArgumentException("min() requires at least 2 arguments");
                    }
                    double minVal = args.get(0);
                    for (int i = 1; i < args.size(); i++) {
                        minVal = Math.min(minVal, args.get(i));
                    }
                    return minVal;

                case "clamp":
                    checkArgCount(name, args, 3);
                    double x = args.get(0);
                    double lo = args.get(1);
                    double hi = args.get(2);
                    return Math.max(lo, Math.min(hi, x));

                default:
                    throw new IllegalArgumentException("Unknown function: " + name);
            }
        }

        private void checkArgCount(String funcName, java.util.ArrayList<Double> args, int expected) {
            if (args.size() != expected) {
                throw new IllegalArgumentException(
                        funcName + "() expects " + expected + " argument(s), got " + args.size());
            }
        }

        private double resolveVariable(String name) {
            if ("x".equals(name)) {
                return tx;
            }
            if ("z".equals(name)) {
                return tz;
            }

            if (bindings.containsKey(name)) {
                return bindings.get(name);
            }

            throw new IllegalArgumentException("Unresolved variable: '" + name + "'");
        }

        private void skipWhitespace() {
            while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
                pos++;
            }
        }
    }

    // ========================================================================
    // Parameter Extraction Helpers
    // ========================================================================

    /**
     * Extract a double parameter from the params map.
     *
     * @param params     the parameter map
     * @param key        the parameter key
     * @param defaultVal the default value if not found
     * @return the extracted double value
     */
    private static double getDoubleParam(Map<String, Object> params, String key, double defaultVal) {
        if (params == null) {
            return defaultVal;
        }
        Object val = params.get(key);
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        return defaultVal;
    }

    /**
     * Extract an integer parameter from the params map.
     *
     * @param params     the parameter map
     * @param key        the parameter key
     * @param defaultVal the default value if not found
     * @return the extracted int value
     */
    private static int getIntParam(Map<String, Object> params, String key, int defaultVal) {
        if (params == null) {
            return defaultVal;
        }
        Object val = params.get(key);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        return defaultVal;
    }

    /**
     * Extract a string parameter from the params map.
     *
     * @param params     the parameter map
     * @param key        the parameter key
     * @param defaultVal the default value if not found
     * @return the extracted string value
     */
    private static String getStringParam(Map<String, Object> params, String key, String defaultVal) {
        if (params == null) {
            return defaultVal;
        }
        Object val = params.get(key);
        if (val instanceof String) {
            return (String) val;
        }
        return defaultVal;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMapParam(Map<String, Object> params, String key) {
        if (params == null) {
            return null;
        }
        Object val = params.get(key);
        if (val instanceof Map) {
            return (Map<String, Object>) val;
        }
        return null;
    }
}