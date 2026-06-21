package com.worldscape.terrain;

import com.worldscape.terrain.TerrainControlPoint;
import com.worldscape.terrain.TerrainFieldSampler;
import com.worldscape.terrain.TerrainType;
import com.worldscape.util.SeedDeriver;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.util.RandomSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Coastal terrain validation constants
// 海岸地形验证常量
import com.worldscape.terrain.WorldScapeConstants;

public class ControlPointRegion {
    private static final Logger LOGGER = LoggerFactory.getLogger(ControlPointRegion.class);
    public static final int REGION_SIZE = 512;
    public static final int CELL_SIZE = 16;
    // Removed unused constants: MAX_ADJACENT_HEIGHT_DIFF, GRID_SPACING, NEIGHBOR_SEARCH_RADIUS,
    // MAX_ITERATIONS, CONVERGENCE_THRESHOLD, OFFSET_DIFF_WARN_THRESHOLD,
    // EXPECTED_MIN_CONTROL_POINTS, EXPECTED_MAX_CONTROL_POINTS.
    // They were dead code; the logic they described used hardcoded literals or was removed.
    // 已移除未使用的常量：MAX_ADJACENT_HEIGHT_DIFF、GRID_SPACING、NEIGHBOR_SEARCH_RADIUS、
    // MAX_ITERATIONS、CONVERGENCE_THRESHOLD、OFFSET_DIFF_WARN_THRESHOLD、
    // EXPECTED_MIN_CONTROL_POINTS、EXPECTED_MAX_CONTROL_POINTS。
    // 它们是死代码；所描述的逻辑要么使用硬编码字面量，要么已被移除。
    private final int regionX;
    private final int regionZ;
    private final long worldSeed;
    private final int macroElevationTier;
    private final MacroVoronoiSystem macroSystem;
    private final List<TerrainControlPoint> controlPoints;
    private final List<TerrainControlPoint>[][] cellIndex;
    private final int cellsPerSide;

    // Radius (in blocks) for ocean proximity check around coastal control points.
    // 512 = one region width; covers the 3×3 neighborhood of macro Voronoi cells.
    // 海岸控制点周围海洋邻近性检查半径（方块）。
    // 512 = 一个区域宽度；覆盖 3×3 宏观 Voronoi 单元邻域。
    private static final int OCEAN_PROXIMITY_CHECK_RADIUS = 512;

    // Probability thresholds for coastal variant upgrades when ocean is nearby.
    // When BEACH is selected and ocean is confirmed nearby, these control the chance
    // of upgrading to more interesting coastal terrain (SEA_CLIFF / FJORD).
    // 当 BEACH 被选中且确认附近有海洋时，这些阈值控制升级为更有趣海岸地形（海蚀崖/峡湾）的概率。
    private static final double SEA_CLIFF_UPGRADE_PROBABILITY = 0.15;
    private static final double FJORD_UPGRADE_PROBABILITY = 0.10;
    private static final double SEA_CLIFF_MAX_MOISTURE = 0.35;
    private static final double FJORD_MIN_MOISTURE = 0.70;
    // Salt for deterministic coastal variant seed derivation (must differ from jitter salt)
    // 用于确定性海岸变体种子推导的盐值（必须与抖动盐值不同）
    private static final long COASTAL_VARIANT_SALT = 88888888L;

    // DOME variant placement constants
    // DOME 变体放置常量
    private static final double DOME_UPGRADE_PROBABILITY = 0.20;
    private static final long DOME_VARIANT_SALT = 99999999L;

    public ControlPointRegion(int regionX, int regionZ, long worldSeed, int macroElevationTier) {
        this(regionX, regionZ, worldSeed, macroElevationTier, null);
    }

    public ControlPointRegion(int regionX, int regionZ, long worldSeed, int macroElevationTier, MacroVoronoiSystem macroSystem) {
        this.regionX = regionX;
        this.regionZ = regionZ;
        this.worldSeed = worldSeed;
        if (macroElevationTier < 0 || macroElevationTier > 5) {
            LOGGER.warn("[World Scape] Invalid macroElevationTier {} in ControlPointRegion ({},{}), clamping to valid range", new Object[]{macroElevationTier, regionX, regionZ});
            macroElevationTier = Math.max(0, Math.min(5, macroElevationTier));
        }
        this.macroElevationTier = macroElevationTier;
        this.macroSystem = macroSystem;
        this.cellsPerSide = 32;
        this.cellIndex = new List[this.cellsPerSide][this.cellsPerSide];
        this.controlPoints = new ArrayList<TerrainControlPoint>();
        this.generateControlPoints();
        this.validateControlPoints();
    }

