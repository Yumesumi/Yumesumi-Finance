package com.yumesumi.finance.network.payload;

import com.yumesumi.finance.transaction.TransactionRecord;
import com.yumesumi.finance.transaction.TransactionType;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record BankTransactionView(
        TransactionType type,
        long amountCents,
        long balanceAfterCents,
        long createdTime,
        String counterpartyName,
        UUID counterpartyId,
        String note
) {
    public static final StreamCodec<RegistryFriendlyByteBuf, BankTransactionView> STREAM_CODEC = StreamCodec.of(
            BankTransactionView::write,
            BankTransactionView::read
    );

    public static BankTransactionView from(TransactionRecord record) {
        return new BankTransactionView(
                record.type(),
                record.amountCents(),
                record.balanceAfterCents(),
                record.createdGameTime(),
                record.counterpartyName(),
                record.counterpartyId(),
                record.note()
        );
    }

    public static void write(RegistryFriendlyByteBuf buffer, BankTransactionView view) {
        buffer.writeEnum(view.type());
        buffer.writeVarLong(view.amountCents());
        buffer.writeVarLong(view.balanceAfterCents());
        buffer.writeVarLong(view.createdTime());
        buffer.writeUtf(view.counterpartyName(), 128);
        buffer.writeBoolean(view.counterpartyId() != null);
        if (view.counterpartyId() != null) {
            buffer.writeUUID(view.counterpartyId());
        }
        buffer.writeUtf(view.note(), 128);
    }

    public static BankTransactionView read(RegistryFriendlyByteBuf buffer) {
        TransactionType type = buffer.readEnum(TransactionType.class);
        long amountCents = buffer.readVarLong();
        long balanceAfterCents = buffer.readVarLong();
        long createdTime = buffer.readVarLong();
        String counterpartyName = buffer.readUtf(128);
        UUID counterpartyId = buffer.readBoolean() ? buffer.readUUID() : null;
        String note = buffer.readUtf(128);
        return new BankTransactionView(
                type,
                amountCents,
                balanceAfterCents,
                createdTime,
                counterpartyName,
                counterpartyId,
                note
        );
    }
}
