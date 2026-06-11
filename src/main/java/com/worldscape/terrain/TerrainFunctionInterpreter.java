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
 * 地形类型函数定义的解释器引擎。
 * <p>
 * Evaluates {@link TerrainFunctionSchema.FunctionDef} against
 * {@link TerrainFieldSampler} noise functions to produce a terrain height value.
 * 对 TerrainFunctionSchema.FunctionDef 进行求值，结合 TerrainFieldSampler
 * 噪声函数生成地形高度值。
 * <p>
 * Supports coordinate transforms, multiple noise primitives, combinators,
 * and final expression processing. Includes a built-in recursive-descent
 * math expression parser for the "math" noise primitive.
 * 支持坐标变换、多种噪声原语、组合器、最终表达式处理，内置
 * 递归下降数学表达式解析器用于 "math" 噪声原语。
 *
 * @author World Scape
 */
public final class TerrainFunctionInterpreter {

    private TerrainFunctionInterpreter() {
    }

    private static final ConcurrentHashMap<String, TerrainPrimitiveProvider> customPrimitives = new ConcurrentHashMap<>();

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
     * 在给定世界坐标处评估地形函数定义。
     *
     * @param def    the parsed function definition / 解析后的函数定义
     * @param worldX world X coordinate / 世界 X 坐标
     * @param worldZ world Z coordinate / 世界 Z 坐标
     * @param fs     the terrain field sampler for noise evaluation / 用于噪声评估的地形场采样器
     * @param blend  the terrain blend result from RegionController / 来自 RegionController 的地形混合结果
     * @return the computed terrain height / 计算出的地形高度
     */
    public static double evaluate(TerrainFunctionSchema.FunctionDef def, int worldX, int worldZ,
                                   TerrainFieldSampler fs, RegionController.TerrainBlendResult blend) {
        if (def == null || fs == null || blend == null) {
            WorldScape.LOGGER.error("[World Scape] evaluate() called with null parameters");
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
     * 应用坐标空间变换生成 (tx, tz)。
     *
     * @param ct     the coordinate transform definition (nullable) / 坐标变换定义（可为 null）
     * @param worldX original world X / 原始世界 X
     * @param worldZ original world Z / 原始世界 Z
     * @param fs     terrain field sampler / 地形场采样器
     * @return double[2] {tx, tz}
     */
    private static double[] applyCoordinateTransform(TerrainFunctionSchema.CoordinateTransform ct,
                                                      int worldX, int worldZ, TerrainFieldSampler fs) {
        if (ct == null || ct.type == null || "identity".equals(ct.type)) {
            return new double[]{worldX, worldZ};
        }

        switch (ct.type) {
            case "energy_stretched":
                return fs.getEnergyStretchedCoords(worldX, worldZ);

            case "scale": {
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
     * 评估单个噪声原语。
     *
     * @param primitive   the noise primitive definition / 噪声原语定义
     * @param ix         integer world X (transformed) / 整数世界 X（已变换）
     * @param iz         integer world Z (transformed) / 整数世界 Z（已变换）
     * @param tx         double world X (transformed, for sine/math) / double 世界 X（已变换，用于 sine/math）
     * @param tz         double world Z (transformed, for sine/math) / double 世界 Z（已变换，用于 sine/math）
     * @param fs         terrain field sampler / 地形场采样器
     * @param blend      terrain blend result / 地形混合结果
     * @param idToOutput map of already-evaluated primitive ids to their outputs / 已评估原语 ID 到输出的映射
     * @return the primitive's output value / 原语的输出值
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
            case "fbm": {
                int octaves = getIntParam(params, "octaves", 6);
                double gain = getDoubleParam(params, "gain", 0.5);
                return fs.sampleFbmCached(ix, iz, octaves, gain) * amp;
            }

            case "turbulence": {
                double strength = getDoubleParam(params, "strength", 1.0);
                return fs.sampleTurbulenceCached(ix, iz, strength) * amp;
            }

            case "domain_rotated": {
                double warpStrength = getDoubleParam(params, "warp_strength", 1.0);
                return fs.sampleDomainRotatedCached(ix, iz, warpStrength) * amp;
            }

            case "gaussian": {
                double sigma = getDoubleParam(params, "sigma", 100.0);
                // 支持高斯中心偏移：offset_x/offset_z 可以引用其他原语的输出值
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

            case "sigmoid": {
                double inputScale = getDoubleParam(params, "input_scale", 1.0);
                double input = getDoubleParam(params, "input", fs.sampleEnergy(ix, iz));
                return TerrainFieldSampler.sigmoid(input * inputScale) * amp;
            }

            case "tanh_scaled": {
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

            case "sine": {
                double freqX = getDoubleParam(params, "freq_x", 0.01);
                double freqZ = getDoubleParam(params, "freq_z", 0.01);
                double phaseOffset = getDoubleParam(params, "phase_offset", 0.0);
                return Math.sin(tx * freqX + tz * freqZ + phaseOffset) * amp;
            }

            case "gradient": {
                return fs.calculateGradient(ix, iz) * amp;
            }

            case "math": {
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

            case "gradient_constrained_sine": {
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

            case "contributing_point_distance": {
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

            case "fm_sine": {
                // 调频正弦波：频率被另一个噪声信号调制，用于风蚀脊等纹理
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

            case "abs": {
                // 绝对值：取另一个原语输出的绝对值，用于峡谷深度等
                // Absolute value: takes the absolute value of another primitive's output, used for canyon depth etc.
                String inputRef = getStringParam(params, "input", null);
                double inputValue = 0.0;
                if (inputRef != null && idToOutput.containsKey(inputRef)) {
                    inputValue = idToOutput.get(inputRef);
                }
                return Math.abs(inputValue) * amp;
            }

            case "negate": {
                // 取反：取另一个原语输出的负值，用于海沟轴等
                // Negation: takes the negative of another primitive's output, used for trench axis etc.
                String inputRef = getStringParam(params, "input", null);
                double inputValue = 0.0;
                if (inputRef != null && idToOutput.containsKey(inputRef)) {
                    inputValue = idToOutput.get(inputRef);
                }
                return -inputValue * amp;
            }

            case "constant": {
                // 常量：返回固定值乘以振幅，用于基础偏移等
                // Constant: returns a fixed value multiplied by amplitude, used for base offsets etc.
                double value = getDoubleParam(params, "value", 1.0);
                return value * amp;
            }

            default:
                WorldScape.LOGGER.error("[World Scape] Unknown noise primitive name: '{}'", primitive.name);
                return 0.0;
        }
    }

    /**
     * Apply a combinator to combine primitive outputs.
     * 应用组合器合并原语输出。
     *
     * @param combinator   the combinator definition / 组合器定义
     * @param idToOutput   map of primitive id to output / 原语 ID 到输出的映射
     * @param lastOutput   the output of the last primitive (fallback) / 最后一个原语的输出（后备）
     * @return the combined value / 组合后的值
     */
    private static double applyCombinator(TerrainFunctionSchema.Combinator combinator,
                                           Map<String, Double> idToOutput, double lastOutput) {
        if (combinator == null || combinator.type == null) {
            return lastOutput;
        }

        switch (combinator.type) {
            case "add": {
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

            case "blend": {
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

            case "product": {
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

            case "scale": {
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
    // Math Expression Parser / 数学表达式解析器
    // ========================================================================

    /**
     * Recursive-descent math expression evaluator.
     * 递归下降数学表达式求值器。
     * <p>
     * Supports: sin(x), cos(x), abs(x), sqrt(x), tanh(x), exp(x), log(x),
     * max(a,b), min(a,b), clamp(x,lo,hi), and +, -, *, /, ^ operators, numeric
     * literals, and named variable references from the bindings map.
     * 支持的函数和运算符如上所述，以及数值字面量和来自 bindings 映射的变量引用。
     *
     * @param expression  the math expression string / 数学表达式字符串
     * @param tx          transformed X coordinate / 变换后的 X 坐标
     * @param tz          transformed Z coordinate / 变换后的 Z 坐标
     * @param bindings    map of named variable values / 命名变量值的映射
     * @param fs          terrain field sampler for fallback / 备用的地形场采样器
     * @param ix          integer X for fallback / 备用的整数 X
     * @param iz          integer Z for fallback / 备用的整数 Z
     * @return the evaluated result / 求值结果
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
            WorldScape.LOGGER.error("[World Scape] Failed to parse math expression '{}': {}", expression, e.getMessage());
            return 0.0;
        }
    }

    /**
     * Simple recursive-descent parser for math expressions.
     * 用于数学表达式的简单递归下降解析器。
     * <p>
     * Grammar:
     * <pre>
     *   expression  → term (('+' | '-') term)*
     *   term        → unary (('*' | '/') unary)*
     *   unary       → ('-' | '+')? power
     *   power       → atom ('^' unary)?
     *   atom        → number | function_call | identifier | '(' expression ')'
     *   function_call → name '(' arglist ')'
     *   arglist     → expression (',' expression)*
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
    // Parameter Extraction Helpers / 参数提取辅助方法
    // ========================================================================

    /**
     * Extract a double parameter from the params map.
     * 从 params 映射中提取 double 参数。
     *
     * @param params     the parameter map / 参数映射
     * @param key        the parameter key / 参数键
     * @param defaultVal the default value if not found / 未找到时的默认值
     * @return the extracted double value / 提取的 double 值
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
     * 从 params 映射中提取整数参数。
     *
     * @param params     the parameter map / 参数映射
     * @param key        the parameter key / 参数键
     * @param defaultVal the default value if not found / 未找到时的默认值
     * @return the extracted int value / 提取的 int 值
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
     * 从 params 映射中提取字符串参数。
     *
     * @param params     the parameter map / 参数映射
     * @param key        the parameter key / 参数键
     * @param defaultVal the default value if not found / 未找到时的默认值
     * @return the extracted string value / 提取的字符串值
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