    private void generateControlPoints() {
        long regionSeed = SeedDeriver.deriveSeed(this.worldSeed, (long)this.regionX * 31L + (long)this.regionZ * 17L + 388350381470L);
        RandomSource random = RandomSource.create((long)regionSeed);
        TerrainFieldSampler fieldSampler = TerrainFieldSampler.getOrCreate(this.worldSeed);
        // 每轴控制点数固定为 2，产生 3×3=9 个控制点覆盖 512×512 区域
        // Control points per axis is fixed at 2, producing 3×3=9 points covering a 512×512 region
        int pointsPerAxis = 2;
        ArrayList<PointData> rawPoints = new ArrayList<PointData>();
        for (int gx = 0; gx <= pointsPerAxis; ++gx) {
            for (int gz = 0; gz <= pointsPerAxis; ++gz) {
                int baseX = this.regionX * REGION_SIZE + gx * REGION_SIZE;
                int baseZ = this.regionZ * REGION_SIZE + gz * REGION_SIZE;
                int offsetX = (int)((random.nextDouble() - 0.5) * (double)REGION_SIZE * 0.75);
                int offsetZ = (int)((random.nextDouble() - 0.5) * (double)REGION_SIZE * 0.75);
                int px = baseX + offsetX;
                int pz = baseZ + offsetZ;
                double energy = fieldSampler.sampleEnergy(px, pz);
                double moisture = fieldSampler.sampleMoisture(px, pz);
                // The new 3-layer noise in sampleEnergy (main + detail + detail2) now provides
                // sufficient differentiation at control point positions, so the macroElevationTier
                // bypass is no longer needed. Use energyToTier() with macroElevationTier constraint
                // to determine tier from the enhanced energy field.
                // 新的 sampleEnergy 3层噪声（main + detail + detail2）现在在控制点位置上
                // 提供了足够的区分度，因此不再需要 macroElevationTier 绕过。
                // 使用 energyToTier() 配合 macroElevationTier 约束从增强的能量场确定等级。
                int tier = fieldSampler.energyToTier(energy, this.macroElevationTier);
                TerrainType type = fieldSampler.selectTypeByMoisture(tier, moisture, px, pz);
                // Coastal terrain validation: BEACH/DELTA must be near ocean.
                // If no ocean (macro tier 0-1) is found within OCEAN_PROXIMITY_CHECK_RADIUS,
                // replace with inland equivalent (FLOODPLAIN).
                // When ocean IS nearby, consider upgrading to SEA_CLIFF/FJORD for variety.
                // 海岸地形验证：BEACH/DELTA 必须靠近海洋。
                // 如果在 OCEAN_PROXIMITY_CHECK_RADIUS 范围内未发现海洋（宏观层级 0-1），
                // 则替换为内陆等价物（FLOODPLAIN）。
                // 当附近有海洋时，考虑升级为 SEA_CLIFF/FJORD 以增加多样性。
                type = this.validateCoastalType(type, tier, moisture, px, pz);
                // DOME placement: tier 4 "control-point-only" type.
                // DOME represents a broad uplift (dome-shaped terrain). When PLATEAU is
                // selected at tier 4, small chance to become DOME for terrain variety.
                // Uses separate deterministic seed to avoid shifting main random stream.
                // DOME 放置：tier 4 "仅控制点放置" 类型。
                // DOME 代表宽阔隆起（穹丘地形）。当 tier 4 选中 PLATEAU 时，
                // 小概率变为 DOME 以增加地形多样性。
                // 使用独立确定性种子以避免偏移主流随机流。
                if (tier == 4 && type == TerrainType.PLATEAU) {
                    long domeSeed = SeedDeriver.deriveSeed(this.worldSeed,
                        (long) px * 31L + (long) pz * 17L + DOME_VARIANT_SALT);
                    RandomSource domeRandom = RandomSource.create(domeSeed);
                    if (domeRandom.nextDouble() < DOME_UPGRADE_PROBABILITY) {
                        LOGGER.info("[DOME_UPGRADE] CP({},{}) PLATEAU -> DOME (dome variant)",
                            px, pz);
                        type = TerrainType.DOME;
                    }
                }
                // ICE_SHEET climate validation: ice sheets form in GLACIAL climate zones.
                // Real-world ice sheets (Antarctica, Greenland) are driven by polar latitude,
                // not just altitude. If ICE_SHEET was selected by moisture but the climate
                // is not GLACIAL, fall back to HORN (the natural tier 5 high-moisture type).
                // ICE_SHEET 气候验证：冰盖在 GLACIAL 气候带形成。
                // 现实世界的冰盖（南极、格陵兰）由极地纬度驱动，而非仅海拔。
                // 如果 ICE_SHEET 被湿度选中但气候不是 GLACIAL，
                // 回退到 HORN（自然的 tier 5 高湿度类型）。
                if (type == TerrainType.ICE_SHEET && this.macroSystem != null) {
                    MacroRegionInfo regionInfo = this.macroSystem.getRegionInfo(px, pz);
                    if (regionInfo.climate != MacroRegionInfo.ClimateZone.GLACIAL) {
                        LOGGER.info("[ICE_SHEET_FIX] CP({},{}) ICE_SHEET -> HORN (non-glacial climate={}, tier={})",
                            px, pz, regionInfo.climate, tier);
                        type = TerrainType.HORN;
                    }
                }
                // Diagnostic: log first 12 control points with energy/moisture/tier
                if (controlPoints.size() + rawPoints.size() < 12) {
                    LOGGER.info("[CP_DIAG] region({},{}), px={}, pz={}, energy={}, moisture={}, tier={}, macroTier={}, type={}",
                        this.regionX, this.regionZ, px, pz,
                        String.format("%.4f", energy),
                        String.format("%.4f", moisture),
                        tier, this.macroElevationTier, type.getId());
                }
                double rawOffset = fieldSampler.calculateContinuousOffset(energy, type);
                double radius = this.calculateInfluenceRadius(px, pz, type, random);
                rawPoints.add(new PointData(px, pz, type, rawOffset, radius));
            }
        }
        this.applyNeighborConstraintIterative(rawPoints);
        for (PointData pd : rawPoints) {
            TerrainControlPoint point = new TerrainControlPoint(pd.x, pd.z, pd.type, pd.constrainedOffset, pd.radius);
            this.controlPoints.add(point);
            int cellX = Math.floorDiv(pd.x - this.regionX * REGION_SIZE, 16);
            int cellZ = Math.floorDiv(pd.z - this.regionZ * REGION_SIZE, 16);
            if (cellX < 0 || cellX >= this.cellsPerSide || cellZ < 0 || cellZ >= this.cellsPerSide) continue;
            if (this.cellIndex[cellX][cellZ] == null) {
                this.cellIndex[cellX][cellZ] = new ArrayList<TerrainControlPoint>(2);
            }
            this.cellIndex[cellX][cellZ].add(point);
        }
    }

