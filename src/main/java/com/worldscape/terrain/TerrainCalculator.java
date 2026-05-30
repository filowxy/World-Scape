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
            finalHeight = TerrainCalculator.calcHeightForType(x, z, baseHeight, dominantType, fs);
        } else if (dominantType == type) {
            // dominantType and type are the same — only one calcHeightForType call needed
            double singleHeight = TerrainCalculator.calcHeightForType(x, z, baseHeight, type, fs);
            finalHeight = singleHeight;
        } else {
            // Two different types: dominantType controls blend shape, type is the current cell's assigned type
            double dominantTypeHeight = TerrainCalculator.calcHeightForType(x, z, baseHeight, dominantType, fs);
            double currentTypeHeight = TerrainCalculator.calcHeightForType(x, z, baseHeight, type, fs);
            double blendFactor = dominantWeight / WorldScapeConstants.DOMINANT_WEIGHT_THRESHOLD;
            finalHeight = dominantTypeHeight * blendFactor + currentTypeHeight * (1.0 - blendFactor);
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

    public static double calcHeightForType(int worldX, int worldZ, double baseHeight, TerrainType type, TerrainFieldSampler fs) {
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
                double yaPrimary = Math.sin((double)worldX * WorldScapeConstants.YARDANG_SINE_FREQ_X + (double)worldZ * WorldScapeConstants.YARDANG_SINE_FREQ_Z) * WorldScapeConstants.YARDANG_AMP;
                double yaDomain = fs.sampleDomainRotated(worldX, worldZ, WorldScapeConstants.YARDANG_DOMAIN_STRENGTH) * WorldScapeConstants.YARDANG_DOMAIN_AMP;
                height = baseHeight + yaPrimary + yaDomain;
                break;
            }
            case GOBI: {
                double goFbm = fs.sampleFbm(worldX, worldZ, WorldScapeConstants.GOBI_FBM_OCTAVES, WorldScapeConstants.GOBI_FBM_GAIN);
                height = baseHeight + goFbm * WorldScapeConstants.GOBI_HEIGHT_AMP;
                break;
            }
            case SALT_FLAT: {
                double sfFbm = fs.sampleFbm(worldX, worldZ, WorldScapeConstants.SALT_FLAT_FBM_OCTAVES, WorldScapeConstants.SALT_FLAT_FBM_GAIN);
                height = baseHeight + sfFbm * WorldScapeConstants.SALT_FLAT_HEIGHT_AMP;
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
                // @FIXME: Distance calculated from world origin (0,0), should use nearest control point center
                double afDist = Math.sqrt((double)worldX * (double)worldX + (double)worldZ * (double)worldZ) % WorldScapeConstants.ALLUVIAL_FAN_DISTANCE_PERIOD;
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
                double isFbm = fs.sampleFbm(worldX, worldZ, WorldScapeConstants.ICE_SHEET_FBM_OCTAVES, WorldScapeConstants.ICE_SHEET_FBM_GAIN);
                height = baseHeight + isFbm * WorldScapeConstants.ICE_SHEET_HEIGHT_AMP;
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
                double hiFbm = fs.sampleFbm(worldX, worldZ, WorldScapeConstants.HILLS_FBM_OCTAVES, WorldScapeConstants.HILLS_FBM_GAIN);
                height = baseHeight + hiFbm * WorldScapeConstants.HILLS_HEIGHT_AMP;
                break;
            }
            case PLAINS: {
                double plFbm2 = fs.sampleFbm(worldX, worldZ, WorldScapeConstants.PLAINS_FBM_OCTAVES, WorldScapeConstants.PLAINS_FBM_GAIN);
                double plHeight = plFbm2 * WorldScapeConstants.PLAINS_HEIGHT_AMP;
                double plLongWave = fs.sampleFbm(worldX, worldZ, (int)WorldScapeConstants.PLAINS_LONGWAVE_OCTAVES, WorldScapeConstants.PLAINS_LONGWAVE_GAIN) * WorldScapeConstants.PLAINS_LONGWAVE_AMP;
                height = baseHeight + plHeight + plLongWave;
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

    public static int calculateErodedHeight(int worldX, int worldZ, double continuousHeight, boolean isRiver, double riverDepth, int seaLevel, NoiseSet noiseSet, RegionController.TerrainBlendResult blend) {
        double erosionIntensity = TerrainCalculator.getRiverErosionIntensity(worldX, worldZ, noiseSet, continuousHeight, seaLevel, blend);
        return TerrainCalculator.calculateErodedHeight(worldX, worldZ, continuousHeight, isRiver, riverDepth, seaLevel, noiseSet, blend, erosionIntensity);
    }

    public static int calculateErodedHeight(int worldX, int worldZ, double continuousHeight, boolean isRiver, double riverDepth, int seaLevel, NoiseSet noiseSet, RegionController.TerrainBlendResult blend, double erosionIntensity) {
        double alluvialFactor = TerrainCalculator.getAlluvialFactor(worldX, worldZ, noiseSet, continuousHeight, seaLevel);
        return TerrainCalculator.calculateErodedHeight(continuousHeight, isRiver, riverDepth, seaLevel, erosionIntensity, alluvialFactor);
    }

    public static int calculateErodedHeight(double continuousHeight, boolean isRiver, double riverDepth, int seaLevel, double erosionIntensity, double alluvialFactor) {
        double erodedHeight = continuousHeight;
        if (isRiver && erosionIntensity > WorldScapeConstants.RIVER_DIFF_THRESHOLD) {
            double erosionCut = erosionIntensity * WorldScapeConstants.EROSION_CUT_MULTIPLIER;
            erodedHeight = continuousHeight - erosionCut;
        }
        if (alluvialFactor > WorldScapeConstants.RIVER_DIFF_THRESHOLD) {
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
        return TerrainCalculator.getRiverDepthAt(worldX, worldZ, noiseSet, surfaceHeight, seaLevel, TerrainCalculator.isRiverAt(worldX, worldZ, noiseSet));
    }

    public static double getRiverDepthAt(int worldX, int worldZ, NoiseSet noiseSet, int surfaceHeight, int seaLevel, boolean isRiver) {
        if (!isRiver) {
            return 0.0;
        }
        double riverNoise = noiseSet.sample(NoiseSet.NoiseProfile.RIVER_PATH, worldX, worldZ);
        double depth = (riverNoise - WorldScapeConstants.RIVER_DIFF_THRESHOLD) * WorldScapeConstants.RIVER_DEPTH_SCALE * WorldScapeConstants.RIVER_DEPTH_AMPLIFIER;
        return Math.max(WorldScapeConstants.RIVER_MIN_DEPTH, Math.min(depth, WorldScapeConstants.RIVER_MAX_DEPTH));
    }
}