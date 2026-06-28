package com.yumesumi.finance.client.gui.foundation;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class FinanceRender {
    private FinanceRender() {}

    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
    }

    public static void card(GuiGraphics g, int x, int y, int w, int h) {
    }

    public static void stroke(GuiGraphics g, int x, int y, int w, int h, int color) {
    }

    public static void tab(GuiGraphics g, Font font, Component label, int x, int y, int w, boolean selected) {
        g.drawCenteredString(font, label, x + w / 2, y + 6, selected ? FinanceTheme.GOLD : FinanceTheme.TEXT);
    }

    public static void labelValue(GuiGraphics g, Font font, Component label, String value, int x, int y, int color) {
        g.drawString(font, label, x, y, FinanceTheme.TEXT_MUTED, true);
        g.drawString(font, value, x + Math.max(66, font.width(label) + 8), y, color, true);
    }

    public static String fit(Font font, String text, int maxWidth) {
        if (text == null) return "";
        if (font.width(text) <= maxWidth) return text;
        String s = text;
        while (!s.isEmpty() && font.width(s + "...") > maxWidth) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "...";
    }
}
