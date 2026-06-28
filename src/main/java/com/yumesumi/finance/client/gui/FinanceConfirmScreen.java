package com.yumesumi.finance.client.gui;

import com.yumesumi.finance.client.gui.foundation.FinanceTheme;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class FinanceConfirmScreen extends Screen {
    private final Consumer<Boolean> callback;
    private final Component message;

    public FinanceConfirmScreen(Consumer<Boolean> callback, Component title, Component message) {
        super(title);
        this.callback = callback;
        this.message = message;
    }

    @Override
    protected void init() {
        int panelW = Math.min(360, Math.max(1, width - 40));
        int panelH = 118;
        int x = (width - panelW) / 2;
        int y = (height - panelH) / 2;
        addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.confirm"), b -> callback.accept(true))
                .bounds(x + panelW / 2 - 78, y + panelH - 34, 72, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.cancel"), b -> callback.accept(false))
                .bounds(x + panelW / 2 + 6, y + panelH - 34, 72, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        int panelW = Math.min(360, Math.max(1, width - 40));
        int panelH = 118;
        int y = (height - panelH) / 2;
        g.drawCenteredString(font, title, width / 2, y + 16, FinanceTheme.GOLD);
        g.drawCenteredString(font, message, width / 2, y + 44, FinanceTheme.TEXT_DIM);
        super.render(g, mx, my, pt);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, FinanceTheme.BACKGROUND);
    }
}
