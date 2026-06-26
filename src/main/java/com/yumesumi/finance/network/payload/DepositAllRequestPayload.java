package com.yumesumi.finance.network.payload;

import com.yumesumi.finance.YumesumiFinance;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DepositAllRequestPayload() implements CustomPacketPayload {
    public static final Type<DepositAllRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(YumesumiFinance.MODID, "deposit_all"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DepositAllRequestPayload> STREAM_CODEC = StreamCodec.unit(new DepositAllRequestPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
