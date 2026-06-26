package com.yumesumi.finance.client.gui;

import com.yumesumi.finance.money.CashDenomination;
import com.yumesumi.finance.money.CashDenominations;
import com.yumesumi.finance.money.MoneyFormatter;
import com.yumesumi.finance.bank.BankAccessChannel;
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
    private static final int MIN_PANEL_WIDTH = 380;
    private static final int MAX_PANEL_WIDTH = 560;
    private static final int MIN_PANEL_HEIGHT = 250;
    private static final int MAX_PANEL_HEIGHT = 420;
    private static final int PANEL_MARGIN = 16;
    private static final int CONTENT_PADDING = 18;
    private static final int BUTTON_HEIGHT = 20;
    private static final int HISTORY_ROW_HEIGHT = 29;
    private static final DateTimeFormatter HISTORY_TIME_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final int TEXT_PRIMARY = 0xFFFFFFFF;
    private static final int TEXT_SECONDARY = 0xFFE0E0E0;
    private static final int TEXT_MUTED = 0xFFC0C0C0;
    private static final int TEXT_TITLE = 0xFFFFFFA0;
    private static final int TEXT_FAINT = 0x88707070;
    private static final int HISTORY_FILTER_WIDTH = 70;
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("\\d+(\\.\\d{1,2})?");

    private Page page = Page.MAIN;
    private TransactionDirection historyDirection = TransactionDirection.ALL;
    private TransactionSortField historySortField = TransactionSortField.TIME;
    private TransactionSortOrder historySortOrder = TransactionSortOrder.DESC;
    private int historyPage;
    private long balanceCents;
    private Component message = Component.empty();
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
    private BankAccessChannel currentAccessChannel = BankAccessChannel.ATM;
    private long requestCounter = System.nanoTime() & Long.MAX_VALUE;

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
        int panelWidth = panelWidth();
        int panelHeight = panelHeight();

        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xDD000000);
        graphics.fill(left + 8, top + 8, left + panelWidth - 8, top + 34, 0xAA202020);
        graphics.drawCenteredString(font, title, width / 2, top + 16, TEXT_PRIMARY);
        if (page == Page.HISTORY) {
            graphics.drawString(font, pageTitle(), left + CONTENT_PADDING, top + 45, TEXT_TITLE, true);
        } else {
            graphics.drawString(font, Component.translatable("screen.yumesumi_finance.balance"), left + CONTENT_PADDING, top + 45, TEXT_SECONDARY, true);
            graphics.drawString(font, MoneyFormatter.format(balanceCents), left + 92, top + 45, TEXT_PRIMARY, true);
            graphics.drawString(font, page == Page.MAIN ? welcomeMessage() : message, left + CONTENT_PADDING, top + 60, TEXT_SECONDARY, true);
            graphics.drawString(font, pageTitle(), left + CONTENT_PADDING, top + 82, TEXT_TITLE, true);
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
        int buttonWidth = Math.min(170, (contentWidth() - 24) / 2);
        int gap = contentWidth() - buttonWidth * 2;
        int x1 = left + CONTENT_PADDING;
        int x2 = x1 + buttonWidth + gap;
        int y1 = top + Math.max(110, panelHeight() / 2 - 18);
        int y2 = y1 + 36;
        addMenuButton(x1, y1, buttonWidth, "screen.yumesumi_finance.deposit", Page.DEPOSIT);
        addMenuButton(x2, y1, buttonWidth, "screen.yumesumi_finance.withdraw", Page.WITHDRAW);
        addMenuButton(x1, y2, buttonWidth, "screen.yumesumi_finance.transfer", Page.TRANSFER);
        addMenuButton(x2, y2, buttonWidth, "screen.yumesumi_finance.history", Page.HISTORY);
    }

    private void buildCashPage(boolean deposit) {
        int left = panelLeft();
        int top = panelTop();
        int startX = left + CONTENT_PADDING;
        int startY = top + 104;
        int columns = Math.max(3, Math.min(5, contentWidth() / 69));
        int denominationButtonWidth = Math.max(58, Math.min(76, (contentWidth() - (columns - 1) * 5) / columns));

        List<CashDenomination> denominations = CashDenominations.ALL;
        for (int i = 0; i < denominations.size(); i++) {
            CashDenomination denomination = denominations.get(i);
            int x = startX + (i % columns) * (denominationButtonWidth + 5);
            int y = startY + (i / columns) * 24;
            Component label = Component.translatable("item.yumesumi_finance." + denomination.id());
            addRenderableWidget(Button.builder(label, button -> sendCashAmount(deposit, denomination.valueCents()))
                    .bounds(x, y, denominationButtonWidth, BUTTON_HEIGHT)
                    .build());
        }

        int actionY = Math.min(top + panelHeight() - 44, startY + ((denominations.size() + columns - 1) / columns) * 24 + 14);
        int inputWidth = Math.max(90, Math.min(150, contentWidth() - 82 - 82 - 58 - 30));
        int confirmX = left + CONTENT_PADDING + 82 + 10 + inputWidth + 8;
        addRenderableWidget(Button.builder(Component.translatable(deposit ? "screen.yumesumi_finance.deposit_all" : "screen.yumesumi_finance.withdraw_all"),
                        button -> confirmAllCash(deposit))
                .bounds(left + CONTENT_PADDING, actionY, 82, BUTTON_HEIGHT)
                .build());

        amountBox = new EditBox(font, left + CONTENT_PADDING + 92, actionY, inputWidth, BUTTON_HEIGHT, Component.translatable("screen.yumesumi_finance.amount"));
        amountBox.setHint(Component.translatable("screen.yumesumi_finance.custom_amount_hint"));
        addRenderableWidget(amountBox);

        addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.confirm"), button -> sendCustomAmount(deposit))
                .bounds(confirmX, actionY, 82, BUTTON_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.back"), button -> switchPage(Page.MAIN))
                .bounds(left + panelWidth() - CONTENT_PADDING - 58, actionY, 58, BUTTON_HEIGHT)
                .build());
    }

    private void buildTransferPage() {
        int left = panelLeft();
        int top = panelTop();
        int fieldX = left + CONTENT_PADDING + 56;
        int fieldWidth = Math.min(300, contentWidth() - 74);
        targetBox = new EditBox(font, fieldX, top + 104, fieldWidth, BUTTON_HEIGHT, Component.translatable("screen.yumesumi_finance.transfer_target"));
        targetBox.setHint(Component.translatable("screen.yumesumi_finance.transfer_target_hint"));
        targetBox.setMaxLength(64);
        targetBox.setValue(transferTargetText);
        targetBox.setResponder(value -> transferTargetText = value);
        addRenderableWidget(targetBox);

        amountBox = new EditBox(font, fieldX, top + 136, fieldWidth, BUTTON_HEIGHT, Component.translatable("screen.yumesumi_finance.amount"));
        amountBox.setHint(Component.translatable("screen.yumesumi_finance.custom_amount_hint"));
        amountBox.setValue(transferAmountText);
        amountBox.setResponder(value -> transferAmountText = value);
        addRenderableWidget(amountBox);

        noteBox = new EditBox(font, fieldX, top + 168, fieldWidth, BUTTON_HEIGHT, Component.translatable("screen.yumesumi_finance.transfer_note"));
        noteBox.setHint(Component.translatable("screen.yumesumi_finance.transfer_note_hint"));
        noteBox.setMaxLength(64);
        noteBox.setValue(transferNoteText);
        noteBox.setResponder(value -> transferNoteText = value);
        addRenderableWidget(noteBox);

        addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.transfer_submit"), button -> confirmTransfer())
                .bounds(left + panelWidth() - CONTENT_PADDING - 154, top + Math.min(200, panelHeight() - 50), 88, BUTTON_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.back"), button -> switchPage(Page.MAIN))
                .bounds(left + panelWidth() - CONTENT_PADDING - 58, top + Math.min(200, panelHeight() - 50), 58, BUTTON_HEIGHT)
                .build());
    }

    private void buildHistoryPage() {
        int left = panelLeft();
        int top = panelTop();
        int controlY = top + 68;
        int searchWidth = Math.max(90, contentWidth() - HISTORY_FILTER_WIDTH * 3 - 24);
        addRenderableWidget(Button.builder(directionLabel(), button -> {
                    historyDirection = switch (historyDirection) {
                        case ALL -> TransactionDirection.INCOME;
                        case INCOME -> TransactionDirection.EXPENSE;
                        case EXPENSE -> TransactionDirection.ALL;
                    };
                    historyPage = 0;
                    rebuildWidgets();
                })
                .bounds(left + CONTENT_PADDING, controlY, HISTORY_FILTER_WIDTH, BUTTON_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(sortLabel(), button -> {
                    historySortField = historySortField == TransactionSortField.TIME ? TransactionSortField.AMOUNT : TransactionSortField.TIME;
                    historyPage = 0;
                    rebuildWidgets();
                })
                .bounds(left + CONTENT_PADDING + 76, controlY, HISTORY_FILTER_WIDTH, BUTTON_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(orderLabel(), button -> {
                    historySortOrder = historySortOrder == TransactionSortOrder.DESC ? TransactionSortOrder.ASC : TransactionSortOrder.DESC;
                    historyPage = 0;
                    rebuildWidgets();
                })
                .bounds(left + CONTENT_PADDING + 152, controlY, HISTORY_FILTER_WIDTH, BUTTON_HEIGHT)
                .build());

        searchBox = new EditBox(font, left + panelWidth() - CONTENT_PADDING - searchWidth, controlY, searchWidth, BUTTON_HEIGHT, Component.translatable("screen.yumesumi_finance.history_search"));
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
                .bounds(left + CONTENT_PADDING, historyFooterY(), 24, BUTTON_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> {
                    historyPage++;
                    clampHistoryPage();
                    rebuildWidgets();
                })
                .bounds(left + CONTENT_PADDING + 30, historyFooterY(), 24, BUTTON_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.back"), button -> switchPage(Page.MAIN))
                .bounds(left + panelWidth() - CONTENT_PADDING - 58, historyFooterY(), 58, BUTTON_HEIGHT)
                .build());
    }

    private void renderHistoryRows(GuiGraphics graphics, int left, int top) {
        List<BankTransactionView> rows = filteredTransactions();
        int rowsPerPage = historyRowsPerPage();
        int from = Math.min(historyPage * rowsPerPage, rows.size());
        int to = Math.min(from + rowsPerPage, rows.size());
        int tableLeft = left + CONTENT_PADDING;
        int tableTop = historyTableTop();
        int tableRight = left + panelWidth() - CONTENT_PADDING;
        int tableBottom = historyTableBottom();
        graphics.fill(tableLeft, tableTop, tableRight, tableBottom, 0x66000000);

        if (rows.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("screen.yumesumi_finance.history_empty"), left + panelWidth() / 2, (tableTop + tableBottom) / 2, TEXT_MUTED);
        }

        for (int i = from; i < to; i++) {
            BankTransactionView view = rows.get(i);
            int row = i - from;
            int y = tableTop + 5 + row * HISTORY_ROW_HEIGHT;
            int color = view.type().income() ? 0xFF80FF80 : 0xFFFF8080;
            String sign = view.type().income() ? "+" : "-";
            int typeX = historyTypeX(left);
            int amountX = historyAmountX(left);
            int noteX = historyNoteX(left);
            int noteWidth = tableRight - noteX - 4;
            Component type = Component.translatable("transaction.yumesumi_finance." + view.type().name().toLowerCase(Locale.ROOT));
            graphics.drawString(font, type, typeX, y, TEXT_PRIMARY, true);
            graphics.drawString(font, sign + MoneyFormatter.format(view.amountCents()), amountX, y, color, true);
            String note = visibleNote(view.note());
            if (!note.isBlank()) {
                graphics.drawString(font, fitText(note, noteWidth), noteX, y, TEXT_MUTED, true);
            }
            graphics.drawString(font, formatHistoryTime(view.createdTime()), typeX, y + 10, TEXT_MUTED, true);
            graphics.drawString(font, Component.translatable("screen.yumesumi_finance.history_balance_after", MoneyFormatter.format(view.balanceAfterCents())), amountX, y + 10, TEXT_SECONDARY, true);
            if (!view.counterpartyName().isBlank()) {
                drawCounterparty(graphics, view, noteX, y + 10, noteWidth);
            }
            if (i + 1 < to) {
                graphics.fill(tableLeft + 6, y + 26, tableRight - 6, y + 27, 0x22FFFFFF);
            }
        }

        int totalPages = Math.max(1, (rows.size() + rowsPerPage - 1) / rowsPerPage);
        graphics.drawCenteredString(font, Component.translatable("screen.yumesumi_finance.history_limit_hint"), left + panelWidth() / 2, tableBottom - 6, TEXT_FAINT);
        graphics.drawString(font, Component.literal((historyPage + 1) + "/" + totalPages + "  " + rows.size() + "/1000"), left + 82, historyFooterY() + 6, TEXT_MUTED, true);
    }

    private void renderHistoryHover(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        List<BankTransactionView> rows = filteredTransactions();
        int rowsPerPage = historyRowsPerPage();
        int from = Math.min(historyPage * rowsPerPage, rows.size());
        int to = Math.min(from + rowsPerPage, rows.size());
        for (int i = from; i < to; i++) {
            BankTransactionView view = rows.get(i);
            if (view.counterpartyId() == null || view.counterpartyName().isBlank()) {
                continue;
            }

            int y = historyTableTop() + 5 + (i - from) * HISTORY_ROW_HEIGHT + 10;
            int noteX = historyNoteX(left);
            int noteWidth = left + panelWidth() - CONTENT_PADDING - noteX - 4;
            int nameX = counterpartyNameX(view, noteX);
            int width = font.width(view.counterpartyName());
            if (mouseX >= nameX && mouseX <= nameX + width && mouseY >= y && mouseY <= y + 9) {
                graphics.renderTooltip(font, Component.literal(view.counterpartyId().toString()), mouseX, mouseY);
                return;
            }

            String note = visibleNote(view.note());
            if (!note.isBlank()) {
                String noteText = fitText(note, noteWidth);
                int noteY = historyTableTop() + 5 + (i - from) * HISTORY_ROW_HEIGHT;
                int renderedNoteWidth = font.width(noteText);
                if (mouseX >= noteX && mouseX <= noteX + renderedNoteWidth && mouseY >= noteY && mouseY <= noteY + 9) {
                    graphics.renderTooltip(font, Component.literal(note), mouseX, mouseY);
                    return;
                }
            }
        }
    }

    private void addMenuButton(int x, int y, int width, String key, Page target) {
        addRenderableWidget(Button.builder(Component.translatable(key), button -> switchPage(target))
                .bounds(x, y, width, 24)
                .build());
    }

    private void sendCashAmount(boolean deposit, long amountCents) {
        if (deposit) {
            PacketDistributor.sendToServer(new DepositRequestPayload(nextRequestId(), currentAccessChannel, amountCents));
        } else {
            PacketDistributor.sendToServer(new WithdrawRequestPayload(nextRequestId(), currentAccessChannel, amountCents));
        }
    }

    private void sendCustomAmount(boolean deposit) {
        try {
            sendCashAmount(deposit, parseValidAmount(amountBox.getValue()));
        } catch (NumberFormatException exception) {
            message = Component.translatable("screen.yumesumi_finance.invalid_amount");
        }
    }

    private void confirmTransfer() {
        String target = targetBox.getValue().trim();
        long amount;
        try {
            amount = parseValidAmount(amountBox.getValue());
        } catch (NumberFormatException exception) {
            message = Component.translatable("screen.yumesumi_finance.invalid_amount");
            return;
        }

        if (target.isEmpty()) {
            message = Component.translatable("screen.yumesumi_finance.invalid_target");
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        String note = noteBox == null ? "" : noteBox.getValue().trim();
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            minecraft.setScreen(this);
            if (confirmed) {
                transferRequestPending = true;
                message = Component.translatable("screen.yumesumi_finance.transfer_loading");
                minecraft.setScreen(new TransferLoadingScreen(this));
                PacketDistributor.sendToServer(new TransferRequestPayload(nextRequestId(), currentAccessChannel, target, amount, note));
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
        currentAccessChannel = payload.channel();
        transactions = payload.transactions();
    }

    private void applyTransferResult(BankSnapshotPayload payload) {
        if (page != Page.TRANSFER || !transferRequestPending) {
            return;
        }

        if (payload.success()) {
            transferTargetText = "";
            transferAmountText = "";
            transferNoteText = "";
        } else if ("message.yumesumi_finance.transfer.account_not_found".equals(payload.messageKey())
                || "message.yumesumi_finance.transfer.target_not_found".equals(payload.messageKey())) {
            transferTargetText = "";
        }
        transferRequestPending = false;
    }

    private void clampHistoryPage() {
        int total = filteredTransactions().size();
        int rowsPerPage = historyRowsPerPage();
        int maxPage = Math.max(0, (total + rowsPerPage - 1) / rowsPerPage - 1);
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
            message = Component.translatable("screen.yumesumi_finance.invalid_amount");
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            minecraft.setScreen(this);
            if (!confirmed) {
                return;
            }
            if (deposit) {
                PacketDistributor.sendToServer(new DepositAllRequestPayload(nextRequestId(), currentAccessChannel));
            } else {
                PacketDistributor.sendToServer(new WithdrawRequestPayload(nextRequestId(), currentAccessChannel, balanceCents));
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

    private void drawCounterparty(GuiGraphics graphics, BankTransactionView view, int x, int y, int maxWidth) {
        Component label = counterpartyLabel(view);
        graphics.drawString(font, label, x, y, TEXT_MUTED, true);
        int nameX = x + font.width(label) + 2;
        graphics.drawString(font, fitText(view.counterpartyName(), Math.max(20, maxWidth - font.width(label) - 2)), nameX, y, TEXT_MUTED, true);
    }

    private int counterpartyNameX(BankTransactionView view, int x) {
        Component label = counterpartyLabel(view);
        return x + font.width(label) + 2;
    }

    private Component counterpartyLabel(BankTransactionView view) {
        return switch (view.type()) {
            case TRANSFER_IN -> Component.translatable("screen.yumesumi_finance.history_from");
            case TRANSFER_OUT -> Component.translatable("screen.yumesumi_finance.history_to");
            case DEPOSIT -> Component.translatable("screen.yumesumi_finance.history_source");
            case WITHDRAW -> Component.translatable("screen.yumesumi_finance.history_destination");
        };
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

    private long nextRequestId() {
        requestCounter++;
        if (requestCounter <= 0) {
            requestCounter = 1;
        }
        return requestCounter;
    }

    private int panelLeft() {
        return (width - panelWidth()) / 2;
    }

    private int panelTop() {
        return (height - panelHeight()) / 2;
    }

    private int panelWidth() {
        int available = width - PANEL_MARGIN * 2;
        if (available < MIN_PANEL_WIDTH) {
            return Math.max(260, available);
        }
        return Math.min(available, MAX_PANEL_WIDTH);
    }

    private int panelHeight() {
        int available = height - PANEL_MARGIN * 2;
        if (available < MIN_PANEL_HEIGHT) {
            return Math.max(210, available);
        }
        return Math.min(available, MAX_PANEL_HEIGHT);
    }

    private int contentWidth() {
        return panelWidth() - CONTENT_PADDING * 2;
    }

    private int historyTableTop() {
        return panelTop() + 94;
    }

    private int historyFooterY() {
        return panelTop() + panelHeight() - 26;
    }

    private int historyTableBottom() {
        return historyFooterY() - 6;
    }

    private int historyRowsPerPage() {
        return Math.max(1, (historyTableBottom() - historyTableTop() - 10) / HISTORY_ROW_HEIGHT);
    }

    private int historyTypeX(int left) {
        return left + CONTENT_PADDING + 6;
    }

    private int historyAmountX(int left) {
        return left + Math.max(112, panelWidth() / 3);
    }

    private int historyNoteX(int left) {
        int preferred = left + Math.max(238, panelWidth() * 5 / 8);
        int latestReadable = left + panelWidth() - CONTENT_PADDING - 80;
        return Math.min(preferred, latestReadable);
    }

    private enum Page {
        MAIN,
        DEPOSIT,
        WITHDRAW,
        TRANSFER,
        HISTORY
    }

    public static final class TransferLoadingScreen extends Screen {
        private final BankScreen parent;

        private TransferLoadingScreen(BankScreen parent) {
            super(Component.translatable("screen.yumesumi_finance.transfer_loading"));
            this.parent = parent;
        }

        public BankScreen parent() {
            return parent;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderBackground(graphics, mouseX, mouseY, partialTick);
            graphics.drawCenteredString(font, title, width / 2, height / 2 - 8, TEXT_PRIMARY);
            super.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xC0202020);
        }
    }
}
