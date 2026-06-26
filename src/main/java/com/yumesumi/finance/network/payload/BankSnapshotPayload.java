package com.yumesumi.finance.network.payload;

import com.yumesumi.finance.YumesumiFinance;
import com.yumesumi.finance.transaction.TransactionRecord;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BankSnapshotPayload(long balanceCents, String message, boolean success, List<BankTransactionView> transactions) implements CustomPacketPayload {
    public static final Type<BankSnapshotPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(YumesumiFinance.MODID, "bank_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BankSnapshotPayload> STREAM_CODEC = StreamCodec.of(
            BankSnapshotPayload::write,
            BankSnapshotPayload::read
    );

    public BankSnapshotPayload {
        transactions = List.copyOf(transactions);
    }

    public static BankSnapshotPayload of(long balanceCents, String message, List<TransactionRecord> transactions) {
        return of(balanceCents, message, true, transactions);
    }

    public static BankSnapshotPayload of(long balanceCents, String message, boolean success, List<TransactionRecord> transactions) {
        return new BankSnapshotPayload(
                balanceCents,
                message,
                success,
                transactions.stream().limit(1_000).map(BankTransactionView::from).toList()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buffer, BankSnapshotPayload payload) {
        buffer.writeVarLong(payload.balanceCents());
        buffer.writeUtf(payload.message(), 512);
        buffer.writeBoolean(payload.success());
        buffer.writeVarInt(payload.transactions().size());
        for (BankTransactionView transaction : payload.transactions()) {
            BankTransactionView.write(buffer, transaction);
        }
    }

    private static BankSnapshotPayload read(RegistryFriendlyByteBuf buffer) {
        long balanceCents = buffer.readVarLong();
        String message = buffer.readUtf(512);
        boolean success = buffer.readBoolean();
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
                success,
                transactions
        );
    }
}
