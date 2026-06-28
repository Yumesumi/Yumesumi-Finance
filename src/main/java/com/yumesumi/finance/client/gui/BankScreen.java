package com.yumesumi.finance.client.gui;

import com.yumesumi.finance.bank.BankAccessChannel;
import com.yumesumi.finance.bank.enterprise.EnterpriseAction;
import com.yumesumi.finance.bank.enterprise.EnterpriseRole;
import com.yumesumi.finance.client.gui.foundation.FinanceRender;
import com.yumesumi.finance.client.gui.foundation.FinanceTheme;
import com.yumesumi.finance.money.CashDenomination;
import com.yumesumi.finance.money.CashDenominations;
import com.yumesumi.finance.money.MoneyFormatter;
import com.yumesumi.finance.network.payload.BankSnapshotPayload;
import com.yumesumi.finance.network.payload.BankTransactionView;
import com.yumesumi.finance.network.payload.DepositAllRequestPayload;
import com.yumesumi.finance.network.payload.DepositRequestPayload;
import com.yumesumi.finance.network.payload.EnterpriseRequestPayload;
import com.yumesumi.finance.network.payload.EnterpriseSummaryView;
import com.yumesumi.finance.network.payload.InboxMessageView;
import com.yumesumi.finance.network.payload.TransferRequestPayload;
import com.yumesumi.finance.network.payload.WithdrawRequestPayload;
import com.yumesumi.finance.transaction.TransactionDirection;
import com.yumesumi.finance.transaction.TransactionSortField;
import com.yumesumi.finance.transaction.TransactionSortOrder;
import com.yumesumi.finance.transaction.TransactionType;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public class BankScreen extends Screen {
    private enum Page { HOME, DEPOSIT, WITHDRAW, TRANSFER, HISTORY, INBOX }
    private enum InboxFilter { ALL, UNREAD, STARRED, INVITE }
    private enum InboxSort { TIME, TITLE }

    private record ActionButton(int x, int y, int w, int h, Component label, Runnable action, boolean active) {}

    private static final int SCREEN_BG = 0xD0101010;
    private static final int PANEL_BG = 0xD8000000;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int MUTED = 0xFFB8B8B8;
    private static final int GOLD = 0xFFFFD000;
    private static final int GREEN = 0xFF00FF38;
    private static final int RED = 0xFFFF2020;
    private static final int LINE = 0xFFFFFFFF;
    private static final int NARROW_W = 312;
    private static final int NARROW_H = 304;
    private static final int HISTORY_ROW_H = 54;
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("\\d+(\\.\\d{1,2})?");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter NOW_FMT = DateTimeFormatter.ofPattern("yyyy年MM月dd日HH点mm分");

    private final List<ActionButton> actionButtons = new ArrayList<>();
    private Page page = Page.HOME;
    private long balanceCents;
    private long cashBalanceCents;
    private Component message = Component.empty();
    private String messageKey = "";
    private boolean success;
    private Page messagePage;
    private boolean accountFrozen;
    private boolean transferEnterpriseTarget;
    private BankAccessChannel currentAccessChannel = BankAccessChannel.ATM;
    private List<BankTransactionView> transactions = List.of();
    private List<InboxMessageView> inbox = List.of();
    private List<EnterpriseSummaryView> enterprises = List.of();
    private EditBox amountBox;
    private EditBox targetBox;
    private EditBox noteBox;
    private EditBox searchBox;
    private String transferTargetText = "";
    private String transferAmountText = "";
    private String transferNoteText = "";
    private String customAmountText = "";
    private String historySearch = "";
    private TransactionDirection historyDirection = TransactionDirection.ALL;
    private TransactionSortField historySortField = TransactionSortField.TIME;
    private TransactionSortOrder historySortOrder = TransactionSortOrder.DESC;
    private String inboxSearch = "";
    private InboxFilter inboxFilter = InboxFilter.ALL;
    private InboxSort inboxSort = InboxSort.TIME;
    private TransactionSortOrder inboxSortOrder = TransactionSortOrder.DESC;
    private int historyPage;
    private int inboxPage;
    private final Set<UUID> selectedInbox = new HashSet<>();
    private boolean transferRequestPending;
    private long requestCounter = System.nanoTime() & Long.MAX_VALUE;

    public BankScreen(BankSnapshotPayload payload) {
        super(Component.translatable("screen.yumesumi_finance.personal_bank"));
        apply(payload);
    }

    public void update(BankSnapshotPayload payload) {
        syncDrafts();
        apply(payload);
        applyTransferResult(payload);
        rebuildWidgets();
    }

    @Override
    protected void init() {
        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        actionButtons.clear();
        amountBox = null;
        targetBox = null;
        noteBox = null;
        searchBox = null;
        switch (page) {
            case HOME -> buildHome();
            case DEPOSIT -> buildCashPage(true);
            case WITHDRAW -> buildCashPage(false);
            case TRANSFER -> buildTransferPage();
            case HISTORY -> buildHistoryPage();
            case INBOX -> buildInboxPage();
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        switch (page) {
            case HOME -> renderHome(g, mx, my);
            case DEPOSIT -> renderCashPage(g, true);
            case WITHDRAW -> renderCashPage(g, false);
            case TRANSFER -> renderTransferPage(g, mx, my);
            case HISTORY -> renderHistory(g, mx, my);
            case INBOX -> renderInbox(g, mx, my);
        }
        super.render(g, mx, my, pt);
        renderActionButtons(g, mx, my);
        renderStatus(g);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, SCREEN_BG);
    }

    private void buildHome() {
        int x = narrowLeft();
        int y = narrowTop();
        int buttonW = 102;
        int left = x + 40;
        int right = x + 170;
        int by = y + 152;
        addAction(Component.translatable("screen.yumesumi_finance.transfer"), left, by, buttonW, 22, () -> switchPage(Page.TRANSFER), !accountFrozen);
        addAction(Component.translatable("screen.yumesumi_finance.deposit"), right, by, buttonW, 22, () -> switchPage(Page.DEPOSIT), !accountFrozen);
        addAction(Component.translatable("screen.yumesumi_finance.history"), left, by + 36, buttonW, 22, () -> switchPage(Page.HISTORY), true);
        addAction(Component.translatable("screen.yumesumi_finance.withdraw"), right, by + 36, buttonW, 22, () -> switchPage(Page.WITHDRAW), !accountFrozen);
        addAction(Component.translatable("screen.yumesumi_finance.inbox"), left, by + 72, buttonW, 22, () -> switchPage(Page.INBOX), true);
        addAction(Component.translatable("screen.yumesumi_finance.enterprise.switch"), right, by + 72, buttonW, 22,
                () -> sendEnterprise(EnterpriseRequestPayload.simple(nextId(), EnterpriseAction.OPEN_LIST)), true);
    }

    private void renderHome(GuiGraphics g, int mx, int my) {
        int x = narrowLeft();
        int y = narrowTop();
        drawPanel(g, x, y, NARROW_W, NARROW_H);
        drawTitle(g, "梦澄银行", "个人版", y + 24);
        String playerName = playerName();
        int infoX = x + 40;
        int infoY = y + 72;
        g.drawString(font, "用户：" + playerName, infoX, infoY, WHITE, true);
        renderPlayerTooltip(g, mx, my, infoX + font.width("用户："), infoY, playerName, currentPlayerId());
        g.drawString(font, "现金：" + MoneyFormatter.format(cashBalanceCents), infoX, infoY + 24, WHITE, true);
        g.drawString(font, "银行余额：" + MoneyFormatter.format(balanceCents), infoX, infoY + 48, WHITE, true);
        long unread = inbox.stream().filter(msg -> !msg.read()).count();
        if (unread > 0) {
            g.drawString(font, unread + " 条未读信息", x + 196, infoY, GOLD, true);
        }
        if (accountFrozen) {
            g.drawCenteredString(font, Component.translatable("message.yumesumi_finance.account.frozen"), x + NARROW_W / 2, y + 262, RED);
        }
    }

    private void buildCashPage(boolean deposit) {
        int x = narrowLeft();
        int y = narrowTop();
        int startX = x + 36;
        int startY = y + 104;
        int bw = 72;
        int bh = 22;
        List<CashDenomination> denominations = CashDenominations.ALL;
        for (int i = 0; i < denominations.size(); i++) {
            CashDenomination d = denominations.get(i);
            int col = i % 3;
            int row = i / 3;
            int bx = startX + col * 85;
            int by = startY + row * 33;
            addAction(Component.translatable("item.yumesumi_finance." + d.id()), bx, by, bw, bh, () -> sendCashAmount(deposit, d.valueCents()), !accountFrozen);
        }
        addAction(Component.translatable("screen.yumesumi_finance.back"), startX, y + 267, bw, bh, () -> switchPage(Page.HOME), true);
        amountBox = new EditBox(font, startX + 85, y + 267, bw, bh, Component.empty());
        amountBox.setHint(Component.translatable("screen.yumesumi_finance.custom_amount_hint"));
        amountBox.setValue(customAmountText);
        amountBox.setResponder(v -> customAmountText = v);
        addRenderableWidget(amountBox);
        addAction(Component.translatable("screen.yumesumi_finance.confirm"), startX + 170, y + 267, bw, bh,
                () -> sendCustomAmount(deposit), !accountFrozen);
        addAction(Component.translatable(deposit ? "screen.yumesumi_finance.deposit_all" : "screen.yumesumi_finance.withdraw_all"),
                x + 206, y + 72, 72, bh, () -> confirmAllCash(deposit), !accountFrozen);
    }

    private void renderCashPage(GuiGraphics g, boolean deposit) {
        int x = narrowLeft();
        int y = narrowTop();
        drawPanel(g, x, y, NARROW_W, NARROW_H);
        drawTitle(g, deposit ? "梦澄银行-存款" : "梦澄银行-取款", "个人版", y + 20);
        g.drawString(font, "现金：" + MoneyFormatter.format(cashBalanceCents), x + 36, y + 64, WHITE, true);
        g.drawString(font, "银行余额：" + MoneyFormatter.format(balanceCents), x + 36, y + 82, WHITE, true);
    }

    private void buildTransferPage() {
        int x = narrowLeft();
        int y = narrowTop();
        int formX = x + 90;
        int formY = y + 134;
        addAction(Component.literal(transferEnterpriseTarget ? "企业" : "个人"), x + 40, formY, 40, 22, () -> {
            transferEnterpriseTarget = !transferEnterpriseTarget;
            transferTargetText = "";
            rebuildWidgets();
        }, !accountFrozen);
        targetBox = new EditBox(font, formX, formY, 134, 22, Component.empty());
        targetBox.setHint(Component.literal(transferEnterpriseTarget ? "企业代码" : "玩家名称/UUID"));
        targetBox.setValue(transferTargetText);
        targetBox.setResponder(v -> transferTargetText = v);
        addRenderableWidget(targetBox);
        amountBox = new EditBox(font, formX, formY + 36, 134, 22, Component.empty());
        amountBox.setHint(Component.literal("金额"));
        amountBox.setValue(transferAmountText);
        amountBox.setResponder(v -> transferAmountText = v);
        addRenderableWidget(amountBox);
        noteBox = new EditBox(font, formX, formY + 72, 134, 22, Component.empty());
        noteBox.setHint(Component.literal("备注，可留空"));
        noteBox.setValue(transferNoteText);
        noteBox.setMaxLength(64);
        noteBox.setResponder(v -> transferNoteText = v);
        addRenderableWidget(noteBox);
        addAction(Component.translatable("screen.yumesumi_finance.back"), x + 40, y + 246, 102, 22, () -> switchPage(Page.HOME), true);
        addAction(Component.translatable("screen.yumesumi_finance.confirm"), x + 170, y + 246, 102, 22, this::confirmTransfer, !accountFrozen);
    }

    private void renderTransferPage(GuiGraphics g, int mx, int my) {
        int x = narrowLeft();
        int y = narrowTop();
        drawPanel(g, x, y, NARROW_W, NARROW_H);
        drawTitle(g, "梦澄银行-转账", "个人版", y + 24);
        String playerName = playerName();
        int infoX = x + 40;
        int infoY = y + 72;
        g.drawString(font, "用户：" + playerName, infoX, infoY, WHITE, true);
        renderPlayerTooltip(g, mx, my, infoX + font.width("用户："), infoY, playerName, currentPlayerId());
        g.drawString(font, "银行余额：" + MoneyFormatter.format(balanceCents), infoX, infoY + 24, WHITE, true);
    }

    private void buildHistoryPage() {
        int x = 20;
        int y = 102;
        addAction(directionLabel(), x, y, 64, 22, () -> {
            historyDirection = switch (historyDirection) {
                case ALL -> TransactionDirection.INCOME;
                case INCOME -> TransactionDirection.EXPENSE;
                case EXPENSE -> TransactionDirection.ALL;
            };
            historyPage = 0;
            rebuildWidgets();
        }, true);
        addAction(sortLabel(), x + 74, y, 64, 22, () -> {
            historySortField = historySortField == TransactionSortField.TIME ? TransactionSortField.AMOUNT : TransactionSortField.TIME;
            historyPage = 0;
            rebuildWidgets();
        }, true);
        addAction(orderLabel(), x + 148, y, 64, 22, () -> {
            historySortOrder = historySortOrder == TransactionSortOrder.DESC ? TransactionSortOrder.ASC : TransactionSortOrder.DESC;
            historyPage = 0;
            rebuildWidgets();
        }, true);
        searchBox = new EditBox(font, x + 226, y, Math.min(310, Math.max(160, width - 260)), 22, Component.empty());
        searchBox.setHint(Component.literal("搜索金额/余额/玩家名称/UUID/备注"));
        searchBox.setValue(historySearch);
        searchBox.setResponder(v -> { historySearch = v; historyPage = 0; });
        addRenderableWidget(searchBox);
        addAction(Component.literal("上一页"), 20, height - 42, 64, 22, () -> { historyPage = Math.max(0, historyPage - 1); rebuildWidgets(); }, true);
        addAction(Component.literal("下一页"), 94, height - 42, 64, 22, () -> { historyPage++; clampHistoryPage(); rebuildWidgets(); }, true);
        addAction(Component.translatable("screen.yumesumi_finance.back"), width - 84, height - 42, 64, 22, () -> switchPage(Page.HOME), true);
    }

    private void renderHistory(GuiGraphics g, int mx, int my) {
        g.fill(0, 0, width, height, 0xF0000000);
        g.drawString(font, "交易明细", 20, 26, WHITE, true);
        drawTitle(g, "梦澄银行-查询", "个人版", 26);
        hLine(g, 0, width, 56);
        String playerName = playerName();
        int infoY = 76;
        g.drawString(font, "用户：" + playerName, 20, infoY, WHITE, true);
        renderPlayerTooltip(g, mx, my, 20 + font.width("用户："), infoY, playerName, currentPlayerId());
        g.drawString(font, "银行余额：" + MoneyFormatter.format(balanceCents), 168, infoY, WHITE, true);
        g.drawString(font, "当前时间：" + NOW_FMT.format(LocalDateTime.now()), 350, infoY, WHITE, true);
        hLine(g, 0, width, 136);

        List<BankTransactionView> rows = filteredTransactions();
        int rowsPerPage = Math.max(1, (height - 204) / HISTORY_ROW_H);
        int totalPages = Math.max(1, (rows.size() + rowsPerPage - 1) / rowsPerPage);
        historyPage = Math.min(historyPage, totalPages - 1);
        int from = Math.min(historyPage * rowsPerPage, rows.size());
        int to = Math.min(from + rowsPerPage, rows.size());
        int[] columns = historyColumns();
        for (int col : columns) vLine(g, col, 136, 136 + rowsPerPage * HISTORY_ROW_H);
        for (int i = from; i < to; i++) {
            BankTransactionView view = rows.get(i);
            int rowY = 136 + (i - from) * HISTORY_ROW_H;
            renderHistoryRow(g, view, rowY, columns, mx, my);
            hLine(g, 0, width, rowY + HISTORY_ROW_H);
        }
        if (rows.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("screen.yumesumi_finance.history_empty"), width / 2, 174, MUTED);
        }
        g.drawCenteredString(font, (historyPage + 1) + "/" + totalPages, 86, height - 62, WHITE);
        g.drawCenteredString(font, rows.size() + "/1000", width / 2, height - 62, WHITE);
        g.drawCenteredString(font, "仅显示最近1000条记录", width / 2, height - 38, WHITE);
    }

    private void renderHistoryRow(GuiGraphics g, BankTransactionView view, int y, int[] columns, int mx, int my) {
        boolean income = view.type().income();
        String direction = income ? "收入" : "支出";
        int amountColor = income ? GREEN : RED;
        g.drawString(font, direction, 20, y + 12, WHITE, true);
        g.drawString(font, formatTime(view.createdTime()), 20, y + 32, WHITE, true);
        g.drawString(font, (income ? "+" : "-") + MoneyFormatter.format(view.amountCents()), columns[0] + 20, y + 12, amountColor, true);
        g.drawString(font, "余额 " + MoneyFormatter.format(view.balanceAfterCents()), columns[0] + 20, y + 32, WHITE, true);
        g.drawString(font, transactionSourceName(view), columns[1] + 14, y + 12, WHITE, true);
        String counterpartyPrefix = income ? "来自 " : "向";
        String counterparty = counterpartyText(view);
        g.drawString(font, counterpartyPrefix + counterparty, columns[1] + 14, y + 32, WHITE, true);
        if (view.counterpartyId() != null && !counterparty.isBlank()) {
            renderPlayerTooltip(g, mx, my, columns[1] + 14 + font.width(counterpartyPrefix), y + 32, counterparty, view.counterpartyId());
        }
        String note = visibleNote(view.note());
        String preview = notePreview(note);
        g.drawString(font, "备注：" + preview, columns[2] + 18, y + 12, WHITE, true);
        if (!note.equals(preview) && mx >= columns[2] + 18 && mx <= width - 20 && my >= y + 10 && my <= y + 24) {
            g.renderTooltip(font, Component.literal(note), mx, my);
        }
        g.drawString(font, "操作人：" + (income ? counterparty : playerName()), columns[2] + 18, y + 32, WHITE, true);
    }

    private void buildInboxPage() {
        int y = 92;
        addAction(inboxFilterLabel(), 20, y, 64, 22, this::cycleInboxFilter, true);
        addAction(inboxSortLabel(), 94, y, 64, 22, () -> {
            inboxSort = inboxSort == InboxSort.TIME ? InboxSort.TITLE : InboxSort.TIME;
            inboxPage = 0;
            rebuildWidgets();
        }, true);
        addAction(inboxOrderLabel(), 168, y, 64, 22, () -> {
            inboxSortOrder = inboxSortOrder == TransactionSortOrder.DESC ? TransactionSortOrder.ASC : TransactionSortOrder.DESC;
            inboxPage = 0;
            rebuildWidgets();
        }, true);
        searchBox = new EditBox(font, 246, y, Math.min(280, Math.max(150, width - 280)), 22, Component.empty());
        searchBox.setHint(Component.translatable("screen.yumesumi_finance.search"));
        searchBox.setValue(inboxSearch);
        searchBox.setResponder(v -> { inboxSearch = v; inboxPage = 0; });
        addRenderableWidget(searchBox);
        addAction(Component.literal("上一页"), 20, height - 42, 64, 22, () -> { inboxPage = Math.max(0, inboxPage - 1); rebuildWidgets(); }, true);
        addAction(Component.literal("下一页"), 94, height - 42, 64, 22, () -> { inboxPage++; clampInboxPage(); rebuildWidgets(); }, true);
        addAction(Component.translatable("screen.yumesumi_finance.select_all"), 168, height - 42, 64, 22, this::selectInboxPage, true);
        addAction(Component.translatable("screen.yumesumi_finance.delete"), 242, height - 42, 64, 22, this::confirmDeleteSelectedInbox, true);
        addAction(Component.translatable("screen.yumesumi_finance.back"), width - 84, height - 42, 64, 22, () -> switchPage(Page.HOME), true);
    }

    private void renderInbox(GuiGraphics g, int mx, int my) {
        g.fill(0, 0, width, height, 0xF0000000);
        drawTitle(g, "梦澄银行-收件箱", "个人版", 26);
        hLine(g, 0, width, 56);
        List<InboxMessageView> messages = visibleInbox();
        int tableY = 128;
        int rowH = 44;
        int rows = Math.max(1, (height - 190) / rowH);
        int pages = Math.max(1, (messages.size() + rows - 1) / rows);
        inboxPage = Math.min(inboxPage, pages - 1);
        int from = Math.min(inboxPage * rows, messages.size());
        int to = Math.min(from + rows, messages.size());
        hLine(g, 0, width, tableY);
        vLine(g, 40, tableY, tableY + rows * rowH);
        vLine(g, width - 210, tableY, tableY + rows * rowH);
        for (int i = from; i < to; i++) {
            InboxMessageView msg = messages.get(i);
            int y = tableY + (i - from) * rowH;
            g.drawString(font, selectedInbox.contains(msg.id()) ? "[x]" : "[ ]", 12, y + 16, WHITE, true);
            g.drawString(font, msg.starred() ? "★" : "☆", 48, y + 8, msg.starred() ? GOLD : MUTED, true);
            g.drawString(font, FinanceRender.fit(font, msg.title(), width - 310), 68, y + 8, msg.read() ? MUTED : WHITE, true);
            g.drawString(font, FinanceRender.fit(font, msg.body(), width - 310), 68, y + 24, MUTED, true);
            g.drawString(font, formatTime(msg.createdAt()), width - 198, y + 8, WHITE, true);
            g.drawString(font, "已读", width - 198, y + 26, FinanceTheme.BLUE, true);
            g.drawString(font, "删除", width - 156, y + 26, msg.starred() ? MUTED : RED, true);
            if (msg.relatedEnterpriseId() != null) g.drawString(font, "接受", width - 114, y + 26, GREEN, true);
            hLine(g, 0, width, y + rowH);
        }
        g.drawCenteredString(font, (inboxPage + 1) + "/" + pages + "  " + selectedInbox.size() + "/" + messages.size(), width / 2, height - 62, WHITE);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (ActionButton actionButton : actionButtons) {
                if (actionButton.active() && inside(mx, my, actionButton.x(), actionButton.y(), actionButton.w(), actionButton.h())) {
                    actionButton.action().run();
                    return true;
                }
            }
            if (page == Page.INBOX && handleInboxClick(mx, my)) return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && page != Page.HOME) {
            switchPage(Page.HOME);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean handleInboxClick(double mx, double my) {
        int tableY = 128;
        int rowH = 44;
        List<InboxMessageView> messages = visibleInbox();
        int rows = Math.max(1, (height - 190) / rowH);
        int row = (int) ((my - tableY) / rowH);
        int index = inboxPage * rows + row;
        if (index < 0 || index >= messages.size()) return false;
        InboxMessageView msg = messages.get(index);
        int y = tableY + row * rowH;
        if (my < y || my > y + rowH) return false;
        if (mx >= 8 && mx <= 34) {
            if (!selectedInbox.remove(msg.id())) selectedInbox.add(msg.id());
            rebuildWidgets();
            return true;
        }
        if (mx >= 44 && mx <= 62) {
            sendEnterprise(new EnterpriseRequestPayload(nextId(), EnterpriseAction.TOGGLE_MESSAGE_STAR, null, msg.id(), "", "", "", "", 0, "", false, false, EnterpriseRole.MEMBER));
            return true;
        }
        if (mx >= width - 198 && mx <= width - 170) {
            sendEnterprise(new EnterpriseRequestPayload(nextId(), EnterpriseAction.MARK_MESSAGE_READ, null, msg.id(), "", "", "", "", 0, "", false, false, EnterpriseRole.MEMBER));
            return true;
        }
        if (mx >= width - 156 && mx <= width - 126) {
            confirmDeleteInbox(msg);
            return true;
        }
        if (msg.relatedEnterpriseId() != null && mx >= width - 114 && mx <= width - 84) {
            sendEnterprise(new EnterpriseRequestPayload(nextId(), EnterpriseAction.ACCEPT_INVITE, msg.relatedEnterpriseId(), msg.id(), "", "", "", "", 0, "", false, true, EnterpriseRole.MEMBER));
            return true;
        }
        return false;
    }

    private void addAction(Component label, int x, int y, int w, int h, Runnable action, boolean active) {
        actionButtons.add(new ActionButton(x, y, w, h, label, action, active));
    }

    private void renderActionButtons(GuiGraphics g, int mx, int my) {
        for (ActionButton button : actionButtons) {
            int color = button.active() ? WHITE : MUTED;
            drawBox(g, button.x(), button.y(), button.w(), button.h(), color);
            g.drawCenteredString(font, button.label(), button.x() + button.w() / 2, button.y() + 7, color);
        }
    }

    private void renderStatus(GuiGraphics g) {
        if (messagePage == page && !message.getString().isBlank()) {
            int y = page == Page.HOME || page == Page.TRANSFER ? narrowTop() + NARROW_H - 22 : height - 18;
            g.drawCenteredString(font, message, width / 2, y, success ? GREEN : RED);
        }
    }

    private void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL_BG);
    }

    private void drawTitle(GuiGraphics g, String titleText, String subtitle, int y) {
        g.drawCenteredString(font, titleText, width / 2, y, WHITE);
        g.drawCenteredString(font, subtitle, width / 2, y + 14, MUTED);
    }

    private void drawBox(GuiGraphics g, int x, int y, int w, int h, int color) {
        hLine(g, x, x + w, y, color);
        hLine(g, x, x + w, y + h, color);
        vLine(g, x, y, y + h, color);
        vLine(g, x + w, y, y + h, color);
    }

    private void hLine(GuiGraphics g, int x1, int x2, int y) {
        hLine(g, x1, x2, y, LINE);
    }

    private void hLine(GuiGraphics g, int x1, int x2, int y, int color) {
        g.fill(x1, y, x2, y + 1, color);
        g.fill(x1, y + 1, x2, y + 2, color);
    }

    private void vLine(GuiGraphics g, int x, int y1, int y2) {
        vLine(g, x, y1, y2, LINE);
    }

    private void vLine(GuiGraphics g, int x, int y1, int y2, int color) {
        g.fill(x, y1, x + 1, y2, color);
        g.fill(x + 1, y1, x + 2, y2, color);
    }

    private boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private void sendCashAmount(boolean deposit, long amountCents) {
        if (accountFrozen) {
            showStatus(Component.translatable("message.yumesumi_finance.account.frozen"), false);
            return;
        }
        PacketDistributor.sendToServer(deposit
                ? new DepositRequestPayload(nextId(), currentAccessChannel, amountCents)
                : new WithdrawRequestPayload(nextId(), currentAccessChannel, amountCents));
    }

    private void sendCustomAmount(boolean deposit) {
        try {
            sendCashAmount(deposit, parseAmount(amountBox == null ? "" : amountBox.getValue()));
        } catch (NumberFormatException exception) {
            showStatus(Component.translatable("screen.yumesumi_finance.invalid_amount"), false);
        }
    }

    private void confirmAllCash(boolean deposit) {
        if (accountFrozen) {
            showStatus(Component.translatable("message.yumesumi_finance.account.frozen"), false);
            return;
        }
        minecraft.setScreen(new FinanceConfirmScreen(confirmed -> {
            minecraft.setScreen(this);
            if (!confirmed) return;
            PacketDistributor.sendToServer(deposit
                    ? new DepositAllRequestPayload(nextId(), currentAccessChannel)
                    : new WithdrawRequestPayload(nextId(), currentAccessChannel, balanceCents));
        }, Component.translatable(deposit ? "screen.yumesumi_finance.deposit_all_confirm_title" : "screen.yumesumi_finance.withdraw_all_confirm_title"),
                Component.translatable(deposit ? "screen.yumesumi_finance.deposit_all_confirm" : "screen.yumesumi_finance.withdraw_all_confirm", MoneyFormatter.format(balanceCents))));
    }

    private void confirmTransfer() {
        if (accountFrozen) {
            showStatus(Component.translatable("message.yumesumi_finance.account.frozen"), false);
            return;
        }
        String target = targetBox == null ? "" : targetBox.getValue().trim();
        if (target.isEmpty()) {
            showStatus(Component.translatable("screen.yumesumi_finance.invalid_target"), false);
            return;
        }
        long amount;
        try {
            amount = parseAmount(amountBox == null ? "" : amountBox.getValue());
        } catch (NumberFormatException exception) {
            showStatus(Component.translatable("screen.yumesumi_finance.invalid_amount"), false);
            return;
        }
        String note = noteBox == null ? "" : noteBox.getValue().trim();
        Component confirmText = transferEnterpriseTarget
                ? enterpriseTransferConfirm(target, amount)
                : Component.translatable("screen.yumesumi_finance.transfer_confirm_player", target, MoneyFormatter.format(amount));
        minecraft.setScreen(new FinanceConfirmScreen(confirmed -> {
            minecraft.setScreen(this);
            if (confirmed) {
                transferRequestPending = true;
                showStatus(Component.translatable("screen.yumesumi_finance.transfer_loading"), true);
                minecraft.setScreen(new TransferLoadingScreen(this));
                PacketDistributor.sendToServer(new TransferRequestPayload(nextId(), currentAccessChannel, target, amount, note, transferEnterpriseTarget));
            }
        }, Component.translatable("screen.yumesumi_finance.transfer_confirm_title"), confirmText));
    }

    private void switchPage(Page nextPage) {
        syncDrafts();
        if (accountFrozen && nextPage != Page.HOME && nextPage != Page.HISTORY && nextPage != Page.INBOX) {
            showStatus(Component.translatable("message.yumesumi_finance.account.frozen"), false);
            nextPage = Page.HOME;
        }
        Page previous = page;
        page = nextPage;
        if (page != previous && !accountFrozen) clearStatus();
        rebuildWidgets();
    }

    private void apply(BankSnapshotPayload payload) {
        balanceCents = payload.balanceCents();
        cashBalanceCents = payload.cashBalanceCents();
        message = payload.message();
        messageKey = payload.messageKey();
        success = payload.success();
        messagePage = message.getString().isBlank() ? null : page;
        currentAccessChannel = payload.channel();
        transactions = payload.transactions();
        accountFrozen = payload.accountFrozen();
        inbox = payload.inbox();
        enterprises = payload.enterprises();
        selectedInbox.removeIf(id -> inbox.stream().noneMatch(message -> message.id().equals(id)));
        if (accountFrozen && page != Page.HOME && page != Page.HISTORY && page != Page.INBOX) page = Page.HOME;
    }

    private void applyTransferResult(BankSnapshotPayload payload) {
        if (page != Page.TRANSFER || !transferRequestPending) return;
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

    private List<BankTransactionView> filteredTransactions() {
        String search = historySearch.trim().toLowerCase(Locale.ROOT);
        Comparator<BankTransactionView> comparator = historySortField == TransactionSortField.AMOUNT
                ? Comparator.comparingLong(BankTransactionView::amountCents)
                : Comparator.comparingLong(BankTransactionView::createdTime);
        if (historySortOrder == TransactionSortOrder.DESC) comparator = comparator.reversed();
        return transactions.stream()
                .filter(view -> historyDirection == TransactionDirection.ALL
                        || (historyDirection == TransactionDirection.INCOME && view.type().income())
                        || (historyDirection == TransactionDirection.EXPENSE && !view.type().income()))
                .filter(view -> search.isEmpty()
                        || view.counterpartyName().toLowerCase(Locale.ROOT).contains(search)
                        || (view.counterpartyId() != null && view.counterpartyId().toString().toLowerCase(Locale.ROOT).contains(search))
                        || visibleNote(view.note()).toLowerCase(Locale.ROOT).contains(search)
                        || Long.toString(view.amountCents()).contains(search)
                        || Long.toString(view.balanceAfterCents()).contains(search)
                        || MoneyFormatter.format(view.amountCents()).toLowerCase(Locale.ROOT).contains(search)
                        || MoneyFormatter.format(view.balanceAfterCents()).toLowerCase(Locale.ROOT).contains(search))
                .sorted(comparator)
                .limit(1_000)
                .toList();
    }

    private List<InboxMessageView> visibleInbox() {
        String search = inboxSearch.trim().toLowerCase(Locale.ROOT);
        Comparator<InboxMessageView> comparator = inboxSort == InboxSort.TITLE
                ? Comparator.comparing(InboxMessageView::title, String.CASE_INSENSITIVE_ORDER)
                : Comparator.comparingLong(InboxMessageView::createdAt);
        if (inboxSortOrder == TransactionSortOrder.DESC) comparator = comparator.reversed();
        return inbox.stream()
                .filter(msg -> switch (inboxFilter) {
                    case ALL -> true;
                    case UNREAD -> !msg.read();
                    case STARRED -> msg.starred();
                    case INVITE -> msg.relatedEnterpriseId() != null;
                })
                .filter(msg -> search.isBlank()
                        || msg.title().toLowerCase(Locale.ROOT).contains(search)
                        || msg.body().toLowerCase(Locale.ROOT).contains(search)
                        || formatTime(msg.createdAt()).toLowerCase(Locale.ROOT).contains(search))
                .sorted(comparator)
                .limit(1_000)
                .toList();
    }

    private void selectInboxPage() {
        List<InboxMessageView> messages = visibleInbox();
        int rows = Math.max(1, (height - 190) / 44);
        int from = Math.min(inboxPage * rows, messages.size());
        int to = Math.min(from + rows, messages.size());
        for (int i = from; i < to; i++) selectedInbox.add(messages.get(i).id());
        rebuildWidgets();
    }

    private void confirmDeleteInbox(InboxMessageView msg) {
        if (msg.starred()) {
            showStatus(Component.translatable("message.yumesumi_finance.inbox.starred_cannot_delete"), false);
            return;
        }
        minecraft.setScreen(new FinanceConfirmScreen(confirmed -> {
            minecraft.setScreen(this);
            if (confirmed) {
                sendEnterprise(new EnterpriseRequestPayload(nextId(), EnterpriseAction.DELETE_MESSAGE, null, msg.id(), "", "", "", "", 0, "", false, false, EnterpriseRole.MEMBER));
            }
        }, Component.translatable("screen.yumesumi_finance.inbox.delete_confirm_title"),
                Component.translatable("screen.yumesumi_finance.inbox.delete_confirm")));
    }

    private void confirmDeleteSelectedInbox() {
        List<InboxMessageView> targets = visibleInbox().stream()
                .filter(msg -> selectedInbox.contains(msg.id()) && !msg.starred())
                .toList();
        if (targets.isEmpty()) {
            showStatus(Component.translatable("message.yumesumi_finance.inbox.no_deletable_selected"), false);
            return;
        }
        minecraft.setScreen(new FinanceConfirmScreen(confirmed -> {
            minecraft.setScreen(this);
            if (confirmed) {
                for (InboxMessageView msg : targets) {
                    sendEnterprise(new EnterpriseRequestPayload(nextId(), EnterpriseAction.DELETE_MESSAGE, null, msg.id(), "", "", "", "", 0, "", false, false, EnterpriseRole.MEMBER));
                }
                selectedInbox.clear();
            }
        }, Component.translatable("screen.yumesumi_finance.inbox.delete_confirm_title"),
                Component.translatable("screen.yumesumi_finance.inbox.bulk_delete_confirm", targets.size())));
    }

    private void cycleInboxFilter() {
        inboxFilter = switch (inboxFilter) {
            case ALL -> InboxFilter.UNREAD;
            case UNREAD -> InboxFilter.STARRED;
            case STARRED -> InboxFilter.INVITE;
            case INVITE -> InboxFilter.ALL;
        };
        inboxPage = 0;
        rebuildWidgets();
    }

    private void clampHistoryPage() {
        int rows = Math.max(1, (height - 204) / HISTORY_ROW_H);
        int max = Math.max(0, (filteredTransactions().size() + rows - 1) / rows - 1);
        historyPage = Math.min(historyPage, max);
    }

    private void clampInboxPage() {
        int rows = Math.max(1, (height - 190) / 44);
        int max = Math.max(0, (visibleInbox().size() + rows - 1) / rows - 1);
        inboxPage = Math.min(inboxPage, max);
    }

    private void syncDrafts() {
        if (targetBox != null) transferTargetText = targetBox.getValue();
        if (amountBox != null && page == Page.TRANSFER) transferAmountText = amountBox.getValue();
        if (amountBox != null && (page == Page.DEPOSIT || page == Page.WITHDRAW)) customAmountText = amountBox.getValue();
        if (noteBox != null) transferNoteText = noteBox.getValue();
    }

    private void sendEnterprise(EnterpriseRequestPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    private void showStatus(Component status, boolean success) {
        this.message = status;
        this.messageKey = "";
        this.success = success;
        this.messagePage = page;
    }

    private void clearStatus() {
        this.message = Component.empty();
        this.messageKey = "";
        this.success = false;
        this.messagePage = null;
    }

    private long parseAmount(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (!AMOUNT_PATTERN.matcher(text).matches()) throw new NumberFormatException("Invalid amount");
        long amount = MoneyFormatter.parseCents(text);
        if (amount <= 0) throw new NumberFormatException("Invalid amount");
        return amount;
    }

    private Component enterpriseTransferConfirm(String code, long amount) {
        String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        return enterprises.stream()
                .filter(view -> view.code().equalsIgnoreCase(normalized))
                .findFirst()
                .<Component>map(view -> Component.translatable("screen.yumesumi_finance.transfer_confirm_enterprise", view.name(), view.code(), MoneyFormatter.format(amount)))
                .orElse(Component.translatable("screen.yumesumi_finance.transfer_confirm_enterprise_unknown", normalized, MoneyFormatter.format(amount)));
    }

    private Component inboxFilterLabel() {
        return Component.translatable("screen.yumesumi_finance.inbox_filter." + inboxFilter.name().toLowerCase(Locale.ROOT));
    }

    private Component inboxSortLabel() {
        return Component.translatable("screen.yumesumi_finance.inbox_sort." + inboxSort.name().toLowerCase(Locale.ROOT));
    }

    private Component inboxOrderLabel() {
        return Component.translatable("screen.yumesumi_finance.history_order." + inboxSortOrder.name().toLowerCase(Locale.ROOT));
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

    private String visibleNote(String note) {
        return note == null || note.startsWith("message.") ? "" : note.trim();
    }

    private String notePreview(String note) {
        if (note.length() <= 24) return note;
        return note.substring(0, 24) + "...";
    }

    private String counterpartyText(BankTransactionView view) {
        return view.counterpartyName().isBlank() ? "" : view.counterpartyName();
    }

    private String transactionSourceName(BankTransactionView view) {
        return switch (view.type()) {
            case DEPOSIT, WITHDRAW -> counterpartyText(view);
            case TRANSFER_IN, TRANSFER_OUT -> currentAccessChannel == BankAccessChannel.ATM ? "ATM机转账" : "随身银行转账";
        };
    }

    private String formatTime(long ms) {
        return ms <= 0 ? "-" : TIME_FMT.format(Instant.ofEpochMilli(ms));
    }

    private String playerName() {
        return Minecraft.getInstance().player == null ? "" : Minecraft.getInstance().player.getGameProfile().getName();
    }

    private UUID currentPlayerId() {
        return Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getUUID();
    }

    private void renderPlayerTooltip(GuiGraphics g, int mx, int my, int x, int y, String name, UUID uuid) {
        if (uuid != null && !name.isBlank() && inside(mx, my, x, y, font.width(name), 10)) {
            g.renderTooltip(font, Component.literal(uuid.toString()), mx, my);
        }
    }

    private int[] historyColumns() {
        int c1 = Math.max(164, width / 6);
        int c2 = Math.max(c1 + 174, width / 3);
        int c3 = Math.max(c2 + 208, width / 2 + 38);
        return new int[]{c1, c2, c3};
    }

    private int narrowLeft() { return (width - NARROW_W) / 2; }
    private int narrowTop() { return (height - NARROW_H) / 2; }

    private long nextId() {
        requestCounter++;
        if (requestCounter <= 0) requestCounter = 1;
        return requestCounter;
    }

    public static final class TransferLoadingScreen extends Screen {
        private final BankScreen parent;

        private TransferLoadingScreen(BankScreen parent) {
            super(Component.translatable("screen.yumesumi_finance.transfer_loading"));
            this.parent = parent;
        }

        public BankScreen parent() { return parent; }

        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {
            g.fill(0, 0, width, height, SCREEN_BG);
            g.drawCenteredString(font, title, width / 2, height / 2 - 8, FinanceTheme.GOLD);
            super.render(g, mx, my, pt);
        }
    }
}
