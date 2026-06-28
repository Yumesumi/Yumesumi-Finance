package com.yumesumi.finance.client.gui;

import com.yumesumi.finance.bank.account.BankAccountType;
import com.yumesumi.finance.bank.admin.BankAccountSortField;
import com.yumesumi.finance.bank.admin.BankAdminAction;
import com.yumesumi.finance.client.gui.foundation.FinanceTheme;
import com.yumesumi.finance.money.MoneyFormatter;
import com.yumesumi.finance.network.payload.BankAdminRequestPayload;
import com.yumesumi.finance.network.payload.BankAdminSnapshotPayload;
import com.yumesumi.finance.network.payload.BankAdminSnapshotPayload.BankAccountView;
import com.yumesumi.finance.network.payload.BankAdminSnapshotPayload.TreasuryTransactionView;
import java.math.BigInteger;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class BankAdminScreen extends Screen {
    private enum Page { MAIN, ACCOUNT_LIST, TREASURY }
    private enum SortOrder { ASC, DESC }
    private enum TreasurySort { TIME, AMOUNT, BALANCE }

    private static final int PANEL_MARGIN = 12;
    private static final int CONTENT_PAD = 16;
    private static final int ROW_HEIGHT = 26;
    private static final int TABLE_HEADER_HEIGHT = 22;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private static final int SCREEN_BG = FinanceTheme.BACKGROUND;
    private static final int C_BG = SCREEN_BG;
    private static final int C_HEADER = SCREEN_BG;
    private static final int C_TABLE_HDR = 0xFF1A2436;
    private static final int C_ROW_EVEN = 0xFF111A28;
    private static final int C_ROW_ODD = 0xFF0B111C;
    private static final int C_ROW_SEL = 0xFF254B7A;
    private static final int C_ROW_FROZEN = 0x22FF3333;
    private static final int C_TEXT = 0xFFFFFFFF;
    private static final int C_TEXT_DIM = 0xFFF4F7FF;
    private static final int C_TEXT_MUTED = 0xFFE3EAF5;
    private static final int C_ACCENT = 0xFF84C2FF;
    private static final int C_ACCENT2 = 0xFFFFD36A;
    private static final int C_GREEN = 0xFF7DFFC0;
    private static final int C_RED = 0xFFFF7480;
    private static final int C_DIVIDER = 0x66FFFFFF;
    private static final UUID TREASURY_UUID = UUID.fromString("a0000000-0000-0000-0000-000000000001");

    private Page page = Page.MAIN;
    private BankAccountType listType = BankAccountType.PERSONAL;
    private List<BankAccountView> allAccounts = List.of();
    private BigInteger treasuryBalance = BigInteger.ZERO;
    private List<TreasuryTransactionView> treasuryTransactions = List.of();
    private final Set<UUID> selected = new HashSet<>();
    private int currentPage;
    private String searchText = "";
    private BankAccountSortField sortField = BankAccountSortField.LAST_ACTIVE;
    private SortOrder sortOrder = SortOrder.DESC;
    private TreasurySort treasurySort = TreasurySort.TIME;
    private SortOrder treasuryOrder = SortOrder.DESC;
    private int treasuryPage;
    private String treasurySearch = "";
    private Component statusMessage = Component.empty();
    private int statusTimer;
    private EditBox searchBox;
    private long requestCounter = System.nanoTime() & Long.MAX_VALUE;

    public BankAdminScreen(BankAdminSnapshotPayload payload) {
        super(Component.translatable("screen.yumesumi_finance.admin.title"));
        apply(payload);
    }

    public void update(BankAdminSnapshotPayload payload) {
        apply(payload);
        rebuildWidgets();
    }

    @Override protected void init() { rebuildWidgets(); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        int left = panelLeft(), top = panelTop(), w = panelWidth(), h = panelHeight();
        g.drawCenteredString(font, title, left + w / 2, top + 11, C_ACCENT);
        switch (page) {
            case MAIN -> renderMainPage(g, left, top, w, h);
            case ACCOUNT_LIST -> renderAccountList(g, left, top, w, h, mx, my);
            case TREASURY -> renderTreasuryPage(g, left, top, w, h, mx, my);
        }
        super.render(g, mx, my, pt);
        if (statusTimer > 0 && !statusMessage.getString().isEmpty()) {
            g.drawCenteredString(font, statusMessage, left + w / 2, top + h - 10, C_ACCENT2);
        }
    }

    @Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) { g.fill(0, 0, width, height, SCREEN_BG); }
    @Override public void tick() { if (statusTimer > 0) statusTimer--; }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        searchBox = null;
        switch (page) {
            case MAIN -> buildMainPage();
            case ACCOUNT_LIST -> buildAccountListPage();
            case TREASURY -> buildTreasuryPage();
        }
    }

    private void renderMainPage(GuiGraphics g, int l, int t, int w, int h) {
        g.drawCenteredString(font, Component.translatable("screen.yumesumi_finance.admin.subtitle"), l + w / 2, t + 48, C_TEXT_DIM);
        g.drawCenteredString(font, Component.translatable("screen.yumesumi_finance.admin.treasury_balance", MoneyFormatter.format(treasuryBalance)), l + w / 2, t + 64, C_ACCENT2);
    }

    private void buildMainPage() {
        int l = panelLeft(), t = panelTop(), w = panelWidth();
        int bw = Math.min(200, (w - CONTENT_PAD * 2 - 20) / 3);
        int startX = l + (w - bw * 3 - 10) / 2;
        int y = t + Math.max(100, panelHeight() / 2 - 20);
        addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.admin.personal"), b -> openList(BankAccountType.PERSONAL)).bounds(startX, y, bw, 32).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.admin.enterprise"), b -> openList(BankAccountType.ENTERPRISE)).bounds(startX + bw + 5, y, bw, 32).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.admin.treasury"), b -> { clearStatus(); page = Page.TREASURY; rebuildWidgets(); }).bounds(startX + bw * 2 + 10, y, bw, 32).build());
    }

    private void openList(BankAccountType type) {
        clearStatus(); listType = type; page = Page.ACCOUNT_LIST; selected.clear(); currentPage = 0; searchText = ""; rebuildWidgets();
    }

    private void renderAccountList(GuiGraphics g, int l, int t, int w, int h, int mx, int my) {
        List<BankAccountView> rows = filteredSorted();
        int tableTop = t + 126;
        int tableBottom = t + h - 38;
        int rowsPerPage = Math.max(1, (tableBottom - tableTop - TABLE_HEADER_HEIGHT) / ROW_HEIGHT);
        int totalPages = Math.max(1, (rows.size() + rowsPerPage - 1) / rowsPerPage);
        currentPage = Math.min(currentPage, totalPages - 1);
        int from = currentPage * rowsPerPage;
        int to = Math.min(from + rowsPerPage, rows.size());
        int tw = w - CONTENT_PAD * 2;
        int tl = l + CONTENT_PAD;

        int tabY = t + 40, tabW = 100;
        boolean isP = listType == BankAccountType.PERSONAL;
        g.drawCenteredString(font, Component.translatable("screen.yumesumi_finance.admin.tab_personal"), tl + tabW / 2, tabY + 6, isP ? C_ACCENT2 : C_TEXT);
        g.drawCenteredString(font, Component.translatable("screen.yumesumi_finance.admin.tab_enterprise"), tl + tabW + 4 + tabW / 2, tabY + 6, !isP ? C_ACCENT2 : C_TEXT);

        int hdrY = tableTop;
        int[] cx = colX(tl, tw);
        String[] headers = listType == BankAccountType.ENTERPRISE
                ? new String[]{"", "企业名称", "企业代码", "企业简介", "\u5f00\u6237\u65f6\u95f4", "\u6700\u540e\u6d3b\u8dc3", "\u4f59\u989d", "\u72b6\u6001", "\u5907\u6ce8"}
                : new String[]{"", "\u540d\u79f0", "UUID", "\u5f00\u6237\u65f6\u95f4", "\u6700\u540e\u6d3b\u8dc3", "\u4f59\u989d", "\u72b6\u6001", "\u5907\u6ce8"};
        for (int i = 0; i < headers.length && i < cx.length; i++) g.drawString(font, headers[i], cx[i] + 2, hdrY + 7, C_TEXT_DIM, true);
        if (rows.isEmpty()) {
            g.drawString(font, "没有可显示的账户", tl, hdrY + TABLE_HEADER_HEIGHT + 12, C_TEXT_MUTED, true);
        }

        for (int i = from; i < to; i++) {
            BankAccountView v = rows.get(i);
            int ry = hdrY + TABLE_HEADER_HEIGHT + (i - from) * ROW_HEIGHT;
            boolean sel = selected.contains(v.owner());
            g.fill(tl, ry, tl + tw, ry + ROW_HEIGHT - 1, sel ? C_ROW_SEL : ((i - from) % 2 == 0 ? C_ROW_EVEN : C_ROW_ODD));
            if (v.frozen()) g.fill(tl, ry, tl + tw, ry + ROW_HEIGHT - 1, C_ROW_FROZEN);
            g.drawString(font, sel ? "[x]" : "[ ]", cx[0] + 2, ry + 5, C_TEXT, true);
            g.drawString(font, fitText(v.ownerName(), colW(1) - 4), cx[1] + 2, ry + 5, C_TEXT, true);
            if (listType == BankAccountType.ENTERPRISE) {
                g.drawString(font, fitText(v.accountCode(), colW(2) - 4), cx[2] + 2, ry + 5, C_TEXT_MUTED, true);
                g.drawString(font, fitText(v.adminNote(), Math.min(colW(3) - 4, font.width("一二三四五六七八九十..."))), cx[3] + 2, ry + 5, C_TEXT_DIM, true);
                if (mx >= cx[3] && mx <= cx[3] + colW(3) && my >= ry && my <= ry + ROW_HEIGHT && font.width(v.adminNote()) > colW(3) - 4) {
                    g.renderTooltip(font, Component.literal(v.adminNote()), mx, my);
                }
            } else {
                g.drawString(font, fitText(v.accountCode(), colW(2) - 4), cx[2] + 2, ry + 5, C_TEXT_MUTED, true);
            }
            int createdCol = listType == BankAccountType.ENTERPRISE ? 4 : 3;
            int activeCol = listType == BankAccountType.ENTERPRISE ? 5 : 4;
            int balanceCol = listType == BankAccountType.ENTERPRISE ? 6 : 5;
            int stateCol = listType == BankAccountType.ENTERPRISE ? 7 : 6;
            int noteCol = listType == BankAccountType.ENTERPRISE ? 8 : 7;
            g.drawString(font, formatTime(v.createdAt()), cx[createdCol] + 2, ry + 5, C_TEXT_MUTED, true);
            g.drawString(font, formatTime(v.lastActiveAt()), cx[activeCol] + 2, ry + 5, C_TEXT_MUTED, true);
            g.drawString(font, MoneyFormatter.format(v.balanceCents()), cx[balanceCol] + 2, ry + 5, v.balanceCents() >= 0 ? C_GREEN : C_RED, true);
            g.drawString(font, v.frozen() ? "\u51bb\u7ed3" : "\u6b63\u5e38", cx[stateCol] + 2, ry + 5, v.frozen() ? C_RED : C_GREEN, true);
            if (listType != BankAccountType.ENTERPRISE) {
                String note = v.adminNote().length() > 32 ? v.adminNote().substring(0, 32) + "..." : v.adminNote();
                g.drawString(font, fitText(note, colW(noteCol) - 4), cx[noteCol] + 2, ry + 5, C_TEXT_MUTED, true);
                if (mx >= cx[noteCol] && mx <= cx[noteCol] + colW(noteCol) && my >= ry && my <= ry + ROW_HEIGHT && v.adminNote().length() > 32) {
                    g.renderTooltip(font, Component.literal(v.adminNote()), mx, my);
                }
            }
            g.fill(tl, ry + ROW_HEIGHT - 1, tl + tw, ry + ROW_HEIGHT, C_DIVIDER);
        }

        int by = t + h - 30;
        int selCount = (int) selected.stream().filter(uuid -> rows.stream().anyMatch(v -> v.owner().equals(uuid))).count();
        g.drawString(font, "\u5df2\u9009: " + selCount + " / " + rows.size(), tl, by - 12, C_TEXT_DIM, true);
        g.drawCenteredString(font, (currentPage + 1) + "/" + totalPages, l + w / 2, by - 12, C_TEXT_MUTED);
    }

    private void buildAccountListPage() {
        int l = panelLeft(), t = panelTop(), w = panelWidth();
        int tl = l + CONTENT_PAD, tw = w - CONTENT_PAD * 2;
        int y1 = t + 64;
        int y2 = t + 88;
        int searchW = Math.min(300, Math.max(180, tw / 3));
        searchBox = new EditBox(font, tl, y1, searchW, 18, Component.empty());
        searchBox.setHint(Component.translatable(listType == BankAccountType.ENTERPRISE ? "screen.yumesumi_finance.admin.enterprise_search_hint" : "screen.yumesumi_finance.admin.search_hint"));
        searchBox.setValue(searchText);
        searchBox.setResponder(v -> { searchText = v; currentPage = 0; });
        addRenderableWidget(searchBox);
        int x = tl + searchW + 10;
        addRenderableWidget(Button.builder(sortLabel(), b -> cycleSortField()).bounds(x, y1, 70, 18).build());
        addRenderableWidget(Button.builder(orderLabel(), b -> { sortOrder = sortOrder == SortOrder.DESC ? SortOrder.ASC : SortOrder.DESC; rebuildWidgets(); }).bounds(x + 76, y1, 50, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.admin.select_all"), b -> selectAll()).bounds(x + 132, y1, 50, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.admin.invert"), b -> invertSelection()).bounds(x + 188, y1, 50, 18).build());
        int ax = tl;
        addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.admin.act_set_bal"), b -> doSetBalance()).bounds(ax, y2, 60, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.admin.act_add_bal"), b -> doAddBalance()).bounds(ax + 66, y2, 60, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.admin.act_freeze"), b -> doToggleFreeze()).bounds(ax + 132, y2, 50, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.admin.act_note"), b -> doEditNote()).bounds(ax + 188, y2, 50, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.admin.act_delete"), b -> doDelete()).bounds(ax + 244, y2, 50, 18).build());
        int by = t + panelHeight() - 34;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> { currentPage = Math.max(0, currentPage - 1); rebuildWidgets(); }).bounds(tl, by, 24, 18).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> { currentPage++; rebuildWidgets(); }).bounds(tl + 28, by, 24, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.back"), b -> { clearStatus(); page = Page.MAIN; rebuildWidgets(); }).bounds(l + w - CONTENT_PAD - 50, by, 50, 18).build());
    }

    private void renderTreasuryPage(GuiGraphics g, int l, int t, int w, int h, int mx, int my) {
        int cx = l + w / 2;
        g.drawCenteredString(font, Component.translatable("screen.yumesumi_finance.admin.treasury_title"), cx, t + 44, C_ACCENT2);
        g.drawCenteredString(font, MoneyFormatter.format(treasuryBalance), cx, t + 62, C_TEXT);
        List<TreasuryTransactionView> rows = filteredTreasuryTransactions();
        int tableX = l + CONTENT_PAD;
        int tableY = t + 128;
        int tableW = w - CONTENT_PAD * 2;
        int rowH = 36;
        int rowsPerPage = Math.max(1, (t + h - 54 - tableY) / rowH);
        int totalPages = Math.max(1, (rows.size() + rowsPerPage - 1) / rowsPerPage);
        treasuryPage = Math.min(treasuryPage, totalPages - 1);
        int from = treasuryPage * rowsPerPage;
        int to = Math.min(from + rowsPerPage, rows.size());
        g.fill(tableX, tableY - 22, tableX + tableW, tableY, C_TABLE_HDR);
        g.drawString(font, "\u7c7b\u578b", tableX + 8, tableY - 15, C_TEXT_DIM, true);
        g.drawString(font, "\u91d1\u989d", tableX + 120, tableY - 15, C_TEXT_DIM, true);
        g.drawString(font, "\u64cd\u4f5c\u4eba", tableX + 250, tableY - 15, C_TEXT_DIM, true);
        g.drawString(font, "\u5907\u6ce8", tableX + 390, tableY - 15, C_TEXT_DIM, true);
        g.fill(tableX, tableY, tableX + tableW, tableY + 2, C_DIVIDER);
        g.fill(tableX + 108, tableY - 22, tableX + 110, tableY + rowsPerPage * rowH, C_DIVIDER);
        g.fill(tableX + 238, tableY - 22, tableX + 240, tableY + rowsPerPage * rowH, C_DIVIDER);
        g.fill(tableX + 378, tableY - 22, tableX + 380, tableY + rowsPerPage * rowH, C_DIVIDER);
        if (rows.isEmpty()) {
            g.drawString(font, "\u6682\u65e0\u56fd\u5e93\u6d41\u6c34", tableX, tableY + 12, C_TEXT_MUTED, true);
        }
        for (int i = from; i < to; i++) {
            TreasuryTransactionView tx = rows.get(i);
            int y = tableY + (i - from) * rowH;
            int color = tx.deltaCents().signum() >= 0 ? C_GREEN : C_RED;
            g.fill(tableX, y, tableX + tableW, y + rowH - 1, (i - from) % 2 == 0 ? C_ROW_EVEN : C_ROW_ODD);
            g.drawString(font, tx.deltaCents().signum() >= 0 ? "\u56fd\u5e93\u6536\u5165" : "\u56fd\u5e93\u652f\u51fa", tableX + 8, y + 6, C_TEXT, true);
            g.drawString(font, MoneyFormatter.format(tx.deltaCents()), tableX + 120, y + 6, color, true);
            g.drawString(font, Component.translatable("screen.yumesumi_finance.enterprise.operator", tx.operatorName()), tableX + 250, y + 6, C_TEXT_DIM, true);
            g.drawString(font, formatTime(tx.createdAt()), tableX + 8, y + 20, C_TEXT_MUTED, true);
            g.drawString(font, "\u4f59\u989d " + MoneyFormatter.format(tx.balanceAfterCents()), tableX + 120, y + 20, C_ACCENT2, true);
            int noteWidth = Math.max(80, tableW - 400);
            g.drawString(font, fitText(tx.note(), noteWidth), tableX + 390, y + 6, C_TEXT_MUTED, true);
            g.drawString(font, tx.deltaCents().signum() >= 0 ? "\u6765\u81ea\u7cfb\u7edf/\u56de\u6d41" : "\u53bb\u5411\u73a9\u5bb6/\u4f01\u4e1a", tableX + 250, y + 20, C_TEXT_MUTED, true);
            if (mx >= tableX + 390 && mx <= tableX + tableW && my >= y && my <= y + rowH && font.width(tx.note()) > noteWidth) {
                g.renderTooltip(font, Component.literal(tx.note()), mx, my);
            }
            g.fill(tableX, y + rowH - 1, tableX + tableW, y + rowH, C_DIVIDER);
        }
        g.drawCenteredString(font, (treasuryPage + 1) + "/" + totalPages + "  " + rows.size() + "/10000", l + w / 2, t + h - 48, C_TEXT_MUTED);
    }

    private void buildTreasuryPage() {
        int l = panelLeft(), t = panelTop(), w = panelWidth(), by = t + panelHeight() - 34;
        searchBox = new EditBox(font, l + CONTENT_PAD, t + 92, 180, 20, Component.empty());
        searchBox.setHint(Component.translatable("screen.yumesumi_finance.history_search"));
        searchBox.setValue(treasurySearch);
        searchBox.setResponder(v -> { treasurySearch = v; treasuryPage = 0; });
        addRenderableWidget(searchBox);
        addRenderableWidget(Button.builder(treasurySortLabel(), b -> cycleTreasurySort()).bounds(l + CONTENT_PAD + 188, t + 92, 68, 20).build());
        addRenderableWidget(Button.builder(orderLabelFor(treasuryOrder), b -> { treasuryOrder = treasuryOrder == SortOrder.DESC ? SortOrder.ASC : SortOrder.DESC; rebuildWidgets(); }).bounds(l + CONTENT_PAD + 262, t + 92, 50, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.admin.treasury_adjust"), b -> doTreasuryAdjust()).bounds(l + w - 374, t + 92, 86, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.admin.treasury_set"), b -> doTreasurySet()).bounds(l + w - 282, t + 92, 86, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.admin.treasury_transfer"), b -> doTreasuryTransfer()).bounds(l + w - 190, t + 92, 86, 20).build());
        addRenderableWidget(Button.builder(Component.literal("<"), b -> { treasuryPage = Math.max(0, treasuryPage - 1); rebuildWidgets(); }).bounds(l + CONTENT_PAD, by, 24, 18).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> { treasuryPage++; rebuildWidgets(); }).bounds(l + CONTENT_PAD + 28, by, 24, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.back"), b -> { clearStatus(); page = Page.MAIN; rebuildWidgets(); }).bounds(l + w - CONTENT_PAD - 50, by, 50, 18).build());
    }

    private List<BankAccountView> filteredSorted() {
        List<BankAccountView> list = new ArrayList<>();
        for (BankAccountView v : allAccounts) {
            if (v.accountType() != listType) continue;
            if (!searchText.isBlank()) {
                String s = searchText.toLowerCase(Locale.ROOT);
                if (!v.ownerName().toLowerCase().contains(s)
                        && !v.accountCode().toLowerCase().contains(s)
                        && !v.adminNote().toLowerCase().contains(s)) continue;
            }
            list.add(v);
        }
        Comparator<BankAccountView> cmp = switch (sortField) {
            case NAME -> Comparator.comparing(BankAccountView::ownerName, String.CASE_INSENSITIVE_ORDER);
            case BALANCE -> Comparator.comparingLong(BankAccountView::balanceCents);
            case CREATED -> Comparator.comparingLong(BankAccountView::createdAt);
            case LAST_ACTIVE -> Comparator.comparingLong(BankAccountView::lastActiveAt);
        };
        if (sortOrder == SortOrder.DESC) cmp = cmp.reversed();
        list.sort(cmp);
        return list;
    }

    private List<TreasuryTransactionView> filteredTreasuryTransactions() {
        String search = treasurySearch.trim().toLowerCase(Locale.ROOT);
        Comparator<TreasuryTransactionView> comparator = switch (treasurySort) {
            case TIME -> Comparator.comparingLong(TreasuryTransactionView::createdAt);
            case AMOUNT -> Comparator.comparing(TreasuryTransactionView::deltaCents);
            case BALANCE -> Comparator.comparing(TreasuryTransactionView::balanceAfterCents);
        };
        if (treasuryOrder == SortOrder.DESC) comparator = comparator.reversed();
        return treasuryTransactions.stream()
                .filter(tx -> search.isBlank()
                        || tx.operatorName().toLowerCase(Locale.ROOT).contains(search)
                        || tx.note().toLowerCase(Locale.ROOT).contains(search)
                        || tx.deltaCents().toString().contains(search)
                        || MoneyFormatter.format(tx.deltaCents()).toLowerCase(Locale.ROOT).contains(search))
                .sorted(comparator)
                .limit(10_000)
                .toList();
    }

    private void cycleTreasurySort() {
        treasurySort = switch (treasurySort) {
            case TIME -> TreasurySort.AMOUNT;
            case AMOUNT -> TreasurySort.BALANCE;
            case BALANCE -> TreasurySort.TIME;
        };
        rebuildWidgets();
    }

    private Component treasurySortLabel() {
        return Component.translatable("screen.yumesumi_finance.admin.treasury_sort." + treasurySort.name().toLowerCase(Locale.ROOT));
    }

    private void selectAll() { for (BankAccountView v : filteredSorted()) selected.add(v.owner()); rebuildWidgets(); }
    private void invertSelection() {
        Set<UUID> ns = new HashSet<>();
        for (BankAccountView v : filteredSorted()) if (!selected.contains(v.owner())) ns.add(v.owner());
        selected.clear(); selected.addAll(ns); rebuildWidgets();
    }

    private void doSetBalance() {
        if (selected.isEmpty()) { setStatus("\u8bf7\u5148\u9009\u62e9\u8d26\u6237"); return; }
        minecraft.setScreen(new InputScreen(this, "\u8bbe\u7f6e\u4f59\u989d (\u5206)", "", input -> {
            try { long val = Long.parseLong(input.trim()); for (UUID id : selected) send(new BankAdminRequestPayload(nextId(), BankAdminAction.SET_BALANCE, id, val, "")); setStatus("\u5df2\u53d1\u9001\u8bbe\u7f6e\u4f59\u989d\u8bf7\u6c42"); } catch (NumberFormatException e) { setStatus("\u65e0\u6548\u91d1\u989d"); }
        }));
    }

    private void doAddBalance() {
        if (selected.isEmpty()) { setStatus("\u8bf7\u5148\u9009\u62e9\u8d26\u6237"); return; }
        minecraft.setScreen(new InputScreen(this, "\u8c03\u6574\u4f59\u989d (\u5206\uff0c\u53ef\u4e3a\u8d1f)", "", input -> {
            try { long val = Long.parseLong(input.trim()); for (UUID id : selected) send(new BankAdminRequestPayload(nextId(), BankAdminAction.ADD_BALANCE, id, val, "")); setStatus("\u5df2\u53d1\u9001\u8c03\u6574\u4f59\u989d\u8bf7\u6c42"); } catch (NumberFormatException e) { setStatus("\u65e0\u6548\u91d1\u989d"); }
        }));
    }

    private void doToggleFreeze() {
        if (selected.isEmpty()) { setStatus("\u8bf7\u5148\u9009\u62e9\u8d26\u6237"); return; }
        for (UUID id : selected) send(new BankAdminRequestPayload(nextId(), BankAdminAction.TOGGLE_FREEZE, id, 0, ""));
        setStatus("\u5df2\u53d1\u9001\u51bb\u7ed3/\u89e3\u51bb\u8bf7\u6c42");
    }

    private void doEditNote() {
        if (selected.isEmpty()) { setStatus("\u8bf7\u5148\u9009\u62e9\u8d26\u6237"); return; }
        List<BankAccountView> selectedViews = filteredSorted().stream().filter(v -> selected.contains(v.owner())).toList();
        String initial = selectedViews.size() == 1
                ? selectedViews.get(0).adminNote()
                : "";
        minecraft.setScreen(new InputScreen(this, "\u7ba1\u7406\u5458\u5907\u6ce8 (\u6700\u957f64\u5b57)", initial, input -> {
            String note = input.length() > 64 ? input.substring(0, 64) : input;
            for (UUID id : selected) send(new BankAdminRequestPayload(nextId(), BankAdminAction.UPDATE_NOTE, id, 0, note));
            setStatus("\u5df2\u53d1\u9001\u66f4\u65b0\u5907\u6ce8\u8bf7\u6c42");
        }));
    }

    private void doDelete() {
        if (selected.isEmpty()) { setStatus("\u8bf7\u5148\u9009\u62e9\u8d26\u6237"); return; }
        boolean hasBal = filteredSorted().stream().anyMatch(v -> selected.contains(v.owner()) && v.balanceCents() != 0);
        if (hasBal) {
            minecraft.setScreen(new FinanceConfirmScreen(confirmed -> {
                minecraft.setScreen(this);
                if (confirmed) {
                    for (UUID id : selected) send(new BankAdminRequestPayload(nextId(), BankAdminAction.DELETE_CONFIRMED, id, 0, ""));
                    selected.clear(); setStatus("\u5df2\u53d1\u9001\u5f3a\u5236\u5220\u9664\u8bf7\u6c42");
                }
            }, Component.translatable("screen.yumesumi_finance.admin.delete_confirm_title"), Component.translatable("screen.yumesumi_finance.admin.delete_confirm")));
        } else {
            for (UUID id : selected) send(new BankAdminRequestPayload(nextId(), BankAdminAction.DELETE, id, 0, ""));
            selected.clear(); setStatus("\u5df2\u53d1\u9001\u5220\u9664\u8bf7\u6c42");
        }
    }

    private void doTreasuryAdjust() {
        minecraft.setScreen(new InputScreen(this, "\u56fd\u5e93\u8c03\u8d26 (\u5206\uff0c\u53ef\u4e3a\u8d1f)", "", input -> {
            try {
                new BigInteger(input.trim());
                send(new BankAdminRequestPayload(nextId(), BankAdminAction.ADD_BALANCE, TREASURY_UUID, 0, input.trim()));
                setStatus("\u5df2\u53d1\u9001\u56fd\u5e93\u8c03\u8d26\u8bf7\u6c42");
            } catch (NumberFormatException e) { setStatus("\u65e0\u6548\u91d1\u989d"); }
        }, 160));
    }

    private void doTreasurySet() {
        minecraft.setScreen(new InputScreen(this, "\u76f4\u63a5\u8bbe\u7f6e\u56fd\u5e93\u4f59\u989d (\u5206)", treasuryBalance.toString(), input -> {
            try {
                new BigInteger(input.trim());
                send(new BankAdminRequestPayload(nextId(), BankAdminAction.SET_TREASURY_BALANCE, TREASURY_UUID, 0, input.trim()));
                setStatus("\u5df2\u53d1\u9001\u56fd\u5e93\u4f59\u989d\u8bbe\u7f6e\u8bf7\u6c42");
            } catch (NumberFormatException e) { setStatus("\u65e0\u6548\u91d1\u989d"); }
        }, 160));
    }


    private void doTreasuryTransfer() {
        minecraft.setScreen(new InputScreen(this, "\u56fd\u5e93\u8f6c\u8d26: \u76ee\u6807,\u91d1\u989d(\u5206),\u5907\u6ce8", "", input -> {
            String[] parts = input.split(",", 3);
            if (parts.length < 2) { setStatus("\u683c\u5f0f: \u76ee\u6807,\u91d1\u989d(\u5206),\u5907\u6ce8"); return; }
            String target = parts[0].trim();
            String note = parts.length >= 3 ? parts[2].trim() : "";
            try {
                long amount = Long.parseLong(parts[1].trim());
                if (amount <= 0) throw new NumberFormatException();
                send(new BankAdminRequestPayload(nextId(), BankAdminAction.TREASURY_TRANSFER, TREASURY_UUID, amount, target + "|" + note));
                setStatus("\u5df2\u53d1\u9001\u56fd\u5e93\u8f6c\u8d26\u8bf7\u6c42");
            } catch (NumberFormatException e) { setStatus("\u65e0\u6548\u91d1\u989d"); }
        }, 160));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (page == Page.ACCOUNT_LIST && button == 0) {
            int tl = panelLeft() + CONTENT_PAD, tw = panelWidth() - CONTENT_PAD * 2;
            int hdrY = panelTop() + 126;
            int rowsPerPage = Math.max(1, (panelTop() + panelHeight() - 38 - hdrY - TABLE_HEADER_HEIGHT) / ROW_HEIGHT);
            int from = currentPage * rowsPerPage;
            int tabY = panelTop() + 40, tabW = 100;
            if (my >= tabY && my <= tabY + 20) {
                if (mx >= tl && mx <= tl + tabW) { listType = BankAccountType.PERSONAL; selected.clear(); currentPage = 0; rebuildWidgets(); return true; }
                if (mx >= tl + tabW + 4 && mx <= tl + tabW * 2 + 4) { listType = BankAccountType.ENTERPRISE; selected.clear(); currentPage = 0; rebuildWidgets(); return true; }
            }
            if (my >= hdrY + TABLE_HEADER_HEIGHT) {
                int rowIdx = (int) ((my - hdrY - TABLE_HEADER_HEIGHT) / ROW_HEIGHT) + from;
                List<BankAccountView> rows = filteredSorted();
                if (rowIdx >= 0 && rowIdx < rows.size()) {
                    int[] cx = colX(tl, tw);
                    if (mx >= cx[0] && mx <= cx[0] + 24) {
                        UUID id = rows.get(rowIdx).owner();
                        if (!selected.remove(id)) selected.add(id);
                        rebuildWidgets(); return true;
                    }
                }
            }
            if (my >= hdrY && my <= hdrY + TABLE_HEADER_HEIGHT) {
                int[] cx = colX(tl, tw);
                BankAccountSortField[] fields = listType == BankAccountType.ENTERPRISE
                        ? new BankAccountSortField[]{null, BankAccountSortField.NAME, null, null, BankAccountSortField.CREATED, BankAccountSortField.LAST_ACTIVE, BankAccountSortField.BALANCE, null, null}
                        : new BankAccountSortField[]{null, BankAccountSortField.NAME, null, BankAccountSortField.CREATED, BankAccountSortField.LAST_ACTIVE, BankAccountSortField.BALANCE, null, null};
                for (int i = 0; i < cx.length && i < fields.length; i++) {
                    if (fields[i] != null && mx >= cx[i] && mx <= cx[i] + colW(i)) {
                        if (sortField == fields[i]) sortOrder = sortOrder == SortOrder.DESC ? SortOrder.ASC : SortOrder.DESC;
                        else { sortField = fields[i]; sortOrder = SortOrder.DESC; }
                        rebuildWidgets(); return true;
                    }
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void apply(BankAdminSnapshotPayload p) { allAccounts = p.accounts(); treasuryBalance = p.treasuryBalance(); treasuryTransactions = p.treasuryTransactions(); if (!p.messageKey().isEmpty()) setStatus(Component.translatable(p.messageKey())); }
    private int[] colX(int tl, int tw) { int[] w = colWidths(tw); int[] x = new int[w.length]; x[0] = tl; for (int i = 1; i < x.length; i++) x[i] = x[i-1] + w[i-1]; return x; }
    private int colW(int idx) { int tw = panelWidth() - CONTENT_PAD * 2; int[] w = colWidths(tw); return idx < w.length ? w[idx] : 60; }
    private int[] colWidths(int tw) {
        if (listType == BankAccountType.PERSONAL) {
            int fixed = 30 + 140 + 190 + 130 + 130 + 110 + 70;
            return new int[]{30, 140, 190, 130, 130, 110, 70, Math.max(120, tw - fixed)};
        }
        int fixed = 30 + 150 + 120 + 180 + 150 + 150 + 120 + 70;
        return new int[]{30, 150, 120, 180, 150, 150, 120, 70, Math.max(120, tw - fixed)};
    }
    private void cycleSortField() { sortField = switch (sortField) { case NAME -> BankAccountSortField.BALANCE; case BALANCE -> BankAccountSortField.CREATED; case CREATED -> BankAccountSortField.LAST_ACTIVE; case LAST_ACTIVE -> BankAccountSortField.NAME; }; rebuildWidgets(); }
    private Component sortLabel() { return Component.translatable("screen.yumesumi_finance.admin.sort." + sortField.name().toLowerCase(Locale.ROOT)); }
    private Component orderLabel() { return Component.translatable(sortOrder == SortOrder.DESC ? "screen.yumesumi_finance.admin.desc" : "screen.yumesumi_finance.admin.asc"); }
    private Component orderLabelFor(SortOrder order) { return Component.translatable(order == SortOrder.DESC ? "screen.yumesumi_finance.admin.desc" : "screen.yumesumi_finance.admin.asc"); }
    private String formatTime(long ms) { if (ms <= 0) return "-"; return TIME_FMT.format(Instant.ofEpochMilli(ms)); }
    private String fitText(String text, int maxW) { if (font.width(text) <= maxW) return text; String s = text; while (!s.isEmpty() && font.width(s + "...") > maxW) s = s.substring(0, s.length() - 1); return s + "..."; }
    private void send(BankAdminRequestPayload p) { PacketDistributor.sendToServer(p); }
    private long nextId() { requestCounter++; if (requestCounter <= 0) requestCounter = 1; return requestCounter; }
    private void setStatus(String msg) { setStatus(Component.literal(msg)); }
    private void setStatus(Component msg) { statusMessage = msg == null ? Component.empty() : msg; statusTimer = 80; }
    private void clearStatus() { statusMessage = Component.empty(); statusTimer = 0; }
    private int panelLeft() { return (width - panelWidth()) / 2; }
    private int panelTop() { return (height - panelHeight()) / 2; }
    private int panelWidth() { return Math.min(1100, Math.max(1, width - PANEL_MARGIN * 2)); }
    private int panelHeight() { return Math.min(720, Math.max(1, height - PANEL_MARGIN * 2)); }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && page != Page.MAIN) {
            clearStatus();
            page = Page.MAIN;
            
            rebuildWidgets();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static class InputScreen extends Screen {
        private final Screen parent;
        private final String prompt;
        private final String initial;
        private final int maxLength;
        private EditBox inputBox;
        private final java.util.function.Consumer<String> onConfirm;

        InputScreen(Screen parent, String prompt, String initial, java.util.function.Consumer<String> onConfirm) {
            this(parent, prompt, initial, onConfirm, 64);
        }

        InputScreen(Screen parent, String prompt, String initial, java.util.function.Consumer<String> onConfirm, int maxLength) {
            super(Component.literal(prompt)); this.parent = parent; this.prompt = prompt; this.initial = initial == null ? "" : initial; this.onConfirm = onConfirm; this.maxLength = Math.max(1, maxLength);
        }
        @Override protected void init() {
            int w = Math.min(360, width - 80); int x = (width - w) / 2; int y = height / 2 - 40;
            inputBox = new EditBox(font, x, y + 24, w, 20, Component.empty()); inputBox.setMaxLength(maxLength); inputBox.setValue(initial); addRenderableWidget(inputBox);
            addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.confirm"), b -> { onConfirm.accept(inputBox.getValue()); minecraft.setScreen(parent); }).bounds(x, y + 52, 60, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.cancel"), b -> minecraft.setScreen(parent)).bounds(x + 66, y + 52, 60, 20).build());
        }
        @Override public void render(GuiGraphics g, int mx, int my, float pt) {
            renderBackground(g, mx, my, pt);
            int w = Math.min(360, width - 80); int x = (width - w) / 2; int y = height / 2 - 40;
            g.drawCenteredString(font, prompt, width / 2, y, C_ACCENT); super.render(g, mx, my, pt);
        }
        @Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) { g.fill(0, 0, width, height, SCREEN_BG); }
    }
}
