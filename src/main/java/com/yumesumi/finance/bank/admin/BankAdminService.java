package com.yumesumi.finance.bank.admin;

import com.yumesumi.finance.bank.account.BankAccount;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

public interface BankAdminService {
    List<BankAccount> queryAccounts(BankAccountQuery query);
    BankAccount getTreasury();
    BigInteger treasuryBalance();
    void updateAdminNote(UUID targetOwner, String note);
    void setAccountBalance(UUID targetOwner, long newBalanceCents);
    void addBalance(UUID targetOwner, long deltaCents);
    void toggleFreeze(UUID targetOwner);
    void deleteAccount(UUID targetOwner, boolean confirmed);
    void deleteAccountConfirmed(UUID targetOwner);
    boolean isOperator(ServerPlayer player);
}
