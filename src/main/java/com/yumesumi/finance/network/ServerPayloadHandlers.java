package com.yumesumi.finance.network;

import com.yumesumi.finance.bank.account.BankAccount;
import com.yumesumi.finance.bank.admin.BankAccountQuery;
import com.yumesumi.finance.bank.admin.BankAdminAction;
import com.yumesumi.finance.bank.enterprise.EnterpriseAccount;
import com.yumesumi.finance.bank.enterprise.EnterpriseAction;
import com.yumesumi.finance.bank.enterprise.EnterpriseTransactionType;
import com.yumesumi.finance.bank.enterprise.EnterpriseTransactionRecord;
import com.yumesumi.finance.bank.BankAccessChannel;
import com.yumesumi.finance.bank.service.ServerBankAdminService;
import com.yumesumi.finance.bank.service.ServerEnterpriseService;
import com.yumesumi.finance.bank.storage.BankSavedData;
import com.yumesumi.finance.bank.storage.EnterpriseSavedData;
import com.yumesumi.finance.bank.transfer.TransferTargetResolver;
import com.yumesumi.finance.bank.transfer.TransferTarget;
import com.yumesumi.finance.bank.ui.BankScreenAccessService;
import com.yumesumi.finance.network.payload.BankAdminRequestPayload;
import com.yumesumi.finance.network.payload.BankAdminSnapshotPayload;
import com.yumesumi.finance.network.payload.DepositAllRequestPayload;
import com.yumesumi.finance.network.payload.DepositRequestPayload;
import com.yumesumi.finance.network.payload.EnterpriseRequestPayload;
import com.yumesumi.finance.network.payload.TransferRequestPayload;
import com.yumesumi.finance.network.payload.WithdrawRequestPayload;
import com.yumesumi.finance.transaction.TransactionType;
import com.yumesumi.finance.transaction.TransactionRecord;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ServerPayloadHandlers {
    private static final int MAX_REMEMBERED_REQUESTS = 128;
    private static final Map<UUID, PlayerRequestIds> PROCESSED_REQUESTS = new HashMap<>();

    private ServerPayloadHandlers() {}

    public static void handleDeposit(DepositRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && accept(player, payload.requestId())) {
            if (isFrozen(player)) {
                sendFrozenSnapshot(player, payload.channel());
                return;
            }
            BankScreenAccessService.deposit(player, payload.amountCents(), payload.channel());
        }
    }

    public static void handleDepositAll(DepositAllRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && accept(player, payload.requestId())) {
            if (isFrozen(player)) {
                sendFrozenSnapshot(player, payload.channel());
                return;
            }
            BankScreenAccessService.depositAll(player, payload.channel());
        }
    }

    public static void handleWithdraw(WithdrawRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && accept(player, payload.requestId())) {
            if (isFrozen(player)) {
                sendFrozenSnapshot(player, payload.channel());
                return;
            }
            BankScreenAccessService.withdraw(player, payload.amountCents(), payload.channel());
        }
    }

    public static void handleTransfer(TransferRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && accept(player, payload.requestId())) {
            if (isFrozen(player)) {
                sendFrozenSnapshot(player, payload.channel());
                return;
            }
            if (payload.targetEnterprise()) {
                ServerEnterpriseService.forServer(player.server)
                        .transferPersonalToEnterprise(player, payload.channel(), payload.target(), payload.amountCents(), payload.note());
                return;
            }
            BankScreenAccessService.transfer(player, payload.target(), payload.amountCents(), payload.note(), payload.channel());
        }
    }

    public static void handleEnterpriseRequest(EnterpriseRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!accept(player, payload.requestId())) return;
        ServerEnterpriseService service = ServerEnterpriseService.forServer(player.server);
        switch (payload.action()) {
            case OPEN_REGISTER -> service.openList(player, "message.yumesumi_finance.enterprise.register_terminal_only");
            case CHECK_CODE -> service.checkCode(player, payload.code());
            case CREATE -> service.createEnterprise(player, payload.code(), payload.name(), payload.description());
            case OPEN_LIST -> service.openList(player, "message.yumesumi_finance.enterprise.list.open");
            case OPEN_PERSONAL -> {
                EnterpriseSavedData.get(player.server).setPrefersEnterprise(player.getUUID(), false);
                BankScreenAccessService.sendSnapshotWithMessage(player, BankAccessChannel.CARD, "message.yumesumi_finance.open.card", true);
            }
            case OPEN_DETAIL -> service.openDetail(player, payload.enterpriseId(), "message.yumesumi_finance.enterprise.detail.open");
            case INVITE_MEMBER -> service.inviteMember(player, payload.enterpriseId(), payload.target());
            case ACCEPT_INVITE -> {
                service.acceptInvite(player, payload.messageId());
                BankScreenAccessService.sendSnapshotWithMessage(player, BankAccessChannel.CARD, "message.yumesumi_finance.enterprise.invite_accepted", true);
            }
            case MARK_MESSAGE_READ -> {
                service.markMessageRead(player, payload.messageId());
                BankScreenAccessService.sendSnapshotWithMessage(player, BankAccessChannel.CARD, "message.yumesumi_finance.inbox.updated", true);
            }
            case TOGGLE_MESSAGE_STAR -> {
                service.toggleMessageStar(player, payload.messageId());
                BankScreenAccessService.sendSnapshotWithMessage(player, BankAccessChannel.CARD, "message.yumesumi_finance.inbox.updated", true);
            }
            case DELETE_MESSAGE -> {
                service.deleteMessage(player, payload.messageId());
                BankScreenAccessService.sendSnapshotWithMessage(player, BankAccessChannel.CARD, "message.yumesumi_finance.inbox.updated", true);
            }
            case UPDATE_SETTINGS -> service.updateSettings(player, payload.enterpriseId(), payload.name(), payload.code(), payload.description());
            case DISBAND -> service.disband(player, payload.enterpriseId(), payload.confirmed());
            case TRANSFER_FROM_ENTERPRISE -> service.transferFromEnterprise(player, payload.enterpriseId(), payload.target(), payload.targetEnterprise(), payload.amountCents(), payload.note());
            case QUICK_TRANSFER_MEMBER -> service.quickTransferMember(player, payload.enterpriseId(), payload.target(), payload.amountCents(), payload.note());
            case SET_MEMBER_ROLE -> service.setMemberRole(player, payload.enterpriseId(), payload.target(), payload.role());
            case REMOVE_MEMBER -> service.removeMember(player, payload.enterpriseId(), payload.target());
        }
    }

    public static void handleAdminRequest(BankAdminRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!accept(player, payload.requestId())) return;
        if (!player.hasPermissions(2)) {
            player.displayClientMessage(Component.translatable("message.yumesumi_finance.admin.no_permission"), true);
            return;
        }
        ServerBankAdminService adminService = ServerBankAdminService.forServer(player.server);
        try {
            switch (payload.action()) {
                case LIST -> sendAdminSnapshot(player, adminService, "message.yumesumi_finance.admin.list");
                case TREASURY_INFO -> sendAdminSnapshot(player, adminService, "message.yumesumi_finance.admin.treasury");
                case UPDATE_NOTE -> {
                    if (payload.targetOwner() != null) {
                        if (handleEnterpriseAdminAction(player, payload, adminService)) return;
                        adminService.updateAdminNote(payload.targetOwner(), payload.text());
                        sendAdminSnapshot(player, adminService, "message.yumesumi_finance.admin.note_updated");
                    }
                }
                case SET_BALANCE -> {
                    if (payload.targetOwner() != null) {
                        if (handleEnterpriseAdminAction(player, payload, adminService)) return;
                        adminService.setAccountBalance(payload.targetOwner(), payload.amountCents());
                        sendAdminSnapshot(player, adminService, "message.yumesumi_finance.admin.balance_set");
                    }
                }
                case ADD_BALANCE -> {
                    if (payload.targetOwner() != null) {
                        if (!BankSavedData.TREASURY_UUID.equals(payload.targetOwner()) && handleEnterpriseAdminAction(player, payload, adminService)) return;
                        if (BankSavedData.TREASURY_UUID.equals(payload.targetOwner())) {
                            BigInteger delta = payload.text().isBlank() ? BigInteger.valueOf(payload.amountCents()) : new BigInteger(payload.text());
                            adminService.adjustTreasury(delta, player.getGameProfile().getName());
                        } else {
                            adminService.addBalance(payload.targetOwner(), payload.amountCents());
                        }
                        sendAdminSnapshot(player, adminService, "message.yumesumi_finance.admin.balance_added");
                    }
                }
                case SET_TREASURY_BALANCE -> {
                    BigInteger balance = payload.text().isBlank() ? BigInteger.valueOf(payload.amountCents()) : new BigInteger(payload.text());
                    adminService.setTreasuryBalance(balance, player.getGameProfile().getName());
                    sendAdminSnapshot(player, adminService, "message.yumesumi_finance.admin.balance_set");
                }
                case TREASURY_TRANSFER -> {
                    handleTreasuryTransfer(player, payload);
                    sendAdminSnapshot(player, adminService, "message.yumesumi_finance.admin.treasury_transfer_done");
                }
                case TOGGLE_FREEZE -> {
                    if (payload.targetOwner() != null) {
                        if (handleEnterpriseAdminAction(player, payload, adminService)) return;
                        adminService.toggleFreeze(payload.targetOwner());
                        sendAdminSnapshot(player, adminService, "message.yumesumi_finance.admin.freeze_toggled");
                    }
                }
                case DELETE -> {
                    if (payload.targetOwner() != null) {
                        if (handleEnterpriseAdminAction(player, payload, adminService)) return;
                        adminService.deleteAccount(payload.targetOwner(), false);
                        sendAdminSnapshot(player, adminService, "message.yumesumi_finance.admin.deleted");
                    }
                }
                case DELETE_CONFIRMED -> {
                    if (payload.targetOwner() != null) {
                        if (handleEnterpriseAdminAction(player, payload, adminService)) return;
                        adminService.deleteAccountConfirmed(payload.targetOwner());
                        sendAdminSnapshot(player, adminService, "message.yumesumi_finance.admin.deleted");
                    }
                }
            }
        } catch (IllegalStateException e) {
            sendAdminSnapshot(player, adminService, "message.yumesumi_finance.admin.needs_confirm");
        } catch (Exception e) {
            sendAdminSnapshot(player, adminService, "message.yumesumi_finance.admin.error");
        }
    }


    private static void handleTreasuryTransfer(ServerPlayer player, BankAdminRequestPayload payload) {
        if (payload.amountCents() <= 0) throw new IllegalArgumentException("Invalid amount");
        String[] parts = payload.text().split("\\|", 2);
        String targetText = parts.length > 0 ? parts[0].trim() : "";
        String note = parts.length > 1 ? parts[1].trim() : "";
        if (targetText.isBlank()) throw new IllegalArgumentException("Target required");
        if (note.length() > 64) note = note.substring(0, 64);
        BankSavedData bankData = BankSavedData.get(player.server);
        EnterpriseSavedData enterpriseData = EnterpriseSavedData.get(player.server);
        EnterpriseAccount enterprise = enterpriseData.findByCode(targetText).orElse(null);
        String operatorName = player.getGameProfile().getName();
        if (enterprise != null) {
            long newBalance = Math.addExact(enterprise.balanceCents(), payload.amountCents());
            enterprise.setBalanceCents(newBalance);
            enterprise.appendTransaction(new EnterpriseTransactionRecord(UUID.randomUUID(), enterprise.id(), EnterpriseTransactionType.TRANSFER_IN,
                    payload.amountCents(), newBalance, player.getUUID(), operatorName, BankSavedData.TREASURY_UUID, BankSavedData.TREASURY_NAME,
                    note.isBlank() ? "\u56fd\u5e93\u8f6c\u8d26" : note, System.currentTimeMillis()));
            enterpriseData.setDirtyAndTouch(enterprise);
            bankData.addToTreasury(-payload.amountCents(), operatorName, "\u56fd\u5e93\u8f6c\u8d26\u81f3\u4f01\u4e1a " + enterprise.code());
            return;
        }
        TransferTarget target = TransferTargetResolver.resolve(player.server, targetText).orElse(null);
        if (target == null || BankSavedData.TREASURY_UUID.equals(target.owner()) || bankData.find(target.owner()).isEmpty()) {
            throw new IllegalArgumentException("Target account not found");
        }
        long newBalance = Math.addExact(bankData.balanceOf(target.owner()), payload.amountCents());
        bankData.setBalance(target.owner(), newBalance);
        bankData.append(new TransactionRecord(UUID.randomUUID(), target.owner(), TransactionType.TRANSFER_IN, payload.amountCents(), newBalance,
                System.currentTimeMillis(), BankSavedData.TREASURY_NAME, BankSavedData.TREASURY_UUID,
                note.isBlank() ? "\u56fd\u5e93\u8f6c\u8d26" : note));
        bankData.addToTreasury(-payload.amountCents(), operatorName, "\u56fd\u5e93\u8f6c\u8d26\u81f3\u73a9\u5bb6 " + target.displayName());
    }

    private static boolean isFrozen(ServerPlayer player) {
        BankSavedData data = BankSavedData.get(player.server);
        return data.find(player.getUUID()).map(BankAccount::frozen).orElse(false);
    }

    private static void sendFrozenSnapshot(ServerPlayer player, com.yumesumi.finance.bank.BankAccessChannel channel) {
        BankScreenAccessService.sendFrozenSnapshot(player, channel);
    }

    private static void sendAdminSnapshot(ServerPlayer player, ServerBankAdminService service, String messageKey) {
        java.util.List<BankAdminSnapshotPayload.BankAccountView> views = new java.util.ArrayList<>(service.queryAccounts(BankAccountQuery.byType(null))
                .stream().map(account -> {
                    String name = account.ownerName();
                    if (name == null || name.isBlank()) {
                        var online = player.server.getPlayerList().getPlayer(account.owner());
                        if (online != null) name = online.getGameProfile().getName();
                        else {
                            var cached = player.server.getProfileCache().get(account.owner());
                            if (cached.isPresent()) name = cached.get().getName();
                            else name = account.owner().toString().substring(0, 8) + "...";
                        }
                    }
                    final String resolvedName = name;
                    return new BankAdminSnapshotPayload.BankAccountView(
                            account.owner(), account.accountType(), resolvedName,
                            account.owner().toString(),
                            account.balanceCents(), account.createdAt(), account.lastActiveAt(),
                            account.adminNote(), account.frozen()
                    );
                }).toList());
        EnterpriseSavedData.get(player.server).allEnterprises().forEach(account -> views.add(
                new BankAdminSnapshotPayload.BankAccountView(
                        account.id(), com.yumesumi.finance.bank.account.BankAccountType.ENTERPRISE, account.name(),
                        account.code(),
                        account.balanceCents(), account.createdAt(), account.lastActiveAt(),
                        account.description(), false
                )
        ));
        PacketDistributor.sendToPlayer(player, new BankAdminSnapshotPayload(
                views,
                service.treasuryBalance(),
                service.treasuryTransactions().stream().map(BankAdminSnapshotPayload.TreasuryTransactionView::from).toList(),
                messageKey
        ));
    }

    private static boolean handleEnterpriseAdminAction(ServerPlayer player, BankAdminRequestPayload payload, ServerBankAdminService adminService) {
        EnterpriseSavedData enterpriseData = EnterpriseSavedData.get(player.server);
        EnterpriseAccount enterprise = enterpriseData.findEnterprise(payload.targetOwner()).orElse(null);
        if (enterprise == null) return false;
        try {
            switch (payload.action()) {
                case UPDATE_NOTE -> {
                    enterpriseData.updateEnterpriseSettings(enterprise, enterprise.name(), enterprise.code(), payload.text());
                    sendAdminSnapshot(player, adminService, "message.yumesumi_finance.admin.note_updated");
                    return true;
                }
                case SET_BALANCE -> {
                    enterprise.setBalanceCents(payload.amountCents());
                    enterpriseData.setDirtyAndTouch(enterprise);
                    sendAdminSnapshot(player, adminService, "message.yumesumi_finance.admin.balance_set");
                    return true;
                }
                case ADD_BALANCE -> {
                    enterprise.setBalanceCents(Math.addExact(enterprise.balanceCents(), payload.amountCents()));
                    enterpriseData.setDirtyAndTouch(enterprise);
                    sendAdminSnapshot(player, adminService, "message.yumesumi_finance.admin.balance_added");
                    return true;
                }
                case TOGGLE_FREEZE -> {
                    sendAdminSnapshot(player, adminService, "message.yumesumi_finance.admin.unsupported_enterprise_freeze");
                    return true;
                }
                case DELETE -> {
                    if (enterprise.balanceCents() != 0) throw new IllegalStateException("Enterprise has balance");
                    enterpriseData.deleteEnterprise(enterprise);
                    sendAdminSnapshot(player, adminService, "message.yumesumi_finance.admin.deleted");
                    return true;
                }
                case DELETE_CONFIRMED -> {
                    if (enterprise.balanceCents() > 0) {
                        BankSavedData.get(player.server).addToTreasury(enterprise.balanceCents(), player.getGameProfile().getName(), "管理员删除企业余额转入国库：" + enterprise.code());
                    }
                    enterpriseData.deleteEnterprise(enterprise);
                    sendAdminSnapshot(player, adminService, "message.yumesumi_finance.admin.deleted");
                    return true;
                }
                default -> {
                    return false;
                }
            }
        } catch (IllegalStateException exception) {
            sendAdminSnapshot(player, adminService, "message.yumesumi_finance.admin.needs_confirm");
            return true;
        } catch (Exception exception) {
            sendAdminSnapshot(player, adminService, "message.yumesumi_finance.admin.error");
            return true;
        }
    }

    private static boolean accept(ServerPlayer player, long requestId) {
        if (requestId <= 0) return false;
        return PROCESSED_REQUESTS.computeIfAbsent(player.getUUID(), ignored -> new PlayerRequestIds()).accept(requestId);
    }

    private static final class PlayerRequestIds {
        private final Deque<Long> order = new ArrayDeque<>();
        private final Set<Long> ids = new HashSet<>();
        private boolean accept(long requestId) {
            if (!ids.add(requestId)) return false;
            order.addLast(requestId);
            while (order.size() > MAX_REMEMBERED_REQUESTS) ids.remove(order.removeFirst());
            return true;
        }
    }
}
