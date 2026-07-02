package com.worldscape.terrain;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.worldscape.terrain.MacroRegionInfo;
import com.worldscape.util.SeedDeriver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MacroVoronoiSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(MacroVoronoiSystem.class);
    public static final int REGION_CELL_SIZE = 2048;
    // Legacy single-value midpoints per tier, kept for backward compatibility
    // (e.g. transitionWidth estimation). Now updated to match range midpoints.
    // 旧的单值中点（按层级），保留用于向后兼容（如过渡宽度估算），现已与范围中点对齐。
    private static final int[] ELEVATION_BASE_HEIGHTS = new int[]{-80, -20, 50, 65, 165, 290};
    // Tier base-height RANGES [min, max], with adjacent tiers overlapping so that
    // boundary transitions can interpolate continuously within the overlap region.
    // Defaults come from WorldScapeConstants; loadTierHeightConfig() can override
    // them from JSON at startup. Terrain generation only reads these arrays (pure).
    //
    // 层级基准高度【范围】[min, max]，相邻层级范围有重叠，使边界过渡可在重叠区内
    // 连续插值。默认值来自 WorldScapeConstants；loadTierHeightConfig() 可在启动时用
    // JSON 覆盖。地形生成仅读取这些数组（纯函数，无文件 IO）。
    private static final int[] TIER_MIN_HEIGHTS = new int[]{
        WorldScapeConstants.TIER_0_MIN_HEIGHT, WorldScapeConstants.TIER_1_MIN_HEIGHT,
        WorldScapeConstants.TIER_2_MIN_HEIGHT, WorldScapeConstants.TIER_3_MIN_HEIGHT,
        WorldScapeConstants.TIER_4_MIN_HEIGHT, WorldScapeConstants.TIER_5_MIN_HEIGHT
    };
    private static final int[] TIER_MAX_HEIGHTS = new int[]{
        WorldScapeConstants.TIER_0_MAX_HEIGHT, WorldScapeConstants.TIER_1_MAX_HEIGHT,
        WorldScapeConstants.TIER_2_MAX_HEIGHT, WorldScapeConstants.TIER_3_MAX_HEIGHT,
        WorldScapeConstants.TIER_4_MAX_HEIGHT, WorldScapeConstants.TIER_5_MAX_HEIGHT
    };
    // JSON config file for tunable tier height ranges (optional override).
    // 可调层级高度范围的 JSON 配置文件（可选覆盖）。
    private static final String TIER_HEIGHT_CONFIG_FILE = "config/worldscape/tier_heights.json";
    private static volatile boolean tierHeightConfigLoaded = false;
    // Removed unused transition-width constants: HEIGHT_DIFF_TO_BANDWIDTH_FACTOR, MIN_TRANSITION_WIDTH,
    // MAX_TRANSITION_WIDTH, WATER_TRANSITION_MULTIPLIER, OCEAN_TIER_THRESHOLD.
    // They were dead code; actual transition logic used different hardcoded values or was removed.
    // 已移除未使用的过渡宽度常量：HEIGHT_DIFF_TO_BANDWIDTH_FACTOR、MIN_TRANSITION_WIDTH、
    // MAX_TRANSITION_WIDTH、WATER_TRANSITION_MULTIPLIER、OCEAN_TIER_THRESHOLD。
    // 它们是死代码；实际过渡逻辑使用了不同的硬编码值或已被移除。
    // @AESTHETIC: Spawn ocean constraint radius (in Voronoi cells, ~2048 blocks each).
    // RADIUS=0 limits ocean enforcement to the spawn cell itself, letting dist≥1 cells
    // develop natural tier distribution including mountains.
    // RADIUS=0 将海洋约束限制在出生点所在单元本身，距离≥1的单元可获得自然 Tier 分布（含山地）。
    private static final int SPAWN_OCEAN_RADIUS_CELLS = 0;
    private static final double SPAWN_MAX_OCEAN_WEIGHT = 0.35;
    private final long worldSeed;
    private final int seaLevel;
    private final long voronoiXSeed;
    private final long voronoiZSeed;
    private final long elevationSeed;
    private final long tectonicSeed;
    private final long riftSeed;
    private final long climateSeed;
    // Spatial coherent noise for tier assignment — replaces random.nextInt(6)
    // 空间连贯噪声用于层级分配 — 替代 random.nextInt(6)
    private final NormalNoise tierNoise;
    private static final int MAX_CACHE_SIZE = 10000;
    private final Map<Long, ControlPoint> controlPointCache = Collections.synchronizedMap(new LinkedHashMap<Long, ControlPoint>(1024, 0.75f, true){

        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, ControlPoint> eldest) {
            return this.size() > 10000;
        }
    });
    private final Map<Long, Integer> adjustedTierCache = Collections.synchronizedMap(new LinkedHashMap<Long, Integer>(1024, 0.75f, true){

        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Integer> eldest) {
            return this.size() > 10000;
        }
    });
    private final Map<Long, ControlPoint[]> cellGridCache = Collections.synchronizedMap(new LinkedHashMap<Long, ControlPoint[]>(1024, 0.75f, true){

        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, ControlPoint[]> eldest) {
            return this.size() > 10000;
        }
    });

    public MacroVoronoiSystem(long worldSeed, int seaLevel) {
        this.worldSeed = worldSeed;
        this.seaLevel = seaLevel;
        this.voronoiXSeed = SeedDeriver.deriveMacroVoronoiX(worldSeed);
        this.voronoiZSeed = SeedDeriver.deriveMacroVoronoiZ(worldSeed);
        this.elevationSeed = SeedDeriver.deriveMacroElevationSeed(worldSeed);
        this.tectonicSeed = SeedDeriver.deriveMacroTectonicSeed(worldSeed);
        this.riftSeed = SeedDeriver.deriveMacroRiftSeed(worldSeed);
        this.climateSeed = SeedDeriver.deriveMacroClimateSeed(worldSeed);
        // Derive a dedicated seed for tier noise to ensure spatial coherence
        // 推导专用种子用于层级噪声，确保空间连贯性
        long tierNoiseSeed = SeedDeriver.deriveSeed(worldSeed, 756324891011L);
        // Multi-octave noise for spatial tier variation: 4 octaves from -3 to 0
        // 多倍频程噪声实现空间层级变化：从 -3 到 0 共 4 个倍频程
        this.tierNoise = NormalNoise.create(RandomSource.create(tierNoiseSeed), -3, new double[]{1.0, 0.5, 0.25, 0.125});
    }

    public MacroRegionInfo getRegionInfo(int x, int z) {
        int cellZ;
        int cellX = Math.floorDiv(x, 2048);
        long cellKey = (long)cellX << 32 | (long)(cellZ = Math.floorDiv(z, 2048)) & 0xFFFFFFFFL;
        ControlPoint[] gridPoints = this.cellGridCache.get(cellKey);
        if (gridPoints == null) {
            gridPoints = new ControlPoint[9];
            for (int dx = -1; dx <= 1; ++dx) {
                for (int dz = -1; dz <= 1; ++dz) {
                    int nx = cellX + dx;
                    int nz = cellZ + dz;
                    gridPoints[(dx + 1) * 3 + (dz + 1)] = this.getControlPoint(nx, nz);
                }
            }
            this.cellGridCache.put(cellKey, gridPoints);
        }
        double minDistSq = Double.MAX_VALUE;
        int nearestCellX = cellX;
        int nearestCellZ = cellZ;
        for (int dx = -1; dx <= 1; ++dx) {
            for (int dz = -1; dz <= 1; ++dz) {
                ControlPoint cp = gridPoints[(dx + 1) * 3 + (dz + 1)];
                double dx2 = (double)x - cp.x;
                double dz2 = (double)z - cp.z;
                double distSq = dx2 * dx2 + dz2 * dz2;
                if (!(distSq < minDistSq)) continue;
                minDistSq = distSq;
                nearestCellX = cellX + dx;
                nearestCellZ = cellZ + dz;
            }
        }
        int primaryTier = this.getAdjustedElevationTier(nearestCellX, nearestCellZ);
        double secondMinDistSq = Double.MAX_VALUE;
        int secondCellX = -1;
        int secondCellZ = -1;
        for (int dx = -1; dx <= 1; ++dx) {
            for (int dz = -1; dz <= 1; ++dz) {
                int nx = cellX + dx;
                int nz = cellZ + dz;
                if (nx == nearestCellX && nz == nearestCellZ) continue;
                ControlPoint cp = gridPoints[(dx + 1) * 3 + (dz + 1)];
                double dx2 = (double)x - cp.x;
                double dz2 = (double)z - cp.z;
                double distSq = dx2 * dx2 + dz2 * dz2;
                if (!(distSq < secondMinDistSq)) continue;
                secondMinDistSq = distSq;
                secondCellX = nx;
                secondCellZ = nz;
            }
        }
        // 使用层级高度范围 + tierNoise 驱动的 lerp，替代单一硬编码基准高度。
        // 范围相邻层级有重叠，配合边界处 lerp 趋近极值，消除 Tier 边界的悬崖/断层。
        // Lerp within tier height ranges (driven by tierNoise) instead of single hardcoded values.
        // Adjacent-tier range overlap + boundary-approaching lerp eliminates cliff/fault artifacts.
        double primaryBaseHeight = this.getTierBaseHeightLerped(primaryTier, nearestCellX, nearestCellZ);
        double blendWeight = 1.0;
        int transitionWidth = 800;
        int secondTier = primaryTier;
        double secondBaseHeight = primaryBaseHeight;
        if (secondCellX != -1) {
            secondTier = this.getAdjustedElevationTier(secondCellX, secondCellZ);
            secondBaseHeight = this.getTierBaseHeightLerped(secondTier, secondCellX, secondCellZ);
            int tierDiff = Math.abs(primaryTier - secondTier);
            int primaryBase = (int) primaryBaseHeight;
            int secondBase = (int) secondBaseHeight;
            int actualHeightDiff = Math.abs(primaryBase - secondBase);
            int calculatedWidth = (int)((double)actualHeightDiff * 10.0);
            // Let transitionWidth be naturally determined by height difference,
            // only enforce the lower bound to prevent razor-thin boundaries.
            // 让 transitionWidth 由高度差自然决定，仅强制下限以防止极窄边界。
            transitionWidth = Math.max(800, calculatedWidth);
            boolean bothUnderwater = primaryBase < this.seaLevel && secondBase < this.seaLevel;
            boolean primaryIsOcean = primaryTier < 2;
            boolean secondIsOcean = secondTier < 2;
            if (bothUnderwater && primaryIsOcean && secondIsOcean) {
                transitionWidth = (int)((double)transitionWidth * 6.0);
            }
            double primaryDist = Math.sqrt(minDistSq);
            double secondDist = Math.sqrt(secondMinDistSq);
            // 使用较大的 epsilon (1.0) 避免近距离时 distRatio 数值不稳定
            // Use larger epsilon (1.0) to avoid numerical instability in distRatio at close distances
            double distRatio = primaryDist / (primaryDist + secondDist + 1.0);
            // Keep only lower bound protection to prevent razor-thin blends
            // 仅保留下限保护以防止过度锐利的混合
            double halfBand = Math.max(0.08, (double)(transitionWidth / 2048) * 2.0);
            double edge0 = 0.5 - halfBand;
            double edge1 = 0.5 + halfBand;
            blendWeight = 1.0 - SeedDeriver.smoothstep(edge0, edge1, distRatio);
        }
        double blendedBaseHeight = primaryBaseHeight * blendWeight + secondBaseHeight * (1.0 - blendWeight);
        MacroRegionInfo.TectonicType tectonic = this.determineTectonicType(nearestCellX, nearestCellZ, primaryTier);
        MacroRegionInfo.ClimateZone climate = this.determineClimateZone(nearestCellX, nearestCellZ);
        return new MacroRegionInfo(primaryTier, secondTier, blendedBaseHeight, tectonic, climate, blendWeight, transitionWidth, nearestCellX, nearestCellZ);
    }

    private ControlPoint getControlPoint(int cellX, int cellZ) {
        long key = (long)cellX << 32 | (long)cellZ & 0xFFFFFFFFL;
        return this.controlPointCache.computeIfAbsent(key, k -> {
            long xSeed = SeedDeriver.deriveSeed(this.voronoiXSeed, (long)cellX * 31L + (long)cellZ * 17L);
            RandomSource xRandom = RandomSource.create((long)xSeed);
            double xOffset = (xRandom.nextDouble() - 0.5) * 2048.0 * 0.6;
            double px = (double)(cellX * 2048) + 1024.0 + xOffset;
            long zSeed = SeedDeriver.deriveSeed(this.voronoiZSeed, (long)cellX * 17L + (long)cellZ * 31L);
            RandomSource zRandom = RandomSource.create((long)zSeed);
            double zOffset = (zRandom.nextDouble() - 0.5) * 2048.0 * 0.6;
            double pz = (double)(cellZ * 2048) + 1024.0 + zOffset;
            return new ControlPoint(px, pz);
        });
    }

    private int getRawElevationTier(int cellX, int cellZ) {
        // Use spatial coherent noise for tier assignment — ensures smooth transitions
        // between adjacent cells instead of random jumps.
        // 使用空间连贯噪声进行层级分配 — 确保相邻单元之间平滑过渡，而非随机跳变。
        double noiseValue = this.tierNoise.getValue(cellX * 0.05, 0.0, cellZ * 0.05);
        // Map noise value [-1, 1] to tier [0, 5] and clamp
        // 将噪声值 [-1, 1] 映射到层级 [0, 5] 并夹紧
        int tier = (int)((noiseValue + 1.0) * 3.0);
        tier = Math.max(0, Math.min(5, tier));

        int distFromSpawn = Math.max(Math.abs(cellX), Math.abs(cellZ));
        if (distFromSpawn <= SPAWN_OCEAN_RADIUS_CELLS) {
            long seed = SeedDeriver.deriveSeed(this.elevationSeed, (long)cellX * 31L + (long)cellZ * 17L);
            RandomSource random = RandomSource.create(seed);
            double oceanWeight = SPAWN_MAX_OCEAN_WEIGHT * (1.0 - (double)distFromSpawn / 3.0);
            if (random.nextDouble() < oceanWeight && tier > 1 && tier < 4) {
                tier = random.nextInt(2);
            }
        }
        return tier;
    }

    public int getAdjustedElevationTier(int cellX, int cellZ) {
        long key = (long)cellX << 32 | (long)cellZ & 0xFFFFFFFFL;
        return this.adjustedTierCache.computeIfAbsent(key, k -> {
            int rawTier = this.getRawElevationTier(cellX, cellZ);
            // Diagnostic: log cell tier info for the first 30 unique cells
            // 诊断：记录前 30 个唯一单元的层级信息
            if (adjustedTierCache.size() < 30) {
                LOGGER.info("[MACRO_DIAG] cell({},{}): rawTier={}", cellX, cellZ, rawTier);
            }
            return rawTier;
        });
    }

    private MacroRegionInfo.TectonicType determineTectonicType(int cellX, int cellZ, int elevationTier) {
        long seed = SeedDeriver.deriveSeed(this.tectonicSeed, (long)cellX * 31L + (long)cellZ * 17L);
        RandomSource random = RandomSource.create((long)seed);
        // Removed dead code: elevationTier >= 6 was unreachable (max tier is 5).
        // If tectonic type expansion is needed in the future, re-add here with higher tier support.
        // 已移除死代码：elevationTier >= 6 永远不可达（最大 tier 为 5）。
        // 若未来需要扩展构造类型，在此处重新添加并支持更高 tier。
        if (elevationTier >= 3) {
            double r = random.nextDouble();
            if (r < 0.2) {
                return MacroRegionInfo.TectonicType.RIFT_ZONE;
            }
            return MacroRegionInfo.TectonicType.CRATON;
        }
        return MacroRegionInfo.TectonicType.CRATON;
    }

    private MacroRegionInfo.ClimateZone determineClimateZone(int cellX, int cellZ) {
        long seed = SeedDeriver.deriveSeed(this.climateSeed, (long)cellX * 31L + (long)cellZ * 17L);
        RandomSource random = RandomSource.create((long)seed);
        double r = random.nextDouble();
        if (r < 0.15) {
            return MacroRegionInfo.ClimateZone.ARID;
        }
        if (r < 0.25) {
            return MacroRegionInfo.ClimateZone.GLACIAL;
        }
        if (r < 0.6) {
            return MacroRegionInfo.ClimateZone.TEMPERATE;
        }
        return MacroRegionInfo.ClimateZone.TROPICAL;
    }

    public static int getBaseHeightForTier(int tier) {
        if (tier < 0) {
            return ELEVATION_BASE_HEIGHTS[0];
        }
        if (tier > 5) {
            return ELEVATION_BASE_HEIGHTS[5];
        }
        return ELEVATION_BASE_HEIGHTS[tier];
    }

    /**
     * 返回层级的最低基准高度（范围下限）。
     * Returns the minimum base height for the tier (range lower bound).
     */
    public static int getTierMinHeight(int tier) {
        if (tier < 0) {
            return TIER_MIN_HEIGHTS[0];
        }
        if (tier > 5) {
            return TIER_MIN_HEIGHTS[5];
        }
        return TIER_MIN_HEIGHTS[tier];
    }

    /**
     * 返回层级的最高基准高度（范围上限）。
     * Returns the maximum base height for the tier (range upper bound).
     */
    public static int getTierMaxHeight(int tier) {
        if (tier < 0) {
            return TIER_MAX_HEIGHTS[0];
        }
        if (tier > 5) {
            return TIER_MAX_HEIGHTS[5];
        }
        return TIER_MAX_HEIGHTS[tier];
    }

    /**
     * 在层级高度范围 [minHeight, maxHeight] 内，用已有 tierNoise 连续噪声场驱动的
     * 局部分数做 lerp，替代单一硬编码基准高度。局部分数在层级内归一化到 [0,1]，
     * 边界处趋近极值；配合相邻层级范围的重叠区实现 Tier 边界的连续过渡，消除悬崖/断层。
     * 不引入新噪声场（复用驱动层级分配的同一 tierNoise）。
     * <p>
     * Lerp within the tier height range [min, max] driven by the existing tierNoise
     * continuous field, replacing the single hardcoded base value. The local fraction
     * is normalized within the tier to [0,1], approaching extremes at tier boundaries;
     * combined with adjacent-tier range overlap this yields continuous transitions
     * across Tier boundaries, eliminating cliffs. No new noise field is introduced
     * (reuses the same tierNoise that drives tier assignment).
     */
    private double getTierBaseHeightLerped(int tier, int cellX, int cellZ) {
        double minHeight = MacroVoronoiSystem.getTierMinHeight(tier);
        double maxHeight = MacroVoronoiSystem.getTierMaxHeight(tier);
        // 复用驱动层级分配的同一连续噪声场，确保空间连续性且不引入新噪声场。
        // Reuse the same continuous noise field that drives tier assignment for spatial coherence.
        double noiseValue = this.tierNoise.getValue((double)cellX * 0.05, 0.0, (double)cellZ * 0.05);
        // 层级在噪声空间的带宽为 1/3（见 getRawElevationTier 的 tier = (int)((n+1)*3) 映射）；
        // 计算层级内的局部分数：层级下边界处→0，上边界处→1，使 lerp 输出在边界处趋近
        // 该层级的极值，落入与相邻层级的重叠区。
        // Tier band width in noise space is 1/3 (see getRawElevationTier mapping); compute the
        // local fraction within the tier: 0 at the lower boundary, 1 at the upper boundary, so the
        // lerp output approaches the tier's extreme at boundaries and lands in the overlap region
        // with the adjacent tier.
        double localFraction = (noiseValue + 1.0) * 3.0 - (double)tier;
        if (localFraction < 0.0) {
            localFraction = 0.0;
        } else if (localFraction > 1.0) {
            localFraction = 1.0;
        }
        return minHeight + (maxHeight - minHeight) * localFraction;
    }

    /**
     * 从 JSON 配置文件加载层级高度范围（可选覆盖常量默认值）。
     * 应在 mod 启动时（onCommonSetup）调用一次；若文件不存在则创建默认配置。
     * 地形生成仅读取静态数组，此方法不在生成热路径中调用，保持生成纯函数。
     * <p>
     * Load tier height ranges from a JSON config file (optional override of constant defaults).
     * Should be called once at mod startup (onCommonSetup); creates a default file if absent.
     * Terrain generation only reads the static arrays — this method is NOT on the gen hot path,
     * keeping generation a pure function.
     * <p>
     * JSON 格式 / JSON format:
     * <pre>{@code
     * { "tier_heights": [
     *   { "tier": 0, "min": -120, "max": -40 },
     *   ...
     *   { "tier": 5, "min": 200, "max": 380 } ] }
     * }</pre>
     */
    public static synchronized void loadTierHeightConfig() {
        if (MacroVoronoiSystem.tierHeightConfigLoaded) {
            return;
        }
        Path configPath = Paths.get(TIER_HEIGHT_CONFIG_FILE, new String[0]);
        try {
            if (!Files.exists(configPath, new LinkOption[0])) {
                MacroVoronoiSystem.createDefaultTierHeightConfig(configPath);
            }
            String json = Files.readString(configPath);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray arr = root.getAsJsonArray("tier_heights");
            if (arr == null) {
                LOGGER.warn("[World Scape] tier_heights.json missing 'tier_heights' array; using constant defaults");
                MacroVoronoiSystem.tierHeightConfigLoaded = true;
                return;
            }
            for (JsonElement el : arr) {
                JsonObject entry = el.getAsJsonObject();
                int tier = entry.get("tier").getAsInt();
                int min = entry.get("min").getAsInt();
                int max = entry.get("max").getAsInt();
                if (tier < 0 || tier > 5) {
                    LOGGER.warn("[World Scape] tier_heights.json: ignoring out-of-range tier {}", (Object)tier);
                    continue;
                }
                if (max <= min) {
                    LOGGER.warn("[World Scape] tier_heights.json: tier {} max ({}) <= min ({}); skipping", new Object[]{tier, max, min});
                    continue;
                }
                TIER_MIN_HEIGHTS[tier] = min;
                TIER_MAX_HEIGHTS[tier] = max;
            }
            LOGGER.info("[World Scape] Loaded tier height ranges from {}", (Object)configPath);
        } catch (IOException e) {
            LOGGER.warn("[World Scape] Failed to load tier height config ({}): {}; using constant defaults", new Object[]{configPath, e.getMessage()});
        } catch (Exception e) {
            LOGGER.warn("[World Scape] Malformed tier height config ({}): {}; using constant defaults", new Object[]{configPath, e.getMessage()});
        }
        MacroVoronoiSystem.tierHeightConfigLoaded = true;
    }

    /**
     * 创建默认的层级高度范围 JSON 配置文件（基于 WorldScapeConstants 常量）。
     * Create the default tier height range JSON config file (based on WorldScapeConstants).
     */
    private static void createDefaultTierHeightConfig(Path configPath) throws IOException {
        Files.createDirectories(configPath.getParent());
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"_comment\": \"Tier base-height ranges [min,max]. Adjacent tiers MUST overlap for cliff-free transitions. / 层级基准高度范围 [min,max]，相邻层级必须重叠以消除悬崖。\",\n");
        sb.append("  \"tier_heights\": [\n");
        for (int tier = 0; tier <= 5; ++tier) {
            // 此时数组尚未被 JSON 覆盖，持有 WorldScapeConstants 常量默认值。
            // Arrays still hold WorldScapeConstants defaults (no JSON override applied yet).
            sb.append("    { \"tier\": ").append(tier)
              .append(", \"min\": ").append(TIER_MIN_HEIGHTS[tier])
              .append(", \"max\": ").append(TIER_MAX_HEIGHTS[tier])
              .append(" }");
            if (tier < 5) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n}\n");
        Files.writeString(configPath, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        LOGGER.info("[World Scape] Created default tier height config at {}", (Object)configPath);
    }

    public static double getTierMinimumHeight(int tier) {
        // Tier minimum heights enforce separation between tiers so that higher
        // tiers consistently produce higher terrain. Previously tier 3 and tier 4
        // both returned 28.0, providing no separation — tier 4 terrain (HILLS,
        // CLIFF, PLATEAU at base height 160) could be pulled down to tier 3
        // levels (base height 60) during blending, blurring the tier boundary.
        // Tier 最小高度强制层级间分离，使更高层级一致地产生更高地形。
        // 之前 tier 3 和 tier 4 都返回 28.0，没有分离 — tier 4 地形
        //（HILLS、CLIFF、PLATEAU 基准高度 160）在混合时可能被拉低到
        // tier 3 水平（基准高度 60），模糊了层级边界。
        return switch (tier) {
            case 0 -> -55.0;
            case 1 -> -28.0;
            case 2 -> -5.0;
            case 3 -> 28.0;
            case 4 -> 80.0;   // Was 28.0 — now enforces tier 3→4 height separation
            case 5 -> 120.0;  // Was 44.0 — raised to enforce tier 4→5 separation
            default -> 0.0;
        };
    }

    private static class ControlPoint {
        final double x;
        final double z;

        ControlPoint(double x, double z) {
            this.x = x;
            this.z = z;
        }
    }
}