    /**
     * Validates and corrects coastal terrain types based on ocean proximity.
     *
     * Rules:
     * 1. BEACH/DELTA without nearby ocean → replaced with FLOODPLAIN (inland equivalent)
     * 2. BEACH with nearby ocean + low moisture → 15% chance to become SEA_CLIFF
     * 3. BEACH/DELTA with nearby ocean + high moisture → 10% chance to become FJORD
     *
     * This ensures beaches only appear on actual coastlines (ocean generates beach,
     * not the other way around) and adds coastal variety with cliffs and fjords.
     *
     * 验证并修正海岸地形类型基于海洋邻近性。
     *
     * 规则：
     * 1. 无附近海洋的 BEACH/DELTA → 替换为 FLOODPLAIN（内陆等价物）
     * 2. 有附近海洋 + 低湿度的 BEACH → 15% 概率变为 SEA_CLIFF
     * 3. 有附近海洋 + 高湿度的 BEACH/DELTA → 10% 概率变为 FJORD
     *
     * 这确保海滩只出现在真实海岸线（海洋生成海滩，而非反过来），
     * 并通过悬崖和峡湾增加海岸多样性。
     */
    private TerrainType validateCoastalType(TerrainType type, int tier, double moisture, int px, int pz) {
        // Only tier 2 has coastal types (BEACH, DELTA, SEA_CLIFF, FJORD)
        // 只有 tier 2 有海岸类型
        if (tier != 2) {
            return type;
        }
        // If macroSystem is not available (legacy constructor), skip validation
        // 如果 macroSystem 不可用（旧构造函数），跳过验证
        if (this.macroSystem == null) {
            return type;
        }

        boolean isCoastalType = (type == TerrainType.BEACH || type == TerrainType.DELTA);
        if (!isCoastalType) {
            return type;
        }

        boolean nearOcean = this.isNearOcean(px, pz);

        if (!nearOcean) {
            // No ocean nearby — BEACH/DELTA are invalid inland.
            // Replace with FLOODPLAIN, the natural inland equivalent at tier 2.
            // 附近无海洋 — BEACH/DELTA 在内陆无效。
            // 替换为 FLOODPLAIN，tier 2 的自然内陆等价物。
            LOGGER.info("[COASTAL_FIX] CP({},{}) {} -> FLOODPLAIN (no ocean within {} blocks)",
                px, pz, type.getId(), OCEAN_PROXIMITY_CHECK_RADIUS);
            return TerrainType.FLOODPLAIN;
        }

        // Ocean confirmed nearby — consider upgrading to SEA_CLIFF or FJORD
        // for coastal variety. Uses a separate deterministic seed (not the main
        // random stream) to avoid shifting offsets of subsequent control points.
        // 附近确认有海洋 — 考虑升级为 SEA_CLIFF 或 FJORD 以增加海岸多样性。
        // 使用独立的确定性种子（非主流随机流）以避免偏移后续控制点的偏移量。
        long variantSeed = SeedDeriver.deriveSeed(this.worldSeed,
            (long) px * 31L + (long) pz * 17L + COASTAL_VARIANT_SALT);
        RandomSource variantRandom = RandomSource.create(variantSeed);
        double variantRoll = variantRandom.nextDouble();

        // SEA_CLIFF: dry coastal areas (low moisture) → rocky sea cliffs
        // SEA_CLIFF：干燥海岸区域（低湿度）→ 岩石海蚀崖
        if (type == TerrainType.BEACH
                && moisture < SEA_CLIFF_MAX_MOISTURE
                && variantRoll < SEA_CLIFF_UPGRADE_PROBABILITY) {
            LOGGER.info("[COASTAL_UPGRADE] CP({},{}) BEACH -> SEA_CLIFF (coastal cliff, moisture={})",
                px, pz, String.format("%.3f", moisture));
            return TerrainType.SEA_CLIFF;
        }

        // FJORD: glacial fjords form only in GLACIAL climate zones with high moisture.
        // Fjords are carved by glaciers, so they require cold climate — tropical
        // high-moisture coasts (e.g. Amazon mouth) must NOT become fjords.
        // FJORD：冰川峡湾只在 GLACIAL 气候带且高湿度时形成。
        // 峡湾由冰川雕刻，因此需要寒冷气候 — 热带高湿度海岸（如亚马逊河口）
        // 不能成为峡湾。
        if (moisture > FJORD_MIN_MOISTURE
                && variantRoll < FJORD_UPGRADE_PROBABILITY) {
            MacroRegionInfo regionInfo = this.macroSystem.getRegionInfo(px, pz);
            if (regionInfo.climate == MacroRegionInfo.ClimateZone.GLACIAL) {
                LOGGER.info("[COASTAL_UPGRADE] CP({},{}) {} -> FJORD (glacial fjord, moisture={}, climate={})",
                    px, pz, type.getId(), String.format("%.3f", moisture), regionInfo.climate);
                return TerrainType.FJORD;
            }
        }

        return type;
    }

