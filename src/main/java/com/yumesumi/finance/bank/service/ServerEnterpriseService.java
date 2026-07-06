package com.yumesumi.finance.bank.service;

import com.yumesumi.finance.bank.enterprise.EnterpriseAccount;
import com.yumesumi.finance.bank.enterprise.EnterpriseRole;
import com.yumesumi.finance.bank.enterprise.EnterpriseScreenMode;
import com.yumesumi.finance.bank.enterprise.EnterpriseTransactionRecord;
import com.yumesumi.finance.bank.enterprise.EnterpriseTransactionType;
import com.yumesumi.finance.bank.BankAccessChannel;
import com.yumesumi.finance.bank.account.BankAccount;
import com.yumesumi.finance.bank.inbox.InboxMessage;
import com.yumesumi.finance.bank.inbox.InboxMessageType;
import com.yumesumi.finance.bank.storage.BankSavedData;
import com.yumesumi.finance.bank.storage.EnterpriseSavedData;
import com.yumesumi.finance.bank.transfer.TransferTarget;
import com.yumesumi.finance.bank.transfer.TransferTargetResolver;
import com.yumesumi.finance.network.payload.EnterpriseDetailView;
import com.yumesumi.finance.network.payload.EnterpriseSnapshotPayload;
import com.yumesumi.finance.network.payload.EnterpriseSummaryView;
import com.yumesumi.finance.transaction.TransactionRecord;
import com.yumesumi.finance.transaction.TransactionResult;
import com.yumesumi.finance.transaction.TransactionType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public class ServerEnterpriseService {
    private static final Set<UUID> TERMINAL_REGISTRATION_ACCESS = new java.util.HashSet<>();

    private final MinecraftServer server;
    private final EnterpriseSavedData enterprises;
    private final BankSavedData bankData;

    private ServerEnterpriseService(MinecraftServer server) {
        this.server = server;
        this.enterprises = EnterpriseSavedData.get(server);
        this.bankData = BankSavedData.get(server);
    }

    public static ServerEnterpriseService forServer(MinecraftServer server) {
        return new ServerEnterpriseService(server);
    }

    public List<EnterpriseSummaryView> summaries(ServerPlayer player) {
        return enterprises.enterprisesFor(player.getUUID()).stream()
                .map(account -> EnterpriseSummaryView.from(account, account.roleOf(player.getUUID())))
                .toList();
    }

    public void openList(ServerPlayer player, String messageKey) {
        TERMINAL_REGISTRATION_ACCESS.remove(player.getUUID());
        enterprises.setPrefersEnterprise(player.getUUID(), true);
        sendSnapshot(player, EnterpriseScreenMode.LIST, null, "", false, messageKey, true);
    }

    public void openRegister(ServerPlayer player) {
        pruneTerminalRegistrationAccess();
        TERMINAL_REGISTRATION_ACCESS.add(player.getUUID());
        sendSnapshot(player, EnterpriseScreenMode.REGISTER, null, "", false, "message.yumesumi_finance.enterprise.register.open", true);
    }

    public void checkCode(ServerPlayer player, String code) {
        boolean available;
        try {
            available = enterprises.isCodeAvailable(code, null);
        } catch (IllegalArgumentException exception) {
            available = false;
        }
        sendSnapshot(player, EnterpriseScreenMode.REGISTER, null, code, available,
                available ? "message.yumesumi_finance.enterprise.code_available" : "message.yumesumi_finance.enterprise.code_unavailable", available);
    }

    public void createEnterprise(ServerPlayer player, String code, String name, String description) {
        if (!TERMINAL_REGISTRATION_ACCESS.contains(player.getUUID())) {
            openList(player, "message.yumesumi_finance.enterprise.register_terminal_only");
            return;
        }
        if (bankData.find(player.getUUID()).map(com.yumesumi.finance.bank.account.BankAccount::frozen).orElse(false)) {
            sendSnapshot(player, EnterpriseScreenMode.REGISTER, null, code, false, "message.yumesumi_finance.account.frozen", false);
            return;
        }
        try {
            if (!enterprises.isCodeAvailable(code, null)) {
                sendSnapshot(player, EnterpriseScreenMode.REGISTER, null, code, false, "message.yumesumi_finance.enterprise.code_unavailable", false);
                return;
            }
            if (enterprises.ownedEnterpriseCount(player.getUUID()) >= EnterpriseSavedData.MAX_OWNER_ENTERPRISES) {
                sendSnapshot(player, EnterpriseScreenMode.REGISTER, null, code, false, "message.yumesumi_finance.enterprise.owner_limit", false);
                return;
            }
        } catch (IllegalArgumentException exception) {
            sendSnapshot(player, EnterpriseScreenMode.REGISTER, null, code, false, "message.yumesumi_finance.enterprise.invalid_code", false);
            return;
        }

        EnterpriseAccount account;
        try {
            account = enterprises.createEnterprise(code, name, description, player.getUUID(), player.getGameProfile().getName());
        } catch (RuntimeException exception) {
            sendSnapshot(player, EnterpriseScreenMode.REGISTER, null, code, false, "message.yumesumi_finance.enterprise.create_failed", false);
            return;
        }

        ServerBankService bankService = ServerBankService.forServerPlayer(player);
        TransactionResult feeResult;
        try {
            feeResult = bankService.transfer(player, BankSavedData.TREASURY_UUID, BankSavedData.TREASURY_NAME,
                    EnterpriseSavedData.REGISTRATION_FEE_CENTS, "企业注册费");
        } catch (RuntimeException exception) {
            enterprises.deleteEnterprise(account);
            sendSnapshot(player, EnterpriseScreenMode.REGISTER, null, code, false, "message.yumesumi_finance.enterprise.create_failed", false);
            return;
        }
        if (!feeResult.success()) {
            enterprises.deleteEnterprise(account);
            sendSnapshot(player, EnterpriseScreenMode.REGISTER, null, code, false, feeResult.messageKey(), false);
            return;
        }

        account.appendTransaction(new EnterpriseTransactionRecord(UUID.randomUUID(), account.id(), EnterpriseTransactionType.REGISTER_FEE,
                EnterpriseSavedData.REGISTRATION_FEE_CENTS, 0L, player.getUUID(), player.getGameProfile().getName(),
                BankSavedData.TREASURY_UUID, BankSavedData.TREASURY_NAME, "企业注册费", System.currentTimeMillis()));
        enterprises.setDirtyAndTouch(account);
        TERMINAL_REGISTRATION_ACCESS.remove(player.getUUID());
        openDetail(player, account.id(), "message.yumesumi_finance.enterprise.created");
    }

    public void openDetail(ServerPlayer player, UUID enterpriseId, String messageKey) {
        EnterpriseAccount account = requireVisible(player, enterpriseId).orElse(null);
        if (account == null) {
            openList(player, "message.yumesumi_finance.enterprise.not_found");
            return;
        }
        sendSnapshot(player, EnterpriseScreenMode.DETAIL, account, "", false, messageKey, true);
    }

    public void inviteMember(ServerPlayer player, UUID enterpriseId, String targetText) {
        EnterpriseAccount account = requireRole(player, enterpriseId, EnterpriseRole::canInvite).orElse(null);
        if (account == null) {
            openList(player, "message.yumesumi_finance.enterprise.permission_denied");
            return;
        }
        TransferTarget target = resolvePlayer(targetText).orElse(null);
        if (target == null) {
            openDetail(player, enterpriseId, "message.yumesumi_finance.enterprise.invite_target_not_found");
            return;
        }
        if (account.containsMember(target.owner())) {
            openDetail(player, enterpriseId, "message.yumesumi_finance.enterprise.already_member");
            return;
        }
        InboxMessage message = new InboxMessage(UUID.randomUUID(), InboxMessageType.ENTERPRISE_INVITE,
                "企业邀请：" + account.name(),
                player.getGameProfile().getName() + " 邀请你加入企业 " + account.name() + "（" + account.code() + "）",
                account.id(),
                System.currentTimeMillis());
        enterprises.addInboxMessage(target.owner(), message);
        openDetail(player, enterpriseId, "message.yumesumi_finance.enterprise.invite_sent");
    }

    public void acceptInvite(ServerPlayer player, UUID messageId) {
        InboxMessage message = enterprises.findMessage(player.getUUID(), messageId).orElse(null);
        if (message == null || message.type() != InboxMessageType.ENTERPRISE_INVITE || message.relatedEnterpriseId() == null) {
            return;
        }
        EnterpriseAccount account = enterprises.findEnterprise(message.relatedEnterpriseId()).orElse(null);
        if (account == null) {
            enterprises.deleteMessage(player.getUUID(), messageId);
            return;
        }
        if (!account.containsMember(player.getUUID())) {
            account.addMember(new com.yumesumi.finance.bank.enterprise.EnterpriseMember(player.getUUID(), player.getGameProfile().getName(), EnterpriseRole.MEMBER, System.currentTimeMillis()));
            enterprises.setDirtyAndTouch(account);
        }
        enterprises.deleteMessage(player.getUUID(), messageId);
    }

    public void markMessageRead(ServerPlayer player, UUID messageId) {
        enterprises.findMessage(player.getUUID(), messageId).ifPresent(message -> {
            message.setRead(true);
            enterprises.setDirty();
        });
    }

    public void toggleMessageStar(ServerPlayer player, UUID messageId) {
        enterprises.findMessage(player.getUUID(), messageId).ifPresent(message -> {
            message.setStarred(!message.starred());
            enterprises.setDirty();
        });
    }

    public void deleteMessage(ServerPlayer player, UUID messageId) {
        enterprises.findMessage(player.getUUID(), messageId)
                .filter(message -> !message.starred())
                .ifPresent(message -> enterprises.deleteMessage(player.getUUID(), message.id()));
    }

    public void updateSettings(ServerPlayer player, UUID enterpriseId, String name, String code, String description) {
        EnterpriseAccount account = requireRole(player, enterpriseId, EnterpriseRole::canManageRoles).orElse(null);
        if (account == null) {
            openList(player, "message.yumesumi_finance.enterprise.permission_denied");
            return;
        }
        try {
            EnterpriseAccount.normalizeCode(code);
            enterprises.updateEnterpriseSettings(account, name, code, description);
            openDetail(player, enterpriseId, "message.yumesumi_finance.enterprise.settings_updated");
        } catch (IllegalArgumentException exception) {
            String rawCode = code == null ? "" : code.trim();
            if (!rawCode.toUpperCase(java.util.Locale.ROOT).matches("[A-Z0-9]{3,4}")) {
                openDetail(player, enterpriseId, "message.yumesumi_finance.enterprise.invalid_code");
                return;
            }
            openDetail(player, enterpriseId, "message.yumesumi_finance.enterprise.code_unavailable");
        }
    }

    public void disband(ServerPlayer player, UUID enterpriseId, boolean confirmed) {
        EnterpriseAccount account = requireRole(player, enterpriseId, EnterpriseRole::canDisband).orElse(null);
        if (account == null) {
            openList(player, "message.yumesumi_finance.enterprise.permission_denied");
            return;
        }
        if (!confirmed) {
            openDetail(player, enterpriseId, "message.yumesumi_finance.enterprise.disband_needs_confirm");
            return;
        }
        if (account.balanceCents() > 0) {
            bankData.addToTreasury(account.balanceCents(), player.getGameProfile().getName(), "企业注销余额转入国库：" + account.code());
        }
        enterprises.deleteEnterprise(account);
        openList(player, "message.yumesumi_finance.enterprise.disbanded");
    }

    public void transferPersonalToEnterprise(ServerPlayer player, BankAccessChannel channel, String code, long amountCents, String note) {
        note = sanitizeNote(note);
        if (bankData.find(player.getUUID()).map(BankAccount::frozen).orElse(false)) {
            sendPersonalTransferSnapshot(player, channel, "message.yumesumi_finance.account.frozen", false);
            return;
        }
        EnterpriseAccount target = enterprises.findByCode(code).orElse(null);
        if (target == null) {
            sendPersonalTransferSnapshot(player, channel, "message.yumesumi_finance.transfer.account_not_found", false);
            return;
        }
        long balance = bankData.balanceOf(player.getUUID());
        if (amountCents <= 0) {
            sendPersonalTransferSnapshot(player, channel, "message.yumesumi_finance.transfer.invalid_amount", false);
            return;
        }
        if (balance < amountCents) {
            sendPersonalTransferSnapshot(player, channel, "message.yumesumi_finance.transfer.not_enough_balance", false);
            return;
        }
        long enterpriseBalance;
        try {
            enterpriseBalance = Math.addExact(target.balanceCents(), amountCents);
        } catch (ArithmeticException exception) {
            sendPersonalTransferSnapshot(player, channel, "message.yumesumi_finance.transfer.too_large", false);
            return;
        }
        long senderBalance = balance - amountCents;
        long originalEnterpriseBalance = target.balanceCents();
        TransactionRecord personalRecord = null;
        EnterpriseTransactionRecord enterpriseRecord = null;
        try {
            bankData.setBalance(player.getUUID(), senderBalance);
            target.setBalanceCents(enterpriseBalance);
            personalRecord = recordPersonal(player.getUUID(), TransactionType.TRANSFER_OUT, amountCents, senderBalance, target.name(), target.id(), note);
            enterpriseRecord = recordEnterprise(target, EnterpriseTransactionType.TRANSFER_IN, amountCents, enterpriseBalance, player.getUUID(), player.getGameProfile().getName(), player.getUUID(), player.getGameProfile().getName(), note);
            enterprises.setDirtyAndTouch(target);
        } catch (RuntimeException exception) {
            if (personalRecord != null) bankData.removeTransaction(personalRecord.id());
            if (enterpriseRecord != null) target.removeTransaction(enterpriseRecord.id());
            bankData.setBalance(player.getUUID(), balance);
            target.setBalanceCents(originalEnterpriseBalance);
            enterprises.setDirtyAndTouch(target);
            sendPersonalTransferSnapshot(player, channel, "message.yumesumi_finance.transfer.failed", false);
            return;
        }
        sendPersonalTransferSnapshot(player, channel, "message.yumesumi_finance.enterprise.transfer_success", true);
    }

    public void transferFromEnterprise(ServerPlayer player, UUID sourceId, String targetText, boolean targetEnterprise, long amountCents, String note) {
        note = sanitizeNote(note);
        EnterpriseAccount source = requireRole(player, sourceId, EnterpriseRole::canManageMoney).orElse(null);
        if (source == null) {
            openList(player, "message.yumesumi_finance.enterprise.permission_denied");
            return;
        }
        if (amountCents <= 0) {
            openDetail(player, sourceId, "message.yumesumi_finance.transfer.invalid_amount");
            return;
        }
        if (source.balanceCents() < amountCents) {
            openDetail(player, sourceId, "message.yumesumi_finance.transfer.not_enough_balance");
            return;
        }
        if (targetEnterprise) {
            EnterpriseAccount target = enterprises.findByCode(targetText).orElse(null);
            if (target == null || target.id().equals(source.id())) {
                openDetail(player, sourceId, "message.yumesumi_finance.transfer.account_not_found");
                return;
            }
            long newTargetBalance;
            try {
                newTargetBalance = Math.addExact(target.balanceCents(), amountCents);
            } catch (ArithmeticException exception) {
                openDetail(player, sourceId, "message.yumesumi_finance.transfer.too_large");
                return;
            }
            long newSourceBalance = source.balanceCents() - amountCents;
            long originalSourceBalance = source.balanceCents();
            long originalTargetBalance = target.balanceCents();
            EnterpriseTransactionRecord sourceRecord = null;
            EnterpriseTransactionRecord targetRecord = null;
            try {
                source.setBalanceCents(newSourceBalance);
                target.setBalanceCents(newTargetBalance);
                sourceRecord = recordEnterprise(source, EnterpriseTransactionType.TRANSFER_OUT, amountCents, newSourceBalance, player.getUUID(), player.getGameProfile().getName(), target.id(), target.name(), note);
                targetRecord = recordEnterprise(target, EnterpriseTransactionType.TRANSFER_IN, amountCents, newTargetBalance, player.getUUID(), player.getGameProfile().getName(), source.id(), source.name(), note);
                enterprises.setDirtyAndTouch(source);
                enterprises.setDirtyAndTouch(target);
            } catch (RuntimeException exception) {
                if (sourceRecord != null) source.removeTransaction(sourceRecord.id());
                if (targetRecord != null) target.removeTransaction(targetRecord.id());
                source.setBalanceCents(originalSourceBalance);
                target.setBalanceCents(originalTargetBalance);
                enterprises.setDirtyAndTouch(source);
                enterprises.setDirtyAndTouch(target);
                openDetail(player, sourceId, "message.yumesumi_finance.transfer.failed");
                return;
            }
            openDetail(player, sourceId, "message.yumesumi_finance.transfer.success");
            return;
        }

        TransferTarget target = TransferTargetResolver.resolve(server, targetText).orElse(null);
        if (target == null) {
            openDetail(player, sourceId, "message.yumesumi_finance.transfer.target_not_found");
            return;
        }
        long newSourceBalance = source.balanceCents() - amountCents;
        if (BankSavedData.TREASURY_UUID.equals(target.owner())) {
            long originalSourceBalance = source.balanceCents();
            EnterpriseTransactionRecord sourceRecord = null;
            boolean treasuryChanged = false;
            try {
                source.setBalanceCents(newSourceBalance);
                bankData.addToTreasury(amountCents, player.getGameProfile().getName(), note.isBlank() ? "企业转账至国库：" + source.code() : note);
                treasuryChanged = true;
                sourceRecord = recordEnterprise(source, EnterpriseTransactionType.TRANSFER_OUT, amountCents, newSourceBalance, player.getUUID(), player.getGameProfile().getName(), BankSavedData.TREASURY_UUID, BankSavedData.TREASURY_NAME, note);
                enterprises.setDirtyAndTouch(source);
            } catch (RuntimeException exception) {
                if (sourceRecord != null) source.removeTransaction(sourceRecord.id());
                source.setBalanceCents(originalSourceBalance);
                if (treasuryChanged) {
                    bankData.addToTreasury(-amountCents, player.getGameProfile().getName(), "\u4f01\u4e1a\u8f6c\u8d26\u81f3\u56fd\u5e93\u56de\u6eda\uff1a" + source.code());
                }
                enterprises.setDirtyAndTouch(source);
                openDetail(player, sourceId, "message.yumesumi_finance.transfer.failed");
                return;
            }
            openDetail(player, sourceId, "message.yumesumi_finance.transfer.success");
            return;
        }
        BankAccount receiver = bankData.find(target.owner()).orElse(null);
        if (receiver == null) {
            openDetail(player, sourceId, "message.yumesumi_finance.transfer.account_not_found");
            return;
        }
        if (receiver.frozen()) {
            openDetail(player, sourceId, "message.yumesumi_finance.account.frozen");
            return;
        }
        long receiverBalance;
        try {
            receiverBalance = Math.addExact(bankData.balanceOf(target.owner()), amountCents);
        } catch (ArithmeticException exception) {
            openDetail(player, sourceId, "message.yumesumi_finance.transfer.too_large");
            return;
        }
        long originalSourceBalance = source.balanceCents();
        long originalReceiverBalance = bankData.balanceOf(target.owner());
        TransactionRecord personalRecord = null;
        EnterpriseTransactionRecord enterpriseRecord = null;
        try {
            source.setBalanceCents(newSourceBalance);
            bankData.setBalance(target.owner(), receiverBalance);
            personalRecord = recordPersonal(target.owner(), TransactionType.TRANSFER_IN, amountCents, receiverBalance, source.name(), source.id(), note);
            enterpriseRecord = recordEnterprise(source, EnterpriseTransactionType.TRANSFER_OUT, amountCents, newSourceBalance, player.getUUID(), player.getGameProfile().getName(), target.owner(), target.displayName(), note);
            enterprises.setDirtyAndTouch(source);
        } catch (RuntimeException exception) {
            if (personalRecord != null) bankData.removeTransaction(personalRecord.id());
            if (enterpriseRecord != null) source.removeTransaction(enterpriseRecord.id());
            source.setBalanceCents(originalSourceBalance);
            bankData.setBalance(target.owner(), originalReceiverBalance);
            enterprises.setDirtyAndTouch(source);
            openDetail(player, sourceId, "message.yumesumi_finance.transfer.failed");
            return;
        }
        openDetail(player, sourceId, "message.yumesumi_finance.transfer.success");
    }

    public void setMemberRole(ServerPlayer player, UUID enterpriseId, String targetText, EnterpriseRole role) {
        EnterpriseAccount account = requireRole(player, enterpriseId, EnterpriseRole::canManageRoles).orElse(null);
        if (account == null) {
            openList(player, "message.yumesumi_finance.enterprise.permission_denied");
            return;
        }
        List<UUID> targets = parseUuidList(targetText);
        if (targets.isEmpty()) {
            openDetail(player, enterpriseId, "message.yumesumi_finance.enterprise.member_not_found");
            return;
        }
        try {
            for (UUID target : targets) {
                EnterpriseRole nextRole = role;
                if (role == EnterpriseRole.ADMIN && account.roleOf(target) == EnterpriseRole.ADMIN) {
                    nextRole = EnterpriseRole.MEMBER;
                }
                account.setRole(target, nextRole);
            }
            enterprises.setDirtyAndTouch(account);
            openDetail(player, enterpriseId, "message.yumesumi_finance.enterprise.member_updated");
        } catch (RuntimeException exception) {
            openDetail(player, enterpriseId, "message.yumesumi_finance.enterprise.member_not_found");
        }
    }

    public void removeMember(ServerPlayer player, UUID enterpriseId, String targetText) {
        EnterpriseAccount account = requireRole(player, enterpriseId, EnterpriseRole::canManageRoles).orElse(null);
        if (account == null) {
            openList(player, "message.yumesumi_finance.enterprise.permission_denied");
            return;
        }
        List<UUID> targets = parseUuidList(targetText);
        if (targets.isEmpty()) {
            openDetail(player, enterpriseId, "message.yumesumi_finance.enterprise.member_not_found");
            return;
        }
        try {
            for (UUID target : targets) {
                account.removeMember(target);
            }
            enterprises.setDirtyAndTouch(account);
            openDetail(player, enterpriseId, "message.yumesumi_finance.enterprise.member_removed");
        } catch (RuntimeException exception) {
            openDetail(player, enterpriseId, "message.yumesumi_finance.enterprise.member_not_found");
        }
    }

    public void quickTransferMember(ServerPlayer player, UUID enterpriseId, String targetText, long amountCents, String note) {
        note = sanitizeNote(note);
        EnterpriseAccount source = requireRole(player, enterpriseId, EnterpriseRole::canManageMoney).orElse(null);
        if (source == null) {
            openList(player, "message.yumesumi_finance.enterprise.permission_denied");
            return;
        }
        List<UUID> targets = parseUuidList(targetText);
        if (targets.isEmpty()) {
            openDetail(player, enterpriseId, "message.yumesumi_finance.enterprise.member_not_found");
            return;
        }
        if (amountCents <= 0) {
            openDetail(player, enterpriseId, "message.yumesumi_finance.transfer.invalid_amount");
            return;
        }
        long total;
        try {
            total = Math.multiplyExact(amountCents, targets.size());
        } catch (ArithmeticException exception) {
            openDetail(player, enterpriseId, "message.yumesumi_finance.transfer.too_large");
            return;
        }
        if (source.balanceCents() < total) {
            openDetail(player, enterpriseId, "message.yumesumi_finance.transfer.not_enough_balance");
            return;
        }
        for (UUID target : targets) {
            if (!source.containsMember(target)) {
                openDetail(player, enterpriseId, "message.yumesumi_finance.enterprise.batch_target_invalid");
                return;
            }
            BankAccount receiver = bankData.find(target).orElse(null);
            if (receiver == null) {
                openDetail(player, enterpriseId, "message.yumesumi_finance.transfer.account_not_found");
                return;
            }
            if (receiver.frozen()) {
                openDetail(player, enterpriseId, "message.yumesumi_finance.account.frozen");
                return;
            }
            try {
                Math.addExact(bankData.balanceOf(target), amountCents);
            } catch (ArithmeticException exception) {
                openDetail(player, enterpriseId, "message.yumesumi_finance.transfer.too_large");
                return;
            }
        }
        long sourceBalance = source.balanceCents() - total;
        long originalSourceBalance = source.balanceCents();
        Map<UUID, Long> originalReceiverBalances = new HashMap<>();
        List<TransactionRecord> personalRecords = new ArrayList<>();
        List<EnterpriseTransactionRecord> enterpriseRecords = new ArrayList<>();
        try {
            source.setBalanceCents(sourceBalance);
            for (UUID target : targets) {
                long originalReceiverBalance = bankData.balanceOf(target);
                long receiverBalance = Math.addExact(originalReceiverBalance, amountCents);
                originalReceiverBalances.put(target, originalReceiverBalance);
                bankData.setBalance(target, receiverBalance);
                String targetName = source.member(target).map(com.yumesumi.finance.bank.enterprise.EnterpriseMember::playerName).orElse(target.toString());
                personalRecords.add(recordPersonal(target, TransactionType.TRANSFER_IN, amountCents, receiverBalance, source.name(), source.id(), note));
                enterpriseRecords.add(recordEnterprise(source, EnterpriseTransactionType.TRANSFER_OUT, amountCents, source.balanceCents(), player.getUUID(), player.getGameProfile().getName(), target, targetName, note));
            }
            enterprises.setDirtyAndTouch(source);
        } catch (RuntimeException exception) {
            personalRecords.forEach(record -> bankData.removeTransaction(record.id()));
            enterpriseRecords.forEach(record -> source.removeTransaction(record.id()));
            source.setBalanceCents(originalSourceBalance);
            originalReceiverBalances.forEach(bankData::setBalance);
            enterprises.setDirtyAndTouch(source);
            openDetail(player, enterpriseId, "message.yumesumi_finance.transfer.failed");
            return;
        }
        openDetail(player, enterpriseId, "message.yumesumi_finance.transfer.success");
    }

    private Optional<EnterpriseAccount> requireVisible(ServerPlayer player, UUID enterpriseId) {
        return enterprises.findEnterprise(enterpriseId)
                .filter(account -> account.containsMember(player.getUUID()));
    }

    private void pruneTerminalRegistrationAccess() {
        TERMINAL_REGISTRATION_ACCESS.removeIf(playerId -> server.getPlayerList().getPlayer(playerId) == null);
    }

    private Optional<EnterpriseAccount> requireRole(ServerPlayer player, UUID enterpriseId, java.util.function.Predicate<EnterpriseRole> predicate) {
        return requireVisible(player, enterpriseId)
                .filter(account -> {
                    EnterpriseRole role = account.roleOf(player.getUUID());
                    return role != null && predicate.test(role);
                });
    }

    private Optional<TransferTarget> resolvePlayer(String rawTarget) {
        return TransferTargetResolver.resolve(server, rawTarget)
                .filter(target -> !BankSavedData.TREASURY_UUID.equals(target.owner()));
    }

    private Optional<UUID> parseUuid(String raw) {
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private List<UUID> parseUuidList(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        Set<UUID> result = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            parseUuid(part.trim()).ifPresent(result::add);
        }
        return new ArrayList<>(result);
    }

    private static String sanitizeNote(String note) {
        String text = note == null ? "" : note.trim();
        return text.length() <= 64 ? text : text.substring(0, 64);
    }

    private void sendSnapshot(ServerPlayer player, EnterpriseScreenMode mode, EnterpriseAccount detailAccount,
                              String checkedCode, boolean codeAvailable, String messageKey, boolean success) {
        EnterpriseDetailView detail = null;
        if (detailAccount != null) {
            String ownerName = resolveName(detailAccount.ownerId());
            detail = EnterpriseDetailView.from(detailAccount, detailAccount.roleOf(player.getUUID()), ownerName, balanceSeries(detailAccount));
        }
        PacketDistributor.sendToPlayer(player, new EnterpriseSnapshotPayload(
                mode,
                summaries(player),
                detail,
                checkedCode,
                codeAvailable,
                Component.translatable(messageKey),
                messageKey,
                success
        ));
    }

    private void sendPersonalTransferSnapshot(ServerPlayer player, BankAccessChannel channel, String messageKey, boolean success) {
        com.yumesumi.finance.bank.ui.BankScreenAccessService.sendSnapshotWithMessage(player, channel, messageKey, success);
    }

    private String resolveName(UUID playerId) {
        var online = server.getPlayerList().getPlayer(playerId);
        if (online != null) return online.getGameProfile().getName();
        var cached = server.getProfileCache().get(playerId);
        return cached.map(profile -> profile.getName()).orElse(playerId.toString().substring(0, 8) + "...");
    }

    private List<Long> balanceSeries(EnterpriseAccount account) {
        List<EnterpriseTransactionRecord> records = account.recentTransactions(12);
        List<Long> result = new ArrayList<>();
        for (int i = records.size() - 1; i >= 0; i--) {
            result.add(records.get(i).balanceAfterCents());
        }
        if (result.isEmpty() || result.get(result.size() - 1) != account.balanceCents()) {
            result.add(account.balanceCents());
        }
        while (result.size() < 6) result.add(0, result.isEmpty() ? 0L : result.get(0));
        return result;
    }

    private EnterpriseTransactionRecord recordEnterprise(EnterpriseAccount account, EnterpriseTransactionType type, long amountCents, long balanceAfter,
                                                        UUID operatorId, String operatorName, UUID counterpartyId, String counterpartyName, String note) {
        EnterpriseTransactionRecord record = new EnterpriseTransactionRecord(UUID.randomUUID(), account.id(), type, amountCents, balanceAfter,
                operatorId, operatorName, counterpartyId, counterpartyName, sanitizeNote(note), System.currentTimeMillis());
        account.appendTransaction(record);
        return record;
    }

    private TransactionRecord recordPersonal(UUID owner, TransactionType type, long amountCents, long balanceAfter,
                                             String counterpartyName, UUID counterpartyId, String note) {
        TransactionRecord record = new TransactionRecord(UUID.randomUUID(), owner, type, amountCents, balanceAfter,
                System.currentTimeMillis(), counterpartyName, counterpartyId, sanitizeNote(note));
        bankData.append(record);
        return record;
    }
}
