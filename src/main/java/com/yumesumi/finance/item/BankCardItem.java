package com.yumesumi.finance.item;

import com.yumesumi.finance.bank.card.BankCardData;
import com.yumesumi.finance.bank.ui.BankScreenAccessService;
import com.yumesumi.finance.registry.ModDataComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class BankCardItem extends Item {
    public BankCardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            BankCardData cardData = stack.get(ModDataComponents.BANK_CARD.get());
            if (cardData == null) {
                cardData = bindTo(serverPlayer, stack);
            } else {
                markBound(stack);
            }
            BankScreenAccessService.openFromCard(serverPlayer, cardData);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private static BankCardData bindTo(ServerPlayer player, ItemStack stack) {
        String playerName = player.getGameProfile().getName();
        BankCardData cardData = new BankCardData(player.getUUID(), playerName);
        stack.set(ModDataComponents.BANK_CARD.get(), cardData);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(playerName + "的梦澄银行卡"));
        markBound(stack);
        return cardData;
    }

    private static void markBound(ItemStack stack) {
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
    }
}
