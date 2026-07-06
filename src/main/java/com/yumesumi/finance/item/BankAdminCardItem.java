package com.yumesumi.finance.item;

import com.yumesumi.finance.bank.admin.BankAdminAction;
import com.yumesumi.finance.network.ClientRequestIds;
import com.yumesumi.finance.network.payload.BankAdminRequestPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

public class BankAdminCardItem extends Item {
    public BankAdminCardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            PacketDistributor.sendToServer(new BankAdminRequestPayload(ClientRequestIds.next(), BankAdminAction.LIST, null, 0, ""));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
