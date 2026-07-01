package com.yumesumi.finance.item;

import com.yumesumi.finance.bank.pos.PosMachineData;
import com.yumesumi.finance.network.payload.PosConfigPayload;
import com.yumesumi.finance.registry.ModDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.PacketDistributor;

public class PosMachineItem extends BlockItem {
    public PosMachineItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player != null && player.isShiftKeyDown()) {
            openConfig(context.getLevel(), player, context.getHand(), context.getItemInHand());
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
        }
        return super.useOn(context);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            openConfig(level, player, hand, stack);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        return super.use(level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        PosMachineData data = stack.get(ModDataComponents.POS_MACHINE.get());
        if (data != null && data.bound()) {
            tooltip.add(Component.translatable("tooltip.yumesumi_finance.pos.bound", data.enterpriseCode()).withStyle(ChatFormatting.AQUA));
        }
    }

    private static void openConfig(Level level, Player player, InteractionHand hand, ItemStack stack) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            PosMachineData data = stack.get(ModDataComponents.POS_MACHINE.get());
            PacketDistributor.sendToPlayer(serverPlayer, new PosConfigPayload(hand, data == null ? "" : data.enterpriseCode()));
        }
    }
}
