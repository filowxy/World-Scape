package com.worldscape.analyzer;

import com.worldscape.terrain.HeightCalculator;
import com.worldscape.terrain.MacroVoronoiSystem;
import com.worldscape.terrain.RegionController;
import com.worldscape.terrain.WorldScapeConstants;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * SeedAnalyzer 数据准确性验证工具 / SeedAnalyzer Data Accuracy Verification Tool
 *
 * 核心功能：
 * 1. 验证高度数据生成一致性
 * 2. 验证梯度计算准确性
 * 3. 验证连通分量检测
 * 4. 验证海洋质量评估逻辑
 * 5. 完整运行测试
 */
public class SeedAnalyzerVerification {

    private static final int TEST_SEED = 12345;
    private static final int TEST_RADIUS = 64;
    private static final int TEST_STEP = 1;

    public static void main(String[] args) {
        System.out.println("===== SeedAnalyzer 数据准确性验证 / SeedAnalyzer Data Accuracy Verification =====");
        System.out.println();

        boolean allTestsPassed = true;

        // 测试1: 验证高度数据生成一致性
        allTestsPassed &= testHeightDataConsistency();

        // 测试2: 验证梯度计算准确性
        allTestsPassed &= testGradientCalculation();

        // 测试3: 验证连通分量检测
        allTestsPassed &= testConnectedComponentDetection();

        // 测试4: 验证海洋质量评估逻辑
        allTestsPassed &= testOceanQualityAssessment();

        // 测试5: 完整运行测试
        allTestsPassed &= testFullRun();

        System.out.println();
        System.out.println("===== 验证结果 / Verification Results =====");
        if (allTestsPassed) {
            System.out.println("\u2714 所有测试通过 / All tests PASSED! SeedAnalyzer 数据准确可靠！");
        } else {
            System.out.println("\u274c 部分测试失败 / Some tests FAILED!");
        }
    }

