package com.yumesumi.finance.bank.service;

import com.yumesumi.finance.api.bank.BankAccountRepository;
import com.yumesumi.finance.api.bank.BankService;
import com.yumesumi.finance.api.money.CashWallet;
import com.yumesumi.finance.api.transaction.TransactionRepository;
import com.yumesumi.finance.bank.storage.BankSavedData;
import com.yumesumi.finance.money.cash.PlayerCashWallet;
import com.yumesumi.finance.transaction.TransactionQuery;
import com.yumesumi.finance.transaction.TransactionRecord;
import com.yumesumi.finance.transaction.TransactionResult;
import com.yumesumi.finance.transaction.TransactionType;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * 服务端银行业务实现。
 *
 * <p>这里不直接打开 GUI，也不直接发送网络包，只处理账户余额和现金转移。</p>
 */
public class ServerBankService implements BankService {
    private final BankAccountRepository accounts;
    private final TransactionRepository transactions;
    private final CashWallet cashWallet;

    public ServerBankService(BankAccountRepository accounts, TransactionRepository transactions, CashWallet cashWallet) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.cashWallet = cashWallet;
    }

    public static ServerBankService forServerPlayer(ServerPlayer player) {
        BankSavedData savedData = BankSavedData.get(player.server);
        return new ServerBankService(savedData, savedData, new PlayerCashWallet());
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

        long newBalance;
        try {
            newBalance = Math.addExact(balance, amountCents);
        } catch (ArithmeticException exception) {
            cashWallet.give(player, amountCents);
            return TransactionResult.failure(TransactionType.DEPOSIT, amountCents, balance, "message.yumesumi_finance.deposit.too_large");
        }
        accounts.setBalance(player.getUUID(), newBalance);
        record(player.getUUID(), TransactionType.DEPOSIT, amountCents, newBalance, "", null, "");
        return TransactionResult.success(TransactionType.DEPOSIT, amountCents, newBalance, "message.yumesumi_finance.deposit.success");
    }

    @Override
    public TransactionResult depositAll(ServerPlayer player) {
        return deposit(player, cashWallet.count(player));
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
        record(player.getUUID(), TransactionType.WITHDRAW, amountCents, newBalance, "", null, "");
        return TransactionResult.success(TransactionType.WITHDRAW, amountCents, newBalance, "message.yumesumi_finance.withdraw.success");
    }

    @Override
    public TransactionResult transfer(ServerPlayer player, UUID target, String targetName, long amountCents, String note) {
        long balance = balanceOf(player);
        if (amountCents <= 0) {
            return TransactionResult.failure(TransactionType.TRANSFER_OUT, amountCents, balance, "message.yumesumi_finance.transfer.invalid_amount");
        }
        if (target == null) {
            return TransactionResult.failure(TransactionType.TRANSFER_OUT, amountCents, balance, "message.yumesumi_finance.transfer.invalid_target");
        }
        if (player.getUUID().equals(target)) {
            return TransactionResult.failure(TransactionType.TRANSFER_OUT, amountCents, balance, "message.yumesumi_finance.transfer.self");
        }
        if (balance < amountCents) {
            return TransactionResult.failure(TransactionType.TRANSFER_OUT, amountCents, balance, "message.yumesumi_finance.transfer.not_enough_balance");
        }

        long receiverBalance = accounts.balanceOf(target);
        long senderBalance = balance - amountCents;
        long newReceiverBalance;
        try {
            newReceiverBalance = Math.addExact(receiverBalance, amountCents);
        } catch (ArithmeticException exception) {
            return TransactionResult.failure(TransactionType.TRANSFER_OUT, amountCents, balance, "message.yumesumi_finance.transfer.too_large");
        }

        String senderName = player.getGameProfile().getName();
        String receiverName = targetName == null || targetName.isBlank() ? target.toString() : targetName;
        String safeNote = sanitizeNote(note);
        accounts.setBalance(player.getUUID(), senderBalance);
        accounts.setBalance(target, newReceiverBalance);
        record(player.getUUID(), TransactionType.TRANSFER_OUT, amountCents, senderBalance, receiverName, target, safeNote);
        record(target, TransactionType.TRANSFER_IN, amountCents, newReceiverBalance, senderName, player.getUUID(), safeNote);
        return TransactionResult.success(TransactionType.TRANSFER_OUT, amountCents, senderBalance, "message.yumesumi_finance.transfer.success");
    }

    @Override
    public List<TransactionRecord> recentTransactions(ServerPlayer player) {
        return transactions.find(player.getUUID(), TransactionQuery.latest());
    }

    private void record(UUID owner, TransactionType type, long amountCents, long balanceAfterCents, String counterpartyName, UUID counterpartyId, String note) {
        transactions.append(new TransactionRecord(
                UUID.randomUUID(),
                owner,
                type,
                amountCents,
                balanceAfterCents,
                System.currentTimeMillis(),
                counterpartyName,
                counterpartyId,
                note
        ));
    }

    private static String sanitizeNote(String note) {
        String trimmed = note == null ? "" : note.trim();
        return trimmed.length() <= 64 ? trimmed : trimmed.substring(0, 64);
    }
}
