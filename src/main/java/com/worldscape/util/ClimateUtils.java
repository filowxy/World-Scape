package com.worldscape.util;

import com.worldscape.terrain.WorldScapeConstants;
import java.util.Objects;

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
        // 空值校验：防止 NPE
        // Null check: prevent NPE
        Objects.requireNonNull(terrainType, "terrainType must not be null");
        boolean isGlacialTerrain;
        switch (terrainType) {
            case "ICE_SHEET": 
            case "GLACIAL_VALLEY": 
            case "CIRQUE": 
            case "HORN": {
                isGlacialTerrain = true;
                break;
            }
            default: {
                isGlacialTerrain = false;
            }
        }
        if (isGlacialTerrain) {
            boolean meetsLatThreshold;
            boolean bl = meetsLatThreshold = Math.abs(normalizedLatitude) > WorldScapeConstants.GLACIAL_LATITUDE_THRESHOLD;
            if (!meetsLatThreshold) {
                return new ClimateProfile(WorldScapeConstants.MOUNTAIN_TEMPERATURE, WorldScapeConstants.MOUNTAIN_HUMIDITY, WorldScapeConstants.MOUNTAIN_SEASONALITY, WorldScapeConstants.MOUNTAIN_CONTINENTALITY);
            }
        }
        switch (terrainType) {
            case "ICE_SHEET": 
            case "GLACIAL_VALLEY": 
            case "CIRQUE": 
            case "HORN": {
                return new ClimateProfile(WorldScapeConstants.ICE_TEMPERATURE, WorldScapeConstants.ICE_HUMIDITY, WorldScapeConstants.ICE_SEASONALITY, WorldScapeConstants.ICE_CONTINENTALITY);
            }
            case "FJORD": {
                return new ClimateProfile(WorldScapeConstants.COASTAL_TEMPERATE_TEMP, WorldScapeConstants.COASTAL_TEMPERATE_HUMID, WorldScapeConstants.COASTAL_TEMPERATE_SEASON, WorldScapeConstants.COASTAL_TEMPERATE_CONT);
            }
            case "HIGH_MOUNTAINS": 
            case "PLATEAU": 
            case "RIDGE": 
            case "CLIFF": 
            case "PEAK": 
            case "DOME": {
                return new ClimateProfile(WorldScapeConstants.MOUNTAIN_TEMPERATURE, WorldScapeConstants.MOUNTAIN_HUMIDITY, WorldScapeConstants.MOUNTAIN_SEASONALITY, WorldScapeConstants.MOUNTAIN_CONTINENTALITY);
            }
            case "PLAINS": 
            case "HILLS": 
            case "FLOODPLAIN": 
            case "ALLUVIAL_FAN": 
            case "BASIN": 
            case "VALLEY": 
            case "SEA_PLATEAU": {
                return new ClimateProfile(WorldScapeConstants.PLAINS_TEMPERATURE, WorldScapeConstants.PLAINS_HUMIDITY, WorldScapeConstants.PLAINS_SEASONALITY, WorldScapeConstants.PLAINS_CONTINENTALITY);
            }
            case "DUNE": 
            case "GOBI": 
            case "YARDANG": 
            case "SALT_FLAT": {
                return new ClimateProfile(WorldScapeConstants.DESERT_TEMPERATURE, WorldScapeConstants.DESERT_HUMIDITY, WorldScapeConstants.DESERT_SEASONALITY, WorldScapeConstants.DESERT_CONTINENTALITY);
            }
            case "PEAK_FOREST": 
            case "SINKHOLE": {
                return new ClimateProfile(WorldScapeConstants.FOREST_TEMPERATURE, WorldScapeConstants.FOREST_HUMIDITY, WorldScapeConstants.FOREST_SEASONALITY, WorldScapeConstants.FOREST_CONTINENTALITY);
            }
            case "CANYON": 
            case "DELTA": {
                return new ClimateProfile(WorldScapeConstants.CANYON_CLIMATE_TEMPERATURE, WorldScapeConstants.CANYON_CLIMATE_HUMIDITY, WorldScapeConstants.CANYON_SEASONALITY, WorldScapeConstants.CANYON_CONTINENTALITY);
            }
            case "BEACH": 
            case "SEA_CLIFF": {
                return new ClimateProfile(WorldScapeConstants.BEACH_TEMPERATURE, WorldScapeConstants.BEACH_HUMIDITY, WorldScapeConstants.BEACH_SEASONALITY, WorldScapeConstants.BEACH_CONTINENTALITY);
            }
            case "TRENCH": {
                return new ClimateProfile(WorldScapeConstants.DEEP_OCEAN_TEMP, WorldScapeConstants.DEEP_OCEAN_HUMID, WorldScapeConstants.DEEP_OCEAN_SEASON, WorldScapeConstants.DEEP_OCEAN_CONT);
            }
        }
        return new ClimateProfile(WorldScapeConstants.DEFAULT_TEMPERATURE, WorldScapeConstants.DEFAULT_HUMIDITY, WorldScapeConstants.DEFAULT_SEASONALITY, WorldScapeConstants.DEFAULT_CONTINENTALITY);
    }

    public static double adjustTemperatureForElevation(double baseTemperature, int elevationTier, double normalizedLatitude) {
        double elevationCooling = (double)elevationTier * WorldScapeConstants.ELEVATION_COOLING_PER_TIER;
        double latitudeCooling = Math.abs(normalizedLatitude) * WorldScapeConstants.LATITUDE_COOLING_FACTOR;
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
            this(temperature, humidity, WorldScapeConstants.DEFAULT_SEASONALITY, WorldScapeConstants.DEFAULT_CONTINENTALITY);
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

