package com.yumesumi.finance.network.payload;

import com.yumesumi.finance.YumesumiFinance;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record WithdrawRequestPayload(long amountCents) implements CustomPacketPayload {
    public static final Type<WithdrawRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(YumesumiFinance.MODID, "withdraw"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WithdrawRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG,
            WithdrawRequestPayload::amountCents,
            WithdrawRequestPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
