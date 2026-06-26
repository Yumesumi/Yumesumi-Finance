package com.yumesumi.finance.network;

import com.yumesumi.finance.bank.ui.BankScreenAccessService;
import com.yumesumi.finance.network.payload.DepositAllRequestPayload;
import com.yumesumi.finance.network.payload.DepositRequestPayload;
import com.yumesumi.finance.network.payload.TransferRequestPayload;
import com.yumesumi.finance.network.payload.WithdrawRequestPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ServerPayloadHandlers {
    private ServerPayloadHandlers() {
    }

    public static void handleDeposit(DepositRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            BankScreenAccessService.deposit(player, payload.amountCents());
        }
    }

    public static void handleDepositAll(DepositAllRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            BankScreenAccessService.depositAll(player);
        }
    }

    public static void handleWithdraw(WithdrawRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            BankScreenAccessService.withdraw(player, payload.amountCents());
        }
    }

    public static void handleTransfer(TransferRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            BankScreenAccessService.transfer(player, payload.target(), payload.amountCents(), payload.note());
        }
    }
}
