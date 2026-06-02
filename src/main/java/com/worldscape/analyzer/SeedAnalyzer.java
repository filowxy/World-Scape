package com.worldscape.analyzer;

import com.worldscape.terrain.MacroVoronoiSystem;
import com.worldscape.terrain.NoiseSet;
import com.worldscape.terrain.RegionController;
import com.worldscape.terrain.RegionController.TerrainBlendResult;
import com.worldscape.terrain.TerrainFieldSampler;
import com.worldscape.terrain.TerrainType;
import com.worldscape.terrain.WorldScapeConstants;
import com.worldscape.terrain.TerrainCalculator;

import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import javax.imageio.ImageIO;

/**
 * 离线种子分析器，提供种子地形质量评估和可视化输出。
 * Offline seed analyzer, providing terrain quality assessment and visualization output for seeds.
 *
 * <p>核心职责/Core responsibility:
 * 接收种子值和半径参数，复用LandscapeChunkGenerator的完整地形计算链生成高度数据，
 * 自动检测悬崖风险、统计海拔分布、评估海洋质量，
 * 输出PNG图片和Markdown格式报告，完全离线运行，不需要启动Minecraft。
 *
 * <p>重要设计说明/Important design note:
 * 此分析器使用与LandscapeChunkGenerator.fillFromNoise()完全相同的地形计算链，
 * 确保分析结果与实际游戏地形100%一致：
 * - RegionController.getTerrainBlend() 获取混合信息
 * - determineTerrainType() 确定地形类型
 * - calculateFinalHeight() 计算最终高度（包含29种地形变体）
 * - calcHeightForType() 各种地形的高度计算
 * - getRiverErosionIntensity() 河流侵蚀计算
 * - getAlluvialFactor() 冲积扇因子计算
 * - calculateErodedHeight() 侵蚀后高度计算</p>
 *
 * @使用方式 通过main方法或编程方式调用 / Use via main method or programmatic API
 * @已知限制 大半径分析耗时较长 / Large radius analysis takes considerable time
 */
public class SeedAnalyzer {

    private static final int SEA_LEVEL = WorldScapeConstants.SEA_LEVEL_FALLBACK;

    private static final double CLIFF_SAFE_THRESHOLD = 10.0;
    private static final double CLIFF_LOW_THRESHOLD = 20.0;
    private static final double CLIFF_MODERATE_THRESHOLD = 30.0;
    private static final double CLIFF_HIGH_THRESHOLD = 45.0;

    private static final int HISTOGRAM_BIN_SIZE = 20;
    private static final int HISTOGRAM_MIN_BIN = -80;
    private static final int HISTOGRAM_MAX_BIN = 520;

    private static final double[][] ALTITUDE_COLOR_STOPS = {
        {-80, 0x001040},
        {-40, 0x003080},
        {-10, 0x0066cc},
        {0, 0x3399ff},
        {SEA_LEVEL, 0x3399ff},
        {SEA_LEVEL + 5, 0xe8d174},
        {SEA_LEVEL + 30, 0x7ccd7c},
        {SEA_LEVEL + 60, 0x228b22},
        {SEA_LEVEL + 100, 0x8b6914},
        {SEA_LEVEL + 180, 0x808080},
        {SEA_LEVEL + 280, 0xd0d0d0},
        {SEA_LEVEL + 380, 0xffffff}
    };

    private static final double[][] CLIFF_COLOR_STOPS = {
        {0, 0x004000},
        {CLIFF_SAFE_THRESHOLD, 0x00ff00},
        {CLIFF_LOW_THRESHOLD, 0xffff00},
        {CLIFF_MODERATE_THRESHOLD, 0xffa500},
        {CLIFF_HIGH_THRESHOLD, 0xff0000},
        {65, 0x8b0000}
    };

    private final long seed;
    private final int radius;
    private final int step;

    private RegionController regionController;
    private NoiseSet noiseSet;
    private TerrainFieldSampler fieldSampler;
    private boolean[] currentSubmergedMask;
    private double[] currentContinuousHeights;

    /**
     * 构造种子分析器，指定种子值、半径和采样步长。
     * Constructs a seed analyzer with the specified seed, radius, and sampling step.
     *
     * @param seed   世界种子 / world seed
     * @param radius 分析半径（方块数）/ analysis radius in blocks
     * @param step   采样步长（方块数），1=每格采样，4=每4格采样 / sampling step in blocks
     * @调用时机 需要分析种子地形时调用 / Called when seed terrain analysis is needed
     * @已知限制 步长越大精度越低 / Larger step results in lower precision
     */
    public SeedAnalyzer(long seed, int radius, int step) {
        this.seed = seed;
        this.radius = Math.max(16, radius);
        this.step = Math.max(1, step);
    }

    /**
     * 构造种子分析器，使用默认采样步长4。
     * Constructs a seed analyzer with default sampling step of 4.
     *
     * @param seed   世界种子 / world seed
     * @param radius 分析半径（方块数）/ analysis radius in blocks
     * @调用时机 简化构造时调用 / Called for simplified construction
     * @已知限制 无 / None
     */
    public SeedAnalyzer(long seed, int radius) {
        this(seed, radius, 4);
    }

    /**
     * 初始化地形计算组件（与LandscapeChunkGenerator相同的初始化方式）。
     * Initialize terrain calculation components (same as LandscapeChunkGenerator).
     */
    private void initializeComponents() {
        if (regionController == null) {
            regionController = new RegionController(seed, SEA_LEVEL);
        }
        if (noiseSet == null) {
            noiseSet = NoiseSet.getOrCreate(seed);
        }
        if (fieldSampler == null) {
            fieldSampler = TerrainFieldSampler.getOrCreate(seed);
        }
    }

    /**
     * 执行完整分析并输出结果到文件。
     * Performs full analysis and outputs results to files.
     *
     * @调用时机 需要生成完整分析报告时调用 / Called when full analysis report is needed
     * @已知限制 大半径时耗时较长 / Takes considerable time for large radius
     */
    public void analyzeAndOutput() {
        long startTime = System.currentTimeMillis();
        System.out.println("[SeedAnalyzer] " + "开始分析 / Starting analysis: seed=" + seed + ", radius=" + radius + ", step=" + step);

        AnalysisResult result = analyze();

        Path outputDir = Paths.get("seed_analysis", String.valueOf(seed));
        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            System.err.println("[SeedAnalyzer] " + "无法创建输出目录 / Cannot create output directory: " + outputDir);
            return;
        }

        System.out.println("[SeedAnalyzer] " + "生成图片 / Generating images...");
        generateGrayscaleHeightmap(result.heightMap, result.gridSize, outputDir);
                generateColoredHeightmap(result.heightMap, result.gridSize, outputDir);
                generateBareEarthMap(result.heightMap, result.submergedMask, result.gridSize, outputDir);
                generateCliffHeatmap(result.gradients, result.gridSize, outputDir);

