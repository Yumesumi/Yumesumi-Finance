package com.yumesumi.finance.network.payload;

import com.yumesumi.finance.YumesumiFinance;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DepositRequestPayload(long amountCents) implements CustomPacketPayload {
    public static final Type<DepositRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(YumesumiFinance.MODID, "deposit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DepositRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG,
            DepositRequestPayload::amountCents,
            DepositRequestPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
