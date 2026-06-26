package com.yumesumi.finance.bank.card;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;

/**
 * Persistent owner binding for a bank card item stack.
 */
public record BankCardData(UUID owner, String ownerName) {
    private static final int MAX_OWNER_NAME_LENGTH = 64;
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.comapFlatMap(
            value -> {
                try {
                    return DataResult.success(UUID.fromString(value));
                } catch (IllegalArgumentException exception) {
                    return DataResult.error(() -> "Invalid UUID: " + value);
                }
            },
            UUID::toString
    );

    public static final Codec<BankCardData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUID_CODEC.fieldOf("owner").forGetter(BankCardData::owner),
            Codec.STRING.optionalFieldOf("owner_name", "").forGetter(BankCardData::ownerName)
    ).apply(instance, BankCardData::new));

    public BankCardData {
        if (owner == null) {
            throw new IllegalArgumentException("Bank card owner cannot be null");
        }
        ownerName = ownerName == null ? "" : ownerName.trim();
        if (ownerName.length() > MAX_OWNER_NAME_LENGTH) {
            ownerName = ownerName.substring(0, MAX_OWNER_NAME_LENGTH);
        }
    }

    public boolean ownedBy(UUID playerId) {
        return owner.equals(playerId);
    }
}
