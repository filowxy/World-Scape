package com.worldscape.util;

public class ClimateUtils {
    public static double calculateClimateDistance(double temp1, double humidity1, double temp2, double humidity2) {
        double tempDiff = temp1 - temp2;
        double humidityDiff = humidity1 - humidity2;
        return Math.sqrt(tempDiff * tempDiff + humidityDiff * humidityDiff);
    }

    public static ClimateProfile getTerrainClimateProfile(String terrainType) {
        switch (terrainType) {
            case "ICE_SHEET": 
            case "GLACIAL_VALLEY": 
            case "CIRQUE": 
            case "HORN": 
            case "FJORD": {
                return new ClimateProfile(0.0, 0.6);
            }
            case "HIGH_MOUNTAINS": 
            case "PLATEAU": 
            case "RIDGE": 
            case "CLIFF": 
            case "PEAK": 
            case "DOME": {
                return new ClimateProfile(0.2, 0.3);
            }
            case "PLAINS": 
            case "HILLS": 
            case "FLOODPLAIN": 
            case "ALLUVIAL_FAN": 
            case "BASIN": 
            case "VALLEY": 
            case "SEA_PLATEAU": {
                return new ClimateProfile(0.5, 0.5);
            }
            case "DUNE": 
            case "GOBI": 
            case "YARDANG": 
            case "SALT_FLAT": {
                return new ClimateProfile(0.8, 0.0);
            }
            case "PEAK_FOREST": 
            case "SINKHOLE": {
                return new ClimateProfile(1.0, 0.8);
            }
            case "CANYON": 
            case "DELTA": {
                return new ClimateProfile(0.5, 0.8);
            }
            case "BEACH": 
            case "SEA_CLIFF": 
            case "TRENCH": {
                return new ClimateProfile(0.5, 0.7);
            }
        }
        return new ClimateProfile(0.5, 0.5);
    }

    public static class ClimateProfile {
        private final double temperature;
        private final double humidity;

        public ClimateProfile(double temperature, double humidity) {
            this.temperature = temperature;
            this.humidity = humidity;
        }

        public double getTemperature() {
            return this.temperature;
        }

        public double getHumidity() {
            return this.humidity;
        }

        public double distanceTo(ClimateProfile other) {
            return ClimateUtils.calculateClimateDistance(this.temperature, this.humidity, other.temperature, other.humidity);
        }
    }
}

