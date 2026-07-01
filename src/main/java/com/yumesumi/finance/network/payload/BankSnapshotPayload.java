package com.yumesumi.finance.network.payload;

import com.yumesumi.finance.YumesumiFinance;
import com.yumesumi.finance.bank.BankAccessChannel;
import com.yumesumi.finance.transaction.TransactionRecord;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BankSnapshotPayload(
        long balanceCents,
        long cashBalanceCents,
        Component message,
        String messageKey,
        boolean success,
        BankAccessChannel channel,
        List<BankTransactionView> transactions,
        boolean accountFrozen,
        List<InboxMessageView> inbox,
        List<EnterpriseSummaryView> enterprises,
        boolean preferEnterprise,
        boolean openEnterpriseTransfer,
        String enterpriseTransferTarget
) implements CustomPacketPayload {
    public static final Type<BankSnapshotPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(YumesumiFinance.MODID, "bank_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BankSnapshotPayload> STREAM_CODEC = StreamCodec.of(
            BankSnapshotPayload::write, BankSnapshotPayload::read
    );

    public BankSnapshotPayload {
        messageKey = messageKey == null ? "" : messageKey;
        channel = channel == null ? BankAccessChannel.ATM : channel;
        transactions = List.copyOf(transactions);
        inbox = List.copyOf(inbox);
        enterprises = List.copyOf(enterprises);
        enterpriseTransferTarget = enterpriseTransferTarget == null ? "" : enterpriseTransferTarget.trim();
        if (enterpriseTransferTarget.length() > 16) enterpriseTransferTarget = enterpriseTransferTarget.substring(0, 16);
    }

    public static BankSnapshotPayload of(long balanceCents, Component message, String messageKey, BankAccessChannel channel, List<TransactionRecord> transactions) {
        return of(balanceCents, message, messageKey, true, channel, transactions, false);
    }

    public static BankSnapshotPayload of(long balanceCents, Component message, String messageKey, boolean success, BankAccessChannel channel, List<TransactionRecord> transactions) {
        return of(balanceCents, message, messageKey, success, channel, transactions, false);
    }

    public static BankSnapshotPayload of(long balanceCents, Component message, String messageKey, boolean success, BankAccessChannel channel, List<TransactionRecord> transactions, boolean accountFrozen) {
        return of(balanceCents, 0L, message, messageKey, success, channel, transactions, accountFrozen, List.of(), List.of(), false);
    }

    public static BankSnapshotPayload of(long balanceCents, long cashBalanceCents, Component message, String messageKey, boolean success, BankAccessChannel channel,
                                         List<TransactionRecord> transactions, boolean accountFrozen,
                                         List<InboxMessageView> inbox, List<EnterpriseSummaryView> enterprises, boolean preferEnterprise) {
        return ofViews(
                balanceCents, cashBalanceCents, message, messageKey, success, channel,
                transactions.stream().limit(1_000).map(BankTransactionView::from).toList(),
                accountFrozen,
                inbox,
                enterprises,
                preferEnterprise,
                false,
                ""
        );
    }

    public static BankSnapshotPayload ofViews(long balanceCents, long cashBalanceCents, Component message, String messageKey, boolean success, BankAccessChannel channel,
                                             List<BankTransactionView> transactions, boolean accountFrozen,
                                             List<InboxMessageView> inbox, List<EnterpriseSummaryView> enterprises, boolean preferEnterprise) {
        return ofViews(balanceCents, cashBalanceCents, message, messageKey, success, channel, transactions, accountFrozen, inbox, enterprises, preferEnterprise, false);
    }

    public static BankSnapshotPayload ofViews(long balanceCents, long cashBalanceCents, Component message, String messageKey, boolean success, BankAccessChannel channel,
                                             List<BankTransactionView> transactions, boolean accountFrozen,
                                             List<InboxMessageView> inbox, List<EnterpriseSummaryView> enterprises, boolean preferEnterprise, boolean openEnterpriseTransfer) {
        return ofViews(balanceCents, cashBalanceCents, message, messageKey, success, channel, transactions, accountFrozen, inbox, enterprises, preferEnterprise, openEnterpriseTransfer, "");
    }

    public static BankSnapshotPayload ofViews(long balanceCents, long cashBalanceCents, Component message, String messageKey, boolean success, BankAccessChannel channel,
                                             List<BankTransactionView> transactions, boolean accountFrozen,
                                             List<InboxMessageView> inbox, List<EnterpriseSummaryView> enterprises, boolean preferEnterprise,
                                             boolean openEnterpriseTransfer, String enterpriseTransferTarget) {
        return new BankSnapshotPayload(
                balanceCents, cashBalanceCents, message, messageKey, success, channel,
                transactions.stream().limit(1_000).toList(),
                accountFrozen,
                inbox,
                enterprises,
                preferEnterprise,
                openEnterpriseTransfer,
                enterpriseTransferTarget
        );
    }

    public static BankSnapshotPayload of(long balanceCents, Component message, String messageKey, boolean success, BankAccessChannel channel,
                                         List<TransactionRecord> transactions, boolean accountFrozen,
                                         List<InboxMessageView> inbox, List<EnterpriseSummaryView> enterprises, boolean preferEnterprise) {
        return of(balanceCents, 0L, message, messageKey, success, channel, transactions, accountFrozen, inbox, enterprises, preferEnterprise);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void write(RegistryFriendlyByteBuf buf, BankSnapshotPayload p) {
        buf.writeVarLong(p.balanceCents());
        buf.writeVarLong(p.cashBalanceCents());
        ComponentSerialization.STREAM_CODEC.encode(buf, p.message());
        buf.writeUtf(p.messageKey(), 128);
        buf.writeBoolean(p.success());
        buf.writeEnum(p.channel());
        buf.writeVarInt(p.transactions().size());
        for (BankTransactionView t : p.transactions()) BankTransactionView.write(buf, t);
        buf.writeBoolean(p.accountFrozen());
        buf.writeVarInt(p.inbox().size());
        for (InboxMessageView message : p.inbox()) InboxMessageView.write(buf, message);
        buf.writeVarInt(p.enterprises().size());
        for (EnterpriseSummaryView enterprise : p.enterprises()) EnterpriseSummaryView.write(buf, enterprise);
        buf.writeBoolean(p.preferEnterprise());
        buf.writeBoolean(p.openEnterpriseTransfer());
        buf.writeUtf(p.enterpriseTransferTarget(), 16);
    }

    private static BankSnapshotPayload read(RegistryFriendlyByteBuf buf) {
        long balanceCents = buf.readVarLong();
        long cashBalanceCents = buf.readVarLong();
        Component message = ComponentSerialization.STREAM_CODEC.decode(buf);
        String messageKey = buf.readUtf(128);
        boolean success = buf.readBoolean();
        BankAccessChannel channel = buf.readEnum(BankAccessChannel.class);
        int size = Math.max(0, buf.readVarInt());
        List<BankTransactionView> transactions = new ArrayList<>(Math.min(size, 1_000));
        for (int i = 0; i < size; i++) {
            BankTransactionView t = BankTransactionView.read(buf);
            if (i < 1_000) transactions.add(t);
        }
        boolean accountFrozen = buf.readBoolean();
        int inboxSize = Math.max(0, buf.readVarInt());
        List<InboxMessageView> inbox = new ArrayList<>(Math.min(inboxSize, 256));
        for (int i = 0; i < inboxSize; i++) inbox.add(InboxMessageView.read(buf));
        int enterpriseSize = Math.max(0, buf.readVarInt());
        List<EnterpriseSummaryView> enterprises = new ArrayList<>(Math.min(enterpriseSize, 256));
        for (int i = 0; i < enterpriseSize; i++) enterprises.add(EnterpriseSummaryView.read(buf));
        boolean preferEnterprise = buf.readBoolean();
        boolean openEnterpriseTransfer = buf.readBoolean();
        String enterpriseTransferTarget = buf.readUtf(16);
        return new BankSnapshotPayload(balanceCents, cashBalanceCents, message, messageKey, success, channel, transactions, accountFrozen, inbox, enterprises,
                preferEnterprise, openEnterpriseTransfer, enterpriseTransferTarget);
    }
}
