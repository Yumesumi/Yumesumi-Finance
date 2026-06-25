package com.yumesumi.finance.bank.service;

import com.yumesumi.finance.api.bank.BankAccountRepository;
import com.yumesumi.finance.api.bank.BankService;
import com.yumesumi.finance.api.money.CashWallet;
import com.yumesumi.finance.bank.storage.BankSavedData;
import com.yumesumi.finance.money.cash.PlayerCashWallet;
import com.yumesumi.finance.transaction.TransactionResult;
import com.yumesumi.finance.transaction.TransactionType;
import net.minecraft.server.level.ServerPlayer;

/**
 * 服务端银行业务实现。
 *
 * <p>这里不直接打开 GUI，也不直接发送网络包，只处理账户余额和现金转移。</p>
 */
public class ServerBankService implements BankService {
    private final BankAccountRepository accounts;
    private final CashWallet cashWallet;

    public ServerBankService(BankAccountRepository accounts, CashWallet cashWallet) {
        this.accounts = accounts;
        this.cashWallet = cashWallet;
    }

    public static ServerBankService forServerPlayer(ServerPlayer player) {
        return new ServerBankService(BankSavedData.get(player.server), new PlayerCashWallet());
    }

    @Override
    public long balanceOf(ServerPlayer player) {
        return accounts.balanceOf(player.getUUID());
    }

    @Override
    public TransactionResult deposit(ServerPlayer player, long amountCents) {
        long balance = balanceOf(player);
        if (amountCents <= 0) {
            return TransactionResult.failure(TransactionType.DEPOSIT, amountCents, balance, "message.yumesumi_finance.deposit.invalid_amount");
        }
        if (!cashWallet.remove(player, amountCents)) {
            return TransactionResult.failure(TransactionType.DEPOSIT, amountCents, balance, "message.yumesumi_finance.deposit.not_enough_cash");
        }

        long newBalance = Math.addExact(balance, amountCents);
        accounts.setBalance(player.getUUID(), newBalance);
        return TransactionResult.success(TransactionType.DEPOSIT, amountCents, newBalance, "message.yumesumi_finance.deposit.success");
    }

    @Override
    public TransactionResult withdraw(ServerPlayer player, long amountCents) {
        long balance = balanceOf(player);
        if (amountCents <= 0) {
            return TransactionResult.failure(TransactionType.WITHDRAW, amountCents, balance, "message.yumesumi_finance.withdraw.invalid_amount");
        }
        if (balance < amountCents) {
            return TransactionResult.failure(TransactionType.WITHDRAW, amountCents, balance, "message.yumesumi_finance.withdraw.not_enough_balance");
        }

        long newBalance = balance - amountCents;
        accounts.setBalance(player.getUUID(), newBalance);
        cashWallet.give(player, amountCents);
        return TransactionResult.success(TransactionType.WITHDRAW, amountCents, newBalance, "message.yumesumi_finance.withdraw.success");
    }
}
