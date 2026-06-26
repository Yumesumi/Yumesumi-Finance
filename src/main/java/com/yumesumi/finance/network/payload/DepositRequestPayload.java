package com.yumesumi.finance.network.payload;

import com.yumesumi.finance.YumesumiFinance;
import com.yumesumi.finance.bank.BankAccessChannel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DepositRequestPayload(long requestId, BankAccessChannel channel, long amountCents) implements CustomPacketPayload {
    public static final Type<DepositRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(YumesumiFinance.MODID, "deposit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DepositRequestPayload> STREAM_CODEC = StreamCodec.of(
            DepositRequestPayload::write,
            DepositRequestPayload::read
    );

    public DepositRequestPayload {
        channel = channel == null ? BankAccessChannel.ATM : channel;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buffer, DepositRequestPayload payload) {
        buffer.writeVarLong(payload.requestId());
        buffer.writeEnum(payload.channel());
        buffer.writeVarLong(payload.amountCents());
    }

    private static DepositRequestPayload read(RegistryFriendlyByteBuf buffer) {
        return new DepositRequestPayload(buffer.readVarLong(), buffer.readEnum(BankAccessChannel.class), buffer.readVarLong());
    }
}
