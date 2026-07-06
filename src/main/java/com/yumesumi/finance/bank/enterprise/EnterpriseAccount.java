package com.yumesumi.finance.bank.enterprise;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class EnterpriseAccount {
    public static final int MAX_DESCRIPTION_LENGTH = 64;
    public static final int MAX_MEMBERS = 64;
    public static final int MAX_LEDGER_RECORDS = 10_000;

    private final UUID id;
    private String code;
    private String name;
    private String description;
    private long balanceCents;
    private final UUID ownerId;
    private final long createdAt;
    private long lastActiveAt;
    private final Map<UUID, EnterpriseMember> members = new LinkedHashMap<>();
    private final List<EnterpriseTransactionRecord> transactions = new ArrayList<>();

    public EnterpriseAccount(UUID id, String code, String name, String description, long balanceCents, UUID ownerId, long createdAt) {
        if (id == null) throw new IllegalArgumentException("Enterprise id cannot be null");
        if (ownerId == null) throw new IllegalArgumentException("Enterprise owner cannot be null");
        if (balanceCents < 0) throw new IllegalArgumentException("Enterprise balance cannot be negative");
        this.id = id;
        this.code = normalizeCode(code);
        this.name = sanitizeName(name);
        this.description = sanitizeDescription(description);
        this.balanceCents = balanceCents;
        this.ownerId = ownerId;
        this.createdAt = createdAt;
        this.lastActiveAt = createdAt;
    }

    public UUID id() { return id; }
    public String code() { return code; }
    public String name() { return name; }
    public String description() { return description; }
    public long balanceCents() { return balanceCents; }
    public UUID ownerId() { return ownerId; }
    public long createdAt() { return createdAt; }
    public long lastActiveAt() { return lastActiveAt; }
    public int memberCount() { return members.size(); }
    public List<EnterpriseMember> members() { return List.copyOf(members.values()); }
    public List<EnterpriseTransactionRecord> transactions() { return List.copyOf(transactions); }
    public List<EnterpriseTransactionRecord> recentTransactions(int limit) {
        if (limit <= 0 || transactions.isEmpty()) return List.of();
        return List.copyOf(transactions.subList(0, Math.min(limit, transactions.size())));
    }

    public void setCode(String code) { this.code = normalizeCode(code); }
    public void setName(String name) { this.name = sanitizeName(name); }
    public void setDescription(String description) { this.description = sanitizeDescription(description); }
    public void setBalanceCents(long balanceCents) {
        if (balanceCents < 0) throw new IllegalArgumentException("Enterprise balance cannot be negative");
        this.balanceCents = balanceCents;
        touch();
    }
    public void setLastActiveAt(long lastActiveAt) { this.lastActiveAt = lastActiveAt > 0 ? lastActiveAt : createdAt; }
    public void touch() { this.lastActiveAt = System.currentTimeMillis(); }

    public Optional<EnterpriseMember> member(UUID playerId) {
        return Optional.ofNullable(members.get(playerId));
    }

    public EnterpriseRole roleOf(UUID playerId) {
        return member(playerId).map(EnterpriseMember::role).orElse(null);
    }

    public boolean containsMember(UUID playerId) {
        return members.containsKey(playerId);
    }

    public void addMember(EnterpriseMember member) {
        if (member == null) throw new IllegalArgumentException("Enterprise member cannot be null");
        if (members.size() >= MAX_MEMBERS && !members.containsKey(member.playerId())) {
            throw new IllegalStateException("Enterprise member limit reached");
        }
        members.put(member.playerId(), member);
        touch();
    }

    public void removeMember(UUID playerId) {
        if (ownerId.equals(playerId)) throw new IllegalArgumentException("Cannot remove enterprise owner");
        members.remove(playerId);
        touch();
    }

    public void setRole(UUID playerId, EnterpriseRole role) {
        if (ownerId.equals(playerId)) throw new IllegalArgumentException("Cannot change enterprise owner role");
        EnterpriseMember member = members.get(playerId);
        if (member == null) throw new IllegalArgumentException("Member not found");
        member.setRole(role);
        touch();
    }

    public void appendTransaction(EnterpriseTransactionRecord record) {
        int index = 0;
        while (index < transactions.size() && transactions.get(index).createdAt() >= record.createdAt()) {
            index++;
        }
        transactions.add(index, record);
        while (transactions.size() > MAX_LEDGER_RECORDS) {
            transactions.remove(transactions.size() - 1);
        }
        touch();
    }

    public void removeTransaction(UUID recordId) {
        if (recordId == null) return;
        if (transactions.removeIf(record -> record.id().equals(recordId))) {
            touch();
        }
    }

    private static String sanitizeName(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) throw new IllegalArgumentException("Enterprise name cannot be empty");
        return text.length() <= 32 ? text : text.substring(0, 32);
    }

    private static String sanitizeDescription(String value) {
        String text = value == null ? "" : value.trim();
        return text.length() <= MAX_DESCRIPTION_LENGTH ? text : text.substring(0, MAX_DESCRIPTION_LENGTH);
    }

    public static String normalizeCode(String code) {
        String text = code == null ? "" : code.trim().toUpperCase();
        if (!text.matches("[A-Z0-9]{3,4}")) {
            throw new IllegalArgumentException("Enterprise code must be 3-4 uppercase letters or digits");
        }
        return text;
    }
}
