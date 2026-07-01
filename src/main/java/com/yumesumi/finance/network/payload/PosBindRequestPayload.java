package com.yumesumi.finance.network.payload;

import com.yumesumi.finance.YumesumiFinance;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

public record PosBindRequestPayload(InteractionHand hand, String enterpriseCode, boolean clear) implements CustomPacketPayload {
    public static final Type<PosBindRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(YumesumiFinance.MODID, "pos_bind"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PosBindRequestPayload> STREAM_CODEC =
            StreamCodec.of(PosBindRequestPayload::write, PosBindRequestPayload::read);

    public PosBindRequestPayload {
        hand = hand == null ? InteractionHand.MAIN_HAND : hand;
        enterpriseCode = enterpriseCode == null ? "" : enterpriseCode.trim();
        if (enterpriseCode.length() > 16) enterpriseCode = enterpriseCode.substring(0, 16);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buf, PosBindRequestPayload payload) {
        buf.writeEnum(payload.hand());
        buf.writeUtf(payload.enterpriseCode(), 16);
        buf.writeBoolean(payload.clear());
    }

    private static PosBindRequestPayload read(RegistryFriendlyByteBuf buf) {
        return new PosBindRequestPayload(buf.readEnum(InteractionHand.class), buf.readUtf(16), buf.readBoolean());
    }
}
