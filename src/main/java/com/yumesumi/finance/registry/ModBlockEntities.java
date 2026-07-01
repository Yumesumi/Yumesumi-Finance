package com.yumesumi.finance.registry;

import com.yumesumi.finance.YumesumiFinance;
import com.yumesumi.finance.block.entity.PosMachineBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, YumesumiFinance.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PosMachineBlockEntity>> POS_MACHINE = BLOCK_ENTITIES.register(
            "pos_machine",
            () -> BlockEntityType.Builder.of(PosMachineBlockEntity::new, ModBlocks.POS_MACHINE.get()).build(null)
    );

    private ModBlockEntities() {
    }
}
