package com.yumesumi.finance.bank.storage;

import com.yumesumi.finance.api.bank.BankAccountRepository;
import com.yumesumi.finance.api.transaction.TransactionRepository;
import com.yumesumi.finance.bank.account.BankAccount;
import com.yumesumi.finance.bank.account.BankAccountType;
import com.yumesumi.finance.bank.admin.BankAccountQuery;
import com.yumesumi.finance.bank.admin.BankAdminService;
import com.yumesumi.finance.bank.admin.TreasuryTransactionRecord;
import com.yumesumi.finance.transaction.TransactionDirection;
import com.yumesumi.finance.transaction.TransactionQuery;
import com.yumesumi.finance.transaction.TransactionRecord;
import com.yumesumi.finance.transaction.TransactionSortField;
import com.yumesumi.finance.transaction.TransactionSortOrder;
import com.yumesumi.finance.transaction.TransactionType;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

public class BankSavedData extends SavedData implements BankAccountRepository, TransactionRepository, BankAdminService {
    private static final String DATA_NAME = "yumesumi_finance_bank";
    public static final UUID TREASURY_UUID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    public static final String TREASURY_NAME = "YB";

    private final Map<UUID, BankAccount> accounts = new HashMap<>();
    private final List<TransactionRecord> transactions = new ArrayList<>();
    private final Map<UUID, List<TransactionRecord>> transactionsByOwner = new HashMap<>();
    private final List<TreasuryTransactionRecord> treasuryTransactions = new ArrayList<>();
    private BigInteger treasuryBalance = BigInteger.ZERO;

