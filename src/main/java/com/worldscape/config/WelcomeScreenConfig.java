package com.worldscape.config;

import com.worldscape.config.WelcomeScreen;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WelcomeScreenConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(WelcomeScreenConfig.class);
    private static final String MOD_VERSION = "1.3.1-beta";
    private static final Path CONFIG_DIR = Paths.get("config", "worldscape");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("settings.txt");
    private static boolean showWelcomeScreen = true;
    private static boolean debugMode = false;
    private static volatile boolean hasChecked = false;
    private static String lastModVersion = "";
    private static String terrainScale = "standard";
    private static String riverIntensity = "standard";
    private static String mountainHeight = "standard";
    private static boolean islandMode = false;
    private static final Object CONFIG_LOCK = new Object();

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private static void ensureLoaded() {
        if (!hasChecked) {
            Object object = CONFIG_LOCK;
            synchronized (object) {
                if (!hasChecked) {
                    WelcomeScreenConfig.load();
                    hasChecked = true;
                }
            }
        }
    }

    public static boolean shouldShowWelcomeScreen() {
        WelcomeScreenConfig.ensureLoaded();
        if (!showWelcomeScreen && !lastModVersion.equals(MOD_VERSION)) {
            return true;
        }
        return showWelcomeScreen;
    }

    public static boolean isDebugMode() {
        WelcomeScreenConfig.ensureLoaded();
        return debugMode;
    }

    public static void markWelcomeScreenShown() {
        showWelcomeScreen = false;
        lastModVersion = MOD_VERSION;
        WelcomeScreenConfig.save();
    }

    public static void setDebugMode(boolean enabled) {
        debugMode = enabled;
        WelcomeScreenConfig.save();
        if (enabled) {
            LOGGER.info("[World Scape] Debug Mode ENABLED - Advanced debugging features activated");
        } else {
            LOGGER.info("[World Scape] Debug Mode DISABLED - Standard mode active");
        }
    }

    public static String getTerrainScale() {
        WelcomeScreenConfig.ensureLoaded();
        return terrainScale;
    }

    public static void setTerrainScale(String scale) {
        terrainScale = scale;
        WelcomeScreenConfig.save();
    }

    public static String getRiverIntensity() {
        WelcomeScreenConfig.ensureLoaded();
        return riverIntensity;
    }

    public static void setRiverIntensity(String intensity) {
        riverIntensity = intensity;
        WelcomeScreenConfig.save();
    }

    public static String getMountainHeight() {
        WelcomeScreenConfig.ensureLoaded();
        return mountainHeight;
    }

    public static void setMountainHeight(String height) {
        mountainHeight = height;
        WelcomeScreenConfig.save();
    }

    public static boolean isIslandMode() {
        WelcomeScreenConfig.ensureLoaded();
        return islandMode;
    }

    public static void setIslandMode(boolean enabled) {
        islandMode = enabled;
        WelcomeScreenConfig.save();
    }

    public static void load() {
        if (Files.exists(CONFIG_FILE, new LinkOption[0])) {
            try {
                for (String line : Files.readAllLines(CONFIG_FILE)) {
                    if (line.startsWith("showWelcomeScreen=")) {
                        showWelcomeScreen = line.substring("showWelcomeScreen=".length()).trim().equals("true");
                        continue;
                    }
                    if (line.startsWith("debugMode=")) {
                        debugMode = line.substring("debugMode=".length()).trim().equals("true");
                        continue;
                    }
                    if (line.startsWith("lastModVersion=")) {
                        lastModVersion = line.substring("lastModVersion=".length()).trim();
                        continue;
                    }
                    if (line.startsWith("terrainScale=")) {
                        terrainScale = line.substring("terrainScale=".length()).trim();
                        continue;
                    }
                    if (line.startsWith("riverIntensity=")) {
                        riverIntensity = line.substring("riverIntensity=".length()).trim();
                        continue;
                    }
                    if (line.startsWith("mountainHeight=")) {
                        mountainHeight = line.substring("mountainHeight=".length()).trim();
                        continue;
                    }
                    if (!line.startsWith("islandMode=")) continue;
                    islandMode = line.substring("islandMode=".length()).trim().equals("true");
                }
            }
            catch (IOException e) {
                LOGGER.error("Failed to load config", (Throwable)e);
            }
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_DIR, new FileAttribute[0]);
            StringBuilder sb = new StringBuilder();
            sb.append("showWelcomeScreen=").append(showWelcomeScreen).append("\n");
            sb.append("debugMode=").append(debugMode).append("\n");
            sb.append("lastModVersion=").append(lastModVersion).append("\n");
            sb.append("terrainScale=").append(terrainScale).append("\n");
            sb.append("riverIntensity=").append(riverIntensity).append("\n");
            sb.append("mountainHeight=").append(mountainHeight).append("\n");
            sb.append("islandMode=").append(islandMode).append("\n");
            Files.writeString(CONFIG_FILE, (CharSequence)sb.toString(), new OpenOption[0]);
        }
        catch (IOException e) {
            LOGGER.error("Failed to save config", (Throwable)e);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!Minecraft.getInstance().isSameThread()) {
            LOGGER.warn("[World Scape] onClientTick called from non-client thread, skipping");
            return;
        }
        if (WelcomeScreenConfig.shouldShowWelcomeScreen() && Minecraft.getInstance().player != null) {
            Minecraft.getInstance().setScreen((Screen)new WelcomeScreen());
            WelcomeScreenConfig.markWelcomeScreenShown();
        }
    }

    public static void openWelcomeScreen() {
        Minecraft.getInstance().setScreen((Screen)new WelcomeScreen());
    }
}

