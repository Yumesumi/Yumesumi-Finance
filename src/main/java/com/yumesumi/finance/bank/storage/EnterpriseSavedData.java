package com.yumesumi.finance.bank.storage;

import com.yumesumi.finance.bank.enterprise.EnterpriseAccount;
import com.yumesumi.finance.bank.enterprise.EnterpriseMember;
import com.yumesumi.finance.bank.enterprise.EnterpriseRole;
import com.yumesumi.finance.bank.enterprise.EnterpriseTransactionRecord;
import com.yumesumi.finance.bank.enterprise.EnterpriseTransactionType;
import com.yumesumi.finance.bank.inbox.InboxMessage;
import com.yumesumi.finance.bank.inbox.InboxMessageType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public class EnterpriseSavedData extends SavedData {
    private static final String DATA_NAME = "yumesumi_finance_enterprises";
    public static final long REGISTRATION_FEE_CENTS = 100_000L;
    public static final int MAX_OWNER_ENTERPRISES = 10;
    public static final int MAX_INBOX_MESSAGES = 500;

    private final Map<UUID, EnterpriseAccount> enterprises = new LinkedHashMap<>();
    private final Map<String, UUID> enterpriseByCode = new HashMap<>();
    private final Map<UUID, List<InboxMessage>> inboxByPlayer = new HashMap<>();
    private final Map<UUID, Boolean> preferEnterprise = new HashMap<>();

    public static EnterpriseSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(EnterpriseSavedData::new, EnterpriseSavedData::load),
                DATA_NAME
        );
    }

    public static EnterpriseSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        EnterpriseSavedData data = new EnterpriseSavedData();

        ListTag enterprisesTag = tag.getList("enterprises", Tag.TAG_COMPOUND);
        for (int i = 0; i < enterprisesTag.size(); i++) {
            readEnterprise(enterprisesTag.getCompound(i)).ifPresent(account -> {
                data.enterprises.put(account.id(), account);
                data.enterpriseByCode.put(account.code(), account.id());
            });
        }

        ListTag inboxTag = tag.getList("inbox", Tag.TAG_COMPOUND);
        for (int i = 0; i < inboxTag.size(); i++) {
            CompoundTag ownerTag = inboxTag.getCompound(i);
            if (!ownerTag.hasUUID("owner")) continue;
            UUID owner = ownerTag.getUUID("owner");
            List<InboxMessage> messages = new ArrayList<>();
            ListTag messagesTag = ownerTag.getList("messages", Tag.TAG_COMPOUND);
            for (int j = 0; j < messagesTag.size(); j++) {
                readInboxMessage(messagesTag.getCompound(j)).ifPresent(messages::add);
            }
            messages.sort(Comparator.comparingLong(InboxMessage::createdAt).reversed());
            pruneInbox(messages);
            if (!messages.isEmpty()) data.inboxByPlayer.put(owner, messages);
        }

        ListTag preferencesTag = tag.getList("preferences", Tag.TAG_COMPOUND);
        for (int i = 0; i < preferencesTag.size(); i++) {
            CompoundTag prefTag = preferencesTag.getCompound(i);
            if (prefTag.hasUUID("owner")) {
                data.preferEnterprise.put(prefTag.getUUID("owner"), prefTag.getBoolean("enterprise"));
            }
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag enterprisesTag = new ListTag();
        for (EnterpriseAccount account : enterprises.values()) {
            enterprisesTag.add(writeEnterprise(account));
        }
        tag.put("enterprises", enterprisesTag);

        ListTag inboxTag = new ListTag();
        for (Map.Entry<UUID, List<InboxMessage>> entry : inboxByPlayer.entrySet()) {
            CompoundTag ownerTag = new CompoundTag();
            ownerTag.putUUID("owner", entry.getKey());
            ListTag messagesTag = new ListTag();
            for (InboxMessage message : entry.getValue()) {
                messagesTag.add(writeInboxMessage(message));
            }
            ownerTag.put("messages", messagesTag);
            inboxTag.add(ownerTag);
        }
        tag.put("inbox", inboxTag);

        ListTag preferencesTag = new ListTag();
        for (Map.Entry<UUID, Boolean> entry : preferEnterprise.entrySet()) {
            CompoundTag prefTag = new CompoundTag();
            prefTag.putUUID("owner", entry.getKey());
            prefTag.putBoolean("enterprise", entry.getValue());
            preferencesTag.add(prefTag);
        }
        tag.put("preferences", preferencesTag);
        return tag;
    }

    public Optional<EnterpriseAccount> findEnterprise(UUID id) {
        return Optional.ofNullable(enterprises.get(id));
    }

    public Optional<EnterpriseAccount> findByCode(String rawCode) {
        String code;
        try {
            code = EnterpriseAccount.normalizeCode(rawCode);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
        UUID id = enterpriseByCode.get(code);
        return id == null ? Optional.empty() : findEnterprise(id);
    }

    public boolean isCodeAvailable(String rawCode, UUID exceptEnterprise) {
        String code = EnterpriseAccount.normalizeCode(rawCode);
        UUID existing = enterpriseByCode.get(code);
        return existing == null || existing.equals(exceptEnterprise);
    }

    public List<EnterpriseAccount> enterprisesFor(UUID playerId) {
        if (playerId == null) return List.of();
        return enterprises.values().stream()
                .filter(account -> account.containsMember(playerId))
                .sorted(Comparator.comparingLong(EnterpriseAccount::lastActiveAt).reversed())
                .toList();
    }

    public List<EnterpriseAccount> allEnterprises() {
        return enterprises.values().stream()
                .sorted(Comparator.comparingLong(EnterpriseAccount::lastActiveAt).reversed())
                .toList();
    }

    public int ownedEnterpriseCount(UUID playerId) {
        if (playerId == null) return 0;
        int count = 0;
        for (EnterpriseAccount account : enterprises.values()) {
            if (playerId.equals(account.ownerId())) count++;
        }
        return count;
    }

    public EnterpriseAccount createEnterprise(String code, String name, String description, UUID ownerId, String ownerName) {
        if (ownedEnterpriseCount(ownerId) >= MAX_OWNER_ENTERPRISES) {
            throw new IllegalStateException("Owner enterprise limit reached");
        }
        String normalizedCode = EnterpriseAccount.normalizeCode(code);
        if (!isCodeAvailable(normalizedCode, null)) {
            throw new IllegalArgumentException("Enterprise code already exists");
        }
        long now = System.currentTimeMillis();
        EnterpriseAccount account = new EnterpriseAccount(UUID.randomUUID(), normalizedCode, name, description, 0L, ownerId, now);
        account.addMember(new EnterpriseMember(ownerId, ownerName, EnterpriseRole.OWNER, now));
        enterprises.put(account.id(), account);
        enterpriseByCode.put(account.code(), account.id());
        setDirty();
        return account;
    }

    public void updateEnterpriseSettings(EnterpriseAccount account, String name, String code, String description) {
        String oldCode = account.code();
        String newCode = EnterpriseAccount.normalizeCode(code);
        if (!oldCode.equals(newCode) && !isCodeAvailable(newCode, account.id())) {
            throw new IllegalArgumentException("Enterprise code already exists");
        }
        account.setName(name);
        account.setDescription(description);
        if (!oldCode.equals(newCode)) {
            enterpriseByCode.remove(oldCode);
            account.setCode(newCode);
            enterpriseByCode.put(newCode, account.id());
        }
        setDirty();
    }

    public void deleteEnterprise(EnterpriseAccount account) {
        enterprises.remove(account.id());
        enterpriseByCode.remove(account.code());
        for (List<InboxMessage> messages : inboxByPlayer.values()) {
            messages.removeIf(message -> account.id().equals(message.relatedEnterpriseId()));
        }
        setDirty();
    }

    public List<InboxMessage> inbox(UUID playerId) {
        if (playerId == null) return List.of();
        return inboxByPlayer.getOrDefault(playerId, List.of()).stream()
                .sorted(Comparator.comparing(InboxMessage::starred).reversed()
                        .thenComparing(Comparator.comparingLong(InboxMessage::createdAt).reversed()))
                .toList();
    }

    public Optional<InboxMessage> findMessage(UUID playerId, UUID messageId) {
        if (playerId == null || messageId == null) return Optional.empty();
        return inboxByPlayer.getOrDefault(playerId, List.of()).stream()
                .filter(message -> message.id().equals(messageId))
                .findFirst();
    }

    public void addInboxMessage(UUID playerId, InboxMessage message) {
        inboxByPlayer.computeIfAbsent(playerId, ignored -> new ArrayList<>()).add(message);
        inboxByPlayer.get(playerId).sort(Comparator.comparingLong(InboxMessage::createdAt).reversed());
        pruneInbox(inboxByPlayer.get(playerId));
        setDirty();
    }

    public void deleteMessage(UUID playerId, UUID messageId) {
        List<InboxMessage> messages = inboxByPlayer.get(playerId);
        if (messages == null) return;
        messages.removeIf(message -> message.id().equals(messageId));
        if (messages.isEmpty()) inboxByPlayer.remove(playerId);
        setDirty();
    }

    public boolean prefersEnterprise(UUID playerId) {
        return preferEnterprise.getOrDefault(playerId, false);
    }

    public void setPrefersEnterprise(UUID playerId, boolean enterprise) {
        preferEnterprise.put(playerId, enterprise);
        setDirty();
    }

    public void setDirtyAndTouch(EnterpriseAccount account) {
        account.touch();
        setDirty();
    }

    private static CompoundTag writeEnterprise(EnterpriseAccount account) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", account.id());
        tag.putString("code", account.code());
        tag.putString("name", account.name());
        tag.putString("description", account.description());
        tag.putLong("balance_cents", account.balanceCents());
        tag.putUUID("owner", account.ownerId());
        tag.putLong("created_at", account.createdAt());
        tag.putLong("last_active_at", account.lastActiveAt());

        ListTag membersTag = new ListTag();
        for (EnterpriseMember member : account.members()) {
            CompoundTag memberTag = new CompoundTag();
            memberTag.putUUID("player", member.playerId());
            memberTag.putString("name", member.playerName());
            memberTag.putString("role", member.role().name());
            memberTag.putLong("joined_at", member.joinedAt());
            memberTag.putLong("last_active_at", member.lastActiveAt());
            membersTag.add(memberTag);
        }
        tag.put("members", membersTag);

        ListTag transactionsTag = new ListTag();
        for (EnterpriseTransactionRecord record : account.transactions()) {
            CompoundTag recordTag = new CompoundTag();
            recordTag.putUUID("id", record.id());
            recordTag.putUUID("enterprise", record.enterpriseId());
            recordTag.putString("type", record.type().name());
            recordTag.putLong("amount_cents", record.amountCents());
            recordTag.putLong("balance_after_cents", record.balanceAfterCents());
            if (record.operatorId() != null) recordTag.putUUID("operator", record.operatorId());
            recordTag.putString("operator_name", record.operatorName());
            if (record.counterpartyId() != null) recordTag.putUUID("counterparty", record.counterpartyId());
            recordTag.putString("counterparty_name", record.counterpartyName());
            recordTag.putString("note", record.note());
            recordTag.putLong("created_at", record.createdAt());
            transactionsTag.add(recordTag);
        }
        tag.put("transactions", transactionsTag);
        return tag;
    }

    private static void pruneInbox(List<InboxMessage> messages) {
        while (messages.size() > MAX_INBOX_MESSAGES) {
            messages.remove(messages.size() - 1);
        }
    }

    private static Optional<EnterpriseAccount> readEnterprise(CompoundTag tag) {
        if (!tag.hasUUID("id") || !tag.hasUUID("owner")) return Optional.empty();
        try {
            EnterpriseAccount account = new EnterpriseAccount(
                    tag.getUUID("id"),
                    tag.getString("code"),
                    tag.getString("name"),
                    tag.getString("description"),
                    Math.max(0L, tag.getLong("balance_cents")),
                    tag.getUUID("owner"),
                    tag.getLong("created_at")
            );
            account.setLastActiveAt(tag.getLong("last_active_at"));

            ListTag membersTag = tag.getList("members", Tag.TAG_COMPOUND);
            for (int i = 0; i < membersTag.size(); i++) {
                CompoundTag memberTag = membersTag.getCompound(i);
                if (!memberTag.hasUUID("player")) continue;
                EnterpriseRole role = EnterpriseRole.MEMBER;
                try {
                    role = EnterpriseRole.valueOf(memberTag.getString("role"));
                } catch (IllegalArgumentException ignored) {
                }
                EnterpriseMember member = new EnterpriseMember(
                        memberTag.getUUID("player"),
                        memberTag.getString("name"),
                        role,
                        memberTag.getLong("joined_at")
                );
                member.setLastActiveAt(memberTag.getLong("last_active_at"));
                account.addMember(member);
            }

            ListTag transactionsTag = tag.getList("transactions", Tag.TAG_COMPOUND);
            for (int i = 0; i < transactionsTag.size(); i++) {
                readEnterpriseTransaction(transactionsTag.getCompound(i)).ifPresent(account::appendTransaction);
            }
            account.setLastActiveAt(tag.getLong("last_active_at"));
            return Optional.of(account);
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<EnterpriseTransactionRecord> readEnterpriseTransaction(CompoundTag tag) {
        if (!tag.hasUUID("id") || !tag.hasUUID("enterprise")) return Optional.empty();
        try {
            UUID operator = tag.hasUUID("operator") ? tag.getUUID("operator") : null;
            UUID counterparty = tag.hasUUID("counterparty") ? tag.getUUID("counterparty") : null;
            return Optional.of(new EnterpriseTransactionRecord(
                    tag.getUUID("id"),
                    tag.getUUID("enterprise"),
                    EnterpriseTransactionType.valueOf(tag.getString("type")),
                    tag.getLong("amount_cents"),
                    Math.max(0L, tag.getLong("balance_after_cents")),
                    operator,
                    tag.getString("operator_name"),
                    counterparty,
                    tag.getString("counterparty_name"),
                    tag.getString("note"),
                    tag.getLong("created_at")
            ));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static CompoundTag writeInboxMessage(InboxMessage message) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", message.id());
        tag.putString("type", message.type().name());
        tag.putLong("created_at", message.createdAt());
        tag.putString("title", message.title());
        tag.putString("body", message.body());
        if (message.relatedEnterpriseId() != null) tag.putUUID("enterprise", message.relatedEnterpriseId());
        tag.putBoolean("read", message.read());
        tag.putBoolean("starred", message.starred());
        return tag;
    }

    private static Optional<InboxMessage> readInboxMessage(CompoundTag tag) {
        if (!tag.hasUUID("id")) return Optional.empty();
        try {
            UUID enterpriseId = tag.hasUUID("enterprise") ? tag.getUUID("enterprise") : null;
            InboxMessage message = new InboxMessage(
                    tag.getUUID("id"),
                    InboxMessageType.valueOf(tag.getString("type")),
                    tag.getString("title"),
                    tag.getString("body"),
                    enterpriseId,
                    tag.getLong("created_at")
            );
            message.setRead(tag.getBoolean("read"));
            message.setStarred(tag.getBoolean("starred"));
            return Optional.of(message);
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
