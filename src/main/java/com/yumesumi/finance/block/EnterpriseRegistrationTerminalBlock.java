package com.yumesumi.finance.block;

import com.yumesumi.finance.bank.service.ServerEnterpriseService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class EnterpriseRegistrationTerminalBlock extends Block {
    public EnterpriseRegistrationTerminalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            ServerEnterpriseService.forServer(serverPlayer.server).openRegister(serverPlayer);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
