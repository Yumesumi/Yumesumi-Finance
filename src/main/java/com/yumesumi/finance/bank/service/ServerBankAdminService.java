package com.yumesumi.finance.bank.service;

import com.yumesumi.finance.bank.account.BankAccount;
import com.yumesumi.finance.bank.admin.BankAccountQuery;
import com.yumesumi.finance.bank.admin.BankAdminAction;
import com.yumesumi.finance.bank.admin.BankAdminService;
import com.yumesumi.finance.bank.admin.TreasuryTransactionRecord;
import com.yumesumi.finance.bank.enterprise.EnterpriseAccount;
import com.yumesumi.finance.bank.enterprise.EnterpriseTransactionRecord;
import com.yumesumi.finance.bank.enterprise.EnterpriseTransactionType;
import com.yumesumi.finance.bank.storage.BankSavedData;
import com.yumesumi.finance.bank.storage.EnterpriseSavedData;
import com.yumesumi.finance.bank.transfer.TransferTarget;
import com.yumesumi.finance.bank.transfer.TransferTargetResolver;
import com.yumesumi.finance.transaction.TransactionRecord;
import com.yumesumi.finance.transaction.TransactionType;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
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

    public void transferFromTreasury(MinecraftServer server, ServerPlayer operator, long amountCents, String transferText) {
        if (amountCents <= 0) throw new IllegalArgumentException("Invalid amount");
        String[] parts = (transferText == null ? "" : transferText).split("\\|", 2);
        String targetText = parts.length > 0 ? parts[0].trim() : "";
        String note = parts.length > 1 ? parts[1].trim() : "";
        if (targetText.isBlank()) throw new IllegalArgumentException("Target required");
        if (note.length() > 64) note = note.substring(0, 64);

        EnterpriseSavedData enterpriseData = EnterpriseSavedData.get(server);
        EnterpriseAccount enterprise = enterpriseData.findByCode(targetText).orElse(null);
        String operatorName = operator.getGameProfile().getName();
        if (enterprise != null) {
            long originalBalance = enterprise.balanceCents();
            long newBalance = Math.addExact(originalBalance, amountCents);
            EnterpriseTransactionRecord record = new EnterpriseTransactionRecord(UUID.randomUUID(), enterprise.id(), EnterpriseTransactionType.TRANSFER_IN,
                    amountCents, newBalance, operator.getUUID(), operatorName, BankSavedData.TREASURY_UUID, BankSavedData.TREASURY_NAME,
                    note.isBlank() ? "国库转账" : note, System.currentTimeMillis());
            try {
                enterprise.setBalanceCents(newBalance);
                enterprise.appendTransaction(record);
                enterpriseData.setDirtyAndTouch(enterprise);
                data.addToTreasury(-amountCents, operatorName, "国库转账至企业 " + enterprise.code());
            } catch (RuntimeException exception) {
                enterprise.removeTransaction(record.id());
                enterprise.setBalanceCents(originalBalance);
                enterpriseData.setDirtyAndTouch(enterprise);
                throw exception;
            }
            return;
        }

        TransferTarget target = TransferTargetResolver.resolve(server, targetText).orElse(null);
        if (target == null || BankSavedData.TREASURY_UUID.equals(target.owner()) || data.find(target.owner()).isEmpty()) {
            throw new IllegalArgumentException("Target account not found");
        }
        long originalBalance = data.balanceOf(target.owner());
        long newBalance = Math.addExact(originalBalance, amountCents);
        TransactionRecord record = new TransactionRecord(UUID.randomUUID(), target.owner(), TransactionType.TRANSFER_IN, amountCents, newBalance,
                System.currentTimeMillis(), BankSavedData.TREASURY_NAME, BankSavedData.TREASURY_UUID,
                note.isBlank() ? "国库转账" : note);
        try {
            data.setBalance(target.owner(), newBalance);
            data.append(record);
            data.addToTreasury(-amountCents, operatorName, "国库转账至玩家 " + target.displayName());
        } catch (RuntimeException exception) {
            data.removeTransaction(record.id());
            data.setBalance(target.owner(), originalBalance);
            throw exception;
        }
    }

    public Optional<String> handleEnterpriseAction(MinecraftServer server, ServerPlayer operator, UUID enterpriseId,
                                                   BankAdminAction action, long amountCents, String text) {
        EnterpriseSavedData enterpriseData = EnterpriseSavedData.get(server);
        EnterpriseAccount enterprise = enterpriseData.findEnterprise(enterpriseId).orElse(null);
        if (enterprise == null) return Optional.empty();
        return switch (action) {
            case UPDATE_NOTE -> {
                enterpriseData.updateEnterpriseSettings(enterprise, enterprise.name(), enterprise.code(), text);
                yield Optional.of("message.yumesumi_finance.admin.note_updated");
            }
            case SET_BALANCE -> {
                enterprise.setBalanceCents(amountCents);
                enterpriseData.setDirtyAndTouch(enterprise);
                yield Optional.of("message.yumesumi_finance.admin.balance_set");
            }
            case ADD_BALANCE -> {
                enterprise.setBalanceCents(Math.addExact(enterprise.balanceCents(), amountCents));
                enterpriseData.setDirtyAndTouch(enterprise);
                yield Optional.of("message.yumesumi_finance.admin.balance_added");
            }
            case TOGGLE_FREEZE -> Optional.of("message.yumesumi_finance.admin.unsupported_enterprise_freeze");
            case DELETE -> {
                if (enterprise.balanceCents() != 0) throw new IllegalStateException("Enterprise has balance");
                enterpriseData.deleteEnterprise(enterprise);
                yield Optional.of("message.yumesumi_finance.admin.deleted");
            }
            case DELETE_CONFIRMED -> {
                if (enterprise.balanceCents() > 0) {
                    data.addToTreasury(enterprise.balanceCents(), operator.getGameProfile().getName(), "管理员删除企业余额转入国库：" + enterprise.code());
                }
                enterpriseData.deleteEnterprise(enterprise);
                yield Optional.of("message.yumesumi_finance.admin.deleted");
            }
            default -> Optional.empty();
        };
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
