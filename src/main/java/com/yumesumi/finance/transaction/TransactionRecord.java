package com.yumesumi.finance.transaction;

import java.util.UUID;

public record TransactionRecord(
        UUID id,
        UUID owner,
        TransactionType type,
        long amountCents,
        long balanceAfterCents,
        long createdGameTime,
        String counterpartyName,
        UUID counterpartyId,
        String note
) {
    public TransactionRecord {
        if (amountCents <= 0) {
            throw new IllegalArgumentException("交易金额必须大于 0");
        }
        counterpartyName = counterpartyName == null ? "" : counterpartyName;
        note = note == null ? "" : note;
    }

    public boolean income() {
        return type.income();
    }
}
