package com.yumesumi.finance.registry;

import com.yumesumi.finance.YumesumiFinance;
import com.yumesumi.finance.money.MoneyData;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, YumesumiFinance.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MoneyData>> CUSTOM_CASH = DATA_COMPONENTS.registerComponentType(
            "custom_cash",
            builder -> builder.persistent(MoneyData.CODEC)
    );

    private ModDataComponents() {
    }
}
