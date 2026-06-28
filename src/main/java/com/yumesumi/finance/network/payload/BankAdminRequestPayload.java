package com.yumesumi.finance.network.payload;

import com.yumesumi.finance.YumesumiFinance;
import com.yumesumi.finance.bank.admin.BankAdminAction;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BankAdminRequestPayload(
        long requestId,
        BankAdminAction action,
        UUID targetOwner,
        long amountCents,
        String text
) implements CustomPacketPayload {
    public static final Type<BankAdminRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(YumesumiFinance.MODID, "bank_admin_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BankAdminRequestPayload> STREAM_CODEC =
            StreamCodec.of(BankAdminRequestPayload::write, BankAdminRequestPayload::read);

    public BankAdminRequestPayload {
        text = text == null ? "" : text.trim();
        if (text.length() > 160) text = text.substring(0, 160);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void write(RegistryFriendlyByteBuf buf, BankAdminRequestPayload payload) {
        buf.writeVarLong(payload.requestId());
        buf.writeEnum(payload.action());
        buf.writeBoolean(payload.targetOwner() != null);
        if (payload.targetOwner() != null) {
            buf.writeUUID(payload.targetOwner());
        }
        buf.writeVarLong(payload.amountCents());
        buf.writeUtf(payload.text() == null ? "" : payload.text(), 160);
    }

    private static BankAdminRequestPayload read(RegistryFriendlyByteBuf buf) {
        long requestId = buf.readVarLong();
        BankAdminAction action = buf.readEnum(BankAdminAction.class);
        UUID target = buf.readBoolean() ? buf.readUUID() : null;
        long amount = buf.readVarLong();
        String text = buf.readUtf(160);
        return new BankAdminRequestPayload(requestId, action, target, amount, text);
    }
}
