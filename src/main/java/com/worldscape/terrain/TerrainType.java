package com.worldscape.terrain;

import com.worldscape.terrain.TerrainContext;
import com.worldscape.util.NoiseUtils;
import com.worldscape.util.WorldScapeUtils;
import java.util.List;
import java.util.function.Function;

public enum TerrainType {
    HIGH_MOUNTAINS("high_mountains", 260, 512, TerrainType::highMountainsHeight),
    HILLS("hills", 55, 110, TerrainType::hillsHeight),
    CLIFF("cliff", 55, 220, TerrainType::cliffHeight),
    PLATEAU("plateau", 165, 275, TerrainType::plateauHeight),
    VALLEY("valley", 28, 83, TerrainType::valleyHeight),
    RIDGE("ridge", 140, 275, TerrainType::ridgeHeight),
    PEAK("peak", 165, 330, TerrainType::peakHeight),
    CANYON("canyon", 11, 83, TerrainType::canyonHeight),
    ALLUVIAL_FAN("alluvial_fan", 44, 110, TerrainType::alluvialFanHeight),
    FLOODPLAIN("floodplain", 28, 44, TerrainType::floodplainHeight),
    DUNE("dune", 28, 55, TerrainType::duneHeight),
    GOBI("gobi", 33, 66, TerrainType::gobiHeight),
    YARDANG("yardang", 44, 99, TerrainType::yardangHeight),
    SALT_FLAT("salt_flat", 22, 33, TerrainType::saltFlatHeight),
    ICE_SHEET("ice_sheet", 55, 165, TerrainType::iceSheetHeight),
    GLACIAL_VALLEY("glacial_valley", 28, 110, TerrainType::glacialValleyHeight),
    CIRQUE("cirque", 83, 193, TerrainType::cirqueHeight),
    HORN("horn", 165, 330, TerrainType::hornHeight),
    BEACH("beach", 28, 39, TerrainType::beachHeight),
    SEA_CLIFF("sea_cliff", 44, 110, TerrainType::seaCliffHeight),
    FJORD("fjord", 17, 110, TerrainType::fjordHeight),
    DELTA("delta", 22, 39, TerrainType::deltaHeight),
    PEAK_FOREST("peak_forest", 83, 193, TerrainType::peakForestHeight),
    SINKHOLE("sinkhole", 11, 55, TerrainType::sinkholeHeight),
    PLAINS("plains", 33, 55, TerrainType::plainsHeight),
    BASIN("basin", 22, 66, TerrainType::basinHeight),
    DOME("dome", 83, 193, TerrainType::domeHeight),
    TRENCH("trench", -55, 0, TerrainType::trenchHeight),
    SEA_PLATEAU("sea_plateau", -28, 17, TerrainType::seaPlateauHeight);

    private final String id;
    private final int minHeight;
    private final int maxHeight;
    private final Function<TerrainContext, Double> heightFunction;
    private static final List<TerrainType> TIER_0_WHITELIST;
    private static final List<TerrainType> TIER_1_WHITELIST;
    private static final List<TerrainType> TIER_2_WHITELIST;
    private static final List<TerrainType> TIER_3_WHITELIST;
    private static final List<TerrainType> TIER_4_WHITELIST;
    private static final List<TerrainType> TIER_5_WHITELIST;

    private TerrainType(String id, int minHeight, int maxHeight, Function<TerrainContext, Double> heightFunction) {
        this.id = id;
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
        this.heightFunction = heightFunction;
    }

    public String getId() {
        return this.id;
    }

    public int getMinHeight() {
        return this.minHeight;
    }

    public int getMaxHeight() {
        return this.maxHeight;
    }

    public double calculateHeight(TerrainContext context) {
        return this.heightFunction.apply(context);
    }

    private static double highMountainsHeight(TerrainContext context) {
        double n1 = context.getN1();
        double n2 = context.getN2();
        double height = NoiseUtils.combineNoises(new double[]{3.0, 2.0}, new double[]{n1, n2});
        return WorldScapeUtils.clamp(height, 260.0, 512.0);
    }

    private static double hillsHeight(TerrainContext context) {
        double n2 = context.getN2();
        return 70.0 + 22.0 * (Math.sin(n2) + Math.cos(n2));
    }

    private static double cliffHeight(TerrainContext context) {
        double n2 = context.getN2();
        return 55.0 + 83.0 * n2;
    }

    private static double plateauHeight(TerrainContext context) {
        double n2 = context.getN2();
        return 165.0 + 55.0 * n2;
    }

    private static double valleyHeight(TerrainContext context) {
        double n2 = context.getN2();
        return 28.0 + 28.0 * (1.0 - Math.abs(n2));
    }

    private static double ridgeHeight(TerrainContext context) {
        double n1 = context.getN1();
        double n2 = context.getN2();
        return 140.0 + 83.0 * Math.abs(n1) + 55.0 * n2;
    }

    private static double peakHeight(TerrainContext context) {
        double n1 = context.getN1();
        double n2 = context.getN2();
        return 165.0 + 110.0 * Math.abs(n1) + 55.0 * n2;
    }

