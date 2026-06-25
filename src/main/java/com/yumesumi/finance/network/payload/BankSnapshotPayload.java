package com.yumesumi.finance.network.payload;

import com.yumesumi.finance.YumesumiFinance;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BankSnapshotPayload(long balanceCents, String message) implements CustomPacketPayload {
    public static final Type<BankSnapshotPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(YumesumiFinance.MODID, "bank_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BankSnapshotPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG,
            BankSnapshotPayload::balanceCents,
            ByteBufCodecs.STRING_UTF8,
            BankSnapshotPayload::message,
            BankSnapshotPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
