/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.ChatFormatting
 *  net.minecraft.client.gui.GuiGraphics
 *  net.minecraft.client.gui.components.Button
 *  net.minecraft.client.gui.components.events.GuiEventListener
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.network.chat.Component
 */
package com.worldscape.config;

import com.worldscape.compat.IncompatibleModWarningScreen;
import com.worldscape.compat.ModCompatibilityChecker;
import com.worldscape.config.WelcomeScreenConfig;
import java.util.Arrays;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class WelcomeScreen
extends Screen {
    private static final int CARD_BG_COLOR = 0x1A1A1A;
    private static final int CARD_BORDER_COLOR = 0x2A2A2A;
    private static final int ACCENT_COLOR = 4890367;
    private static final int TITLE_COLOR = 0xFFFFFF;
    private static final int TEXT_COLOR = 0xCCCCCC;
    private static final int SUBTEXT_COLOR = 0x888888;
    private static final int SUCCESS_COLOR = 5025616;
    private static final int SELECTED_COLOR = 4890367;
    private static final int UNSELECTED_COLOR = 0x3A3A3A;
    private static final int WINDOW_WIDTH = 600;
    private static final int WINDOW_HEIGHT = 460;
    private static final int PADDING = 15;
    private static final int CARD_PADDING = 12;
    private Button continueButton;
    private Button debugModeButton;
    private String terrainScale = WelcomeScreenConfig.getTerrainScale();
    private String riverIntensity = WelcomeScreenConfig.getRiverIntensity();
    private String mountainHeight = WelcomeScreenConfig.getMountainHeight();
    private boolean islandMode = WelcomeScreenConfig.isIslandMode();
    private boolean isDebugMode = WelcomeScreenConfig.isDebugMode();
    private int selectedPresetY = 0;

    public WelcomeScreen() {
        super((Component)Component.literal((String)"World Scape - Welcome"));
    }

    protected void init() {
        ModCompatibilityChecker.checkCompatibility();
        if (ModCompatibilityChecker.hasAnyIssues()) {
            this.minecraft.setScreen((Screen)new IncompatibleModWarningScreen(this));
            return;
        }
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int buttonY = centerY + 230 - 15;
        this.continueButton = Button.builder((Component)Component.literal((String)"Let's Go! / \u5f00\u59cb!"), button -> {
            this.saveSettings();
            this.onClose();
        }).bounds(centerX + 300 - 85, buttonY, 170, 24).build();
        String debugText = this.isDebugMode ? "Debug: ON" : "Debug: OFF";
        this.debugModeButton = Button.builder((Component)Component.literal((String)debugText), button -> {
            this.isDebugMode = !this.isDebugMode;
            WelcomeScreenConfig.setDebugMode(this.isDebugMode);
            button.setMessage((Component)Component.literal((String)(this.isDebugMode ? "Debug: ON" : "Debug: OFF")));
        }).bounds(centerX - 300 + 15, buttonY, 100, 24).build();
        this.addRenderableWidget(this.continueButton);
        this.addRenderableWidget(this.debugModeButton);
    }

    private void saveSettings() {
        WelcomeScreenConfig.setTerrainScale(this.terrainScale);
        WelcomeScreenConfig.setRiverIntensity(this.riverIntensity);
        WelcomeScreenConfig.setMountainHeight(this.mountainHeight);
        WelcomeScreenConfig.setIslandMode(this.islandMode);
        WelcomeScreenConfig.markWelcomeScreenShown();
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int windowX = Math.max(10, centerX - 300);
        int windowY = Math.max(10, centerY - 230);
        this.drawMainWindow(graphics, windowX, windowY);
        int contentY = windowY + 15 + 45;
        this.drawHeader(graphics, windowX + 15, windowY + 15);
        contentY = this.drawIntroCard(graphics, windowX + 15, contentY);
        contentY = this.drawPresetCard(graphics, windowX + 15, contentY, mouseX, mouseY);
        this.drawWorldOptionsCard(graphics, windowX + 15, contentY, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawMainWindow(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 600, y + 460, -871559923);
        graphics.fill(x, y, x + 600, y + 2, 0x2A2A2A);
        graphics.fill(x, y + 460 - 2, x + 600, y + 460, 0x2A2A2A);
        graphics.fill(x, y, x + 2, y + 460, 0x2A2A2A);
        graphics.fill(x + 600 - 2, y, x + 600, y + 460, 0x2A2A2A);
    }

    private void drawHeader(GuiGraphics graphics, int x, int y) {
        graphics.drawString(this.font, (Component)Component.literal((String)"World Scape").withStyle(s -> s.withBold(Boolean.valueOf(true)).withColor(0xFFFFFF)), x, y, 0xFFFFFF);
        graphics.drawString(this.font, (Component)Component.literal((String)"Advanced Terrain Generation / \u9ad8\u7ea7\u5730\u5f62\u751f\u6210").withStyle(ChatFormatting.GRAY), x, y + 18, 0x888888);
        graphics.drawString(this.font, (Component)Component.literal((String)"v1.2.0").withStyle(ChatFormatting.ITALIC).withStyle(ChatFormatting.DARK_GRAY), x + 600 - 15 - 50, y + 8, 0x888888);
        graphics.fill(x, y + 35, x + 600 - 30, y + 36, 0x2A2A2A);
    }

    private int drawIntroCard(GuiGraphics graphics, int x, int y) {
        int cardWidth = 570;
        int cardHeight = 55;
        this.drawCard(graphics, x, y, cardWidth, cardHeight);
        int cardX = x + 12;
        int cardY = y + 12 - 2;
        graphics.drawString(this.font, (Component)Component.literal((String)"Welcome to World Scape!").withStyle(s -> s.withBold(Boolean.valueOf(true)).withColor(4890367)), cardX, cardY, 0xCCCCCC);
        graphics.drawString(this.font, (Component)Component.literal((String)"Customize your terrain below, then create a new world to begin."), cardX, cardY += 16, 0xCCCCCC);
        return y + cardHeight + 12;
    }

    private int drawPresetCard(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        int cardWidth = 570;
        int cardHeight = 140;
        this.drawCard(graphics, x, y, cardWidth, cardHeight);
        int cardX = x + 12;
        int cardY = y + 12 - 2;
        graphics.drawString(this.font, (Component)Component.literal((String)"Terrain Presets / \u5730\u5f62\u9884\u8bbe").withStyle(s -> s.withBold(Boolean.valueOf(true)).withColor(0xCCCCCC)), cardX, cardY, 0xCCCCCC);
        int optionY = cardY += 20;
        this.drawPresetOption(graphics, cardX, optionY, "Vanilla-like", "Standard terrain similar to default Minecraft", this.terrainScale.equals("standard"), mouseX, mouseY, () -> {
            this.terrainScale = "standard";
        });
        this.drawPresetOption(graphics, cardX, optionY += 28, "Large Scale", "Wider valleys and gentler slopes", this.terrainScale.equals("large"), mouseX, mouseY, () -> {
            this.terrainScale = "large";
        });
        this.drawPresetOption(graphics, cardX, optionY += 28, "Dramatic", "Sharper features and deeper valleys", this.terrainScale.equals("dramatic"), mouseX, mouseY, () -> {
            this.terrainScale = "dramatic";
        });
        return y + cardHeight + 12;
    }

    private int drawWorldOptionsCard(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        int cardWidth = 570;
        int cardHeight = 120;
        this.drawCard(graphics, x, y, cardWidth, cardHeight);
        int cardX = x + 12;
        int cardY = y + 12 - 2;
        graphics.drawString(this.font, (Component)Component.literal((String)"World Options / \u4e16\u754c\u9009\u9879").withStyle(s -> s.withBold(Boolean.valueOf(true)).withColor(0xCCCCCC)), cardX, cardY, 0xCCCCCC);
        int optionY = cardY + 20;
        int col1X = cardX;
        int col2X = cardX + cardWidth / 2;
        this.drawToggleOption(graphics, col1X, optionY, "River Intensity", this.riverIntensity, new String[]{"calm", "standard", "deep"}, mouseX, mouseY, () -> {
            String[] levels = new String[]{"calm", "standard", "deep"};
            int idx = Arrays.asList(levels).indexOf(this.riverIntensity);
            this.riverIntensity = levels[(idx + 1) % 3];
        });
        this.drawToggleOption(graphics, col1X, optionY += 28, "Mountain Height", this.mountainHeight, new String[]{"low", "standard", "extreme"}, mouseX, mouseY, () -> {
            String[] levels = new String[]{"low", "standard", "extreme"};
            int idx = Arrays.asList(levels).indexOf(this.mountainHeight);
            this.mountainHeight = levels[(idx + 1) % 3];
        });
        optionY = cardY + 20;
        this.drawIslandOption(graphics, col2X, optionY, "Island Mode", this.islandMode, mouseX, mouseY, () -> {
            this.islandMode = !this.islandMode;
        });
        return y + cardHeight + 12;
    }

    private void drawPresetOption(GuiGraphics graphics, int x, int y, String name, String desc, boolean selected, int mouseX, int mouseY, Runnable onClick) {
        int radioSize = 12;
        int radioX = x;
        int radioY = y + 2;
        int bgColor = selected ? 4890367 : 0x3A3A3A;
        graphics.fill(radioX, radioY, radioX + radioSize, radioY + radioSize, bgColor);
        if (selected) {
            graphics.fill(radioX + 2, radioY + 2, radioX + radioSize - 2, radioY + radioSize - 2, 0xFFFFFF);
        }
        int textX = x + radioSize + 8;
        int textColor = selected ? 0xFFFFFF : 0xCCCCCC;
        graphics.drawString(this.font, (Component)Component.literal((String)name).withStyle(s -> s.withBold(Boolean.valueOf(selected))), textX, y, textColor);
        int descColor = selected ? 0x888888 : 0x666666;
        graphics.drawString(this.font, (Component)Component.literal((String)desc).withStyle(ChatFormatting.GRAY), textX, y + 12, descColor);
        if (mouseX >= x && mouseX <= x + 280 && mouseY >= y && mouseY <= y + 26 && !selected) {
            graphics.fill(x, y, x + 280, y + 26, 0x15FFFFFF);
        }
    }

    private void drawToggleOption(GuiGraphics graphics, int x, int y, String label, String value, String[] options, int mouseX, int mouseY, Runnable onCycle) {
        graphics.drawString(this.font, (Component)Component.literal((String)label).withStyle(s -> s.withColor(0x888888)), x, y + 3, 0xCCCCCC);
        int buttonWidth = 70;
        int buttonX = x + 90;
        graphics.fill(buttonX, y, buttonX + buttonWidth, y + 20, 0x2A2A2A);
        graphics.fill(buttonX, y, buttonX + buttonWidth, y + 1, 0x2A2A2A);
        graphics.fill(buttonX, y + 19, buttonX + buttonWidth, y + 20, 0x2A2A2A);
        graphics.fill(buttonX, y, buttonX + 1, y + 20, 0x2A2A2A);
        graphics.fill(buttonX + buttonWidth - 1, y, buttonX + buttonWidth, y + 20, 0x2A2A2A);
        String displayValue = value.substring(0, 1).toUpperCase() + value.substring(1);
        graphics.drawCenteredString(this.font, (Component)Component.literal((String)displayValue), buttonX + buttonWidth / 2, y + 6, 4890367);
        if (mouseX >= buttonX && mouseX <= buttonX + buttonWidth && mouseY >= y && mouseY <= y + 20) {
            graphics.fill(buttonX, y, buttonX + buttonWidth, y + 20, 0x30FFFFFF);
        }
    }

    private void drawIslandOption(GuiGraphics graphics, int x, int y, String label, boolean selected, int mouseX, int mouseY, Runnable onToggle) {
        graphics.drawString(this.font, (Component)Component.literal((String)label).withStyle(s -> s.withColor(0x888888)), x, y + 3, 0xCCCCCC);
        int toggleWidth = 50;
        int toggleX = x + 90;
        int bgColor = selected ? 5025616 : 0x555555;
        graphics.fill(toggleX, y, toggleX + toggleWidth, y + 20, bgColor);
        graphics.fill(toggleX, y, toggleX + 1, y + 20, 0x666666);
        graphics.fill(toggleX + toggleWidth - 1, y, toggleX + toggleWidth, y + 20, 0x333333);
        graphics.drawCenteredString(this.font, (Component)Component.literal((String)(selected ? "ON" : "OFF")), toggleX + toggleWidth / 2, y + 6, 0xFFFFFF);
        if (mouseX >= toggleX && mouseX <= toggleX + toggleWidth && mouseY >= y && mouseY <= y + 20) {
            graphics.fill(toggleX, y, toggleX + toggleWidth, y + 20, 0x30FFFFFF);
        }
    }

    private void drawCard(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0x1A1A1A);
        graphics.fill(x, y, x + width, y + 1, 0x2A2A2A);
        graphics.fill(x, y + height - 1, x + width, y + height, 0x2A2A2A);
        graphics.fill(x, y, x + 1, y + height, 0x2A2A2A);
        graphics.fill(x + width - 1, y, x + width, y + height, 0x2A2A2A);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int windowX = Math.max(10, centerX - 300);
        int windowY = Math.max(10, centerY - 230);
        int contentX = windowX + 15;
        int contentY = windowY + 15 + 45;
        int presetCardY = contentY = windowY + 15 + 45 + 55 + 12;
        int optionY = presetCardY + 10;
        for (int i = 0; i < 3; ++i) {
            if (mouseX >= (double)contentX && mouseX <= (double)(contentX + 280) && mouseY >= (double)optionY && mouseY <= (double)(optionY + 26)) {
                switch (i) {
                    case 0: {
                        this.terrainScale = "standard";
                        break;
                    }
                    case 1: {
                        this.terrainScale = "large";
                        break;
                    }
                    case 2: {
                        this.terrainScale = "dramatic";
                    }
                }
                return true;
            }
            optionY += 28;
        }
        int optionsCardY = contentY = presetCardY + 140 + 12;
        optionY = optionsCardY + 20;
        int col1X = contentX;
        int col2X = contentX + 285;
        if (mouseX >= (double)(col1X + 90) && mouseX <= (double)(col1X + 160) && mouseY >= (double)optionY && mouseY <= (double)(optionY + 20)) {
            String[] levels = new String[]{"calm", "standard", "deep"};
            int idx = Arrays.asList(levels).indexOf(this.riverIntensity);
            this.riverIntensity = levels[(idx + 1) % 3];
            return true;
        }
        optionY += 28;
        if (mouseX >= (double)(col1X + 90) && mouseX <= (double)(col1X + 160) && mouseY >= (double)optionY && mouseY <= (double)(optionY + 20)) {
            String[] levels = new String[]{"low", "standard", "extreme"};
            int idx = Arrays.asList(levels).indexOf(this.mountainHeight);
            this.mountainHeight = levels[(idx + 1) % 3];
            return true;
        }
        optionY = optionsCardY + 20;
        if (mouseX >= (double)(col2X + 90) && mouseX <= (double)(col2X + 140) && mouseY >= (double)optionY && mouseY <= (double)(optionY + 20)) {
            this.islandMode = !this.islandMode;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    public boolean isPauseScreen() {
        return false;
    }
}

