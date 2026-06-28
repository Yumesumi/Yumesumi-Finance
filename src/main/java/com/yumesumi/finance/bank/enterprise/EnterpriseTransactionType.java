package com.yumesumi.finance.bank.enterprise;

public enum EnterpriseTransactionType {
    REGISTER_FEE(false),
    TRANSFER_IN(true),
    TRANSFER_OUT(false),
    WITHDRAW(false),
    ADMIN_ADJUST(true);

    private final boolean income;

    EnterpriseTransactionType(boolean income) {
        this.income = income;
    }

    public boolean income() {
        return income;
    }
}
