package com.yumesumi.finance.bank.ui;

import com.yumesumi.finance.api.bank.BankService;
import com.yumesumi.finance.bank.BankAccessChannel;
import com.yumesumi.finance.bank.account.BankAccount;
import com.yumesumi.finance.bank.card.BankCardData;
import com.yumesumi.finance.bank.enterprise.EnterpriseAccount;
import com.yumesumi.finance.bank.service.ServerEnterpriseService;
import com.yumesumi.finance.bank.service.ServerBankService;
import com.yumesumi.finance.bank.storage.BankSavedData;
import com.yumesumi.finance.bank.storage.EnterpriseSavedData;
import com.yumesumi.finance.bank.transfer.TransferTarget;
import com.yumesumi.finance.bank.transfer.TransferTargetResolver;
import com.yumesumi.finance.money.MoneyFormatter;
import com.yumesumi.finance.money.cash.PlayerCashWallet;
import com.yumesumi.finance.network.payload.BankSnapshotPayload;
import com.yumesumi.finance.network.payload.BankTransactionView;
import com.yumesumi.finance.network.payload.EnterpriseSummaryView;
import com.yumesumi.finance.network.payload.InboxMessageView;
import com.yumesumi.finance.transaction.TransactionRecord;
import com.yumesumi.finance.transaction.TransactionResult;
import com.yumesumi.finance.transaction.TransactionType;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class BankScreenAccessService {
    private BankScreenAccessService() {}

    public static void openFromAtm(ServerPlayer player) {
        if (openPreferredEnterprise(player)) return;
        sendSnapshot(player, "message.yumesumi_finance.open.atm", BankAccessChannel.ATM);
    }

    public static void openFromCard(ServerPlayer player) {
        if (openPreferredEnterprise(player)) return;
        sendSnapshot(player, "message.yumesumi_finance.open.card", BankAccessChannel.CARD);
    }

    public static void openFromCard(ServerPlayer player, BankCardData cardData) {
        if (cardData == null || !cardData.ownedBy(player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.yumesumi_finance.card.wrong_owner"), true);
            return;
        }
        if (openPreferredEnterprise(player)) return;
        sendSnapshot(player, "message.yumesumi_finance.open.card", BankAccessChannel.CARD);
    }

    public static void deposit(ServerPlayer player, long amountCents, BankAccessChannel channel) {
        sendTransactionResult(player, service(player).deposit(player, amountCents, channel), channel);
    }

    public static void depositAll(ServerPlayer player, BankAccessChannel channel) {
        sendTransactionResult(player, service(player).depositAll(player, channel), channel);
    }

    public static void withdraw(ServerPlayer player, long amountCents, BankAccessChannel channel) {
        sendTransactionResult(player, service(player).withdraw(player, amountCents, channel), channel);
    }

    public static void transfer(ServerPlayer player, String rawTarget, long amountCents, String note, BankAccessChannel channel) {
        BankService bankService = service(player);
        TransferTarget target = TransferTargetResolver.resolve(player.server, rawTarget).orElse(null);
        TransactionResult result = target == null
                ? TransactionResult.failure(TransactionType.TRANSFER_OUT, amountCents, bankService.balanceOf(player), "message.yumesumi_finance.transfer.target_not_found")
                : bankService.transfer(player, target.owner(), target.displayName(), amountCents, note);
        sendTransactionResult(player, result, channel, target == null ? Component.literal(rawTarget == null ? "" : rawTarget) : Component.literal(target.displayName()));
    }

    public static void sendFrozenSnapshot(ServerPlayer player, BankAccessChannel channel) {
        BankService bankService = service(player);
        PacketDistributor.sendToPlayer(player, BankSnapshotPayload.ofViews(
                bankService.balanceOf(player),
                cashBalance(player),
                Component.translatable("message.yumesumi_finance.account.frozen"),
                "message.yumesumi_finance.account.frozen",
                false,
                channel,
                transactionViews(player, bankService.recentTransactions(player)),
                true,
                inbox(player),
                enterpriseSummaries(player),
                EnterpriseSavedData.get(player.server).prefersEnterprise(player.getUUID())
        ));
    }

    public static void sendSnapshotWithMessage(ServerPlayer player, BankAccessChannel channel, String messageKey, boolean success) {
        BankService bankService = service(player);
        boolean frozen = BankSavedData.get(player.server).find(player.getUUID()).map(BankAccount::frozen).orElse(false);
        PacketDistributor.sendToPlayer(player, BankSnapshotPayload.ofViews(
                bankService.balanceOf(player),
                cashBalance(player),
                Component.translatable(messageKey),
                messageKey,
                success,
                channel,
                transactionViews(player, bankService.recentTransactions(player)),
                frozen,
                inbox(player),
                enterpriseSummaries(player),
                EnterpriseSavedData.get(player.server).prefersEnterprise(player.getUUID())
        ));
    }

    private static void sendTransactionResult(ServerPlayer player, TransactionResult result, BankAccessChannel channel) {
        sendTransactionResult(player, result, channel, Component.literal(""));
    }

    private static void sendTransactionResult(ServerPlayer player, TransactionResult result, BankAccessChannel channel, Component target) {
        Component amount = Component.literal(MoneyFormatter.format(result.amountCents()));
        Component message = Component.translatable(result.messageKey(), amount, target);
        BankService bankService = service(player);
        boolean frozen = BankSavedData.get(player.server).find(player.getUUID()).map(BankAccount::frozen).orElse(false);
        PacketDistributor.sendToPlayer(player, BankSnapshotPayload.ofViews(
                result.balanceCents(), cashBalance(player), message, result.messageKey(), result.success(), channel,
                transactionViews(player, bankService.recentTransactions(player)), frozen,
                inbox(player),
                enterpriseSummaries(player),
                EnterpriseSavedData.get(player.server).prefersEnterprise(player.getUUID())
        ));
    }

    private static void sendSnapshot(ServerPlayer player, String messageKey, BankAccessChannel channel) {
        BankService bankService = service(player);
        boolean frozen = BankSavedData.get(player.server).find(player.getUUID()).map(BankAccount::frozen).orElse(false);
        EnterpriseSavedData enterpriseData = EnterpriseSavedData.get(player.server);
        PacketDistributor.sendToPlayer(player, BankSnapshotPayload.ofViews(
                bankService.balanceOf(player),
                cashBalance(player),
                Component.translatable(messageKey),
                messageKey,
                true,
                channel,
                transactionViews(player, bankService.recentTransactions(player)),
                frozen,
                inbox(player),
                enterpriseSummaries(player),
                enterpriseData.prefersEnterprise(player.getUUID())
        ));
    }

    private static boolean openPreferredEnterprise(ServerPlayer player) {
        EnterpriseSavedData data = EnterpriseSavedData.get(player.server);
        if (data.prefersEnterprise(player.getUUID()) && !data.enterprisesFor(player.getUUID()).isEmpty()) {
            ServerEnterpriseService.forServer(player.server).openList(player, "message.yumesumi_finance.enterprise.list.open");
            return true;
        }
        data.setPrefersEnterprise(player.getUUID(), false);
        return false;
    }

    private static List<InboxMessageView> inbox(ServerPlayer player) {
        return EnterpriseSavedData.get(player.server).inbox(player.getUUID()).stream()
                .limit(100)
                .map(InboxMessageView::from)
                .toList();
    }

    private static List<EnterpriseSummaryView> enterpriseSummaries(ServerPlayer player) {
        return ServerEnterpriseService.forServer(player.server).summaries(player);
    }

    private static List<BankTransactionView> transactionViews(ServerPlayer player, List<TransactionRecord> records) {
        EnterpriseSavedData enterpriseData = EnterpriseSavedData.get(player.server);
        return records.stream()
                .map(record -> BankTransactionView.from(record, enterpriseCode(enterpriseData, record.counterpartyId())))
                .toList();
    }

    private static String enterpriseCode(EnterpriseSavedData enterpriseData, UUID id) {
        if (id == null) return "";
        return enterpriseData.findEnterprise(id)
                .map(EnterpriseAccount::code)
                .orElse("");
    }

    private static BankService service(ServerPlayer player) {
        return ServerBankService.forServerPlayer(player);
    }

    private static long cashBalance(ServerPlayer player) {
        try {
            return new PlayerCashWallet().count(player);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }
}
