/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.neoforged.fml.event.config.ModConfigEvent$Loading
 *  net.neoforged.fml.event.config.ModConfigEvent$Reloading
 *  net.neoforged.neoforge.common.ModConfigSpec
 *  net.neoforged.neoforge.common.ModConfigSpec$BooleanValue
 *  net.neoforged.neoforge.common.ModConfigSpec$Builder
 *  net.neoforged.neoforge.common.ModConfigSpec$DoubleValue
 *  net.neoforged.neoforge.common.ModConfigSpec$IntValue
 */
package com.worldscape.config;

import java.util.concurrent.atomic.AtomicReference;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

public class ConfigManager {
    public static final ModConfigSpec SPEC;
    public static final Config CONFIG;
    private static final AtomicReference<ConfigSnapshot> SNAPSHOT;

    public static void onConfigLoad(ModConfigEvent.Loading event) {
        ConfigManager.refreshSnapshot();
    }

    public static void onConfigReload(ModConfigEvent.Reloading event) {
        ConfigManager.refreshSnapshot();
    }

    private static void refreshSnapshot() {
        ConfigSnapshot snapshot = new ConfigSnapshot((Integer)ConfigManager.CONFIG.seaLevel.get(), (Integer)ConfigManager.CONFIG.cacheMaxSize.get(), (Double)ConfigManager.CONFIG.autoMatchThreshold.get(), (Boolean)ConfigManager.CONFIG.enableTemperatureLink.get(), (Boolean)ConfigManager.CONFIG.silenceNoisiumWarning.get(), (Integer)ConfigManager.CONFIG.controlPointMinDistance.get(), (Integer)ConfigManager.CONFIG.controlPointMaxDistance.get(), (Integer)ConfigManager.CONFIG.elevationDiffThreshold.get(), (Integer)ConfigManager.CONFIG.plainsMacroWavelength.get(), (Double)ConfigManager.CONFIG.plainsMacroAmplitude.get(), (Integer)ConfigManager.CONFIG.plainsMesoWavelength.get(), (Double)ConfigManager.CONFIG.plainsMesoAmplitude.get(), (Integer)ConfigManager.CONFIG.plainsMicroWavelength.get(), (Double)ConfigManager.CONFIG.plainsMicroAmplitude.get(), (Double)ConfigManager.CONFIG.plainsErosionDepth.get(), (Integer)ConfigManager.CONFIG.regionSize.get(), (Integer)ConfigManager.CONFIG.cellSize.get(), (Integer)ConfigManager.CONFIG.searchRadius.get(), (Integer)ConfigManager.CONFIG.reloadCooldown.get(), (String)ConfigManager.CONFIG.terrainTypeConfigDir.get());
        SNAPSHOT.set(snapshot);
    }

    public static ConfigSnapshot getSnapshot() {
        ConfigSnapshot snapshot = SNAPSHOT.get();
        if (snapshot == null) {
            ConfigManager.refreshSnapshot();
            snapshot = SNAPSHOT.get();
        }
        return snapshot;
    }

    public static int getSeaLevel() {
        return ConfigManager.getSnapshot().seaLevel();
    }

    public static int getCacheMaxSize() {
        return ConfigManager.getSnapshot().cacheMaxSize();
    }

    public static double getAutoMatchThreshold() {
        return ConfigManager.getSnapshot().autoMatchThreshold();
    }

    public static boolean isTemperatureLinkEnabled() {
        return ConfigManager.getSnapshot().enableTemperatureLink();
    }

    public static boolean shouldSilenceNoisiumWarning() {
        return ConfigManager.getSnapshot().silenceNoisiumWarning();
    }

    public static int getControlPointMinDistance() {
        return ConfigManager.getSnapshot().controlPointMinDistance();
    }

    public static int getControlPointMaxDistance() {
        return ConfigManager.getSnapshot().controlPointMaxDistance();
    }

    public static int getElevationDiffThreshold() {
        return ConfigManager.getSnapshot().elevationDiffThreshold();
    }

    public static int getPlainsMacroWavelength() {
        return ConfigManager.getSnapshot().plainsMacroWavelength();
    }

    public static double getPlainsMacroAmplitude() {
        return ConfigManager.getSnapshot().plainsMacroAmplitude();
    }

    public static int getPlainsMesoWavelength() {
        return ConfigManager.getSnapshot().plainsMesoWavelength();
    }

