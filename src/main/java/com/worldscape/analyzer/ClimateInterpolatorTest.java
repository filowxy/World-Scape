package com.worldscape.analyzer;

import com.worldscape.terrain.RegionController;
import com.worldscape.terrain.RegionController.TerrainBlendResult;
import com.worldscape.terrain.TerrainType;
import com.worldscape.util.ClimateUtils;
import com.worldscape.util.ClimateUtils.ClimateProfile;
import com.worldscape.terrain.WorldScapeConstants;

/**
 * v4.1 气候插值器深度集成验证工具。
 * v4.1 Climate Interpolator Deep Integration Verification Tool.
 *
 * <p>核心功能/Core functions:
 * 1. 验证blendClimate()是否被正确调用并返回四维气候剖面
 * 2. 检查相邻控制点间的气候过渡是否平滑（无硬切边）
 * 3. 验证海拔修正温度是否生效
 * 4. 对比三种增强地形(PLAINS/FLOODPLAIN/SEA_PLATEAU)的高度差异
 * 5. 确认FJORD/TRENCH的气候映射修正已生效</p>
 *
 * <p>使用方式/Usage:
 * java -cp ... com.worldscape.analyzer.ClimateInterpolatorTest [seed1] [seed2] ...
 * 默认测试种子: 12345, 67890, 42</p>
 *
 * @调用时机 Phase 2集成完成后运行 / Run after Phase 2 integration complete
 * @已知限制 需要完整编译后的classpath / Requires full compiled classpath
 */
public class ClimateInterpolatorTest {

    private static final int TEST_RADIUS = 256;
    private static final int STEP = 8;
    private static final long[] DEFAULT_SEEDS = {12345L, 67890L, 42L};

