package com.yumesumi.finance.network.payload;

import com.yumesumi.finance.YumesumiFinance;
import com.yumesumi.finance.bank.BankAccessChannel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DepositAllRequestPayload(long requestId, BankAccessChannel channel) implements CustomPacketPayload {
    public static final Type<DepositAllRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(YumesumiFinance.MODID, "deposit_all"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DepositAllRequestPayload> STREAM_CODEC = StreamCodec.of(
            DepositAllRequestPayload::write,
            DepositAllRequestPayload::read
    );

    public DepositAllRequestPayload {
        channel = channel == null ? BankAccessChannel.ATM : channel;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buffer, DepositAllRequestPayload payload) {
        buffer.writeVarLong(payload.requestId());
        buffer.writeEnum(payload.channel());
    }

    private static DepositAllRequestPayload read(RegistryFriendlyByteBuf buffer) {
        return new DepositAllRequestPayload(buffer.readVarLong(), buffer.readEnum(BankAccessChannel.class));
    }
}
