package com.yumesumi.finance.network;

import com.yumesumi.finance.network.payload.BankAdminRequestPayload;
import com.yumesumi.finance.network.payload.BankAdminSnapshotPayload;
import com.yumesumi.finance.network.payload.BankSnapshotPayload;
import com.yumesumi.finance.network.payload.DepositAllRequestPayload;
import com.yumesumi.finance.network.payload.DepositRequestPayload;
import com.yumesumi.finance.network.payload.EnterpriseRequestPayload;
import com.yumesumi.finance.network.payload.EnterpriseSnapshotPayload;
import com.yumesumi.finance.network.payload.TransferRequestPayload;
import com.yumesumi.finance.network.payload.WithdrawRequestPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModPayloads {
    private ModPayloads() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(BankSnapshotPayload.TYPE, BankSnapshotPayload.STREAM_CODEC, ModPayloads::handleBankSnapshotClient);
        registrar.playToClient(BankAdminSnapshotPayload.TYPE, BankAdminSnapshotPayload.STREAM_CODEC, ModPayloads::handleAdminSnapshotClient);
        registrar.playToClient(EnterpriseSnapshotPayload.TYPE, EnterpriseSnapshotPayload.STREAM_CODEC, ModPayloads::handleEnterpriseSnapshotClient);
        registrar.playToServer(DepositAllRequestPayload.TYPE, DepositAllRequestPayload.STREAM_CODEC, ServerPayloadHandlers::handleDepositAll);
        registrar.playToServer(DepositRequestPayload.TYPE, DepositRequestPayload.STREAM_CODEC, ServerPayloadHandlers::handleDeposit);
        registrar.playToServer(WithdrawRequestPayload.TYPE, WithdrawRequestPayload.STREAM_CODEC, ServerPayloadHandlers::handleWithdraw);
        registrar.playToServer(TransferRequestPayload.TYPE, TransferRequestPayload.STREAM_CODEC, ServerPayloadHandlers::handleTransfer);
        registrar.playToServer(BankAdminRequestPayload.TYPE, BankAdminRequestPayload.STREAM_CODEC, ServerPayloadHandlers::handleAdminRequest);
        registrar.playToServer(EnterpriseRequestPayload.TYPE, EnterpriseRequestPayload.STREAM_CODEC, ServerPayloadHandlers::handleEnterpriseRequest);
    }

    private static void handleBankSnapshotClient(BankSnapshotPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientPayloadHandlers.handleBankSnapshot(payload, context);
        }
    }

    private static void handleAdminSnapshotClient(BankAdminSnapshotPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientPayloadHandlers.handleAdminSnapshot(payload, context);
        }
    }

    private static void handleEnterpriseSnapshotClient(EnterpriseSnapshotPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientPayloadHandlers.handleEnterpriseSnapshot(payload, context);
        }
    }
}
