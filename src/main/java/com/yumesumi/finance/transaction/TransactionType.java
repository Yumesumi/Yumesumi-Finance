package com.yumesumi.finance.transaction;

public enum TransactionType {
    DEPOSIT(true),
    WITHDRAW(false),
    TRANSFER_IN(true),
    TRANSFER_OUT(false);

    private final boolean income;

    TransactionType(boolean income) {
        this.income = income;
    }

    public boolean income() {
        return income;
    }
}
