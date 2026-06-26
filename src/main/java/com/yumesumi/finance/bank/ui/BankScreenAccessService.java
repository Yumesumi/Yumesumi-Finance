package com.yumesumi.finance.bank.ui;

import com.yumesumi.finance.api.bank.BankService;
import com.yumesumi.finance.bank.BankAccessChannel;
import com.yumesumi.finance.bank.card.BankCardData;
import com.yumesumi.finance.bank.service.ServerBankService;
import com.yumesumi.finance.bank.transfer.TransferTarget;
import com.yumesumi.finance.bank.transfer.TransferTargetResolver;
import com.yumesumi.finance.money.MoneyFormatter;
import com.yumesumi.finance.network.payload.BankSnapshotPayload;
import com.yumesumi.finance.transaction.TransactionResult;
import com.yumesumi.finance.transaction.TransactionType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 银行 GUI 的服务端接入层。
 *
 * <p>方块、物品和网络包只进入这里；真正的银行业务交给 {@link BankService}。</p>
 */
public final class BankScreenAccessService {
    private BankScreenAccessService() {
    }

    public static void openFromAtm(ServerPlayer player) {
        sendSnapshot(player, "message.yumesumi_finance.open.atm", BankAccessChannel.ATM);
    }

    public static void openFromCard(ServerPlayer player) {
        sendSnapshot(player, "message.yumesumi_finance.open.card", BankAccessChannel.CARD);
    }

    public static void openFromCard(ServerPlayer player, BankCardData cardData) {
        if (cardData == null || !cardData.ownedBy(player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.yumesumi_finance.card.wrong_owner"), true);
            return;
        }
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

    private static void sendTransactionResult(ServerPlayer player, TransactionResult result, BankAccessChannel channel) {
        sendTransactionResult(player, result, channel, Component.literal(""));
    }

    private static void sendTransactionResult(ServerPlayer player, TransactionResult result, BankAccessChannel channel, Component target) {
        Component amount = Component.literal(MoneyFormatter.format(result.amountCents()));
        Component message = Component.translatable(result.messageKey(), amount, target);
        BankService bankService = service(player);
        PacketDistributor.sendToPlayer(player, BankSnapshotPayload.of(result.balanceCents(), message, result.messageKey(), result.success(), channel, bankService.recentTransactions(player)));
    }

    private static void sendSnapshot(ServerPlayer player, String messageKey, BankAccessChannel channel) {
        BankService bankService = service(player);
        PacketDistributor.sendToPlayer(player, BankSnapshotPayload.of(
                bankService.balanceOf(player),
                Component.translatable(messageKey),
                messageKey,
                channel,
                bankService.recentTransactions(player)
        ));
    }

    private static BankService service(ServerPlayer player) {
        return ServerBankService.forServerPlayer(player);
    }
}