    /**
     * Checks whether ocean (macro elevation tier 0-1) exists within
     * OCEAN_PROXIMITY_CHECK_RADIUS of the given block coordinates.
     *
     * Samples the center point plus 8 surrounding points at 512-block distance,
     * covering a 1024×1024 area. Since macro Voronoi cells are 2048 blocks apart,
     * this reliably detects ocean in adjacent cells.
     *
     * Returns false if macroSystem is null (legacy mode without coastal validation).
     *
     * 检查给定方块坐标周围 OCEAN_PROXIMITY_CHECK_RADIUS 范围内是否存在海洋（宏观海拔层级 0-1）。
     *
     * 采样中心点加上 512 方块距离的 8 个周围点，覆盖 1024×1024 区域。
     * 由于宏观 Voronoi 单元间距为 2048 方块，这能可靠检测相邻单元中的海洋。
     *
     * 如果 macroSystem 为 null（无海岸验证的旧模式），返回 false。
     */
    private boolean isNearOcean(int px, int pz) {
        if (this.macroSystem == null) {
            return false;
        }
        // Check center point
        // 检查中心点
        if (this.macroSystem.getRegionInfo(px, pz).getElevationTier() <= WorldScapeConstants.OCEAN_ELEVATION_TIER_MAX) {
            return true;
        }
        // Check 8 surrounding points at OCEAN_PROXIMITY_CHECK_RADIUS distance
        // 检查 OCEAN_PROXIMITY_CHECK_RADIUS 距离处的 8 个周围点
        int r = OCEAN_PROXIMITY_CHECK_RADIUS;
        int[][] offsets = {
            {r, 0}, {-r, 0}, {0, r}, {0, -r},
            {r, r}, {r, -r}, {-r, r}, {-r, -r}
        };
        for (int[] offset : offsets) {
            int tier = this.macroSystem.getRegionInfo(px + offset[0], pz + offset[1]).getElevationTier();
            if (tier <= WorldScapeConstants.OCEAN_ELEVATION_TIER_MAX) {
                return true;
            }
        }
        return false;
    }

