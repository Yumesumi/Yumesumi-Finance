package com.yumesumi.finance.client.gui;

import com.yumesumi.finance.money.MoneyFormatter;
import com.yumesumi.finance.network.payload.BankSnapshotPayload;
import com.yumesumi.finance.network.payload.DepositRequestPayload;
import com.yumesumi.finance.network.payload.WithdrawRequestPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class BankScreen extends Screen {
    private static final long[] PRESET_AMOUNTS = {100, 500, 1_000, 2_000, 5_000, 10_000, 50_000, 100_000};

    private Page page = Page.MAIN;
    private long balanceCents;
    private String message;
    private EditBox amountBox;

    public BankScreen(BankSnapshotPayload payload) {
        super(Component.translatable("screen.yumesumi_finance.bank"));
        apply(payload);
    }

    public void update(BankSnapshotPayload payload) {
        apply(payload);
        rebuildWidgets();
    }

    @Override
    protected void init() {
        rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawSolidAtmBackground(graphics);

        int left = panelLeft();
        int top = panelTop();
        graphics.fill(left, top, left + 320, top + 220, 0xFF0F172A);
        graphics.fill(left, top, left + 320, top + 4, 0xFF38BDF8);
        graphics.fill(left + 8, top + 34, left + 312, top + 88, 0xFF111827);

        graphics.drawCenteredString(font, title, width / 2, top + 14, 0xFFE0F2FE);
        graphics.drawString(font, Component.translatable("screen.yumesumi_finance.balance"), left + 22, top + 44, 0xFF94A3B8, false);
        graphics.drawString(font, MoneyFormatter.format(balanceCents), left + 106, top + 44, 0xFFF8FAFC, false);
        graphics.drawString(font, Component.literal(message).withStyle(ChatFormatting.AQUA), left + 22, top + 68, 0xFFBAE6FD, false);

        Component pageTitle = switch (page) {
            case MAIN -> Component.translatable("screen.yumesumi_finance.main_menu");
            case DEPOSIT -> Component.translatable("screen.yumesumi_finance.deposit_menu");
            case WITHDRAW -> Component.translatable("screen.yumesumi_finance.withdraw_menu");
        };
        graphics.drawString(font, pageTitle, left + 22, top + 100, 0xFFE2E8F0, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // BankScreen 自己绘制完整不透明背景，禁止原版菜单背景与模糊效果插入。
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
        // 防止“视频设置 -> 菜单背景模糊程度”影响 ATM 界面。
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        amountBox = null;

        addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.exit"), button -> onClose())
                .bounds(8, 8, 54, 20)
                .build());

        if (page == Page.MAIN) {
            buildMainPage();
        } else {
            buildAmountPage();
        }
    }

    private void buildMainPage() {
        int left = panelLeft();
        int top = panelTop();
        addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.deposit"),
                        button -> switchPage(Page.DEPOSIT))
                .bounds(left + 36, top + 138, 112, 24)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.withdraw"),
                        button -> switchPage(Page.WITHDRAW))
                .bounds(left + 172, top + 138, 112, 24)
                .build());
    }

    private void buildAmountPage() {
        int left = panelLeft();
        int top = panelTop();
        int startX = left + 22;
        int startY = top + 122;

        for (int i = 0; i < PRESET_AMOUNTS.length; i++) {
            long amount = PRESET_AMOUNTS[i];
            int x = startX + (i % 4) * 70;
            int y = startY + (i / 4) * 26;
            addRenderableWidget(Button.builder(Component.literal((amount / 100) + "元"), button -> sendAmount(amount))
                    .bounds(x, y, 62, 20)
                    .build());
        }

        amountBox = new EditBox(font, left + 22, top + 176, 116, 20, Component.translatable("screen.yumesumi_finance.amount"));
        amountBox.setHint(Component.translatable("screen.yumesumi_finance.amount_hint"));
        addRenderableWidget(amountBox);

        addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.custom_amount"), button -> sendCustomAmount())
                .bounds(left + 146, top + 176, 82, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.back"), button -> switchPage(Page.MAIN))
                .bounds(left + 236, top + 176, 62, 20)
                .build());
    }

    private void sendAmount(long amountCents) {
        if (page == Page.DEPOSIT) {
            PacketDistributor.sendToServer(new DepositRequestPayload(amountCents));
        } else if (page == Page.WITHDRAW) {
            PacketDistributor.sendToServer(new WithdrawRequestPayload(amountCents));
        }
    }

    private void sendCustomAmount() {
        try {
            sendAmount(MoneyFormatter.parseCents(amountBox.getValue()));
        } catch (NumberFormatException exception) {
            message = Component.translatable("screen.yumesumi_finance.invalid_amount").getString();
        }
    }

    private void switchPage(Page nextPage) {
        page = nextPage;
        rebuildWidgets();
    }

    private void apply(BankSnapshotPayload payload) {
        balanceCents = payload.balanceCents();
        message = payload.message();
    }

    private void drawSolidAtmBackground(GuiGraphics graphics) {
        // 不调用 Screen#renderBackground，避免游戏菜单背景模糊选项影响 ATM 界面可读性。
        graphics.fill(0, 0, width, height, 0xFF050B14);
        graphics.fill(0, 0, width, 24, 0xFF08111F);
    }

    private int panelLeft() {
        return (width - 320) / 2;
    }

    private int panelTop() {
        return (height - 220) / 2;
    }

    private enum Page {
        MAIN,
        DEPOSIT,
        WITHDRAW
    }
}
