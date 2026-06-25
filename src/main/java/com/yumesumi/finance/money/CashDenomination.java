package com.yumesumi.finance.money;

import net.minecraft.world.item.Rarity;

public record CashDenomination(String id, long valueCents, CashKind kind, Rarity rarity) {
    public static CashDenomination coin(String id, long cents) {
        return new CashDenomination(id, cents, CashKind.COIN, Rarity.COMMON);
    }

    public static CashDenomination bill(String id, long cents, Rarity rarity) {
        return new CashDenomination(id, cents, CashKind.BILL, rarity);
    }
}
