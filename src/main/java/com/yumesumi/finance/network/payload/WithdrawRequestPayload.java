package com.yumesumi.finance.network.payload;

import com.yumesumi.finance.YumesumiFinance;
import com.yumesumi.finance.bank.BankAccessChannel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record WithdrawRequestPayload(long requestId, BankAccessChannel channel, long amountCents) implements CustomPacketPayload {
    public static final Type<WithdrawRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(YumesumiFinance.MODID, "withdraw"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WithdrawRequestPayload> STREAM_CODEC = StreamCodec.of(
            WithdrawRequestPayload::write,
            WithdrawRequestPayload::read
    );

    public WithdrawRequestPayload {
        channel = channel == null ? BankAccessChannel.ATM : channel;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buffer, WithdrawRequestPayload payload) {
        buffer.writeVarLong(payload.requestId());
        buffer.writeEnum(payload.channel());
        buffer.writeVarLong(payload.amountCents());
    }

    private static WithdrawRequestPayload read(RegistryFriendlyByteBuf buffer) {
        return new WithdrawRequestPayload(buffer.readVarLong(), buffer.readEnum(BankAccessChannel.class), buffer.readVarLong());
    }
}
