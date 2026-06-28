package com.yumesumi.finance.network.payload;

import com.yumesumi.finance.bank.enterprise.EnterpriseTransactionRecord;
import com.yumesumi.finance.bank.enterprise.EnterpriseTransactionType;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;

public record EnterpriseTransactionView(
        UUID id,
        EnterpriseTransactionType type,
        long amountCents,
        long balanceAfterCents,
        UUID operatorId,
        String operatorName,
        UUID counterpartyId,
        String counterpartyName,
        String note,
        long createdAt
) {
    public static EnterpriseTransactionView from(EnterpriseTransactionRecord record) {
        return new EnterpriseTransactionView(
                record.id(),
                record.type(),
                record.amountCents(),
                record.balanceAfterCents(),
                record.operatorId(),
                record.operatorName(),
                record.counterpartyId(),
                record.counterpartyName(),
                record.note(),
                record.createdAt()
        );
    }

    public static void write(RegistryFriendlyByteBuf buf, EnterpriseTransactionView view) {
        buf.writeUUID(view.id());
        buf.writeEnum(view.type());
        buf.writeVarLong(view.amountCents());
        buf.writeVarLong(view.balanceAfterCents());
        buf.writeBoolean(view.operatorId() != null);
        if (view.operatorId() != null) buf.writeUUID(view.operatorId());
        buf.writeUtf(view.operatorName(), 64);
        buf.writeBoolean(view.counterpartyId() != null);
        if (view.counterpartyId() != null) buf.writeUUID(view.counterpartyId());
        buf.writeUtf(view.counterpartyName(), 96);
        buf.writeUtf(view.note(), 128);
        buf.writeVarLong(view.createdAt());
    }

    public static EnterpriseTransactionView read(RegistryFriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        EnterpriseTransactionType type = buf.readEnum(EnterpriseTransactionType.class);
        long amount = buf.readVarLong();
        long balanceAfter = buf.readVarLong();
        UUID operatorId = buf.readBoolean() ? buf.readUUID() : null;
        String operatorName = buf.readUtf(64);
        UUID counterpartyId = buf.readBoolean() ? buf.readUUID() : null;
        String counterpartyName = buf.readUtf(96);
        String note = buf.readUtf(128);
        long createdAt = buf.readVarLong();
        return new EnterpriseTransactionView(id, type, amount, balanceAfter, operatorId, operatorName, counterpartyId, counterpartyName, note, createdAt);
    }
}
