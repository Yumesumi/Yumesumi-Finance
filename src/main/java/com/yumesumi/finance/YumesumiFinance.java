package com.yumesumi.finance;

import com.mojang.logging.LogUtils;
import com.yumesumi.finance.registry.ModBlocks;
import com.yumesumi.finance.registry.ModBlockEntities;
import com.yumesumi.finance.registry.ModCreativeTabs;
import com.yumesumi.finance.registry.ModDataComponents;
import com.yumesumi.finance.registry.ModItems;
import com.yumesumi.finance.network.ModPayloads;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(YumesumiFinance.MODID)
public class YumesumiFinance {
    public static final String MODID = "yumesumi_finance";
    public static final Logger LOGGER = LogUtils.getLogger();

    public YumesumiFinance(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        ModDataComponents.DATA_COMPONENTS.register(modEventBus);
        modEventBus.addListener(ModPayloads::register);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
}
