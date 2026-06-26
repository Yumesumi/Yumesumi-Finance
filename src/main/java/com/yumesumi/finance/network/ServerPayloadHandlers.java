package com.yumesumi.finance.network;

import com.yumesumi.finance.bank.ui.BankScreenAccessService;
import com.yumesumi.finance.network.payload.DepositAllRequestPayload;
import com.yumesumi.finance.network.payload.DepositRequestPayload;
import com.yumesumi.finance.network.payload.TransferRequestPayload;
import com.yumesumi.finance.network.payload.WithdrawRequestPayload;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ServerPayloadHandlers {
    private static final int MAX_REMEMBERED_REQUESTS = 128;
    private static final Map<UUID, PlayerRequestIds> PROCESSED_REQUESTS = new HashMap<>();

    private ServerPayloadHandlers() {
    }

    public static void handleDeposit(DepositRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && accept(player, payload.requestId())) {
            BankScreenAccessService.deposit(player, payload.amountCents(), payload.channel());
        }
    }

    public static void handleDepositAll(DepositAllRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && accept(player, payload.requestId())) {
            BankScreenAccessService.depositAll(player, payload.channel());
        }
    }

    public static void handleWithdraw(WithdrawRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && accept(player, payload.requestId())) {
            BankScreenAccessService.withdraw(player, payload.amountCents(), payload.channel());
        }
    }

    public static void handleTransfer(TransferRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && accept(player, payload.requestId())) {
            BankScreenAccessService.transfer(player, payload.target(), payload.amountCents(), payload.note(), payload.channel());
        }
    }

    private static boolean accept(ServerPlayer player, long requestId) {
        if (requestId <= 0) {
            return false;
        }
        return PROCESSED_REQUESTS.computeIfAbsent(player.getUUID(), ignored -> new PlayerRequestIds()).accept(requestId);
    }

    private static final class PlayerRequestIds {
        private final Deque<Long> order = new ArrayDeque<>();
        private final Set<Long> ids = new HashSet<>();

        private boolean accept(long requestId) {
            if (!ids.add(requestId)) {
                return false;
            }
            order.addLast(requestId);
            while (order.size() > MAX_REMEMBERED_REQUESTS) {
                ids.remove(order.removeFirst());
            }
            return true;
        }
    }
}
