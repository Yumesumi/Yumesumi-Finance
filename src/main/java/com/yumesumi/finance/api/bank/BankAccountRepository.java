package com.yumesumi.finance.api.bank;

import com.yumesumi.finance.bank.account.BankAccount;
import java.util.Optional;
import java.util.UUID;

/**
 * 银行账户仓储接口。
 *
 * <p>业务层只依赖这个接口，不关心账户最终存到 SavedData、数据库还是未来的跨服服务。</p>
 */
public interface BankAccountRepository {
    Optional<BankAccount> find(UUID owner);

    default long balanceOf(UUID owner) {
        return find(owner).map(BankAccount::balanceCents).orElse(0L);
    }

    void setBalance(UUID owner, long balanceCents);
}
