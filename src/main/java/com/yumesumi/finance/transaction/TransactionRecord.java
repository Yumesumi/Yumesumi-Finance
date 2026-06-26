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
        if (id == null) {
            throw new IllegalArgumentException("交易 ID 不能为空");
        }
        if (owner == null) {
            throw new IllegalArgumentException("交易所属账户不能为空");
        }
        if (type == null) {
            throw new IllegalArgumentException("交易类型不能为空");
        }
        if (amountCents <= 0) {
            throw new IllegalArgumentException("交易金额必须大于 0");
        }
        if (balanceAfterCents < 0) {
            throw new IllegalArgumentException("交易后余额不能为负数");
        }
        counterpartyName = counterpartyName == null ? "" : counterpartyName;
        note = note == null ? "" : note;
    }

    public boolean income() {
        return type.income();
    }
}
