package com.yumesumi.finance;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = YumesumiFinance.MODID, dist = Dist.CLIENT)
public class YumesumiFinanceClient {
    public YumesumiFinanceClient(ModContainer container) {
        // 客户端只注册配置界面；银行 GUI 由服务端快照包打开。
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
