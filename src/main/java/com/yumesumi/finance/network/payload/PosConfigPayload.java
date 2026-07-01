package com.yumesumi.finance.network.payload;

import com.yumesumi.finance.YumesumiFinance;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

public record PosConfigPayload(InteractionHand hand, String enterpriseCode) implements CustomPacketPayload {
    public static final Type<PosConfigPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(YumesumiFinance.MODID, "pos_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PosConfigPayload> STREAM_CODEC =
            StreamCodec.of(PosConfigPayload::write, PosConfigPayload::read);

    public PosConfigPayload {
        hand = hand == null ? InteractionHand.MAIN_HAND : hand;
        enterpriseCode = enterpriseCode == null ? "" : enterpriseCode.trim();
        if (enterpriseCode.length() > 16) enterpriseCode = enterpriseCode.substring(0, 16);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buf, PosConfigPayload payload) {
        buf.writeEnum(payload.hand());
        buf.writeUtf(payload.enterpriseCode(), 16);
    }

    private static PosConfigPayload read(RegistryFriendlyByteBuf buf) {
        return new PosConfigPayload(buf.readEnum(InteractionHand.class), buf.readUtf(16));
    }
}
