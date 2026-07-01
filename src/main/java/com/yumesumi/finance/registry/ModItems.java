package com.yumesumi.finance.registry;

import com.yumesumi.finance.YumesumiFinance;
import com.yumesumi.finance.item.BankAdminCardItem;
import com.yumesumi.finance.item.BankCardItem;
import com.yumesumi.finance.item.CashItem;
import com.yumesumi.finance.item.PosMachineItem;
import com.yumesumi.finance.money.CashDenomination;
import com.yumesumi.finance.money.CashDenominations;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(YumesumiFinance.MODID);

    public static final DeferredItem<BlockItem> ATM = ITEMS.registerSimpleBlockItem("atm", ModBlocks.ATM);
    public static final DeferredItem<BlockItem> ENTERPRISE_REGISTRATION_TERMINAL = ITEMS.registerSimpleBlockItem("enterprise_registration_terminal", ModBlocks.ENTERPRISE_REGISTRATION_TERMINAL);
    public static final DeferredItem<PosMachineItem> POS_MACHINE = ITEMS.register("pos_machine",
            () -> new PosMachineItem(ModBlocks.POS_MACHINE.get(), new Item.Properties()));
    public static final DeferredItem<BankCardItem> BANK_CARD = ITEMS.register("bank_card",
            () -> new BankCardItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<BankAdminCardItem> BANK_ADMIN_CARD = ITEMS.register("bank_admin_card",
            () -> new BankAdminCardItem(new Item.Properties().stacksTo(1)));

    public static final Map<CashDenomination, DeferredItem<CashItem>> CASH = new LinkedHashMap<>();

    static {
        for (CashDenomination denomination : CashDenominations.ALL) {
            CASH.put(denomination, ITEMS.register(denomination.id(),
                    () -> new CashItem(denomination, new Item.Properties().stacksTo(64).rarity(denomination.rarity()))));
        }
    }

    private ModItems() {
    }
}
