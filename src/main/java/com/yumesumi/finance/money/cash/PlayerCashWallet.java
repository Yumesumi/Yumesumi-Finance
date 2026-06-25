package com.yumesumi.finance.money.cash;

import com.yumesumi.finance.api.money.CashWallet;
import com.yumesumi.finance.item.CashItem;
import com.yumesumi.finance.money.CashDenomination;
import com.yumesumi.finance.money.CashDenominations;
import com.yumesumi.finance.registry.ModItems;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * 基于玩家背包的实体现金钱包。
 */
public class PlayerCashWallet implements CashWallet {
    @Override
    public long count(ServerPlayer player) {
        long total = 0L;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof CashItem cashItem) {
                total = Math.addExact(total, Math.multiplyExact(cashItem.getValueCents(stack), stack.getCount()));
            }
        }
        return total;
    }

    @Override
    public boolean remove(ServerPlayer player, long amountCents) {
        long total = count(player);
        if (amountCents <= 0 || total < amountCents) {
            return false;
        }

        // 现金可由多种面值组成：先整体收走，再按标准面值找零，避免复杂组合扣款失败。
        clearCash(player);
        long change = total - amountCents;
        if (change > 0) {
            give(player, change);
        }
        return true;
    }

    @Override
    public void give(ServerPlayer player, long amountCents) {
        long remaining = amountCents;
        for (CashDenomination denomination : CashDenominations.DESCENDING) {
            if (remaining < denomination.valueCents()) {
                continue;
            }

            int count = (int) (remaining / denomination.valueCents());
            remaining %= denomination.valueCents();
            while (count > 0) {
                int stackSize = Math.min(64, count);
                ItemStack stack = new ItemStack(itemFor(denomination).get(), stackSize);
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
                count -= stackSize;
            }
        }
    }

    private void clearCash(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof CashItem) {
                stack.setCount(0);
            }
        }
    }

    private DeferredItem<CashItem> itemFor(CashDenomination denomination) {
        for (Map.Entry<CashDenomination, DeferredItem<CashItem>> entry : ModItems.CASH.entrySet()) {
            if (entry.getKey().equals(denomination)) {
                return entry.getValue();
            }
        }
        throw new IllegalStateException("未注册的现金面值：" + denomination.id());
    }
}