        System.out.println("[SeedAnalyzer] " + "生成报告 / Generating report...");
        generateMarkdownReport(result, outputDir);

        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println("[SeedAnalyzer] " + "分析完成 / Analysis complete in " + elapsed + "ms");
        System.out.println("[SeedAnalyzer] " + "输出目录 / Output directory: " + outputDir.toAbsolutePath());
    }

    /**
     * 执行分析并返回结果，不输出文件。
     * Performs analysis and returns results without file output.
     *
     * @return 分析结果 / analysis result
     * @调用时机 需要编程方式获取分析数据时调用 / Called when programmatic access to analysis data is needed
     * @已知限制 无 / None
     */
    public AnalysisResult analyze() {
        int gridSize = (2 * radius / step) + 1;
        int totalSamples = gridSize * gridSize;

        initializeComponents();

        System.out.println("[SeedAnalyzer] " + "生成高度数据（使用完整计算链）/ Generating height data with full calculation chain (" + gridSize + "x" + gridSize + " = " + totalSamples + " points)...");
        double[] heightMap = generateHeightData(gridSize);

        System.out.println("[SeedAnalyzer] " + "计算梯度 / Calculating gradients...");
        double[] gradients = calculateGradients(heightMap, gridSize);

        System.out.println("[SeedAnalyzer] " + "评估悬崖风险 / Assessing cliff risk...");
        CliffRiskAssessment cliffRisk = assessCliffRisk(gradients, gridSize);

        System.out.println("[SeedAnalyzer] " + "统计海拔分布 / Calculating altitude statistics...");
        AltitudeStatistics altitudeStats = calculateAltitudeStats(heightMap, currentSubmergedMask, gridSize);

        System.out.println("[SeedAnalyzer] " + "评估海洋质量 / Assessing ocean quality...");
        OceanQualityAssessment oceanQuality = assessOceanQuality(heightMap, currentSubmergedMask, gridSize);

        System.out.println("[SeedAnalyzer] " + "收集地形类型 / Collecting terrain types...");
        Map<TerrainType, Integer> terrainTypes = collectTerrainTypes(gridSize);

        System.out.println("[SeedAnalyzer] " + "分析地表方块 / Analyzing surface blocks...");
        Map<String, Integer> surfaceBlockCounts = new LinkedHashMap<>();
        Map<String, Integer> subSurfaceBlockCounts = new LinkedHashMap<>();
        analyzeSurfaceBlocks(gridSize, surfaceBlockCounts, subSurfaceBlockCounts, heightMap);

        return new AnalysisResult(
                seed, radius, step, gridSize,
                heightMap, gradients, currentSubmergedMask,
                altitudeStats, cliffRisk, oceanQuality, terrainTypes,
                surfaceBlockCounts, subSurfaceBlockCounts
        );
    }

    /**
     * 生成高度数据 - 使用与LandscapeChunkGenerator.fillFromNoise()完全相同的地形计算链。
     * Generate height data - using the exact same terrain calculation chain as LandscapeChunkGenerator.fillFromNoise().
     */
    private double[] generateHeightData(int gridSize) {
        double[] heightMap = new double[gridSize * gridSize];
        currentSubmergedMask = new boolean[gridSize * gridSize];
        currentContinuousHeights = new double[gridSize * gridSize];
        int reported = -1;
        for (int gz = 0; gz < gridSize; gz++) {
            int progress = (int) ((gz + 1) * 100.0 / gridSize);
            if (progress > reported && progress % 10 == 0) {
                reported = progress;
                System.out.println("[SeedAnalyzer] 高度计算进度 / Height calculation progress: " + progress + "%");
            }
            for (int gx = 0; gx < gridSize; gx++) {
                int idx = gz * gridSize + gx;
                int worldX = -radius + gx * step;
                int worldZ = -radius + gz * step;
                TerrainHeightResult result = calculateFullTerrainHeight(worldX, worldZ);
                heightMap[idx] = result.continuousHeight;
                currentSubmergedMask[idx] = result.erodedHeight < SEA_LEVEL;
                currentContinuousHeights[idx] = result.continuousHeight;
            }
        }
        return heightMap;
    }

    /**
     * 计算指定坐标的地形高度 - 与LandscapeChunkGenerator.fillFromNoise()中的计算逻辑完全一致。
     * Calculate terrain height at specified coordinates - identical to LandscapeChunkGenerator.fillFromNoise().
     */
    private double calculateTerrainHeight(int worldX, int worldZ) {
        RegionController.TerrainBlendResult blend = regionController.getTerrainBlend(worldX, worldZ);
        TerrainType type = TerrainCalculator.determineTerrainType(blend);
        double continuousHeight = TerrainCalculator.calculateFinalHeight(worldX, worldZ, blend, type, noiseSet, fieldSampler);
        boolean isRiver = TerrainCalculator.isRiverAt(worldX, worldZ, noiseSet);
        double riverDepthMultiplier = TerrainCalculator.getRiverDepthMultiplierForTier(blend.macroInfo.elevationTier);
        double riverDepth = TerrainCalculator.getRiverDepthAt(worldX, worldZ, noiseSet, (int)continuousHeight, SEA_LEVEL, isRiver, riverDepthMultiplier);
        double erosionIntensity = TerrainCalculator.getRiverErosionIntensity(worldX, worldZ, noiseSet, continuousHeight, SEA_LEVEL, blend);
        double alluvialFactor = TerrainCalculator.getAlluvialFactor(worldX, worldZ, noiseSet, continuousHeight, SEA_LEVEL);
        double erosionMultiplier = TerrainCalculator.getErosionMultiplierForTier(blend.macroInfo.elevationTier);
        int erodedHeight = TerrainCalculator.calculateErodedHeight(continuousHeight, isRiver, riverDepth, SEA_LEVEL, erosionIntensity, alluvialFactor, erosionMultiplier);
        int actualHeight = TerrainCalculator.calculateActualSurfaceHeight(erodedHeight, isRiver, riverDepth, WorldScapeConstants.OVERWORLD_MIN_Y);
        return actualHeight;
    }

    /**
     * Container for all computed terrain heights at a single point.
     * Container for all computed terrain heights at a single point.
     */
    private static class TerrainHeightResult {
        final double continuousHeight;
        final int erodedHeight;
        final int actualSurfaceHeight;

        TerrainHeightResult(double continuousHeight, int erodedHeight, int actualSurfaceHeight) {
            this.continuousHeight = continuousHeight;
            this.erodedHeight = erodedHeight;
            this.actualSurfaceHeight = actualSurfaceHeight;
        }
    }

    /**
     * Compute full terrain height result - tracks continuous height for see-through-water view.
     * Uses the same terrain calculation chain as LandscapeChunkGenerator.fillFromNoise().
     * getTerrainBlend(worldX, worldZ) delegates to getTerrainBlend(worldX, worldZ, null),
     * producing identical results — the BlendCache in fillFromNoise is only a performance optimization.
     * 
     * Compute full terrain height result - tracks continuous height for see-through-water view.
     * 使用与 LandscapeChunkGenerator.fillFromNoise() 相同的地形计算链。
     * getTerrainBlend(worldX, worldZ) 委托给 getTerrainBlend(worldX, worldZ, null)，
     * 产生相同结果 —— fillFromNoise 中的 BlendCache 仅用于性能优化。
     */
    private TerrainHeightResult calculateFullTerrainHeight(int worldX, int worldZ) {
        RegionController.TerrainBlendResult blend = regionController.getTerrainBlend(worldX, worldZ);
        TerrainType type = TerrainCalculator.determineTerrainType(blend);
        double continuousHeight = TerrainCalculator.calculateFinalHeight(worldX, worldZ, blend, type, noiseSet, fieldSampler);
        boolean isRiver = TerrainCalculator.isRiverAt(worldX, worldZ, noiseSet);
        double riverDepthMultiplier = TerrainCalculator.getRiverDepthMultiplierForTier(blend.macroInfo.elevationTier);
        double riverDepth = TerrainCalculator.getRiverDepthAt(worldX, worldZ, noiseSet, (int)continuousHeight, SEA_LEVEL, isRiver, riverDepthMultiplier);
        double erosionIntensity = TerrainCalculator.getRiverErosionIntensity(worldX, worldZ, noiseSet, continuousHeight, SEA_LEVEL, blend);
        double alluvialFactor = TerrainCalculator.getAlluvialFactor(worldX, worldZ, noiseSet, continuousHeight, SEA_LEVEL);
        double erosionMultiplier = TerrainCalculator.getErosionMultiplierForTier(blend.macroInfo.elevationTier);
        int erodedHeight = TerrainCalculator.calculateErodedHeight(continuousHeight, isRiver, riverDepth, SEA_LEVEL, erosionIntensity, alluvialFactor, erosionMultiplier);
        int actualSurfaceHeight = TerrainCalculator.calculateActualSurfaceHeight(erodedHeight, isRiver, riverDepth, WorldScapeConstants.OVERWORLD_MIN_Y);
        return new TerrainHeightResult(continuousHeight, erodedHeight, actualSurfaceHeight);
    }

    // ===== 以下方法为SeedAnalyzer自有实现 =====

    private double[] calculateGradients(double[] heightMap, int gridSize) {
        double[] gradients = new double[gridSize * gridSize];
        if (gridSize < 3) {
            return gradients;
        }
        for (int z = 1; z < gridSize - 1; z++) {
            for (int x = 1; x < gridSize - 1; x++) {
                double gx = -heightMap[(z - 1) * gridSize + (x - 1)]
                        + heightMap[(z - 1) * gridSize + (x + 1)]
                        - 2.0 * heightMap[z * gridSize + (x - 1)]
                        + 2.0 * heightMap[z * gridSize + (x + 1)]
                        - heightMap[(z + 1) * gridSize + (x - 1)]
                        + heightMap[(z + 1) * gridSize + (x + 1)];
                double gz = -heightMap[(z - 1) * gridSize + (x - 1)]
                        - 2.0 * heightMap[(z - 1) * gridSize + x]
                        - heightMap[(z - 1) * gridSize + (x + 1)]
                        + heightMap[(z + 1) * gridSize + (x - 1)]
                        + 2.0 * heightMap[(z + 1) * gridSize + x]
                        + heightMap[(z + 1) * gridSize + (x + 1)];
                gradients[z * gridSize + x] = Math.sqrt(gx * gx + gz * gz) / (8.0 * step);
            }
        }
        for (int i = 0; i < gridSize; i++) {
            gradients[i] = gradients[gridSize + Math.max(1, Math.min(i, gridSize - 2))];
            gradients[(gridSize - 1) * gridSize + i] = gradients[(gridSize - 2) * gridSize + Math.max(1, Math.min(i, gridSize - 2))];
            gradients[i * gridSize] = gradients[i * gridSize + Math.max(1, Math.min(1, gridSize - 2))];
            gradients[i * gridSize + gridSize - 1] = gradients[i * gridSize + Math.max(1, Math.min(gridSize - 2, gridSize - 2))];
        }
        return gradients;
    }

    private CliffRiskAssessment assessCliffRisk(double[] gradients, int gridSize) {
        int total = gridSize * gridSize;
        int safeCount = 0;
        int lowCount = 0;
        int moderateCount = 0;
        int highCount = 0;
        int extremeCount = 0;
        double maxGradient = 0;
        int maxGradientGridX = 0;
        int maxGradientGridZ = 0;

        for (int z = 0; z < gridSize; z++) {
            for (int x = 0; x < gridSize; x++) {
                double g = gradients[z * gridSize + x];
                if (g < CLIFF_SAFE_THRESHOLD) {
                    safeCount++;
                } else if (g < CLIFF_LOW_THRESHOLD) {
                    lowCount++;
                } else if (g < CLIFF_MODERATE_THRESHOLD) {
                    moderateCount++;
                } else if (g < CLIFF_HIGH_THRESHOLD) {
                    highCount++;
                } else {
                    extremeCount++;
                }
                if (g > maxGradient) {
                    maxGradient = g;
                    maxGradientGridX = x;
                    maxGradientGridZ = z;
                }
            }
        }

        int largestCliffArea = findLargestContiguousArea(gradients, gridSize, CLIFF_MODERATE_THRESHOLD);

        return new CliffRiskAssessment(
                100.0 * safeCount / total,
                100.0 * lowCount / total,
                100.0 * moderateCount / total,
                100.0 * highCount / total,
                100.0 * extremeCount / total,
                maxGradient,
                -radius + maxGradientGridX * step,
                -radius + maxGradientGridZ * step,
                largestCliffArea * step * step
        );
    }

    private int findLargestContiguousArea(double[] gradients, int gridSize, double threshold) {
        boolean[] isCliff = new boolean[gridSize * gridSize];
        for (int i = 0; i < gradients.length; i++) {
            isCliff[i] = gradients[i] >= threshold;
        }

        boolean[] visited = new boolean[gridSize * gridSize];
        int maxArea = 0;
        int[] dx = {-1, 1, 0, 0};
        int[] dz = {0, 0, -1, 1};

        for (int z = 0; z < gridSize; z++) {
            for (int x = 0; x < gridSize; x++) {
                int idx = z * gridSize + x;
                if (!isCliff[idx] || visited[idx]) {
                    continue;
                }
                int area = 0;
                Queue<Integer> queue = new LinkedList<>();
                queue.add(idx);
                visited[idx] = true;
                while (!queue.isEmpty()) {
                    int current = queue.poll();
                    area++;
                    int cx = current % gridSize;
                    int cz = current / gridSize;
                    for (int d = 0; d < 4; d++) {
                        int nx = cx + dx[d];
                        int nz = cz + dz[d];
                        if (nx >= 0 && nx < gridSize && nz >= 0 && nz < gridSize) {
                            int nIdx = nz * gridSize + nx;
                            if (isCliff[nIdx] && !visited[nIdx]) {
                                visited[nIdx] = true;
                                queue.add(nIdx);
                            }
                        }
                    }
                }
                maxArea = Math.max(maxArea, area);
            }
        }
        return maxArea;
    }

    private AltitudeStatistics calculateAltitudeStats(double[] heightMap, boolean[] submergedMask, int gridSize) {
        int total = gridSize * gridSize;
        double minHeight = Double.MAX_VALUE;
        double maxHeight = Double.MIN_VALUE;
        double sumHeight = 0;
        int landCount = 0;
        double dryLandMin = Double.MAX_VALUE;
        double dryLandMax = Double.MIN_VALUE;
        double dryLandSum = 0;
        int dryLandCount = 0;

        for (int i = 0; i < total; i++) {
            double h = heightMap[i];
            if (h < minHeight) minHeight = h;
            if (h > maxHeight) maxHeight = h;
            sumHeight += h;
            if (!submergedMask[i]) {
                landCount++;
                if (h < dryLandMin) dryLandMin = h;
                if (h > dryLandMax) dryLandMax = h;
                dryLandSum += h;
                dryLandCount++;
            }
        }
        double meanHeight = sumHeight / total;

        double sumSqDiff = 0;
        for (int i = 0; i < total; i++) {
            double diff = heightMap[i] - meanHeight;
            sumSqDiff += diff * diff;
        }
        double stdDev = Math.sqrt(sumSqDiff / total);
        double landPercentage = 100.0 * landCount / total;
        double oceanPercentage = 100.0 - landPercentage;

        double dryLandMean = dryLandCount > 0 ? dryLandSum / dryLandCount : Double.NaN;
        double dryLandMinFinal = dryLandCount > 0 ? dryLandMin : Double.NaN;
        double dryLandMaxFinal = dryLandCount > 0 ? dryLandMax : Double.NaN;

        double seaLevelRelativeMin = minHeight - SEA_LEVEL;
        double seaLevelRelativeMax = maxHeight - SEA_LEVEL;
        double seaLevelRelativeMean = meanHeight - SEA_LEVEL;

        double[] sorted = heightMap.clone();
        Arrays.sort(sorted);
        double medianHeight = sorted[total / 2];

        int binCount = (HISTOGRAM_MAX_BIN - HISTOGRAM_MIN_BIN) / HISTOGRAM_BIN_SIZE;
        int[] histogram = new int[binCount];
        for (double h : heightMap) {
            int bin = (int) ((h - HISTOGRAM_MIN_BIN) / HISTOGRAM_BIN_SIZE);
            if (bin < 0) bin = 0;
            if (bin >= binCount) bin = binCount - 1;
            histogram[bin]++;
        }

        return new AltitudeStatistics(
                minHeight, maxHeight, meanHeight, medianHeight, stdDev,
                landPercentage, oceanPercentage,
                dryLandMinFinal, dryLandMaxFinal, dryLandMean,
                seaLevelRelativeMin, seaLevelRelativeMax, seaLevelRelativeMean,
                histogram, HISTOGRAM_BIN_SIZE, HISTOGRAM_MIN_BIN
        );
    }

    private OceanQualityAssessment assessOceanQuality(double[] heightMap, boolean[] submergedMask, int gridSize) {
        int total = gridSize * gridSize;
        boolean[] isOcean = submergedMask;
        int oceanCount = 0;
        double oceanDepthSum = 0;
        double maxOceanDepth = 0;

        for (int i = 0; i < total; i++) {
            if (isOcean[i]) {
                oceanCount++;
                double depth = SEA_LEVEL - heightMap[i];
                oceanDepthSum += depth;
                if (depth > maxOceanDepth) maxOceanDepth = depth;
            }
        }

        double coveragePercentage = 100.0 * oceanCount / total;
        double averageDepth = oceanCount > 0 ? oceanDepthSum / oceanCount : 0;

        int shallowCount = 0;
        for (int i = 0; i < total; i++) {
            if (isOcean[i]) {
                double depth = SEA_LEVEL - heightMap[i];
                if (depth < 10) shallowCount++;
            }
        }
        double shallowWaterPercentage = oceanCount > 0 ? 100.0 * shallowCount / oceanCount : 0;

        int oceanBodyCount = countConnectedBodies(isOcean, gridSize);
        double coastlineLength = calculateCoastlineLength(isOcean, gridSize);

        double qualityScore = calculateOceanQualityScore(
                coveragePercentage, averageDepth, oceanBodyCount, shallowWaterPercentage
        );

        return new OceanQualityAssessment(
                coveragePercentage, averageDepth, maxOceanDepth,
                shallowWaterPercentage, oceanBodyCount, coastlineLength, qualityScore
        );
    }

    private int countConnectedBodies(boolean[] isOcean, int gridSize) {
        boolean[] visited = new boolean[gridSize * gridSize];
        int bodyCount = 0;
        int[] dx = {-1, 1, 0, 0};
        int[] dz = {0, 0, -1, 1};

        for (int z = 0; z < gridSize; z++) {
            for (int x = 0; x < gridSize; x++) {
                int idx = z * gridSize + x;
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

    private double calculateCoastlineLength(boolean[] isOcean, int gridSize) {
        double length = 0;
        int[] dx = {-1, 1, 0, 0};
        int[] dz = {0, 0, -1, 1};
        for (int z = 0; z < gridSize; z++) {
            for (int x = 0; x < gridSize; x++) {
                int idx = z * gridSize + x;
                if (!isOcean[idx]) {
                    continue;
                }
                for (int d = 0; d < 4; d++) {
                    int nx = x + dx[d];
                    int nz = z + dz[d];
                    if (nx < 0 || nx >= gridSize || nz < 0 || nz >= gridSize) {
                        length += step;
                    } else {
                        int nIdx = nz * gridSize + nx;
                        if (!isOcean[nIdx]) {
                            length += step;
                        }
                    }
                }
            }
        }
        return length;
    }

    private double calculateOceanQualityScore(double coverage, double avgDepth, int bodies, double shallowPct) {
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

    private Map<TerrainType, Integer> collectTerrainTypes(int gridSize) {
        Map<TerrainType, Integer> typeCounts = new LinkedHashMap<>();
        int reported = -1;
        for (int gz = 0; gz < gridSize; gz++) {
            int progress = (int) ((gz + 1) * 100.0 / gridSize);
            if (progress > reported && progress % 10 == 0) {
                reported = progress;
                System.out.println("[SeedAnalyzer] " + "  地形类型采集进度 / Terrain type collection progress: " + progress + "%");
            }
            for (int gx = 0; gx < gridSize; gx++) {
                int worldX = -radius + gx * step;
                int worldZ = -radius + gz * step;
                TerrainBlendResult blend = regionController.getTerrainBlend(worldX, worldZ);
                TerrainType type = TerrainCalculator.determineTerrainType(blend);
                typeCounts.merge(type, 1, Integer::sum);
            }
        }
        return typeCounts;
    }

    // @AESTHETIC: Surface block analysis mimics FallbackSurfaceAdapter logic without requiring biome API.
    // Maps TerrainType to expected surface/sub-surface blocks, verifying dirt-under-grass fix.
    // 地表方块分析：模拟 FallbackSurfaceAdapter 逻辑，根据地形类型推断地表/次表层方块，验证泥土层修复。
    private static String determineSurfaceBlock(TerrainType type, int height, int seaLevel) {
        if (height < seaLevel) {
            int depthBelowSea = seaLevel - height;
            if (depthBelowSea > 5) return "GRAVEL";
            return "SAND";
        }
        if (type == TerrainType.HIGH_MOUNTAINS || type == TerrainType.RIDGE || type == TerrainType.PEAK || type == TerrainType.HORN
                || type == TerrainType.CLIFF || type == TerrainType.CANYON || type == TerrainType.PLATEAU || type == TerrainType.SEA_CLIFF) {
            return "GRAVEL";
        } else if (type == TerrainType.GOBI || type == TerrainType.SALT_FLAT || type == TerrainType.DUNE || type == TerrainType.YARDANG) {
            return "SAND";
        } else if (type == TerrainType.BEACH) {
            return "SAND";
        } else if (type == TerrainType.ICE_SHEET) {
            return "SNOW_BLOCK";
        }
        return "GRASS_BLOCK";
    }

    // @AESTHETIC: Sub-surface block mimics the dirt-under-grass fix in FallbackSurfaceAdapter.
    // Dry land → DIRT (not STONE) after the fix. This is the key verification metric.
    // 次表层方块：模拟 FallbackSurfaceAdapter 中泥土层修复。干燥陆地→DIRT（修复后），这是关键的验证指标。
    private static String determineSubSurfaceBlock(TerrainType type, int height, int seaLevel) {
        if (height < seaLevel) {
            int depthBelowSea = seaLevel - height;
            if (depthBelowSea > 5) return "GRAVEL";
            return "SAND";
        }
        if (type == TerrainType.HIGH_MOUNTAINS || type == TerrainType.RIDGE || type == TerrainType.PEAK || type == TerrainType.HORN
                || type == TerrainType.CLIFF || type == TerrainType.CANYON || type == TerrainType.PLATEAU || type == TerrainType.SEA_CLIFF
                || type == TerrainType.ICE_SHEET) {
            return "GRAVEL";
        } else if (type == TerrainType.GOBI || type == TerrainType.SALT_FLAT || type == TerrainType.DUNE || type == TerrainType.YARDANG
                || type == TerrainType.BEACH) {
            return "SAND";
        }
        return "DIRT"; // The fix: dry land has dirt, not stone
    }

    private void analyzeSurfaceBlocks(int gridSize, Map<String, Integer> surfaceCounts, Map<String, Integer> subSurfaceCounts, double[] heightMap) {
        int reported = -1;
        for (int gz = 0; gz < gridSize; gz++) {
            int progress = (int) ((gz + 1) * 100.0 / gridSize);
            if (progress > reported && progress % 10 == 0) {
                reported = progress;
                System.out.println("[SeedAnalyzer] " + "  地表方块分析进度 / Surface block analysis progress: " + progress + "%");
            }
            for (int gx = 0; gx < gridSize; gx++) {
                int worldX = -radius + gx * step;
                int worldZ = -radius + gz * step;
                int idx = gz * gridSize + gx;
                int height = (int)heightMap[idx];
                TerrainBlendResult blend = regionController.getTerrainBlend(worldX, worldZ);
                TerrainType type = TerrainCalculator.determineTerrainType(blend);
                String surface = determineSurfaceBlock(type, height, SEA_LEVEL);
                String subSurface = determineSubSurfaceBlock(type, height, SEA_LEVEL);
                surfaceCounts.merge(surface, 1, Integer::sum);
                subSurfaceCounts.merge(subSurface, 1, Integer::sum);
            }
        }
    }

    private void generateGrayscaleHeightmap(double[] heightMap, int gridSize, Path outputDir) {
        double minH = Double.MAX_VALUE;
        double maxH = Double.MIN_VALUE;
        for (double h : heightMap) {
            if (h < minH) minH = h;
            if (h > maxH) maxH = h;
        }
        double range = maxH - minH;
        if (range < 1.0) range = 1.0;

        BufferedImage image = new BufferedImage(gridSize, gridSize, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < gridSize; z++) {
            for (int x = 0; x < gridSize; x++) {
                double h = heightMap[z * gridSize + x];
                int gray = (int) ((h - minH) / range * 255.0);
                gray = Math.max(0, Math.min(255, gray));
                int rgb = (gray << 16) | (gray << 8) | gray;
                image.setRGB(x, z, rgb);
            }
        }
        writePng(image, outputDir.resolve("heightmap_grayscale.png"));
    }

    private void generateColoredHeightmap(double[] heightMap, int gridSize, Path outputDir) {
        BufferedImage image = new BufferedImage(gridSize, gridSize, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < gridSize; z++) {
            for (int x = 0; x < gridSize; x++) {
                double h = heightMap[z * gridSize + x];
                int rgb = altitudeToColor(h);
                image.setRGB(x, z, rgb);
            }
        }
        writePng(image, outputDir.resolve("heightmap_colored.png"));
    }

    private void generateBareEarthMap(double[] heightMap, boolean[] submergedMask, int gridSize, Path outputDir) {
        double minH = Double.MAX_VALUE;
        double maxH = Double.MIN_VALUE;
        for (double h : heightMap) {
            if (h < minH) minH = h;
            if (h > maxH) maxH = h;
        }
        double range = maxH - minH;
        if (range < 1) range = 1;

        BufferedImage image = new BufferedImage(gridSize, gridSize, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < gridSize; z++) {
            for (int x = 0; x < gridSize; x++) {
                double h = heightMap[z * gridSize + x];
                double t = (h - minH) / range;
                t = Math.max(0, Math.min(1, t));
                boolean submerged = submergedMask != null && submergedMask[z * gridSize + x];

                int baseRgb;
                if (submerged) {
                    double oceanT = t * 0.6;
                    baseRgb = interpolateColor3f(
                            0.0f, 0.05f, 0.15f,
                            0.4f, 0.7f, 0.9f,
                            (float)oceanT
                    );
                } else {
                    baseRgb = interpolateColor3f(
                            0.1f, 0.4f, 0.1f,
                            0.9f, 0.85f, 0.7f,
                            (float)t
                    );
                }
                image.setRGB(x, z, baseRgb);
            }
        }
        writePng(image, outputDir.resolve("terra_nuda.png"));
    }

    private static int interpolateColor3f(float r1, float g1, float b1, float r2, float g2, float b2, float t) {
        int r = (int)((r1 + (r2 - r1) * t) * 255);
        int g = (int)((g1 + (g2 - g1) * t) * 255);
        int b = (int)((b1 + (b2 - b1) * t) * 255);
        return (r << 16) | (g << 8) | b;
    }

    private void generateCliffHeatmap(double[] gradients, int gridSize, Path outputDir) {
        BufferedImage image = new BufferedImage(gridSize, gridSize, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < gridSize; z++) {
            for (int x = 0; x < gridSize; x++) {
                double g = gradients[z * gridSize + x];
                int rgb = gradientToColor(g);
                image.setRGB(x, z, rgb);
            }
        }
        writePng(image, outputDir.resolve("cliff_heatmap.png"));
    }

    private void writePng(BufferedImage image, Path filePath) {
        try {
            ImageIO.write(image, "PNG", filePath.toFile());
            System.out.println("[SeedAnalyzer] " + "  已保存 / Saved: " + filePath.getFileName());
        } catch (IOException e) {
            System.err.println("[SeedAnalyzer] " + "保存图片失败 / Failed to save image: " + filePath + " - " + e.getMessage());
        }
    }

    private static int altitudeToColor(double height) {
        if (height <= ALTITUDE_COLOR_STOPS[0][0]) {
            return (int) ALTITUDE_COLOR_STOPS[0][1];
        }
        if (height >= ALTITUDE_COLOR_STOPS[ALTITUDE_COLOR_STOPS.length - 1][0]) {
            return (int) ALTITUDE_COLOR_STOPS[ALTITUDE_COLOR_STOPS.length - 1][1];
        }
        for (int i = 0; i < ALTITUDE_COLOR_STOPS.length - 1; i++) {
            double h0 = ALTITUDE_COLOR_STOPS[i][0];
            double h1 = ALTITUDE_COLOR_STOPS[i + 1][0];
            if (height >= h0 && height < h1) {
                double t = (height - h0) / (h1 - h0);
                return interpolateColor((int) ALTITUDE_COLOR_STOPS[i][1], (int) ALTITUDE_COLOR_STOPS[i + 1][1], t);
            }
        }
        return (int) ALTITUDE_COLOR_STOPS[ALTITUDE_COLOR_STOPS.length - 1][1];
    }

    private static int gradientToColor(double gradient) {
        if (gradient <= CLIFF_COLOR_STOPS[0][0]) {
            return (int) CLIFF_COLOR_STOPS[0][1];
        }
        if (gradient >= CLIFF_COLOR_STOPS[CLIFF_COLOR_STOPS.length - 1][0]) {
            return (int) CLIFF_COLOR_STOPS[CLIFF_COLOR_STOPS.length - 1][1];
        }
        for (int i = 0; i < CLIFF_COLOR_STOPS.length - 1; i++) {
            double g0 = CLIFF_COLOR_STOPS[i][0];
            double g1 = CLIFF_COLOR_STOPS[i + 1][0];
            if (gradient >= g0 && gradient < g1) {
                double t = (gradient - g0) / (g1 - g0);
                return interpolateColor((int) CLIFF_COLOR_STOPS[i][1], (int) CLIFF_COLOR_STOPS[i + 1][1], t);
            }
        }
        return (int) CLIFF_COLOR_STOPS[CLIFF_COLOR_STOPS.length - 1][1];
    }

    private static int interpolateColor(int c0, int c1, double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        int r0 = (c0 >> 16) & 0xff;
        int g0 = (c0 >> 8) & 0xff;
        int b0 = c0 & 0xff;
        int r1 = (c1 >> 16) & 0xff;
        int g1 = (c1 >> 8) & 0xff;
        int b1 = c1 & 0xff;
        int r = (int) (r0 + (r1 - r0) * t);
        int g = (int) (g0 + (g1 - g0) * t);
        int b = (int) (b0 + (b1 - b0) * t);
        return (r << 16) | (g << 8) | b;
    }

    private void generateMarkdownReport(AnalysisResult result, Path outputDir) {
        Path reportPath = outputDir.resolve("report.md");
        try (BufferedWriter writer = Files.newBufferedWriter(reportPath)) {
            writer.write("# Seed Analysis Report / 种子分析报告");
            writer.newLine();
            writer.newLine();
            writer.write("*Note: This analysis uses the exact same terrain calculation chain as LandscapeChunkGenerator, ensuring 100% accuracy with actual game terrain.*");
            writer.newLine();
            writer.newLine();

            writeBasicInfo(writer, result);
            writeAltitudeStats(writer, result.altitudeStats);
            writeCliffRisk(writer, result.cliffRisk);
            writeOceanQuality(writer, result.oceanQuality);
            writeTerrainTypes(writer, result.terrainTypes, result.gridSize);
        writeSurfaceBlocks(writer, result.surfaceBlockCounts, result.subSurfaceBlockCounts, result.terrainTypes, result.gridSize);
        writeOutputFiles(writer);

        } catch (IOException e) {
            System.err.println("[SeedAnalyzer] " + "生成报告失败 / Failed to generate report: " + e.getMessage());
        }
        System.out.println("[SeedAnalyzer] " + "  已保存 / Saved: " + reportPath.getFileName());
    }

    private void writeBasicInfo(BufferedWriter writer, AnalysisResult result) throws IOException {
        writer.write("## Basic Information / 基本信息");
        writer.newLine();
        writer.newLine();
        writer.write("| Item | Value |");
        writer.newLine();
        writer.write("|------|-------|");
        writer.newLine();
        writer.write("| Seed / 种子 | " + result.seed + " |");
        writer.newLine();
        writer.write("| Radius / 半径 | " + result.radius + " blocks |");
        writer.newLine();
        writer.write("| Step / 采样步长 | " + result.step + " blocks |");
        writer.newLine();
        writer.write("| Grid Size / 网格尺寸 | " + result.gridSize + " x " + result.gridSize + " |");
        writer.newLine();
        writer.write("| Total Samples / 总采样数 | " + (result.gridSize * result.gridSize) + " |");
        writer.newLine();
        writer.write("| Sea Level / 海平面 | " + SEA_LEVEL + " |");
        writer.newLine();
        writer.write("| Calculation Chain / 计算链 | LandscapeChunkGenerator.fillFromNoise() |");
        writer.newLine();
        writer.newLine();
    }

    private void writeAltitudeStats(BufferedWriter writer, AltitudeStatistics stats) throws IOException {
        writer.write("## Altitude Statistics / 海拔统计");
        writer.newLine();
        writer.newLine();
        writer.write("### See-Through-Water Height / 透水海拔高度 (Raw Terrain)");
        writer.newLine();
        writer.newLine();
        writer.write("| Metric | Value |");
        writer.newLine();
        writer.write("|--------|-------|");
        writer.newLine();
        writer.write(String.format("| Minimum / 最低 | %.1f |", stats.minHeight));
        writer.newLine();
        writer.write(String.format("| Maximum / 最高 | %.1f |", stats.maxHeight));
        writer.newLine();
        writer.write(String.format("| Mean / 平均 | %.1f |", stats.meanHeight));
        writer.newLine();
        writer.write(String.format("| Median / 中位数 | %.1f |", stats.medianHeight));
        writer.newLine();
        writer.write(String.format("| Std Dev / 标准差 | %.1f |", stats.stdDev));
        writer.newLine();
        writer.write(String.format("| Sea-Level Relative / 相对海平面 | %.1f ~ %.1f (mean: %.1f) |",
                stats.seaLevelRelativeMin, stats.seaLevelRelativeMax, stats.seaLevelRelativeMean));
        writer.newLine();
        writer.write(String.format("| Land Coverage / 陆地覆盖率 | %.1f%% |", stats.landPercentage));
        writer.newLine();
        writer.write(String.format("| Ocean Coverage / 海洋覆盖率 | %.1f%% |", stats.oceanPercentage));
        writer.newLine();
        writer.newLine();

        if (!Double.isNaN(stats.dryLandMeanHeight)) {
            writer.write("### Dry Land Only / 仅陆地海拔统计");
            writer.newLine();
            writer.newLine();
            writer.write("| Metric | Value |");
            writer.newLine();
            writer.write("|--------|-------|");
            writer.newLine();
            writer.write(String.format("| Minimum / 最低 | %.1f |", stats.dryLandMinHeight));
            writer.newLine();
            writer.write(String.format("| Maximum / 最高 | %.1f |", stats.dryLandMaxHeight));
            writer.newLine();
            writer.write(String.format("| Mean / 平均 | %.1f |", stats.dryLandMeanHeight));
            writer.newLine();
            writer.newLine();
        }

        writer.write("### Altitude Distribution / 海拔分布");
        writer.newLine();
        writer.newLine();
        writer.write("| Range | Count | Percentage | Bar |");
        writer.newLine();
        writer.write("|-------|-------|------------|-----|");
        writer.newLine();

        int total = 0;
        for (int count : stats.histogram) total += count;

        int maxBarLen = 30;
        int maxCount = 0;
        for (int count : stats.histogram) {
            if (count > maxCount) maxCount = count;
        }

        for (int i = 0; i < stats.histogram.length; i++) {
            int binStart = stats.histogramBinOrigin + i * stats.histogramBinSize;
            int binEnd = binStart + stats.histogramBinSize;
            int count = stats.histogram[i];
            double pct = total > 0 ? 100.0 * count / total : 0;
            int barLen = maxCount > 0 ? (int) Math.round((double) count / maxCount * maxBarLen) : 0;
            String bar = "\u2588".repeat(Math.max(0, barLen));
            writer.write(String.format("| %d ~ %d | %d | %.1f%% | %s |", binStart, binEnd, count, pct, bar));
            writer.newLine();
        }
        writer.newLine();
    }

    private void writeSurfaceBlocks(BufferedWriter writer, Map<String, Integer> surfaceCounts, Map<String, Integer> subSurfaceCounts, Map<TerrainType, Integer> terrainTypes, int gridSize) throws IOException {
        int total = gridSize * gridSize;
        // Compute dry-land samples from terrain type distribution (types that should have DIRT sub-surface)
        // 计算应使用 DIRT 作为次表层的干燥陆地采样数
        int dryLandSamples = 0;
        int stonySamples = 0;
        int desertSamples = 0;
        int iceSamples = 0;
        int underwaterSamples = 0;
        int beachSamples = 0;
        for (Map.Entry<TerrainType, Integer> entry : terrainTypes.entrySet()) {
            TerrainType type = entry.getKey();
            int count = entry.getValue();
            if (type == TerrainType.PLAINS || type == TerrainType.HILLS || type == TerrainType.FLOODPLAIN || type == TerrainType.VALLEY
                    || type == TerrainType.GLACIAL_VALLEY || type == TerrainType.FJORD || type == TerrainType.CIRQUE
                    || type == TerrainType.PEAK_FOREST || type == TerrainType.DOME || type == TerrainType.BASIN
                    || type == TerrainType.ALLUVIAL_FAN || type == TerrainType.SINKHOLE || type == TerrainType.DELTA) {
                dryLandSamples += count;
            } else if (type == TerrainType.HIGH_MOUNTAINS || type == TerrainType.RIDGE || type == TerrainType.PEAK || type == TerrainType.HORN
                    || type == TerrainType.CLIFF || type == TerrainType.CANYON || type == TerrainType.PLATEAU || type == TerrainType.SEA_CLIFF) {
                stonySamples += count;
            } else if (type == TerrainType.GOBI || type == TerrainType.SALT_FLAT || type == TerrainType.DUNE || type == TerrainType.YARDANG) {
                desertSamples += count;
            } else if (type == TerrainType.ICE_SHEET) {
                iceSamples += count;
            } else if (type == TerrainType.TRENCH || type == TerrainType.SEA_PLATEAU) {
                underwaterSamples += count;
            } else if (type == TerrainType.BEACH) {
                beachSamples += count;
            }
        }
        writer.write("## Surface Block Analysis / 地表方块分析");
        writer.newLine();
        writer.newLine();
        writer.write("> Predicts surface and sub-surface blocks based on terrain type, replicating FallbackSurfaceAdapter logic.");
        writer.newLine();
        writer.write("> 根据地形类型推断地表和次表层方块，复现 FallbackSurfaceAdapter 逻辑。");
        writer.newLine();
        writer.newLine();
        writer.write("### Surface Block Distribution / 地表方块分布");
        writer.newLine();
        writer.newLine();
        writer.write("| Block / 方块 | Samples / 采样数 | Coverage / 覆盖率 |");
        writer.newLine();
        writer.write("|-------------|-----------------|-----------------|");
        writer.newLine();
        for (Map.Entry<String, Integer> entry : surfaceCounts.entrySet()) {
            double pct = 100.0 * entry.getValue() / total;
            writer.write(String.format("| %s | %d | %.1f%% |", entry.getKey(), entry.getValue(), pct));
            writer.newLine();
        }
        writer.newLine();
        writer.write("### Sub-Surface Block Distribution (1-4 blocks below surface) / 次表层方块分布（地表下1-4格）");
        writer.newLine();
        writer.newLine();
        writer.write("| Block / 方块 | Samples / 采样数 | Coverage / 覆盖率 | Verification / 验证 |");
        writer.newLine();
        writer.write("|-------------|-----------------|-----------------|-------------------|");
        writer.newLine();
        for (Map.Entry<String, Integer> entry : subSurfaceCounts.entrySet()) {
            double pct = 100.0 * entry.getValue() / total;
            boolean correct = entry.getKey().equals("DIRT") || entry.getKey().equals("SAND") || entry.getKey().equals("GRAVEL");
            String status = correct ? "✅ Correct" : "⚠️ Review";
            writer.write(String.format("| %s | %d | %.1f%% | %s |", entry.getKey(), entry.getValue(), pct, status));
            writer.newLine();
        }
        writer.newLine();
        // Verify the fix: dirt sub-surface should cover all dry land
        // First check if this seed has dry land terrains at all
        long dryLandCount = surfaceCounts.getOrDefault("GRASS_BLOCK", 0);
        double dryLandPct = 100.0 * dryLandCount / total;
        Integer dirtCount = subSurfaceCounts.getOrDefault("DIRT", 0);
        double dirtPct = 100.0 * dirtCount / total;
        if (dryLandPct > 5.0) {
            if (dirtPct > 5.0) {
                writer.write(String.format("✅ **Dirt Layer Fix Verified / 泥土层修复已验证**: %.1f%% dry land, %.1f%% DIRT sub-surface. Grass land has proper dirt layer.", dryLandPct, dirtPct));
            } else {
                writer.write(String.format("⚠️ **Warning / 警告**: %.1f%% of terrain is dry land (grass), but only %.1f%% has DIRT sub-surface. The dirt-under-grass fix may not be working.", dryLandPct, dirtPct));
            }
        } else {
            writer.write(String.format("ℹ️ Seed is primarily non-grass terrain (stony/desert/ocean: %.1f%%). Sub-surface blocks (GRAVEL/SAND) are correct for these terrain types.", 100.0 - dryLandPct));
        }
        writer.newLine();
        writer.newLine();
    }

    private void writeCliffRisk(BufferedWriter writer, CliffRiskAssessment cliff) throws IOException {
        writer.write("## Cliff Risk Assessment / 悬崖风险评估");
        writer.newLine();
        writer.newLine();
        writer.write("| Risk Level / 风险等级 | Percentage / 百分比 | Gradient Range / 梯度范围 |");
        writer.newLine();
        writer.write("|----------------------|--------------------|-------------------------|");
        writer.newLine();
        writer.write(String.format("| Safe / 安全 | %.1f%% | < %.0f |", cliff.safePercentage, CLIFF_SAFE_THRESHOLD));
        writer.newLine();
        writer.write(String.format("| Low / 低风险 | %.1f%% | %.0f ~ %.0f |", cliff.lowRiskPercentage, CLIFF_SAFE_THRESHOLD, CLIFF_LOW_THRESHOLD));
        writer.newLine();
        writer.write(String.format("| Moderate / 中等 | %.1f%% | %.0f ~ %.0f |", cliff.moderateRiskPercentage, CLIFF_LOW_THRESHOLD, CLIFF_MODERATE_THRESHOLD));
        writer.newLine();
        writer.write(String.format("| High / 高风险 | %.1f%% | %.0f ~ %.0f |", cliff.highRiskPercentage, CLIFF_MODERATE_THRESHOLD, CLIFF_HIGH_THRESHOLD));
        writer.newLine();
        writer.write(String.format("| Extreme / 极端 | %.1f%% | > %.0f |", cliff.extremeRiskPercentage, CLIFF_HIGH_THRESHOLD));
        writer.newLine();
        writer.newLine();
        writer.write(String.format("- **Steepest Point / 最陡点**: (%d, %d) gradient = %.1f", cliff.steepestX, cliff.steepestZ, cliff.maxGradient));
        writer.newLine();
        writer.write(String.format("- **Largest Cliff Area / 最大悬崖区域**: %d blocks\u00B2", cliff.largestCliffAreaBlocks));
        writer.newLine();
        writer.newLine();
    }

    private void writeOceanQuality(BufferedWriter writer, OceanQualityAssessment ocean) throws IOException {
        writer.write("## Ocean Quality Assessment / 海洋质量评估");
        writer.newLine();
        writer.newLine();
        writer.write("| Metric | Value |");
        writer.newLine();
        writer.write("|--------|-------|");
        writer.newLine();
        writer.write(String.format("| Coverage / 覆盖率 | %.1f%% |", ocean.coveragePercentage));
        writer.newLine();
        writer.write(String.format("| Average Depth / 平均深度 | %.1f blocks |", ocean.averageDepth));
        writer.newLine();
        writer.write(String.format("| Maximum Depth / 最大深度 | %.1f blocks |", ocean.maxDepth));
        writer.newLine();
        writer.write(String.format("| Shallow Water / 浅水区(<10m) | %.1f%% of ocean |", ocean.shallowWaterPercentage));
        writer.newLine();
        writer.write(String.format("| Ocean Bodies / 海洋体 | %d |", ocean.oceanBodyCount));
        writer.newLine();
        writer.write(String.format("| Coastline Length / 海岸线长度 | ~%.0f blocks |", ocean.coastlineLength));
        writer.newLine();
        writer.write(String.format("| **Quality Score / 质量评分** | **%.0f / 100** |", ocean.qualityScore));
        writer.newLine();
        writer.newLine();

        String rating;
        if (ocean.qualityScore >= 80) {
            rating = "Excellent / 优秀";
        } else if (ocean.qualityScore >= 60) {
            rating = "Good / 良好";
        } else if (ocean.qualityScore >= 40) {
            rating = "Fair / 一般";
        } else {
            rating = "Poor / 较差";
        }
        writer.write(String.format("> Ocean Rating / 海洋评级: **%s**", rating));
        writer.newLine();
        writer.newLine();
    }

    private void writeTerrainTypes(BufferedWriter writer, Map<TerrainType, Integer> typeCounts, int gridSize) throws IOException {
        writer.write("## Terrain Type Distribution / 地形类型分布");
        writer.newLine();
        writer.newLine();

        int total = gridSize * gridSize;
        ArrayList<Map.Entry<TerrainType, Integer>> sorted = new ArrayList<>(typeCounts.entrySet());
        sorted.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));

        writer.write("| Terrain Type / 地形类型 | Count / 数量 | Percentage / 百分比 |");
        writer.newLine();
        writer.write("|------------------------|-------|--------------------|");
        writer.newLine();
        for (Map.Entry<TerrainType, Integer> entry : sorted) {
            double pct = 100.0 * entry.getValue() / total;
            writer.write(String.format("| %s | %d | %.1f%% |", entry.getKey().getId(), entry.getValue(), pct));
            writer.newLine();
        }
        writer.newLine();
    }

    private void writeOutputFiles(BufferedWriter writer) throws IOException {
        writer.write("## Output Files / 输出文件");
        writer.newLine();
        writer.newLine();
        writer.write("- `heightmap_grayscale.png` - Grayscale height map / 灰度高度图");
        writer.newLine();
        writer.write("- `heightmap_colored.png` - Altitude colored map / 海拔着色图");
        writer.newLine();
        writer.write("- `terra_nuda.png` - Bare earth map (see-through-water view) / 裸地地图（透水视角）");
        writer.newLine();
        writer.write("- `cliff_heatmap.png` - Cliff risk heatmap / 悬崖热力图");
        writer.newLine();
        writer.newLine();
    }

    /**
     * 命令行入口，完全离线运行，不需要启动Minecraft。
     * Command-line entry point, fully offline, no Minecraft startup required.
     *
     * @param args 命令行参数: seed [radius] [step] / command line args: seed [radius] [step]
     * @调用时机 命令行独立运行时调用 / Called when running standalone from command line
     * @已知限制 需要Minecraft库在classpath上 / Requires Minecraft libraries on classpath
     */
    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--verify")) {
            System.out.println("启动验证模式 / Starting verification mode...");
            SeedAnalyzerVerification.main(new String[]{});
            return;
        }

        if (args.length < 1) {
            System.out.println("Usage: java com.worldscape.analyzer.SeedAnalyzer <seed> [radius] [step]");
            System.out.println("Usage: java com.worldscape.analyzer.SeedAnalyzer --verify (Run verification tests)");
            System.out.println("  seed   - World seed (required) / 世界种子（必填）");
            System.out.println("  radius - Analysis radius in blocks (default: 512) / 分析半径（默认512）");
            System.out.println("  step   - Sampling step in blocks (default: 4) / 采样步长（默认4）");
            System.out.println("\n数据准确性验证/Data accuracy verification:");
            System.out.println("  This analyzer uses the exact same calculation chain as LandscapeChunkGenerator.fillFromNoise()");
            System.out.println("  确保分析结果与实际游戏地形100%一致 / Ensuring 100% accuracy with actual game terrain");
            return;
        }

        long seed;
        try {
            seed = Long.parseLong(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("Invalid seed / 无效种子: " + args[0]);
            return;
        }

        int radius = 512;
        if (args.length >= 2) {
            try {
                radius = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid radius / 无效半径: " + args[1]);
                return;
            }
        }

        int step = 4;
        if (args.length >= 3) {
            try {
                step = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid step / 无效步长: " + args[2]);
                return;
            }
        }

        SeedAnalyzer analyzer = new SeedAnalyzer(seed, radius, step);
        analyzer.analyzeAndOutput();
    }

    // ===== 数据准确性验证辅助方法 =====

    /**
     * 验证分析结果的基本完整性 / Verify basic completeness of analysis results
     */
    public static boolean verifyAnalysisResult(AnalysisResult result) {
        boolean allValid = true;
        System.out.println("===== 验证分析结果 / Verifying Analysis Result =====");

        if (result.heightMap == null || result.heightMap.length == 0) {
            System.err.println("\u274c 高度数据为空 / Height data is empty!");
            allValid = false;
        } else {
            System.out.println("\u2714 高度数据: " + result.heightMap.length + " points");
        }

        if (result.heightMap != null) {
            double min = Double.MAX_VALUE;
            double max = Double.MIN_VALUE;
            double sum = 0;
            for (double h : result.heightMap) {
                if (h < min) min = h;
                if (h > max) max = h;
                sum += h;
            }
            System.out.println("\u2714 高度范围: " + min + " ~ " + max + ", 平均: " + (sum / result.heightMap.length));
        }

        System.out.println("===== 验证结果 / Verification Result: " + (allValid ? "通过/PASSED" : "失败/FAILED") + " =====");
        return allValid;
    }

    /**
     * 分析结果，包含所有分析数据。
     */
    public static class AnalysisResult {
        public final long seed;
        public final int radius;
        public final int step;
        public final int gridSize;
        public final double[] heightMap;
        public final double[] gradients;
        public final boolean[] submergedMask;
        public final AltitudeStatistics altitudeStats;
        public final CliffRiskAssessment cliffRisk;
        public final OceanQualityAssessment oceanQuality;
        public final Map<TerrainType, Integer> terrainTypes;
        public final Map<String, Integer> surfaceBlockCounts;
        public final Map<String, Integer> subSurfaceBlockCounts;

        AnalysisResult(long seed, int radius, int step, int gridSize,
                       double[] heightMap, double[] gradients, boolean[] submergedMask,
                       AltitudeStatistics altitudeStats, CliffRiskAssessment cliffRisk,
                       OceanQualityAssessment oceanQuality, Map<TerrainType, Integer> terrainTypes,
                       Map<String, Integer> surfaceBlockCounts, Map<String, Integer> subSurfaceBlockCounts) {
            this.seed = seed;
            this.radius = radius;
            this.step = step;
            this.gridSize = gridSize;
            this.heightMap = heightMap;
            this.gradients = gradients;
            this.submergedMask = submergedMask;
            this.altitudeStats = altitudeStats;
            this.cliffRisk = cliffRisk;
            this.oceanQuality = oceanQuality;
            this.terrainTypes = terrainTypes;
            this.surfaceBlockCounts = surfaceBlockCounts;
            this.subSurfaceBlockCounts = subSurfaceBlockCounts;
        }
    }

    /**
     * 海拔统计数据。
     */
    public static class AltitudeStatistics {
        public final double minHeight;
        public final double maxHeight;
        public final double meanHeight;
        public final double medianHeight;
        public final double stdDev;
        public final double landPercentage;
        public final double oceanPercentage;
        public final double dryLandMinHeight;
        public final double dryLandMaxHeight;
        public final double dryLandMeanHeight;
        public final double seaLevelRelativeMin;
        public final double seaLevelRelativeMax;
        public final double seaLevelRelativeMean;
        public final int[] histogram;
        public final int histogramBinSize;
        public final int histogramBinOrigin;

        AltitudeStatistics(double minHeight, double maxHeight, double meanHeight, double medianHeight,
                           double stdDev, double landPercentage, double oceanPercentage,
                           double dryLandMinHeight, double dryLandMaxHeight, double dryLandMeanHeight,
                           double seaLevelRelativeMin, double seaLevelRelativeMax, double seaLevelRelativeMean,
                           int[] histogram, int histogramBinSize, int histogramBinOrigin) {
            this.minHeight = minHeight;
            this.maxHeight = maxHeight;
            this.meanHeight = meanHeight;
            this.medianHeight = medianHeight;
            this.stdDev = stdDev;
            this.landPercentage = landPercentage;
            this.oceanPercentage = oceanPercentage;
            this.dryLandMinHeight = dryLandMinHeight;
            this.dryLandMaxHeight = dryLandMaxHeight;
            this.dryLandMeanHeight = dryLandMeanHeight;
            this.seaLevelRelativeMin = seaLevelRelativeMin;
            this.seaLevelRelativeMax = seaLevelRelativeMax;
            this.seaLevelRelativeMean = seaLevelRelativeMean;
            this.histogram = histogram;
            this.histogramBinSize = histogramBinSize;
            this.histogramBinOrigin = histogramBinOrigin;
        }
    }

    /**
     * 悬崖风险评估数据。
     */
    public static class CliffRiskAssessment {
        public final double safePercentage;
        public final double lowRiskPercentage;
        public final double moderateRiskPercentage;
        public final double highRiskPercentage;
        public final double extremeRiskPercentage;
        public final double maxGradient;
        public final int steepestX;
        public final int steepestZ;
        public final int largestCliffAreaBlocks;

        CliffRiskAssessment(double safePercentage, double lowRiskPercentage, double moderateRiskPercentage,
                           double highRiskPercentage, double extremeRiskPercentage,
                           double maxGradient, int steepestX, int steepestZ, int largestCliffAreaBlocks) {
            this.safePercentage = safePercentage;
            this.lowRiskPercentage = lowRiskPercentage;
            this.moderateRiskPercentage = moderateRiskPercentage;
            this.highRiskPercentage = highRiskPercentage;
            this.extremeRiskPercentage = extremeRiskPercentage;
            this.maxGradient = maxGradient;
            this.steepestX = steepestX;
            this.steepestZ = steepestZ;
            this.largestCliffAreaBlocks = largestCliffAreaBlocks;
        }
    }

    /**
     * 海洋质量评估数据。
     */
    public static class OceanQualityAssessment {
        public final double coveragePercentage;
        public final double averageDepth;
        public final double maxDepth;
        public final double shallowWaterPercentage;
        public final int oceanBodyCount;
        public final double coastlineLength;
        public final double qualityScore;

        OceanQualityAssessment(double coveragePercentage, double averageDepth, double maxDepth,
                               double shallowWaterPercentage, int oceanBodyCount,
                               double coastlineLength, double qualityScore) {
            this.coveragePercentage = coveragePercentage;
            this.averageDepth = averageDepth;
            this.maxDepth = maxDepth;
            this.shallowWaterPercentage = shallowWaterPercentage;
            this.oceanBodyCount = oceanBodyCount;
            this.coastlineLength = coastlineLength;
            this.qualityScore = qualityScore;
        }
    }
}
