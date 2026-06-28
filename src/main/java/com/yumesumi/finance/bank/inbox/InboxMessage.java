package com.yumesumi.finance.bank.inbox;

import java.util.UUID;

public class InboxMessage {
    private final UUID id;
    private final InboxMessageType type;
    private final long createdAt;
    private String title;
    private String body;
    private UUID relatedEnterpriseId;
    private boolean read;
    private boolean starred;

    public InboxMessage(UUID id, InboxMessageType type, String title, String body, UUID relatedEnterpriseId, long createdAt) {
        this.id = id == null ? UUID.randomUUID() : id;
        this.type = type == null ? InboxMessageType.INFO : type;
        this.title = title == null ? "" : title;
        this.body = body == null ? "" : body;
        this.relatedEnterpriseId = relatedEnterpriseId;
        this.createdAt = createdAt;
    }

    public UUID id() { return id; }
    public InboxMessageType type() { return type; }
    public long createdAt() { return createdAt; }
    public String title() { return title; }
    public String body() { return body; }
    public UUID relatedEnterpriseId() { return relatedEnterpriseId; }
    public boolean read() { return read; }
    public boolean starred() { return starred; }

    public void setTitle(String title) { this.title = title == null ? "" : title; }
    public void setBody(String body) { this.body = body == null ? "" : body; }
    public void setRelatedEnterpriseId(UUID relatedEnterpriseId) { this.relatedEnterpriseId = relatedEnterpriseId; }
    public void setRead(boolean read) { this.read = read; }
    public void setStarred(boolean starred) { this.starred = starred; }
}
