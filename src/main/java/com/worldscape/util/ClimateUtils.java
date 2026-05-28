/*
 * Decompiled with CFR 0.152.
 */
package com.worldscape.util;

public class ClimateUtils {
    public static double calculateClimateDistance(double temp1, double humidity1, double seasonality1, double continentality1, double temp2, double humidity2, double seasonality2, double continentality2) {
        double tempDiff = temp1 - temp2;
        double humidityDiff = humidity1 - humidity2;
        double seasonalityDiff = seasonality1 - seasonality2;
        double continentalityDiff = continentality1 - continentality2;
        return Math.sqrt(tempDiff * tempDiff + humidityDiff * humidityDiff + seasonalityDiff * seasonalityDiff + continentalityDiff * continentalityDiff);
    }

    public static double calculateClimateDistance(double temp1, double humidity1, double temp2, double humidity2) {
        return ClimateUtils.calculateClimateDistance(temp1, humidity1, 0.0, 0.0, temp2, humidity2, 0.0, 0.0);
    }

    public static ClimateProfile getTerrainClimateProfile(String terrainType) {
        return ClimateUtils.getTerrainClimateProfile(terrainType, 0.0);
    }

    public static ClimateProfile getTerrainClimateProfile(String terrainType, double normalizedLatitude) {
        boolean isGlacialTerrain;
        switch (terrainType) {
            case "ICE_SHEET": 
            case "GLACIAL_VALLEY": 
            case "CIRQUE": 
            case "HORN": {
                boolean bl = true;
                break;
            }
            default: {
                boolean bl = isGlacialTerrain = false;
            }
        }
        if (isGlacialTerrain) {
            boolean meetsLatThreshold;
            boolean bl = meetsLatThreshold = Math.abs(normalizedLatitude) > 0.44;
            if (!meetsLatThreshold) {
                return new ClimateProfile(0.2, 0.3, 0.6, 0.7);
            }
        }
        switch (terrainType) {
            case "ICE_SHEET": 
            case "GLACIAL_VALLEY": 
            case "CIRQUE": 
            case "HORN": {
                return new ClimateProfile(0.0, 0.6, 0.1, 0.8);
            }
            case "FJORD": {
                return new ClimateProfile(0.55, 0.75, 0.35, 0.15);
            }
            case "HIGH_MOUNTAINS": 
            case "PLATEAU": 
            case "RIDGE": 
            case "CLIFF": 
            case "PEAK": 
            case "DOME": {
                return new ClimateProfile(0.2, 0.3, 0.6, 0.7);
            }
            case "PLAINS": 
            case "HILLS": 
            case "FLOODPLAIN": 
            case "ALLUVIAL_FAN": 
            case "BASIN": 
            case "VALLEY": 
            case "SEA_PLATEAU": {
                return new ClimateProfile(0.5, 0.5, 0.6, 0.5);
            }
            case "DUNE": 
            case "GOBI": 
            case "YARDANG": 
            case "SALT_FLAT": {
                return new ClimateProfile(0.8, 0.0, 0.8, 0.95);
            }
            case "PEAK_FOREST": 
            case "SINKHOLE": {
                return new ClimateProfile(1.0, 0.8, 0.7, 0.3);
            }
            case "CANYON": 
            case "DELTA": {
                return new ClimateProfile(0.5, 0.8, 0.55, 0.75);
            }
            case "BEACH": 
            case "SEA_CLIFF": {
                return new ClimateProfile(0.5, 0.7, 0.3, 0.2);
            }
            case "TRENCH": {
                return new ClimateProfile(0.08, 0.95, 0.05, 0.05);
            }
        }
        return new ClimateProfile(0.5, 0.5, 0.5, 0.5);
    }

    public static double adjustTemperatureForElevation(double baseTemperature, int elevationTier, double normalizedLatitude) {
        double elevationCooling = (double)elevationTier * 0.05;
        double latitudeCooling = Math.abs(normalizedLatitude) * 0.3;
        return Math.max(0.0, baseTemperature - elevationCooling - latitudeCooling);
    }

    public static ClimateProfile blendClimate(ClimateProfile profile1, ClimateProfile profile2, double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        double invT = 1.0 - t;
        return new ClimateProfile(profile1.getTemperature() * invT + profile2.getTemperature() * t, profile1.getHumidity() * invT + profile2.getHumidity() * t, profile1.getSeasonality() * invT + profile2.getSeasonality() * t, profile1.getContinentality() * invT + profile2.getContinentality() * t);
    }

    public static class ClimateProfile {
        private final double temperature;
        private final double humidity;
        private final double seasonality;
        private final double continentality;

        public ClimateProfile(double temperature, double humidity, double seasonality, double continentality) {
            this.temperature = temperature;
            this.humidity = humidity;
            this.seasonality = seasonality;
            this.continentality = continentality;
        }

        public ClimateProfile(double temperature, double humidity) {
            this(temperature, humidity, 0.5, 0.5);
        }

        public double getTemperature() {
            return this.temperature;
        }

        public double getHumidity() {
            return this.humidity;
        }

        public double getSeasonality() {
            return this.seasonality;
        }

        public double getContinentality() {
            return this.continentality;
        }

        public double distanceTo(ClimateProfile other) {
            return ClimateUtils.calculateClimateDistance(this.temperature, this.humidity, this.seasonality, this.continentality, other.temperature, other.humidity, other.seasonality, other.continentality);
        }

        public String toString() {
            return String.format("ClimateProfile[temp=%.2f, humid=%.2f, season=%.2f, cont=%.2f]", this.temperature, this.humidity, this.seasonality, this.continentality);
        }
    }
}

