package com.yumesumi.finance.bank.admin;

import java.math.BigInteger;
import java.util.UUID;

public record TreasuryTransactionRecord(
        UUID id,
        BigInteger deltaCents,
        BigInteger balanceAfterCents,
        String operatorName,
        String note,
        long createdAt
) {
    public TreasuryTransactionRecord {
        if (id == null) id = UUID.randomUUID();
        if (deltaCents == null) deltaCents = BigInteger.ZERO;
        if (balanceAfterCents == null) balanceAfterCents = BigInteger.ZERO;
        operatorName = operatorName == null ? "" : operatorName;
        note = note == null ? "" : note.trim();
        if (note.length() > 64) note = note.substring(0, 64);
    }
}
