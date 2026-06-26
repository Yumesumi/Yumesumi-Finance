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

public record BankSnapshotPayload(long balanceCents, Component message, String messageKey, boolean success, BankAccessChannel channel, List<BankTransactionView> transactions) implements CustomPacketPayload {
    public static final Type<BankSnapshotPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(YumesumiFinance.MODID, "bank_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BankSnapshotPayload> STREAM_CODEC = StreamCodec.of(
            BankSnapshotPayload::write,
            BankSnapshotPayload::read
    );

    public BankSnapshotPayload {
        messageKey = messageKey == null ? "" : messageKey;
        channel = channel == null ? BankAccessChannel.ATM : channel;
        transactions = List.copyOf(transactions);
    }

    public static BankSnapshotPayload of(long balanceCents, Component message, String messageKey, BankAccessChannel channel, List<TransactionRecord> transactions) {
        return of(balanceCents, message, messageKey, true, channel, transactions);
    }

    public static BankSnapshotPayload of(long balanceCents, Component message, String messageKey, boolean success, BankAccessChannel channel, List<TransactionRecord> transactions) {
        return new BankSnapshotPayload(
                balanceCents,
                message,
                messageKey,
                success,
                channel,
                transactions.stream().limit(1_000).map(BankTransactionView::from).toList()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buffer, BankSnapshotPayload payload) {
        buffer.writeVarLong(payload.balanceCents());
        ComponentSerialization.STREAM_CODEC.encode(buffer, payload.message());
        buffer.writeUtf(payload.messageKey(), 128);
        buffer.writeBoolean(payload.success());
        buffer.writeEnum(payload.channel());
        buffer.writeVarInt(payload.transactions().size());
        for (BankTransactionView transaction : payload.transactions()) {
            BankTransactionView.write(buffer, transaction);
        }
    }

    private static BankSnapshotPayload read(RegistryFriendlyByteBuf buffer) {
        long balanceCents = buffer.readVarLong();
        Component message = ComponentSerialization.STREAM_CODEC.decode(buffer);
        String messageKey = buffer.readUtf(128);
        boolean success = buffer.readBoolean();
        BankAccessChannel channel = buffer.readEnum(BankAccessChannel.class);
        int size = Math.max(0, buffer.readVarInt());
        List<BankTransactionView> transactions = new ArrayList<>(Math.min(size, 1_000));
        for (int i = 0; i < size; i++) {
            BankTransactionView transaction = BankTransactionView.read(buffer);
            if (i < 1_000) {
                transactions.add(transaction);
            }
        }
        return new BankSnapshotPayload(
                balanceCents,
                message,
                messageKey,
                success,
                channel,
                transactions
        );
    }
}
