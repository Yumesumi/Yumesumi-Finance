package com.yumesumi.finance.network.payload;

import com.yumesumi.finance.YumesumiFinance;
import com.yumesumi.finance.bank.enterprise.EnterpriseAction;
import com.yumesumi.finance.bank.enterprise.EnterpriseRole;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record EnterpriseRequestPayload(
        long requestId,
        EnterpriseAction action,
        UUID enterpriseId,
        UUID messageId,
        String code,
        String name,
        String description,
        String target,
        long amountCents,
        String note,
        boolean targetEnterprise,
        boolean confirmed,
        EnterpriseRole role
) implements CustomPacketPayload {
    private static final int TARGET_MAX_LENGTH = 4096;

    public static final Type<EnterpriseRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(YumesumiFinance.MODID, "enterprise_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EnterpriseRequestPayload> STREAM_CODEC =
            StreamCodec.of(EnterpriseRequestPayload::write, EnterpriseRequestPayload::read);

    public EnterpriseRequestPayload {
        code = code == null ? "" : code.trim();
        name = name == null ? "" : name.trim();
        description = description == null ? "" : description.trim();
        target = target == null ? "" : target.trim();
        note = note == null ? "" : note.trim();
        if (name.length() > 32) name = name.substring(0, 32);
        if (description.length() > 64) description = description.substring(0, 64);
        if (target.length() > TARGET_MAX_LENGTH) target = target.substring(0, TARGET_MAX_LENGTH);
        if (note.length() > 64) note = note.substring(0, 64);
        role = role == null ? EnterpriseRole.MEMBER : role;
    }

    public static EnterpriseRequestPayload simple(long requestId, EnterpriseAction action) {
        return new EnterpriseRequestPayload(requestId, action, null, null, "", "", "", "", 0L, "", false, false, EnterpriseRole.MEMBER);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void write(RegistryFriendlyByteBuf buf, EnterpriseRequestPayload payload) {
        buf.writeVarLong(payload.requestId());
        buf.writeEnum(payload.action());
        buf.writeBoolean(payload.enterpriseId() != null);
        if (payload.enterpriseId() != null) buf.writeUUID(payload.enterpriseId());
        buf.writeBoolean(payload.messageId() != null);
        if (payload.messageId() != null) buf.writeUUID(payload.messageId());
        buf.writeUtf(payload.code(), 16);
        buf.writeUtf(payload.name(), 32);
        buf.writeUtf(payload.description(), 64);
        buf.writeUtf(payload.target(), TARGET_MAX_LENGTH);
        buf.writeVarLong(payload.amountCents());
        buf.writeUtf(payload.note(), 64);
        buf.writeBoolean(payload.targetEnterprise());
        buf.writeBoolean(payload.confirmed());
        buf.writeEnum(payload.role());
    }

    private static EnterpriseRequestPayload read(RegistryFriendlyByteBuf buf) {
        long requestId = buf.readVarLong();
        EnterpriseAction action = buf.readEnum(EnterpriseAction.class);
        UUID enterpriseId = buf.readBoolean() ? buf.readUUID() : null;
        UUID messageId = buf.readBoolean() ? buf.readUUID() : null;
        String code = buf.readUtf(16);
        String name = buf.readUtf(32);
        String description = buf.readUtf(64);
        String target = buf.readUtf(TARGET_MAX_LENGTH);
        long amount = buf.readVarLong();
        String note = buf.readUtf(64);
        boolean targetEnterprise = buf.readBoolean();
        boolean confirmed = buf.readBoolean();
        EnterpriseRole role = buf.readEnum(EnterpriseRole.class);
        return new EnterpriseRequestPayload(requestId, action, enterpriseId, messageId, code, name, description, target, amount, note, targetEnterprise, confirmed, role);
    }
}