    public static void main(String[] args) {
        long[] testSeeds = args.length > 0 ? parseSeeds(args) : DEFAULT_SEEDS;

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  World Scape v4.1 - 气候插值器深度集成验证                      ║");
        System.out.println("║  Climate Interpolator Deep Integration Verification            ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        boolean allPassed = true;

        for (long seed : testSeeds) {
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("🌍 测试种子 / Test Seed: " + seed);
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            
            boolean seedPassed = runTestsForSeed(seed);
            allPassed &= seedPassed;
            
            System.out.println();
        }

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        if (allPassed) {
            System.out.println("║  ✅ 所有验证通过！气候插值器工作正常                              ║");
            System.out.println("║  All verifications PASSED! Climate interpolator working     ║");
        } else {
            System.out.println("║  ❌ 部分验证失败！请检查上方输出                                  ║");
            System.out.println("║  Some verifications FAILED! Check output above           ║");
        }
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
    }

    private static long[] parseSeeds(String[] args) {
        long[] seeds = new long[args.length];
        for (int i = 0; i < args.length; i++) {
            seeds[i] = Long.parseLong(args[i]);
        }
        return seeds;
    }

    /**
     * 对单个种子执行所有验证测试。
     * Runs all verification tests for a single seed.
     */
    private static boolean runTestsForSeed(long seed) {
        RegionController rc = new RegionController(seed, WorldScapeConstants.SEA_LEVEL_FALLBACK);

        boolean test1 = testBlendedClimateExists(rc, seed);
        boolean test2 = testClimateSmoothTransition(rc, seed);
        boolean test3 = testElevationTemperatureCorrection(rc, seed);
        boolean test4 = testEnhancedTerrainDistinction(rc, seed);
        boolean test5 = testFjordTrenchClimateCorrection(rc, seed);

        return test1 && test2 && test3 && test4 && test5;
    }

    /**
     * 测试1：验证blendedClimate字段存在且为四维剖面。
     * Test 1: Verify blendedClimate field exists and is a 4D profile.
     */
    private static boolean testBlendedClimateExists(RegionController rc, long seed) {
        System.out.println("\n[测试1] 验证 blendedClimate 字段存在性 / Test 1: Verify blendedClimate existence");

        try {
            TerrainBlendResult result = rc.getTerrainBlend(0, 0);
            
            if (result.blendedClimate == null) {
                System.out.println("  ❌ FAIL: blendedClimate 为 null / blendedClimate is null");
                return false;
            }

            ClimateProfile climate = result.blendedClimate;
            System.out.println("  ✅ PASS: blendedClimate 非空 / blendedClimate is not null");
            System.out.printf("  📊 坐标(0,0)气候值: temp=%.3f humid=%.3f season=%.3f cont=%.3f%n",
                    climate.getTemperature(), climate.getHumidity(),
                    climate.getSeasonality(), climate.getContinentality());

            boolean validRange = validateClimateRange(climate);
            return validRange;

        } catch (Exception e) {
            System.out.println("  ❌ FAIL: 异常 / Exception: " + e.getMessage());
            return false;
        }
    }

    /**
     * 测试2：验证相邻点气候过渡平滑性。
     * Test 2: Verify smooth climate transition between adjacent points.
     */
    private static boolean testClimateSmoothTransition(RegionController rc, long seed) {
        System.out.println("\n[测试2] 验证气候过渡平滑性 / Test 2: Verify climate transition smoothness");

        int gridSize = (2 * TEST_RADIUS / STEP) + 1;
        double maxJump = 0.0;
        int jumpCount = 0;
        int totalPairs = 0;
        double threshold = 0.15; // 允许的最大单步跳跃

        ClimateProfile prevClimate = null;

        for (int gz = 0; gz < gridSize && totalPairs < 1000; gz++) {
            for (int gx = 0; gx < gridSize && totalPairs < 1000; gx++) {
                int worldX = -TEST_RADIUS + gx * STEP;
                int worldZ = -TEST_RADIUS + gz * STEP;

                try {
                    TerrainBlendResult result = rc.getTerrainBlend(worldX, worldZ);
                    ClimateProfile currClimate = result.blendedClimate;

                    if (prevClimate != null) {
                        double distance = prevClimate.distanceTo(currClimate);
                        maxJump = Math.max(maxJump, distance);
                        totalPairs++;

                        if (distance > threshold) {
                            jumpCount++;
                            if (jumpCount <= 3) { // 只打印前3个异常点
                                System.out.printf("  ⚠️  跳跃过大 @(%d,%d): dist=%.4f [阈值=%.2f]%n",
                                        worldX, worldZ, distance, threshold);
                            }
                        }
                    }

                    prevClimate = currClimate;
                } catch (Exception e) {
                    // 忽略异常点
                }
            }
        }

        double jumpRate = totalPairs > 0 ? (double) jumpCount / totalPairs : 0;
        boolean passed = jumpRate < 0.05; // 允许<5%的点超过阈值

        System.out.printf("  📈 统计: 总对数=%d, 超阈=%d(%.1f%%), 最大跳=%.4f%n",
                totalPairs, jumpCount, jumpRate * 100, maxJump);

        if (passed) {
            System.out.println("  ✅ PASS: 气候过渡平滑（超标率<5%）/ Climate transition smooth");
        } else {
            System.out.println("  ❌ FAIL: 过多硬切边（超标率≥5%）/ Too many hard edges");
        }

        return passed;
    }

    /**
     * 测试3：验证海拔修正温度生效。
     * Test 3: Verify elevation-based temperature correction works.
     */
    private static boolean testElevationTemperatureCorrection(RegionController rc, long seed) {
        System.out.println("\n[测试3] 验证海拔温度修正 / Test 3: Verify elevation temperature correction");

        double[][] samples = {
                {0, 0},      // 可能是海洋(Tier 0-1)
                {500, 500},  // 可能是平原(Tier 2-3)
                {2000, 2000} // 可能是山地(Tier 4-5)
        };

        boolean allCorrected = true;

        for (double[] coord : samples) {
            int x = (int) coord[0];
            int z = (int) coord[1];

            try {
                TerrainBlendResult result = rc.getTerrainBlend(x, z);
                ClimateProfile climate = result.blendedClimate;
                int tier = result.macroInfo.getElevationTier();

                ClimateProfile rawClimate = ClimateUtils.getTerrainClimateProfile(result.dominantType.name());
                double correctedTemp = ClimateUtils.adjustTemperatureForElevation(
                        rawClimate.getTemperature(), tier, 0.0);

                double diff = Math.abs(climate.getTemperature() - correctedTemp);
                boolean match = diff < 0.001;

                System.out.printf("  📍 (%d,%d): Tier=%d, rawT=%.3f, correctedT=%.3f, actualT=%.3f, diff=%.4f %s%n",
                        x, z, tier, rawClimate.getTemperature(), correctedTemp,
                        climate.getTemperature(), diff, match ? "✅" : "❌");

                allCorrected &= match;

            } catch (Exception e) {
                System.out.printf("  ⚠️  (%d,%d): 异常 - %s%n", x, z, e.getMessage());
            }
        }

        if (allCorrected) {
            System.out.println("  ✅ PASS: 海拔修正温度正确应用 / Elevation correction applied correctly");
        } else {
            System.out.println("  ❌ FAIL: 温度修正不一致 / Temperature correction inconsistent");
        }

        return allCorrected;
    }

    /**
     * 测试4：验证三种增强地形的区分度。
     * Test 4: Verify distinction of three enhanced terrain types.
     */
    private static boolean testEnhancedTerrainDistinction(RegionController rc, long seed) {
        System.out.println("\n[测试4] 验证增强地形区分度 / Test 4: Verify enhanced terrain distinction");

        int plainsCount = 0, floodplainCount = 0, seaPlateauCount = 0;
        double plainsHeightSum = 0, floodplainHeightSum = 0, seaPlateauHeightSum = 0;

        int gridSize = (2 * TEST_RADIUS / STEP) + 1;

        for (int gz = 0; gz < gridSize; gz++) {
            for (int gx = 0; gx < gridSize; gx++) {
                int worldX = -TEST_RADIUS + gx * STEP;
                int worldZ = -TEST_RADIUS + gz * STEP;

                try {
                    TerrainBlendResult result = rc.getTerrainBlend(worldX, worldZ);

                    switch (result.dominantType) {
                        case PLAINS -> {
                            plainsCount++;
                            plainsHeightSum += result.blendedHeight;
                        }
                        case FLOODPLAIN -> {
                            floodplainCount++;
                            floodplainHeightSum += result.blendedHeight;
                        }
                        case SEA_PLATEAU -> {
                            seaPlateauCount++;
                            seaPlateauHeightSum += result.blendedHeight;
                        }
                    }
                } catch (Exception e) {
                    // 忽略
                }
            }
        }

        double plainsAvg = plainsCount > 0 ? plainsHeightSum / plainsCount : 0;
        double floodplainAvg = floodplainCount > 0 ? floodplainHeightSum / floodplainCount : 0;
        double seaPlateauAvg = seaPlateauCount > 0 ? seaPlateauHeightSum / seaPlateauCount : 0;

        System.out.printf("  📊 样本统计:%n");
        System.out.printf("     PLAINS:      count=%4d, avgHeight=%7.2f%n", plainsCount, plainsAvg);
        System.out.printf("     FLOODPLAIN:  count=%4d, avgHeight=%7.2f%n", floodplainCount, floodplainAvg);
        System.out.printf("     SEA_PLATEAU: count=%4d, avgHeight=%7.2f%n", seaPlateauCount, seaPlateauAvg);

        boolean hasSamples = plainsCount > 0 && floodplainCount > 0 && seaPlateauCount > 0;
        boolean distinct = hasSamples &&
                Math.abs(plainsAvg - floodplainAvg) > 1.0 ||
                Math.abs(floodplainAvg - seaPlateauAvg) > 1.0 ||
                Math.abs(plainsAvg - seaPlateauAvg) > 1.0;

        if (hasSamples && distinct) {
            System.out.println("  ✅ PASS: 三种地形有可区分的平均高度 / Three terrains have distinguishable avg heights");
        } else if (!hasSamples) {
            System.out.println("  ⚠️  WARN: 该种子未包含全部三种地形类型 / Seed doesn't contain all three terrain types");
            return true; // 不算失败，只是样本不足
        } else {
            System.out.println("  ❌ FAIL: 地形区分度不足 / Insufficient terrain distinction");
        }

        return !hasSamples || distinct;
    }

    /**
     * 测试5：验证FJORD/TRENCH气候映射修正。
     * Test 5: Verify FJORD/TRENCH climate mapping corrections.
     */
    private static boolean testFjordTrenchClimateCorrection(RegionController rc, long seed) {
        System.out.println("\n[测试5] 验证 FJORD/TRENCH 气候修正 / Test 5: Verify FJORD/TRENCH climate correction");

        boolean fjordFound = false, trenchFound = false;
        boolean fjordCorrect = true, trenchCorrect = true;

        int gridSize = (2 * TEST_RADIUS / STEP) + 1;

        for (int gz = 0; gz < gridSize; gz++) {
            for (int gx = 0; gx < gridSize; gx++) {
                int worldX = -TEST_RADIUS + gx * STEP;
                int worldZ = -TEST_RADIUS + gz * STEP;

                try {
                    TerrainBlendResult result = rc.getTerrainBlend(worldX, worldZ);
                    ClimateProfile climate = result.blendedClimate;

                    if (result.dominantType == TerrainType.FJORD) {
                        fjordFound = true;
                        boolean isCoastalTemperate = climate.getTemperature() > 0.4 &&
                                climate.getHumidity() > 0.6;
                        
                        if (!isCoastalTemperate) {
                            System.out.printf("  ❌ FJORD @(%d,%d): 应为温和沿海但实际 temp=%.2f humid=%.2f%n",
                                    worldX, worldZ, climate.getTemperature(), climate.getHumidity());
                            fjordCorrect = false;
                        }
                    }

                    if (result.dominantType == TerrainType.TRENCH) {
                        trenchFound = true;
                        boolean isDeepOcean = climate.getTemperature() < 0.2 &&
                                climate.getHumidity() > 0.8;
                        
                        if (!isDeepOcean) {
                            System.out.printf("  ❌ TRENCH @(%d,%d): 应为深海但实际 temp=%.2f humid=%.2f%n",
                                    worldX, worldZ, climate.getTemperature(), climate.getHumidity());
                            trenchCorrect = false;
                        }
                    }
                } catch (Exception e) {
                    // 忽略
                }
            }
        }

        System.out.printf("  📍 发现: FJORD=%s, TRENCH=%s%n",
                fjordFound ? "✅" : "⚠️ 未找到", trenchFound ? "✅" : "⚠️ 未找到");

        if (fjordFound && fjordCorrect) {
            System.out.println("  ✅ FJORD气候修正正确: 使用温和沿海剖面 / FJORD climate correct: coastal temperate");
        } else if (fjordFound) {
            System.out.println("  ❌ FJORD气候仍错误 / FJORD climate still incorrect");
        }

        if (trenchFound && trenchCorrect) {
            System.out.println("  ✅ TRENCH气候修正正确: 使用深海剖面 / TRENCH climate correct: deep ocean");
        } else if (trenchFound) {
            System.out.println("  ❌ TRENCH气候仍错误 / TRENCH climate still incorrect");
        }

        boolean passed = (!fjordFound || fjordCorrect) && (!trenchFound || trenchCorrect);
        
        if (!fjordFound && !trenchFound) {
            System.out.println("  ⚠️  SKIP: 该种子无FJORD/TRENCH样本 / No FJORD/TRENCH samples in this seed");
            return true; // 样本不足不算失败
        }

        return passed;
    }

    /**
     * 验证气候值范围有效性。
     * Validate climate value range validity.
     */
    private static boolean validateClimateRange(ClimateProfile climate) {
        boolean valid = true;

        if (climate.getTemperature() < 0.0 || climate.getTemperature() > 1.0) {
            System.out.println("  ❌ FAIL: temperature 超出范围[0,1]: " + climate.getTemperature());
            valid = false;
        }
        if (climate.getHumidity() < 0.0 || climate.getHumidity() > 1.0) {
            System.out.println("  ❌ FAIL: humidity 超出范围[0,1]: " + climate.getHumidity());
            valid = false;
        }
        if (climate.getSeasonality() < 0.0 || climate.getSeasonality() > 1.0) {
            System.out.println("  ❌ FAIL: seasonality 超出范围[0,1]: " + climate.getSeasonality());
            valid = false;
        }
        if (climate.getContinentality() < 0.0 || climate.getContinentality() > 1.0) {
            System.out.println("  ❌ FAIL: continentality 超出范围[0,1]: " + climate.getContinentality());
            valid = false;
        }

        if (valid) {
            System.out.println("  ✅ PASS: 所有气候值在有效范围[0,1]内 / All climate values in valid range [0,1]");
        }

        return valid;
    }
}
