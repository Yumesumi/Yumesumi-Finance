package com.yumesumi.finance.bank.enterprise;

import java.util.UUID;

public class EnterpriseMember {
    private final UUID playerId;
    private String playerName;
    private EnterpriseRole role;
    private final long joinedAt;
    private long lastActiveAt;

    public EnterpriseMember(UUID playerId, String playerName, EnterpriseRole role, long joinedAt) {
        if (playerId == null) throw new IllegalArgumentException("Member player id cannot be null");
        this.playerId = playerId;
        this.playerName = playerName == null ? "" : playerName;
        this.role = role == null ? EnterpriseRole.MEMBER : role;
        this.joinedAt = joinedAt;
        this.lastActiveAt = joinedAt;
    }

    public UUID playerId() { return playerId; }
    public String playerName() { return playerName; }
    public EnterpriseRole role() { return role; }
    public long joinedAt() { return joinedAt; }
    public long lastActiveAt() { return lastActiveAt; }

    public void setPlayerName(String playerName) { this.playerName = playerName == null ? "" : playerName; }
    public void setRole(EnterpriseRole role) { this.role = role == null ? EnterpriseRole.MEMBER : role; }
    public void setLastActiveAt(long lastActiveAt) { this.lastActiveAt = lastActiveAt > 0 ? lastActiveAt : joinedAt; }
    public void touch() { this.lastActiveAt = System.currentTimeMillis(); }
}
