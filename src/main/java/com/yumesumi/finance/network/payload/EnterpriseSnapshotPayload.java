package com.yumesumi.finance.network.payload;

import com.yumesumi.finance.YumesumiFinance;
import com.yumesumi.finance.bank.enterprise.EnterpriseScreenMode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record EnterpriseSnapshotPayload(
        EnterpriseScreenMode mode,
        List<EnterpriseSummaryView> enterprises,
        EnterpriseDetailView detail,
        String checkedCode,
        boolean codeAvailable,
        Component message,
        String messageKey,
        boolean success
) implements CustomPacketPayload {
    public static final Type<EnterpriseSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(YumesumiFinance.MODID, "enterprise_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EnterpriseSnapshotPayload> STREAM_CODEC =
            StreamCodec.of(EnterpriseSnapshotPayload::write, EnterpriseSnapshotPayload::read);

    public EnterpriseSnapshotPayload {
        mode = mode == null ? EnterpriseScreenMode.LIST : mode;
        enterprises = List.copyOf(enterprises);
        checkedCode = checkedCode == null ? "" : checkedCode;
        message = message == null ? Component.empty() : message;
        messageKey = messageKey == null ? "" : messageKey;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void write(RegistryFriendlyByteBuf buf, EnterpriseSnapshotPayload payload) {
        buf.writeEnum(payload.mode());
        buf.writeVarInt(payload.enterprises().size());
        for (EnterpriseSummaryView enterprise : payload.enterprises()) EnterpriseSummaryView.write(buf, enterprise);
        buf.writeBoolean(payload.detail() != null);
        if (payload.detail() != null) EnterpriseDetailView.write(buf, payload.detail());
        buf.writeUtf(payload.checkedCode(), 16);
        buf.writeBoolean(payload.codeAvailable());
        ComponentSerialization.STREAM_CODEC.encode(buf, payload.message());
        buf.writeUtf(payload.messageKey(), 128);
        buf.writeBoolean(payload.success());
    }

    private static EnterpriseSnapshotPayload read(RegistryFriendlyByteBuf buf) {
        EnterpriseScreenMode mode = buf.readEnum(EnterpriseScreenMode.class);
        int size = Math.max(0, buf.readVarInt());
        List<EnterpriseSummaryView> enterprises = new ArrayList<>(Math.min(size, 256));
        for (int i = 0; i < size; i++) enterprises.add(EnterpriseSummaryView.read(buf));
        EnterpriseDetailView detail = buf.readBoolean() ? EnterpriseDetailView.read(buf) : null;
        String checkedCode = buf.readUtf(16);
        boolean codeAvailable = buf.readBoolean();
        Component message = ComponentSerialization.STREAM_CODEC.decode(buf);
        String messageKey = buf.readUtf(128);
        boolean success = buf.readBoolean();
        return new EnterpriseSnapshotPayload(mode, enterprises, detail, checkedCode, codeAvailable, message, messageKey, success);
    }
}
