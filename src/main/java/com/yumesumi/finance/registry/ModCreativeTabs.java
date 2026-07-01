package com.yumesumi.finance.registry;

import com.yumesumi.finance.YumesumiFinance;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, YumesumiFinance.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> FINANCE = CREATIVE_MODE_TABS.register("finance", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.yumesumi_finance.finance"))
            .withTabsBefore(CreativeModeTabs.FUNCTIONAL_BLOCKS)
            .icon(() -> ModItems.BANK_CARD.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(ModItems.ATM.get());
                output.accept(ModItems.ENTERPRISE_REGISTRATION_TERMINAL.get());
                output.accept(ModItems.POS_MACHINE.get());
                output.accept(ModItems.BANK_CARD.get());
                output.accept(ModItems.BANK_ADMIN_CARD.get());
                ModItems.CASH.values().forEach(item -> output.accept(item.get()));
            })
            .build());

    private ModCreativeTabs() {
    }
}