    private void applyNeighborConstraintIterative(List<PointData> allPoints) {
        for (PointData pd : allPoints) {
            pd.constrainedOffset = pd.rawOffset;
        }
        double neighborSearchRadiusSq = 1048576.0;
        int maxIter = 10; // Math.min(12, 10) always evaluates to 10 / Math.min(12, 10) 始终为 10
        for (int iteration = 0; iteration < maxIter; ++iteration) {
            double maxChange = 0.0;
            for (PointData point : allPoints) {
                double oldOffset;
                double newOffset = oldOffset = point.constrainedOffset;
                for (PointData neighbor : allPoints) {
                    int maxAllowedDiff;
                    double neighborOffset;
                    double diff;
                    double dz;
                    double dx;
                    double distSq;
                    if (neighbor == point || (distSq = (dx = (double)(neighbor.x - point.x)) * dx + (dz = (double)(neighbor.z - point.z)) * dz) > neighborSearchRadiusSq || !((diff = Math.abs(newOffset - (neighborOffset = neighbor.constrainedOffset))) > (double)(maxAllowedDiff = this.calculateAdaptiveMaxHeightDiff(point.type, neighbor.type)))) continue;
                    if (newOffset > neighborOffset) {
                        newOffset = neighborOffset + (double)maxAllowedDiff;
                        continue;
                    }
                    newOffset = neighborOffset - (double)maxAllowedDiff;
                }
                point.constrainedOffset = newOffset;
                maxChange = Math.max(maxChange, Math.abs(newOffset - oldOffset));
            }
            if (!(maxChange < 1.0)) continue;
            LOGGER.debug("[World Scape] Region ({},{}) constraint converged at iteration {} (maxChange={})", new Object[]{this.regionX, this.regionZ, iteration + 1, String.format("%.2f", maxChange)});
            break;
        }
        this.applyTerrainTypeConstraints(allPoints);
    }

    private int calculateAdaptiveMaxHeightDiff(TerrainType t1, TerrainType t2) {
        if (this.isNaturalCliffPair(t1, t2)) {
            return 300;
        }
        int level1 = this.getTerrainLevel(t1);
        int level2 = this.getTerrainLevel(t2);
        int levelDiff = Math.abs(level1 - level2);
        int baseDiff = 100 + levelDiff * 100;
        boolean hasExtreme = this.isExtremeTerrain(t1) || this.isExtremeTerrain(t2);
        boolean bl = hasExtreme;
        if (hasExtreme && levelDiff >= 3) {
            baseDiff += 100;
        }
        return Math.max(100, Math.min(500, baseDiff));
    }

    private boolean isNaturalCliffPair(TerrainType t1, TerrainType t2) {
        if (t1 == TerrainType.CLIFF || t2 == TerrainType.CLIFF) {
            TerrainType other = t1 == TerrainType.CLIFF ? t2 : t1;
            TerrainType terrainType = other;
            if (other == TerrainType.CLIFF) {
                return true;
            }
            return other == TerrainType.HIGH_MOUNTAINS || other == TerrainType.RIDGE || other == TerrainType.PEAK
                    || other == TerrainType.HORN || other == TerrainType.CIRQUE || other == TerrainType.PLATEAU
                    || other == TerrainType.HILLS || other == TerrainType.CANYON || other == TerrainType.VALLEY
                    || other == TerrainType.GLACIAL_VALLEY || other == TerrainType.FJORD || other == TerrainType.SEA_CLIFF;
        }
        boolean isHighMountain = t1 == TerrainType.HIGH_MOUNTAINS || t2 == TerrainType.HIGH_MOUNTAINS;
        boolean isHorn = t1 == TerrainType.HORN || t2 == TerrainType.HORN;
        boolean isLowlandErosion = t1 == TerrainType.CANYON || t2 == TerrainType.CANYON || t1 == TerrainType.TRENCH || t2 == TerrainType.TRENCH || t1 == TerrainType.GLACIAL_VALLEY || t2 == TerrainType.GLACIAL_VALLEY || t1 == TerrainType.SINKHOLE || t2 == TerrainType.SINKHOLE;
        boolean bl = isLowlandErosion;
        if ((isHighMountain || isHorn) && isLowlandErosion) {
            return true;
        }
        boolean isPlateau = t1 == TerrainType.PLATEAU || t2 == TerrainType.PLATEAU || t1 == TerrainType.DOME || t2 == TerrainType.DOME || t1 == TerrainType.SEA_CLIFF || t2 == TerrainType.SEA_CLIFF;
        boolean isDeepCut = t1 == TerrainType.CANYON || t2 == TerrainType.CANYON || t1 == TerrainType.TRENCH || t2 == TerrainType.TRENCH || t1 == TerrainType.FJORD || t2 == TerrainType.FJORD;
        return isPlateau && isDeepCut;
    }

