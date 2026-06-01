/*
 * Decompiled with CFR 0.152.
 */
package com.worldscape.terrain;

import com.worldscape.terrain.NoiseSet;
import com.worldscape.terrain.RegionController;
import com.worldscape.terrain.TerrainFieldSampler;
import com.worldscape.terrain.TerrainType;
import com.worldscape.terrain.WorldScapeConstants;

public final class TerrainCalculator {
    private TerrainCalculator() {
    }

    public static double calculateFinalHeight(int x, int z, RegionController.TerrainBlendResult blend, TerrainType type, NoiseSet noiseSet, TerrainFieldSampler fs) {
        double finalHeight;
        double baseHeight = blend.blendedHeight;
        double dominantWeight = blend.dominantWeight;
        TerrainType dominantType = blend.dominantType;
        if (dominantWeight >= WorldScapeConstants.DOMINANT_WEIGHT_THRESHOLD) {
            finalHeight = TerrainCalculator.calcHeightForType(x, z, baseHeight, dominantType, fs, blend);
        } else if (dominantType == type) {
            // dominantType and type are the same — only one calcHeightForType call needed
            double singleHeight = TerrainCalculator.calcHeightForType(x, z, baseHeight, type, fs, blend);
            finalHeight = singleHeight;
        } else {
            // Two different types: dominantType controls blend shape, type is the current cell's assigned type
            double dominantTypeHeight = TerrainCalculator.calcHeightForType(x, z, baseHeight, dominantType, fs, blend);
            double currentTypeHeight = TerrainCalculator.calcHeightForType(x, z, baseHeight, type, fs, blend);
            // @AESTHETIC: Smoothstep blendFactor replaces linear interpolation to eliminate
        // vertical cliff faces at Voronoi cell boundaries. smoothstep (Hermite: t²(3-2t))
        // produces C¹-continuous transitions with zero derivative at endpoints.
        // 用 smoothstep 替代线性插值消除 Voronoi 单元边界的垂直悬崖。
            double blendFactor = dominantWeight / WorldScapeConstants.DOMINANT_WEIGHT_THRESHOLD;
            // smoothstep: t²(3 - 2t), clamps 0..1, C¹-continuous at boundaries
            // Hermite 平滑：t²(3-2t)，在边界处导数为零，过渡自然
            double t = Math.max(0.0, Math.min(1.0, blendFactor));
            double smoothFactor = t * t * (3.0 - 2.0 * t);
            finalHeight = dominantTypeHeight * smoothFactor + currentTypeHeight * (1.0 - smoothFactor);
        }
        return finalHeight;
    }

    public static TerrainType determineTerrainType(RegionController.TerrainBlendResult blend) {
        TerrainType dominantType = blend.dominantType;
        double dominantWeight = blend.dominantWeight;
        if (dominantType != null && dominantWeight >= WorldScapeConstants.DOMINANT_WEIGHT_THRESHOLD) {
            return dominantType;
        }
        int tier = blend.macroInfo.elevationTier;
        if (tier <= 0) {
            return TerrainType.TRENCH;
        }
        if (tier == 1) {
            return TerrainType.SEA_PLATEAU;
        }
        if (tier == 2) {
            return TerrainType.BEACH;
        }
        if (tier == 3) {
            return TerrainType.PLAINS;
        }
        if (tier == 4) {
            return TerrainType.HILLS;
        }
        return TerrainType.HIGH_MOUNTAINS;
    }

    // @AESTHETIC: Original method kept for backward compatibility.
    // Calls blend-aware variant with null blend (control point info not available).
    public static double calcHeightForType(int worldX, int worldZ, double baseHeight, TerrainType type, TerrainFieldSampler fs) {
        return calcHeightForType(worldX, worldZ, baseHeight, type, fs, null);
    }

