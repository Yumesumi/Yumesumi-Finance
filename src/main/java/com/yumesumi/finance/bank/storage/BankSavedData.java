package com.yumesumi.finance.bank.storage;

import com.yumesumi.finance.api.bank.BankAccountRepository;
import com.yumesumi.finance.bank.account.BankAccount;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
public class BankSavedData extends SavedData implements BankAccountRepository {
    private static final String DATA_NAME = "yumesumi_finance_bank";
    private final Map<UUID, BankAccount> accounts = new HashMap<>();

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
            UUID owner = accountTag.getUUID("owner");
            long balance = accountTag.getLong("balance_cents");
            data.accounts.put(owner, new BankAccount(owner, balance));
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
        return tag;
    }

    @Override
    public Optional<BankAccount> find(UUID owner) {
        return Optional.ofNullable(accounts.get(owner));
    }

    @Override
    public void setBalance(UUID owner, long balanceCents) {
        accounts.put(owner, new BankAccount(owner, balanceCents));
        setDirty();
    }
}
