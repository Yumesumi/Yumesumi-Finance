package com.yumesumi.finance.bank.account;

import java.util.UUID;

/**
 * 梦澄金融账户。
 *
 * <p>金额统一使用“分”为最小单位，避免浮点数造成金融精度问题。</p>
 */
public record BankAccount(UUID owner, long balanceCents) {
    public BankAccount {
        if (balanceCents < 0) {
            throw new IllegalArgumentException("账户余额不能为负数");
        }
    }
}
