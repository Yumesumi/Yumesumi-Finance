package com.yumesumi.finance.bank.enterprise;

import java.util.UUID;

public record EnterpriseTransactionRecord(
        UUID id,
        UUID enterpriseId,
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
    public EnterpriseTransactionRecord {
        if (id == null) id = UUID.randomUUID();
        if (enterpriseId == null) throw new IllegalArgumentException("Enterprise id cannot be null");
        if (type == null) throw new IllegalArgumentException("Enterprise transaction type cannot be null");
        operatorName = operatorName == null ? "" : operatorName;
        counterpartyName = counterpartyName == null ? "" : counterpartyName;
        note = note == null ? "" : note;
    }
}