    private static double canyonHeight(TerrainContext context) {
        double dist = context.getDistance();
        double width = 44.0;
        return 35.0 - 44.0 * NoiseUtils.bowlNoise(dist, width);
    }

    private static double alluvialFanHeight(TerrainContext context) {
        double n2 = context.getN2();
        return 44.0 + 33.0 * n2;
    }

    private static double floodplainHeight(TerrainContext context) {
        double n3 = context.getN3();
        return 28.0 + 8.0 * n3;
    }

    private static double duneHeight(TerrainContext context) {
        double n1 = context.getN1();
        double n2 = context.getN2();
        return 28.0 + NoiseUtils.duneNoise(n1, n2, 17.0);
    }

    private static double gobiHeight(TerrainContext context) {
        double n2 = context.getN2();
        return 33.0 + 17.0 * n2;
    }

    private static double yardangHeight(TerrainContext context) {
        double n2 = context.getN2();
        return 44.0 + 28.0 * Math.abs(n2);
    }

    private static double saltFlatHeight(TerrainContext context) {
        double n3 = context.getN3();
        return 22.0 + 6.0 * n3;
    }

    private static double iceSheetHeight(TerrainContext context) {
        double n1 = context.getN1();
        double n2 = context.getN2();
        return 55.0 + 55.0 * n1 + 28.0 * n2;
    }

    private static double glacialValleyHeight(TerrainContext context) {
        double n2 = context.getN2();
        return 28.0 + 41.0 * (1.0 - Math.abs(n2));
    }

    private static double cirqueHeight(TerrainContext context) {
        double n2 = context.getN2();
        return 83.0 + 55.0 * n2;
    }

    private static double hornHeight(TerrainContext context) {
        double n1 = context.getN1();
        double n2 = context.getN2();
        return 165.0 + 110.0 * n1 + 55.0 * n2;
    }

    private static double beachHeight(TerrainContext context) {
        double n3 = context.getN3();
        return 28.0 + 6.0 * n3;
    }

    private static double seaCliffHeight(TerrainContext context) {
        double n2 = context.getN2();
        return 44.0 + 33.0 * n2;
    }

    private static double fjordHeight(TerrainContext context) {
        double n2 = context.getN2();
        return 17.0 + 47.0 * (1.0 - Math.abs(n2));
    }

    private static double deltaHeight(TerrainContext context) {
        double n3 = context.getN3();
        return 22.0 + 8.0 * n3;
    }

    private static double peakForestHeight(TerrainContext context) {
        double n2 = context.getN2();
        return 83.0 + 55.0 * n2;
    }

    private static double sinkholeHeight(TerrainContext context) {
        double dist = context.getDistance();
        double width = 28.0;
        return 11.0 + 22.0 * Math.exp(-Math.pow(dist / width, 2.0));
    }

    private static double plainsHeight(TerrainContext context) {
        double n3 = context.getN3();
        return 35.0 + 11.0 * n3;
    }

    private static double basinHeight(TerrainContext context) {
        double dist = context.getDistance();
        double rimHeight = 66.0;
        return rimHeight - 33.0 * NoiseUtils.bowlNoise(dist, 55.0);
    }

    private static double domeHeight(TerrainContext context) {
        double n1 = context.getN1();
        double n2 = context.getN2();
        return 83.0 + 55.0 * n1 + 28.0 * n2;
    }

    private static double trenchHeight(TerrainContext context) {
        double n2 = context.getN2();
        return -28.0 - 28.0 * n2;
    }

    private static double seaPlateauHeight(TerrainContext context) {
        double n2 = context.getN2();
        return -6.0 - 11.0 * n2;
    }

    public static List<TerrainType> getWhitelistForTier(int tier) {
        return switch (tier) {
            case 0 -> TIER_0_WHITELIST;
            case 1 -> TIER_1_WHITELIST;
            case 2 -> TIER_2_WHITELIST;
            case 3 -> TIER_3_WHITELIST;
            case 4 -> TIER_4_WHITELIST;
            case 5 -> TIER_5_WHITELIST;
            default -> TIER_3_WHITELIST;
        };
    }

    public static boolean isValidForTier(TerrainType type, int tier) {
        return TerrainType.getWhitelistForTier(tier).contains((Object)type);
    }

    static {
        TIER_0_WHITELIST = List.of(TRENCH, SEA_PLATEAU);
        TIER_1_WHITELIST = List.of(SEA_PLATEAU, DELTA);
        TIER_2_WHITELIST = List.of(BEACH, DELTA, FLOODPLAIN, DUNE, SALT_FLAT, SEA_CLIFF, FJORD);
        TIER_3_WHITELIST = List.of(PLAINS, HILLS, FLOODPLAIN, DUNE, GOBI, YARDANG, BASIN, SINKHOLE, PEAK_FOREST);
        TIER_4_WHITELIST = List.of(HILLS, CLIFF, PLATEAU, VALLEY, CANYON, ALLUVIAL_FAN, GOBI, CIRQUE, GLACIAL_VALLEY, DOME);
        TIER_5_WHITELIST = List.of(HIGH_MOUNTAINS, CLIFF, PLATEAU, RIDGE, PEAK, CIRQUE, HORN, ICE_SHEET, GLACIAL_VALLEY);
    }
}

