package com.yumesumi.finance.api.bank;

import java.util.UUID;

/**
 * Future-facing account identity for v1.4 multi-account and organization accounts.
 */
public record BankAccountId(BankAccountKind kind, UUID id) {
    public static BankAccountId personal(UUID playerId) {
        return new BankAccountId(BankAccountKind.PERSONAL, playerId);
    }

    public BankAccountId {
        if (kind == null) {
            throw new IllegalArgumentException("Account kind cannot be null");
        }
        if (id == null) {
            throw new IllegalArgumentException("Account id cannot be null");
        }
    }
}
