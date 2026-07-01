package com.yumesumi.finance.registry;

import com.yumesumi.finance.YumesumiFinance;
import com.yumesumi.finance.block.AtmBlock;
import com.yumesumi.finance.block.EnterpriseRegistrationTerminalBlock;
import com.yumesumi.finance.block.PosMachineBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(YumesumiFinance.MODID);

    public static final DeferredBlock<AtmBlock> ATM = BLOCKS.register("atm", () -> new AtmBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .sound(SoundType.METAL)
    ));
    public static final DeferredBlock<EnterpriseRegistrationTerminalBlock> ENTERPRISE_REGISTRATION_TERMINAL = BLOCKS.register("enterprise_registration_terminal",
            () -> new EnterpriseRegistrationTerminalBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.5F, 6.0F)
                            .sound(SoundType.METAL)
            ));
    public static final DeferredBlock<PosMachineBlock> POS_MACHINE = BLOCKS.register("pos_machine", () -> new PosMachineBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0F, 3.0F)
                    .sound(SoundType.METAL)
    ));

    private ModBlocks() {
    }
}
