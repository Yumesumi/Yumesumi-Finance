package com.yumesumi.finance.network.payload;

import com.yumesumi.finance.bank.inbox.InboxMessage;
import com.yumesumi.finance.bank.inbox.InboxMessageType;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;

public record InboxMessageView(
        UUID id,
        InboxMessageType type,
        String title,
        String body,
        UUID relatedEnterpriseId,
        long createdAt,
        boolean read,
        boolean starred
) {
    public static InboxMessageView from(InboxMessage message) {
        return new InboxMessageView(
                message.id(),
                message.type(),
                message.title(),
                message.body(),
                message.relatedEnterpriseId(),
                message.createdAt(),
                message.read(),
                message.starred()
        );
    }

    public static void write(RegistryFriendlyByteBuf buf, InboxMessageView view) {
        buf.writeUUID(view.id());
        buf.writeEnum(view.type());
        buf.writeUtf(view.title(), 96);
        buf.writeUtf(view.body(), 512);
        buf.writeBoolean(view.relatedEnterpriseId() != null);
        if (view.relatedEnterpriseId() != null) buf.writeUUID(view.relatedEnterpriseId());
        buf.writeVarLong(view.createdAt());
        buf.writeBoolean(view.read());
        buf.writeBoolean(view.starred());
    }

    public static InboxMessageView read(RegistryFriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        InboxMessageType type = buf.readEnum(InboxMessageType.class);
        String title = buf.readUtf(96);
        String body = buf.readUtf(512);
        UUID enterpriseId = buf.readBoolean() ? buf.readUUID() : null;
        long createdAt = buf.readVarLong();
        boolean read = buf.readBoolean();
        boolean starred = buf.readBoolean();
        return new InboxMessageView(id, type, title, body, enterpriseId, createdAt, read, starred);
    }
}
