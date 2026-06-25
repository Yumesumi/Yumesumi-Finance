package com.yumesumi.finance.money;

import java.util.Comparator;
import java.util.List;
import net.minecraft.world.item.Rarity;

public final class CashDenominations {
    public static final List<CashDenomination> ALL = List.of(
            CashDenomination.coin("coin_1_cent", 1),
            CashDenomination.coin("coin_5_cent", 5),
            CashDenomination.coin("coin_10_cent", 10),
            CashDenomination.coin("coin_25_cent", 25),
            CashDenomination.coin("coin_50_cent", 50),
            CashDenomination.coin("coin_1_yuan", 100),
            CashDenomination.bill("bill_1_yuan", 100, Rarity.COMMON),
            CashDenomination.bill("bill_2_yuan", 200, Rarity.COMMON),
            CashDenomination.bill("bill_5_yuan", 500, Rarity.COMMON),
            CashDenomination.bill("bill_10_yuan", 1_000, Rarity.COMMON),
            CashDenomination.bill("bill_20_yuan", 2_000, Rarity.COMMON),
            CashDenomination.bill("bill_50_yuan", 5_000, Rarity.COMMON),
            CashDenomination.bill("bill_100_yuan", 10_000, Rarity.UNCOMMON),
            CashDenomination.bill("bill_500_yuan", 50_000, Rarity.RARE),
            CashDenomination.bill("bill_1000_yuan", 100_000, Rarity.EPIC)
    );

    public static final List<CashDenomination> DESCENDING = ALL.stream()
            .sorted(Comparator.comparingLong(CashDenomination::valueCents).reversed())
            .toList();

    private CashDenominations() {
    }
}
