package com.yumesumi.finance.bank.storage;

import com.yumesumi.finance.api.bank.BankAccountRepository;
import com.yumesumi.finance.api.transaction.TransactionRepository;
import com.yumesumi.finance.bank.account.BankAccount;
import com.yumesumi.finance.transaction.TransactionDirection;
import com.yumesumi.finance.transaction.TransactionQuery;
import com.yumesumi.finance.transaction.TransactionRecord;
import com.yumesumi.finance.transaction.TransactionSortField;
import com.yumesumi.finance.transaction.TransactionSortOrder;
import com.yumesumi.finance.transaction.TransactionType;
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
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 服务端账户存档。
 *
 * <p>数据挂在主世界 SavedData 上，保证单人和服务端都由服务端权威保存。</p>
 */
public class BankSavedData extends SavedData implements BankAccountRepository, TransactionRepository {
    private static final String DATA_NAME = "yumesumi_finance_bank";
    private final Map<UUID, BankAccount> accounts = new HashMap<>();
    private final List<TransactionRecord> transactions = new ArrayList<>();
    private final Map<UUID, List<TransactionRecord>> transactionsByOwner = new HashMap<>();

    public static BankSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(BankSavedData::new, BankSavedData::load),
                DATA_NAME
        );
    }

    public static BankSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        BankSavedData data = new BankSavedData();
        ListTag accountsTag = tag.getList("accounts", Tag.TAG_COMPOUND);
        for (int i = 0; i < accountsTag.size(); i++) {
            CompoundTag accountTag = accountsTag.getCompound(i);
            if (!accountTag.hasUUID("owner")) {
                continue;
            }
            UUID owner = accountTag.getUUID("owner");
            long balance = Math.max(0L, accountTag.getLong("balance_cents"));
            data.accounts.put(owner, new BankAccount(owner, balance));
        }

        ListTag transactionsTag = tag.getList("transactions", Tag.TAG_COMPOUND);
        for (int i = 0; i < transactionsTag.size(); i++) {
            CompoundTag transactionTag = transactionsTag.getCompound(i);
            readTransaction(transactionTag).ifPresent(data::addTransaction);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag accountsTag = new ListTag();
        for (BankAccount account : accounts.values()) {
            CompoundTag accountTag = new CompoundTag();
            accountTag.putUUID("owner", account.owner());
            accountTag.putLong("balance_cents", account.balanceCents());
            accountsTag.add(accountTag);
        }
        tag.put("accounts", accountsTag);

        ListTag transactionsTag = new ListTag();
        for (TransactionRecord transaction : transactions) {
            transactionsTag.add(writeTransaction(transaction));
        }
        tag.put("transactions", transactionsTag);
        return tag;
    }

    @Override
    public Optional<BankAccount> find(UUID owner) {
        return Optional.ofNullable(accounts.get(owner));
    }

    @Override
    public void setBalance(UUID owner, long balanceCents) {
        if (owner == null) {
            throw new IllegalArgumentException("Account owner cannot be null");
        }
        if (balanceCents < 0) {
            throw new IllegalArgumentException("Account balance cannot be negative");
        }
        accounts.put(owner, new BankAccount(owner, balanceCents));
        setDirty();
    }

    @Override
    public void append(TransactionRecord record) {
        addTransaction(record);
        pruneOwner(record.owner());
        setDirty();
    }

    @Override
    public List<TransactionRecord> find(UUID owner, TransactionQuery query) {
        if (owner == null) {
            return List.of();
        }

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
        if (query.sortOrder() == TransactionSortOrder.DESC) {
            comparator = comparator.reversed();
        }

        return stream.sorted(comparator)
                .limit(query.limit())
                .toList();
    }

    private static boolean matches(TransactionRecord record, String searchText) {
        return record.counterpartyName().toLowerCase().contains(searchText)
                || counterpartyIdText(record).contains(searchText)
                || record.note().toLowerCase().contains(searchText)
                || amountText(record).contains(searchText);
    }

    private static String counterpartyIdText(TransactionRecord record) {
        return record.counterpartyId() == null ? "" : record.counterpartyId().toString().toLowerCase();
    }

    private static String amountText(TransactionRecord record) {
        long cents = record.amountCents();
        long yuan = cents / 100L;
        long remain = Math.abs(cents % 100L);
        return Long.toString(cents) + " " + yuan + "." + (remain < 10 ? "0" : "") + remain;
    }

    private static Comparator<TransactionRecord> comparator(TransactionSortField sortField) {
        return switch (sortField) {
            case AMOUNT -> Comparator.comparingLong(TransactionRecord::amountCents)
                    .thenComparingLong(TransactionRecord::createdGameTime);
            case TIME -> Comparator.comparingLong(TransactionRecord::createdGameTime)
                    .thenComparing(TransactionRecord::id);
        };
    }

    private void pruneOwner(UUID owner) {
        List<TransactionRecord> ownerTransactions = transactionsByOwner.get(owner);
        if (ownerTransactions == null) {
            return;
        }
        int extra = ownerTransactions.size() - TransactionRepository.MAX_VISIBLE_RECORDS;
        if (extra <= 0) {
            return;
        }

        List<TransactionRecord> oldest = ownerTransactions.stream()
                .sorted(Comparator.comparingLong(TransactionRecord::createdGameTime))
                .limit(extra)
                .toList();
        transactions.removeAll(oldest);
        ownerTransactions.removeAll(oldest);
        if (ownerTransactions.isEmpty()) {
            transactionsByOwner.remove(owner);
        }
    }

    private void addTransaction(TransactionRecord record) {
        transactions.add(record);
        transactionsByOwner.computeIfAbsent(record.owner(), ignored -> new ArrayList<>()).add(record);
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
        if (record.counterpartyId() != null) {
            tag.putUUID("counterparty_id", record.counterpartyId());
        }
        tag.putString("note", record.note());
        return tag;
    }

    private static Optional<TransactionRecord> readTransaction(CompoundTag tag) {
        if (!tag.hasUUID("id") || !tag.hasUUID("owner")) {
            return Optional.empty();
        }
        UUID counterpartyId = tag.hasUUID("counterparty_id") ? tag.getUUID("counterparty_id") : null;
        try {
            return Optional.of(new TransactionRecord(
                    tag.getUUID("id"),
                    tag.getUUID("owner"),
                    TransactionType.valueOf(tag.getString("type")),
                    tag.getLong("amount_cents"),
                    Math.max(0L, tag.getLong("balance_after_cents")),
                    tag.getLong("created_game_time"),
                    tag.getString("counterparty_name"),
                    counterpartyId,
                    tag.getString("note")
            ));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
