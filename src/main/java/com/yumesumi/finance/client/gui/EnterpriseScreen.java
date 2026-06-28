package com.yumesumi.finance.client.gui;

import com.yumesumi.finance.bank.enterprise.EnterpriseAction;
import com.yumesumi.finance.bank.enterprise.EnterpriseRole;
import com.yumesumi.finance.bank.enterprise.EnterpriseScreenMode;
import com.yumesumi.finance.client.gui.foundation.FinanceLineChart;
import com.yumesumi.finance.client.gui.foundation.FinanceRender;
import com.yumesumi.finance.client.gui.foundation.FinanceTheme;
import com.yumesumi.finance.money.MoneyFormatter;
import com.yumesumi.finance.network.payload.EnterpriseDetailView;
import com.yumesumi.finance.network.payload.EnterpriseMemberView;
import com.yumesumi.finance.network.payload.EnterpriseRequestPayload;
import com.yumesumi.finance.network.payload.EnterpriseSnapshotPayload;
import com.yumesumi.finance.network.payload.EnterpriseSummaryView;
import com.yumesumi.finance.network.payload.EnterpriseTransactionView;
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
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public class EnterpriseScreen extends Screen {
    private enum DetailTab { OVERVIEW, MEMBERS, LEDGER, SETTINGS }
    private enum MemberSort { NAME, ROLE, JOINED, LAST_ACTIVE }
    private enum EnterpriseListSort { NAME, CODE, BALANCE, ROLE, MEMBERS }
    private enum LedgerSort { TIME, AMOUNT, BALANCE, OPERATOR }
    private enum SortOrder { ASC, DESC }

    private static final Pattern AMOUNT_PATTERN = Pattern.compile("\\d+(\\.\\d{1,2})?");
    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z0-9]{3,4}");
    private static final int SCREEN_BG = FinanceTheme.BACKGROUND;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private EnterpriseScreenMode mode;
    private List<EnterpriseSummaryView> enterprises;
    private EnterpriseDetailView detail;
    private String checkedCode;
    private boolean codeAvailable;
    private Component message;
    private String messageKey = "";
    private boolean success;
    private DetailTab tab = DetailTab.OVERVIEW;
    private EditBox nameBox;
    private EditBox codeBox;
    private EditBox descriptionBox;
    private EditBox targetBox;
    private EditBox amountBox;
    private EditBox noteBox;
    private EditBox listSearchBox;
    private EditBox memberSearchBox;
    private EditBox ledgerSearchBox;
    private boolean targetEnterprise = true;
    private final Set<UUID> selectedMembers = new HashSet<>();
    private String listSearch = "";
    private String memberSearch = "";
    private String ledgerSearch = "";
    private EnterpriseListSort listSort = EnterpriseListSort.NAME;
    private SortOrder listOrder = SortOrder.ASC;
    private MemberSort memberSort = MemberSort.NAME;
    private SortOrder memberOrder = SortOrder.ASC;
    private LedgerSort ledgerSort = LedgerSort.TIME;
    private SortOrder ledgerOrder = SortOrder.DESC;
    private String registerNameText = "";
    private String registerCodeText = "";
    private String registerDescriptionText = "";
    private String enterpriseTransferTargetText = "";
    private String enterpriseTransferAmountText = "";
    private String enterpriseTransferNoteText = "";
    private String settingsNameText = "";
    private String settingsCodeText = "";
    private String settingsDescriptionText = "";
    private UUID settingsDraftEnterpriseId;
    private boolean settingsDraftDirty;
    private int listPage;
    private int ledgerPage;
    private int memberPage;
    private long requestCounter = System.nanoTime() & Long.MAX_VALUE;
    private int disbandCountdown = -1;
    private boolean disbandReady;

    public EnterpriseScreen(EnterpriseSnapshotPayload payload) {
        super(Component.translatable("screen.yumesumi_finance.enterprise.title"));
        apply(payload);
    }

    public void update(EnterpriseSnapshotPayload payload) {
        syncDrafts();
        apply(payload);
        rebuildWidgets();
    }

    @Override
    protected void init() {
        rebuildWidgets();
    }

    @Override
    public void tick() {
        if (disbandCountdown > 0) {
            disbandCountdown--;
            if (disbandCountdown == 0) {
                disbandReady = true;
                rebuildWidgets();
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        int l = left(), t = top(), w = panelWidth(), h = panelHeight();
        g.drawString(font, title, l + 14, t + 12, FinanceTheme.GOLD, true);
        g.drawString(font, message, l + w - 14 - font.width(message), t + 12, success ? FinanceTheme.GREEN : FinanceTheme.RED, true);
        switch (mode) {
            case REGISTER -> renderRegister(g, l, t, w, h);
            case LIST -> renderList(g, l, t, w, h, mx, my);
            case DETAIL -> renderDetail(g, l, t, w, h, mx, my);
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, SCREEN_BG);
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        nameBox = null;
        codeBox = null;
        descriptionBox = null;
        targetBox = null;
        amountBox = null;
        noteBox = null;
        listSearchBox = null;
        memberSearchBox = null;
        ledgerSearchBox = null;
        switch (mode) {
            case REGISTER -> buildRegister();
            case LIST -> buildList();
            case DETAIL -> buildDetail();
        }
    }

    private void renderRegister(GuiGraphics g, int l, int t, int w, int h) {
        int x = l + 34;
        int y = t + 48;
        g.drawString(font, Component.translatable("screen.yumesumi_finance.enterprise.register_title"), x, y, FinanceTheme.GOLD, true);
        g.drawString(font, Component.translatable("screen.yumesumi_finance.enterprise.register_fee", MoneyFormatter.format(100_000L)), x, y + 16, FinanceTheme.TEXT_DIM, true);
        g.drawString(font, Component.translatable("screen.yumesumi_finance.enterprise.name"), x, y + 60, FinanceTheme.TEXT_MUTED, true);
        g.drawString(font, Component.translatable("screen.yumesumi_finance.enterprise.code"), x, y + 90, FinanceTheme.TEXT_MUTED, true);
        g.drawString(font, Component.translatable("screen.yumesumi_finance.enterprise.code_rule"), x + 240, y + 90, FinanceTheme.TEXT_MUTED, true);
        g.drawString(font, Component.translatable("screen.yumesumi_finance.enterprise.description"), x, y + 120, FinanceTheme.TEXT_MUTED, true);
        if (!checkedCode.isBlank()) {
            g.drawString(font, Component.translatable(codeAvailable ? "screen.yumesumi_finance.enterprise.code_ok" : "screen.yumesumi_finance.enterprise.code_bad", checkedCode),
                    x, y + 34, codeAvailable ? FinanceTheme.GREEN : FinanceTheme.RED, true);
        }
    }

    private void buildRegister() {
        int l = left(), t = top();
        int x = l + 34;
        int y = t + 104;
        nameBox = new EditBox(font, x + 72, y, 180, 20, Component.empty());
        nameBox.setHint(Component.translatable("screen.yumesumi_finance.enterprise.name"));
        nameBox.setMaxLength(32);
        nameBox.setValue(registerNameText);
        nameBox.setResponder(v -> registerNameText = v);
        addRenderableWidget(nameBox);
        codeBox = new EditBox(font, x + 72, y + 30, 80, 20, Component.empty());
        codeBox.setHint(Component.translatable("screen.yumesumi_finance.enterprise.code"));
        codeBox.setMaxLength(4);
        codeBox.setValue(registerCodeText);
        codeBox.setResponder(v -> registerCodeText = v.toUpperCase(Locale.ROOT));
        addRenderableWidget(codeBox);
        descriptionBox = new EditBox(font, x + 72, y + 60, 260, 20, Component.empty());
        descriptionBox.setHint(Component.translatable("screen.yumesumi_finance.enterprise.description"));
        descriptionBox.setMaxLength(64);
        descriptionBox.setValue(registerDescriptionText);
        descriptionBox.setResponder(v -> registerDescriptionText = v);
        addRenderableWidget(descriptionBox);
        addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.enterprise.check_code"), b -> checkCode()).bounds(x + 160, y + 30, 72, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.enterprise.create"), b -> confirmCreate()).bounds(x + 72, y + 94, 84, 22).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.back"), b -> send(EnterpriseRequestPayload.simple(nextId(), EnterpriseAction.OPEN_LIST))).bounds(x + 164, y + 94, 60, 22).build());
    }

    private void buildList() {
        int l = left(), t = top(), w = panelWidth(), by = t + panelHeight() - 34;
        int x = l + 24;
        int y = t + 72;
        int searchW = Math.min(230, Math.max(120, w - 520));
        listSearchBox = new EditBox(font, x, y, searchW, 20, Component.empty());
        listSearchBox.setHint(Component.translatable("screen.yumesumi_finance.admin.enterprise_search_hint"));
        listSearchBox.setValue(listSearch);
        listSearchBox.setResponder(v -> { listSearch = v; listPage = 0; });
        addRenderableWidget(listSearchBox);
        addRenderableWidget(Button.builder(listSortLabel(), b -> cycleListSort()).bounds(x + searchW + 8, y, 72, 20).build());
        addRenderableWidget(Button.builder(orderLabel(listOrder), b -> { listOrder = listOrder == SortOrder.ASC ? SortOrder.DESC : SortOrder.ASC; listPage = 0; rebuildWidgets(); }).bounds(x + searchW + 86, y, 54, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.enterprise.personal_bank"),
                b -> send(EnterpriseRequestPayload.simple(nextId(), EnterpriseAction.OPEN_PERSONAL))).bounds(l + w - 134, t + 42, 112, 22).build());
        int rows = Math.max(1, (panelHeight() - 164) / FinanceTheme.ROW_H);
        int pages = Math.max(1, (filteredEnterprises().size() + rows - 1) / rows);
        listPage = Math.min(listPage, pages - 1);
        Button prev = Button.builder(Component.literal("<"), b -> { listPage = Math.max(0, listPage - 1); rebuildWidgets(); }).bounds(l + 24, by, 24, 18).build();
        prev.active = listPage > 0;
        addRenderableWidget(prev);
        Button next = Button.builder(Component.literal(">"), b -> { listPage = Math.min(pages - 1, listPage + 1); rebuildWidgets(); }).bounds(l + 52, by, 24, 18).build();
        next.active = listPage < pages - 1;
        addRenderableWidget(next);
    }

    private void renderList(GuiGraphics g, int l, int t, int w, int h, int mx, int my) {
        g.drawString(font, Component.translatable("screen.yumesumi_finance.enterprise.my_accounts"), l + 24, t + 44, FinanceTheme.GOLD, true);
        List<EnterpriseSummaryView> rowsData = filteredEnterprises();
        int tableX = l + 24;
        int tableY = t + 108;
        int tableW = w - 48;
        int rows = Math.max(1, (h - 164) / FinanceTheme.ROW_H);
        int totalPages = Math.max(1, (rowsData.size() + rows - 1) / rows);
        listPage = Math.min(listPage, totalPages - 1);
        int from = Math.min(listPage * rows, rowsData.size());
        int to = Math.min(from + rows, rowsData.size());
        g.drawString(font, "企业账户：" + rowsData.size(), tableX, tableY - 14, FinanceTheme.TEXT, true);
        if (rowsData.isEmpty()) {
            g.drawCenteredString(font, "需要创建或加入一个企业", l + w / 2, t + h / 2, FinanceTheme.TEXT_MUTED);
        }
        g.drawString(font, "企业名称", tableX + 8, tableY + 7, FinanceTheme.TEXT_DIM, true);
        g.drawString(font, "代码", tableX + 190, tableY + 7, FinanceTheme.TEXT_DIM, true);
        g.drawString(font, Component.translatable("screen.yumesumi_finance.balance"), tableX + 250, tableY + 7, FinanceTheme.TEXT_DIM, true);
        g.drawString(font, "最后活跃", tableX + 390, tableY + 7, FinanceTheme.TEXT_DIM, true);
        g.drawString(font, "成员", tableX + 470, tableY + 7, FinanceTheme.TEXT_DIM, true);
        for (int i = from; i < to; i++) {
            EnterpriseSummaryView view = rowsData.get(i);
            int y = tableY + 22 + (i - from) * FinanceTheme.ROW_H;
            g.drawString(font, FinanceRender.fit(font, view.name(), 170), tableX + 8, y + 6, FinanceTheme.TEXT, true);
            g.drawString(font, view.code(), tableX + 190, y + 6, FinanceTheme.BLUE, true);
            g.drawString(font, MoneyFormatter.format(view.balanceCents()), tableX + 250, y + 6, FinanceTheme.GOLD, true);
            g.drawString(font, roleName(view.myRole()), tableX + 390, y + 6, FinanceTheme.TEXT_DIM, true);
            g.drawString(font, String.valueOf(view.memberCount()), tableX + 470, y + 6, FinanceTheme.TEXT_DIM, true);
            drawSeparator(g, tableX, tableX + tableW, y + FinanceTheme.ROW_H - 1);
        }
        g.drawCenteredString(font, (listPage + 1) + "/" + totalPages + "  " + rowsData.size(), l + w / 2, t + h - 48, FinanceTheme.TEXT_MUTED);
    }

    private void buildDetail() {
        if (detail == null) return;
        int l = left(), t = top(), w = panelWidth();
        int tabY = t + 38;
        int tx = l + 16;
        for (DetailTab detailTab : DetailTab.values()) {
            int tabW = 58;
            addRenderableWidget(Button.builder(tabLabel(detailTab), b -> { clearStatus(); tab = detailTab; rebuildWidgets(); }).bounds(tx, tabY, tabW, 20).build());
            tx += tabW + 4;
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.back"), b -> send(EnterpriseRequestPayload.simple(nextId(), EnterpriseAction.OPEN_LIST))).bounds(l + w - 72, tabY, 54, 20).build());
        switch (tab) {
            case OVERVIEW -> buildOverview();
            case MEMBERS -> buildMembers();
            case LEDGER -> buildLedger();
            case SETTINGS -> buildSettings();
        }
    }

    private void renderDetail(GuiGraphics g, int l, int t, int w, int h, int mx, int my) {
        if (detail == null) return;
        g.drawString(font, detail.name() + "  [" + detail.code() + "]", l + 24, t + 66, FinanceTheme.GOLD, true);
        g.drawString(font, detail.description(), l + 24, t + 80, FinanceTheme.TEXT_MUTED, true);
        switch (tab) {
            case OVERVIEW -> renderOverview(g, l, t, w, h, mx, my);
            case MEMBERS -> renderMembers(g, l, t, w, h, mx, my);
            case LEDGER -> renderLedger(g, l, t, w, h, mx, my);
            case SETTINGS -> renderSettings(g, l, t, w, h);
        }
    }

    private void buildOverview() {
        if (detail == null || !detail.myRole().canManageMoney()) return;
        int l = left(), t = top();
        int y = t + 210;
        addRenderableWidget(Button.builder(Component.translatable(targetEnterprise ? "screen.yumesumi_finance.enterprise.target_enterprise" : "screen.yumesumi_finance.enterprise.target_personal"),
                b -> {
                    syncEnterpriseTransferDraft();
                    targetEnterprise = !targetEnterprise;
                    enterpriseTransferTargetText = "";
                    rebuildWidgets();
                }).bounds(l + 28, y, 96, 20).build());
        targetBox = new EditBox(font, l + 132, y, 116, 20, Component.empty());
        targetBox.setHint(Component.translatable(targetEnterprise ? "screen.yumesumi_finance.enterprise.code" : "screen.yumesumi_finance.transfer_target_hint"));
        targetBox.setValue(enterpriseTransferTargetText);
        targetBox.setResponder(v -> enterpriseTransferTargetText = v);
        addRenderableWidget(targetBox);
        amountBox = new EditBox(font, l + 256, y, 84, 20, Component.empty());
        amountBox.setHint(Component.translatable("screen.yumesumi_finance.amount"));
        amountBox.setValue(enterpriseTransferAmountText);
        amountBox.setResponder(v -> enterpriseTransferAmountText = v);
        addRenderableWidget(amountBox);
        noteBox = new EditBox(font, l + 348, y, 110, 20, Component.empty());
        noteBox.setHint(Component.translatable("screen.yumesumi_finance.transfer_note"));
        noteBox.setMaxLength(64);
        noteBox.setValue(enterpriseTransferNoteText);
        noteBox.setResponder(v -> enterpriseTransferNoteText = v);
        addRenderableWidget(noteBox);
        addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.transfer_submit"), b -> confirmEnterpriseTransfer()).bounds(l + 466, y, 80, 20).build());
    }

    private void renderOverview(GuiGraphics g, int l, int t, int w, int h, int mx, int my) {
        int x = l + 24;
        int y = t + 104;
        FinanceRender.labelValue(g, font, Component.translatable("screen.yumesumi_finance.balance"), MoneyFormatter.format(detail.balanceCents()), x + 12, y + 12, FinanceTheme.GOLD);
        FinanceRender.labelValue(g, font, Component.translatable("screen.yumesumi_finance.enterprise.owner"), detail.ownerName(), x + 12, y + 30, FinanceTheme.TEXT);
        if (mx >= x + 78 && mx <= x + 210 && my >= y + 26 && my <= y + 40) {
            g.renderTooltip(font, Component.literal(detail.ownerId().toString()), mx, my);
        }
        FinanceRender.labelValue(g, font, Component.translatable("screen.yumesumi_finance.enterprise.my_role"), roleName(detail.myRole()), x + 12, y + 48, FinanceTheme.TEXT_DIM);
        FinanceRender.labelValue(g, font, Component.translatable("screen.yumesumi_finance.enterprise.members"), detail.memberCount() + "/" + detail.maxMembers(), x + 12, y + 66, FinanceTheme.TEXT_DIM);
        List<Long> series = detail.balanceSeries().isEmpty() ? List.of(detail.balanceCents()) : detail.balanceSeries();
        FinanceLineChart.draw(g, font, x + 250, y, Math.max(250, w - 310), 116, series, FinanceTheme.GOLD);
        if (detail.myRole().canManageMoney()) {
            g.drawString(font, Component.translatable("screen.yumesumi_finance.enterprise.transfer_from"), x + 4, t + 194, FinanceTheme.TEXT_DIM, true);
        }
    }

    private void buildMembers() {
        if (detail == null) return;
        int l = left(), t = top(), w = panelWidth(), by = t + panelHeight() - 34;
        memberSearchBox = new EditBox(font, l + 24, t + 98, Math.min(180, w - 80), 20, Component.empty());
        memberSearchBox.setHint(Component.translatable("screen.yumesumi_finance.search"));
        memberSearchBox.setValue(memberSearch);
        memberSearchBox.setResponder(v -> { memberSearch = v; memberPage = 0; });
        addRenderableWidget(memberSearchBox);
        addRenderableWidget(Button.builder(memberSortLabel(), b -> { cycleMemberSort(); }).bounds(l + 210, t + 98, 72, 20).build());
        addRenderableWidget(Button.builder(orderLabel(memberOrder), b -> { memberOrder = memberOrder == SortOrder.ASC ? SortOrder.DESC : SortOrder.ASC; memberPage = 0; rebuildWidgets(); }).bounds(l + 288, t + 98, 54, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.select_all"), b -> selectVisibleMembers()).bounds(l + 348, t + 98, 54, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.invert"), b -> invertVisibleMembers()).bounds(l + 408, t + 98, 54, 20).build());
        int rows = Math.max(1, (panelHeight() - 192) / FinanceTheme.ROW_H);
        int pages = Math.max(1, (filteredMembers().size() + rows - 1) / rows);
        memberPage = Math.min(memberPage, pages - 1);
        Button prev = Button.builder(Component.literal("<"), b -> { memberPage = Math.max(0, memberPage - 1); rebuildWidgets(); }).bounds(l + w / 2 - 30, by, 24, 18).build();
        prev.active = memberPage > 0;
        addRenderableWidget(prev);
        Button next = Button.builder(Component.literal(">"), b -> { memberPage = Math.min(pages - 1, memberPage + 1); rebuildWidgets(); }).bounds(l + w / 2, by, 24, 18).build();
        next.active = memberPage < pages - 1;
        addRenderableWidget(next);
        if (detail.myRole().canInvite()) {
            addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.enterprise.invite"), b -> openInput("邀请玩家", "", value -> send(new EnterpriseRequestPayload(nextId(), EnterpriseAction.INVITE_MEMBER, detail.id(), null, "", "", "", value, 0, "", false, false, EnterpriseRole.MEMBER))))
                    .bounds(l + 24, by, 72, 20).build());
        }
        if (detail.myRole().canManageRoles()) {
            addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.enterprise.toggle_admin"), b -> setSelectedRole(EnterpriseRole.ADMIN)).bounds(l + 104, by, 78, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.enterprise.remove_member"), b -> removeSelected()).bounds(l + 188, by, 78, 20).build());
        }
        if (detail.myRole().canManageMoney()) {
            addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.transfer"), b -> batchPaySelected()).bounds(w + l - 110, by, 86, 20).build());
        }
    }

    private void renderMembers(GuiGraphics g, int l, int t, int w, int h, int mx, int my) {
        List<EnterpriseMemberView> members = filteredMembers();
        int tableX = l + 24, tableY = t + 138, tableW = w - 48;
        int rows = Math.max(1, (h - 192) / FinanceTheme.ROW_H);
        int totalPages = Math.max(1, (members.size() + rows - 1) / rows);
        memberPage = Math.min(memberPage, totalPages - 1);
        int from = Math.min(memberPage * rows, members.size());
        int to = Math.min(from + rows, members.size());
        g.drawString(font, "成员数量：" + members.size(), tableX, tableY - 14, FinanceTheme.TEXT, true);
        String selectedText = selectedMembers.size() + "/" + members.size();
        g.drawString(font, selectedText, tableX + tableW - font.width(selectedText), tableY - 14, FinanceTheme.TEXT_MUTED, true);
        if (members.isEmpty()) {
            g.drawString(font, "没有符合筛选条件的成员", tableX, tableY + 32, FinanceTheme.TEXT_MUTED, true);
        }
        g.drawString(font, "", tableX + 8, tableY + 7, FinanceTheme.TEXT_DIM, true);
        g.drawString(font, "成员", tableX + 30, tableY + 7, FinanceTheme.TEXT_DIM, true);
        g.drawString(font, "角色", tableX + 200, tableY + 7, FinanceTheme.TEXT_DIM, true);
        g.drawString(font, "加入时间", tableX + 280, tableY + 7, FinanceTheme.TEXT_DIM, true);
        g.drawString(font, "最后活跃", tableX + 390, tableY + 7, FinanceTheme.TEXT_DIM, true);
        for (int i = from; i < to; i++) {
            EnterpriseMemberView member = members.get(i);
            int y = tableY + 22 + (i - from) * FinanceTheme.ROW_H;
            boolean selected = selectedMembers.contains(member.playerId());
            g.drawString(font, selected ? "[x]" : "[ ]", tableX + 8, y + 6, FinanceTheme.TEXT, true);
            g.drawString(font, FinanceRender.fit(font, member.playerName(), 160), tableX + 30, y + 6, FinanceTheme.TEXT, true);
            g.drawString(font, roleName(member.role()), tableX + 200, y + 6, FinanceTheme.TEXT_DIM, true);
            g.drawString(font, formatTime(member.joinedAt()), tableX + 280, y + 6, FinanceTheme.TEXT_MUTED, true);
            g.drawString(font, formatTime(member.lastActiveAt()), tableX + 390, y + 6, FinanceTheme.TEXT_MUTED, true);
            drawSeparator(g, tableX, tableX + tableW, y + FinanceTheme.ROW_H - 1);
            if (mx >= tableX + 30 && mx <= tableX + 190 && my >= y && my <= y + FinanceTheme.ROW_H) {
                g.renderTooltip(font, Component.literal(member.playerId().toString()), mx, my);
            }
        }
        g.drawCenteredString(font, (memberPage + 1) + "/" + totalPages, l + w / 2, t + h - 48, FinanceTheme.TEXT_MUTED);
    }

    private void buildLedger() {
        if (detail == null) return;
        int l = left(), t = top(), w = panelWidth(), by = t + panelHeight() - 34;
        ledgerSearchBox = new EditBox(font, l + 24, t + 98, Math.min(190, w - 80), 20, Component.empty());
        ledgerSearchBox.setHint(Component.translatable("screen.yumesumi_finance.enterprise.ledger_search"));
        ledgerSearchBox.setValue(ledgerSearch);
        ledgerSearchBox.setResponder(v -> { ledgerSearch = v; ledgerPage = 0; });
        addRenderableWidget(ledgerSearchBox);
        addRenderableWidget(Button.builder(ledgerSortLabel(), b -> cycleLedgerSort()).bounds(l + 222, t + 98, 74, 20).build());
        addRenderableWidget(Button.builder(orderLabel(ledgerOrder), b -> { ledgerOrder = ledgerOrder == SortOrder.ASC ? SortOrder.DESC : SortOrder.ASC; ledgerPage = 0; rebuildWidgets(); }).bounds(l + 302, t + 98, 54, 20).build());
        int rows = Math.max(1, (panelHeight() - 184) / 36);
        int pages = Math.max(1, (filteredLedgerTransactions().size() + rows - 1) / rows);
        ledgerPage = Math.min(ledgerPage, pages - 1);
        Button prev = Button.builder(Component.literal("<"), b -> { ledgerPage = Math.max(0, ledgerPage - 1); rebuildWidgets(); }).bounds(l + 24, by, 24, 18).build();
        prev.active = ledgerPage > 0;
        addRenderableWidget(prev);
        Button next = Button.builder(Component.literal(">"), b -> { ledgerPage = Math.min(pages - 1, ledgerPage + 1); rebuildWidgets(); }).bounds(l + 52, by, 24, 18).build();
        next.active = ledgerPage < pages - 1;
        addRenderableWidget(next);
    }

    private void renderLedger(GuiGraphics g, int l, int t, int w, int h, int mx, int my) {
        List<EnterpriseTransactionView> rowsData = filteredLedgerTransactions();
        int tableX = l + 24, tableY = t + 132, tableW = w - 48;
        int rowH = 36;
        int rows = Math.max(1, (h - 184) / rowH);
        int totalPages = Math.max(1, (rowsData.size() + rows - 1) / rows);
        ledgerPage = Math.min(ledgerPage, totalPages - 1);
        int from = Math.min(ledgerPage * rows, rowsData.size());
        int to = Math.min(from + rows, rowsData.size());
        if (rowsData.isEmpty()) {
            g.drawString(font, "暂无流水", tableX, tableY + 12, FinanceTheme.TEXT_MUTED, true);
        }
        for (int i = from; i < to; i++) {
            EnterpriseTransactionView tx = rowsData.get(i);
            int y = tableY + (i - from) * rowH;
            int color = tx.type().income() ? FinanceTheme.GREEN : FinanceTheme.RED;
            g.drawString(font, Component.translatable("enterprise_transaction.yumesumi_finance." + tx.type().name().toLowerCase(Locale.ROOT)), tableX + 8, y + 6, FinanceTheme.TEXT, true);
            g.drawString(font, (tx.type().income() ? "+" : "-") + MoneyFormatter.format(tx.amountCents()), tableX + 120, y + 6, color, true);
            g.drawString(font, Component.translatable("screen.yumesumi_finance.enterprise.operator", tx.operatorName()), tableX + 250, y + 6, FinanceTheme.TEXT_DIM, true);
            if (tx.operatorId() != null && mx >= tableX + 250 && mx <= tableX + 384 && my >= y && my <= y + rowH) {
                g.renderTooltip(font, Component.literal(tx.operatorId().toString()), mx, my);
            }
            g.drawString(font, Component.translatable(tx.type().income() ? "screen.yumesumi_finance.history_from_value" : "screen.yumesumi_finance.history_to_value", tx.counterpartyName()), tableX + 390, y + 6, FinanceTheme.TEXT_DIM, true);
            if (tx.counterpartyId() != null && mx >= tableX + 390 && mx <= tableX + tableW && my >= y && my <= y + rowH) {
                g.renderTooltip(font, Component.literal(tx.counterpartyId().toString()), mx, my);
            }
            g.drawString(font, formatTime(tx.createdAt()), tableX + 8, y + 20, FinanceTheme.TEXT_MUTED, true);
            g.drawString(font, "余额 " + MoneyFormatter.format(tx.balanceAfterCents()), tableX + 120, y + 20, FinanceTheme.TEXT_MUTED, true);
            g.drawString(font, FinanceRender.fit(font, tx.note(), tableW - 360), tableX + 250, y + 20, FinanceTheme.TEXT_MUTED, true);
            drawSeparator(g, tableX, tableX + tableW, y + rowH - 1);
        }
        g.drawCenteredString(font, (ledgerPage + 1) + "/" + totalPages + "  " + rowsData.size() + "/10000", l + w / 2, t + h - 48, FinanceTheme.TEXT_MUTED);
    }

    private void buildSettings() {
        if (detail == null) return;
        int l = left(), t = top();
        int x = l + 100, y = t + 120;
        boolean useDraft = settingsDraftDirty && detail.id().equals(settingsDraftEnterpriseId);
        nameBox = new EditBox(font, x, y, 180, 20, Component.empty());
        nameBox.setValue(useDraft ? settingsNameText : detail.name());
        nameBox.setResponder(value -> {
            settingsNameText = value;
            settingsDraftEnterpriseId = detail.id();
            settingsDraftDirty = true;
        });
        addRenderableWidget(nameBox);
        codeBox = new EditBox(font, x, y + 30, 80, 20, Component.empty());
        codeBox.setValue(useDraft ? settingsCodeText : detail.code());
        codeBox.setMaxLength(4);
        codeBox.setResponder(value -> {
            settingsCodeText = value.toUpperCase(Locale.ROOT);
            settingsDraftEnterpriseId = detail.id();
            settingsDraftDirty = true;
        });
        addRenderableWidget(codeBox);
        descriptionBox = new EditBox(font, x, y + 60, 260, 20, Component.empty());
        descriptionBox.setValue(useDraft ? settingsDescriptionText : detail.description());
        descriptionBox.setMaxLength(64);
        descriptionBox.setResponder(value -> {
            settingsDescriptionText = value;
            settingsDraftEnterpriseId = detail.id();
            settingsDraftDirty = true;
        });
        addRenderableWidget(descriptionBox);
        boolean canManage = detail.myRole().canManageRoles();
        Button save = Button.builder(Component.translatable("screen.yumesumi_finance.enterprise.save_settings"), b -> confirmSettings()).bounds(x, y + 94, 90, 20).build();
        save.active = canManage;
        addRenderableWidget(save);
        Button disband = Button.builder(Component.translatable(disbandReady ? "screen.yumesumi_finance.enterprise.disband_final" : "screen.yumesumi_finance.enterprise.disband"), b -> confirmDisband()).bounds(x + 100, y + 94, 90, 20).build();
        disband.active = canManage && disbandCountdown <= 0;
        addRenderableWidget(disband);
    }

    private void renderSettings(GuiGraphics g, int l, int t, int w, int h) {
        int x = l + 24, y = t + 124;
        g.drawString(font, "企业名称", x, y, FinanceTheme.TEXT_DIM, true);
        g.drawString(font, "企业代码", x, y + 30, FinanceTheme.TEXT_DIM, true);
        g.drawString(font, "企业简介", x, y + 60, FinanceTheme.TEXT_DIM, true);
        if (disbandCountdown > 0) {
            g.drawString(font, Component.translatable("screen.yumesumi_finance.enterprise.disband_countdown", disbandCountdown / 20 + 1), x, y + 128, FinanceTheme.RED, true);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && mode == EnterpriseScreenMode.LIST) {
            int tableX = left() + 24, rowStart = top() + 130, tableW = panelWidth() - 48;
            int row = (int) ((my - rowStart) / FinanceTheme.ROW_H);
            if (mx >= tableX && mx <= tableX + tableW && row >= 0) {
                int rows = Math.max(1, (panelHeight() - 164) / FinanceTheme.ROW_H);
                int index = listPage * rows + row;
                List<EnterpriseSummaryView> visible = filteredEnterprises();
                if (index >= 0 && index < visible.size()) {
                    send(new EnterpriseRequestPayload(nextId(), EnterpriseAction.OPEN_DETAIL, visible.get(index).id(), null, "", "", "", "", 0, "", false, false, EnterpriseRole.MEMBER));
                    return true;
                }
            }
        }
        if (button == 0 && mode == EnterpriseScreenMode.DETAIL && tab == DetailTab.MEMBERS && detail != null) {
            int tableX = left() + 24, rowStart = top() + 160, tableW = panelWidth() - 48;
            int row = (int) ((my - rowStart) / FinanceTheme.ROW_H);
            if (mx >= tableX && mx <= tableX + tableW && row >= 0) {
                int rows = Math.max(1, (panelHeight() - 192) / FinanceTheme.ROW_H);
                int index = memberPage * rows + row;
                List<EnterpriseMemberView> members = filteredMembers();
                if (index >= 0 && index < members.size()) {
                    UUID id = members.get(index).playerId();
                    if (!selectedMembers.remove(id)) selectedMembers.add(id);
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void apply(EnterpriseSnapshotPayload payload) {
        this.mode = payload.mode();
        this.enterprises = payload.enterprises();
        this.detail = payload.detail();
        this.checkedCode = payload.checkedCode();
        this.codeAvailable = payload.codeAvailable();
        this.message = payload.message();
        this.messageKey = payload.messageKey();
        this.success = payload.success();
        if ("message.yumesumi_finance.enterprise.settings_updated".equals(messageKey)) {
            settingsDraftDirty = false;
            settingsDraftEnterpriseId = null;
        }
        selectedMembers.removeIf(id -> detail == null || detail.members().stream().noneMatch(member -> member.playerId().equals(id)));
    }

    private void checkCode() {
        syncRegisterDraft();
        String code = codeBox == null ? "" : codeBox.getValue().trim().toUpperCase();
        send(new EnterpriseRequestPayload(nextId(), EnterpriseAction.CHECK_CODE, null, null, code, "", "", "", 0, "", false, false, EnterpriseRole.MEMBER));
    }

    private void confirmCreate() {
        syncRegisterDraft();
        String code = codeBox == null ? "" : codeBox.getValue().trim().toUpperCase();
        if (!CODE_PATTERN.matcher(code).matches()) return;
        String name = nameBox == null ? "" : nameBox.getValue();
        String desc = descriptionBox == null ? "" : descriptionBox.getValue();
        minecraft.setScreen(new FinanceConfirmScreen(confirmed -> {
            minecraft.setScreen(this);
            if (confirmed) send(new EnterpriseRequestPayload(nextId(), EnterpriseAction.CREATE, null, null, code, name, desc, "", 0, "", false, true, EnterpriseRole.MEMBER));
        }, Component.translatable("screen.yumesumi_finance.enterprise.create_confirm_title"),
                Component.translatable("screen.yumesumi_finance.enterprise.create_confirm", MoneyFormatter.format(100_000L))));
    }

    private void syncRegisterDraft() {
        if (nameBox != null) registerNameText = nameBox.getValue();
        if (codeBox != null) registerCodeText = codeBox.getValue().trim().toUpperCase(Locale.ROOT);
        if (descriptionBox != null) registerDescriptionText = descriptionBox.getValue();
    }

    private void syncEnterpriseTransferDraft() {
        if (targetBox != null) enterpriseTransferTargetText = targetBox.getValue();
        if (amountBox != null && mode == EnterpriseScreenMode.DETAIL && tab == DetailTab.OVERVIEW) enterpriseTransferAmountText = amountBox.getValue();
        if (noteBox != null) enterpriseTransferNoteText = noteBox.getValue();
    }

    private void syncSettingsDraft() {
        if (detail == null || mode != EnterpriseScreenMode.DETAIL || tab != DetailTab.SETTINGS) return;
        if (nameBox != null) settingsNameText = nameBox.getValue();
        if (codeBox != null) settingsCodeText = codeBox.getValue().trim().toUpperCase(Locale.ROOT);
        if (descriptionBox != null) settingsDescriptionText = descriptionBox.getValue();
        settingsDraftEnterpriseId = detail.id();
        settingsDraftDirty = true;
    }

    private void syncDrafts() {
        syncRegisterDraft();
        syncEnterpriseTransferDraft();
        syncSettingsDraft();
    }

    private void confirmEnterpriseTransfer() {
        if (detail == null) return;
        long amount;
        try {
            amount = parseAmount(amountBox == null ? "" : amountBox.getValue());
        } catch (NumberFormatException exception) {
            return;
        }
        String target = targetBox == null ? "" : targetBox.getValue();
        String note = noteBox == null ? "" : noteBox.getValue();
        minecraft.setScreen(new FinanceConfirmScreen(confirmed -> {
            minecraft.setScreen(this);
            if (confirmed) send(new EnterpriseRequestPayload(nextId(), EnterpriseAction.TRANSFER_FROM_ENTERPRISE, detail.id(), null, "", "", "", target, amount, note, targetEnterprise, true, EnterpriseRole.MEMBER));
        }, Component.translatable("screen.yumesumi_finance.transfer_confirm_title"),
                Component.translatable("screen.yumesumi_finance.transfer_confirm", MoneyFormatter.format(amount), target)));
    }

    private void setSelectedRole(EnterpriseRole role) {
        if (detail == null || selectedMembers.isEmpty()) return;
        send(new EnterpriseRequestPayload(nextId(), EnterpriseAction.SET_MEMBER_ROLE, detail.id(), null, "", "", "", selectedMemberCsv(), 0, "", false, true, role));
    }

    private void removeSelected() {
        if (detail == null || selectedMembers.isEmpty()) return;
        minecraft.setScreen(new FinanceConfirmScreen(confirmed -> {
            minecraft.setScreen(this);
            if (confirmed) send(new EnterpriseRequestPayload(nextId(), EnterpriseAction.REMOVE_MEMBER, detail.id(), null, "", "", "", selectedMemberCsv(), 0, "", false, true, EnterpriseRole.MEMBER));
        }, Component.translatable("screen.yumesumi_finance.enterprise.remove_confirm_title"),
                Component.translatable("screen.yumesumi_finance.enterprise.remove_confirm", selectedMembers.size())));
    }

    private void batchPaySelected() {
        if (detail == null || selectedMembers.isEmpty()) return;
        minecraft.setScreen(new BatchTransferInputScreen(this, selectedMembers.size(), (amount, note) ->
                minecraft.setScreen(new FinanceConfirmScreen(confirmed -> {
                    minecraft.setScreen(this);
                    if (confirmed) send(new EnterpriseRequestPayload(nextId(), EnterpriseAction.QUICK_TRANSFER_MEMBER, detail.id(), null, "", "", "", selectedMemberCsv(), amount, note, false, true, EnterpriseRole.MEMBER));
                }, Component.translatable("screen.yumesumi_finance.transfer_confirm_title"),
                        Component.translatable("screen.yumesumi_finance.enterprise.batch_transfer_confirm", MoneyFormatter.format(amount), selectedMembers.size())))));
    }

    private void confirmSettings() {
        if (detail == null) return;
        syncSettingsDraft();
        String code = codeBox == null ? detail.code() : codeBox.getValue().trim().toUpperCase();
        if (!CODE_PATTERN.matcher(code).matches()) {
            message = Component.translatable("message.yumesumi_finance.enterprise.invalid_code");
            messageKey = "message.yumesumi_finance.enterprise.invalid_code";
            success = false;
            return;
        }
        String name = nameBox == null ? detail.name() : nameBox.getValue();
        String desc = descriptionBox == null ? detail.description() : descriptionBox.getValue();
        minecraft.setScreen(new FinanceConfirmScreen(confirmed -> {
            minecraft.setScreen(this);
            if (confirmed) send(new EnterpriseRequestPayload(nextId(), EnterpriseAction.UPDATE_SETTINGS, detail.id(), null, code, name, desc, "", 0, "", false, true, EnterpriseRole.MEMBER));
        }, Component.translatable("screen.yumesumi_finance.enterprise.settings_confirm_title"),
                Component.translatable("screen.yumesumi_finance.enterprise.settings_confirm")));
    }

    private void confirmDisband() {
        if (detail == null) return;
        if (disbandReady) {
            minecraft.setScreen(new FinanceConfirmScreen(confirmed -> {
                minecraft.setScreen(this);
                if (confirmed) {
                    send(new EnterpriseRequestPayload(nextId(), EnterpriseAction.DISBAND, detail.id(), null, "", "", "", "", 0, "", false, true, EnterpriseRole.MEMBER));
                    disbandReady = false;
                }
            }, Component.translatable("screen.yumesumi_finance.enterprise.disband_final_title"),
                    Component.translatable("screen.yumesumi_finance.enterprise.disband_final_confirm")));
            return;
        }
        minecraft.setScreen(new FinanceConfirmScreen(first -> {
            minecraft.setScreen(this);
            if (!first) return;
            disbandCountdown = 100;
            disbandReady = false;
            rebuildWidgets();
        }, Component.translatable("screen.yumesumi_finance.enterprise.disband_confirm_title"),
                Component.translatable("screen.yumesumi_finance.enterprise.disband_confirm_first")));
    }

    private void openInput(String prompt, String initial, java.util.function.Consumer<String> onConfirm) {
        minecraft.setScreen(new InputScreen(this, prompt, initial, onConfirm));
    }

    private Component tabLabel(DetailTab tab) {
        return Component.translatable("screen.yumesumi_finance.enterprise.tab." + tab.name().toLowerCase(Locale.ROOT));
    }


    private List<EnterpriseSummaryView> filteredEnterprises() {
        String search = listSearch.trim().toLowerCase(Locale.ROOT);
        Comparator<EnterpriseSummaryView> comparator = switch (listSort) {
            case NAME -> Comparator.comparing(EnterpriseSummaryView::name, String.CASE_INSENSITIVE_ORDER);
            case CODE -> Comparator.comparing(EnterpriseSummaryView::code, String.CASE_INSENSITIVE_ORDER);
            case BALANCE -> Comparator.comparingLong(EnterpriseSummaryView::balanceCents);
            case ROLE -> Comparator.comparing(view -> view.myRole().ordinal());
            case MEMBERS -> Comparator.comparingInt(EnterpriseSummaryView::memberCount);
        };
        if (listOrder == SortOrder.DESC) comparator = comparator.reversed();
        return enterprises.stream()
                .filter(view -> search.isBlank()
                        || view.name().toLowerCase(Locale.ROOT).contains(search)
                        || view.code().toLowerCase(Locale.ROOT).contains(search)
                        || roleName(view.myRole()).toLowerCase(Locale.ROOT).contains(search))
                .sorted(comparator)
                .toList();
    }

    private List<EnterpriseTransactionView> filteredLedgerTransactions() {
        if (detail == null) return List.of();
        String search = ledgerSearch.trim().toLowerCase(Locale.ROOT);
        Comparator<EnterpriseTransactionView> comparator = switch (ledgerSort) {
            case TIME -> Comparator.comparingLong(EnterpriseTransactionView::createdAt);
            case AMOUNT -> Comparator.comparingLong(EnterpriseTransactionView::amountCents);
            case BALANCE -> Comparator.comparingLong(EnterpriseTransactionView::balanceAfterCents);
            case OPERATOR -> Comparator.comparing(EnterpriseTransactionView::operatorName, String.CASE_INSENSITIVE_ORDER);
        };
        if (ledgerOrder == SortOrder.DESC) comparator = comparator.reversed();
        return detail.transactions().stream()
                .filter(tx -> search.isBlank()
                        || tx.operatorName().toLowerCase(Locale.ROOT).contains(search)
                        || tx.counterpartyName().toLowerCase(Locale.ROOT).contains(search)
                        || tx.note().toLowerCase(Locale.ROOT).contains(search)
                        || tx.type().name().toLowerCase(Locale.ROOT).contains(search)
                        || MoneyFormatter.format(tx.amountCents()).toLowerCase(Locale.ROOT).contains(search))
                .sorted(comparator)
                .limit(10_000)
                .toList();
    }

    private List<EnterpriseMemberView> filteredMembers() {
        if (detail == null) return List.of();
        String search = memberSearch.trim().toLowerCase(Locale.ROOT);
        Comparator<EnterpriseMemberView> comparator = switch (memberSort) {
            case NAME -> Comparator.comparing(EnterpriseMemberView::playerName, String.CASE_INSENSITIVE_ORDER);
            case ROLE -> Comparator.comparing(member -> member.role().ordinal());
            case JOINED -> Comparator.comparingLong(EnterpriseMemberView::joinedAt);
            case LAST_ACTIVE -> Comparator.comparingLong(EnterpriseMemberView::lastActiveAt);
        };
        if (memberOrder == SortOrder.DESC) comparator = comparator.reversed();
        return detail.members().stream()
                .filter(member -> search.isBlank()
                        || member.playerName().toLowerCase(Locale.ROOT).contains(search)
                        || member.playerId().toString().toLowerCase(Locale.ROOT).contains(search)
                        || member.role().name().toLowerCase(Locale.ROOT).contains(search))
                .sorted(comparator)
                .toList();
    }

    private void cycleMemberSort() {
        memberSort = switch (memberSort) {
            case NAME -> MemberSort.ROLE;
            case ROLE -> MemberSort.JOINED;
            case JOINED -> MemberSort.LAST_ACTIVE;
            case LAST_ACTIVE -> MemberSort.NAME;
        };
        rebuildWidgets();
    }

    private Component memberSortLabel() {
        return Component.translatable("screen.yumesumi_finance.enterprise.member_sort." + memberSort.name().toLowerCase(Locale.ROOT));
    }

    private Component orderLabel(SortOrder order) {
        return Component.translatable(order == SortOrder.ASC ? "screen.yumesumi_finance.admin.asc" : "screen.yumesumi_finance.admin.desc");
    }

    private Component listSortLabel() {
        return Component.translatable("screen.yumesumi_finance.enterprise.list_sort." + listSort.name().toLowerCase(Locale.ROOT));
    }

    private void cycleListSort() {
        listSort = switch (listSort) {
            case NAME -> EnterpriseListSort.CODE;
            case CODE -> EnterpriseListSort.BALANCE;
            case BALANCE -> EnterpriseListSort.ROLE;
            case ROLE -> EnterpriseListSort.MEMBERS;
            case MEMBERS -> EnterpriseListSort.NAME;
        };
        listPage = 0;
        rebuildWidgets();
    }

    private Component ledgerSortLabel() {
        return Component.translatable("screen.yumesumi_finance.enterprise.ledger_sort." + ledgerSort.name().toLowerCase(Locale.ROOT));
    }

    private void cycleLedgerSort() {
        ledgerSort = switch (ledgerSort) {
            case TIME -> LedgerSort.AMOUNT;
            case AMOUNT -> LedgerSort.BALANCE;
            case BALANCE -> LedgerSort.OPERATOR;
            case OPERATOR -> LedgerSort.TIME;
        };
        ledgerPage = 0;
        rebuildWidgets();
    }

    private void drawSeparator(GuiGraphics g, int x1, int x2, int y) {
        g.fill(x1, y, x2, y + 1, 0x66FFFFFF);
    }

    private void selectVisibleMembers() {
        filteredMembers().forEach(member -> selectedMembers.add(member.playerId()));
        rebuildWidgets();
    }

    private void invertVisibleMembers() {
        for (EnterpriseMemberView member : filteredMembers()) {
            if (!selectedMembers.remove(member.playerId())) selectedMembers.add(member.playerId());
        }
        rebuildWidgets();
    }

    private String selectedMemberCsv() {
        return String.join(",", selectedMembers.stream().map(UUID::toString).toList());
    }

    private String roleName(EnterpriseRole role) {
        return Component.translatable("screen.yumesumi_finance.enterprise.role." + role.name().toLowerCase(Locale.ROOT)).getString();
    }

    private String formatTime(long ms) {
        return ms <= 0 ? "-" : TIME_FMT.format(Instant.ofEpochMilli(ms));
    }

    private long parseAmount(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (!AMOUNT_PATTERN.matcher(text).matches()) throw new NumberFormatException("Invalid amount");
        long amount = MoneyFormatter.parseCents(text);
        if (amount <= 0) throw new NumberFormatException("Invalid amount");
        return amount;
    }

    private void send(EnterpriseRequestPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    private void clearStatus() {
        message = Component.empty();
        success = false;
    }

    private long nextId() {
        requestCounter++;
        if (requestCounter <= 0) requestCounter = 1;
        return requestCounter;
    }

    private int left() { return (width - panelWidth()) / 2; }
    private int top() { return (height - panelHeight()) / 2; }
    private int panelWidth() { return Math.min(980, Math.max(1, width - 24)); }
    private int panelHeight() { return Math.min(620, Math.max(1, height - 24)); }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (mode == EnterpriseScreenMode.DETAIL) {
                send(EnterpriseRequestPayload.simple(nextId(), EnterpriseAction.OPEN_LIST));
                return true;
            }
            if (mode == EnterpriseScreenMode.REGISTER) {
                send(EnterpriseRequestPayload.simple(nextId(), EnterpriseAction.OPEN_LIST));
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static class InputScreen extends Screen {
        private final Screen parent;
        private final String prompt;
        private final String initial;
        private final java.util.function.Consumer<String> onConfirm;
        private EditBox input;

        InputScreen(Screen parent, String prompt, String initial, java.util.function.Consumer<String> onConfirm) {
            super(Component.literal(prompt));
            this.parent = parent;
            this.prompt = prompt;
            this.initial = initial == null ? "" : initial;
            this.onConfirm = onConfirm;
        }

        @Override
        protected void init() {
            int w = Math.min(320, width - 60);
            int x = (width - w) / 2;
            int y = height / 2 - 36;
            input = new EditBox(font, x, y + 22, w, 20, Component.empty());
            input.setValue(initial);
            addRenderableWidget(input);
            addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.confirm"), b -> {
                Screen current = minecraft.screen;
                onConfirm.accept(input.getValue());
                if (minecraft.screen == current) minecraft.setScreen(parent);
            }).bounds(x, y + 50, 58, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.cancel"), b -> minecraft.setScreen(parent)).bounds(x + 64, y + 50, 58, 20).build());
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {
            renderBackground(g, mx, my, pt);
            int w = Math.min(320, width - 60);
            int x = (width - w) / 2;
            int y = height / 2 - 36;
            g.drawCenteredString(font, prompt, width / 2, y, FinanceTheme.GOLD);
            super.render(g, mx, my, pt);
        }

        @Override
        public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
            g.fill(0, 0, width, height, SCREEN_BG);
        }
    }

    private static class BatchTransferInputScreen extends Screen {
        private final Screen parent;
        private final int selectedCount;
        private final BiConsumer<Long, String> onConfirm;
        private EditBox amountBox;
        private EditBox noteBox;
        private Component error = Component.empty();

        BatchTransferInputScreen(Screen parent, int selectedCount, BiConsumer<Long, String> onConfirm) {
            super(Component.translatable("screen.yumesumi_finance.enterprise.batch_transfer_title"));
            this.parent = parent;
            this.selectedCount = selectedCount;
            this.onConfirm = onConfirm;
        }

        @Override
        protected void init() {
            int panelW = Math.min(380, Math.max(1, width - 40));
            int x = (width - panelW) / 2;
            int y = height / 2 - 72;
            amountBox = new EditBox(font, x + 86, y + 50, panelW - 110, 20, Component.empty());
            amountBox.setHint(Component.translatable("screen.yumesumi_finance.amount"));
            addRenderableWidget(amountBox);
            noteBox = new EditBox(font, x + 86, y + 78, panelW - 110, 20, Component.empty());
            noteBox.setHint(Component.translatable("screen.yumesumi_finance.transfer_note"));
            noteBox.setMaxLength(64);
            addRenderableWidget(noteBox);
            addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.confirm"), b -> confirm())
                    .bounds(x + panelW / 2 - 78, y + 118, 72, 20)
                    .build());
            addRenderableWidget(Button.builder(Component.translatable("screen.yumesumi_finance.cancel"), b -> minecraft.setScreen(parent))
                    .bounds(x + panelW / 2 + 6, y + 118, 72, 20)
                    .build());
            setInitialFocus(amountBox);
        }

        private void confirm() {
            try {
                String raw = amountBox == null ? "" : amountBox.getValue().trim();
                if (!AMOUNT_PATTERN.matcher(raw).matches()) throw new NumberFormatException("Invalid amount");
                long amount = MoneyFormatter.parseCents(raw);
                if (amount <= 0) throw new NumberFormatException("Invalid amount");
                String note = noteBox == null ? "" : noteBox.getValue().trim();
                onConfirm.accept(amount, note);
            } catch (NumberFormatException exception) {
                error = Component.translatable("message.yumesumi_finance.transfer.invalid_amount");
            }
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {
            renderBackground(g, mx, my, pt);
            int panelW = Math.min(380, Math.max(1, width - 40));
            int panelH = 156;
            int x = (width - panelW) / 2;
            int y = (height - panelH) / 2;
            g.drawCenteredString(font, title, width / 2, y + 14, FinanceTheme.GOLD);
            g.drawCenteredString(font, Component.translatable("screen.yumesumi_finance.enterprise.batch_transfer_count", selectedCount), width / 2, y + 30, FinanceTheme.TEXT_DIM);
            g.drawString(font, Component.translatable("screen.yumesumi_finance.enterprise.batch_transfer_amount"), x + 22, y + 54, FinanceTheme.TEXT_DIM, true);
            g.drawString(font, Component.translatable("screen.yumesumi_finance.enterprise.batch_transfer_note"), x + 22, y + 82, FinanceTheme.TEXT_DIM, true);
            if (!error.getString().isEmpty()) {
                g.drawCenteredString(font, error, width / 2, y + 104, FinanceTheme.RED);
            }
            super.render(g, mx, my, pt);
        }

        @Override
        public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
            g.fill(0, 0, width, height, SCREEN_BG);
        }
    }
}