    private boolean isExtremeTerrain(TerrainType type) {
        if (type == TerrainType.HIGH_MOUNTAINS || type == TerrainType.HORN || type == TerrainType.CANYON || type == TerrainType.CLIFF || type == TerrainType.TRENCH) {
            return true;
        }
        return false;
    }

    private void applyTerrainTypeConstraints(List<PointData> allPoints) {
        double neighborSearchRadiusSq = 1048576.0;
        for (int iteration = 0; iteration < 5; ++iteration) {
            boolean converged = true;
            for (PointData point : allPoints) {
                for (PointData neighbor : allPoints) {
                    int typeDifference;
                    double dz;
                    double dx;
                    double distSq;
                    if (neighbor == point || (distSq = (dx = (double)(neighbor.x - point.x)) * dx + (dz = (double)(neighbor.z - point.z)) * dz) > neighborSearchRadiusSq || (typeDifference = this.getTerrainTypeDifference(point.type, neighbor.type)) <= 2) continue;
                    double adjustment = this.calculateTerrainTypeAdjustment(point.type, neighbor.type);
                    if (this.isHigherElevation(point.type, neighbor.type)) {
                        point.constrainedOffset -= adjustment;
                        neighbor.constrainedOffset += adjustment;
                    } else {
                        neighbor.constrainedOffset -= adjustment;
                        point.constrainedOffset += adjustment;
                    }
                    converged = false;
                }
            }
            if (converged) break;
        }
    }

    private int getTerrainTypeDifference(TerrainType t1, TerrainType t2) {
        int level1 = this.getTerrainLevel(t1);
        int level2 = this.getTerrainLevel(t2);
        return Math.abs(level1 - level2);
    }

    private int getTerrainLevel(TerrainType type) {
        if (type == TerrainType.TRENCH) {
            return 0;
        } else if (type == TerrainType.SEA_PLATEAU || type == TerrainType.BASIN || type == TerrainType.SINKHOLE) {
            // SEA_PLATEAU moved from level 2 to level 1: it's an underwater terrain
            // (base height -11, tier 0-1) and should be grouped with other low/deep
            // terrains, not with coastal types like BEACH (base height 11).
            // SEA_PLATEAU 从 level 2 移至 level 1：它是水下地形
            //（基准高度 -11，tier 0-1），应与其他低洼/深层地形归为一组，
            // 而非与 BEACH（基准高度 11）等海岸类型归为一组。
            return 1;
        } else if (type == TerrainType.BEACH || type == TerrainType.DELTA || type == TerrainType.FLOODPLAIN || type == TerrainType.SALT_FLAT) {
            return 2;
        } else if (type == TerrainType.CANYON || type == TerrainType.GLACIAL_VALLEY || type == TerrainType.FJORD || type == TerrainType.PLAINS || type == TerrainType.DUNE || type == TerrainType.GOBI || type == TerrainType.YARDANG || type == TerrainType.SEA_CLIFF) {
            // CANYON moved from level 1 to level 3: although CANYON is a subtractive
            // terrain (effective height lowered by typeModifier -50 and clamp [-80,-30]),
            // it generates at tier 4 (base height 160). Its effective height (~110-130)
            // is closer to tier 3-4 upland terrain than to underwater level 1 terrains.
            // At level 1, adjacent HILLS (level 4) produced levelDiff=3 → 400-block
            // allowed height diff, causing unrealistic cliff transitions.
            // CANYON 从 level 1 移至 level 3：虽然 CANYON 是减法地形
            //（有效高度被 typeModifier -50 和钳制 [-80,-30] 降低），
            // 但它在 tier 4（基准高度 160）生成。其有效高度（约 110-130）
            // 更接近 tier 3-4 的高地地形，而非 level 1 的水下地形。
            // 在 level 1 时，相邻的 HILLS（level 4）产生 levelDiff=3 → 400 方块
            // 的允许高度差，导致不现实的悬崖过渡。
            // SEA_CLIFF moved from level 5 to level 3: it's a tier 2 coastal feature
            // (base height 28) that shouldn't be grouped with high-elevation types
            // like RIDGE/PLATEAU/DOME (base height 83). Level 3 aligns with its
            // actual elevation and allows appropriate height transitions with BEACH.
            // SEA_CLIFF 从 level 5 移至 level 3：它是 tier 2 海岸特征
            //（基准高度 28），不应与 RIDGE/PLATEAU/DOME（基准高度 83）等
            // 高海拔类型归为一组。Level 3 与其实际海拔一致，
            // 并允许与 BEACH 之间有适当的高度过渡。
            return 3;
        } else if (type == TerrainType.CIRQUE || type == TerrainType.HILLS || type == TerrainType.VALLEY || type == TerrainType.ALLUVIAL_FAN || type == TerrainType.ICE_SHEET || type == TerrainType.PEAK_FOREST) {
            return 4;
        } else if (type == TerrainType.RIDGE || type == TerrainType.PLATEAU || type == TerrainType.DOME) {
            return 5;
        } else if (type == TerrainType.HIGH_MOUNTAINS || type == TerrainType.PEAK || type == TerrainType.HORN || type == TerrainType.CLIFF) {
            return 6;
        }
        return 3;
    }

