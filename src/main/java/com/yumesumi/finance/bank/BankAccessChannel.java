package com.yumesumi.finance.bank;

public enum BankAccessChannel {
    ATM("ATM存入", "ATM取现"),
    CARD("随身银行存入", "随身银行取现");

    private final String depositLedgerName;
    private final String withdrawLedgerName;

    BankAccessChannel(String depositLedgerName, String withdrawLedgerName) {
        this.depositLedgerName = depositLedgerName;
        this.withdrawLedgerName = withdrawLedgerName;
    }

    public String depositLedgerName() {
        return depositLedgerName;
    }

    public String withdrawLedgerName() {
        return withdrawLedgerName;
    }
}