    // @AESTHETIC: Blend-aware variant — enables terrain features to use control point
    // locations for spatially-dependent calculations (e.g., alluvial fan radial patterns).
    // 使地形函数能够利用控制点位置进行空间相关的计算（如冲积扇放射状图案）。
    public static double calcHeightForType(int worldX, int worldZ, double baseHeight, TerrainType type, TerrainFieldSampler fs, RegionController.TerrainBlendResult blend) {
        double height = baseHeight;
        switch (type) {
            case HIGH_MOUNTAINS: {
                double hmFbm = fs.sampleFbm(worldX, worldZ, WorldScapeConstants.FBM_OCTAVES, WorldScapeConstants.FBM_GAIN);
                double hmHeight = hmFbm * WorldScapeConstants.HIGH_MOUNTAINS_HEIGHT_AMP;
                double hmDomain = fs.sampleDomainRotated(worldX, worldZ, WorldScapeConstants.DOMAIN_ROTATION_STRENGTH) * WorldScapeConstants.HIGH_MOUNTAINS_DOMAIN_AMP;
                double hmTurb = fs.sampleTurbulence(worldX, worldZ, WorldScapeConstants.RIDGE_TURBULENCE_STRENGTH) * WorldScapeConstants.HIGH_MOUNTAINS_TURB_AMP;
                height = baseHeight + hmHeight + hmDomain + hmTurb;
                height = Math.min(height, baseHeight + WorldScapeConstants.HIGH_MOUNTAINS_HEIGHT_CAP);
                break;
            }
            case RIDGE: {
                double rPrimarySine = Math.sin((double)worldX * WorldScapeConstants.RIDGE_SINE_FREQ_X + (double)worldZ * WorldScapeConstants.RIDGE_SINE_FREQ_Z);
                double rSecondarySine = Math.sin((double)worldX * WorldScapeConstants.RIDGE_SINE2_FREQ_X - (double)worldZ * WorldScapeConstants.RIDGE_SINE2_FREQ_Z);
                double rSineRaw = rPrimarySine * WorldScapeConstants.RIDGE_SINE_PRIMARY_AMP + rSecondarySine * WorldScapeConstants.RIDGE_SINE_SECONDARY_AMP;
                double rGx = Math.sin((double)(worldX + 1) * WorldScapeConstants.RIDGE_SINE_FREQ_X + (double)worldZ * WorldScapeConstants.RIDGE_SINE_FREQ_Z) - Math.sin((double)(worldX - 1) * WorldScapeConstants.RIDGE_SINE_FREQ_X + (double)worldZ * WorldScapeConstants.RIDGE_SINE_FREQ_Z);
                double rGz = Math.sin((double)worldX * WorldScapeConstants.RIDGE_SINE_FREQ_X + (double)(worldZ + 1) * WorldScapeConstants.RIDGE_SINE_FREQ_Z) - Math.sin((double)worldX * WorldScapeConstants.RIDGE_SINE_FREQ_X + (double)(worldZ - 1) * WorldScapeConstants.RIDGE_SINE_FREQ_Z);
                double rGradMag = Math.sqrt(rGx * rGx + rGz * rGz);
                double rSensitivity = WorldScapeConstants.RIDGE_GRADIENT_SENSITIVITY;
                double rHalfSensitivity = rSensitivity * WorldScapeConstants.RIDGE_GRADIENT_HALF_FACTOR;
                double t = Math.max(0.0, Math.min(1.0, (rGradMag - rHalfSensitivity) / (rSensitivity - rHalfSensitivity)));
                double rSineWeight = 1.0 + t * t * (3.0 - 2.0 * t) * -0.7;
                double rSine = rSineRaw * rSineWeight;
                double rFbm = fs.sampleFbm(worldX, worldZ, WorldScapeConstants.FBM_OCTAVES, WorldScapeConstants.FBM_GAIN) * WorldScapeConstants.RIDGE_FBM_AMP;
                double rTurb = fs.sampleTurbulence(worldX, worldZ, WorldScapeConstants.RIDGE_TURBULENCE_STRENGTH) * WorldScapeConstants.RIDGE_TURB_AMP;
                double rDomain = fs.sampleDomainRotated(worldX, worldZ, WorldScapeConstants.DOMAIN_ROTATION_STRENGTH) * WorldScapeConstants.RIDGE_DOMAIN_AMP;
                height = baseHeight + rFbm + rSine + rTurb + rDomain;
                break;
            }
            case PEAK: {
                double pFbm = fs.sampleFbm(worldX, worldZ, WorldScapeConstants.FBM_OCTAVES, WorldScapeConstants.PEAK_FBM_GAIN);
                double pHeight = pFbm * WorldScapeConstants.PEAK_HEIGHT_AMP;
                double pTurb = fs.sampleTurbulence(worldX, worldZ, WorldScapeConstants.RIDGE_TURBULENCE_STRENGTH) * WorldScapeConstants.PEAK_TURB_AMP;
                double pDomain = fs.sampleDomainRotated(worldX, worldZ, WorldScapeConstants.DOMAIN_ROTATION_STRENGTH) * WorldScapeConstants.PEAK_DOMAIN_AMP;
                height = baseHeight + pHeight + pTurb + pDomain;
                height = Math.min(height, WorldScapeConstants.HIGH_MOUNTAIN_PEAK_CEILING);
                break;
            }
            case HORN: {
                double hFbm = fs.sampleFbm(worldX, worldZ, WorldScapeConstants.FBM_OCTAVES, WorldScapeConstants.HORN_FBM_GAIN);
                double hHeight = hFbm * WorldScapeConstants.HORN_HEIGHT_AMP;
                double hTurb = fs.sampleTurbulence(worldX, worldZ, WorldScapeConstants.HORN_TURB_STRENGTH) * WorldScapeConstants.HORN_TURB_AMP;
                height = baseHeight + hHeight + hTurb;
                height = Math.min(height, WorldScapeConstants.HIGH_MOUNTAIN_PEAK_CEILING);
                break;
            }
            case CLIFF: {
                double cFbm = fs.sampleFbm(worldX, worldZ, WorldScapeConstants.FBM_OCTAVES, WorldScapeConstants.FBM_GAIN);
                double cRaw = cFbm * WorldScapeConstants.CLIFF_HEIGHT_AMP;
                double cTanh = TerrainFieldSampler.tanhScaled(cFbm, WorldScapeConstants.TANH_STEEPNESS_CLIFF) * WorldScapeConstants.CLIFF_TANH_AMP;
                height = baseHeight + cRaw + cTanh;
                break;
            }
            case PLATEAU: {
                double plFbm = fs.sampleFbm(worldX, worldZ, WorldScapeConstants.PLATEAU_FBM_OCTAVES, WorldScapeConstants.PLATEAU_FBM_GAIN);
                height = baseHeight + plFbm * WorldScapeConstants.PLATEAU_HEIGHT_AMP;
                break;
            }
            case DOME: {
                double dOffsetX = fs.sampleFbm(worldX, worldZ, WorldScapeConstants.DOME_OFFSET_FBM_OCTAVES, WorldScapeConstants.DOME_OFFSET_FBM_GAIN) * WorldScapeConstants.DOME_OFFSET;
                double dOffsetZ = fs.sampleFbm(worldX + WorldScapeConstants.DOME_OFFSET_SEED_DISTANCE, worldZ + WorldScapeConstants.DOME_OFFSET_SEED_DISTANCE, WorldScapeConstants.DOME_OFFSET_FBM_OCTAVES, WorldScapeConstants.DOME_OFFSET_FBM_GAIN) * WorldScapeConstants.DOME_OFFSET;
                double dGauss = TerrainFieldSampler.gaussian((double)worldX - dOffsetX, (double)worldZ - dOffsetZ, WorldScapeConstants.GAUSSIAN_SIGMA_DOME);
                height = baseHeight + dGauss * WorldScapeConstants.DOME_AMPLITUDE;
                break;
            }
            case DUNE: {
                double duPrimary = Math.sin((double)worldX * WorldScapeConstants.DUNE_SINE_FREQ_X + (double)worldZ * WorldScapeConstants.DUNE_SINE_FREQ_Z) * WorldScapeConstants.DUNE_PRIMARY_AMP;
                double duSecondary = Math.sin((double)worldX * WorldScapeConstants.DUNE_SINE2_FREQ_X - (double)worldZ * WorldScapeConstants.DUNE_SINE2_FREQ_Z) * WorldScapeConstants.DUNE_SECONDARY_AMP;
                double duRidge = Math.sqrt(duPrimary * duPrimary + 4.0) - 2.0 + duSecondary;
                double duFbm = fs.sampleFbm(worldX, worldZ, 2, 0.1) * WorldScapeConstants.DUNE_FBM_AMP;
                height = baseHeight + duRidge + duFbm;
                break;
            }
            case YARDANG: {
                // @AESTHETIC: Frequency-modulated sine with domain rotation + sharpening turbulence
                // Frequency modulation breaks uniform ridge spacing for irregular yardang patterns.
                // Turbulence sharpens ridge edges to simulate steep wind-eroded rock scarps.
                // NOT using DUNE's sqrt trick — yardangs are rock ridges, not sand dunes.
                // 频率调制正弦波 + 域旋转 + 锐化湍流
                // 频率调制打破均匀间距，湍流锐化脊线边缘模拟风蚀岩壁，不使用沙丘的 sqrt 技巧。
                double yaFreqMod = fs.sampleFbm(worldX, worldZ, WorldScapeConstants.YARDANG_FREQ_MOD_OCTAVES, WorldScapeConstants.YARDANG_FREQ_MOD_GAIN);
                double yaModFactor = 1.0 + yaFreqMod * WorldScapeConstants.YARDANG_FREQ_MOD_AMP;
                double yaPhase = (double)worldX * WorldScapeConstants.YARDANG_SINE_FREQ_X * yaModFactor + (double)worldZ * WorldScapeConstants.YARDANG_SINE_FREQ_Z * yaModFactor;
                double yaPrimary = Math.sin(yaPhase) * WorldScapeConstants.YARDANG_AMP;
                double yaSharp = fs.sampleTurbulence(worldX, worldZ, WorldScapeConstants.YARDANG_SHARP_STRENGTH) * WorldScapeConstants.YARDANG_SHARP_AMP;
                double yaDomain = fs.sampleDomainRotated(worldX, worldZ, WorldScapeConstants.YARDANG_DOMAIN_STRENGTH) * WorldScapeConstants.YARDANG_DOMAIN_AMP;
                height = baseHeight + yaPrimary + yaSharp + yaDomain;
                break;
            }
            case GOBI: {
                // @AESTHETIC: fBm base + high-frequency turbulence for gravel/rock fragment texture
                // 碎石纹理：fBm 基底 + 高频湍流模拟碎石表面的粗糙感
                double goFbm = fs.sampleFbm(worldX, worldZ, WorldScapeConstants.GOBI_FBM_OCTAVES, WorldScapeConstants.GOBI_FBM_GAIN);
                double goGravel = fs.sampleTurbulence(worldX, worldZ, WorldScapeConstants.GOBI_GRAVEL_STRENGTH) * WorldScapeConstants.GOBI_GRAVEL_AMP;
                height = baseHeight + goFbm * WorldScapeConstants.GOBI_HEIGHT_AMP + goGravel;
                break;
            }
            case SALT_FLAT: {
                // @AESTHETIC: fBm base + turbulence-crack texture for polygonal salt crust fissures
                // 盐壳裂纹：fBm 基底 + 湍流 abs() 产生的多边形裂纹纹理
                double sfFbm = fs.sampleFbm(worldX, worldZ, WorldScapeConstants.SALT_FLAT_FBM_OCTAVES, WorldScapeConstants.SALT_FLAT_FBM_GAIN);
                double sfCrack = fs.sampleTurbulence(worldX, worldZ, WorldScapeConstants.SALT_FLAT_CRACK_STRENGTH) * WorldScapeConstants.SALT_FLAT_CRACK_AMP;
                height = baseHeight + sfFbm * WorldScapeConstants.SALT_FLAT_HEIGHT_AMP + sfCrack;
                break;
            }
            case CANYON: {
                double caGradDir = fs.sampleFbm(worldX, worldZ, WorldScapeConstants.CANYON_GRAD_FBM_OCTAVES, WorldScapeConstants.CANYON_GRAD_FBM_GAIN);
                double caDepth = Math.abs(caGradDir) * WorldScapeConstants.CANYON_DEPTH_AMP;
                double caFbm = fs.sampleFbm(worldX, worldZ, WorldScapeConstants.CANYON_FBM_OCTAVES, WorldScapeConstants.CANYON_FBM_GAIN) * WorldScapeConstants.CANYON_FBM_AMP;
                height = baseHeight - caDepth + caFbm;
                break;
            }
            case VALLEY: {
                double vGradMag = fs.calculateGradient(worldX, worldZ);
                double vDepth = TerrainFieldSampler.sigmoid(vGradMag * WorldScapeConstants.VALLEY_SIGMOID_INPUT_SCALE) * WorldScapeConstants.VALLEY_DEPTH;
                double vFbm = fs.sampleFbm(worldX, worldZ, WorldScapeConstants.VALLEY_FBM_OCTAVES, WorldScapeConstants.VALLEY_FBM_GAIN) * WorldScapeConstants.VALLEY_FBM_AMP;
                height = baseHeight - vDepth + vFbm;
                break;
            }
            case FLOODPLAIN: {
                double fpFbm = fs.sampleFbm(worldX, worldZ, WorldScapeConstants.FLOODPLAIN_FBM_OCTAVES, WorldScapeConstants.FLOODPLAIN_FBM_GAIN);
                double fpHeight = fpFbm * WorldScapeConstants.FLOODPLAIN_HEIGHT_AMP;
                double fpRiverStripe = Math.sin((double)worldX * WorldScapeConstants.FLOODPLAINS_RIVER_FREQ_X + (double)worldZ * WorldScapeConstants.FLOODPLAINS_RIVER_FREQ_Z) * WorldScapeConstants.FLOODPLAINS_RIVER_AMP;
                height = baseHeight + fpHeight + fpRiverStripe;
                break;
            }
            case DELTA: {
                double dtGrad = fs.calculateGradient(worldX, worldZ);
                double dtHeight = dtGrad * WorldScapeConstants.DELTA_GRADIENT_HEIGHT;
                double dtDomain = fs.sampleDomainRotated(worldX, worldZ, WorldScapeConstants.DELTA_DOMAIN_STRENGTH) * WorldScapeConstants.DELTA_DOMAIN_AMP;
                height = baseHeight + dtHeight + dtDomain;
                break;
            }
            case ALLUVIAL_FAN: {
                // @AESTHETIC: Distance now calculated relative to nearest contributing control point,
                // not world origin (0,0). This produces radial fan patterns around each control point
                // instead of concentric rings centered at origin.
                // 距离现在相对于最近贡献控制点计算，而非世界原点。
                // 每个冲积扇围绕各自控制点呈放射状，消除同心圆分布。
                double afCenterX = 0.0;
                double afCenterZ = 0.0;
                if (blend != null && blend.contributingPoints != null) {
                    double bestDistSq = Double.MAX_VALUE;
                    for (RegionController.PointWeight pw : blend.contributingPoints) {
                        double dx = (double)worldX - (double)pw.point.getX();
                        double dz = (double)worldZ - (double)pw.point.getZ();
                        double distSq = dx * dx + dz * dz;
                        if (distSq < bestDistSq) {
                            bestDistSq = distSq;
                            afCenterX = (double)pw.point.getX();
                            afCenterZ = (double)pw.point.getZ();
                        }
                    }
                }
                double afDx = (double)worldX - afCenterX;
                double afDz = (double)worldZ - afCenterZ;
                // @AESTHETIC: Replace modulo with sin-based continuous periodic function
                // to eliminate hard edges at period boundaries. Converts sawtooth to smooth sine wave,
                // preserving the cone shape while ensuring C¹ continuity.
                // 使用 sin 连续周期函数替代取模运算，消除周期边界处的硬边。
                // 将锯齿波转换为平滑正弦波，保留锥形同时确保 C¹ 连续。
                double afRawDist = Math.sqrt(afDx * afDx + afDz * afDz);
                double afPhase = afRawDist / WorldScapeConstants.ALLUVIAL_FAN_DISTANCE_PERIOD * Math.PI * 2.0;
                double afDist = (Math.sin(afPhase) * 0.5 + 0.5) * WorldScapeConstants.ALLUVIAL_FAN_DISTANCE_PERIOD;
                double afErf = Math.tanh(afDist / WorldScapeConstants.ALLUVIAL_FAN_DISTANCE_NORM * WorldScapeConstants.ERF_APPROX_FACTOR);
                double afSlope = afErf * WorldScapeConstants.ALLUVIAL_FAN_AMPLITUDE;
                double afFbm = fs.sampleFbm(worldX, worldZ, WorldScapeConstants.ALLUVIAL_FAN_FBM_OCTAVES, WorldScapeConstants.ALLUVIAL_FAN_FBM_GAIN) * WorldScapeConstants.ALLUVIAL_FAN_FBM_AMP;
                height = baseHeight + afSlope + afFbm;
                break;
            }
            case BASIN: {
                double bOffsetX = fs.sampleFbm(worldX, worldZ, WorldScapeConstants.BASIN_OFFSET_FBM_OCTAVES, WorldScapeConstants.BASIN_OFFSET_FBM_GAIN) * WorldScapeConstants.BASIN_OFFSET;
                double bOffsetZ = fs.sampleFbm(worldX + WorldScapeConstants.BASIN_OFFSET_SEED_DISTANCE, worldZ + WorldScapeConstants.BASIN_OFFSET_SEED_DISTANCE, WorldScapeConstants.BASIN_OFFSET_FBM_OCTAVES, WorldScapeConstants.BASIN_OFFSET_FBM_GAIN) * WorldScapeConstants.BASIN_OFFSET;
                double bGauss = TerrainFieldSampler.gaussian((double)worldX - bOffsetX, (double)worldZ - bOffsetZ, WorldScapeConstants.GAUSSIAN_SIGMA_BASIN);
                double bDepth = bGauss * WorldScapeConstants.BASIN_DEPTH;
                height = baseHeight - bDepth;
                break;
            }
            case FJORD: {
                double fjTurb = fs.sampleTurbulence(worldX, worldZ, WorldScapeConstants.FJORD_TURB_STRENGTH) * WorldScapeConstants.FJORD_TURB_AMP;
                double fjCliffEdge = fs.sampleFbm(worldX, worldZ, WorldScapeConstants.FJORD_CLIFF_FBM_OCTAVES, WorldScapeConstants.FJORD_CLIFF_FBM_GAIN);
                double fjTanh = TerrainFieldSampler.tanhScaled(fjCliffEdge, WorldScapeConstants.TANH_STEEPNESS_CLIFF) * WorldScapeConstants.FJORD_CLIFF_AMP;
                height = baseHeight - fjTurb + fjTanh;
                break;
            }
            case GLACIAL_VALLEY: {
                double gvGradMag = fs.calculateGradient(worldX, worldZ);
                double gvDepth = TerrainFieldSampler.sigmoid(gvGradMag * WorldScapeConstants.VALLEY_SIGMOID_INPUT_SCALE) * WorldScapeConstants.GLACIAL_VALLEY_DEPTH;
                double gvFbm = fs.sampleFbm(worldX, worldZ, WorldScapeConstants.GLACIAL_VALLEY_FBM_OCTAVES, WorldScapeConstants.GLACIAL_VALLEY_FBM_GAIN) * WorldScapeConstants.GLACIAL_VALLEY_FBM_AMP;
                height = baseHeight - gvDepth + gvFbm;
                break;
            }
            case CIRQUE: {
                double ciOffsetX = fs.sampleFbm(worldX, worldZ, WorldScapeConstants.CIRQUE_OFFSET_FBM_OCTAVES, WorldScapeConstants.CIRQUE_OFFSET_FBM_GAIN) * WorldScapeConstants.CIRQUE_OFFSET_AMP;
                double ciOffsetZ = fs.sampleFbm(worldX + WorldScapeConstants.CIRQUE_OFFSET_SEED_DISTANCE, worldZ + WorldScapeConstants.CIRQUE_OFFSET_SEED_DISTANCE, WorldScapeConstants.CIRQUE_OFFSET_FBM_OCTAVES, WorldScapeConstants.CIRQUE_OFFSET_FBM_GAIN) * WorldScapeConstants.CIRQUE_OFFSET_AMP;
                double ciGauss = TerrainFieldSampler.gaussian((double)worldX - ciOffsetX, (double)worldZ - ciOffsetZ, WorldScapeConstants.GAUSSIAN_SIGMA_CIRQUE);
                double ciDepth = ciGauss * WorldScapeConstants.CIRQUE_DEPTH;
                double ciEdgeTurb = fs.sampleTurbulence(worldX, worldZ, 0.5) * WorldScapeConstants.CIRQUE_EDGE_TURBULENCE;
                height = baseHeight - ciDepth + ciEdgeTurb;
                break;
            }
            case ICE_SHEET: {
                // @AESTHETIC: fBm base + directional ridge noise + turbulence for glacial crevasse patterns
                // 冰盖纹理：fBm 基底 + 方向性脊线 + 湍流模拟冰川裂隙
                double isFbm = fs.sampleFbm(worldX, worldZ, WorldScapeConstants.ICE_SHEET_FBM_OCTAVES, WorldScapeConstants.ICE_SHEET_FBM_GAIN);
                double isRidge = Math.sin((double)worldX * WorldScapeConstants.ICE_SHEET_RIDGE_FREQ_X + (double)worldZ * WorldScapeConstants.ICE_SHEET_RIDGE_FREQ_Z) * WorldScapeConstants.ICE_SHEET_RIDGE_AMP;
                double isCrevasse = fs.sampleTurbulence(worldX, worldZ, WorldScapeConstants.ICE_SHEET_CREVASSE_TURB_STRENGTH) * WorldScapeConstants.ICE_SHEET_CREVASSE_TURB_AMP;
                height = baseHeight + isFbm * WorldScapeConstants.ICE_SHEET_HEIGHT_AMP + isRidge + isCrevasse;
                break;
            }
            case SEA_CLIFF: {
                double scEdge = fs.sampleFbm(worldX, worldZ, WorldScapeConstants.SEA_CLIFF_EDGE_FBM_OCTAVES, WorldScapeConstants.SEA_CLIFF_EDGE_FBM_GAIN);
                double scTanh = TerrainFieldSampler.tanhScaled(scEdge, WorldScapeConstants.TANH_STEEPNESS_SEA_CLIFF) * WorldScapeConstants.SEA_CLIFF_AMP;
                height = baseHeight + scTanh;
                break;
            }
            case BEACH: {
                double beDist = fs.sampleFbm(worldX, worldZ, WorldScapeConstants.BEACH_DIST_FBM_OCTAVES, WorldScapeConstants.BEACH_DIST_FBM_GAIN);
                double beSigmoid = TerrainFieldSampler.sigmoid(beDist * WorldScapeConstants.BEACH_SIGMOID_INPUT_SCALE) * WorldScapeConstants.BEACH_HEIGHT_AMP;
                height = baseHeight + beSigmoid;
                break;
            }
            case SINKHOLE: {
                double skOffsetX = fs.sampleFbm(worldX, worldZ, 2, 0.2) * WorldScapeConstants.SINKHOLE_OFFSET_AMP;
                double skOffsetZ = fs.sampleFbm(worldX + WorldScapeConstants.SINKHOLE_OFFSET_SEED_DISTANCE, worldZ + WorldScapeConstants.SINKHOLE_OFFSET_SEED_DISTANCE, 2, 0.2) * WorldScapeConstants.SINKHOLE_OFFSET_AMP;
                double skGauss = TerrainFieldSampler.gaussian((double)worldX - skOffsetX, (double)worldZ - skOffsetZ, WorldScapeConstants.GAUSSIAN_SIGMA_SINKHOLE);
                double skDepth = skGauss * WorldScapeConstants.SINKHOLE_DEPTH;
                height = baseHeight - skDepth;
                break;
            }
            case PEAK_FOREST: {
                double pfTurb = fs.sampleTurbulence(worldX, worldZ, WorldScapeConstants.PEAK_FOREST_TURB_STRENGTH) * WorldScapeConstants.PEAK_FOREST_TURB_AMP;
                double pfFbm = fs.sampleFbm(worldX, worldZ, WorldScapeConstants.PEAK_FOREST_FBM_OCTAVES, WorldScapeConstants.PEAK_FOREST_FBM_GAIN) * WorldScapeConstants.PEAK_FOREST_FBM_AMP;
                height = baseHeight + pfTurb + pfFbm;
                break;
            }
            case TRENCH: {
                double trAxis = fs.sampleFbm(worldX, worldZ, WorldScapeConstants.TRENCH_AXIS_FBM_OCTAVES, WorldScapeConstants.TRENCH_AXIS_FBM_GAIN);
                double trDepth = TerrainFieldSampler.sigmoid(-trAxis * WorldScapeConstants.TRENCH_SIGMOID_INPUT_SCALE) * WorldScapeConstants.TRENCH_DEPTH;
                height = baseHeight - WorldScapeConstants.TRENCH_BASE_OFFSET - trDepth;
                break;
            }
            case SEA_PLATEAU: {
                double spFbm = fs.sampleFbm(worldX, worldZ, WorldScapeConstants.SEA_PLATEAU_FBM_OCTAVES, WorldScapeConstants.SEA_PLATEAU_FBM_GAIN);
                double spHeight = spFbm * WorldScapeConstants.SEA_PLATEAU_HEIGHT_AMP;
                double spRockTexture = fs.sampleFbm(worldX * WorldScapeConstants.SEA_PLATEAU_TEXTURE_SCALE, worldZ * WorldScapeConstants.SEA_PLATEAU_TEXTURE_SCALE, WorldScapeConstants.SEA_PLATEAU_TEXTURE_OCTAVES, WorldScapeConstants.SEA_PLATEAU_TEXTURE_GAIN) * WorldScapeConstants.SEA_PLATEAU_TEXTURE_AMP;
                height = baseHeight + spHeight + spRockTexture;
                break;
            }
            case HILLS: {
                // @AESTHETIC: fBm base + turbulence for rolling hill character
                // 丘陵纹理：fBm 基底 + 湍流增强起伏感
                double hiFbm = fs.sampleFbm(worldX, worldZ, WorldScapeConstants.HILLS_FBM_OCTAVES, WorldScapeConstants.HILLS_FBM_GAIN);
                double hiTurb = fs.sampleTurbulence(worldX, worldZ, WorldScapeConstants.HILLS_TURB_STRENGTH) * WorldScapeConstants.HILLS_TURB_AMP;
                height = baseHeight + hiFbm * WorldScapeConstants.HILLS_HEIGHT_AMP + hiTurb;
                break;
            }
            case PLAINS: {
                // @AESTHETIC: fBm base + longwave undulation + subtle gully texture
                // The longwave provides nearly invisible macro-undulation (±0.15 blocks),
                // while gully texture adds micro-scale erosion character without breaking flatness.
                // 平原纹理：fBm + 长波起伏 + 微弱侵蚀沟纹
                // 长波提供肉眼几乎不可见的微起伏（±0.15格），沟纹增加微观侵蚀特征，保持平原平坦但非绝对平面。
                double plFbm2 = fs.sampleFbm(worldX, worldZ, WorldScapeConstants.PLAINS_FBM_OCTAVES, WorldScapeConstants.PLAINS_FBM_GAIN);
                double plHeight = plFbm2 * WorldScapeConstants.PLAINS_HEIGHT_AMP;
                double plLongWave = fs.sampleFbm(worldX, worldZ, (int)WorldScapeConstants.PLAINS_LONGWAVE_OCTAVES, WorldScapeConstants.PLAINS_LONGWAVE_GAIN) * WorldScapeConstants.PLAINS_LONGWAVE_AMP;
                double plGully = fs.sampleTurbulence(worldX, worldZ, WorldScapeConstants.PLAINS_GULLY_STRENGTH) * WorldScapeConstants.PLAINS_GULLY_AMP;
                height = baseHeight + plHeight + plLongWave + plGully;
                break;
            }
            default: {
                double defFbm = fs.sampleFbm(worldX, worldZ, WorldScapeConstants.FBM_OCTAVES, WorldScapeConstants.FBM_GAIN);
                height = baseHeight + defFbm * WorldScapeConstants.DEFAULT_HEIGHT_AMP;
            }
        }
        height = Math.max(WorldScapeConstants.MIN_TERRAIN_HEIGHT, Math.min(WorldScapeConstants.MAX_TERRAIN_HEIGHT, height));
        return height;
    }

