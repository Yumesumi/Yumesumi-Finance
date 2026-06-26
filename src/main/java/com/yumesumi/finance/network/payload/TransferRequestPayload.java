package com.yumesumi.finance.network.payload;

import com.yumesumi.finance.YumesumiFinance;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TransferRequestPayload(String target, long amountCents, String note) implements CustomPacketPayload {
    public static final Type<TransferRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(YumesumiFinance.MODID, "transfer"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TransferRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            TransferRequestPayload::target,
            ByteBufCodecs.VAR_LONG,
            TransferRequestPayload::amountCents,
            ByteBufCodecs.STRING_UTF8,
            TransferRequestPayload::note,
            TransferRequestPayload::new
    );

    public TransferRequestPayload {
        note = note == null ? "" : note.trim();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
