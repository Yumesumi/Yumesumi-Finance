package com.yumesumi.finance.network.payload;

import com.yumesumi.finance.bank.enterprise.EnterpriseAccount;
import com.yumesumi.finance.bank.enterprise.EnterpriseRole;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;

public record EnterpriseSummaryView(
        UUID id,
        String code,
        String name,
        String description,
        long balanceCents,
        EnterpriseRole myRole,
        int memberCount,
        long lastActiveAt
) {
    public static EnterpriseSummaryView from(EnterpriseAccount account, EnterpriseRole role) {
        return new EnterpriseSummaryView(
                account.id(),
                account.code(),
                account.name(),
                account.description(),
                account.balanceCents(),
                role,
                account.memberCount(),
                account.lastActiveAt()
        );
    }

    public static void write(RegistryFriendlyByteBuf buf, EnterpriseSummaryView view) {
        buf.writeUUID(view.id());
        buf.writeUtf(view.code(), 8);
        buf.writeUtf(view.name(), 64);
        buf.writeUtf(view.description(), 128);
        buf.writeVarLong(view.balanceCents());
        buf.writeEnum(view.myRole());
        buf.writeVarInt(view.memberCount());
        buf.writeVarLong(view.lastActiveAt());
    }

    public static EnterpriseSummaryView read(RegistryFriendlyByteBuf buf) {
        return new EnterpriseSummaryView(
                buf.readUUID(),
                buf.readUtf(8),
                buf.readUtf(64),
                buf.readUtf(128),
                buf.readVarLong(),
                buf.readEnum(EnterpriseRole.class),
                buf.readVarInt(),
                buf.readVarLong()
        );
    }
}
