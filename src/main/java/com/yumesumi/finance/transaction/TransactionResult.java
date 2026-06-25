package com.yumesumi.finance.transaction;

/**
 * 一次银行操作的结果。
 *
 * @param success 是否成功
 * @param type 交易类型
 * @param amountCents 本次交易金额，单位为分
 * @param balanceCents 操作后的账户余额
 * @param messageKey 结果消息翻译键
 */
public record TransactionResult(
        boolean success,
        TransactionType type,
        long amountCents,
        long balanceCents,
        String messageKey
) {
    public static TransactionResult success(TransactionType type, long amountCents, long balanceCents, String messageKey) {
        return new TransactionResult(true, type, amountCents, balanceCents, messageKey);
    }

    public static TransactionResult failure(TransactionType type, long amountCents, long balanceCents, String messageKey) {
        return new TransactionResult(false, type, amountCents, balanceCents, messageKey);
    }
}
