package com.yumesumi.finance.bank.service;

import com.yumesumi.finance.api.bank.BankAccountRepository;
import com.yumesumi.finance.api.bank.BankService;
import com.yumesumi.finance.api.money.CashWallet;
import com.yumesumi.finance.api.transaction.TransactionRepository;
import com.yumesumi.finance.bank.BankAccessChannel;
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
    public TransactionResult deposit(ServerPlayer player, long amountCents, BankAccessChannel channel) {
        channel = normalizeChannel(channel);
        long balance = balanceOf(player);
        if (amountCents <= 0) {
            return TransactionResult.failure(TransactionType.DEPOSIT, amountCents, balance, "message.yumesumi_finance.deposit.invalid_amount");
        }
        try {
            if (!cashWallet.remove(player, amountCents)) {
                return TransactionResult.failure(TransactionType.DEPOSIT, amountCents, balance, "message.yumesumi_finance.deposit.not_enough_cash");
            }
        } catch (ArithmeticException exception) {
            return TransactionResult.failure(TransactionType.DEPOSIT, amountCents, balance, "message.yumesumi_finance.deposit.too_large");
        }

        long newBalance;
        try {
            newBalance = Math.addExact(balance, amountCents);
        } catch (ArithmeticException exception) {
            cashWallet.give(player, amountCents);
            return TransactionResult.failure(TransactionType.DEPOSIT, amountCents, balance, "message.yumesumi_finance.deposit.too_large");
        }
        try {
            applySingleAccountTransaction(player.getUUID(), TransactionType.DEPOSIT, amountCents, newBalance, channel.depositLedgerName(), null, "");
        } catch (RuntimeException exception) {
            cashWallet.give(player, amountCents);
            throw exception;
        }
        return TransactionResult.success(TransactionType.DEPOSIT, amountCents, newBalance, "message.yumesumi_finance.deposit.success");
    }

    @Override
    public TransactionResult depositAll(ServerPlayer player, BankAccessChannel channel) {
        long amountCents;
        try {
            amountCents = cashWallet.count(player);
        } catch (ArithmeticException exception) {
            return TransactionResult.failure(TransactionType.DEPOSIT, Long.MAX_VALUE, balanceOf(player), "message.yumesumi_finance.deposit.too_large");
        }
        return deposit(player, amountCents, channel);
    }

    @Override
    public TransactionResult withdraw(ServerPlayer player, long amountCents, BankAccessChannel channel) {
        channel = normalizeChannel(channel);
        long balance = balanceOf(player);
        if (amountCents <= 0) {
            return TransactionResult.failure(TransactionType.WITHDRAW, amountCents, balance, "message.yumesumi_finance.withdraw.invalid_amount");
        }
        if (balance < amountCents) {
            return TransactionResult.failure(TransactionType.WITHDRAW, amountCents, balance, "message.yumesumi_finance.withdraw.not_enough_balance");
        }

        long newBalance = balance - amountCents;
        applySingleAccountTransaction(player.getUUID(), TransactionType.WITHDRAW, amountCents, newBalance, channel.withdrawLedgerName(), null, "");
        try {
            cashWallet.give(player, amountCents);
        } catch (RuntimeException exception) {
            accounts.setBalance(player.getUUID(), balance);
            throw exception;
        }
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
        if (BankSavedData.TREASURY_UUID.equals(target)) {
            return transferToTreasury(player, amountCents, note);
        }
        if (accounts.find(target).isEmpty()) {
            return TransactionResult.failure(TransactionType.TRANSFER_OUT, amountCents, balance, "message.yumesumi_finance.transfer.account_not_found");
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
        applyTransferTransaction(player.getUUID(), target, amountCents, senderBalance, newReceiverBalance, senderName, receiverName, safeNote);
        return TransactionResult.success(TransactionType.TRANSFER_OUT, amountCents, senderBalance, "message.yumesumi_finance.transfer.success");
    }

    private TransactionResult transferToTreasury(ServerPlayer player, long amountCents, String note) {
        long balance = balanceOf(player);
        if (balance < amountCents) {
            return TransactionResult.failure(TransactionType.TRANSFER_OUT, amountCents, balance, "message.yumesumi_finance.transfer.not_enough_balance");
        }
        if (!(accounts instanceof BankSavedData savedData)) {
            return TransactionResult.failure(TransactionType.TRANSFER_OUT, amountCents, balance, "message.yumesumi_finance.transfer.account_not_found");
        }

        long senderBalance = balance - amountCents;
        String safeNote = sanitizeNote(note);
        long originalSenderBalance = accounts.balanceOf(player.getUUID());
        boolean treasuryChanged = false;
        accounts.setBalance(player.getUUID(), senderBalance);
        try {
            savedData.addToTreasury(amountCents, player.getGameProfile().getName(), safeNote.isBlank() ? "个人转账至国库" : safeNote);
            treasuryChanged = true;
            record(player.getUUID(), TransactionType.TRANSFER_OUT, amountCents, senderBalance, BankSavedData.TREASURY_NAME, BankSavedData.TREASURY_UUID, safeNote);
        } catch (RuntimeException exception) {
            accounts.setBalance(player.getUUID(), originalSenderBalance);
            if (treasuryChanged) {
                savedData.addToTreasury(-amountCents, "SYSTEM", "个人转账至国库回滚");
            }
            throw exception;
        }
        return TransactionResult.success(TransactionType.TRANSFER_OUT, amountCents, senderBalance, "message.yumesumi_finance.transfer.success");
    }

    @Override
    public List<TransactionRecord> recentTransactions(ServerPlayer player) {
        return transactions.find(player.getUUID(), TransactionQuery.latest());
    }

    private void applySingleAccountTransaction(UUID owner, TransactionType type, long amountCents, long balanceAfterCents, String counterpartyName, UUID counterpartyId, String note) {
        long originalBalance = accounts.balanceOf(owner);
        accounts.setBalance(owner, balanceAfterCents);
        try {
            record(owner, type, amountCents, balanceAfterCents, counterpartyName, counterpartyId, note);
        } catch (RuntimeException exception) {
            accounts.setBalance(owner, originalBalance);
            throw exception;
        }
    }

    private void applyTransferTransaction(UUID sender, UUID receiver, long amountCents, long senderBalance, long receiverBalance, String senderName, String receiverName, String note) {
        long originalSenderBalance = accounts.balanceOf(sender);
        long originalReceiverBalance = accounts.balanceOf(receiver);
        accounts.setBalance(sender, senderBalance);
        try {
            accounts.setBalance(receiver, receiverBalance);
            record(sender, TransactionType.TRANSFER_OUT, amountCents, senderBalance, receiverName, receiver, note);
            record(receiver, TransactionType.TRANSFER_IN, amountCents, receiverBalance, senderName, sender, note);
        } catch (RuntimeException exception) {
            accounts.setBalance(sender, originalSenderBalance);
            accounts.setBalance(receiver, originalReceiverBalance);
            throw exception;
        }
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

    private static BankAccessChannel normalizeChannel(BankAccessChannel channel) {
        return channel == null ? BankAccessChannel.ATM : channel;
    }
}
