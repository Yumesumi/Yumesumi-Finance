package com.yumesumi.finance.bank.service;

import com.yumesumi.finance.bank.account.BankAccount;
import com.yumesumi.finance.bank.admin.BankAccountQuery;
import com.yumesumi.finance.bank.admin.BankAdminService;
import com.yumesumi.finance.bank.admin.TreasuryTransactionRecord;
import com.yumesumi.finance.bank.storage.BankSavedData;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class ServerBankAdminService implements BankAdminService {
    private final BankSavedData data;

    public ServerBankAdminService(BankSavedData data) {
        this.data = data;
    }

    public static ServerBankAdminService forServer(MinecraftServer server) {
        return new ServerBankAdminService(BankSavedData.get(server));
    }

    @Override
    public List<BankAccount> queryAccounts(BankAccountQuery query) {
        return data.queryAccounts(query);
    }

    @Override
    public BankAccount getTreasury() {
        return data.getTreasury();
    }

    @Override
    public BigInteger treasuryBalance() {
        return data.treasuryBalance();
    }

    public List<TreasuryTransactionRecord> treasuryTransactions() {
        return data.treasuryTransactions();
    }

    public void adjustTreasury(BigInteger deltaCents, String operatorName) {
        data.adjustTreasury(deltaCents, operatorName, "国库调账");
    }

    public void setTreasuryBalance(BigInteger balanceCents, String operatorName) {
        data.setTreasuryBalance(balanceCents, operatorName, "国库调账");
    }

    @Override
    public void updateAdminNote(UUID targetOwner, String note) {
        data.updateAdminNote(targetOwner, note);
    }

    @Override
    public void setAccountBalance(UUID targetOwner, long newBalanceCents) {
        data.setAccountBalance(targetOwner, newBalanceCents);
    }

    @Override
    public void addBalance(UUID targetOwner, long deltaCents) {
        data.addBalance(targetOwner, deltaCents);
    }

    @Override
    public void toggleFreeze(UUID targetOwner) {
        data.toggleFreeze(targetOwner);
    }

    @Override
    public void deleteAccount(UUID targetOwner, boolean confirmed) {
        data.deleteAccount(targetOwner, confirmed);
    }

    @Override
    public void deleteAccountConfirmed(UUID targetOwner) {
        data.deleteAccountConfirmed(targetOwner);
    }

    @Override
    public boolean isOperator(ServerPlayer player) {
        return player.hasPermissions(2);
    }
}