    public static BankSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(BankSavedData::new, BankSavedData::load),
                DATA_NAME
        );
    }

    public static BankSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        BankSavedData data = new BankSavedData();

        if (tag.contains("treasury_balance")) {
            try {
                data.treasuryBalance = new BigInteger(tag.getString("treasury_balance"));
            } catch (NumberFormatException ignored) {
                data.treasuryBalance = BigInteger.ZERO;
            }
        }

        ListTag accountsTag = tag.getList("accounts", Tag.TAG_COMPOUND);
        for (int i = 0; i < accountsTag.size(); i++) {
            CompoundTag accountTag = accountsTag.getCompound(i);
            if (!accountTag.hasUUID("owner")) continue;
            UUID owner = accountTag.getUUID("owner");
            BankAccountType type = BankAccountType.PERSONAL;
            try {
                type = BankAccountType.valueOf(accountTag.getString("account_type"));
            } catch (IllegalArgumentException ignored) {}
            String ownerName = accountTag.getString("owner_name");
            long balance = accountTag.getLong("balance_cents");
            long createdAt = accountTag.getLong("created_at");
            long lastActive = accountTag.getLong("last_active_at");
            try {
                BankAccount account = new BankAccount(owner, type, ownerName, balance, createdAt);
                account.setLastActiveAt(lastActive);
                if (accountTag.contains("admin_note")) account.setAdminNote(accountTag.getString("admin_note"));
                account.setFrozen(accountTag.getBoolean("frozen"));
                data.accounts.put(owner, account);
            } catch (IllegalArgumentException ignored) {
            }
        }

        ListTag transactionsTag = tag.getList("transactions", Tag.TAG_COMPOUND);
        for (int i = 0; i < transactionsTag.size(); i++) {
            readTransaction(transactionsTag.getCompound(i)).ifPresent(data::addTransaction);
        }

        ListTag treasuryTransactionsTag = tag.getList("treasury_transactions", Tag.TAG_COMPOUND);
        for (int i = 0; i < treasuryTransactionsTag.size(); i++) {
            readTreasuryTransaction(treasuryTransactionsTag.getCompound(i)).ifPresent(data::addTreasuryTransaction);
        }

        data.ensureTreasuryAccount();
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString("treasury_balance", treasuryBalance.toString());

        ListTag accountsTag = new ListTag();
        for (BankAccount account : accounts.values()) {
            CompoundTag accountTag = new CompoundTag();
            accountTag.putUUID("owner", account.owner());
            accountTag.putString("account_type", account.accountType().name());
            accountTag.putString("owner_name", account.ownerName());
            accountTag.putLong("balance_cents", account.balanceCents());
            accountTag.putLong("created_at", account.createdAt());
            accountTag.putLong("last_active_at", account.lastActiveAt());
            if (!account.adminNote().isEmpty()) accountTag.putString("admin_note", account.adminNote());
            if (account.frozen()) accountTag.putBoolean("frozen", true);
            accountsTag.add(accountTag);
        }
        tag.put("accounts", accountsTag);

        ListTag transactionsTag = new ListTag();
        for (TransactionRecord record : transactions) {
            transactionsTag.add(writeTransaction(record));
        }
        tag.put("transactions", transactionsTag);

        ListTag treasuryTransactionsTag = new ListTag();
        for (TreasuryTransactionRecord record : treasuryTransactions) {
            treasuryTransactionsTag.add(writeTreasuryTransaction(record));
        }
        tag.put("treasury_transactions", treasuryTransactionsTag);
        return tag;
    }

    private void ensureTreasuryAccount() {
        if (!accounts.containsKey(TREASURY_UUID)) {
            BankAccount treasury = new BankAccount(TREASURY_UUID, BankAccountType.OFFICIAL, TREASURY_NAME, 0L, System.currentTimeMillis());
            treasury.setAdminNote("梦澄银行官方国库账户");
            accounts.put(TREASURY_UUID, treasury);
            setDirty();
        }
    }

    // --- BankAccountRepository ---

    @Override
    public Optional<BankAccount> find(UUID owner) {
        return Optional.ofNullable(accounts.get(owner));
    }

    @Override
    public void setBalance(UUID owner, long balanceCents) {
        if (owner == null) throw new IllegalArgumentException("Account owner cannot be null");
        BankAccount account = accounts.get(owner);
        if (account == null) {
            account = new BankAccount(owner, BankAccountType.PERSONAL, "", balanceCents, System.currentTimeMillis());
            accounts.put(owner, account);
            setDirty();
            return;
        }
        account.setBalanceCents(balanceCents);
        account.touchLastActive();
        setDirty();
    }

    // --- TransactionRepository ---

    @Override
    public void append(TransactionRecord record) {
        addTransaction(record);
        pruneOwner(record.owner());
        setDirty();
    }

    @Override
    public List<TransactionRecord> find(UUID owner, TransactionQuery query) {
        if (owner == null) return List.of();
        Stream<TransactionRecord> stream = transactionsByOwner.getOrDefault(owner, List.of()).stream();
        if (query.direction() == TransactionDirection.INCOME) {
            stream = stream.filter(TransactionRecord::income);
        } else if (query.direction() == TransactionDirection.EXPENSE) {
            stream = stream.filter(record -> !record.income());
        }
        String searchText = query.searchText().toLowerCase();
        if (!searchText.isEmpty()) {
            stream = stream.filter(record -> matches(record, searchText));
        }
        Comparator<TransactionRecord> comparator = comparator(query.sortField());
        if (query.sortOrder() == TransactionSortOrder.DESC) comparator = comparator.reversed();
        return stream.sorted(comparator).limit(query.limit()).toList();
    }

    // --- BankAdminService ---

    @Override
    public List<BankAccount> queryAccounts(BankAccountQuery query) {
        Stream<BankAccount> stream = accounts.values().stream()
                .filter(a -> a.accountType() != BankAccountType.OFFICIAL);
        if (query.accountType() != null) {
            stream = stream.filter(a -> a.accountType() == query.accountType());
        }
        if (query.frozenOnly() != null && query.frozenOnly()) {
            stream = stream.filter(BankAccount::frozen);
        }
        if (query.searchText() != null && !query.searchText().isBlank()) {
            String search = query.searchText().toLowerCase();
            stream = stream.filter(a -> a.ownerName().toLowerCase().contains(search)
                    || a.owner().toString().toLowerCase().contains(search)
                    || a.adminNote().toLowerCase().contains(search));
        }
        return stream.sorted(Comparator.comparingLong(BankAccount::lastActiveAt).reversed()).toList();
    }

    @Override
    public BankAccount getTreasury() {
        return accounts.get(TREASURY_UUID);
    }

    @Override
    public BigInteger treasuryBalance() {
        return treasuryBalance;
    }

    public List<TreasuryTransactionRecord> treasuryTransactions() {
        return treasuryTransactions.stream()
                .sorted(Comparator.comparingLong(TreasuryTransactionRecord::createdAt).reversed())
                .limit(10_000)
                .toList();
    }

    @Override
    public void updateAdminNote(UUID targetOwner, String note) {
        BankAccount account = accounts.get(targetOwner);
        if (account == null) throw new IllegalArgumentException("Account not found");
        account.setAdminNote(note);
        setDirty();
    }

    @Override
    public void setAccountBalance(UUID targetOwner, long newBalanceCents) {
        BankAccount account = accounts.get(targetOwner);
        if (account == null) throw new IllegalArgumentException("Account not found");
        account.setBalanceCents(newBalanceCents);
        account.touchLastActive();
        setDirty();
    }

    @Override
    public void addBalance(UUID targetOwner, long deltaCents) {
        BankAccount account = accounts.get(targetOwner);
        if (account == null) throw new IllegalArgumentException("Account not found");
        if (account.accountType() == BankAccountType.OFFICIAL) {
            adjustTreasury(BigInteger.valueOf(deltaCents), "管理员", "国库调账");
        } else {
            account.setBalanceCents(Math.addExact(account.balanceCents(), deltaCents));
        }
        account.touchLastActive();
        setDirty();
    }

    @Override
    public void toggleFreeze(UUID targetOwner) {
        BankAccount account = accounts.get(targetOwner);
        if (account == null) throw new IllegalArgumentException("Account not found");
        account.setFrozen(!account.frozen());
        setDirty();
    }

    @Override
    public void deleteAccount(UUID targetOwner, boolean confirmed) {
        BankAccount account = accounts.get(targetOwner);
        if (account == null) throw new IllegalArgumentException("Account not found");
        if (account.accountType() == BankAccountType.OFFICIAL) throw new IllegalArgumentException("Cannot delete official account");
        if (account.balanceCents() != 0 && !confirmed) {
            throw new IllegalStateException("Account has balance, needs confirmation");
        }
        removeAccount(account);
    }

    @Override
    public void deleteAccountConfirmed(UUID targetOwner) {
        BankAccount account = accounts.get(targetOwner);
        if (account == null) throw new IllegalArgumentException("Account not found");
        if (account.accountType() == BankAccountType.OFFICIAL) throw new IllegalArgumentException("Cannot delete official account");
        removeAccount(account);
    }

    private void removeAccount(BankAccount account) {
        if (account.balanceCents() != 0) {
            adjustTreasury(BigInteger.valueOf(account.balanceCents()), "SYSTEM", "\u5220\u9664\u8d26\u6237\u4f59\u989d\u8f6c\u5165\u56fd\u5e93");
        }
        accounts.remove(account.owner());
        setDirty();
    }

    @Override
    public boolean isOperator(ServerPlayer player) {
        return player.hasPermissions(2);
    }

    // --- Treasury operations for BankService ---

    public void addToTreasury(long cents) {
        addToTreasury(cents, "SYSTEM", "\u56fd\u5e93\u6d41\u6c34");
    }

    public void addToTreasury(long cents, String operatorName, String note) {
        adjustTreasury(BigInteger.valueOf(cents), operatorName, note);
    }

    public void adjustTreasury(BigInteger deltaCents, String operatorName, String note) {
        if (deltaCents == null || deltaCents.equals(BigInteger.ZERO)) return;
        treasuryBalance = treasuryBalance.add(deltaCents);
        addTreasuryTransaction(new TreasuryTransactionRecord(UUID.randomUUID(), deltaCents, treasuryBalance, operatorName, note, System.currentTimeMillis()));
        setDirty();
    }

    public void setTreasuryBalance(BigInteger newBalance, String operatorName, String note) {
        if (newBalance == null) throw new IllegalArgumentException("Treasury balance cannot be null");
        BigInteger delta = newBalance.subtract(treasuryBalance);
        treasuryBalance = newBalance;
        addTreasuryTransaction(new TreasuryTransactionRecord(UUID.randomUUID(), delta, treasuryBalance, operatorName, note, System.currentTimeMillis()));
        setDirty();
    }

    public boolean subtractFromTreasury(long cents) {
        adjustTreasury(BigInteger.valueOf(-cents), "SYSTEM", "\u56fd\u5e93\u652f\u51fa");
        return true;
    }

    // --- Internal helpers ---

    private void addTransaction(TransactionRecord record) {
        transactions.add(record);
        transactionsByOwner.computeIfAbsent(record.owner(), ignored -> new ArrayList<>()).add(record);
    }

    private void addTreasuryTransaction(TreasuryTransactionRecord record) {
        treasuryTransactions.add(record);
        treasuryTransactions.sort(Comparator.comparingLong(TreasuryTransactionRecord::createdAt).reversed());
        while (treasuryTransactions.size() > 10_000) {
            treasuryTransactions.remove(treasuryTransactions.size() - 1);
        }
    }

    private void pruneOwner(UUID owner) {
        List<TransactionRecord> ownerTx = transactionsByOwner.get(owner);
        if (ownerTx == null) return;
        int extra = ownerTx.size() - TransactionRepository.MAX_VISIBLE_RECORDS;
        if (extra <= 0) return;
        List<TransactionRecord> oldest = ownerTx.stream()
                .sorted(Comparator.comparingLong(TransactionRecord::createdGameTime))
                .limit(extra).toList();
        transactions.removeAll(oldest);
        ownerTx.removeAll(oldest);
        if (ownerTx.isEmpty()) transactionsByOwner.remove(owner);
    }

    private static boolean matches(TransactionRecord record, String searchText) {
        return record.counterpartyName().toLowerCase().contains(searchText)
                || (record.counterpartyId() != null && record.counterpartyId().toString().contains(searchText))
                || record.note().toLowerCase().contains(searchText)
                || Long.toString(record.amountCents()).contains(searchText);
    }

    private static Comparator<TransactionRecord> comparator(TransactionSortField sortField) {
        return switch (sortField) {
            case AMOUNT -> Comparator.comparingLong(TransactionRecord::amountCents)
                    .thenComparingLong(TransactionRecord::createdGameTime);
            case TIME -> Comparator.comparingLong(TransactionRecord::createdGameTime)
                    .thenComparing(TransactionRecord::id);
        };
    }

    private static CompoundTag writeTransaction(TransactionRecord record) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", record.id());
        tag.putUUID("owner", record.owner());
        tag.putString("type", record.type().name());
        tag.putLong("amount_cents", record.amountCents());
        tag.putLong("balance_after_cents", record.balanceAfterCents());
        tag.putLong("created_game_time", record.createdGameTime());
        tag.putString("counterparty_name", record.counterpartyName());
        if (record.counterpartyId() != null) tag.putUUID("counterparty_id", record.counterpartyId());
        tag.putString("note", record.note());
        return tag;
    }

    private static Optional<TransactionRecord> readTransaction(CompoundTag tag) {
        if (!tag.hasUUID("id") || !tag.hasUUID("owner")) return Optional.empty();
        UUID counterpartyId = tag.hasUUID("counterparty_id") ? tag.getUUID("counterparty_id") : null;
        try {
            return Optional.of(new TransactionRecord(
                    tag.getUUID("id"), tag.getUUID("owner"),
                    TransactionType.valueOf(tag.getString("type")),
                    tag.getLong("amount_cents"), Math.max(0L, tag.getLong("balance_after_cents")),
                    tag.getLong("created_game_time"), tag.getString("counterparty_name"),
                    counterpartyId, tag.getString("note")
            ));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static CompoundTag writeTreasuryTransaction(TreasuryTransactionRecord record) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", record.id());
        tag.putString("delta_cents", record.deltaCents().toString());
        tag.putString("balance_after_cents", record.balanceAfterCents().toString());
        tag.putString("operator_name", record.operatorName());
        tag.putString("note", record.note());
        tag.putLong("created_at", record.createdAt());
        return tag;
    }

    private static Optional<TreasuryTransactionRecord> readTreasuryTransaction(CompoundTag tag) {
        if (!tag.hasUUID("id")) return Optional.empty();
        try {
            return Optional.of(new TreasuryTransactionRecord(
                    tag.getUUID("id"),
                    new BigInteger(tag.getString("delta_cents")),
                    new BigInteger(tag.getString("balance_after_cents")),
                    tag.getString("operator_name"),
                    tag.getString("note"),
                    tag.getLong("created_at")
            ));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }
}
