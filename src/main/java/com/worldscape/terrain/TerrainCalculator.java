/*
 * Decompiled with CFR 0.152.
 */
package com.worldscape.terrain;

import com.worldscape.terrain.NoiseSet;
import com.worldscape.terrain.RegionController;
import com.worldscape.terrain.TerrainFieldSampler;
import com.worldscape.terrain.TerrainType;

public final class TerrainCalculator {
    private TerrainCalculator() {
    }

    public static double calculateFinalHeight(int x, int z, RegionController.TerrainBlendResult blend, TerrainType type, NoiseSet noiseSet, TerrainFieldSampler fs) {
        double finalHeight;
        double baseHeight = blend.blendedHeight;
        double dominantWeight = blend.dominantWeight;
        TerrainType dominantType = blend.dominantType;
        if (dominantWeight >= 0.4) {
            finalHeight = TerrainCalculator.calcHeightForType(x, z, baseHeight, dominantType, fs);
        } else {
            double dominantTypeHeight = TerrainCalculator.calcHeightForType(x, z, baseHeight, dominantType, fs);
            double currentTypeHeight = TerrainCalculator.calcHeightForType(x, z, baseHeight, type, fs);
            double blendFactor = dominantWeight / 0.4;
            finalHeight = dominantTypeHeight * blendFactor + currentTypeHeight * (1.0 - blendFactor);
        }
        return finalHeight;
    }