    private boolean isHigherElevation(TerrainType t1, TerrainType t2) {
        return this.getTerrainLevel(t1) > this.getTerrainLevel(t2);
    }

    private double calculateTerrainTypeAdjustment(TerrainType t1, TerrainType t2) {
        int difference = this.getTerrainTypeDifference(t1, t2);
        return (double)difference * 50.0;
    }

    private void validateControlPoints() {
        if (!LOGGER.isDebugEnabled()) {
            return;
        }
        int totalPoints = this.controlPoints.size();
        LOGGER.debug("[World Scape] Region ({},{}) generated {} control points", new Object[]{this.regionX, this.regionZ, totalPoints});
        if (totalPoints < 2) {
            LOGGER.warn("[World Scape] Region ({},{}) control point density too low: {} (expected {}-{}), may cause insufficient transition zones and steep slopes", new Object[]{this.regionX, this.regionZ, totalPoints, 2, 8});
        }
        double neighborSearchRadiusSq = 1048576.0;
        double offsetDiffWarnThresholdSq = 40000.0;
        int anomalyCount = 0;
        for (int i = 0; i < this.controlPoints.size(); ++i) {
            TerrainControlPoint p1 = this.controlPoints.get(i);
            for (int j = i + 1; j < this.controlPoints.size(); ++j) {
                double totalHeightDiff;
                TerrainControlPoint p2 = this.controlPoints.get(j);
                double dx = p2.x - p1.x;
                double dz = p2.z - p1.z;
                double distSq = dx * dx + dz * dz;
                if (distSq > neighborSearchRadiusSq || !((totalHeightDiff = Math.abs(p1.elevationOffset - p2.elevationOffset)) > 200.0)) continue;
                ++anomalyCount;
                LOGGER.warn("[World Scape] ControlPoint offset anomaly in region ({},{}) (Block Grid): CP1(x={}, z={}, elevationOffset={}, type={}) <-> CP2(x={}, z={}, elevationOffset={}, type={}), distance={}, totalHeightDiff={}", new Object[]{this.regionX, this.regionZ, p1.x, p1.z, p1.elevationOffset, p1.terrainType, p2.x, p2.z, p2.elevationOffset, p2.terrainType, Math.sqrt(distSq), totalHeightDiff});
            }
        }
        if (anomalyCount == 0) {
            LOGGER.debug("[World Scape] Region ({},{}) passed offset validation (no anomalies)", (Object)this.regionX, (Object)this.regionZ);
        } else {
            LOGGER.warn("[World Scape] Region ({},{}) has {} offset anomalies (threshold: {})", new Object[]{this.regionX, this.regionZ, anomalyCount, 200});
        }
    }