    public static double getPlainsMesoAmplitude() {
        return ConfigManager.getSnapshot().plainsMesoAmplitude();
    }

    public static int getPlainsMicroWavelength() {
        return ConfigManager.getSnapshot().plainsMicroWavelength();
    }

    public static double getPlainsMicroAmplitude() {
        return ConfigManager.getSnapshot().plainsMicroAmplitude();
    }

    public static double getPlainsErosionDepth() {
        return ConfigManager.getSnapshot().plainsErosionDepth();
    }

    public static int getRegionSize() {
        return ConfigManager.getSnapshot().regionSize();
    }

    public static int getCellSize() {
        return ConfigManager.getSnapshot().cellSize();
    }

    public static int getSearchRadius() {
        return ConfigManager.getSnapshot().searchRadius();
    }

    public static int getReloadCooldown() {
        return ConfigManager.getSnapshot().reloadCooldown();
    }

    /**
     * Get the configured terrain type config directory path.
     * 获取配置的地形类型配置目录路径。
     *
     * @return the directory path string, or empty string if not configured / 目录路径字符串，未配置时返回空字符串
     */
    public static String getTerrainTypeConfigDirStatic() {
        return ConfigManager.getSnapshot().terrainTypeConfigDir();
    }

    static {
        SNAPSHOT = new AtomicReference();
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        CONFIG = new Config(builder);
        SPEC = builder.build();
    }

    public record ConfigSnapshot(int seaLevel, int cacheMaxSize, double autoMatchThreshold, boolean enableTemperatureLink, boolean silenceNoisiumWarning, int controlPointMinDistance, int controlPointMaxDistance, int elevationDiffThreshold, int plainsMacroWavelength, double plainsMacroAmplitude, int plainsMesoWavelength, double plainsMesoAmplitude, int plainsMicroWavelength, double plainsMicroAmplitude, double plainsErosionDepth, int regionSize, int cellSize, int searchRadius, int reloadCooldown, String terrainTypeConfigDir) {
    }

    public static class Config {
        public final ModConfigSpec.IntValue seaLevel;
        public final ModConfigSpec.IntValue cacheMaxSize;
        public final ModConfigSpec.DoubleValue autoMatchThreshold;
        public final ModConfigSpec.BooleanValue enableTemperatureLink;
        public final ModConfigSpec.BooleanValue silenceNoisiumWarning;
        public final ModConfigSpec.IntValue controlPointMinDistance;
        public final ModConfigSpec.IntValue controlPointMaxDistance;
        public final ModConfigSpec.IntValue elevationDiffThreshold;
        public final ModConfigSpec.IntValue plainsMacroWavelength;
        public final ModConfigSpec.DoubleValue plainsMacroAmplitude;
        public final ModConfigSpec.IntValue plainsMesoWavelength;
        public final ModConfigSpec.DoubleValue plainsMesoAmplitude;
        public final ModConfigSpec.IntValue plainsMicroWavelength;
        public final ModConfigSpec.DoubleValue plainsMicroAmplitude;
        public final ModConfigSpec.DoubleValue plainsErosionDepth;
        public final ModConfigSpec.IntValue regionSize;
        public final ModConfigSpec.IntValue cellSize;
        public final ModConfigSpec.IntValue searchRadius;
        public final ModConfigSpec.IntValue reloadCooldown;
        public final ModConfigSpec.ConfigValue<String> terrainTypeConfigDir;

