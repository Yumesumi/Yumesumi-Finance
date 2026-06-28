package com.yumesumi.finance.bank.admin;

import com.yumesumi.finance.bank.account.BankAccountType;

public record BankAccountQuery(
        BankAccountType accountType,
        String searchText,
        Boolean frozenOnly
) {
    public static BankAccountQuery byType(BankAccountType type) {
        return new BankAccountQuery(type, null, null);
    }
}