    public static double getRiverErosionIntensity(int worldX, int worldZ, NoiseSet noiseSet, double baseHeight, int seaLevel, RegionController.TerrainBlendResult blend) {
        if (blend.macroInfo.elevationTier < WorldScapeConstants.HILLS_TIER_THRESHOLD) {
            return 0.0;
        }
        double erosionNoise = noiseSet.sample(NoiseSet.NoiseProfile.DRAINAGE, worldX, worldZ);
        if (erosionNoise < WorldScapeConstants.EROSION_NOISE_THRESHOLD) {
            return 0.0;
        }
        double intensity = (erosionNoise - WorldScapeConstants.EROSION_NOISE_THRESHOLD) / 0.55;
        double elevationFactor = Math.max(0.0, (baseHeight - (double)seaLevel) / WorldScapeConstants.ELEVATION_NORMALIZATION_FACTOR);
        return intensity * elevationFactor * WorldScapeConstants.EROSION_INTENSITY_FACTOR;
    }

    public static double getAlluvialFactor(int worldX, int worldZ, NoiseSet noiseSet, double baseHeight, int seaLevel) {
        if (baseHeight > (double)(seaLevel + WorldScapeConstants.ALLUVIAL_HEIGHT_RANGE)) {
            return 0.0;
        }
        double alluvialNoise = noiseSet.sample(NoiseSet.NoiseProfile.SEABED, worldX, worldZ);
        if (alluvialNoise < WorldScapeConstants.ALLUVIAL_THRESHOLD) {
            return 0.0;
        }
        double factor = (alluvialNoise - WorldScapeConstants.ALLUVIAL_THRESHOLD) / 0.55;
        double distanceFactor = Math.max(0.0, 1.0 - (baseHeight - (double)seaLevel) / WorldScapeConstants.ALLUVIAL_DISTANCE_RANGE);
        return factor * distanceFactor * WorldScapeConstants.ALLUVIAL_FACTOR;
    }