        public Config(ModConfigSpec.Builder builder) {
            builder.push("general");
            this.seaLevel = builder.comment("\u6d77\u5e73\u9762\u9ad8\u5ea6\uff08\u65b9\u5757\uff09").defineInRange("sea_level", 64, 0, 256);
            this.cacheMaxSize = builder.comment("\u6bcf\u4e2a\u7ef4\u5ea6\u7684\u6700\u5927\u7f13\u5b58\u5355\u5143\u6570").defineInRange("cache_max_size", 1024, 256, 8192);
            this.autoMatchThreshold = builder.comment("\u751f\u7269\u7fa4\u7cfb\u4e0e\u5730\u5f62\u5339\u914d\u7684\u9608\u503c\uff080.0-1.0\uff09").defineInRange("auto_match_threshold", 0.3, 0.0, 1.0);
            this.enableTemperatureLink = builder.comment("\u542f\u7528\u4e0e\u6e29\u5ea6\u76f8\u5173\u6a21\u7ec4\u7684\u94fe\u63a5").define("enable_temperature_link", true);
            this.silenceNoisiumWarning = builder.comment("\u9759\u97f3 Noisium \u6a21\u7ec4\u8b66\u544a").define("silence_noisium_warning", false);
            this.terrainTypeConfigDir = builder.comment("Custom terrain type JSON directory (highest priority). Leave empty to disable.", "自定义地形类型 JSON 目录（最高优先级）。留空则禁用。").define("terrain_type_config_dir", "");
            builder.pop();
            builder.push("control_points");
            this.controlPointMinDistance = builder.comment("\u63a7\u5236\u70b9\u4e4b\u95f4\u7684\u6700\u5c0f\u8ddd\u79bb\uff08\u65b9\u5757\uff09").defineInRange("control_point_min_distance", 150, 50, 500);
            this.controlPointMaxDistance = builder.comment("\u63a7\u5236\u70b9\u4e4b\u95f4\u7684\u6700\u5927\u8ddd\u79bb\uff08\u65b9\u5757\uff09").defineInRange("control_point_max_distance", 300, 100, 1000);
            this.elevationDiffThreshold = builder.comment("\u76f8\u90bb\u63a7\u5236\u70b9\u4e4b\u95f4\u7684\u6700\u5927\u6d77\u62d4\u5dee\u5f02\uff08\u65b9\u5757\uff09").defineInRange("elevation_diff_threshold", 120, 50, 300);
            builder.pop();
            builder.push("plains_folds");
            this.plainsMacroWavelength = builder.comment("\u5e73\u539f\u5b8f\u89c2\u57fa\u51c6\u6ce2\u957f\uff08\u683c\uff09").defineInRange("plains_macro_wavelength", 512, 256, 2048);
            this.plainsMacroAmplitude = builder.comment("\u5e73\u539f\u5b8f\u89c2\u57fa\u51c6\u632f\u5e45\uff08\u683c\uff09").defineInRange("plains_macro_amplitude", 2.0, 0.5, 10.0);
            this.plainsMesoWavelength = builder.comment("\u5e73\u539f\u4e2d\u89c2\u8936\u76b1\u6ce2\u957f\uff08\u683c\uff09").defineInRange("plains_meso_wavelength", 96, 48, 384);
            this.plainsMesoAmplitude = builder.comment("\u5e73\u539f\u4e2d\u89c2\u8936\u76b1\u632f\u5e45\uff08\u683c\uff09").defineInRange("plains_meso_amplitude", 5.0, 1.0, 20.0);
            this.plainsMicroWavelength = builder.comment("\u5e73\u539f\u5fae\u89c2\u7ec6\u8282\u6ce2\u957f\uff08\u683c\uff09").defineInRange("plains_micro_wavelength", 24, 12, 96);
            this.plainsMicroAmplitude = builder.comment("\u5e73\u539f\u5fae\u89c2\u7ec6\u8282\u632f\u5e45\uff08\u683c\uff09").defineInRange("plains_micro_amplitude", 1.2, 0.3, 5.0);
            this.plainsErosionDepth = builder.comment("\u5e73\u539f\u4fb5\u8680\u5370\u8bb0\u6700\u5927\u4e0b\u5207\u6df1\u5ea6\uff08\u683c\uff09").defineInRange("plains_erosion_depth", 2.5, 0.5, 10.0);
            builder.pop();
            builder.push("terrain_regions");
            this.regionSize = builder.comment("\u5730\u5f62\u533a\u57df\u5927\u5c0f\uff08\u683c\uff09").defineInRange("region_size", 256, 128, 1024);
            this.cellSize = builder.comment("\u5143\u80de\u5927\u5c0f\uff08\u683c\uff09").defineInRange("cell_size", 16, 8, 64);
            this.searchRadius = builder.comment("\u63a7\u5236\u70b9\u641c\u7d22\u534a\u5f84\uff08\u683c\uff09").defineInRange("search_radius", 300, 100, 1000);
            builder.pop();
            builder.push("performance");
            this.reloadCooldown = builder.comment("\u914d\u7f6e\u91cd\u8f7d\u51b7\u5374\u65f6\u95f4\uff08\u6beb\u79d2\uff09").defineInRange("reload_cooldown", 5000, 1000, 60000);
            builder.pop();
        }
    }
}

