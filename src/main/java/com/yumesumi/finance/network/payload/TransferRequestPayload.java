package com.yumesumi.finance.network.payload;

import com.yumesumi.finance.YumesumiFinance;
import com.yumesumi.finance.bank.BankAccessChannel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TransferRequestPayload(long requestId, BankAccessChannel channel, String target, long amountCents, String note) implements CustomPacketPayload {
    public static final Type<TransferRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(YumesumiFinance.MODID, "transfer"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TransferRequestPayload> STREAM_CODEC = StreamCodec.of(
            TransferRequestPayload::write,
            TransferRequestPayload::read
    );

    public TransferRequestPayload {
        channel = channel == null ? BankAccessChannel.ATM : channel;
        note = note == null ? "" : note.trim();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buffer, TransferRequestPayload payload) {
        buffer.writeVarLong(payload.requestId());
        buffer.writeEnum(payload.channel());
        buffer.writeUtf(payload.target());
        buffer.writeVarLong(payload.amountCents());
        buffer.writeUtf(payload.note());
    }

    private static TransferRequestPayload read(RegistryFriendlyByteBuf buffer) {
        return new TransferRequestPayload(
                buffer.readVarLong(),
                buffer.readEnum(BankAccessChannel.class),
                buffer.readUtf(),
                buffer.readVarLong(),
                buffer.readUtf()
        );
    }
}
