package com.yumesumi.finance.client.gui.foundation;

import com.yumesumi.finance.money.MoneyFormatter;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class FinanceLineChart {
    private FinanceLineChart() {}

    public static void draw(GuiGraphics g, Font font, int x, int y, int w, int h, List<Long> values, int color) {
        g.fill(x + 10, y + h - 18, x + w - 8, y + h - 17, 0xFFFFFFFF);
        g.fill(x + 10, y + 8, x + 11, y + h - 16, 0xFFFFFFFF);
        if (values == null || values.isEmpty()) {
            g.drawCenteredString(font, net.minecraft.network.chat.Component.translatable("screen.yumesumi_finance.no_data"), x + w / 2, y + h / 2 - 4, FinanceTheme.TEXT);
            return;
        }
        long max = values.stream().mapToLong(Long::longValue).max().orElse(1L);
        long min = values.stream().mapToLong(Long::longValue).min().orElse(0L);
        if (max == min) max = min + 1;
        int plotX = x + 16;
        int plotY = y + 8;
        int plotW = w - 28;
        int plotH = h - 30;
        int n = values.size();
        if (n == 1) {
            int py = plotY + plotH / 2;
            drawLine(g, plotX, py, plotX + plotW, py, color);
            g.fill(plotX - 2, py - 2, plotX + 3, py + 3, color);
            g.fill(plotX + plotW - 2, py - 2, plotX + plotW + 3, py + 3, color);
            g.drawString(font, compact(values.get(0)), x + 13, y + 6, FinanceTheme.TEXT, true);
            return;
        }
        int prevX = -1;
        int prevY = -1;
        for (int i = 0; i < n; i++) {
            double p = n == 1 ? 0.0 : (double) i / (n - 1);
            int px = plotX + (int) Math.round(plotW * p);
            double normalized = (double) (values.get(i) - min) / (double) (max - min);
            int py = plotY + plotH - (int) Math.round(plotH * normalized);
            if (prevX >= 0) {
                drawLine(g, prevX, prevY, px, py, color);
            }
            g.fill(px - 2, py - 2, px + 3, py + 3, color);
            prevX = px;
            prevY = py;
        }
        g.drawString(font, compact(max), x + 13, y + 6, FinanceTheme.TEXT, true);
        g.drawString(font, compact(min), x + 13, y + h - 14, FinanceTheme.TEXT, true);
    }

    private static void drawLine(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        if (steps <= 0) return;
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            int x = x1 + (int) Math.round((x2 - x1) * t);
            int y = y1 + (int) Math.round((y2 - y1) * t);
            g.fill(x - 1, y - 1, x + 2, y + 2, color);
        }
    }

    private static String compact(long value) {
        return MoneyFormatter.decimal(value);
    }
}
