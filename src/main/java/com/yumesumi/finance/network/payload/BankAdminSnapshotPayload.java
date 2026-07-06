package com.yumesumi.finance.network.payload;

import com.yumesumi.finance.YumesumiFinance;
import com.yumesumi.finance.bank.account.BankAccount;
import com.yumesumi.finance.bank.account.BankAccountType;
import com.yumesumi.finance.bank.admin.TreasuryTransactionRecord;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BankAdminSnapshotPayload(
        List<BankAccountView> accounts,
        BigInteger treasuryBalance,
        List<TreasuryTransactionView> treasuryTransactions,
        String messageKey
) implements CustomPacketPayload {
    public static final Type<BankAdminSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(YumesumiFinance.MODID, "bank_admin_snapshot"));
    private static final int MAX_ACCOUNT_VIEWS = 4_096;
    private static final int MAX_TREASURY_TRANSACTIONS = 10_000;
    public static final StreamCodec<RegistryFriendlyByteBuf, BankAdminSnapshotPayload> STREAM_CODEC =
            StreamCodec.of(BankAdminSnapshotPayload::write, BankAdminSnapshotPayload::read);

    public BankAdminSnapshotPayload {
        accounts = List.copyOf((accounts == null ? List.<BankAccountView>of() : accounts).stream().limit(MAX_ACCOUNT_VIEWS).toList());
        if (treasuryBalance == null) treasuryBalance = BigInteger.ZERO;
        treasuryTransactions = List.copyOf((treasuryTransactions == null ? List.<TreasuryTransactionView>of() : treasuryTransactions).stream().limit(MAX_TREASURY_TRANSACTIONS).toList());
        if (messageKey == null) messageKey = "";
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void write(RegistryFriendlyByteBuf buf, BankAdminSnapshotPayload payload) {
        buf.writeVarInt(payload.accounts().size());
        for (BankAccountView view : payload.accounts()) {
            BankAccountView.write(buf, view);
        }
        buf.writeUtf(payload.treasuryBalance().toString(), 256);
        buf.writeVarInt(payload.treasuryTransactions().size());
        for (TreasuryTransactionView view : payload.treasuryTransactions()) {
            TreasuryTransactionView.write(buf, view);
        }
        buf.writeUtf(payload.messageKey(), 128);
    }

    private static BankAdminSnapshotPayload read(RegistryFriendlyByteBuf buf) {
        int size = Math.max(0, buf.readVarInt());
        List<BankAccountView> accounts = new ArrayList<>(Math.min(size, MAX_ACCOUNT_VIEWS));
        for (int i = 0; i < size; i++) {
            BankAccountView view = BankAccountView.read(buf);
            if (i < MAX_ACCOUNT_VIEWS) accounts.add(view);
        }
        BigInteger treasury = new BigInteger(buf.readUtf(256));
        int treasurySize = Math.max(0, buf.readVarInt());
        List<TreasuryTransactionView> treasuryTransactions = new ArrayList<>(Math.min(treasurySize, MAX_TREASURY_TRANSACTIONS));
        for (int i = 0; i < treasurySize; i++) {
            TreasuryTransactionView view = TreasuryTransactionView.read(buf);
            if (i < MAX_TREASURY_TRANSACTIONS) treasuryTransactions.add(view);
        }
        String messageKey = buf.readUtf(128);
        return new BankAdminSnapshotPayload(accounts, treasury, treasuryTransactions, messageKey);
    }

    public record BankAccountView(
            java.util.UUID owner,
            BankAccountType accountType,
            String ownerName,
            String accountCode,
            long balanceCents,
            long createdAt,
            long lastActiveAt,
            String adminNote,
            boolean frozen
    ) {
        public static BankAccountView from(BankAccount account) {
            return new BankAccountView(
                    account.owner(),
                    account.accountType(),
                    account.ownerName(),
                    account.owner().toString(),
                    account.balanceCents(),
                    account.createdAt(),
                    account.lastActiveAt(),
                    account.adminNote(),
                    account.frozen()
            );
        }

        public static void write(RegistryFriendlyByteBuf buf, BankAccountView view) {
            buf.writeUUID(view.owner());
            buf.writeEnum(view.accountType());
            buf.writeUtf(view.ownerName(), 64);
            buf.writeUtf(view.accountCode(), 64);
            buf.writeVarLong(view.balanceCents());
            buf.writeVarLong(view.createdAt());
            buf.writeVarLong(view.lastActiveAt());
            buf.writeUtf(view.adminNote(), 64);
            buf.writeBoolean(view.frozen());
        }

        public static BankAccountView read(RegistryFriendlyByteBuf buf) {
            return new BankAccountView(
                    buf.readUUID(),
                    buf.readEnum(BankAccountType.class),
                    buf.readUtf(64),
                    buf.readUtf(64),
                    buf.readVarLong(),
                    buf.readVarLong(),
                    buf.readVarLong(),
                    buf.readUtf(64),
                    buf.readBoolean()
            );
        }
    }

    public record TreasuryTransactionView(
            java.util.UUID id,
            BigInteger deltaCents,
            BigInteger balanceAfterCents,
            String operatorName,
            String note,
            long createdAt
    ) {
        public static TreasuryTransactionView from(TreasuryTransactionRecord record) {
            return new TreasuryTransactionView(
                    record.id(),
                    record.deltaCents(),
                    record.balanceAfterCents(),
                    record.operatorName(),
                    record.note(),
                    record.createdAt()
            );
        }

        public static void write(RegistryFriendlyByteBuf buf, TreasuryTransactionView view) {
            buf.writeUUID(view.id());
            buf.writeUtf(view.deltaCents().toString(), 256);
            buf.writeUtf(view.balanceAfterCents().toString(), 256);
            buf.writeUtf(view.operatorName(), 64);
            buf.writeUtf(view.note(), 64);
            buf.writeVarLong(view.createdAt());
        }

        public static TreasuryTransactionView read(RegistryFriendlyByteBuf buf) {
            return new TreasuryTransactionView(
                    buf.readUUID(),
                    new BigInteger(buf.readUtf(256)),
                    new BigInteger(buf.readUtf(256)),
                    buf.readUtf(64),
                    buf.readUtf(64),
                    buf.readVarLong()
            );
        }
    }
}
