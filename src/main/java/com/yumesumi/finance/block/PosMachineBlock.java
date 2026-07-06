package com.yumesumi.finance.block;

import com.yumesumi.finance.bank.ui.BankScreenAccessService;
import com.yumesumi.finance.bank.pos.PosMachineData;
import com.yumesumi.finance.block.entity.PosMachineBlockEntity;
import com.yumesumi.finance.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public class PosMachineBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE_NORTH_SOUTH = Block.box(5.0D, 0.0D, 2.0D, 11.0D, 5.0D, 14.0D);
    private static final VoxelShape SHAPE_EAST_WEST = Block.box(2.0D, 0.0D, 5.0D, 14.0D, 5.0D, 11.0D);

    public PosMachineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return facing == Direction.EAST || facing == Direction.WEST ? SHAPE_EAST_WEST : SHAPE_NORTH_SOUTH;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PosMachineBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof PosMachineBlockEntity posMachine) {
            PosMachineData data = stack.get(ModDataComponents.POS_MACHINE.get());
            posMachine.setEnterpriseCode(data == null ? "" : data.enterpriseCode());
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            String enterpriseCode = level.getBlockEntity(pos) instanceof PosMachineBlockEntity posMachine ? posMachine.enterpriseCode() : "";
            BankScreenAccessService.openFromPos(serverPlayer, enterpriseCode);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        BlockEntity blockEntity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (blockEntity instanceof PosMachineBlockEntity posMachine && !posMachine.enterpriseCode().isBlank()) {
            for (ItemStack drop : drops) {
                if (drop.is(asItem())) {
                    drop.set(ModDataComponents.POS_MACHINE.get(), new PosMachineData(posMachine.enterpriseCode()));
                }
            }
        }
        return drops;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }
}