    public static TerrainType determineTerrainType(RegionController.TerrainBlendResult blend) {
        TerrainType dominantType = blend.dominantType;
        double dominantWeight = blend.dominantWeight;
        if (dominantType != null && dominantWeight >= 0.4) {
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
                double hmFbm = fs.sampleFbm(worldX, worldZ, 6, 0.5);
                double hmHeight = hmFbm * 200.0;
                double hmDomain = fs.sampleDomainRotated(worldX, worldZ, 0.15) * 15.0;
                double hmTurb = fs.sampleTurbulence(worldX, worldZ, 0.6) * 20.0;
                height = baseHeight + hmHeight + hmDomain + hmTurb;
                height = Math.min(height, baseHeight + 250.0);
                break;
            }
            case RIDGE: {
                double rPrimarySine = Math.sin((double)worldX * 0.007 + (double)worldZ * 0.004);
                double rSecondarySine = Math.sin((double)worldX * 0.025 - (double)worldZ * 0.018);
                double rSineRaw = rPrimarySine * 35.0 + rSecondarySine * 18.0;
                double rGx = Math.sin((double)(worldX + 1) * 0.007 + (double)worldZ * 0.004) - Math.sin((double)(worldX - 1) * 0.007 + (double)worldZ * 0.004);
                double rGz = Math.sin((double)worldX * 0.007 + (double)(worldZ + 1) * 0.004) - Math.sin((double)worldX * 0.007 + (double)(worldZ - 1) * 0.004);
                double rGradMag = Math.sqrt(rGx * rGx + rGz * rGz);
                double rSensitivity = 0.6;
                double rHalfSensitivity = rSensitivity * 0.5;
                double t = Math.max(0.0, Math.min(1.0, (rGradMag - rHalfSensitivity) / (rSensitivity - rHalfSensitivity)));
                double rSineWeight = 1.0 + t * t * (3.0 - 2.0 * t) * -0.7;
                double rSine = rSineRaw * rSineWeight;
                double rFbm = fs.sampleFbm(worldX, worldZ, 6, 0.5) * 150.0;
                double rTurb = fs.sampleTurbulence(worldX, worldZ, 0.6) * 15.0;
                double rDomain = fs.sampleDomainRotated(worldX, worldZ, 0.15) * 10.0;
                height = baseHeight + rFbm + rSine + rTurb + rDomain;
                break;
            }
            case PEAK: {
                double pFbm = fs.sampleFbm(worldX, worldZ, 6, 0.4);
                double pHeight = pFbm * 120.0;
                double pTurb = fs.sampleTurbulence(worldX, worldZ, 0.6) * 80.0;
                double pDomain = fs.sampleDomainRotated(worldX, worldZ, 0.15) * 12.0;
                height = baseHeight + pHeight + pTurb + pDomain;
                height = Math.min(height, 510.0);
                break;
            }
            case HORN: {
                double hFbm = fs.sampleFbm(worldX, worldZ, 6, 0.3);
                double hHeight = hFbm * 100.0;
                double hTurb = fs.sampleTurbulence(worldX, worldZ, 0.8) * 70.0;
                height = baseHeight + hHeight + hTurb;
                height = Math.min(height, 510.0);
                break;
            }
            case CLIFF: {
                double cFbm = fs.sampleFbm(worldX, worldZ, 6, 0.5);
                double cRaw = cFbm * 80.0;
                double cTanh = TerrainFieldSampler.tanhScaled(cFbm, 2.0) * 40.0;
                height = baseHeight + cRaw + cTanh;
                break;
            }
            case PLATEAU: {
                double plFbm = fs.sampleFbm(worldX, worldZ, 3, 0.3);
                height = baseHeight + plFbm * 100.0;
                break;
            }
            case DOME: {
                double dOffsetX = fs.sampleFbm(worldX, worldZ, 2, 0.2) * 50.0;
                double dOffsetZ = fs.sampleFbm(worldX + 10000, worldZ + 10000, 2, 0.2) * 50.0;
                double dGauss = TerrainFieldSampler.gaussian((double)worldX - dOffsetX, (double)worldZ - dOffsetZ, 200.0);
                height = baseHeight + dGauss * 150.0;
                break;
            }
            case DUNE: {
                double duPrimary = Math.sin((double)worldX * 0.02 + (double)worldZ * 0.005) * 25.0;
                double duSecondary = Math.sin((double)worldX * 0.005 - (double)worldZ * 0.015) * 8.0;
                double duRidge = Math.sqrt(duPrimary * duPrimary + 4.0) - 2.0 + duSecondary;
                double duFbm = fs.sampleFbm(worldX, worldZ, 2, 0.1) * 5.0;
                height = baseHeight + duRidge + duFbm;
                break;
            }
            case YARDANG: {
                double yaPrimary = Math.sin((double)worldX * 0.015 + (double)worldZ * 0.003) * 30.0;
                double yaDomain = fs.sampleDomainRotated(worldX, worldZ, 0.2) * 15.0;
                height = baseHeight + yaPrimary + yaDomain;
                break;
            }
            case GOBI: {
                double goFbm = fs.sampleFbm(worldX, worldZ, 4, 0.7);
                height = baseHeight + goFbm * 15.0;
                break;
            }
            case SALT_FLAT: {
                double sfFbm = fs.sampleFbm(worldX, worldZ, 2, 0.1);
                height = baseHeight + sfFbm * 3.0;
                break;
            }
            case CANYON: {
                double caGradDir = fs.sampleFbm(worldX, worldZ, 3, 0.4);
                double caDepth = Math.abs(caGradDir) * 60.0;
                double caFbm = fs.sampleFbm(worldX, worldZ, 4, 0.5) * 10.0;
                height = baseHeight - caDepth + caFbm;
                break;
            }
            case VALLEY: {
                double vGradMag = fs.calculateGradient(worldX, worldZ);
                double vDepth = TerrainFieldSampler.sigmoid(vGradMag * 5.0) * 40.0;
                double vFbm = fs.sampleFbm(worldX, worldZ, 4, 0.5) * 10.0;
                height = baseHeight - vDepth + vFbm;
                break;
            }
            case FLOODPLAIN: {
                double fpFbm = fs.sampleFbm(worldX, worldZ, 3, 0.15);
                double fpHeight = fpFbm * 5.0;
                double fpRiverStripe = Math.sin((double)worldX * 0.01 + (double)worldZ * 0.003) * 2.0;
                height = baseHeight + fpHeight + fpRiverStripe;
                break;
            }
            case DELTA: {
                double dtGrad = fs.calculateGradient(worldX, worldZ);
                double dtHeight = dtGrad * 10.0;
                double dtDomain = fs.sampleDomainRotated(worldX, worldZ, 0.05) * 8.0;
                height = baseHeight + dtHeight + dtDomain;
                break;
            }
            case ALLUVIAL_FAN: {
                double afDist = Math.sqrt((double)worldX * (double)worldX + (double)worldZ * (double)worldZ) % 200.0;
                double afErf = Math.tanh(afDist / 100.0 * 0.886);
                double afSlope = afErf * 25.0;
                double afFbm = fs.sampleFbm(worldX, worldZ, 3, 0.3) * 5.0;
                height = baseHeight + afSlope + afFbm;
                break;
            }
            case BASIN: {
                double bOffsetX = fs.sampleFbm(worldX, worldZ, 2, 0.2) * 80.0;
                double bOffsetZ = fs.sampleFbm(worldX + 20000, worldZ + 20000, 2, 0.2) * 80.0;
                double bGauss = TerrainFieldSampler.gaussian((double)worldX - bOffsetX, (double)worldZ - bOffsetZ, 300.0);
                double bDepth = bGauss * 30.0;
                height = baseHeight - bDepth;
                break;
            }
            case FJORD: {
                double fjTurb = fs.sampleTurbulence(worldX, worldZ, 0.7) * 55.0;
                double fjCliffEdge = fs.sampleFbm(worldX, worldZ, 3, 0.3);
                double fjTanh = TerrainFieldSampler.tanhScaled(fjCliffEdge, 2.0) * 80.0;
                height = baseHeight - fjTurb + fjTanh;
                break;
            }
            case GLACIAL_VALLEY: {
                double gvGradMag = fs.calculateGradient(worldX, worldZ);
                double gvDepth = TerrainFieldSampler.sigmoid(gvGradMag * 5.0) * 60.0;
                double gvFbm = fs.sampleFbm(worldX, worldZ, 4, 0.5) * 8.0;
                height = baseHeight - gvDepth + gvFbm;
                break;
            }
            case CIRQUE: {
                double ciOffsetX = fs.sampleFbm(worldX, worldZ, 2, 0.2) * 40.0;
                double ciOffsetZ = fs.sampleFbm(worldX + 30000, worldZ + 30000, 2, 0.2) * 40.0;
                double ciGauss = TerrainFieldSampler.gaussian((double)worldX - ciOffsetX, (double)worldZ - ciOffsetZ, 150.0);
                double ciDepth = ciGauss * 70.0;
                double ciEdgeTurb = fs.sampleTurbulence(worldX, worldZ, 0.5) * 40.0;
                height = baseHeight - ciDepth + ciEdgeTurb;
                break;
            }
            case ICE_SHEET: {
                double isFbm = fs.sampleFbm(worldX, worldZ, 3, 0.2);
                height = baseHeight + isFbm * 8.0;
                break;
            }
            case SEA_CLIFF: {
                double scEdge = fs.sampleFbm(worldX, worldZ, 4, 0.4);
                double scTanh = TerrainFieldSampler.tanhScaled(scEdge, 3.0) * 100.0;
                height = baseHeight + scTanh;
                break;
            }
            case BEACH: {
                double beDist = fs.sampleFbm(worldX, worldZ, 2, 0.2);
                double beSigmoid = TerrainFieldSampler.sigmoid(beDist * 3.0) * 5.0;
                height = baseHeight + beSigmoid;
                break;
            }
            case SINKHOLE: {
                double skOffsetX = fs.sampleFbm(worldX, worldZ, 2, 0.2) * 30.0;
                double skOffsetZ = fs.sampleFbm(worldX + 40000, worldZ + 40000, 2, 0.2) * 30.0;
                double skGauss = TerrainFieldSampler.gaussian((double)worldX - skOffsetX, (double)worldZ - skOffsetZ, 80.0);
                double skDepth = skGauss * 40.0;
                height = baseHeight - skDepth;
                break;
            }
            case PEAK_FOREST: {
                double pfTurb = fs.sampleTurbulence(worldX, worldZ, 0.7) * 80.0;
                double pfFbm = fs.sampleFbm(worldX, worldZ, 4, 0.5) * 40.0;
                height = baseHeight + pfTurb + pfFbm;
                break;
            }
            case TRENCH: {
                double trAxis = fs.sampleFbm(worldX, worldZ, 3, 0.3);
                double trDepth = TerrainFieldSampler.sigmoid(-trAxis * 3.0) * 30.0;
                height = baseHeight - 20.0 - trDepth;
                break;
            }
            case SEA_PLATEAU: {
                double spFbm = fs.sampleFbm(worldX, worldZ, 3, 0.15);
                double spHeight = spFbm * 15.0;
                double spRockTexture = fs.sampleFbm(worldX * 4, worldZ * 4, 2, 0.3) * 2.0;
                height = baseHeight + spHeight + spRockTexture;
                break;
            }
            case HILLS: {
                double hiFbm = fs.sampleFbm(worldX, worldZ, 6, 0.65);
                height = baseHeight + hiFbm * 40.0;
                break;
            }
            case PLAINS: {
                double plFbm2 = fs.sampleFbm(worldX, worldZ, 4, 0.2);
                double plHeight = plFbm2 * 15.0;
                double plLongWave = fs.sampleFbm(worldX, worldZ, 2, 0.05) * 3.0;
                height = baseHeight + plHeight + plLongWave;
                break;
            }
            default: {
                double defFbm = fs.sampleFbm(worldX, worldZ, 6, 0.5);
                height = baseHeight + defFbm * 20.0;
            }
        }
        height = Math.max(-64.0, Math.min(512.0, height));
        return height;
    }

    public static double getRiverErosionIntensity(int worldX, int worldZ, NoiseSet noiseSet, double baseHeight, int seaLevel, RegionController.TerrainBlendResult blend) {
        if (blend.macroInfo.elevationTier < 3) {
            return 0.0;
        }
        double erosionNoise = noiseSet.sample(NoiseSet.NoiseProfile.DRAINAGE, worldX, worldZ);
        if (erosionNoise < 0.45) {
            return 0.0;
        }
        double intensity = (erosionNoise - 0.45) / 0.55;
        double elevationFactor = Math.max(0.0, (baseHeight - (double)seaLevel) / 100.0);
        return intensity * elevationFactor * 0.8;
    }

    public static double getAlluvialFactor(int worldX, int worldZ, NoiseSet noiseSet, double baseHeight, int seaLevel) {
        if (baseHeight > (double)(seaLevel + 20)) {
            return 0.0;
        }
        double alluvialNoise = noiseSet.sample(NoiseSet.NoiseProfile.SEABED, worldX, worldZ);
        if (alluvialNoise < 0.45) {
            return 0.0;
        }
        double factor = (alluvialNoise - 0.45) / 0.55;
        double distanceFactor = Math.max(0.0, 1.0 - (baseHeight - (double)seaLevel) / 20.0);
        return factor * distanceFactor * 0.4;
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
        if (isRiver && erosionIntensity > 0.1) {
            double erosionCut = erosionIntensity * 30.0;
            erodedHeight = continuousHeight - erosionCut;
        }
        if (alluvialFactor > 0.1) {
            double alluvialRaise = alluvialFactor * 5.0;
            erodedHeight += alluvialRaise;
        }
        return (int)Math.floor(erodedHeight);
    }

    public static int calculateActualSurfaceHeight(int terrainHeight, boolean isRiver, double riverDepth, int minY) {
        if (isRiver && riverDepth > 0.5) {
            return (int)Math.max((double)minY, (double)terrainHeight - riverDepth);
        }
        return terrainHeight;
    }

    public static boolean isRiverAt(int worldX, int worldZ, NoiseSet noiseSet) {
        double riverNoise = noiseSet.sample(NoiseSet.NoiseProfile.RIVER_PATH, worldX, worldZ);
        double riverPath = noiseSet.sample(NoiseSet.NoiseProfile.RIVER_WIDTH, worldX, worldZ);
        return riverNoise > 0.1 && Math.abs(riverPath) < 0.15;
    }

    public static double getRiverDepthAt(int worldX, int worldZ, NoiseSet noiseSet, int surfaceHeight, int seaLevel) {
        if (!TerrainCalculator.isRiverAt(worldX, worldZ, noiseSet)) {
            return 0.0;
        }
        double riverNoise = noiseSet.sample(NoiseSet.NoiseProfile.RIVER_PATH, worldX, worldZ);
        double depth = (riverNoise - 0.1) * 2.5 * 10.0;
        return Math.max(3.0, Math.min(depth, 20.0));
    }
}