    // @AESTHETIC: Compute erosion multiplier based on elevation tier.
    // Higher tier = more dramatic terrain = deeper erosion gullies.
    // 基于海拔等级的侵蚀乘数：高等级→更深的侵蚀沟壑。
    public static double getErosionMultiplierForTier(int elevationTier) {
        if (elevationTier >= 5) return WorldScapeConstants.EROSION_MULTIPLIER_MOUNTAIN;
        if (elevationTier <= 2) return WorldScapeConstants.EROSION_MULTIPLIER_PLAIN;
        return 1.0;
    }

    // @AESTHETIC: Compute river depth multiplier based on elevation tier.
    // Mountain rivers cut deeper; plain rivers spread wider and shallower.
    // 基于海拔等级的河流深度乘数：山区河流切割更深，平原河流更浅更宽。
    public static double getRiverDepthMultiplierForTier(int elevationTier) {
        if (elevationTier >= 4) return WorldScapeConstants.RIVER_DEPTH_MULTIPLIER_MOUNTAIN;
        if (elevationTier <= 2) return WorldScapeConstants.RIVER_DEPTH_MULTIPLIER_PLAIN;
        return 1.0;
    }

    public static int calculateErodedHeight(int worldX, int worldZ, double continuousHeight, boolean isRiver, double riverDepth, int seaLevel, NoiseSet noiseSet, RegionController.TerrainBlendResult blend) {
        double erosionIntensity = TerrainCalculator.getRiverErosionIntensity(worldX, worldZ, noiseSet, continuousHeight, seaLevel, blend);
        return TerrainCalculator.calculateErodedHeight(worldX, worldZ, continuousHeight, isRiver, riverDepth, seaLevel, noiseSet, blend, erosionIntensity);
    }

