package com.yumesumi.finance.item;

import com.yumesumi.finance.money.CashDenomination;
import com.yumesumi.finance.money.MoneyData;
import com.yumesumi.finance.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class CashItem extends Item {
    private final CashDenomination denomination;

    public CashItem(CashDenomination denomination, Properties properties) {
        super(properties);
        this.denomination = denomination;
    }

    public long getValueCents(ItemStack stack) {
        MoneyData custom = stack.get(ModDataComponents.CUSTOM_CASH.get());
        return custom != null ? custom.valueCents() : denomination.valueCents();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        MoneyData custom = stack.get(ModDataComponents.CUSTOM_CASH.get());
        if (custom != null) {
            tooltip.add(Component.literal(custom.displayName()).withStyle(ChatFormatting.GOLD));
        }
        tooltip.add(Component.translatable("tooltip.yumesumi_finance.cash_value", getValueCents(stack) / 100.0D).withStyle(ChatFormatting.GRAY));
    }
}
