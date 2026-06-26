package com.yumesumi.finance.network;

import com.yumesumi.finance.network.payload.BankSnapshotPayload;
import com.yumesumi.finance.network.payload.DepositAllRequestPayload;
import com.yumesumi.finance.network.payload.DepositRequestPayload;
import com.yumesumi.finance.network.payload.TransferRequestPayload;
import com.yumesumi.finance.network.payload.WithdrawRequestPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModPayloads {
    private ModPayloads() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(BankSnapshotPayload.TYPE, BankSnapshotPayload.STREAM_CODEC, ClientPayloadHandlers::handleBankSnapshot);
        registrar.playToServer(DepositAllRequestPayload.TYPE, DepositAllRequestPayload.STREAM_CODEC, ServerPayloadHandlers::handleDepositAll);
        registrar.playToServer(DepositRequestPayload.TYPE, DepositRequestPayload.STREAM_CODEC, ServerPayloadHandlers::handleDeposit);
        registrar.playToServer(WithdrawRequestPayload.TYPE, WithdrawRequestPayload.STREAM_CODEC, ServerPayloadHandlers::handleWithdraw);
        registrar.playToServer(TransferRequestPayload.TYPE, TransferRequestPayload.STREAM_CODEC, ServerPayloadHandlers::handleTransfer);
    }
}
