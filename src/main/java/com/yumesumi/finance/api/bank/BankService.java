package com.yumesumi.finance.api.bank;

import com.yumesumi.finance.transaction.TransactionResult;
import net.minecraft.server.level.ServerPlayer;

/**
 * 银行业务接口。
 *
 * <p>GUI、网络包和方块交互只调用这些用例，避免直接操作账户存储或玩家背包。</p>
 */
public interface BankService {
    long balanceOf(ServerPlayer player);

    TransactionResult deposit(ServerPlayer player, long amountCents);

    TransactionResult withdraw(ServerPlayer player, long amountCents);
}
