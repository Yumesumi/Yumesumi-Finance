package com.yumesumi.finance.api.money;

import net.minecraft.server.level.ServerPlayer;

/**
 * 玩家实体现金钱包接口。
 *
 * <p>当前实现读取玩家背包，未来可替换为钱包物品、保险箱或领地金库。</p>
 */
public interface CashWallet {
    long count(ServerPlayer player);

    boolean remove(ServerPlayer player, long amountCents);

    void give(ServerPlayer player, long amountCents);
}