    private double calculateInfluenceRadius(int x, int z, TerrainType type, RandomSource random) {
        double baseRadius = 600.0;
        if (type == TerrainType.PLAINS) {
            return baseRadius + random.nextDouble() * 200.0;
        } else if (type == TerrainType.BEACH || type == TerrainType.DELTA) {
            return baseRadius + random.nextDouble() * 150.0;
        } else if (type == TerrainType.FLOODPLAIN || type == TerrainType.SALT_FLAT) {
            return baseRadius + 50.0 + random.nextDouble() * 150.0;
        } else if (type == TerrainType.HILLS) {
            return baseRadius + 100.0 + random.nextDouble() * 200.0;
        } else if (type == TerrainType.VALLEY || type == TerrainType.ALLUVIAL_FAN) {
            return baseRadius + 50.0 + random.nextDouble() * 150.0;
        } else if (type == TerrainType.PLATEAU) {
            return baseRadius + 150.0 + random.nextDouble() * 200.0;
        } else if (type == TerrainType.RIDGE || type == TerrainType.DOME) {
            return baseRadius + 100.0 + random.nextDouble() * 200.0;
        } else if (type == TerrainType.HIGH_MOUNTAINS) {
            return baseRadius + 200.0 + random.nextDouble() * 200.0;
        } else if (type == TerrainType.PEAK || type == TerrainType.HORN || type == TerrainType.CLIFF) {
            return baseRadius + 150.0 + random.nextDouble() * 200.0;
        } else if (type == TerrainType.DUNE) {
            return baseRadius + 50.0 + random.nextDouble() * 150.0;
        } else if (type == TerrainType.GOBI || type == TerrainType.YARDANG) {
            return baseRadius + random.nextDouble() * 150.0;
        } else if (type == TerrainType.ICE_SHEET) {
            return baseRadius + 100.0 + random.nextDouble() * 200.0;
        } else if (type == TerrainType.CIRQUE || type == TerrainType.GLACIAL_VALLEY) {
            return baseRadius + 50.0 + random.nextDouble() * 150.0;
        } else if (type == TerrainType.CANYON) {
            return baseRadius - 100.0 + random.nextDouble() * 100.0;
        } else if (type == TerrainType.BASIN || type == TerrainType.SINKHOLE) {
            return baseRadius - 50.0 + random.nextDouble() * 100.0;
        } else if (type == TerrainType.TRENCH) {
            return baseRadius - 50.0 + random.nextDouble() * 100.0;
        } else if (type == TerrainType.SEA_CLIFF) {
            return baseRadius + 50.0 + random.nextDouble() * 100.0;
        } else if (type == TerrainType.FJORD) {
            // Fjords are narrow features — smaller influence radius than other coastal types
            // 峡湾是狭窄特征 — 比其他海岸类型更小的影响半径
            return baseRadius - 50.0 + random.nextDouble() * 100.0;
        }
        return baseRadius + random.nextDouble() * 150.0;
    }

    public List<TerrainControlPoint> getPointsInRange(int targetX, int targetZ, double radius) {
        ArrayList<TerrainControlPoint> result = new ArrayList<TerrainControlPoint>();
        int cellRadius = (int)Math.ceil(radius / 16.0);
        int centerCellX = Math.floorDiv(targetX - this.regionX * REGION_SIZE, 16);
        int centerCellZ = Math.floorDiv(targetZ - this.regionZ * REGION_SIZE, 16);
        for (int dx = -cellRadius; dx <= cellRadius; ++dx) {
            for (int dz = -cellRadius; dz <= cellRadius; ++dz) {
                List<TerrainControlPoint> cell;
                int cx = centerCellX + dx;
                int cz = centerCellZ + dz;
                if (cx < 0 || cx >= this.cellsPerSide || cz < 0 || cz >= this.cellsPerSide || (cell = this.cellIndex[cx][cz]) == null) continue;
                for (TerrainControlPoint point : cell) {
                    double distSq = point.squaredDistanceTo(targetX, targetZ);
                    if (!(distSq <= point.influenceRadius * point.influenceRadius) || !(point.calculateInfluenceFromSquaredDist(distSq) > 0.0)) continue;
                    result.add(point);
                }
            }
        }
        return result;
    }

    public int getRegionX() {
        return this.regionX;
    }

    public int getRegionZ() {
        return this.regionZ;
    }

    public List<TerrainControlPoint> getControlPoints() {
        return Collections.unmodifiableList(this.controlPoints);
    }

    private static class PointData {
        final int x;
        final int z;
        final TerrainType type;
        final double rawOffset;
        final double radius;
        double constrainedOffset;

        PointData(int x, int z, TerrainType type, double rawOffset, double radius) {
            this.x = x;
            this.z = z;
            this.type = type;
            this.rawOffset = rawOffset;
            this.radius = radius;
            this.constrainedOffset = rawOffset;
        }
    }
}

