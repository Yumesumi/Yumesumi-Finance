package com.yumesumi.finance.client.gui;

import com.yumesumi.finance.bank.enterprise.EnterpriseAccount;
import com.yumesumi.finance.network.payload.PosBindRequestPayload;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;

public class PosMachineConfigScreen extends Screen {
    private static final int PANEL_W = 260;
    private static final int PANEL_H = 126;
    private static final int BG = 0xD0101010;
    private static final int PANEL_BG = 0xE0000000;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int MUTED = 0xFFB8B8B8;

    private final InteractionHand hand;
    private final String initialCode;
    private EditBox codeBox;

    public PosMachineConfigScreen(InteractionHand hand, String enterpriseCode) {
        super(Component.translatable("screen.yumesumi_finance.pos.title"));
        this.hand = hand == null ? InteractionHand.MAIN_HAND : hand;
        this.initialCode = enterpriseCode == null ? "" : enterpriseCode.trim().toUpperCase(Locale.ROOT);
    }

    @Override
    protected void init() {
        int x = (width - PANEL_W) / 2;
        int y = (height - PANEL_H) / 2;
        codeBox = new EditBox(font, x + 38, y + 48, 184, 22, Component.translatable("screen.yumesumi_finance.pos.code"));
        codeBox.setMaxLength(4);
        codeBox.setValue(initialCode);
        codeBox.setHint(Component.translatable("screen.yumesumi_finance.pos.code"));
        codeBox.setResponder(value -> {
            String normalized = value.toUpperCase(Locale.ROOT);
            if (!value.equals(normalized)) {
                codeBox.setValue(normalized);
            }
        });
        addRenderableWidget(codeBox);
        if (initialCode.isBlank()) {
            addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.pos.bind"), button -> bind())
                    .bounds(x + 38, y + 84, 84, 20)
                    .build());
        } else {
            addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.pos.unbind"), button -> unbind())
                    .bounds(x + 38, y + 84, 84, 20)
                    .build());
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.back"), button -> onClose())
                .bounds(x + 138, y + 84, 84, 20)
                .build());
        setInitialFocus(codeBox);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        int x = (width - PANEL_W) / 2;
        int y = (height - PANEL_H) / 2;
        g.fill(x, y, x + PANEL_W, y + PANEL_H, PANEL_BG);
        g.drawCenteredString(font, title, width / 2, y + 14, WHITE);
        Component status = initialCode.isBlank()
                ? Component.translatable("screen.yumesumi_finance.pos.unbound")
                : Component.translatable("screen.yumesumi_finance.pos.bound", initialCode);
        g.drawCenteredString(font, status, width / 2, y + 30, MUTED);
        super.render(g, mx, my, pt);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, BG);
    }

    private void bind() {
        String code = codeBox == null ? "" : codeBox.getValue().trim().toUpperCase(Locale.ROOT);
        try {
            EnterpriseAccount.normalizeCode(code);
        } catch (IllegalArgumentException exception) {
            return;
        }
        PacketDistributor.sendToServer(new PosBindRequestPayload(hand, code, false));
        onClose();
    }

    private void unbind() {
        PacketDistributor.sendToServer(new PosBindRequestPayload(hand, "", true));
        onClose();
    }
}
