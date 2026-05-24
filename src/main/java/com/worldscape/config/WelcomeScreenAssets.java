package com.worldscape.config;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class WelcomeScreenAssets {
    private static final Map<String, BufferedImage> textureCache = new HashMap<String, BufferedImage>();
    private static final Map<String, ResourceLocation> resourceCache = new HashMap<String, ResourceLocation>();
    private static final int LOGO_WIDTH = 200;
    private static final int LOGO_HEIGHT = 60;
    private static final int PREVIEW_WIDTH = 600;
    private static final int PREVIEW_HEIGHT = 200;
    public static final int TITLE_COLOR = 5025616;
    public static final int HEADER_COLOR = 3046706;
    public static final int ACCENT_COLOR = 8505220;
    public static final int DARK_COLOR = 1793568;

    public static BufferedImage getLogo() {
        return WelcomeScreenAssets.getOrCreate("logo", () -> WelcomeScreenAssets.createWorldScapeLogo());
    }

    public static BufferedImage getTerrainPreview() {
        return WelcomeScreenAssets.getOrCreate("terrain_preview", () -> WelcomeScreenAssets.createTerrainPreview());
    }

    public static BufferedImage getBackgroundPattern() {
        return WelcomeScreenAssets.getOrCreate("bg_pattern", () -> WelcomeScreenAssets.createBackgroundPattern());
    }

    private static BufferedImage getOrCreate(String key, Supplier<BufferedImage> creator) {
        return textureCache.computeIfAbsent(key, k -> (BufferedImage)creator.get());
    }

    private static BufferedImage createWorldScapeLogo() {
        BufferedImage image = new BufferedImage(200, 60, 2);
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setColor(new Color(0, 0, 0, 0));
        g2d.fillRect(0, 0, 200, 60);
        Font titleFont = new Font("Arial", 1, 36);
        Font subtitleFont = new Font("Arial", 0, 14);
        String title = "World Scape";
        FontMetrics fm = g2d.getFontMetrics(titleFont);
        int titleX = (200 - fm.stringWidth(title)) / 2;
        GradientPaint gradient = new GradientPaint(0.0f, 10.0f, new Color(5025616), 200.0f, 40.0f, new Color(8505220));
        g2d.setPaint(gradient);
        g2d.setFont(titleFont);
        g2d.drawString(title, titleX, 40);
        g2d.setColor(new Color(0xAAAAAA));
        g2d.setFont(subtitleFont);
        String subtitle = "Advanced Terrain Generation";
        fm = g2d.getFontMetrics(subtitleFont);
        int subX = (200 - fm.stringWidth(subtitle)) / 2;
        g2d.drawString(subtitle, subX, 55);
        g2d.dispose();
        return image;
    }

    private static BufferedImage createTerrainPreview() {
        BufferedImage image = new BufferedImage(600, 200, 2);
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        int[] heightMap = WelcomeScreenAssets.generateTerrainHeights(600, 200);
        for (int x = 0; x < 600; ++x) {
            for (int y = 0; y < 200; ++y) {
                int height = heightMap[y * 600 + x];
                int color = WelcomeScreenAssets.getTerrainColor(height, y);
                image.setRGB(x, y, color);
            }
        }
        g2d.setStroke(new BasicStroke(1.0f));
        g2d.setColor(new Color(0xFFFFFF, true));
        for (int i = 0; i < 3; ++i) {
            int yPos = 50 * (i + 1);
            g2d.drawLine(0, yPos, 600, yPos);
        }
        g2d.dispose();
        return image;
    }

    private static int[] generateTerrainHeights(int width, int height) {
        int[] heights = new int[width * height];
        for (int x = 0; x < width; ++x) {
            for (int y = 0; y < height; ++y) {
                double nx = (double)x * 0.02;
                double ny = (double)y * 0.02;
                double terrainHeight = 0.0;
                terrainHeight += WelcomeScreenAssets.noise(nx, ny, 1.0) * 40.0;
                terrainHeight += WelcomeScreenAssets.noise(nx * 2.0, ny * 2.0, 0.5) * 20.0;
                terrainHeight += WelcomeScreenAssets.noise(nx * 4.0, ny * 4.0, 0.25) * 10.0;
                double mountainRegion = WelcomeScreenAssets.smoothNoise((double)x * 0.005, (double)y * 0.005, 0.3);
                if (mountainRegion > 0.6) {
                    terrainHeight += (mountainRegion - 0.6) * 150.0;
                }
                heights[y * width + x] = (int)(terrainHeight + 64.0);
            }
        }
        return heights;
    }

    private static double noise(double x, double y, double scale) {
        int ix = (int)Math.floor(x * scale);
        int iy = (int)Math.floor(y * scale);
        double fx = x * scale - (double)ix;
        double fy = y * scale - (double)iy;
        double v00 = WelcomeScreenAssets.hash(ix, iy);
        double v10 = WelcomeScreenAssets.hash(ix + 1, iy);
        double v01 = WelcomeScreenAssets.hash(ix, iy + 1);
        double v11 = WelcomeScreenAssets.hash(ix + 1, iy + 1);
        double sx = fx * fx * (3.0 - 2.0 * fx);
        double sy = fy * fy * (3.0 - 2.0 * fy);
        return v00 * (1.0 - sx) * (1.0 - sy) + v10 * sx * (1.0 - sy) + v01 * (1.0 - sx) * sy + v11 * sx * sy;
    }

    private static double smoothNoise(double x, double y, double scale) {
        int ix = (int)Math.floor(x * scale);
        int iy = (int)Math.floor(y * scale);
        double fx = x * scale - (double)ix;
        double fy = y * scale - (double)iy;
        double v00 = WelcomeScreenAssets.hash(ix, iy);
        double v10 = WelcomeScreenAssets.hash(ix + 1, iy);
        double v01 = WelcomeScreenAssets.hash(ix, iy + 1);
        double v11 = WelcomeScreenAssets.hash(ix + 1, iy + 1);
        double sx = fx * fx * (3.0 - 2.0 * fx);
        double sy = fy * fy * (3.0 - 2.0 * fy);
        return v00 * (1.0 - sx) * (1.0 - sy) + v10 * sx * (1.0 - sy) + v01 * (1.0 - sx) * sy + v11 * sx * sy;
    }

    private static double hash(int x, int y) {
        int n = x + y * 57;
        n = n << 13 ^ n;
        return (double)(n * (n * n * 15731 + 789221) + 1376312589 & Integer.MAX_VALUE) / 2.147483647E9;
    }

    private static int getTerrainColor(int height, int y) {
        if (height < 32) {
            return WelcomeScreenAssets.lerpColor(1713022, 870305, (double)(32 - height) / 32.0);
        }
        if (height < 48) {
            return WelcomeScreenAssets.lerpColor(870305, 16113331, (double)(48 - height) / 16.0);
        }
        if (height < 64) {
            return WelcomeScreenAssets.lerpColor(16113331, 5025616, (double)(64 - height) / 16.0);
        }
        if (height < 100) {
            return WelcomeScreenAssets.lerpColor(5025616, 3706428, (double)(100 - height) / 36.0);
        }
        if (height < 140) {
            return WelcomeScreenAssets.lerpColor(3706428, 7951688, (double)(140 - height) / 40.0);
        }
        if (height < 180) {
            return WelcomeScreenAssets.lerpColor(7951688, 0x9E9E9E, (double)(180 - height) / 40.0);
        }
        return WelcomeScreenAssets.lerpColor(0x9E9E9E, 0xFFFFFF, Math.min(1.0, (double)(height - 180) / 40.0));
    }

    private static int lerpColor(int c1, int c2, double t) {
        int r1 = c1 >> 16 & 0xFF;
        int g1 = c1 >> 8 & 0xFF;
        int b1 = c1 & 0xFF;
        int r2 = c2 >> 16 & 0xFF;
        int g2 = c2 >> 8 & 0xFF;
        int b2 = c2 & 0xFF;
        int r = (int)((double)r1 + (double)(r2 - r1) * t);
        int g = (int)((double)g1 + (double)(g2 - g1) * t);
        int b = (int)((double)b1 + (double)(b2 - b1) * t);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static BufferedImage createBackgroundPattern() {
        int size = 32;
        BufferedImage image = new BufferedImage(size, size, 2);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(new Color(0xFFFFFF, true));
        g2d.drawLine(0, 0, size, size);
        g2d.drawLine(size, 0, 0, size);
        g2d.dispose();
        return image;
    }

    public static void saveTexturesToFiles() {
        Path textureDir = Paths.get("config", "worldscape", "generated");
        try {
            Files.createDirectories(textureDir, new FileAttribute[0]);
            BufferedImage logo = WelcomeScreenAssets.getLogo();
            ImageIO.write((RenderedImage)logo, "PNG", textureDir.resolve("logo.png").toFile());
            BufferedImage preview = WelcomeScreenAssets.getTerrainPreview();
            ImageIO.write((RenderedImage)preview, "PNG", textureDir.resolve("preview.png").toFile());
            Minecraft.getInstance().player.displayClientMessage((Component)Component.literal((String)"[World Scape] Textures saved to config/worldscape/generated/"), false);
        }
        catch (IOException e) {
            Minecraft.getInstance().player.displayClientMessage((Component)Component.literal((String)("[World Scape] Failed to save textures: " + e.getMessage())), false);
        }
    }

    public static void clearCache() {
        textureCache.clear();
        resourceCache.clear();
    }
}

