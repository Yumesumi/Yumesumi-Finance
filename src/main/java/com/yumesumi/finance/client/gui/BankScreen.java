package com.yumesumi.finance.client.gui;

import com.yumesumi.finance.money.CashDenomination;
import com.yumesumi.finance.money.CashDenominations;
import com.yumesumi.finance.money.MoneyFormatter;
import com.yumesumi.finance.network.payload.BankSnapshotPayload;
import com.yumesumi.finance.network.payload.BankTransactionView;
import com.yumesumi.finance.network.payload.DepositAllRequestPayload;
import com.yumesumi.finance.network.payload.DepositRequestPayload;
import com.yumesumi.finance.network.payload.TransferRequestPayload;
import com.yumesumi.finance.network.payload.WithdrawRequestPayload;
import com.yumesumi.finance.transaction.TransactionDirection;
import com.yumesumi.finance.transaction.TransactionSortField;
import com.yumesumi.finance.transaction.TransactionSortOrder;
import com.yumesumi.finance.transaction.TransactionType;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class BankScreen extends Screen {
    private static final int PANEL_WIDTH = 380;
    private static final int PANEL_HEIGHT = 250;
    private static final int HISTORY_PAGE_SIZE = 4;
    private static final DateTimeFormatter HISTORY_TIME_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final int TEXT_PRIMARY = 0xFFFFFFFF;
    private static final int TEXT_SECONDARY = 0xFFE0E0E0;
    private static final int TEXT_MUTED = 0xFFC0C0C0;
    private static final int TEXT_TITLE = 0xFFFFFFA0;
    private static final int TEXT_FAINT = 0x88707070;
    private static final int HISTORY_COL_TYPE = 24;
    private static final int HISTORY_COL_AMOUNT = 116;
    private static final int HISTORY_COL_NOTE = 238;
    private static final int HISTORY_FILTER_WIDTH = 70;
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("\\d+(\\.\\d{1,2})?");

    private Page page = Page.MAIN;
    private TransactionDirection historyDirection = TransactionDirection.ALL;
    private TransactionSortField historySortField = TransactionSortField.TIME;
    private TransactionSortOrder historySortOrder = TransactionSortOrder.DESC;
    private int historyPage;
    private long balanceCents;
    private String message;
    private List<BankTransactionView> transactions = List.of();
    private EditBox amountBox;
    private EditBox targetBox;
    private EditBox noteBox;
    private EditBox searchBox;
    private String historySearch = "";
    private String transferTargetText = "";
    private String transferAmountText = "";
    private String transferNoteText = "";
    private boolean transferRequestPending;

    public BankScreen(BankSnapshotPayload payload) {
        super(Component.translatable("screen.yumesumi_finance.bank"));
        apply(payload);
    }

    public void update(BankSnapshotPayload payload) {
        syncTransferDraft();
        apply(payload);
        applyTransferResult(payload);
        clampHistoryPage();
        rebuildWidgets();
    }

    @Override
    protected void init() {
        rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawVanillaLikeBackground(graphics);
        int left = panelLeft();
        int top = panelTop();

        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xDD000000);
        graphics.fill(left + 8, top + 8, left + PANEL_WIDTH - 8, top + 34, 0xAA202020);
        graphics.drawCenteredString(font, title, width / 2, top + 16, TEXT_PRIMARY);
        if (page == Page.HISTORY) {
            graphics.drawString(font, pageTitle(), left + 18, top + 45, TEXT_TITLE, true);
        } else {
            graphics.drawString(font, Component.translatable("screen.yumesumi_finance.balance"), left + 18, top + 45, TEXT_SECONDARY, true);
            graphics.drawString(font, MoneyFormatter.format(balanceCents), left + 92, top + 45, TEXT_PRIMARY, true);
            graphics.drawString(font, page == Page.MAIN ? welcomeMessage() : Component.literal(message), left + 18, top + 60, TEXT_SECONDARY, true);
            graphics.drawString(font, pageTitle(), left + 18, top + 82, TEXT_TITLE, true);
        }

        if (page == Page.HISTORY) {
            renderHistoryRows(graphics, left, top);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        if (page == Page.HISTORY) {
            renderHistoryHover(graphics, left, top, mouseX, mouseY);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawVanillaLikeBackground(graphics);
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        amountBox = null;
        targetBox = null;
        noteBox = null;
        searchBox = null;

        addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.exit"), button -> onClose())
                .bounds(8, 8, 54, 20)
                .build());

        switch (page) {
            case MAIN -> buildMainPage();
            case DEPOSIT -> buildCashPage(true);
            case WITHDRAW -> buildCashPage(false);
            case TRANSFER -> buildTransferPage();
            case HISTORY -> buildHistoryPage();
        }
    }

    private void buildMainPage() {
        int left = panelLeft();
        int top = panelTop();
        addMenuButton(left + 48, top + 110, "screen.yumesumi_finance.deposit", Page.DEPOSIT);
        addMenuButton(left + 202, top + 110, "screen.yumesumi_finance.withdraw", Page.WITHDRAW);
        addMenuButton(left + 48, top + 146, "screen.yumesumi_finance.transfer", Page.TRANSFER);
        addMenuButton(left + 202, top + 146, "screen.yumesumi_finance.history", Page.HISTORY);
    }

    private void buildCashPage(boolean deposit) {
        int left = panelLeft();
        int top = panelTop();
        int startX = left + 18;
        int startY = top + 104;

        List<CashDenomination> denominations = CashDenominations.ALL;
        for (int i = 0; i < denominations.size(); i++) {
            CashDenomination denomination = denominations.get(i);
            int x = startX + (i % 5) * 69;
            int y = startY + (i / 5) * 24;
            Component label = Component.translatable("item.yumesumi_finance." + denomination.id());
            addRenderableWidget(Button.builder(label, button -> sendCashAmount(deposit, denomination.valueCents()))
                    .bounds(x, y, 64, 20)
                    .build());
        }

        int actionY = top + 184;
        addRenderableWidget(Button.builder(Component.translatable(deposit ? "screen.yumesumi_finance.deposit_all" : "screen.yumesumi_finance.withdraw_all"),
                        button -> confirmAllCash(deposit))
                .bounds(left + 18, actionY, 82, 20)
                .build());

        amountBox = new EditBox(font, left + 110, actionY, 96, 20, Component.translatable("screen.yumesumi_finance.amount"));
        amountBox.setHint(Component.translatable("screen.yumesumi_finance.custom_amount_hint"));
        addRenderableWidget(amountBox);

        addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.confirm"), button -> sendCustomAmount(deposit))
                .bounds(left + 214, actionY, 82, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.back"), button -> switchPage(Page.MAIN))
                .bounds(left + 304, actionY, 58, 20)
                .build());
    }

    private void buildTransferPage() {
        int left = panelLeft();
        int top = panelTop();
        targetBox = new EditBox(font, left + 74, top + 104, 210, 20, Component.translatable("screen.yumesumi_finance.transfer_target"));
        targetBox.setHint(Component.translatable("screen.yumesumi_finance.transfer_target_hint"));
        targetBox.setMaxLength(64);
        targetBox.setValue(transferTargetText);
        targetBox.setResponder(value -> transferTargetText = value);
        addRenderableWidget(targetBox);

        amountBox = new EditBox(font, left + 74, top + 136, 210, 20, Component.translatable("screen.yumesumi_finance.amount"));
        amountBox.setHint(Component.translatable("screen.yumesumi_finance.custom_amount_hint"));
        amountBox.setValue(transferAmountText);
        amountBox.setResponder(value -> transferAmountText = value);
        addRenderableWidget(amountBox);

        noteBox = new EditBox(font, left + 74, top + 168, 210, 20, Component.translatable("screen.yumesumi_finance.transfer_note"));
        noteBox.setHint(Component.translatable("screen.yumesumi_finance.transfer_note_hint"));
        noteBox.setMaxLength(64);
        noteBox.setValue(transferNoteText);
        noteBox.setResponder(value -> transferNoteText = value);
        addRenderableWidget(noteBox);

        addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.transfer_submit"), button -> confirmTransfer())
                .bounds(left + 196, top + 200, 88, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.back"), button -> switchPage(Page.MAIN))
                .bounds(left + 292, top + 200, 58, 20)
                .build());
    }

    private void buildHistoryPage() {
        int left = panelLeft();
        int top = panelTop();
        int controlY = top + 68;
        addRenderableWidget(Button.builder(directionLabel(), button -> {
                    historyDirection = switch (historyDirection) {
                        case ALL -> TransactionDirection.INCOME;
                        case INCOME -> TransactionDirection.EXPENSE;
                        case EXPENSE -> TransactionDirection.ALL;
                    };
                    historyPage = 0;
                    rebuildWidgets();
                })
                .bounds(left + 18, controlY, HISTORY_FILTER_WIDTH, 20)
                .build());
        addRenderableWidget(Button.builder(sortLabel(), button -> {
                    historySortField = historySortField == TransactionSortField.TIME ? TransactionSortField.AMOUNT : TransactionSortField.TIME;
                    historyPage = 0;
                    rebuildWidgets();
                })
                .bounds(left + 94, controlY, HISTORY_FILTER_WIDTH, 20)
                .build());
        addRenderableWidget(Button.builder(orderLabel(), button -> {
                    historySortOrder = historySortOrder == TransactionSortOrder.DESC ? TransactionSortOrder.ASC : TransactionSortOrder.DESC;
                    historyPage = 0;
                    rebuildWidgets();
                })
                .bounds(left + 170, controlY, HISTORY_FILTER_WIDTH, 20)
                .build());

        searchBox = new EditBox(font, left + 246, controlY, 116, 20, Component.translatable("screen.yumesumi_finance.history_search"));
        searchBox.setValue(historySearch);
        searchBox.setResponder(value -> {
            historySearch = value;
            historyPage = 0;
        });
        searchBox.setHint(Component.translatable("screen.yumesumi_finance.history_search"));
        addRenderableWidget(searchBox);

        addRenderableWidget(Button.builder(Component.literal("<"), button -> {
                    historyPage = Math.max(0, historyPage - 1);
                    rebuildWidgets();
                })
                .bounds(left + 18, top + 224, 24, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> {
                    historyPage++;
                    clampHistoryPage();
                    rebuildWidgets();
                })
                .bounds(left + 48, top + 224, 24, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.back"), button -> switchPage(Page.MAIN))
                .bounds(left + 304, top + 224, 58, 20)
                .build());
    }

    private void renderHistoryRows(GuiGraphics graphics, int left, int top) {
        List<BankTransactionView> rows = filteredTransactions();
        int from = Math.min(historyPage * HISTORY_PAGE_SIZE, rows.size());
        int to = Math.min(from + HISTORY_PAGE_SIZE, rows.size());
        graphics.fill(left + 18, top + 94, left + 362, top + 218, 0x66000000);

        if (rows.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("screen.yumesumi_finance.history_empty"), left + PANEL_WIDTH / 2, top + 150, TEXT_MUTED);
        }

        for (int i = from; i < to; i++) {
            BankTransactionView view = rows.get(i);
            int row = i - from;
            int y = top + 99 + row * 29;
            int color = view.type().income() ? 0xFF80FF80 : 0xFFFF8080;
            String sign = view.type().income() ? "+" : "-";
            Component type = Component.translatable("transaction.yumesumi_finance." + view.type().name().toLowerCase(Locale.ROOT));
            graphics.drawString(font, type, left + HISTORY_COL_TYPE, y, TEXT_PRIMARY, true);
            graphics.drawString(font, sign + MoneyFormatter.format(view.amountCents()), left + HISTORY_COL_AMOUNT, y, color, true);
            String note = visibleNote(view.note());
            if (!note.isBlank()) {
                graphics.drawString(font, fitText(note, 116), left + HISTORY_COL_NOTE, y, TEXT_MUTED, true);
            }
            graphics.drawString(font, formatHistoryTime(view.createdTime()), left + HISTORY_COL_TYPE, y + 10, TEXT_MUTED, true);
            graphics.drawString(font, Component.translatable("screen.yumesumi_finance.history_balance_after", MoneyFormatter.format(view.balanceAfterCents())), left + HISTORY_COL_AMOUNT, y + 10, TEXT_SECONDARY, true);
            if (!view.counterpartyName().isBlank()) {
                drawCounterparty(graphics, view, left + HISTORY_COL_NOTE, y + 10);
            }
            if (i + 1 < to) {
                graphics.fill(left + 24, y + 26, left + 356, y + 27, 0x22FFFFFF);
            }
        }

        int totalPages = Math.max(1, (rows.size() + HISTORY_PAGE_SIZE - 1) / HISTORY_PAGE_SIZE);
        graphics.drawCenteredString(font, Component.translatable("screen.yumesumi_finance.history_limit_hint"), left + PANEL_WIDTH / 2, top + 212, TEXT_FAINT);
        graphics.drawString(font, Component.literal((historyPage + 1) + "/" + totalPages + "  " + rows.size() + "/1000"), left + 82, top + 230, TEXT_MUTED, true);
    }

    private void renderHistoryHover(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        List<BankTransactionView> rows = filteredTransactions();
        int from = Math.min(historyPage * HISTORY_PAGE_SIZE, rows.size());
        int to = Math.min(from + HISTORY_PAGE_SIZE, rows.size());
        for (int i = from; i < to; i++) {
            BankTransactionView view = rows.get(i);
            if (view.counterpartyId() == null || view.counterpartyName().isBlank()) {
                continue;
            }

            int y = top + 99 + (i - from) * 29 + 10;
            int nameX = counterpartyNameX(view, left + HISTORY_COL_NOTE);
            int width = font.width(view.counterpartyName());
            if (mouseX >= nameX && mouseX <= nameX + width && mouseY >= y && mouseY <= y + 9) {
                graphics.renderTooltip(font, Component.literal(view.counterpartyId().toString()), mouseX, mouseY);
                return;
            }

            String note = visibleNote(view.note());
            if (!note.isBlank()) {
                String noteText = fitText(note, 116);
                int noteX = left + HISTORY_COL_NOTE;
                int noteY = top + 99 + (i - from) * 29;
                int noteWidth = font.width(noteText);
                if (mouseX >= noteX && mouseX <= noteX + noteWidth && mouseY >= noteY && mouseY <= noteY + 9) {
                    graphics.renderTooltip(font, Component.literal(note), mouseX, mouseY);
                    return;
                }
            }
        }
    }

    private void addMenuButton(int x, int y, String key, Page target) {
        addRenderableWidget(Button.builder(Component.translatable(key), button -> switchPage(target))
                .bounds(x, y, 130, 24)
                .build());
    }

    private void sendCashAmount(boolean deposit, long amountCents) {
        if (deposit) {
            PacketDistributor.sendToServer(new DepositRequestPayload(amountCents));
        } else {
            PacketDistributor.sendToServer(new WithdrawRequestPayload(amountCents));
        }
    }

    private void sendCustomAmount(boolean deposit) {
        try {
            sendCashAmount(deposit, parseValidAmount(amountBox.getValue()));
        } catch (NumberFormatException exception) {
            message = Component.translatable("screen.yumesumi_finance.invalid_amount").getString();
        }
    }

    private void confirmTransfer() {
        String target = targetBox.getValue().trim();
        long amount;
        try {
            amount = parseValidAmount(amountBox.getValue());
        } catch (NumberFormatException exception) {
            message = Component.translatable("screen.yumesumi_finance.invalid_amount").getString();
            return;
        }

        if (target.isEmpty()) {
            message = Component.translatable("screen.yumesumi_finance.invalid_target").getString();
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        String note = noteBox == null ? "" : noteBox.getValue().trim();
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            minecraft.setScreen(this);
            if (confirmed) {
                transferRequestPending = true;
                PacketDistributor.sendToServer(new TransferRequestPayload(target, amount, note));
            }
        }, Component.translatable("screen.yumesumi_finance.transfer_confirm_title"),
                Component.translatable("screen.yumesumi_finance.transfer_confirm", MoneyFormatter.format(amount), target)));
    }

    private List<BankTransactionView> filteredTransactions() {
        String search = historySearch.trim().toLowerCase(Locale.ROOT);
        Comparator<BankTransactionView> comparator = historySortField == TransactionSortField.AMOUNT
                ? Comparator.comparingLong(BankTransactionView::amountCents)
                : Comparator.comparingLong(BankTransactionView::createdTime);
        if (historySortOrder == TransactionSortOrder.DESC) {
            comparator = comparator.reversed();
        }

        return transactions.stream()
                .filter(view -> historyDirection == TransactionDirection.ALL
                        || (historyDirection == TransactionDirection.INCOME && view.type().income())
                        || (historyDirection == TransactionDirection.EXPENSE && !view.type().income()))
                .filter(view -> search.isEmpty() || matchesHistorySearch(view, search))
                .sorted(comparator)
                .limit(1_000)
                .toList();
    }

    private void switchPage(Page nextPage) {
        page = nextPage;
        historyPage = 0;
        rebuildWidgets();
    }

    private void apply(BankSnapshotPayload payload) {
        balanceCents = payload.balanceCents();
        message = payload.message();
        transactions = payload.transactions();
    }

    private void applyTransferResult(BankSnapshotPayload payload) {
        if (page != Page.TRANSFER || !transferRequestPending) {
            return;
        }

        transferAmountText = "";
        if (payload.success()) {
            transferTargetText = "";
            transferNoteText = "";
        }
        transferRequestPending = false;
    }

    private void clampHistoryPage() {
        int total = filteredTransactions().size();
        int maxPage = Math.max(0, (total + HISTORY_PAGE_SIZE - 1) / HISTORY_PAGE_SIZE - 1);
        historyPage = Math.min(historyPage, maxPage);
    }

    private Component pageTitle() {
        return switch (page) {
            case MAIN -> Component.translatable("screen.yumesumi_finance.main_menu");
            case DEPOSIT -> Component.translatable("screen.yumesumi_finance.deposit_menu");
            case WITHDRAW -> Component.translatable("screen.yumesumi_finance.withdraw_menu");
            case TRANSFER -> Component.translatable("screen.yumesumi_finance.transfer_menu");
            case HISTORY -> Component.translatable("screen.yumesumi_finance.history_menu");
        };
    }

    private Component directionLabel() {
        return Component.translatable("screen.yumesumi_finance.history_direction." + historyDirection.name().toLowerCase(Locale.ROOT));
    }

    private Component sortLabel() {
        return Component.translatable("screen.yumesumi_finance.history_sort." + historySortField.name().toLowerCase(Locale.ROOT));
    }

    private Component orderLabel() {
        return Component.translatable("screen.yumesumi_finance.history_order." + historySortOrder.name().toLowerCase(Locale.ROOT));
    }

    private Component welcomeMessage() {
        Minecraft minecraft = Minecraft.getInstance();
        String playerName = minecraft.player == null ? "" : minecraft.player.getGameProfile().getName();
        return Component.translatable("screen.yumesumi_finance.welcome_player", playerName);
    }

    private String formatHistoryTime(long createdTime) {
        return HISTORY_TIME_FORMAT.format(Instant.ofEpochMilli(createdTime));
    }

    private void confirmAllCash(boolean deposit) {
        if (!deposit && balanceCents <= 0) {
            message = Component.translatable("screen.yumesumi_finance.invalid_amount").getString();
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            minecraft.setScreen(this);
            if (!confirmed) {
                return;
            }
            if (deposit) {
                PacketDistributor.sendToServer(new DepositAllRequestPayload());
            } else {
                PacketDistributor.sendToServer(new WithdrawRequestPayload(balanceCents));
            }
        }, Component.translatable(deposit ? "screen.yumesumi_finance.deposit_all_confirm_title" : "screen.yumesumi_finance.withdraw_all_confirm_title"),
                Component.translatable(deposit ? "screen.yumesumi_finance.deposit_all_confirm" : "screen.yumesumi_finance.withdraw_all_confirm", MoneyFormatter.format(balanceCents))));
    }

    private long parseValidAmount(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (!AMOUNT_PATTERN.matcher(text).matches()) {
            throw new NumberFormatException("Invalid amount");
        }

        long amount = MoneyFormatter.parseCents(text);
        if (amount <= 0) {
            throw new NumberFormatException("Amount must be positive");
        }
        return amount;
    }

    private void syncTransferDraft() {
        if (targetBox != null) {
            transferTargetText = targetBox.getValue();
        }
        if (amountBox != null && page == Page.TRANSFER) {
            transferAmountText = amountBox.getValue();
        }
        if (noteBox != null) {
            transferNoteText = noteBox.getValue();
        }
    }

    private void drawCounterparty(GuiGraphics graphics, BankTransactionView view, int x, int y) {
        Component label = Component.translatable(view.type() == TransactionType.TRANSFER_IN
                ? "screen.yumesumi_finance.history_from"
                : "screen.yumesumi_finance.history_to");
        graphics.drawString(font, label, x, y, TEXT_MUTED, true);
        graphics.drawString(font, view.counterpartyName(), x + font.width(label) + 2, y, TEXT_MUTED, true);
    }

    private int counterpartyNameX(BankTransactionView view, int x) {
        Component label = Component.translatable(view.type() == TransactionType.TRANSFER_IN
                ? "screen.yumesumi_finance.history_from"
                : "screen.yumesumi_finance.history_to");
        return x + font.width(label) + 2;
    }

    private boolean matchesHistorySearch(BankTransactionView view, String search) {
        String counterpartyId = view.counterpartyId() == null ? "" : view.counterpartyId().toString();
        return view.counterpartyName().toLowerCase(Locale.ROOT).contains(search)
                || counterpartyId.toLowerCase(Locale.ROOT).contains(search)
                || visibleNote(view.note()).toLowerCase(Locale.ROOT).contains(search)
                || MoneyFormatter.format(view.amountCents()).toLowerCase(Locale.ROOT).contains(search)
                || Long.toString(view.amountCents()).contains(search)
                || amountAsDecimal(view.amountCents()).contains(search);
    }

    private String visibleNote(String note) {
        if (note == null || note.startsWith("message.")) {
            return "";
        }
        return note.trim();
    }

    private String amountAsDecimal(long cents) {
        long yuan = cents / 100;
        long remain = Math.abs(cents % 100);
        return yuan + "." + (remain < 10 ? "0" : "") + remain;
    }

    private String fitText(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }

        String suffix = "...";
        String result = text;
        while (!result.isEmpty() && font.width(result + suffix) > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + suffix;
    }

    private void drawVanillaLikeBackground(GuiGraphics graphics) {
        graphics.fill(0, 0, width, height, 0xC0202020);
    }

    private int panelLeft() {
        return (width - PANEL_WIDTH) / 2;
    }

    private int panelTop() {
        return (height - PANEL_HEIGHT) / 2;
    }

    private enum Page {
        MAIN,
        DEPOSIT,
        WITHDRAW,
        TRANSFER,
        HISTORY
    }
}