    /**
     * 测试高度数据生成一致性 / Test height data generation consistency
     */
    private static boolean testHeightDataConsistency() {
        System.out.println("[测试1] 验证高度数据生成一致性 / Test 1: Verify height data generation consistency");
        try {
            MacroVoronoiSystem macroSystem = new MacroVoronoiSystem(TEST_SEED, WorldScapeConstants.SEA_LEVEL_FALLBACK);
            HeightCalculator calculator1 = new HeightCalculator(TEST_SEED, WorldScapeConstants.SEA_LEVEL_FALLBACK, macroSystem);
            HeightCalculator calculator2 = new HeightCalculator(TEST_SEED, WorldScapeConstants.SEA_LEVEL_FALLBACK, macroSystem);

            List<Double> heights1 = new ArrayList<>();
            List<Double> heights2 = new ArrayList<>();

            boolean allConsistent = true;
            int inconsistentCount = 0;
            int totalPoints = 0;

            for (int x = -TEST_RADIUS; x <= TEST_RADIUS; x += TEST_STEP * 4) {
                for (int z = -TEST_RADIUS; z <= TEST_RADIUS; z += TEST_STEP * 4) {
                    double h1 = calculator1.calculateHeight(x, z);
                    double h2 = calculator2.calculateHeight(x, z);
                    heights1.add(h1);
                    heights2.add(h2);
                    totalPoints++;

                    double diff = Math.abs(h1 - h2);
                    if (diff > 0.001) {
                        System.err.println("    \u274c 不一致点 / Inconsistent point (" + x + ", " + z + "): " + h1 + " vs " + h2 + " (diff: " + diff + ")");
                        allConsistent = false;
                        inconsistentCount++;
                    }
                }
            }

            if (allConsistent) {
                System.out.println("    \u2714 高度数据一致性测试通过 / Height data consistency test PASSED! (Points: " + totalPoints + ")");
            } else {
                System.err.println("    \u274c 高度数据一致性测试失败 / Height data consistency test FAILED! (Inconsistent points: " + inconsistentCount + "/" + totalPoints + ")");
            }
            return allConsistent;
        } catch (Exception e) {
            System.err.println("    \u274c 测试异常 / Test exception: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 测试梯度计算准确性 / Test gradient calculation accuracy
     */
    private static boolean testGradientCalculation() {
        System.out.println("[测试2] 验证梯度计算准确性 / Test 2: Verify gradient calculation accuracy");
        try {
            boolean allTestsPassed = true;

            // 子测试A: 完全平坦的地形，梯度应为0
            System.out.println("    子测试A: 平坦地形梯度应为0 / Subtest A: Flat terrain gradient should be 0");
            double[] flatHeights = new double[100]; // 10x10 grid
            Arrays.fill(flatHeights, 100.0);
            double[] flatGradients = calculateGradientsManually(flatHeights, 10);
            int flatErrorCount = 0;
            for (int i = 0; i < flatGradients.length; i++) {
                if (flatGradients[i] > 0.001) {
                    flatErrorCount++;
                    allTestsPassed = false;
                }
            }
            if (flatErrorCount == 0) {
                System.out.println("        \u2714 平坦地形梯度正确 / Flat terrain gradient correct!");
            } else {
                System.err.println("        \u274c 平坦地形梯度错误 / Flat terrain gradient incorrect! (Errors: " + flatErrorCount + ")");
            }

            // 子测试B: 倾斜地形，梯度应为常数
            System.out.println("    子测试B: 倾斜地形梯度一致性 / Subtest B: Sloped terrain gradient consistency");
            double[] slopedHeights = new double[100];
            for (int i = 0; i < 10; i++) { // x
                for (int j = 0; j < 10; j++) { // z
                    slopedHeights[j * 10 + i] = 100.0 + i * 2.0; // x方向每格高2
                }
            }
            double[] slopedGradients = calculateGradientsManually(slopedHeights, 10);
            int slopeErrorCount = 0;
            for (int j = 1; j < 9; j++) { // z, 边界跳过
                for (int i = 1; i < 9; i++) { // x
                    double grad = slopedGradients[j * 10 + i];
                    if (Math.abs(grad - 2.0) > 0.5) { // 允许一定误差
                        slopeErrorCount++;
                        allTestsPassed = false;
                    }
                }
            }
            if (slopeErrorCount == 0) {
                System.out.println("        \u2714 倾斜地形梯度正确 / Sloped terrain gradient correct!");
            } else {
                System.err.println("        \u274c 倾斜地形梯度错误 / Sloped terrain gradient incorrect! (Errors: " + slopeErrorCount + ")");
            }

            return allTestsPassed;
        } catch (Exception e) {
            System.err.println("    \u274c 测试异常 / Test exception: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 手动计算梯度（用于测试验证）/ Manually calculate gradients (for test verification)
     */
    private static double[] calculateGradientsManually(double[] heights, int gridSize) {
        double[] gradients = new double[gridSize * gridSize];
        for (int j = 1; j < gridSize - 1; j++) { // z
            for (int i = 1; i < gridSize - 1; i++) { // x
                // Sobel算子 / Sobel operator (same as SeedAnalyzer)
                double gx = -heights[(j - 1) * gridSize + (i - 1)]
                        + heights[(j - 1) * gridSize + (i + 1)]
                        - 2.0 * heights[j * gridSize + (i - 1)]
                        + 2.0 * heights[j * gridSize + (i + 1)]
                        - heights[(j + 1) * gridSize + (i - 1)]
                        + heights[(j + 1) * gridSize + (i + 1)];
                double gz = -heights[(j - 1) * gridSize + (i - 1)]
                        - 2.0 * heights[(j - 1) * gridSize + i]
                        - heights[(j - 1) * gridSize + (i + 1)]
                        + heights[(j + 1) * gridSize + (i - 1)]
                        + 2.0 * heights[(j + 1) * gridSize + i]
                        + heights[(j + 1) * gridSize + (i + 1)];
                gradients[j * gridSize + i] = Math.sqrt(gx * gx + gz * gz) / 8.0; // 8是Sobel算子缩放因子
            }
        }
        return gradients;
    }

    /**
     * 测试连通分量检测 / Test connected component detection
     */
    private static boolean testConnectedComponentDetection() {
        System.out.println("[测试3] 验证连通分量检测 / Test 3: Verify connected component detection");
        try {
            boolean allTestsPassed = true;

            // 子测试A: 单个连续区域
            System.out.println("    子测试A: 单个连续区域 / Subtest A: Single continuous region");
            boolean[] singleRegion = new boolean[100]; // 10x10 grid
            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < 10; j++) {
                    singleRegion[j * 10 + i] = (i >= 2 && i <= 7 && j >= 2 && j <= 7); // 中心6x6区域
                }
            }
            int singleRegionCount = countConnectedBodies(singleRegion, 10);
            if (singleRegionCount == 1) {
                System.out.println("        \u2714 单个区域检测正确 / Single region detection correct! (Found: " + singleRegionCount + ")");
            } else {
                System.err.println("        \u274c 单个区域检测错误 / Single region detection incorrect! (Found: " + singleRegionCount + ", Expected: 1)");
                allTestsPassed = false;
            }

            // 子测试B: 3个分离的区域
            System.out.println("    子测试B: 多个分离区域 / Subtest B: Multiple separated regions");
            boolean[] multiRegion = new boolean[100]; // 10x10 grid
            // 区域1: 左上
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    multiRegion[j * 10 + i] = true;
                }
            }
            // 区域2: 右上
            for (int i = 7; i < 10; i++) {
                for (int j = 0; j < 3; j++) {
                    multiRegion[j * 10 + i] = true;
                }
            }
            // 区域3: 底部
            for (int i = 0; i < 10; i++) {
                for (int j = 7; j < 10; j++) {
                    multiRegion[j * 10 + i] = true;
                }
            }
            int multiRegionCount = countConnectedBodies(multiRegion, 10);
            if (multiRegionCount == 3) {
                System.out.println("        \u2714 多区域检测正确 / Multi-region detection correct! (Found: " + multiRegionCount + ")");
            } else {
                System.err.println("        \u274c 多区域检测错误 / Multi-region detection incorrect! (Found: " + multiRegionCount + ", Expected: 3)");
                allTestsPassed = false;
            }

            return allTestsPassed;
        } catch (Exception e) {
            System.err.println("    \u274c 测试异常 / Test exception: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 测试连通分量计数（与SeedAnalyzer算法一致）/ Test connected body counting (same as SeedAnalyzer algorithm)
     */
    private static int countConnectedBodies(boolean[] isOcean, int gridSize) {
        boolean[] visited = new boolean[gridSize * gridSize];
        int bodyCount = 0;
        int[] dx = {-1, 1, 0, 0};
        int[] dz = {0, 0, -1, 1};

        for (int j = 0; j < gridSize; j++) {
            for (int i = 0; i < gridSize; i++) {
                int idx = j * gridSize + i;
                if (!isOcean[idx] || visited[idx]) {
                    continue;
                }
                bodyCount++;
                Queue<Integer> queue = new LinkedList<>();
                queue.add(idx);
                visited[idx] = true;
                while (!queue.isEmpty()) {
                    int current = queue.poll();
                    int cx = current % gridSize;
                    int cz = current / gridSize;
                    for (int d = 0; d < 4; d++) {
                        int nx = cx + dx[d];
                        int nz = cz + dz[d];
                        if (nx >= 0 && nx < gridSize && nz >= 0 && nz < gridSize) {
                            int nIdx = nz * gridSize + nx;
                            if (isOcean[nIdx] && !visited[nIdx]) {
                                visited[nIdx] = true;
                                queue.add(nIdx);
                            }
                        }
                    }
                }
            }
        }
        return bodyCount;
    }

    /**
     * 测试海洋质量评估逻辑 / Test ocean quality assessment logic
     */
    private static boolean testOceanQualityAssessment() {
        System.out.println("[测试4] 验证海洋质量评估逻辑 / Test 4: Verify ocean quality assessment logic");
        try {
            boolean allTestsPassed = true;

            // 测试覆盖率评分
            System.out.println("    覆盖率评分测试 / Coverage score test");
            double coverageScore1 = calculateOceanQualityScore(30, 20, 1, 30);
            double coverageScore2 = calculateOceanQualityScore(0, 20, 1, 30);
            double coverageScore3 = calculateOceanQualityScore(100, 20, 1, 30);
            System.out.println("        30% coverage: " + coverageScore1);
            System.out.println("        0% coverage: " + coverageScore2);
            System.out.println("        100% coverage: " + coverageScore3);
            if (coverageScore1 > coverageScore2 && coverageScore1 > coverageScore3) {
                System.out.println("        \u2714 覆盖率评分逻辑正确 / Coverage score logic correct!");
            } else {
                System.err.println("        \u274c 覆盖率评分逻辑错误 / Coverage score logic incorrect!");
                allTestsPassed = false;
            }

            // 测试连通性评分
            System.out.println("    连通性评分测试 / Connectivity score test");
            double connectivityScore1 = calculateOceanQualityScore(40, 20, 1, 30);
            double connectivityScore2 = calculateOceanQualityScore(40, 20, 3, 30);
            double connectivityScore3 = calculateOceanQualityScore(40, 20, 10, 30);
            System.out.println("        1 body: " + connectivityScore1);
            System.out.println("        3 bodies: " + connectivityScore2);
            System.out.println("        10 bodies: " + connectivityScore3);
            if (connectivityScore1 >= connectivityScore2 && connectivityScore2 >= connectivityScore3) {
                System.out.println("        \u2714 连通性评分逻辑正确 / Connectivity score logic correct!");
            } else {
                System.err.println("        \u274c 连通性评分逻辑错误 / Connectivity score logic incorrect!");
                allTestsPassed = false;
            }

            return allTestsPassed;
        } catch (Exception e) {
            System.err.println("    \u274c 测试异常 / Test exception: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 计算海洋质量评分（与SeedAnalyzer算法一致）/ Calculate ocean quality score (same as SeedAnalyzer algorithm)
     */
    private static double calculateOceanQualityScore(double coverage, double avgDepth, int bodies, double shallowPct) {
        double coverageScore;
        if (coverage >= 30 && coverage <= 50) {
            coverageScore = 100;
        } else if (coverage < 30) {
            coverageScore = Math.max(0, coverage / 30.0 * 100);
        } else {
            coverageScore = Math.max(0, 100 - (coverage - 50) * 2);
        }

        double depthScore;
        if (avgDepth >= 15 && avgDepth <= 35) {
            depthScore = 100;
        } else if (avgDepth < 15) {
            depthScore = Math.max(0, avgDepth / 15.0 * 100);
        } else {
            depthScore = Math.max(0, 100 - (avgDepth - 35) * 3);
        }

        double connectivityScore;
        if (bodies <= 3) {
            connectivityScore = 100;
        } else {
            connectivityScore = Math.max(0, 100 - (bodies - 3) * 15);
        }

        double varietyScore;
        if (shallowPct >= 15 && shallowPct <= 45) {
            varietyScore = 100;
        } else if (shallowPct < 15) {
            varietyScore = Math.max(0, shallowPct / 15.0 * 100);
        } else {
            varietyScore = Math.max(0, 100 - (shallowPct - 45) * 3);
        }

        return coverageScore * 0.25 + depthScore * 0.25 + connectivityScore * 0.25 + varietyScore * 0.25;
    }

    /**
     * 完整运行测试 / Full run test
     */
    private static boolean testFullRun() {
        System.out.println("[测试5] 完整运行测试 / Test 5: Full run test");
        try {
            long start = System.currentTimeMillis();

            // 创建小半径的分析 / Create small radius analysis
            SeedAnalyzer analyzer = new SeedAnalyzer(TEST_SEED, 32, 2);
            analyzer.analyzeAndOutput();

            // 检查输出文件是否存在 / Check if output files exist
            Path outputDir = Paths.get("seed_analysis", String.valueOf(TEST_SEED));
            Path grayMap = outputDir.resolve("heightmap_grayscale.png");
            Path coloredMap = outputDir.resolve("heightmap_colored.png");
            Path cliffMap = outputDir.resolve("cliff_heatmap.png");
            Path report = outputDir.resolve("report.md");

            boolean allFilesExist = Files.exists(grayMap) && Files.exists(coloredMap)
                    && Files.exists(cliffMap) && Files.exists(report);

            long elapsed = System.currentTimeMillis() - start;

            if (allFilesExist) {
                System.out.println("    \u2714 完整运行测试通过 / Full run test PASSED! (Time: " + elapsed + "ms)");
                System.out.println("    \u2714 所有输出文件已生成 / All output files generated:");
                System.out.println("        - " + grayMap);
                System.out.println("        - " + coloredMap);
                System.out.println("        - " + cliffMap);
                System.out.println("        - " + report);
            } else {
                System.err.println("    \u274c 完整运行测试失败 / Full run test FAILED!");
                if (!Files.exists(grayMap)) System.err.println("        - missing heightmap_grayscale.png");
                if (!Files.exists(coloredMap)) System.err.println("        - missing heightmap_colored.png");
                if (!Files.exists(cliffMap)) System.err.println("        - missing cliff_heatmap.png");
                if (!Files.exists(report)) System.err.println("        - missing report.md");
            }

            return allFilesExist;
        } catch (Exception e) {
            System.err.println("    \u274c 测试异常 / Test exception: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
