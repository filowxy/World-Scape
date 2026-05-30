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
package com.worldscape.compat;

import com.worldscape.compat.ModCompatibilityChecker;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class IncompatibleModWarningScreen
extends Screen {
    private static final int WARNING_COLOR = 16750592;
    private static final int DANGER_COLOR = 16007990;
    private static final int TEXT_COLOR = 0xCCCCCC;
    private static final int SUBTEXT_COLOR = 0x888888;
    private static final int CARD_BG_COLOR = 0x1A1A1A;
    private static final int CARD_BORDER_COLOR = 0x2A2A2A;
    private static final int WINDOW_WIDTH = 500;
    private static final int WINDOW_HEIGHT = 380;
    private final Screen parentScreen;

    public IncompatibleModWarningScreen(Screen parent) {
        super((Component)Component.literal((String)"Compatibility Warning"));
        this.parentScreen = parent;
        ModCompatibilityChecker.checkCompatibility();
    }

    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        Button continueButton = Button.builder((Component)Component.literal((String)"Continue Anyway / \u7ee7\u7eed\u4f7f\u7528"), button -> this.onClose()).bounds(centerX - 150, centerY + 190 - 30, 140, 24).build();
        Button backButton = Button.builder((Component)Component.literal((String)"Back / \u8fd4\u56de"), button -> this.minecraft.setScreen(this.parentScreen)).bounds(centerX + 10, centerY + 190 - 30, 140, 24).build();
        this.addRenderableWidget(continueButton);
        this.addRenderableWidget(backButton);
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        List<String> conflicts;
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int windowX = Math.max(10, centerX - 250);
        int windowY = Math.max(10, centerY - 190);
        this.drawMainWindow(graphics, windowX, windowY);
        int contentX = windowX + 20;
        int contentY = windowY + 20;
        graphics.drawString(this.font, (Component)Component.literal((String)"Compatibility Warning / \u517c\u5bb9\u6027\u8b66\u544a").withStyle(s -> s.withBold(Boolean.valueOf(true)).withColor(16750592)), contentX, contentY, 0xCCCCCC);
        graphics.fill(contentX, contentY += 25, contentX + 500 - 40, contentY + 1, 0x2A2A2A);
        graphics.drawString(this.font, (Component)Component.literal((String)"Warning: Incompatible or conflicting mods detected!").withStyle(ChatFormatting.RED), contentX, contentY += 15, 0xCCCCCC);
        graphics.drawString(this.font, (Component)Component.literal((String)"Using World Scape with these mods may cause issues:"), contentX, contentY += 18, 0x888888);
        contentY += 14;
        List<String> incompatible = ModCompatibilityChecker.getIncompatibleMods();
        if (!incompatible.isEmpty()) {
            graphics.drawString(this.font, (Component)Component.literal((String)"Incompatible / \u4e0d\u517c\u5bb9:").withStyle(ChatFormatting.GOLD), contentX, contentY, 0xCCCCCC);
            contentY += 14;
            for (String mod : incompatible) {
                graphics.drawString(this.font, (Component)Component.literal((String)("  - " + ModCompatibilityChecker.formatModName(mod))).withStyle(ChatFormatting.RED), contentX, contentY, 0xCCCCCC);
                contentY += 14;
            }
            contentY += 10;
        }
        if (!(conflicts = ModCompatibilityChecker.getConflictMods()).isEmpty()) {
            graphics.drawString(this.font, (Component)Component.literal((String)"Conflicting / \u53ef\u80fd\u51b2\u7a81:").withStyle(ChatFormatting.GOLD), contentX, contentY, 0xCCCCCC);
            contentY += 14;
            for (String mod : conflicts) {
                graphics.drawString(this.font, (Component)Component.literal((String)("  - " + ModCompatibilityChecker.formatModName(mod))).withStyle(ChatFormatting.GOLD), contentX, contentY, 0xCCCCCC);
                contentY += 14;
            }
            contentY += 10;
        }
        graphics.fill(contentX, contentY, contentX + 500 - 40, contentY + 1, 0x2A2A2A);
        graphics.drawString(this.font, (Component)Component.literal((String)"Recommendations / \u5efa\u8bae:"), contentX, contentY += 15, 0x888888);
        graphics.drawString(this.font, (Component)Component.literal((String)"\u2022 Remove incompatible mods before using World Scape"), contentX, contentY += 14, 0xCCCCCC);
        graphics.drawString(this.font, (Component)Component.literal((String)"\u2022 Backup your worlds before creating new ones"), contentX, contentY += 12, 0xCCCCCC);
        graphics.drawString(this.font, (Component)Component.literal((String)"\u2022 Report issues on the mod's issue tracker"), contentX, contentY += 12, 0xCCCCCC);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawMainWindow(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 500, y + 380, -870704614);
        graphics.fill(x, y, x + 500, y + 2, 16007990);
        graphics.fill(x, y + 380 - 2, x + 500, y + 380, 16007990);
        graphics.fill(x, y, x + 2, y + 380, 16007990);
        graphics.fill(x + 500 - 2, y, x + 500, y + 380, 16007990);
    }

    public boolean isPauseScreen() {
        return false;
    }
}