    public static int calculateErodedHeight(int worldX, int worldZ, double continuousHeight, boolean isRiver, double riverDepth, int seaLevel, NoiseSet noiseSet, RegionController.TerrainBlendResult blend, double erosionIntensity) {
        double alluvialFactor = TerrainCalculator.getAlluvialFactor(worldX, worldZ, noiseSet, continuousHeight, seaLevel);
        double erosionMultiplier = blend != null && blend.macroInfo != null
            ? getErosionMultiplierForTier(blend.macroInfo.elevationTier) : 1.0;
        return TerrainCalculator.calculateErodedHeight(continuousHeight, isRiver, riverDepth, seaLevel, erosionIntensity, alluvialFactor, erosionMultiplier);
    }

    public static int calculateErodedHeight(double continuousHeight, boolean isRiver, double riverDepth, int seaLevel, double erosionIntensity, double alluvialFactor, double erosionMultiplier) {
        double erodedHeight = continuousHeight;
        if (isRiver && erosionIntensity > WorldScapeConstants.RIVER_DIFF_THRESHOLD) {
            double erosionCut = erosionIntensity * WorldScapeConstants.EROSION_CUT_MULTIPLIER * erosionMultiplier;
            erodedHeight = continuousHeight - erosionCut;
        }
        // @CONSISTENCY: Use ALLUVIAL_THRESHOLD (0.45) to match getAlluvialFactor()'s internal threshold.
        // RIVER_DIFF_THRESHOLD (0.1) is for river noise detection; ALLUVIAL_THRESHOLD is for alluvial deposition.
        // 使用 ALLUVIAL_THRESHOLD (0.45) 与 getAlluvialFactor() 内部阈值保持一致。
        // RIVER_DIFF_THRESHOLD (0.1) 用于河流噪声检测；ALLUVIAL_THRESHOLD 用于冲积沉积判断。
        if (alluvialFactor > WorldScapeConstants.ALLUVIAL_THRESHOLD) {
            double alluvialRaise = alluvialFactor * WorldScapeConstants.ALLUVIAL_RAISE_MULTIPLIER;
            erodedHeight += alluvialRaise;
        }
        return (int)Math.floor(erodedHeight);
    }

