package com.yumesumi.finance.bank.account;

import java.math.BigInteger;
import java.util.UUID;

public class BankAccount {
    public static final int MAX_ADMIN_NOTE_LENGTH = 64;

    private final UUID owner;
    private final BankAccountType accountType;
    private String ownerName;
    private long balanceCents;
    private long createdAt;
    private long lastActiveAt;
    private String adminNote;
    private boolean frozen;

    public BankAccount(UUID owner, BankAccountType accountType, String ownerName, long balanceCents, long createdAt) {
        if (owner == null) throw new IllegalArgumentException("Account owner cannot be null");
        if (accountType == null) throw new IllegalArgumentException("Account type cannot be null");
        if (accountType != BankAccountType.OFFICIAL && balanceCents < 0) throw new IllegalArgumentException("Non-official account balance cannot be negative");
        this.owner = owner;
        this.accountType = accountType;
        this.ownerName = ownerName == null ? "" : ownerName;
        this.balanceCents = balanceCents;
        this.createdAt = createdAt;
        this.lastActiveAt = createdAt;
        this.adminNote = "";
        this.frozen = false;
    }

    public UUID owner() { return owner; }
    public BankAccountType accountType() { return accountType; }
    public String ownerName() { return ownerName; }
    public long balanceCents() { return balanceCents; }
    public long createdAt() { return createdAt; }
    public long lastActiveAt() { return lastActiveAt; }
    public String adminNote() { return adminNote; }
    public boolean frozen() { return frozen; }

    public void setOwnerName(String name) { this.ownerName = name == null ? "" : name; }
    public void setBalanceCents(long balanceCents) {
        if (accountType != BankAccountType.OFFICIAL && balanceCents < 0) throw new IllegalArgumentException("Non-official account balance cannot be negative");
        this.balanceCents = balanceCents;
    }
    public void touchLastActive() { this.lastActiveAt = System.currentTimeMillis(); }
    public void setLastActiveAt(long lastActiveAt) { this.lastActiveAt = lastActiveAt > 0 ? lastActiveAt : createdAt; }
    public void setAdminNote(String note) { this.adminNote = note == null ? "" : note.substring(0, Math.min(note.length(), MAX_ADMIN_NOTE_LENGTH)); }
    public void setFrozen(boolean frozen) { this.frozen = frozen; }
}
