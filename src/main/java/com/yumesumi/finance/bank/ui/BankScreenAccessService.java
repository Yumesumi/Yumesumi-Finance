package com.yumesumi.finance.bank.ui;

import com.yumesumi.finance.api.bank.BankService;
import com.yumesumi.finance.bank.service.ServerBankService;
import com.yumesumi.finance.money.MoneyFormatter;
import com.yumesumi.finance.network.payload.BankSnapshotPayload;
import com.yumesumi.finance.transaction.TransactionResult;
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
        sendSnapshot(player, service(player).balanceOf(player), "message.yumesumi_finance.open.atm");
    }

    public static void openFromCard(ServerPlayer player) {
        sendSnapshot(player, service(player).balanceOf(player), "message.yumesumi_finance.open.card");
    }

    public static void deposit(ServerPlayer player, long amountCents) {
        sendTransactionResult(player, service(player).deposit(player, amountCents));
    }

    public static void withdraw(ServerPlayer player, long amountCents) {
        sendTransactionResult(player, service(player).withdraw(player, amountCents));
    }

    private static void sendTransactionResult(ServerPlayer player, TransactionResult result) {
        Component amount = Component.literal(MoneyFormatter.format(result.amountCents()));
        Component message = Component.translatable(result.messageKey(), amount);
        PacketDistributor.sendToPlayer(player, new BankSnapshotPayload(result.balanceCents(), message.getString()));
    }

    private static void sendSnapshot(ServerPlayer player, long balanceCents, String messageKey) {
        PacketDistributor.sendToPlayer(player, new BankSnapshotPayload(balanceCents, Component.translatable(messageKey).getString()));
    }

    private static BankService service(ServerPlayer player) {
        return ServerBankService.forServerPlayer(player);
    }
}
