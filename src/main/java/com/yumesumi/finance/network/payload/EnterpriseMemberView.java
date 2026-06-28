package com.yumesumi.finance.network.payload;

import com.yumesumi.finance.bank.enterprise.EnterpriseMember;
import com.yumesumi.finance.bank.enterprise.EnterpriseRole;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;

public record EnterpriseMemberView(
        UUID playerId,
        String playerName,
        EnterpriseRole role,
        long joinedAt,
        long lastActiveAt
) {
    public static EnterpriseMemberView from(EnterpriseMember member) {
        return new EnterpriseMemberView(
                member.playerId(),
                member.playerName(),
                member.role(),
                member.joinedAt(),
                member.lastActiveAt()
        );
    }

    public static void write(RegistryFriendlyByteBuf buf, EnterpriseMemberView view) {
        buf.writeUUID(view.playerId());
        buf.writeUtf(view.playerName(), 64);
        buf.writeEnum(view.role());
        buf.writeVarLong(view.joinedAt());
        buf.writeVarLong(view.lastActiveAt());
    }

    public static EnterpriseMemberView read(RegistryFriendlyByteBuf buf) {
        return new EnterpriseMemberView(
                buf.readUUID(),
                buf.readUtf(64),
                buf.readEnum(EnterpriseRole.class),
                buf.readVarLong(),
                buf.readVarLong()
        );
    }
}
