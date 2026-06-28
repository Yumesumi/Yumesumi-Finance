package com.yumesumi.finance.network.payload;

import com.yumesumi.finance.bank.enterprise.EnterpriseAccount;
import com.yumesumi.finance.bank.enterprise.EnterpriseRole;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;

public record EnterpriseDetailView(
        UUID id,
        String code,
        String name,
        String description,
        long balanceCents,
        UUID ownerId,
        String ownerName,
        EnterpriseRole myRole,
        int memberCount,
        int maxMembers,
        long createdAt,
        long lastActiveAt,
        List<EnterpriseMemberView> members,
        List<EnterpriseTransactionView> transactions,
        List<Long> balanceSeries
) {
    public EnterpriseDetailView {
        members = List.copyOf(members);
        transactions = List.copyOf(transactions);
        balanceSeries = List.copyOf(balanceSeries);
    }

    public static EnterpriseDetailView from(EnterpriseAccount account, EnterpriseRole role, String ownerName, List<Long> balanceSeries) {
        return new EnterpriseDetailView(
                account.id(),
                account.code(),
                account.name(),
                account.description(),
                account.balanceCents(),
                account.ownerId(),
                ownerName,
                role,
                account.memberCount(),
                EnterpriseAccount.MAX_MEMBERS,
                account.createdAt(),
                account.lastActiveAt(),
                account.members().stream().map(EnterpriseMemberView::from).toList(),
                account.transactions().stream().limit(10_000).map(EnterpriseTransactionView::from).toList(),
                balanceSeries
        );
    }

    public static void write(RegistryFriendlyByteBuf buf, EnterpriseDetailView view) {
        buf.writeUUID(view.id());
        buf.writeUtf(view.code(), 8);
        buf.writeUtf(view.name(), 64);
        buf.writeUtf(view.description(), 128);
        buf.writeVarLong(view.balanceCents());
        buf.writeUUID(view.ownerId());
        buf.writeUtf(view.ownerName(), 64);
        buf.writeEnum(view.myRole());
        buf.writeVarInt(view.memberCount());
        buf.writeVarInt(view.maxMembers());
        buf.writeVarLong(view.createdAt());
        buf.writeVarLong(view.lastActiveAt());
        buf.writeVarInt(view.members().size());
        for (EnterpriseMemberView member : view.members()) EnterpriseMemberView.write(buf, member);
        buf.writeVarInt(view.transactions().size());
        for (EnterpriseTransactionView transaction : view.transactions()) EnterpriseTransactionView.write(buf, transaction);
        buf.writeVarInt(view.balanceSeries().size());
        for (Long value : view.balanceSeries()) buf.writeVarLong(value);
    }

    public static EnterpriseDetailView read(RegistryFriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        String code = buf.readUtf(8);
        String name = buf.readUtf(64);
        String description = buf.readUtf(128);
        long balance = buf.readVarLong();
        UUID ownerId = buf.readUUID();
        String ownerName = buf.readUtf(64);
        EnterpriseRole role = buf.readEnum(EnterpriseRole.class);
        int memberCount = buf.readVarInt();
        int maxMembers = buf.readVarInt();
        long createdAt = buf.readVarLong();
        long lastActiveAt = buf.readVarLong();
        int memberSize = Math.max(0, buf.readVarInt());
        List<EnterpriseMemberView> members = new ArrayList<>(Math.min(memberSize, EnterpriseAccount.MAX_MEMBERS));
        for (int i = 0; i < memberSize; i++) members.add(EnterpriseMemberView.read(buf));
        int transactionSize = Math.max(0, buf.readVarInt());
        List<EnterpriseTransactionView> transactions = new ArrayList<>(Math.min(transactionSize, EnterpriseAccount.MAX_LEDGER_RECORDS));
        for (int i = 0; i < transactionSize; i++) transactions.add(EnterpriseTransactionView.read(buf));
        int seriesSize = Math.max(0, buf.readVarInt());
        List<Long> series = new ArrayList<>(Math.min(seriesSize, 64));
        for (int i = 0; i < seriesSize; i++) series.add(buf.readVarLong());
        return new EnterpriseDetailView(id, code, name, description, balance, ownerId, ownerName, role, memberCount, maxMembers, createdAt, lastActiveAt, members, transactions, series);
    }
}