    public static int calculateActualSurfaceHeight(int terrainHeight, boolean isRiver, double riverDepth, int minY) {
        if (isRiver && riverDepth > WorldScapeConstants.RIVER_DEPTH_THRESHOLD) {
            return (int)Math.max((double)minY, (double)terrainHeight - riverDepth);
        }
        return terrainHeight;
    }

    public static boolean isRiverAt(int worldX, int worldZ, NoiseSet noiseSet) {
        double riverNoise = noiseSet.sample(NoiseSet.NoiseProfile.RIVER_PATH, worldX, worldZ);
        double riverPath = noiseSet.sample(NoiseSet.NoiseProfile.RIVER_WIDTH, worldX, worldZ);
        return riverNoise > WorldScapeConstants.RIVER_DIFF_THRESHOLD && Math.abs(riverPath) < WorldScapeConstants.RIVER_WIDTH_THRESHOLD;
    }

    public static double getRiverDepthAt(int worldX, int worldZ, NoiseSet noiseSet, int surfaceHeight, int seaLevel) {
        return TerrainCalculator.getRiverDepthAt(worldX, worldZ, noiseSet, surfaceHeight, seaLevel, TerrainCalculator.isRiverAt(worldX, worldZ, noiseSet), 1.0);
    }

    public static double getRiverDepthAt(int worldX, int worldZ, NoiseSet noiseSet, int surfaceHeight, int seaLevel, boolean isRiver) {
        return getRiverDepthAt(worldX, worldZ, noiseSet, surfaceHeight, seaLevel, isRiver, 1.0);
    }

    // @AESTHETIC: River depth with terrain-type-dependent multiplier.
    // 带地形乘数的河流深度计算。
    public static double getRiverDepthAt(int worldX, int worldZ, NoiseSet noiseSet, int surfaceHeight, int seaLevel, boolean isRiver, double depthMultiplier) {
        if (!isRiver) {
            return 0.0;
        }
        double riverNoise = noiseSet.sample(NoiseSet.NoiseProfile.RIVER_PATH, worldX, worldZ);
        double depth = (riverNoise - WorldScapeConstants.RIVER_DIFF_THRESHOLD) * WorldScapeConstants.RIVER_DEPTH_SCALE * WorldScapeConstants.RIVER_DEPTH_AMPLIFIER * depthMultiplier;
        return Math.max(WorldScapeConstants.RIVER_MIN_DEPTH, Math.min(depth, WorldScapeConstants.RIVER_MAX_DEPTH));
    }
}